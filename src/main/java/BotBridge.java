import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import autoplay.BossInfo;
import autoplay.Controls;
import autoplay.Enemy;
import autoplay.EnemyType;
import autoplay.GameView;
import autoplay.Item;
import autoplay.Key;
import autoplay.Loot;
import autoplay.Mode;
import autoplay.PlayerState;
import autoplay.Tiles;
import autoplay.WorldSnapshot;

/**
 * The bot's adapter onto the game: builds immutable {@link WorldSnapshot}s from {@code Game}'s
 * package-private state and turns abstract {@link Key}s into the key events the game already
 * understands. The only class the autoplay package is wired to at runtime, and the only bot class
 * that touches {@code Game}.
 */
final class BotBridge implements GameView, Controls {

    private final Game game;

    BotBridge(Game game) {
        this.game = game;
    }

    @Override
    public WorldSnapshot snapshot() {
        return new WorldSnapshot(
                mode(),
                game.floor,
                player(),
                new Tiles(Game.MAP_WIDTH, Game.MAP_HEIGHT, game.map, game.explored, game.visible),
                enemies(),
                loot(),
                game.merchantX, game.merchantY,
                boss(),
                inventory(),
                game.inventorySelection,
                game.inventory.length,
                game.potionCost(),
                Game.POTION_CAP,
                game.floor % 5 == 0);
    }

    private Mode mode() {
        return switch (game.state) {
            case Game.DEAD -> Mode.DEAD;
            case Game.SHOP -> Mode.SHOP;
            case Game.PAUSED -> Mode.PAUSED;
            case Game.INVENTORY -> Mode.INVENTORY;
            default -> Mode.PLAYING;
        };
    }

    private PlayerState player() {
        return new PlayerState(
                game.playerX, game.playerY,
                game.playerHp, game.playerMaxHp, game.playerLevel,
                game.gold, game.potions, game.keys,
                item(game.equippedWeapon), item(game.equippedArmor), item(game.equippedTrinket),
                game.attackProgress >= 1f,
                game.moveProgress < 1f);
    }

    /** Only enemies on visible tiles — exactly what the world and minimap render to a player. */
    private List<Enemy> enemies() {
        List<Enemy> enemies = new ArrayList<>(game.enemyCount);
        for (int i = 0; i < game.enemyCount; i++) {
            if (game.visible[Game.index(game.enemyX[i], game.enemyY[i])]) {
                enemies.add(new Enemy(game.enemyX[i], game.enemyY[i], EnemyType.fromId(game.enemyType[i])));
            }
        }
        return enemies;
    }

    /** Only loot on visible tiles, and only its kind — a player sees sprites, never an item's stats. */
    private List<Loot> loot() {
        List<Loot> loot = new ArrayList<>(game.lootCount);
        for (int i = 0; i < game.lootCount; i++) {
            if (!game.visible[Game.index(game.lootX[i], game.lootY[i])]) {
                continue;
            }
            Loot.Kind kind = game.lootKey[i] ? Loot.Kind.KEY
                    : game.lootChest[i] ? Loot.Kind.CHEST : Loot.Kind.ITEM;
            loot.add(new Loot(game.lootX[i], game.lootY[i], kind));
        }
        return loot;
    }

    /** The boss exists for the bot only while part of its footprint is visible, like the rendered boss bar. */
    private BossInfo boss() {
        if (!game.bossActive || !footprintVisible()) {
            return null;
        }
        return new BossInfo(game.bossX, game.bossY, Game.BOSS_SIZE,
                game.bossHp, game.bossMaxHp, game.bossTelegraph(), Game.BOSS_SLAM_RADIUS);
    }

    private boolean footprintVisible() {
        for (int y = game.bossY; y < game.bossY + Game.BOSS_SIZE; y++) {
            for (int x = game.bossX; x < game.bossX + Game.BOSS_SIZE; x++) {
                if (x >= 0 && y >= 0 && x < Game.MAP_WIDTH && y < Game.MAP_HEIGHT
                        && game.visible[Game.index(x, y)]) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<Item> inventory() {
        List<Item> items = new ArrayList<>(game.inventoryCount);
        for (int i = 0; i < game.inventoryCount; i++) {
            items.add(item(game.inventory[i]));
        }
        return items;
    }

    private Item item(int packed) {
        if (packed < 0) {
            return null;
        }
        return new Item(packed, Game.itemId(packed), Game.itemRarity(packed),
                game.itemValue(packed), game.itemName(packed));
    }

    @Override
    public void hold(Key key) {
        game.keyDown(code(key));
    }

    @Override
    public void release(Key key) {
        game.keyUp(code(key));
    }

    @Override
    public void pressOnce(Key key) {
        int code = code(key);
        game.keyDown(code);
        game.keyUp(code);
    }

    @Override
    public void releaseAll() {
        for (Key key : Key.values()) {
            game.keyUp(code(key));
        }
    }

    private static int code(Key key) {
        return switch (key) {
            case UP, MENU_UP -> KeyEvent.VK_W;
            case DOWN, MENU_DOWN -> KeyEvent.VK_S;
            case LEFT -> KeyEvent.VK_A;
            case RIGHT, DROP -> KeyEvent.VK_D;
            case ATTACK, RESTART -> KeyEvent.VK_SPACE;
            case INTERACT -> KeyEvent.VK_E;
            case CANCEL -> KeyEvent.VK_Q;
            case INVENTORY -> KeyEvent.VK_TAB;
        };
    }
}
