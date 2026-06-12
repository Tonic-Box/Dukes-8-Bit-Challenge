package autoplay;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.function.IntPredicate;

/**
 * Grid pathfinding over the explored map. Plans are recomputed from scratch every tick — the grid
 * is at most 45x31, so a BFS costs microseconds and a stale cached path can never misdirect the bot.
 *
 * <p>Walkability honors the game's movement rules: walls never; stairs, pits, and locked doors only
 * as the final goal tile (stepping onto stairs changes floors, walking into a door bumps it open);
 * enemy, boss, and merchant tiles are blocked. Two comfort rules relax in stages when no path is
 * found: traps are avoided, then allowed; scenery is avoided, then treated as smash-through.
 */
public final class Navigator {

    /** The next concrete action along a path. */
    public record Step(Kind kind, Key direction) {
        public enum Kind { MOVE, SMASH, ARRIVED, UNREACHABLE }

        static final Step ARRIVED_STEP = new Step(Kind.ARRIVED, null);
        static final Step UNREACHABLE_STEP = new Step(Kind.UNREACHABLE, null);
    }

    private static final int[] DX = {0, 0, -1, 1};
    private static final int[] DY = {-1, 1, 0, 0};
    private static final Key[] DIRECTION_KEYS = {Key.UP, Key.DOWN, Key.LEFT, Key.RIGHT};

    /** Plans the next step onto the exact target tile. */
    public Step toward(WorldSnapshot world, int targetX, int targetY) {
        if (world.player().x() == targetX && world.player().y() == targetY) {
            return Step.ARRIVED_STEP;
        }
        return plan(world, index(world, targetX, targetY), i -> i == index(world, targetX, targetY));
    }

    /** Plans the next step to any tile cardinally adjacent to the target. */
    public Step adjacentTo(WorldSnapshot world, int targetX, int targetY) {
        PlayerState player = world.player();
        if (Math.abs(player.x() - targetX) + Math.abs(player.y() - targetY) == 1) {
            return Step.ARRIVED_STEP;
        }
        IntPredicate goal = i -> {
            int x = i % world.tiles().width();
            int y = i / world.tiles().width();
            return Math.abs(x - targetX) + Math.abs(y - targetY) == 1;
        };
        return plan(world, -1, goal);
    }

    /**
     * Plans the next step toward the nearest tile matching the goal predicate. When no goal tile is
     * reachable (typically an unseen enemy blocking a corridor), walks to the reachable tile closest
     * to a goal instead — getting near enough usually reveals the blocker, which other behaviors
     * then deal with. UNREACHABLE only when already standing at the best possible vantage.
     */
    public Step towardNearest(WorldSnapshot world, IntPredicate goal) {
        if (goal.test(index(world, world.player().x(), world.player().y()))) {
            return Step.ARRIVED_STEP;
        }
        Step direct = plan(world, -1, goal);
        if (direct.kind() != Step.Kind.UNREACHABLE) {
            return direct;
        }
        return approachNearest(world, goal);
    }

    private Step approachNearest(WorldSnapshot world, IntPredicate goal) {
        Tiles tiles = world.tiles();
        int width = tiles.width();
        int size = width * tiles.height();
        boolean[] blocked = blockedTiles(world);
        int[] parent = new int[size];
        Arrays.fill(parent, -1);
        int start = index(world, world.player().x(), world.player().y());
        parent[start] = start;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            int current = queue.poll();
            int cx = current % width;
            int cy = current / width;
            for (int d = 0; d < 4; d++) {
                int nx = cx + DX[d];
                int ny = cy + DY[d];
                int next = ny * width + nx;
                if (!tiles.inBounds(nx, ny) || !tiles.explored(nx, ny) || parent[next] >= 0
                        || blocked[next] || impassable(tiles.tile(nx, ny), true, true)) {
                    continue;
                }
                parent[next] = current;
                queue.add(next);
            }
        }
        int[] goals = new int[size];
        int goalCount = 0;
        for (int target = 0; target < size; target++) {
            if (parent[target] < 0 && goal.test(target)) {
                goals[goalCount++] = target;
            }
        }
        if (goalCount == 0) {
            return Step.UNREACHABLE_STEP;
        }
        int best = start;
        int bestDistance = distanceToNearestGoal(start, goals, goalCount, width);
        for (int reached = 0; reached < size; reached++) {
            if (parent[reached] < 0 || reached == start) {
                continue;
            }
            int distance = distanceToNearestGoal(reached, goals, goalCount, width);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = reached;
            }
        }
        if (best == start) {
            return Step.UNREACHABLE_STEP;
        }
        return firstStep(world, parent, start, best);
    }

    private static int distanceToNearestGoal(int from, int[] goals, int goalCount, int width) {
        int nearest = Integer.MAX_VALUE;
        for (int i = 0; i < goalCount; i++) {
            int distance = Math.abs(from % width - goals[i] % width)
                    + Math.abs(from / width - goals[i] / width);
            nearest = Math.min(nearest, distance);
        }
        return nearest;
    }

    /** Picks the walkable neighbor that most increases distance from a point; ARRIVED when cornered. */
    public Step awayFrom(WorldSnapshot world, int fromX, int fromY) {
        PlayerState player = world.player();
        boolean[] blocked = blockedTiles(world);
        int bestDirection = -1;
        int bestDistance = distance(player.x(), player.y(), fromX, fromY);
        for (int d = 0; d < 4; d++) {
            int nx = player.x() + DX[d];
            int ny = player.y() + DY[d];
            if (!world.tiles().inBounds(nx, ny) || blocked[index(world, nx, ny)]
                    || !plainWalkable(world.tiles().tile(nx, ny))) {
                continue;
            }
            int distance = distance(nx, ny, fromX, fromY);
            if (distance > bestDistance) {
                bestDistance = distance;
                bestDirection = d;
            }
        }
        return bestDirection < 0 ? Step.ARRIVED_STEP : new Step(Step.Kind.MOVE, DIRECTION_KEYS[bestDirection]);
    }

    private Step plan(WorldSnapshot world, int enterableGoal, IntPredicate goal) {
        boolean[] blocked = blockedTiles(world);
        for (boolean allowTraps : new boolean[] {false, true}) {
            for (boolean smashScenery : new boolean[] {false, true}) {
                Step step = search(world, blocked, goal, enterableGoal, allowTraps, smashScenery);
                if (step != null) {
                    return step;
                }
            }
        }
        return Step.UNREACHABLE_STEP;
    }

    private Step search(WorldSnapshot world, boolean[] blocked, IntPredicate goal, int enterableGoal,
                        boolean allowTraps, boolean smashScenery) {
        Tiles tiles = world.tiles();
        int width = tiles.width();
        int size = width * tiles.height();
        int[] parent = new int[size];
        Arrays.fill(parent, -1);
        int start = index(world, world.player().x(), world.player().y());
        parent[start] = start;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            int current = queue.poll();
            if (current != start && goal.test(current)) {
                return firstStep(world, parent, start, current);
            }
            int cx = current % width;
            int cy = current / width;
            for (int d = 0; d < 4; d++) {
                int nx = cx + DX[d];
                int ny = cy + DY[d];
                if (!tiles.inBounds(nx, ny) || !tiles.explored(nx, ny)) {
                    continue;
                }
                int next = ny * width + nx;
                if (parent[next] >= 0) {
                    continue;
                }
                if (next != enterableGoal
                        && (blocked[next] || impassable(tiles.tile(nx, ny), allowTraps, smashScenery))) {
                    continue;
                }
                parent[next] = current;
                queue.add(next);
            }
        }
        return null;
    }

    private Step firstStep(WorldSnapshot world, int[] parent, int start, int goal) {
        int width = world.tiles().width();
        int current = goal;
        while (parent[current] != start) {
            current = parent[current];
        }
        int stepX = current % width;
        int stepY = current / width;
        int direction = directionIndex(stepX - world.player().x(), stepY - world.player().y());
        if (world.tiles().tile(stepX, stepY) == TileType.SCENERY) {
            return new Step(Step.Kind.SMASH, DIRECTION_KEYS[direction]);
        }
        return new Step(Step.Kind.MOVE, DIRECTION_KEYS[direction]);
    }

    private static boolean impassable(TileType tile, boolean allowTraps, boolean smashScenery) {
        return switch (tile) {
            case FLOOR -> false;
            case TRAP -> !allowTraps;
            case SCENERY -> !smashScenery;
            case WALL, LOCKED_DOOR, PIT, DOWN_STAIRS, UP_STAIRS -> true;
        };
    }

    private static boolean plainWalkable(TileType tile) {
        return tile == TileType.FLOOR || tile == TileType.TRAP;
    }

    private static boolean[] blockedTiles(WorldSnapshot world) {
        Tiles tiles = world.tiles();
        boolean[] blocked = new boolean[tiles.width() * tiles.height()];
        for (Enemy enemy : world.enemies()) {
            blocked[index(world, enemy.x(), enemy.y())] = true;
        }
        BossInfo boss = world.boss();
        if (boss != null) {
            for (int y = boss.y(); y < boss.y() + boss.size(); y++) {
                for (int x = boss.x(); x < boss.x() + boss.size(); x++) {
                    if (tiles.inBounds(x, y)) {
                        blocked[index(world, x, y)] = true;
                    }
                }
            }
        }
        if (world.merchantX() >= 0) {
            blocked[index(world, world.merchantX(), world.merchantY())] = true;
        }
        return blocked;
    }

    private static int directionIndex(int dx, int dy) {
        for (int d = 0; d < 4; d++) {
            if (DX[d] == dx && DY[d] == dy) {
                return d;
            }
        }
        throw new IllegalStateException("non-cardinal step " + dx + "," + dy);
    }

    private static int distance(int x1, int y1, int x2, int y2) {
        return Math.abs(x1 - x2) + Math.abs(y1 - y2);
    }

    private static int index(WorldSnapshot world, int x, int y) {
        return y * world.tiles().width() + x;
    }
}
