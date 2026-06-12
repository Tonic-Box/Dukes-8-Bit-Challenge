package autoplay.behavior;

import java.util.Optional;

import autoplay.BossInfo;
import autoplay.BotConfig;
import autoplay.BotMemory;
import autoplay.Enemy;
import autoplay.Mode;
import autoplay.WorldSnapshot;
import autoplay.intent.Intent;

/**
 * Closes on the nearest visible enemy within the hunt radius (or the boss, which gates the stairs).
 * On a rushed floor only nearby threats are engaged — the kind in the way, not the kind to farm.
 */
public final class HuntEnemy implements Behavior {

    private static final int RUSH_HUNT_RADIUS = 3;

    private final BotConfig config;

    public HuntEnemy(BotConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "Hunt";
    }

    @Override
    public Optional<Intent> decide(WorldSnapshot world, BotMemory memory) {
        if (world.mode() != Mode.PLAYING) {
            return Optional.empty();
        }
        int px = world.player().x();
        int py = world.player().y();

        boolean clearBlockers = memory.frontierExhausted();
        int radius = memory.rushingFloor() ? RUSH_HUNT_RADIUS : config.huntRadius();
        Enemy quarry = null;
        for (Enemy enemy : world.enemies()) {
            if (memory.isUnreachable(enemy.x(), enemy.y())) {
                continue;
            }
            if (enemy.manhattanTo(px, py) > radius && !clearBlockers) {
                continue;
            }
            if (quarry == null || enemy.manhattanTo(px, py) < quarry.manhattanTo(px, py)) {
                quarry = enemy;
            }
        }
        if (quarry != null) {
            String why = clearBlockers ? "clearing blocker " : "hunting ";
            return Optional.of(new Intent.MoveAdjacentTo(quarry.x(), quarry.y(),
                    why + quarry.type() + " dist=" + quarry.manhattanTo(px, py)));
        }

        BossInfo boss = world.boss();
        if (boss != null) {
            int targetX = clamp(px, boss.x(), boss.x() + boss.size() - 1);
            int targetY = clamp(py, boss.y(), boss.y() + boss.size() - 1);
            return Optional.of(new Intent.MoveAdjacentTo(targetX, targetY, "engaging boss hp=" + boss.hp()));
        }
        return Optional.empty();
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }
}
