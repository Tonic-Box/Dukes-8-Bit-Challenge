package autoplay;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds {@link WorldSnapshot}s for tests from ASCII maps. Legend: {@code #} wall, {@code .} floor,
 * {@code ?} unexplored floor, {@code T} trap, {@code P} pit, {@code S} scenery, {@code D} locked
 * door, {@code >} down stairs, {@code <} up stairs, {@code @} the player (on floor).
 */
final class WorldBuilder {

    private static final int MAX_HP = 30;

    private String[] rows;
    private Mode mode = Mode.PLAYING;
    private int hp = MAX_HP;
    private int potions;
    private boolean attackReady = true;
    private Item equippedWeapon;
    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Loot> loot = new ArrayList<>();
    private final List<Item> inventory = new ArrayList<>();
    private int inventorySelection;
    private final int inventoryCapacity = 8;

    WorldBuilder map(String... rows) {
        this.rows = rows;
        return this;
    }

    WorldBuilder mode(Mode mode) {
        this.mode = mode;
        return this;
    }

    /** Sets current hp out of the fixed {@link #MAX_HP}-point pool. */
    WorldBuilder hp(int hp) {
        this.hp = hp;
        return this;
    }

    WorldBuilder potions(int potions) {
        this.potions = potions;
        return this;
    }

    WorldBuilder attackReady(boolean ready) {
        this.attackReady = ready;
        return this;
    }

    WorldBuilder equippedWeapon(Item item) {
        this.equippedWeapon = item;
        return this;
    }

    WorldBuilder enemy(int x, int y, EnemyType type) {
        enemies.add(new Enemy(x, y, type));
        return this;
    }

    WorldBuilder carrying(Item... items) {
        inventory.addAll(List.of(items));
        return this;
    }

    WorldBuilder selection(int index) {
        this.inventorySelection = index;
        return this;
    }

    WorldSnapshot build() {
        int height = rows.length;
        int width = rows[0].length();
        int[] map = new int[width * height];
        boolean[] explored = new boolean[width * height];
        int playerX = -1;
        int playerY = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                char cell = rows[y].charAt(x);
                int index = y * width + x;
                explored[index] = cell != '?';
                map[index] = switch (cell) {
                    case '#' -> 0;
                    case '>' -> 2;
                    case '<' -> 3;
                    case 'T' -> 4;
                    case 'D' -> 5;
                    case 'P' -> 6;
                    case 'S' -> 7;
                    default -> 1;
                };
                if (cell == '@') {
                    playerX = x;
                    playerY = y;
                }
            }
        }
        if (playerX < 0) {
            throw new IllegalArgumentException("map has no @ player marker");
        }
        PlayerState player = new PlayerState(playerX, playerY, hp, MAX_HP, 1, 0, potions, 0,
                equippedWeapon, null, null, attackReady, false);
        Tiles tiles = new Tiles(width, height, map, explored, explored);
        return new WorldSnapshot(mode, 1, player, tiles, enemies, loot, -1, -1, null,
                inventory, inventorySelection, inventoryCapacity, 12, 12, false);
    }

    static Item weapon(int rarity, int value, String name) {
        return new Item(0, 0, rarity, value, name);
    }
}
