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
    static final int HUD_HEIGHT = 72;
    static final int PLAY_WIDTH = 27 * TILE;
    static final int PLAY_HEIGHT = 19 * TILE;
    static final int VIEW_WIDTH = PLAY_WIDTH;
    static final int VIEW_HEIGHT = PLAY_HEIGHT + HUD_HEIGHT;

    static final int MAP_WIDTH = 45;
    static final int MAP_HEIGHT = 31;

    static final int WALL = 0;
    static final int FLOOR = 1;
    static final int DOWN_STAIRS = 2;
    static final int UP_STAIRS = 3;
    static final int TRAP = 4;
    static final int LOCKED_DOOR = 5;
    static final int PIT = 6;
    static final int SCENERY = 7;

    static final int PLAYING = 0;
    static final int DEAD = 1;
    static final int SHOP = 2;
    static final int PAUSED = 3;
    static final int INVENTORY = 4;

    static final int FACE_DOWN = 0;
    static final int FACE_UP = 1;
    static final int FACE_LEFT = 2;
    static final int FACE_RIGHT = 3;

    static final int BUG = 0;
    static final int NULLPTR = 1;
    static final int LEAK = 2;
    static final int FORKBOMB = 3;
    static final int DEADLOCK = 4;
    static final int MIMIC = 5;

    /** Expands a packed numeric table: one printable char per value, each char = value + '0'. */
    private static int[] unpack(String packed) {
        int[] table = new int[packed.length()];
        for (int i = 0; i < table.length; i++) {
            table[i] = packed.charAt(i) - '0';
        }
        return table;
    }

    /**
     * Per-enemy-type base stats {maxHp, attack, gold, xp, def} per type id, read as ENEMY_STATS[type*5+stat];
     * depth scaling is added at each call site. Packed one char per value (char = value + '0'); decoded rows by
     * type id: BUG 4,1,2,4,0 / NULLPTR 6,2,4,8,1 / LEAK 9,3,7,14,1 / FORKBOMB 7,2,5,10,1 /
     * DEADLOCK 17,4,12,22,2 / MIMIC 21,4,12,24,2.
     */
    private static final int[] ENEMY_STATS = unpack("4124062481937>1725:1A4<F2E4<H2");

    static final int MAX_ENEMIES = 32;
    static final int POTION_COST = 12;

    // Equipment effect types. EFF_NONE is plain gear; the rest layer a behaviour on top of the slot's base stat.
    static final int EFF_NONE = 0;
    static final int EFF_LIFESTEAL = 1;
    static final int EFF_CRIT = 2;
    static final int EFF_REACH = 3;
    static final int EFF_POISON = 4;
    static final int EFF_KNOCKBACK = 5;
    static final int EFF_THORNS = 6;
    static final int EFF_DODGE = 7;
    static final int EFF_HEAL_ON_KILL = 8;
    static final int EFF_REGEN = 9;
    static final int EFF_SIGHT = 10;
    static final int EFF_GOLD = 11;
    static final int EFF_XP = 12;
    static final int EFF_SPEED = 13;
    static final int EFF_KEYFIND = 14;

    static final int WEAPON = 0;
    static final int ARMOR = 1;
    static final int TRINKET = 2;

    /**
     * Item ids are indices into these parallel tables. ITEM_VALUE is the slot's base stat
     * (+ATK for weapons, +DEF for armor); ITEM_EFFECT/ITEM_EFFECT_MAG layer a special behaviour
     * on top, so base combat math stays untouched and plain gear is simply EFF_NONE.
     */
    private static final String[] ITEM_BASE_NAME = ("Debugger Blade|Null Sword|Refactor Axe|Garbage Collector|"
            + "Hot-Swap Dagger|Stack-Trace Spear|Venom Linter|Null-Cannon|Try-Catch Vest|Final Plate|Sandbox Shield|"
            + "Exception Mail|Async Cloak|Daemon Plate|Hot Coffee|Lantern|Lucky Coin|Profiler|Caffeine IV|Keyring").split("\\|");
    // Slot base stat per item id (char = value + '0'): weapons 3,5,8,4,4,5,4,6 / armor 2,4,6,3,2,4 / trinkets 0x6.
    private static final int[] ITEM_VALUE = unpack("35844546246324000000");
    // EFF_* id per item id (0-14): weapons NONE,NONE,NONE,LIFESTEAL,CRIT,REACH,POISON,KNOCKBACK /
    // armor NONE,NONE,NONE,THORNS,DODGE,HEAL_ON_KILL / trinkets REGEN,SIGHT,GOLD,XP,SPEED,KEYFIND.
    private static final int[] ITEM_EFFECT = unpack("000123450006789:;<=>");
    // Effect magnitude per item id: weapons 0,0,0,25,30,0,0,3 / armor 0,0,0,3,30,3 / trinkets 0,0,0,50,0,8.
    private static final int[] ITEM_EFFECT_MAG = unpack("000IN0030003N3000b08");

    static final int COMMON = 0;
    static final int RARE = 1;
    static final int LEGENDARY = 2;
    private static final String[] RARITY_LABEL = {"", " [Rare]", " [Legendary]"};
    // Flat stat added to ITEM_VALUE per tier; effect magnitudes are scaled separately.
    private static final int[] RARITY_STAT_BONUS = {0, 2, 5};
    // Multiplier numerator for effect magnitudes (denominator 10): ×1.0 / ×1.5 / ×2.5.
    private static final int[] RARITY_MAG_SCALE = {10, 15, 25};
    // Weapon ATK and armor DEF gain an uncapped bonus from the floor an item dropped on, scaled to
    // track enemy growth (enemy HP rises floor/2, enemy attack rises floor/4) so deep gear keeps pace.
    private static final int DEPTH_ATK_DIVISOR = 2;
    private static final int DEPTH_DEF_DIVISOR = 4;

    // Contiguous id ranges used by the loot roller; effect variants are the rarer half of each gear pool.
    private static final int WEAPON_EFFECT_FIRST = 3;
    private static final int WEAPON_EFFECT_COUNT = 5;
    private static final int ARMOR_PLAIN_FIRST = 8;
    private static final int ARMOR_EFFECT_FIRST = 11;
    private static final int ARMOR_EFFECT_COUNT = 3;
    private static final int TRINKET_FIRST = 14;
    private static final int TRINKET_COUNT = 6;

    private static final float POISON_DURATION_MS = 3000f;
    private static final float POISON_TICK_MS = 600f;
    private static final int POISON_DAMAGE = 2;

    // A boss guards every fifth floor: a large multi-tile guardian whose arena seals the stairs down.
    static final int BOSS_SIZE = 3;
    private static final int BOSS_FLOOR_INTERVAL = 5;
    private static final float BOSS_MOVE_MS = 430f;
    private static final float BOSS_ATTACK_MS = 1100f;
    private static final float BOSS_WINDUP_MS = 950f;
    private static final float BOSS_SHOCKWAVE_MS = 360f;
    // The slam only strikes the ring of tiles touching the footprint, so one step out of melee is safe.
    static final int BOSS_SLAM_RADIUS = 1;
    // The slam punctuates the regular bites: a cooldown spaces slams out so they stay a telegraphed threat.
    private static final int BOSS_SLAM_CHANCE = 55;
    private static final float BOSS_SLAM_COOLDOWN_MS = 2500f;
    private static final int BOSS_IDLE = 0;
    private static final int BOSS_WINDUP = 1;

    private static final int INVENTORY_SIZE = 8;
    private static final int LOOT_CAP = 16;

    private static final int FOV_RADIUS = 5;
    private static final int MAX_ROOMS = 20;
    private static final int ROOM_MIN = 4;
    private static final int ROOM_MAX = 8;
    // A vault's key is kept this many tiles from its door so it never sits in the same room.
    private static final int KEY_DOOR_MIN_DISTANCE = 9;
    private static final float MOVE_DURATION_MS = 228f;
    private static final float ENEMY_DURATION_MS = 450f;
    private static final float ENEMY_ATTACK_MS = 600f;
    private static final float ATTACK_DURATION_MS = 500f;
    private static final float HIT_DURATION_MS = 180f;
    private static final float REGEN_INTERVAL_MS = 6500f;
    private static final int HP_PER_LEVEL = 4;
    private static final int[] DIR_X = {0, 0, -1, 1};
    private static final int[] DIR_Y = {-1, 1, 0, 0};

    private static final float FALL_DURATION_MS = 900f;
    private static final float LIGHT_RISE_MS = 120f;
    private static final float LIGHT_FALL_MS = 200f;
    private static final int BREAK_CAP = 8;
    private static final float BREAK_MS = 350f;

    boolean falling;
    float fallProgress;
    private int fallFloors;

    final int[] breakX = new int[BREAK_CAP];
    final int[] breakY = new int[BREAK_CAP];
    final float[] breakTimer = new float[BREAK_CAP];
    int breakCount;

    private final Random random = new Random();
    // Generation uses its own seeded RNG so floor layouts are reproducible, independent of combat rolls.
    private final Random genRandom = new Random();
    // Each visited floor is cached as one packed int[] so revisiting restores it rather than regenerating.
    // Layout: an 11-int header, then 5 ints per enemy, 5 per loot, the map, and the explored mask.
    private static final int SNAP_ENEMY_BASE = 11;
    private static final int SNAP_LOOT_BASE = SNAP_ENEMY_BASE + MAX_ENEMIES * 5;
    private static final int SNAP_MAP_BASE = SNAP_LOOT_BASE + LOOT_CAP * 5;
    private static final int SNAP_EXPLORED_BASE = SNAP_MAP_BASE + MAP_WIDTH * MAP_HEIGHT;
    private static final int SNAP_SIZE = SNAP_EXPLORED_BASE + MAP_WIDTH * MAP_HEIGHT;
    // Direct-indexed by (floor - 1); avoids Integer boxing and HashMap Entry overhead.
    private static final int FLOOR_CACHE_CAP = 50;
    private final int[][] floorCache = new int[FLOOR_CACHE_CAP][];

    final int[] map = new int[MAP_WIDTH * MAP_HEIGHT];
    final boolean[] explored = new boolean[MAP_WIDTH * MAP_HEIGHT];
    final boolean[] visible = new boolean[MAP_WIDTH * MAP_HEIGHT];
    final float[] lightLevel = new float[MAP_WIDTH * MAP_HEIGHT];

    // Parallel primitive arrays per enemy slot — no entity objects, no per-enemy allocation.
    final int[] enemyX = new int[MAX_ENEMIES];
    final int[] enemyY = new int[MAX_ENEMIES];
    final int[] enemyType = new int[MAX_ENEMIES];
    final float[] enemyHit = new float[MAX_ENEMIES];
    final float[] enemyCrit = new float[MAX_ENEMIES];
    final float[] enemyPoison = new float[MAX_ENEMIES];
    final float[] enemyAttack = new float[MAX_ENEMIES];
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
    final boolean[] lootKey = new boolean[LOOT_CAP];
    int lootCount;

    int playerX;
    int playerY;
    int facing;
    int floor;
    int playerHp;
    int playerMaxHp;
    int playerAtk;
    int playerLevel;
    int playerXp;
    int gold;
    int potions;
    int keys;
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
    // Transient proc flashes on Duke, decayed each frame like enemyHit.
    float playerHeal;
    float playerDodge;

    // The arena boss is a single large entity held outside the per-tile enemy arrays.
    boolean bossActive;
    int bossType;
    int bossX;
    int bossY;
    int bossHp;
    int bossMaxHp;
    boolean bossEnraged;
    float bossHit;
    float bossWindup;
    float bossShockwave;
    float bossAnimTime;
    private int bossPrevX;
    private int bossPrevY;
    private int bossState;
    private float bossMoveProgress;
    private float bossAttackCooldown;
    private float bossSlamCooldown;

    private int vaultDoorX;
    private int vaultDoorY;
    private int previousX;
    private int previousY;
    private float moveProgress = 1f;
    private float enemyProgress;
    private int enemyStepParity;
    private float regenTimer;
    private long baseSeed;
    // Key-down state indexed by AWT key code; covers both held movement and edge-detected one-shots.
    private final boolean[] held = new boolean[256];
    // One-shot action requests set by keyDown() and consumed once by update(); indexed by REQ_*.
    private static final int REQ_RESTART = 0, REQ_QUAFF = 1, REQ_BUY = 2, REQ_TALK = 3, REQ_EQUIP = 4, REQ_DROP = 5;
    private final boolean[] req = new boolean[6];

    Game() {
        Sound.init();
        startNewGame();
    }

    /** Flattens a tile coordinate into the row-major {@link #map} index. */
    static int index(int x, int y) {
        return y * MAP_WIDTH + x;
    }

    /** Extracts the template id from a packed item int (bits 0-4). */
    static int itemId(int packed) { return packed & 0x1F; }

    /** Extracts the rarity tier from a packed item int (bits 5-6). The floor it dropped on is bits 7+. */
    static int itemRarity(int packed) { return (packed >> 5) & 0x3; }

    private void startNewGame() {
        floor = 1;
        playerMaxHp = 20;
        playerHp = 20;
        playerAtk = 4;
        playerLevel = 1;
        playerXp = 0;
        gold = 0;
        potions = 0;
        keys = 0;
        equippedWeapon = equippedArmor = equippedTrinket = -1;
        inventoryCount = 0;
        inventorySelection = 0;
        state = PLAYING;
        facing = FACE_DOWN;
        attackProgress = 1f;
        for (int i = 0; i < held.length; i++) held[i] = false;
        pauseSelection = 0;
        for (int i = 0; i < req.length; i++) req[i] = false;
        regenTimer = 0f;
        playerHeal = 0f;
        playerDodge = 0f;
        bossActive = false;
        falling = false;
        fallProgress = 0f;
        breakCount = 0;
        // Manual floop here instead of Arrays::fill saves on const pool bytes
        for (int i = 0; i < lightLevel.length; i++) lightLevel[i] = 0f;
        for (int i = 0; i < FLOOR_CACHE_CAP; i++) floorCache[i] = null;
        baseSeed = random.nextLong();
        generate(false);
    }

    /** Effective attack including the equipped weapon. */
    int attackPower() {
        return playerAtk + (equippedWeapon >= 0 ? itemValue(equippedWeapon) : 0);
    }

    /** Damage reduction from the equipped armor. */
    int defense() {
        return equippedArmor >= 0 ? itemValue(equippedArmor) : 0;
    }

    /** The effect of a packed equipped item, or EFF_NONE when the slot is empty (-1). */
    int effectOf(int packed) {
        return packed >= 0 ? ITEM_EFFECT[itemId(packed)] : EFF_NONE;
    }

    /** The rarity-scaled magnitude of a packed equipped item, or 0 when the slot is empty (-1). */
    int magOf(int packed) {
        return packed >= 0 ? itemMag(packed) : 0;
    }

    /** Percent chance the equipped weapon lands a critical hit, 0 when it has no crit effect. */
    int critChance() {
        return effectOf(equippedWeapon) == EFF_CRIT ? magOf(equippedWeapon) : 0;
    }

    /** Percent of damage dealt returned as health, 0 without a lifesteal weapon. */
    int lifestealPercent() {
        return effectOf(equippedWeapon) == EFF_LIFESTEAL ? magOf(equippedWeapon) : 0;
    }

    /** Percent chance the equipped armor evades an incoming bite, 0 without a dodge effect. */
    int dodgeChance() {
        return effectOf(equippedArmor) == EFF_DODGE ? magOf(equippedArmor) : 0;
    }

    int itemSlot(int packed)   { return slotOf(itemId(packed)); }
    int itemEffect(int packed) { return ITEM_EFFECT[itemId(packed)]; }

    /** Item slot from its id; items are laid out contiguously as weapons, then armor, then trinkets. */
    private static int slotOf(int id) { return id < 8 ? WEAPON : id < 14 ? ARMOR : TRINKET; }

    /** Slot base stat plus rarity bonus plus an uncapped depth bonus on weapons (ATK) and armor (DEF). */
    int itemValue(int packed) {
        int id = itemId(packed);
        int value = ITEM_VALUE[id] + RARITY_STAT_BONUS[itemRarity(packed)];
        if (slotOf(id) == WEAPON) {
            value += (packed >> 7) / DEPTH_ATK_DIVISOR;
        } else if (slotOf(id) == ARMOR) {
            value += (packed >> 7) / DEPTH_DEF_DIVISOR;
        }
        return value;
    }
    int itemMag(int packed)    { return ITEM_EFFECT_MAG[itemId(packed)] * RARITY_MAG_SCALE[itemRarity(packed)] / 10; }

    /** The item currently equipped in the same slot as {@code carried}, or -1 if that slot is empty. */
    int equippedCounterpart(int carried) {
        return switch (slotOf(itemId(carried))) {
            case WEAPON -> equippedWeapon;
            case ARMOR -> equippedArmor;
            default -> equippedTrinket;
        };
    }

    /** Human-readable label for an effect type (indexed by EFF_*); empty for plain gear. */
    private static final String[] EFFECT_LABEL = ("|Lifesteal|Crit|Reach|Poison|Knockback|Thorns|Dodge|"
            + "Heal on kill|Regen|Sight|Gold find|XP boost|Speed|Vault luck").split("\\|", -1);
    static String effectLabel(int effect) { return EFFECT_LABEL[effect]; }

    /** Builds the display string for a packed item: base name, scaled stats, scaled effect, and rarity label. */
    String itemName(int packed) {
        int id = itemId(packed);
        int rar = itemRarity(packed);
        int slot = slotOf(id);
        int val = itemValue(packed);
        StringBuilder name = new StringBuilder(ITEM_BASE_NAME[id]);
        if (slot == WEAPON) name.append("  +").append(val).append(" ATK");
        else if (slot == ARMOR) name.append("  +").append(val).append(" DEF");
        int eff = ITEM_EFFECT[id];
        if (eff != EFF_NONE) {
            name.append("  ").append(effectLabel(eff));
            int mag = itemMag(packed);
            if (mag > 0) {
                name.append(' ').append(mag);
                if (eff == EFF_CRIT || eff == EFF_LIFESTEAL || eff == EFF_DODGE || eff == EFF_KEYFIND || eff == EFF_XP)
                    name.append('%');
            }
        }
        if (rar > COMMON) name.append(RARITY_LABEL[rar]);
        return name.toString();
    }

    /** Decays a timer toward zero by the given amount without overshooting below zero. */
    private static float decay(float value, float amount) {
        return value > amount ? value - amount : 0f;
    }

    /** Heals Duke up to his maximum and flags a green proc flash when any health is actually restored. */
    private void healPlayer(int amount) {
        if (amount <= 0) {
            return;
        }
        int before = playerHp;
        playerHp = Math.min(playerMaxHp, playerHp + amount);
        if (playerHp > before) {
            playerHeal = 1f;
        }
    }

    /**
     * Records a key press for the current screen: held-movement and one-shot action
     * flags while playing, and the menu choices in the shop, pause, inventory, and
     * death states.
     */
    void keyDown(int code) {
        if (code < 0 || code >= held.length) {
            return;
        }
        // Edge-detect: a press only counts as "fresh" on the up→down transition, never on OS key-repeat.
        boolean fresh = !held[code];
        held[code] = true;
        // OS key-repeat re-fires keyDown, but the held flag is already recorded for movement, so a
        // non-fresh press has no one-shot work left. Gating here makes every check below a fresh edge.
        if (!fresh) {
            return;
        }
        if (code == KeyEvent.VK_M) {
            Sound.toggleMute();
            return;
        }
        if (code == KeyEvent.VK_T) {
            Sound.toggleMusicMute();
            return;
        }
        if (state == DEAD) {
            if (code == KeyEvent.VK_SPACE || code == KeyEvent.VK_ENTER) {
                req[REQ_RESTART] = true;
            } else if (code == KeyEvent.VK_ESCAPE) {
                quitRequested = true;
            }
            return;
        }
        if (state == SHOP) {
            if (code == KeyEvent.VK_Q || code == KeyEvent.VK_ESCAPE) {
                state = PLAYING;
            } else if (code == KeyEvent.VK_E) {
                req[REQ_BUY] = true;
            }
            return;
        }
        if (state == PAUSED) {
            if (code == KeyEvent.VK_ESCAPE) {
                state = PLAYING;
            } else if (code == KeyEvent.VK_W) {
                pauseSelection = 0;
            } else if (code == KeyEvent.VK_S) {
                pauseSelection = 1;
            } else if (code == KeyEvent.VK_E) {
                if (pauseSelection == 0) {
                    state = PLAYING;
                } else {
                    quitRequested = true;
                }
            }
            return;
        }
        if (state == INVENTORY) {
            if (code == KeyEvent.VK_Q || code == KeyEvent.VK_ESCAPE) {
                state = PLAYING;
            } else if (code == KeyEvent.VK_W && inventorySelection > 0) {
                inventorySelection--;
            } else if (code == KeyEvent.VK_S && inventorySelection < inventoryCount - 1) {
                inventorySelection++;
            } else if (code == KeyEvent.VK_E) {
                req[REQ_EQUIP] = true;
            } else if (code == KeyEvent.VK_D) {
                req[REQ_DROP] = true;
            }
            return;
        }
        if (code == KeyEvent.VK_ESCAPE) {
            state = PAUSED;
            pauseSelection = 0;
        }
        if (code == KeyEvent.VK_I) {
            state = INVENTORY;
            inventorySelection = 0;
        }
        if (code == KeyEvent.VK_Q) {
            req[REQ_QUAFF] = true;
        }
        if (code == KeyEvent.VK_E) {
            req[REQ_TALK] = true;
        }
    }

    /** Clears the key-down state for a released key so its next press edge-detects fresh. */
    void keyUp(int code) {
        if (code >= 0 && code < held.length) {
            held[code] = false;
        }
    }

    /** Reads and clears a one-shot request flag, returning whether it was set. */
    private boolean consume(int request) {
        boolean set = req[request];
        req[request] = false;
        return set;
    }

    /**
     * Advances every real-time clock by the elapsed time and resolves what each
     * is due for: enemy contact damage and cooldowns each frame, the player's
     * spin attack, the enemy movement turn when its clock wraps, and a player
     * step when a direction is held and the previous slide has finished. Menu
     * states (dead, paused, shop, inventory) freeze the world.
     */
    void update(long deltaMillis) {
        float rise = deltaMillis / LIGHT_RISE_MS;
        float fall = deltaMillis / LIGHT_FALL_MS;
        for (int i = 0; i < lightLevel.length; i++) {
            if (!explored[i]) continue;
            if (visible[i]) { if (lightLevel[i] < 1f) lightLevel[i] = Math.min(1f, lightLevel[i] + rise); }
            else             { if (lightLevel[i] > 0f) lightLevel[i] = Math.max(0f, lightLevel[i] - fall); }
        }
        if (state == DEAD) {
            if (consume(REQ_RESTART)) startNewGame();
            return;
        }
        if (state == PAUSED) {
            return;
        }
        if (state == SHOP) {
            if (consume(REQ_BUY)) buyPotion();
            return;
        }
        if (state == INVENTORY) {
            if (consume(REQ_EQUIP)) equipSelected();
            if (consume(REQ_DROP)) dropSelected();
            return;
        }
        for (int i = breakCount - 1; i >= 0; i--) {
            breakTimer[i] -= deltaMillis / BREAK_MS;
            if (breakTimer[i] <= 0f) {
                breakCount--;
                breakX[i] = breakX[breakCount];
                breakY[i] = breakY[breakCount];
                breakTimer[i] = breakTimer[breakCount];
            }
        }
        if (falling) {
            fallProgress = Math.min(1f, fallProgress + deltaMillis / FALL_DURATION_MS);
            if (fallProgress >= 1f) {
                falling = false;
                playerHp = Math.max(0, playerHp - fallFloors * 4);
                if (playerHp == 0) {
                    state = DEAD;
                    return;
                }
                changeFloor(fallFloors, false);
                placeAtRandom();
            }
            return;
        }
        if (consume(REQ_QUAFF)) quaff();
        if (consume(REQ_TALK)) {
            if (adjacentToMerchant()) {
                state = SHOP;
                return;
            }
            openAdjacentChest();
        }

        int trinket = effectOf(equippedTrinket);
        float regenInterval = trinket == EFF_REGEN ? REGEN_INTERVAL_MS * 0.55f : REGEN_INTERVAL_MS;
        regenTimer += deltaMillis;
        if (regenTimer >= regenInterval) {
            regenTimer -= regenInterval;
            if (playerHp < playerMaxHp) {
                playerHp++;
            }
        }
        playerHeal = decay(playerHeal, deltaMillis / HIT_DURATION_MS);
        playerDodge = decay(playerDodge, deltaMillis / HIT_DURATION_MS);

        enemyContacts(deltaMillis);
        updateBoss(deltaMillis);
        if (state != PLAYING) {
            return;
        }

        if (attackProgress < 1f) {
            float attackDuration = trinket == EFF_SPEED ? ATTACK_DURATION_MS * 0.8f : ATTACK_DURATION_MS;
            attackProgress = Math.min(1f, attackProgress + deltaMillis / attackDuration);
        } else if (held[KeyEvent.VK_SPACE]) {
            performAttack();
        }

        enemyProgress += deltaMillis / ENEMY_DURATION_MS;
        if (enemyProgress >= 1f) {
            enemyProgress = 0f;
            enemyStep();
        }

        if (moveProgress < 1f) {
            float moveDuration = trinket == EFF_SPEED ? MOVE_DURATION_MS * 0.7f : MOVE_DURATION_MS;
            moveProgress = Math.min(1f, moveProgress + deltaMillis / moveDuration);
            return;
        }
        int moveX = (held[KeyEvent.VK_D] ? 1 : 0) - (held[KeyEvent.VK_A] ? 1 : 0);
        int moveY = (held[KeyEvent.VK_S] ? 1 : 0) - (held[KeyEvent.VK_W] ? 1 : 0);
        if (moveX != 0) {
            attemptMove(moveX, 0);
        } else if (moveY != 0) {
            attemptMove(0, moveY);
        }
    }

    /**
     * Moves Duke if the target tile is open; enemies and walls block, stairs
     * change floor, traps bite, and any loot on the destination is picked up.
     */
    private void attemptMove(int dx, int dy) {
        if (dx < 0) {
            facing = FACE_LEFT;
        } else if (dx > 0) {
            facing = FACE_RIGHT;
        } else if (dy < 0) {
            facing = FACE_UP;
        } else {
            facing = FACE_DOWN;
        }
        int nextX = playerX + dx;
        int nextY = playerY + dy;
        if (!inBounds(nextX, nextY)) {
            return;
        }
        int targetTile = map[index(nextX, nextY)];
        if (targetTile == LOCKED_DOOR) {
            if (keys > 0) {
                keys--;
                map[index(nextX, nextY)] = FLOOR;
                computeFieldOfView();
                Sound.doorUnlock();
            }
            return;
        }
        if (targetTile == WALL || targetTile == SCENERY || enemyAt(nextX, nextY) >= 0
                || bossOccupies(nextX, nextY) || (nextX == merchantX && nextY == merchantY)) {
            return;
        }
        if (targetTile == DOWN_STAIRS) {
            if (bossActive) {
                return;
            }
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
            playerHp -= 6 + floor;
            if (playerHp <= 0) {
                playerHp = 0;
                state = DEAD;
                return;
            }
        }
        computeFieldOfView();
        pickUpAt(nextX, nextY);
        if (targetTile == PIT) {
            falling = true;
            fallProgress = 0f;
            fallFloors = 1 + random.nextInt(3);
            moveProgress = 1f;
            Sound.pitFall();
            return;
        }
        Sound.footstep();
        moveProgress = 0f;
    }

    /**
     * A spinning slash that damages every enemy in the surrounding tiles (two rings out with a reach
     * weapon), applying the equipped weapon's effect — crit, lifesteal, poison, or knockback — to each hit.
     */
    private void performAttack() {
        attackProgress = 0f;
        Sound.swordAttack();
        int effect = effectOf(equippedWeapon);
        int reach = effect == EFF_REACH ? 2 : 1;
        for (int i = enemyCount - 1; i >= 0; i--) {
            int offsetX = enemyX[i] - playerX;
            int offsetY = enemyY[i] - playerY;
            int ring = Math.max(Math.abs(offsetX), Math.abs(offsetY));
            if (ring < 1 || ring > reach) {
                continue;
            }
            int damage = Math.max(1, attackPower() + random.nextInt(3) - ENEMY_STATS[enemyType[i] * 5 + 4] - floor / 6);
            if (effect == EFF_CRIT && random.nextInt(100) < magOf(equippedWeapon)) {
                damage *= 2;
                enemyCrit[i] = 1f;
            }
            enemyHp[i] -= damage;
            enemyHit[i] = 1f;
            if (effect == EFF_LIFESTEAL) {
                healPlayer(Math.max(1, damage * magOf(equippedWeapon) / 100));
            } else if (effect == EFF_POISON) {
                enemyPoison[i] = POISON_DURATION_MS;
            } else if (effect == EFF_KNOCKBACK) {
                knockbackEnemy(i, offsetX, offsetY);
            }
            if (enemyHp[i] <= 0) {
                killEnemy(i);
            }
        }
        if (bossActive) {
            int distance = bossDistanceToPlayer();
            if (distance >= 1 && distance <= reach) {
                int damage = Math.max(1, attackPower() + random.nextInt(3));
                if (effect == EFF_CRIT && random.nextInt(100) < magOf(equippedWeapon)) {
                    damage *= 2;
                }
                if (effect == EFF_LIFESTEAL) {
                    healPlayer(Math.max(1, damage * magOf(equippedWeapon) / 100));
                }
                damageBoss(damage);
            }
        }
        for (int attackDy = -reach; attackDy <= reach; attackDy++) {
            for (int attackDx = -reach; attackDx <= reach; attackDx++) {
                if (Math.max(Math.abs(attackDx), Math.abs(attackDy)) < 1) continue;
                int targetX = playerX + attackDx, targetY = playerY + attackDy;
                if (inBounds(targetX, targetY) && map[index(targetX, targetY)] == SCENERY) {
                    breakScenery(targetX, targetY);
                }
            }
        }
    }

    private void breakScenery(int x, int y) {
        map[index(x, y)] = FLOOR;
        Sound.sceneryBreak();
        if (random.nextInt(100) < 20) {
            spawnLoot(x, y, rollItem(random, 0), false);
        }
        if (breakCount < BREAK_CAP) {
            breakX[breakCount] = x;
            breakY[breakCount] = y;
            breakTimer[breakCount++] = 1f;
        }
    }

    /**
     * Shoves an enemy one tile directly away from Duke along its dominant axis; the existing render
     * interpolation animates the slide. A shove into a wall, Duke, or another enemy lands as impact damage.
     */
    private void knockbackEnemy(int i, int offsetX, int offsetY) {
        int pushX = Integer.signum(offsetX);
        int pushY = Integer.signum(offsetY);
        if (Math.abs(offsetX) >= Math.abs(offsetY)) {
            pushY = 0;
        } else {
            pushX = 0;
        }
        int nextX = enemyX[i] + pushX;
        int nextY = enemyY[i] + pushY;
        if (inBounds(nextX, nextY) && map[index(nextX, nextY)] != WALL
                && !(nextX == playerX && nextY == playerY) && enemyAt(nextX, nextY) < 0) {
            enemyPrevX[i] = enemyX[i];
            enemyPrevY[i] = enemyY[i];
            enemyX[i] = nextX;
            enemyY[i] = nextY;
        } else {
            enemyHp[i] -= magOf(equippedWeapon);
            enemyHit[i] = 1f;
        }
    }

    private void killEnemy(int i) {
        int deadType = enemyType[i];
        int deadX = enemyX[i];
        int deadY = enemyY[i];
        gold += ENEMY_STATS[deadType * 5 + 2] + floor + (effectOf(equippedTrinket) == EFF_GOLD ? floor : 0);
        grantXp(ENEMY_STATS[deadType * 5 + 3] + floor);
        if (effectOf(equippedArmor) == EFF_HEAL_ON_KILL) {
            healPlayer(magOf(equippedArmor));
        }
        removeEnemy(i);
        if (deadType == FORKBOMB) {
            splitForkBomb(deadX, deadY);
        }
        if (deadType == MIMIC) {
            spawnLoot(deadX, deadY, rollItem(random, 0, true), false);
        } else if (deadType == DEADLOCK || random.nextInt(6) == 0) {
            spawnLoot(deadX, deadY, rollItem(random, 0), false);
        }
    }

    /** A dying Fork Bomb spawns up to two plain Bugs on open neighbouring tiles. */
    private void splitForkBomb(int x, int y) {
        int spawned = 0;
        for (int dir = 0; dir < 4 && spawned < 2; dir++) {
            int neighborX = x + DIR_X[dir];
            int neighborY = y + DIR_Y[dir];
            if (inBounds(neighborX, neighborY) && map[index(neighborX, neighborY)] != WALL
                    && !(neighborX == playerX && neighborY == playerY) && enemyAt(neighborX, neighborY) < 0) {
                addEnemy(neighborX, neighborY, BUG, true);
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
        enemyHp[slot] = ENEMY_STATS[type * 5] + floor / 2;
        enemyHit[slot] = 0f;
        enemyCrit[slot] = 0f;
        enemyPoison[slot] = 0f;
        enemyAttack[slot] = 0f;
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
        enemyCrit[i] = enemyCrit[enemyCount];
        enemyPoison[i] = enemyPoison[enemyCount];
        enemyAttack[i] = enemyAttack[enemyCount];
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
        // Iterate downward: poison ticks and thorns can kill mid-loop, and killEnemy swap-removes the slot.
        for (int i = enemyCount - 1; i >= 0; i--) {
            enemyHit[i] = decay(enemyHit[i], deltaMillis / HIT_DURATION_MS);
            enemyCrit[i] = decay(enemyCrit[i], deltaMillis / HIT_DURATION_MS);
            enemyAttack[i] = decay(enemyAttack[i], deltaMillis / 220f);
            enemyCooldown[i] = decay(enemyCooldown[i], deltaMillis);
            if (enemyPoison[i] > 0f) {
                tickPoison(i, deltaMillis);
                if (enemyHp[i] <= 0) {
                    killEnemy(i);
                    continue;
                }
            }
            if (distToPlayer(enemyX[i], enemyY[i]) == 1 && enemyCooldown[i] <= 0f) {
                enemyCooldown[i] = ENEMY_ATTACK_MS;
                enemyAttack[i] = 1f;
                aggroed[i] = true;
                if (damagePlayer(ENEMY_STATS[enemyType[i] * 5 + 1] + floor / 4 + random.nextInt(2))
                        && effectOf(equippedArmor) == EFF_THORNS) {
                    enemyHp[i] -= magOf(equippedArmor);
                    enemyHit[i] = 1f;
                    if (enemyHp[i] <= 0) {
                        killEnemy(i);
                    }
                }
            }
        }
    }

    /**
     * Advances an enemy's poison timer and deals a fixed bite of damage for every tick interval it crosses,
     * giving it a small hop so the green tint pulses; the kill itself is resolved by the caller.
     */
    private void tickPoison(int i, long deltaMillis) {
        float before = enemyPoison[i];
        float after = Math.max(0f, before - deltaMillis);
        enemyPoison[i] = after;
        int ticks = (int) (before / POISON_TICK_MS) - (int) (after / POISON_TICK_MS);
        if (ticks > 0) {
            enemyHp[i] -= ticks * POISON_DAMAGE;
            enemyHit[i] = Math.max(enemyHit[i], 0.5f);
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
        if (!inBounds(nextX, nextY)) {
            return false;
        }
        if (blocksMovement(map[index(nextX, nextY)])
                || (nextX == playerX && nextY == playerY) || enemyAt(nextX, nextY) >= 0) {
            return false;
        }
        enemyX[i] = nextX;
        enemyY[i] = nextY;
        return true;
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

    /**
     * Picks a packed item int: rolls rarity (floor-biased toward higher tiers at depth), then picks the
     * template. Trinkets and effect-bearing gear are the rarer slice; plain gear tier scales with floor.
     */
    private int rollItem(Random rng, int rarityBonus) { return rollItem(rng, rarityBonus, false); }

    private int rollItem(Random rng, int rarityBonus, boolean minRare) {
        int legendary = Math.max(0, Math.min(25, (floor - 6) * 4));
        int rare = Math.min(55, floor * 2 + rarityBonus);
        int rv = rng.nextInt(100);
        int rarity = minRare
                ? (rv < legendary ? LEGENDARY : RARE)
                : (rv < legendary ? LEGENDARY : rv < legendary + rare ? RARE : COMMON);

        int roll = rng.nextInt(100);
        int id;
        if (roll < 28) {
            id = TRINKET_FIRST + rng.nextInt(TRINKET_COUNT);
        } else {
            boolean weapon = roll < 64;
            if (rng.nextInt(3) == 0) {
                id = weapon ? WEAPON_EFFECT_FIRST + rng.nextInt(WEAPON_EFFECT_COUNT)
                            : ARMOR_EFFECT_FIRST + rng.nextInt(ARMOR_EFFECT_COUNT);
            } else {
                int tier = Math.min(2, (floor - 1) / 4 + rng.nextInt(2));
                id = (weapon ? 0 : ARMOR_PLAIN_FIRST) + tier;
            }
        }
        return (floor << 7) | (rarity << 5) | id;
    }

    /** Adds experience and levels Duke up while he has enough, boosting his stats. */
    private void grantXp(int amount) {
        if (effectOf(equippedTrinket) == EFF_XP) {
            amount += amount * magOf(equippedTrinket) / 100;
        }
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
        switch (slotOf(itemId(item))) {
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

    /** Permanently discards the selected carried item; dropped gear is destroyed, not left on the floor. */
    private void dropSelected() {
        if (inventorySelection < inventoryCount) {
            removeInventory(inventorySelection);
        }
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
        lootKey[lootCount] = false;
        lootCount++;
    }

    /** Drops a key pickup, the resource that opens sealed vault doors. */
    private void spawnKey(int x, int y) {
        if (lootCount >= LOOT_CAP || lootAt(x, y) >= 0) {
            return;
        }
        lootX[lootCount] = x;
        lootY[lootCount] = y;
        lootItem[lootCount] = 0;
        lootChest[lootCount] = false;
        lootKey[lootCount] = true;
        lootCount++;
    }

    private void removeLoot(int i) {
        lootCount--;
        lootX[i] = lootX[lootCount];
        lootY[i] = lootY[lootCount];
        lootItem[i] = lootItem[lootCount];
        lootChest[i] = lootChest[lootCount];
        lootKey[i] = lootKey[lootCount];
    }

    private void pickUpAt(int x, int y) {
        int i = lootAt(x, y);
        if (i < 0) return;
        if (lootKey[i]) {
            keys++;
            Sound.keyPickup();
            removeLoot(i);
        } else if (!lootChest[i] && inventoryCount < INVENTORY_SIZE) {
            inventory[inventoryCount++] = lootItem[i];
            removeLoot(i);
        }
    }

    private void openAdjacentChest() {
        for (int i = 0; i < lootCount; i++) {
            if (lootChest[i] && Math.abs(lootX[i] - playerX) + Math.abs(lootY[i] - playerY) <= 1) {
                int chestX = lootX[i], chestY = lootY[i];
                if (random.nextInt(100) < 15) {
                    removeLoot(i);
                    int mimicX = chestX, mimicY = chestY;
                    if (mimicX == playerX && mimicY == playerY) {
                        for (int dir = 0; dir < 4; dir++) {
                            int neighborX = chestX + DIR_X[dir], neighborY = chestY + DIR_Y[dir];
                            if (inBounds(neighborX, neighborY) && map[index(neighborX, neighborY)] == FLOOR && enemyAt(neighborX, neighborY) < 0) {
                                mimicX = neighborX; mimicY = neighborY; break;
                            }
                        }
                    }
                    addEnemy(mimicX, mimicY, MIMIC, true);
                    Sound.mimicReveal();
                } else if (inventoryCount < INVENTORY_SIZE) {
                    inventory[inventoryCount++] = lootItem[i];
                    removeLoot(i);
                }
                return;
            }
        }
    }

    /**
     * Moves between floors (delta +1 to descend, -1 to ascend). The floor being left is cached so its
     * opened doors, taken chests, slain enemies, and felled boss persist; a previously visited
     * destination is restored from that cache, and a fresh floor is generated only on a first visit.
     */
    private void changeFloor(int delta, boolean arriveAtDownStairs) {
        Sound.stairs();
        saveFloor();
        floor += delta;
        // Start the new floor dark so its lit area fades in rather than snapping on arrival.
        // Manual floop here instead of Arrays::fill saves on const pool bytes
        for (int i = 0; i < lightLevel.length; i++) lightLevel[i] = 0f;
        if (floor >= 1 && floor <= FLOOR_CACHE_CAP && floorCache[floor - 1] != null) {
            restoreFloor(arriveAtDownStairs);
        } else {
            generate(arriveAtDownStairs);
        }
        state = PLAYING;
        for (int i = 0; i < held.length; i++) held[i] = false;
    }

    /**
     * Snapshots the current floor into one packed int[] in the cache, keyed by floor number. Only
     * durable state is stored; transient combat clocks and flashes are recreated on restore.
     */
    private void saveFloor() {
        if (floor < 1 || floor > FLOOR_CACHE_CAP) return;
        if (floorCache[floor - 1] == null) floorCache[floor - 1] = new int[SNAP_SIZE];
        int[] snapshot = floorCache[floor - 1];
        snapshot[0] = enemyCount;
        snapshot[1] = lootCount;
        snapshot[2] = merchantX;
        snapshot[3] = merchantY;
        snapshot[4] = bossActive ? 1 : 0;
        snapshot[5] = bossEnraged ? 1 : 0;
        snapshot[6] = bossType;
        snapshot[7] = bossX;
        snapshot[8] = bossY;
        snapshot[9] = bossHp;
        snapshot[10] = bossMaxHp;
        for (int i = 0; i < enemyCount; i++) {
            int offset = SNAP_ENEMY_BASE + i * 5;
            snapshot[offset] = enemyX[i];
            snapshot[offset + 1] = enemyY[i];
            snapshot[offset + 2] = enemyType[i];
            snapshot[offset + 3] = enemyHp[i];
            snapshot[offset + 4] = aggroed[i] ? 1 : 0;
        }
        for (int i = 0; i < lootCount; i++) {
            int offset = SNAP_LOOT_BASE + i * 5;
            snapshot[offset] = lootX[i];
            snapshot[offset + 1] = lootY[i];
            snapshot[offset + 2] = lootItem[i];
            snapshot[offset + 3] = lootChest[i] ? 1 : 0;
            snapshot[offset + 4] = lootKey[i] ? 1 : 0;
        }
        System.arraycopy(map, 0, snapshot, SNAP_MAP_BASE, map.length);
        for (int i = 0; i < explored.length; i++) {
            snapshot[SNAP_EXPLORED_BASE + i] = explored[i] ? 1 : 0;
        }
    }

    /** Restores a cached floor from its packed snapshot and drops Duke onto the arrival stairs. */
    private void restoreFloor(boolean arriveAtDownStairs) {
        int[] snapshot = floorCache[floor - 1];
        enemyCount = snapshot[0];
        lootCount = snapshot[1];
        merchantX = snapshot[2];
        merchantY = snapshot[3];
        bossActive = snapshot[4] != 0;
        bossEnraged = snapshot[5] != 0;
        bossType = snapshot[6];
        bossX = snapshot[7];
        bossY = snapshot[8];
        bossHp = snapshot[9];
        bossMaxHp = snapshot[10];
        for (int i = 0; i < enemyCount; i++) {
            int offset = SNAP_ENEMY_BASE + i * 5;
            enemyX[i] = snapshot[offset];
            enemyY[i] = snapshot[offset + 1];
            enemyType[i] = snapshot[offset + 2];
            enemyHp[i] = snapshot[offset + 3];
            aggroed[i] = snapshot[offset + 4] != 0;
            enemyPrevX[i] = enemyX[i];
            enemyPrevY[i] = enemyY[i];
            enemyHit[i] = 0f;
            enemyCrit[i] = 0f;
            enemyPoison[i] = 0f;
            enemyCooldown[i] = 0f;
        }
        for (int i = 0; i < lootCount; i++) {
            int offset = SNAP_LOOT_BASE + i * 5;
            lootX[i] = snapshot[offset];
            lootY[i] = snapshot[offset + 1];
            lootItem[i] = snapshot[offset + 2];
            lootChest[i] = snapshot[offset + 3] != 0;
            lootKey[i] = snapshot[offset + 4] != 0;
        }
        System.arraycopy(snapshot, SNAP_MAP_BASE, map, 0, map.length);
        for (int i = 0; i < explored.length; i++) {
            explored[i] = snapshot[SNAP_EXPLORED_BASE + i] != 0;
        }
        bossPrevX = bossX;
        bossPrevY = bossY;
        bossState = BOSS_IDLE;
        bossWindup = 0f;
        bossShockwave = 0f;
        bossAnimTime = 0f;
        bossHit = 0f;
        bossMoveProgress = 0f;
        bossAttackCooldown = BOSS_ATTACK_MS;
        bossSlamCooldown = BOSS_SLAM_COOLDOWN_MS;
        moveProgress = 1f;
        enemyProgress = 0f;
        attackProgress = 1f;
        placeAtStairs(arriveAtDownStairs);
    }

    /** Drops Duke onto a random open floor tile after a pit fall. */
    private void placeAtRandom() {
        for (int attempt = 0; attempt < 200; attempt++) {
            int x = 1 + random.nextInt(MAP_WIDTH - 2);
            int y = 1 + random.nextInt(MAP_HEIGHT - 2);
            if (map[index(x, y)] == FLOOR && enemyAt(x, y) < 0
                    && !bossOccupies(x, y) && !(x == merchantX && y == merchantY)) {
                playerX = x; playerY = y;
                previousX = x; previousY = y;
                computeFieldOfView();
                return;
            }
        }
    }

    /** Lands Duke on this floor's down stairs (when climbing back down) or up stairs (when descending). */
    private void placeAtStairs(boolean atDownStairs) {
        int target = atDownStairs ? DOWN_STAIRS : UP_STAIRS;
        for (int y = 0; y < MAP_HEIGHT; y++) {
            for (int x = 0; x < MAP_WIDTH; x++) {
                if (map[index(x, y)] == target) {
                    playerX = x;
                    playerY = y;
                    previousX = x;
                    previousY = y;
                    computeFieldOfView();
                    return;
                }
            }
        }
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

        bossActive = false;
        if (isBossFloor()) {
            generateArena(arriveAtDownStairs);
            return;
        }

        int floorWidth = Math.min(MAP_WIDTH, 20 + (floor - 1) * 5);
        int floorHeight = Math.min(MAP_HEIGHT, 14 + (floor - 1) * 3);
        int roomTarget = Math.min(MAX_ROOMS, 4 + (floor - 1) * 2);

        int[] centerX = new int[MAX_ROOMS];
        int[] centerY = new int[MAX_ROOMS];
        int roomCount = 0;

        for (int attempt = 0; attempt < roomTarget * 10 && roomCount < roomTarget; attempt++) {
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
        if (floor > 1) {
            map[index(centerX[0], centerY[0])] = UP_STAIRS;
        }
        int downX = centerX[last];
        int downY = centerY[last];
        if (downX == centerX[0] && downY == centerY[0]) {
            downX++;
        }
        map[index(downX, downY)] = DOWN_STAIRS;
        if (arriveAtDownStairs) {
            playerX = downX;
            playerY = downY;
        } else {
            playerX = centerX[0];
            playerY = centerY[0];
        }
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
        placePits(floorWidth, floorHeight);
        placeScenery(floorWidth, floorHeight, roomCount);
        if (placeVault(floorWidth, floorHeight)) {
            placeFloorKey(floorWidth, floorHeight);
        }
    }

    /**
     * Carves a sealed treasure vault into solid rock and gates it with a single locked door, returning
     * whether one was placed. The vault is a 3x3 room reached only through the door, so the key the floor
     * also drops is the sole way in. Vaults begin appearing on the second floor.
     */
    private boolean placeVault(int floorWidth, int floorHeight) {
        if (floor < 2 || genRandom.nextInt(100) >= vaultChancePercent()) {
            return false;
        }
        for (int attempt = 0; attempt < 160; attempt++) {
            int anchorX = randInterior(floorWidth);
            int anchorY = randInterior(floorHeight);
            if (map[index(anchorX, anchorY)] != FLOOR) {
                continue;
            }
            int dir = genRandom.nextInt(4);
            int forwardX = DIR_X[dir];
            int forwardY = DIR_Y[dir];
            int sideX = DIR_Y[dir];
            int sideY = DIR_X[dir];
            if (vaultRegionClear(anchorX, anchorY, forwardX, forwardY, sideX, sideY)) {
                carveVault(anchorX, anchorY, forwardX, forwardY, sideX, sideY);
                return true;
            }
        }
        return false;
    }

    /** Vault odds: 25% on the second floor, climbing 7% per floor with depth and capped at 80%. Keyring adds a small flat bonus. */
    private int vaultChancePercent() {
        int bonus = effectOf(equippedTrinket) == EFF_KEYFIND ? magOf(equippedTrinket) : 0;
        return Math.min(80, 25 + (floor - 2) * 7 + bonus);
    }

    /**
     * True only when the door tile and the whole 3x3 vault footprint sit in untouched rock with a solid
     * wall border, guaranteeing the carved vault connects to the dungeon through its door alone.
     */
    private boolean vaultRegionClear(int ax, int ay, int forwardX, int forwardY, int sideX, int sideY) {
        for (int along = 1; along <= 5; along++) {
            for (int across = -2; across <= 2; across++) {
                int x = ax + forwardX * along + sideX * across;
                int y = ay + forwardY * along + sideY * across;
                if (!inBounds(x, y) || map[index(x, y)] != WALL) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Opens the door, carves the 3x3 room, and fills it with three chests of floor-tier loot. */
    private void carveVault(int ax, int ay, int forwardX, int forwardY, int sideX, int sideY) {
        vaultDoorX = ax + forwardX;
        vaultDoorY = ay + forwardY;
        map[index(vaultDoorX, vaultDoorY)] = LOCKED_DOOR;
        for (int along = 2; along <= 4; along++) {
            for (int across = -1; across <= 1; across++) {
                map[index(ax + forwardX * along + sideX * across, ay + forwardY * along + sideY * across)] = FLOOR;
            }
        }
        spawnLoot(ax + forwardX * 3, ay + forwardY * 3, rollItem(genRandom, 0), true);
        if (genRandom.nextBoolean())
            spawnLoot(ax + forwardX * 4 + sideX, ay + forwardY * 4 + sideY, rollItem(genRandom, 0), true);
        if (genRandom.nextInt(100) < 15)
            spawnLoot(ax + forwardX * 4 - sideX, ay + forwardY * 4 - sideY, rollItem(genRandom, 0), true);
    }

    /** Drops one key on an open floor tile a good walk away from the vault door, never in its room. */
    private void placeFloorKey(int floorWidth, int floorHeight) {
        for (int attempt = 0; attempt < 150; attempt++) {
            int x = randInterior(floorWidth);
            int y = randInterior(floorHeight);
            if (openSpot(x, y) && Math.abs(x - vaultDoorX) + Math.abs(y - vaultDoorY) >= KEY_DOOR_MIN_DISTANCE) {
                spawnKey(x, y);
                return;
            }
        }
    }

    private boolean isBossFloor() {
        return floor % BOSS_FLOOR_INTERVAL == 0;
    }

    /**
     * Builds a boss floor: a single broad arena with Duke arriving at one end and a sealed stairway at
     * the other, guarded by a large boss in the center. No merchant, traps, or wandering enemies share
     * the room — the fight is the floor.
     */
    private void generateArena(boolean arriveAtDownStairs) {
        enemyCount = 0;
        lootCount = 0;
        merchantX = -1;
        merchantY = -1;
        int roomWidth = Math.min(MAP_WIDTH - 2, 21);
        int roomHeight = Math.min(MAP_HEIGHT - 2, 13);
        int left = (MAP_WIDTH - roomWidth) / 2;
        int top = (MAP_HEIGHT - roomHeight) / 2;
        carveRoom(left, top, roomWidth, roomHeight);

        int midY = top + roomHeight / 2;
        int upX = left + 1;
        int downX = left + roomWidth - 2;
        map[index(upX, midY)] = UP_STAIRS;
        map[index(downX, midY)] = DOWN_STAIRS;

        if (arriveAtDownStairs) {
            playerX = downX;
        } else {
            playerX = upX;
        }
        playerY = midY;
        previousX = playerX;
        previousY = playerY;
        moveProgress = 1f;
        enemyProgress = 0f;
        spawnBoss(left + roomWidth / 2 - BOSS_SIZE / 2, midY - BOSS_SIZE / 2);
        computeFieldOfView();
    }

    private void spawnBoss(int x, int y) {
        bossActive = true;
        bossType = floor / BOSS_FLOOR_INTERVAL - 1;
        bossX = x;
        bossY = y;
        bossPrevX = x;
        bossPrevY = y;
        bossMaxHp = 60 + floor * 14;
        bossHp = bossMaxHp;
        bossState = BOSS_IDLE;
        bossEnraged = false;
        bossHit = 0f;
        bossWindup = 0f;
        bossShockwave = 0f;
        bossAnimTime = 0f;
        bossMoveProgress = 0f;
        bossAttackCooldown = BOSS_ATTACK_MS;
        bossSlamCooldown = BOSS_SLAM_COOLDOWN_MS;
    }

    /** True when the tile lies within the live boss's square footprint. */
    boolean bossOccupies(int x, int y) {
        return bossActive && x >= bossX && x < bossX + BOSS_SIZE && y >= bossY && y < bossY + BOSS_SIZE;
    }

    /** Chebyshev distance from Duke to the boss footprint; 1 means he stands right against it. */
    private int bossDistanceToPlayer() {
        int deltaX = Math.max(0, Math.max(bossX - playerX, playerX - (bossX + BOSS_SIZE - 1)));
        int deltaY = Math.max(0, Math.max(bossY - playerY, playerY - (bossY + BOSS_SIZE - 1)));
        return Math.max(deltaX, deltaY);
    }

    /** Telegraph intensity for the boss's slam wind-up: 0 when idle, rising toward 1 as the slam lands. */
    float bossTelegraph() {
        return bossState == BOSS_WINDUP ? 1f - bossWindup / BOSS_WINDUP_MS : 0f;
    }

    float bossRenderPixelX() {
        return (bossPrevX + (bossX - bossPrevX) * bossMoveProgress) * TILE;
    }

    float bossRenderPixelY() {
        return (bossPrevY + (bossY - bossPrevY) * bossMoveProgress) * TILE;
    }

    private float currentMoveInterval() {
        return bossEnraged ? BOSS_MOVE_MS * 0.62f : BOSS_MOVE_MS;
    }

    private float currentAttackInterval() {
        return bossEnraged ? BOSS_ATTACK_MS * 0.6f : BOSS_ATTACK_MS;
    }

    private int bossBiteDamage() {
        return 4 + floor / 2 + (bossEnraged ? 2 : 0);
    }

    private int bossSlamDamage() {
        return 7 + floor + (bossEnraged ? 3 : 0);
    }

    /**
     * Drives the boss each frame: decays its flashes, resolves a telegraphed slam when its wind-up
     * elapses, advances it toward Duke on its own clock, and—pressed against him—either bites or begins
     * winding up a heavy area slam he must retreat from.
     */
    private void updateBoss(long deltaMillis) {
        if (!bossActive) {
            return;
        }
        bossAnimTime += deltaMillis;
        bossHit = decay(bossHit, deltaMillis / HIT_DURATION_MS);
        bossShockwave = decay(bossShockwave, deltaMillis / BOSS_SHOCKWAVE_MS);
        bossAttackCooldown = decay(bossAttackCooldown, deltaMillis);
        bossSlamCooldown = decay(bossSlamCooldown, deltaMillis);
        if (bossState == BOSS_WINDUP) {
            bossWindup -= deltaMillis;
            if (bossWindup <= 0f) {
                bossWindup = 0f;
                bossState = BOSS_IDLE;
                bossSlam();
                bossAttackCooldown = currentAttackInterval();
            }
            return;
        }
        bossMoveProgress += deltaMillis / currentMoveInterval();
        if (bossMoveProgress >= 1f) {
            bossMoveProgress = 0f;
            stepBoss();
        }
        if (bossDistanceToPlayer() == 1 && bossAttackCooldown <= 0f) {
            if (bossSlamCooldown <= 0f && random.nextInt(100) < BOSS_SLAM_CHANCE) {
                bossState = BOSS_WINDUP;
                bossWindup = BOSS_WINDUP_MS;
                bossSlamCooldown = BOSS_SLAM_COOLDOWN_MS;
            } else {
                applyBossHitToPlayer(bossBiteDamage());
                bossAttackCooldown = currentAttackInterval();
            }
        }
    }

    private void stepBoss() {
        bossPrevX = bossX;
        bossPrevY = bossY;
        int centerX = bossX + BOSS_SIZE / 2;
        int centerY = bossY + BOSS_SIZE / 2;
        int deltaX = Integer.signum(playerX - centerX);
        int deltaY = Integer.signum(playerY - centerY);
        if (Math.abs(playerX - centerX) >= Math.abs(playerY - centerY)) {
            if (!moveBoss(deltaX, 0)) {
                moveBoss(0, deltaY);
            }
        } else if (!moveBoss(0, deltaY)) {
            moveBoss(deltaX, 0);
        }
    }

    /** Slides the whole footprint one tile only if every destination tile is clear of walls, Duke, and enemies. */
    private boolean moveBoss(int offsetX, int offsetY) {
        if (offsetX == 0 && offsetY == 0) {
            return false;
        }
        int nextX = bossX + offsetX;
        int nextY = bossY + offsetY;
        for (int oy = 0; oy < BOSS_SIZE; oy++) {
            for (int ox = 0; ox < BOSS_SIZE; ox++) {
                int checkX = nextX + ox;
                int checkY = nextY + oy;
                if (!inBounds(checkX, checkY) || blocksMovement(map[index(checkX, checkY)])
                        || (checkX == playerX && checkY == playerY) || enemyAt(checkX, checkY) >= 0) {
                    return false;
                }
            }
        }
        bossX = nextX;
        bossY = nextY;
        return true;
    }

    /** A telegraphed area slam: everything within {@link #BOSS_SLAM_RADIUS} of the footprint is struck. */
    private void bossSlam() {
        bossShockwave = 1f;
        Sound.bossSlam();
        if (bossDistanceToPlayer() <= BOSS_SLAM_RADIUS) {
            applyBossHitToPlayer(bossSlamDamage());
        }
    }

    /** Applies a melee hit to Duke through dodge and defense, plays the hurt sound, and resolves death; returns false if dodged. */
    private boolean damagePlayer(int raw) {
        if (effectOf(equippedArmor) == EFF_DODGE && random.nextInt(100) < magOf(equippedArmor)) {
            playerDodge = 1f;
            return false;
        }
        playerHp -= Math.max(1, raw - defense());
        Sound.enemyAttack();
        if (playerHp <= 0) {
            playerHp = 0;
            state = DEAD;
        }
        return true;
    }

    /** Applies boss damage to Duke, honoring dodge and reflecting thorns back into the boss. */
    private void applyBossHitToPlayer(int damage) {
        if (damagePlayer(damage) && effectOf(equippedArmor) == EFF_THORNS) {
            damageBoss(magOf(equippedArmor));
        }
    }

    /** Records damage on the boss, triggering its enrage-and-summon phase at half health and its death. */
    private void damageBoss(int amount) {
        bossHp -= amount;
        bossHit = 1f;
        if (!bossEnraged && bossHp <= bossMaxHp / 2 && bossHp > 0) {
            bossEnraged = true;
            summonAdds();
        }
        if (bossHp <= 0) {
            killBoss();
        }
    }

    /** The enraged boss calls a few minions in around its bulk to pressure Duke. */
    private void summonAdds() {
        int placed = 0;
        for (int attempt = 0; attempt < 60 && placed < 3; attempt++) {
            int x = bossX - 1 + random.nextInt(BOSS_SIZE + 2);
            int y = bossY - 1 + random.nextInt(BOSS_SIZE + 2);
            if (!inBounds(x, y) || map[index(x, y)] != FLOOR || bossOccupies(x, y)
                    || enemyAt(x, y) >= 0 || (x == playerX && y == playerY)) {
                continue;
            }
            addEnemy(x, y, random.nextInt(2) == 0 ? NULLPTR : BUG, true);
            placed++;
        }
    }

    /** Felling the boss unseals the stairs and showers Duke with gold, experience, and treasure. */
    private void killBoss() {
        bossActive = false;
        gold += 40 + floor * 6;
        grantXp(50 + floor * 8);
        spawnLoot(bossX, bossY, rollItem(random, 20), true);
        spawnLoot(bossX + BOSS_SIZE - 1, bossY + BOSS_SIZE - 1, rollItem(random, 20), true);
        Sound.bossDefeat();
    }

    /**
     * Carves pit clusters into room interiors. Each pit is 1–4 tiles grown from a room-interior seed;
     * every tile must pass isOpenTile so corridors and room edges are never touched.
     */
    private void placePits(int floorWidth, int floorHeight) {
        if (floor < 2) return;
        int count = Math.min(3, 1 + (floor - 2) / 3);
        int[] cellX = new int[5], cellY = new int[5];
        for (int cluster = 0; cluster < count; cluster++) {
            for (int attempt = 0; attempt < 40; attempt++) {
                int seedX = randInterior(floorWidth);
                int seedY = randInterior(floorHeight);
                if (map[index(seedX, seedY)] != FLOOR || !isOpenTile(seedX, seedY) || distToPlayer(seedX, seedY) < 5
                        || (seedX == merchantX && seedY == merchantY)) continue;
                cellX[0] = seedX; cellY[0] = seedY;
                int cellCount = 1;
                int extra = genRandom.nextInt(4);
                for (int growth = 0; growth < extra && cellCount < 5; growth++) {
                    int baseIndex = genRandom.nextInt(cellCount);
                    for (int dir = 0; dir < 4; dir++) {
                        int neighborX = cellX[baseIndex] + DIR_X[dir], neighborY = cellY[baseIndex] + DIR_Y[dir];
                        if (map[index(neighborX, neighborY)] != FLOOR || !isOpenTile(neighborX, neighborY)
                                || (neighborX == merchantX && neighborY == merchantY)) continue;
                        boolean duplicate = false;
                        for (int i = 0; i < cellCount; i++) if (cellX[i] == neighborX && cellY[i] == neighborY) { duplicate = true; break; }
                        if (!duplicate) { cellX[cellCount] = neighborX; cellY[cellCount++] = neighborY; break; }
                    }
                }
                for (int i = 0; i < cellCount; i++) map[index(cellX[i], cellY[i])] = PIT;
                break;
            }
        }
    }

    private void placeScenery(int floorWidth, int floorHeight, int roomCount) {
        int count = Math.max(1, roomCount / 3) + genRandom.nextInt(2);
        int placed = 0;
        for (int attempt = 0; attempt < count * 20 && placed < count; attempt++) {
            int x = randInterior(floorWidth);
            int y = randInterior(floorHeight);
            if (map[index(x, y)] != FLOOR) continue;
            if (x == merchantX && y == merchantY) continue;
            if (distToPlayer(x, y) < 3) continue;
            map[index(x, y)] = SCENERY;
            placed++;
        }
    }

    private void placeMerchant(int floorWidth, int floorHeight) {
        merchantX = -1;
        merchantY = -1;
        for (int attempt = 0; attempt < 200; attempt++) {
            int x = randInterior(floorWidth);
            int y = randInterior(floorHeight);
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
            int x = randInterior(floorWidth);
            int y = randInterior(floorHeight);
            if (!openSpot(x, y)) {
                continue;
            }
            spawnLoot(x, y, rollItem(genRandom, 0), true);
            placed++;
        }
    }

    private void placeTraps(int floorWidth, int floorHeight) {
        int traps = Math.min(8, floor / 2);
        int placed = 0;
        for (int attempt = 0; attempt < traps * 20 && placed < traps; attempt++) {
            int x = randInterior(floorWidth);
            int y = randInterior(floorHeight);
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

    /** A random interior coordinate in [1, span-2] from the generation RNG, never on the border. */
    private int randInterior(int span) {
        return 1 + genRandom.nextInt(span - 2);
    }

    boolean adjacentToMerchant() {
        return merchantX >= 0 && distToPlayer(merchantX, merchantY) == 1;
    }

    /**
     * True only when the full 3x3 block around a tile is floor, so the merchant
     * lands in open room space rather than a corridor the player must pass through.
     */
    private boolean isOpenTile(int x, int y) {
        for (int neighborY = y - 1; neighborY <= y + 1; neighborY++) {
            for (int neighborX = x - 1; neighborX <= x + 1; neighborX++) {
                if (!inBounds(neighborX, neighborY) || map[index(neighborX, neighborY)] != FLOOR) {
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
            int x = randInterior(floorWidth);
            int y = randInterior(floorHeight);
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
        // Explicit loop saves on const pool over Arrays::fill
        for (int i = 0; i < visible.length; i++) {
            visible[i] = false;
        }
        int radius = FOV_RADIUS + (effectOf(equippedTrinket) == EFF_SIGHT ? 2 : 0);
        for (int y = playerY - radius; y <= playerY + radius; y++) {
            for (int x = playerX - radius; x <= playerX + radius; x++) {
                int deltaX = x - playerX;
                int deltaY = y - playerY;
                if (inBounds(x, y) && deltaX * deltaX + deltaY * deltaY <= radius * radius) {
                    castLight(x, y);
                }
            }
        }
    }

    private void castLight(int targetX, int targetY) {
        int x = playerX;
        int y = playerY;
        int deltaX = Math.abs(targetX - x);
        int deltaY = Math.abs(targetY - y);
        int stepX = x < targetX ? 1 : -1;
        int stepY = y < targetY ? 1 : -1;
        int error = deltaX - deltaY;
        while (true) {
            int idx = index(x, y);
            visible[idx] = true;
            explored[idx] = true;
            if (map[idx] == WALL || map[idx] == LOCKED_DOOR || map[idx] == SCENERY || (x == targetX && y == targetY)) {
                return;
            }
            int doubled = 2 * error;
            if (doubled > -deltaY) {
                error -= deltaY;
                x += stepX;
            }
            if (doubled < deltaX) {
                error += deltaX;
                y += stepY;
            }
        }
    }

    private boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < MAP_WIDTH && y < MAP_HEIGHT;
    }

    /** Tiles that stop a walking entity: walls, locked vault doors, pits, and breakable scenery. */
    private static boolean blocksMovement(int tile) {
        return tile == WALL || tile == LOCKED_DOOR || tile == PIT || tile == SCENERY;
    }

    /** Manhattan distance from Duke to a tile. */
    private int distToPlayer(int x, int y) {
        return Math.abs(playerX - x) + Math.abs(playerY - y);
    }
}
