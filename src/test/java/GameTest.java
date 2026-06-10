import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Game-logic invariants: the tile-index packing, that a freshly generated floor is well-formed (the persistence
 * and gameplay both rely on it), and that base combat/progression stats are sane. Default package so the tests
 * can read the game's package-visible state directly, the same way the renderer does.
 */
class GameTest {

    @Test
    void indexPacksEveryCellToADistinctInRangeSlot() {
        assertEquals(0, Game.index(0, 0));
        assertEquals(Game.MAP_WIDTH, Game.index(0, 1));
        assertEquals(Game.MAP_WIDTH + 1, Game.index(1, 1));
        assertEquals(Game.MAP_WIDTH * Game.MAP_HEIGHT - 1,
                Game.index(Game.MAP_WIDTH - 1, Game.MAP_HEIGHT - 1));

        Set<Integer> seen = new HashSet<>();
        for (int y = 0; y < Game.MAP_HEIGHT; y++) {
            for (int x = 0; x < Game.MAP_WIDTH; x++) {
                int index = Game.index(x, y);
                assertTrue(index >= 0 && index < Game.MAP_WIDTH * Game.MAP_HEIGHT, "index in range");
                assertTrue(seen.add(index), "distinct cells must pack to distinct indices");
            }
        }
    }

    @Test
    void freshFloorIsWellFormed() {
        Game game = new Game();

        assertTrue(game.playerX >= 0 && game.playerX < Game.MAP_WIDTH, "player x in bounds");
        assertTrue(game.playerY >= 0 && game.playerY < Game.MAP_HEIGHT, "player y in bounds");
        assertNotEquals(Game.WALL, game.map[Game.index(game.playerX, game.playerY)],
                "the player must spawn on a walkable tile");

        boolean hasWayDown = false;
        for (int tile : game.map) {
            if (tile == Game.DOWN_STAIRS) {
                hasWayDown = true;
                break;
            }
        }
        assertTrue(hasWayDown, "every generated floor must have a way down");

        assertTrue(game.playerHp > 0, "player starts alive");
        assertEquals(game.playerMaxHp, game.playerHp, "player starts at full health");
        assertEquals(1, game.playerLevel, "player starts at level 1");
    }

    @Test
    void baseCombatAndProgressionStatsAreSane() {
        Game game = new Game();

        assertTrue(game.attackPower() > 0, "base attack power should be positive");
        assertTrue(game.defense() >= 0, "base defense should be non-negative");
        assertPercentage(game.critChance(), "crit chance");
        assertPercentage(game.dodgeChance(), "dodge chance");
        assertPercentage(game.lifestealPercent(), "lifesteal");
        assertTrue(game.xpForNext() > 0, "xp required for the next level should be positive");
    }

    private static void assertPercentage(int value, String label) {
        assertTrue(value >= 0 && value <= 100, label + " should be a 0-100 percentage, was " + value);
    }
}
