package com.tonicbox.dukes8bit;

import java.util.Random;
import java.awt.event.KeyEvent;

/**
 * Simulation state and rules for Duke's Descent. Holds the dungeon floor, the
 * player, enemies, and floor loot as flat primitive data. The player glides on a
 * fast clock and attacks with a spinning slash; enemies advance on their own
 * slower clock and only hunt once they have been spotted in the player's light.
 * Contains no rendering or windowing code.
 */
final class Game {

    static final int TILE = 24;
    static final int VIEWPORT_W = 27;
    static final int VIEWPORT_H = 19;
    static final int HUD_HEIGHT = 72;
    static final int PLAY_WIDTH = VIEWPORT_W * TILE;
    static final int PLAY_HEIGHT = VIEWPORT_H * TILE;
    static final int VIEW_WIDTH = PLAY_WIDTH;
    static final int VIEW_HEIGHT = PLAY_HEIGHT + HUD_HEIGHT;

    static final int MAP_WIDTH = 45;
    static final int MAP_HEIGHT = 31;

    static final int WALL = 0;
    static final int FLOOR = 1;
    static final int DOWN_STAIRS = 2;
    static final int UP_STAIRS = 3;
    static final int TRAP = 4;

    static final int PLAYING = 0;
    static final int DEAD = 1;
    static final int SHOP = 2;
    static final int PAUSED = 3;
    static final int INVENTORY = 4;

    static final int BUG = 0;
    static final int NULLPTR = 1;
    static final int LEAK = 2;
    static final int FORKBOMB = 3;
    static final int DEADLOCK = 4;

    static final int MAX_ENEMIES = 32;
    static final int POTION_COST = 12;

    /** Item ids are indices into these tables; names carry the effect for display. */
    static final String[] ITEM_NAME = {
        "Debugger Blade  +3 ATK", "Null Sword  +5 ATK", "Refactor Axe  +8 ATK",
        "Try-Catch Vest  +2 DEF", "Final Plate  +4 DEF", "Sandbox Shield  +6 DEF",
        "Hot Coffee  regen+", "Lantern  sight+", "Lucky Coin  gold+",
    };
    private static final int WEAPON = 0;
    private static final int ARMOR = 1;
    private static final int TRINKET = 2;
    private static final int[] ITEM_SLOT = {WEAPON, WEAPON, WEAPON, ARMOR, ARMOR, ARMOR, TRINKET, TRINKET, TRINKET};
    private static final int[] ITEM_VALUE = {3, 5, 8, 2, 4, 6, 0, 0, 0};
    private static final int TRINKET_COFFEE = 6;
    private static final int TRINKET_LANTERN = 7;
    private static final int TRINKET_COIN = 8;

    private static final int INVENTORY_SIZE = 8;
    private static final int LOOT_CAP = 16;

    private static final int FOV_RADIUS = 7;
    private static final int MAX_ROOMS = 20;
    private static final int ROOM_MIN = 5;
    private static final int ROOM_MAX = 10;
    private static final int BASE_MAP_W = 20;
    private static final int BASE_MAP_H = 14;
    private static final int MAP_GROW_W = 5;
    private static final int MAP_GROW_H = 3;
    private static final int BASE_ROOMS = 4;
    private static final int ROOM_GROW = 2;
    private static final float MOVE_DURATION_MS = 120f;
    private static final float ENEMY_DURATION_MS = 300f;
    private static final float ENEMY_ATTACK_MS = 600f;
    private static final float ATTACK_DURATION_MS = 220f;
    private static final float HIT_DURATION_MS = 180f;
    private static final float REGEN_INTERVAL_MS = 6500f;
    private static final int HP_PER_LEVEL = 4;
    private static final int[] DIR_X = {0, 0, -1, 1};
    private static final int[] DIR_Y = {-1, 1, 0, 0};

    private final Random random = new Random();
    // Generation uses its own seeded RNG so floor layouts are reproducible, independent of combat rolls.
    private final Random genRandom = new Random();

    final int[] map = new int[MAP_WIDTH * MAP_HEIGHT];
    final boolean[] explored = new boolean[MAP_WIDTH * MAP_HEIGHT];
    final boolean[] visible = new boolean[MAP_WIDTH * MAP_HEIGHT];

    // Parallel primitive arrays per enemy slot — no entity objects, no per-enemy allocation.
    final int[] enemyX = new int[MAX_ENEMIES];
    final int[] enemyY = new int[MAX_ENEMIES];
    final int[] enemyType = new int[MAX_ENEMIES];
    final float[] enemyHit = new float[MAX_ENEMIES];
    private final int[] enemyHp = new int[MAX_ENEMIES];
    private final int[] enemyPrevX = new int[MAX_ENEMIES];
    private final int[] enemyPrevY = new int[MAX_ENEMIES];
    private final boolean[] aggroed = new boolean[MAX_ENEMIES];
    private final float[] enemyCooldown = new float[MAX_ENEMIES];
    int enemyCount;

    // Floor loot (chests placed at generation + drops); picked up by walking onto the tile.
    final int[] lootX = new int[LOOT_CAP];
    final int[] lootY = new int[LOOT_CAP];
    final int[] lootItem = new int[LOOT_CAP];
    final boolean[] lootChest = new boolean[LOOT_CAP];
    int lootCount;

    int playerX;
    int playerY;
    int floor;
    int playerHp;
    int playerMaxHp;
    int playerAtk;
    int playerLevel;
    int playerXp;
    int gold;
    int potions;
    int equippedWeapon;
    int equippedArmor;
    int equippedTrinket;
    final int[] inventory = new int[INVENTORY_SIZE];
    int inventoryCount;
    int inventorySelection;
    int merchantX;
    int merchantY;
    int state;
    int pauseSelection;
    boolean quitRequested;
    float attackProgress = 1f;

    private int previousX;
    private int previousY;
    private float moveProgress = 1f;
    private float enemyProgress;
    private int enemyStepParity;
    private float regenTimer;
    private long baseSeed;
    private boolean restartRequested;
    private boolean leftHeld;
    private boolean rightHeld;
    private boolean upHeld;
    private boolean downHeld;
    private boolean attackHeld;
    private boolean quaffHeld;
    private boolean buyHeld;
    private boolean enterHeld;
    private boolean escHeld;
    private boolean inventoryHeld;
    private boolean dropHeld;
    private boolean quaffRequested;
    private boolean buyRequested;
    private boolean talkRequested;
    private boolean equipRequested;
    private boolean dropRequested;

    Game() {
        startNewGame();
    }

    /** Flattens a tile coordinate into the row-major {@link #map} index. */
    static int index(int x, int y) {
        return y * MAP_WIDTH + x;
    }

    private void startNewGame() {
        floor = 1;
        playerMaxHp = 20;
        playerHp = 20;
        playerAtk = 4;
        playerLevel = 1;
        playerXp = 0;
        gold = 0;
        potions = 0;
        equippedWeapon = equippedArmor = equippedTrinket = -1;
        inventoryCount = 0;
        inventorySelection = 0;
        state = PLAYING;
        attackProgress = 1f;
        leftHeld = rightHeld = upHeld = downHeld = attackHeld = false;
        pauseSelection = 0;
        quaffHeld = buyHeld = enterHeld = escHeld = inventoryHeld = dropHeld = false;
        quaffRequested = buyRequested = talkRequested = equipRequested = dropRequested = false;
        regenTimer = 0f;
        baseSeed = random.nextLong();
        generate(false);
    }

    /** Effective attack including the equipped weapon. */
    int attackPower() {
        return playerAtk + (equippedWeapon >= 0 ? ITEM_VALUE[equippedWeapon] : 0);
    }

    /** Damage reduction from the equipped armor. */
    int defense() {
        return equippedArmor >= 0 ? ITEM_VALUE[equippedArmor] : 0;
    }

    private float regenInterval() {
        return equippedTrinket == TRINKET_COFFEE ? REGEN_INTERVAL_MS * 0.55f : REGEN_INTERVAL_MS;
    }

    private int fovRadius() {
        return FOV_RADIUS + (equippedTrinket == TRINKET_LANTERN ? 2 : 0);
    }

    /**
     * Records a key press for the current screen: held-movement and one-shot action
     * flags while playing, and the menu choices in the shop, pause, inventory, and
     * death states.
     */
    void keyDown(int code) {
        // Key events only set flags for the loop to drain; edge-detect so a held key fires once, not on OS repeat.
        boolean enterEdge = false;
        if (code == KeyEvent.VK_ENTER) {
            enterEdge = !enterHeld;
            enterHeld = true;
        }
        boolean escEdge = false;
        if (code == KeyEvent.VK_ESCAPE) {
            escEdge = !escHeld;
            escHeld = true;
        }
        boolean inventoryEdge = false;
        if (code == KeyEvent.VK_I) {
            inventoryEdge = !inventoryHeld;
            inventoryHeld = true;
        }
        if (state == DEAD) {
            if (code == KeyEvent.VK_SPACE || enterEdge) {
                restartRequested = true;
            } else if (escEdge) {
                quitRequested = true;
            }
            return;
        }
        if (state == SHOP) {
            if (enterEdge || escEdge) {
                state = PLAYING;
            } else if (code == KeyEvent.VK_B && !buyHeld) {
                buyRequested = true;
                buyHeld = true;
            }
            return;
        }
        if (state == PAUSED) {
            if (escEdge) {
                state = PLAYING;
            } else if (code == KeyEvent.VK_UP || code == KeyEvent.VK_W) {
                pauseSelection = 0;
            } else if (code == KeyEvent.VK_DOWN || code == KeyEvent.VK_S) {
                pauseSelection = 1;
            } else if (enterEdge) {
                if (pauseSelection == 0) {
                    state = PLAYING;
                } else {
                    quitRequested = true;
                }
            }
            return;
        }
        if (state == INVENTORY) {
            if (inventoryEdge || escEdge) {
                state = PLAYING;
            } else if ((code == KeyEvent.VK_UP || code == KeyEvent.VK_W) && inventorySelection > 0) {
                inventorySelection--;
            } else if ((code == KeyEvent.VK_DOWN || code == KeyEvent.VK_S) && inventorySelection < inventoryCount - 1) {
                inventorySelection++;
            } else if (enterEdge) {
                equipRequested = true;
            } else if (code == KeyEvent.VK_D && !dropHeld) {
                dropRequested = true;
                dropHeld = true;
            }
            return;
        }
        if (escEdge) {
            state = PAUSED;
            pauseSelection = 0;
            return;
        }
        if (inventoryEdge) {
            state = INVENTORY;
            inventorySelection = 0;
            return;
        }
        setHeld(code, true);
        if (code == KeyEvent.VK_Q && !quaffHeld) {
            quaffRequested = true;
            quaffHeld = true;
        }
        if (enterEdge) {
            talkRequested = true;
        }
    }

    /** Clears the held and edge state for a released key. */
    void keyUp(int code) {
        setHeld(code, false);
        if (code == KeyEvent.VK_B) {
            buyHeld = false;
        }
        if (code == KeyEvent.VK_Q) {
            quaffHeld = false;
        }
        if (code == KeyEvent.VK_ENTER) {
            enterHeld = false;
        }
        if (code == KeyEvent.VK_ESCAPE) {
            escHeld = false;
        }
        if (code == KeyEvent.VK_I) {
            inventoryHeld = false;
        }
        if (code == KeyEvent.VK_D) {
            dropHeld = false;
        }
    }

    private void setHeld(int code, boolean held) {
        switch (code) {
            case KeyEvent.VK_LEFT, KeyEvent.VK_A -> leftHeld = held;
            case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> rightHeld = held;
            case KeyEvent.VK_UP, KeyEvent.VK_W -> upHeld = held;
            case KeyEvent.VK_DOWN, KeyEvent.VK_S -> downHeld = held;
            case KeyEvent.VK_SPACE -> attackHeld = held;
        }
    }

    /**
     * Advances every real-time clock by the elapsed time and resolves what each
     * is due for: enemy contact damage and cooldowns each frame, the player's
     * spin attack, the enemy movement turn when its clock wraps, and a player
     * step when a direction is held and the previous slide has finished. Menu
     * states (dead, paused, shop, inventory) freeze the world.
     */
    void update(long deltaMillis) {
        if (state == DEAD) {
            if (restartRequested) {
                restartRequested = false;
                startNewGame();
            }
            return;
        }
        if (state == PAUSED) {
            return;
        }
        if (state == SHOP) {
            if (buyRequested) {
                buyRequested = false;
                buyPotion();
            }
            return;
        }
        if (state == INVENTORY) {
            if (equipRequested) {
                equipRequested = false;
                equipSelected();
            }
            if (dropRequested) {
                dropRequested = false;
                dropSelected();
            }
            return;
        }
        if (quaffRequested) {
            quaffRequested = false;
            quaff();
        }
        if (talkRequested) {
            talkRequested = false;
            if (adjacentToMerchant()) {
                state = SHOP;
                return;
            }
        }

        regenTimer += deltaMillis;
        if (regenTimer >= regenInterval()) {
            regenTimer -= regenInterval();
            if (playerHp < playerMaxHp) {
                playerHp++;
            }
        }

        enemyContacts(deltaMillis);
        if (state != PLAYING) {
            return;
        }

        if (attackProgress < 1f) {
            attackProgress = Math.min(1f, attackProgress + deltaMillis / ATTACK_DURATION_MS);
        } else if (attackHeld) {
            performAttack();
        }

        enemyProgress += deltaMillis / ENEMY_DURATION_MS;
        if (enemyProgress >= 1f) {
            enemyProgress = 0f;
            enemyStep();
        }

        if (moveProgress < 1f) {
            moveProgress = Math.min(1f, moveProgress + deltaMillis / MOVE_DURATION_MS);
            return;
        }
        int dx = (rightHeld ? 1 : 0) - (leftHeld ? 1 : 0);
        int dy = (downHeld ? 1 : 0) - (upHeld ? 1 : 0);
        if (dx != 0) {
            attemptMove(dx, 0);
        } else if (dy != 0) {
            attemptMove(0, dy);
        }
    }

    /**
     * Moves Duke if the target tile is open; enemies and walls block, stairs
     * change floor, traps bite, and any loot on the destination is picked up.
     */
    private void attemptMove(int dx, int dy) {
        int nextX = playerX + dx;
        int nextY = playerY + dy;
        if (!inBounds(nextX, nextY)) {
            return;
        }
        int targetTile = map[index(nextX, nextY)];
        if (targetTile == WALL || enemyAt(nextX, nextY) >= 0
                || (nextX == merchantX && nextY == merchantY)) {
            return;
        }
        if (targetTile == DOWN_STAIRS) {
            changeFloor(1, false);
            return;
        }
        if (targetTile == UP_STAIRS) {
            changeFloor(-1, true);
            return;
        }
        previousX = playerX;
        previousY = playerY;
        playerX = nextX;
        playerY = nextY;
        if (targetTile == TRAP) {
            playerHp -= trapDamage();
            if (playerHp <= 0) {
                playerHp = 0;
                state = DEAD;
                return;
            }
        }
        computeFieldOfView();
        pickUpAt(nextX, nextY);
        moveProgress = 0f;
    }

    private int trapDamage() {
        return 6 + floor;
    }

    /** A spinning slash that damages every enemy in the eight surrounding tiles. */
    private void performAttack() {
        attackProgress = 0f;
        for (int i = enemyCount - 1; i >= 0; i--) {
            int dx = Math.abs(enemyX[i] - playerX);
            int dy = Math.abs(enemyY[i] - playerY);
            if (Math.max(dx, dy) == 1) {
                enemyHp[i] -= Math.max(1, attackPower() + random.nextInt(3));
                enemyHit[i] = 1f;
                if (enemyHp[i] <= 0) {
                    killEnemy(i);
                }
            }
        }
    }

    private void killEnemy(int i) {
        int deadType = enemyType[i];
        int deadX = enemyX[i];
        int deadY = enemyY[i];
        gold += goldReward(deadType) + (equippedTrinket == TRINKET_COIN ? floor : 0);
        grantXp(xpReward(deadType));
        removeEnemy(i);
        if (deadType == FORKBOMB) {
            splitForkBomb(deadX, deadY);
        }
        if (deadType == DEADLOCK || random.nextInt(6) == 0) {
            spawnLoot(deadX, deadY, rollItem(random), false);
        }
    }

    /** A dying Fork Bomb spawns up to two plain Bugs on open neighbouring tiles. */
    private void splitForkBomb(int x, int y) {
        int spawned = 0;
        for (int d = 0; d < 4 && spawned < 2; d++) {
            int nx = x + DIR_X[d];
            int ny = y + DIR_Y[d];
            if (inBounds(nx, ny) && map[index(nx, ny)] != WALL
                    && !(nx == playerX && ny == playerY) && enemyAt(nx, ny) < 0) {
                addEnemy(nx, ny, BUG, true);
                spawned++;
            }
        }
    }

    private int enemyAt(int x, int y) {
        for (int i = 0; i < enemyCount; i++) {
            if (enemyX[i] == x && enemyY[i] == y) {
                return i;
            }
        }
        return -1;
    }

    private void addEnemy(int x, int y, int type, boolean aggro) {
        if (enemyCount >= MAX_ENEMIES) {
            return;
        }
        int slot = enemyCount++;
        enemyX[slot] = x;
        enemyY[slot] = y;
        enemyPrevX[slot] = x;
        enemyPrevY[slot] = y;
        enemyType[slot] = type;
        enemyHp[slot] = enemyMaxHp(type);
        enemyHit[slot] = 0f;
        aggroed[slot] = aggro;
        enemyCooldown[slot] = 0f;
    }

    private void removeEnemy(int i) {
        // Swap-remove: move the last live enemy into this slot. O(1), and ordering does not matter.
        enemyCount--;
        enemyX[i] = enemyX[enemyCount];
        enemyY[i] = enemyY[enemyCount];
        enemyType[i] = enemyType[enemyCount];
        enemyHp[i] = enemyHp[enemyCount];
        enemyPrevX[i] = enemyPrevX[enemyCount];
        enemyPrevY[i] = enemyPrevY[enemyCount];
        enemyHit[i] = enemyHit[enemyCount];
        aggroed[i] = aggroed[enemyCount];
        enemyCooldown[i] = enemyCooldown[enemyCount];
    }

    /**
     * One enemy movement turn, fired on the enemy clock: each enemy wakes if it can
     * see Duke, then steps toward him unless already adjacent. Deadlocks move only
     * on alternate steps, making them half-speed brutes.
     */
    private void enemyStep() {
        enemyStepParity ^= 1;
        for (int i = 0; i < enemyCount; i++) {
            enemyPrevX[i] = enemyX[i];
            enemyPrevY[i] = enemyY[i];
            int distance = distToPlayer(enemyX[i], enemyY[i]);
            // Sticky aggro: once seen in the light, an enemy keeps hunting even after slipping back into the dark.
            if (visible[index(enemyX[i], enemyY[i])]) {
                aggroed[i] = true;
            }
            boolean slow = enemyType[i] == DEADLOCK && enemyStepParity == 0;
            if (distance > 1 && aggroed[i] && !slow) {
                stepEnemyToward(i);
            }
        }
    }

    /**
     * Real-time per-frame upkeep for enemies: decays hit reactions and attack
     * cooldowns, and lets any enemy adjacent to Duke bite him on its own cadence
     * (reduced by armor), independent of how often it moves.
     */
    private void enemyContacts(long deltaMillis) {
        for (int i = 0; i < enemyCount; i++) {
            if (enemyHit[i] > 0f) {
                enemyHit[i] = Math.max(0f, enemyHit[i] - deltaMillis / HIT_DURATION_MS);
            }
            if (enemyCooldown[i] > 0f) {
                enemyCooldown[i] = Math.max(0f, enemyCooldown[i] - deltaMillis);
            }
            if (distToPlayer(enemyX[i], enemyY[i]) == 1 && enemyCooldown[i] <= 0f) {
                playerHp -= Math.max(1, enemyAttack(enemyType[i]) + random.nextInt(2) - defense());
                enemyCooldown[i] = ENEMY_ATTACK_MS;
                aggroed[i] = true;
            }
        }
        if (playerHp <= 0) {
            playerHp = 0;
            state = DEAD;
        }
    }

    private void stepEnemyToward(int i) {
        int dx = Integer.signum(playerX - enemyX[i]);
        int dy = Integer.signum(playerY - enemyY[i]);
        if (Math.abs(playerX - enemyX[i]) >= Math.abs(playerY - enemyY[i])) {
            if (!moveEnemy(i, dx, 0)) {
                moveEnemy(i, 0, dy);
            }
        } else if (!moveEnemy(i, 0, dy)) {
            moveEnemy(i, dx, 0);
        }
    }

    private boolean moveEnemy(int i, int dx, int dy) {
        if (dx == 0 && dy == 0) {
            return false;
        }
        int nextX = enemyX[i] + dx;
        int nextY = enemyY[i] + dy;
        if (!inBounds(nextX, nextY) || map[index(nextX, nextY)] == WALL || (nextX == playerX && nextY == playerY) || enemyAt(nextX, nextY) >= 0) {
            return false;
        }
        enemyX[i] = nextX;
        enemyY[i] = nextY;
        return true;
    }

    private int enemyMaxHp(int type) {
        return (switch (type) {
            case BUG -> 3;
            case NULLPTR -> 5;
            case LEAK -> 8;
            case FORKBOMB -> 6;
            default -> 16;
        }) + floor / 2;
    }

    private int enemyAttack(int type) {
        return (switch (type) {
            case BUG -> 1;
            case NULLPTR, FORKBOMB -> 2;
            case LEAK -> 3;
            default -> 4;
        }) + floor / 4;
    }

    private int rollType() {
        int roll = genRandom.nextInt(floor + 4);
        if (roll < 2) {
            return BUG;
        }
        if (roll < 4) {
            return NULLPTR;
        }
        if (roll < 6) {
            return LEAK;
        }
        return roll < 8 ? FORKBOMB : DEADLOCK;
    }

    private int goldReward(int type) {
        return (switch (type) {
            case BUG -> 2;
            case NULLPTR -> 4;
            case LEAK -> 7;
            case FORKBOMB -> 5;
            default -> 12;
        }) + floor;
    }

    private int xpReward(int type) {
        return (switch (type) {
            case BUG -> 4;
            case NULLPTR -> 8;
            case LEAK -> 14;
            case FORKBOMB -> 10;
            default -> 22;
        }) + floor;
    }

    /** Picks an item id biased toward better tiers on deeper floors. */
    private int rollItem(Random rng) {
        int roll = rng.nextInt(10);
        if (roll < 3) {
            return TRINKET_COFFEE + rng.nextInt(3);
        }
        int tier = Math.min(2, (floor - 1) / 4 + rng.nextInt(2));
        return (roll < 6 ? 0 : 3) + tier;
    }

    /** Adds experience and levels Duke up while he has enough, boosting his stats. */
    private void grantXp(int amount) {
        playerXp += amount;
        while (playerXp >= xpForNext()) {
            playerXp -= xpForNext();
            playerLevel++;
            playerMaxHp += HP_PER_LEVEL;
            playerHp = Math.min(playerMaxHp, playerHp + HP_PER_LEVEL);
            playerAtk++;
        }
    }

    int xpForNext() {
        return playerLevel * 25;
    }

    private void quaff() {
        if (potions > 0 && playerHp < playerMaxHp) {
            potions--;
            playerHp = Math.min(playerMaxHp, playerHp + playerMaxHp / 2);
        }
    }

    private void buyPotion() {
        if (gold >= POTION_COST) {
            gold -= POTION_COST;
            potions++;
        }
    }

    /** Equips the selected carried item, returning the displaced item to the bag. */
    private void equipSelected() {
        if (inventorySelection >= inventoryCount) {
            return;
        }
        int item = inventory[inventorySelection];
        int previous;
        switch (ITEM_SLOT[item]) {
            case WEAPON -> {
                previous = equippedWeapon;
                equippedWeapon = item;
            }
            case ARMOR -> {
                previous = equippedArmor;
                equippedArmor = item;
            }
            default -> {
                previous = equippedTrinket;
                equippedTrinket = item;
            }
        }
        if (previous >= 0) {
            inventory[inventorySelection] = previous;
        } else {
            removeInventory(inventorySelection);
        }
    }

    private void dropSelected() {
        if (inventorySelection >= inventoryCount) {
            return;
        }
        int item = inventory[inventorySelection];
        removeInventory(inventorySelection);
        spawnLoot(playerX, playerY, item, false);
    }

    private void removeInventory(int i) {
        for (int k = i; k < inventoryCount - 1; k++) {
            inventory[k] = inventory[k + 1];
        }
        inventoryCount--;
        if (inventorySelection >= inventoryCount && inventorySelection > 0) {
            inventorySelection--;
        }
    }

    private int lootAt(int x, int y) {
        for (int i = 0; i < lootCount; i++) {
            if (lootX[i] == x && lootY[i] == y) {
                return i;
            }
        }
        return -1;
    }

    private void spawnLoot(int x, int y, int item, boolean chest) {
        if (lootCount >= LOOT_CAP || lootAt(x, y) >= 0) {
            return;
        }
        lootX[lootCount] = x;
        lootY[lootCount] = y;
        lootItem[lootCount] = item;
        lootChest[lootCount] = chest;
        lootCount++;
    }

    private void removeLoot(int i) {
        lootCount--;
        lootX[i] = lootX[lootCount];
        lootY[i] = lootY[lootCount];
        lootItem[i] = lootItem[lootCount];
        lootChest[i] = lootChest[lootCount];
    }

    private void pickUpAt(int x, int y) {
        int i = lootAt(x, y);
        if (i >= 0 && inventoryCount < INVENTORY_SIZE) {
            inventory[inventoryCount++] = lootItem[i];
            removeLoot(i);
        }
    }

    /**
     * Moves between floors (delta +1 to descend, -1 to ascend), regenerating the
     * destination and placing Duke on the matching arrival stairs.
     */
    private void changeFloor(int delta, boolean arriveAtDownStairs) {
        floor += delta;
        generate(arriveAtDownStairs);
        state = PLAYING;
        leftHeld = rightHeld = upHeld = downHeld = attackHeld = false;
    }

    /** Duke's interpolated pixel position, blending his previous and current tile for smooth motion. */
    float renderPixelX() {
        return (previousX + (playerX - previousX) * moveProgress) * TILE;
    }

    float renderPixelY() {
        return (previousY + (playerY - previousY) * moveProgress) * TILE;
    }

    float enemyRenderPixelX(int i) {
        return (enemyPrevX[i] + (enemyX[i] - enemyPrevX[i]) * enemyProgress) * TILE;
    }

    float enemyRenderPixelY(int i) {
        return (enemyPrevY[i] + (enemyY[i] - enemyPrevY[i]) * enemyProgress) * TILE;
    }

    /** Top-left corner of the view in world pixels, kept centered on Duke. */
    float cameraX() {
        return renderPixelX() + TILE / 2f - PLAY_WIDTH / 2f;
    }

    float cameraY() {
        return renderPixelY() + TILE / 2f - PLAY_HEIGHT / 2f;
    }

    /**
     * Builds the current floor from its deterministic seed, so a floor looks
     * identical every time it is revisited. The arrival flag chooses whether
     * Duke lands on the up-stairs (descending into a floor) or the down-stairs
     * (climbing back up to it).
     */
    private void generate(boolean arriveAtDownStairs) {
        // Re-seed per floor so a level regenerates identically every time it is revisited.
        genRandom.setSeed(baseSeed + (long) floor * 0x9E3779B97F4A7C15L);
        for (int i = 0; i < map.length; i++) {
            map[i] = WALL;
            explored[i] = false;
        }

        int floorWidth = Math.min(MAP_WIDTH, BASE_MAP_W + (floor - 1) * MAP_GROW_W);
        int floorHeight = Math.min(MAP_HEIGHT, BASE_MAP_H + (floor - 1) * MAP_GROW_H);
        int roomTarget = Math.min(MAX_ROOMS, BASE_ROOMS + (floor - 1) * ROOM_GROW);

        int[] centerX = new int[MAX_ROOMS];
        int[] centerY = new int[MAX_ROOMS];
        int roomCount = 0;

        for (int attempt = 0; attempt < roomTarget * 3 && roomCount < roomTarget; attempt++) {
            int width = ROOM_MIN + genRandom.nextInt(ROOM_MAX - ROOM_MIN + 1);
            int height = ROOM_MIN + genRandom.nextInt(ROOM_MAX - ROOM_MIN + 1);
            int left = 1 + genRandom.nextInt(floorWidth - width - 1);
            int top = 1 + genRandom.nextInt(floorHeight - height - 1);
            if (regionTouched(left, top, width, height)) {
                continue;
            }
            carveRoom(left, top, width, height);
            int cx = left + width / 2;
            int cy = top + height / 2;
            if (roomCount > 0) {
                connect(centerX[roomCount - 1], centerY[roomCount - 1], cx, cy);
            }
            centerX[roomCount] = cx;
            centerY[roomCount] = cy;
            roomCount++;
        }

        int last = roomCount - 1;
        map[index(centerX[last], centerY[last])] = DOWN_STAIRS;
        if (floor > 1) {
            map[index(centerX[0], centerY[0])] = UP_STAIRS;
        }
        int arrival = arriveAtDownStairs ? last : 0;
        playerX = centerX[arrival];
        playerY = centerY[arrival];
        previousX = playerX;
        previousY = playerY;
        moveProgress = 1f;
        enemyProgress = 0f;
        lootCount = 0;
        computeFieldOfView();
        spawnEnemies(floorWidth, floorHeight);
        placeMerchant(floorWidth, floorHeight);
        placeChests(floorWidth, floorHeight);
        placeTraps(floorWidth, floorHeight);
    }

    private void placeMerchant(int floorWidth, int floorHeight) {
        merchantX = -1;
        merchantY = -1;
        for (int attempt = 0; attempt < 200; attempt++) {
            int x = 1 + genRandom.nextInt(floorWidth - 2);
            int y = 1 + genRandom.nextInt(floorHeight - 2);
            if (!isOpenTile(x, y) || enemyAt(x, y) >= 0) {
                continue;
            }
            if (distToPlayer(x, y) < 4) {
                continue;
            }
            merchantX = x;
            merchantY = y;
            return;
        }
    }

    private void placeChests(int floorWidth, int floorHeight) {
        int chests = Math.min(3, floor / 3);
        int placed = 0;
        for (int attempt = 0; attempt < chests * 30 && placed < chests; attempt++) {
            int x = 1 + genRandom.nextInt(floorWidth - 2);
            int y = 1 + genRandom.nextInt(floorHeight - 2);
            if (!openSpot(x, y)) {
                continue;
            }
            spawnLoot(x, y, rollItem(genRandom), true);
            placed++;
        }
    }

    private void placeTraps(int floorWidth, int floorHeight) {
        int traps = Math.min(8, floor / 2);
        int placed = 0;
        for (int attempt = 0; attempt < traps * 20 && placed < traps; attempt++) {
            int x = 1 + genRandom.nextInt(floorWidth - 2);
            int y = 1 + genRandom.nextInt(floorHeight - 2);
            if (!openSpot(x, y) || !isOpenTile(x, y) || distToPlayer(x, y) < 5) {
                continue;
            }
            map[index(x, y)] = TRAP;
            placed++;
        }
    }

    /** A free floor tile, clear of the merchant, enemies, existing loot, and Duke's landing spot. */
    private boolean openSpot(int x, int y) {
        return map[index(x, y)] == FLOOR && lootAt(x, y) < 0 && enemyAt(x, y) < 0
                && !(x == merchantX && y == merchantY)
                && distToPlayer(x, y) >= 4;
    }

    boolean adjacentToMerchant() {
        return merchantX >= 0 && distToPlayer(merchantX, merchantY) == 1;
    }

    /**
     * True only when the full 3x3 block around a tile is floor, so the merchant
     * lands in open room space rather than a corridor the player must pass through.
     */
    private boolean isOpenTile(int x, int y) {
        for (int ny = y - 1; ny <= y + 1; ny++) {
            for (int nx = x - 1; nx <= x + 1; nx++) {
                if (!inBounds(nx, ny) || map[index(nx, ny)] != FLOOR) {
                    return false;
                }
            }
        }
        return true;
    }

    private void spawnEnemies(int floorWidth, int floorHeight) {
        enemyCount = 0;
        int target = Math.min(MAX_ENEMIES, 1 + (int) (2.5 * Math.log(floor)));
        for (int attempt = 0; attempt < target * 20 && enemyCount < target; attempt++) {
            int x = 1 + genRandom.nextInt(floorWidth - 2);
            int y = 1 + genRandom.nextInt(floorHeight - 2);
            if (map[index(x, y)] != FLOOR || enemyAt(x, y) >= 0) {
                continue;
            }
            if (distToPlayer(x, y) < 6) {
                continue;
            }
            addEnemy(x, y, rollType(), false);
        }
    }

    private boolean regionTouched(int left, int top, int width, int height) {
        for (int y = top - 1; y <= top + height; y++) {
            for (int x = left - 1; x <= left + width; x++) {
                if (inBounds(x, y) && map[index(x, y)] != WALL) {
                    return true;
                }
            }
        }
        return false;
    }

    private void carveRoom(int left, int top, int width, int height) {
        for (int y = top; y < top + height; y++) {
            for (int x = left; x < left + width; x++) {
                map[index(x, y)] = FLOOR;
            }
        }
    }

    private void connect(int x1, int y1, int x2, int y2) {
        if (genRandom.nextBoolean()) {
            carveHorizontal(x1, x2, y1);
            carveVertical(y1, y2, x2);
        } else {
            carveVertical(y1, y2, x1);
            carveHorizontal(x1, x2, y2);
        }
    }

    private void carveHorizontal(int x1, int x2, int y) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            map[index(x, y)] = FLOOR;
        }
    }

    private void carveVertical(int y1, int y2, int x) {
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
            map[index(x, y)] = FLOOR;
        }
    }

    /**
     * Recomputes which tiles are visible from the player by casting a straight
     * line to every tile within the (trinket-boosted) view radius, stopping at
     * the first wall. Visible tiles are also marked permanently explored.
     */
    private void computeFieldOfView() {
        for (int i = 0; i < visible.length; i++) {
            visible[i] = false;
        }
        int radius = fovRadius();
        for (int y = playerY - radius; y <= playerY + radius; y++) {
            for (int x = playerX - radius; x <= playerX + radius; x++) {
                int dx = x - playerX;
                int dy = y - playerY;
                if (inBounds(x, y) && dx * dx + dy * dy <= radius * radius) {
                    castLight(x, y);
                }
            }
        }
    }

    private void castLight(int targetX, int targetY) {
        int x = playerX;
        int y = playerY;
        int dx = Math.abs(targetX - x);
        int dy = Math.abs(targetY - y);
        int stepX = x < targetX ? 1 : -1;
        int stepY = y < targetY ? 1 : -1;
        int error = dx - dy;
        while (true) {
            int idx = index(x, y);
            visible[idx] = true;
            explored[idx] = true;
            if (map[idx] == WALL || (x == targetX && y == targetY)) {
                return;
            }
            int doubled = 2 * error;
            if (doubled > -dy) {
                error -= dy;
                x += stepX;
            }
            if (doubled < dx) {
                error += dx;
                y += stepY;
            }
        }
    }

    private boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < MAP_WIDTH && y < MAP_HEIGHT;
    }

    /** Manhattan distance from Duke to a tile. */
    private int distToPlayer(int x, int y) {
        return Math.abs(playerX - x) + Math.abs(playerY - y);
    }
}
