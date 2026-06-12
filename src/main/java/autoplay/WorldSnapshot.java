package autoplay;

import java.util.List;

/**
 * Everything the brain may know about one tick, captured immutably so behaviors reason over a
 * consistent world. Built once per tick by the game-side adapter.
 *
 * @param merchantX merchant tile, or -1 when this floor has none
 * @param boss      the floor boss, or null when none is active
 * @param inventory carried items in selection order
 * @param inventorySelection the currently highlighted inventory index
 * @param inventoryCapacity  the carry limit; pickups are lost while full
 * @param potionCost shop price of one potion (inflates with depth)
 * @param potionCap  the most potions that can be carried
 * @param bossFloor  whether this floor is a boss arena (a stated game rule, so fair knowledge)
 */
public record WorldSnapshot(Mode mode, int floor, PlayerState player, Tiles tiles,
                            List<Enemy> enemies, List<Loot> loot,
                            int merchantX, int merchantY,
                            BossInfo boss,
                            List<Item> inventory, int inventorySelection, int inventoryCapacity,
                            int potionCost, int potionCap, boolean bossFloor) {

    public WorldSnapshot {
        enemies = List.copyOf(enemies);
        loot = List.copyOf(loot);
        inventory = List.copyOf(inventory);
    }

    public boolean merchantKnown() {
        return merchantX >= 0 && tiles.explored(merchantX, merchantY);
    }

    public Enemy nearestEnemy() {
        Enemy nearest = null;
        for (Enemy enemy : enemies) {
            if (nearest == null
                    || enemy.manhattanTo(player.x(), player.y()) < nearest.manhattanTo(player.x(), player.y())) {
                nearest = enemy;
            }
        }
        return nearest;
    }
}
