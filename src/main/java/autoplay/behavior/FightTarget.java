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
 * Melee combat against whatever stands in swing range (the spin hits all surrounding tiles). The
 * recovery between swings is spent kiting away only when it matters — hurt below the comfort
 * threshold, or facing the boss; a healthy fighter trades hits like a person who can't be bothered
 * to micro-step every cooldown.
 */
public final class FightTarget implements Behavior {

    private final BotConfig config;

    public FightTarget(BotConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "Fight";
    }

    @Override
    public Optional<Intent> decide(WorldSnapshot world, BotMemory memory) {
        if (world.mode() != Mode.PLAYING) {
            return Optional.empty();
        }
        int px = world.player().x();
        int py = world.player().y();

        BossInfo boss = world.boss();
        if (boss != null && boss.footprintDistance(px, py) <= 1) {
            int targetX = clamp(px, boss.x(), boss.x() + boss.size() - 1);
            int targetY = clamp(py, boss.y(), boss.y() + boss.size() - 1);
            return Optional.of(swingOrKite(world, targetX, targetY, true, "boss hp=" + boss.hp()));
        }
        for (Enemy enemy : world.enemies()) {
            if (enemy.chebyshevTo(px, py) <= 1) {
                return Optional.of(swingOrKite(world, enemy.x(), enemy.y(), false, enemy.type().toString()));
            }
        }
        return Optional.empty();
    }

    private Intent swingOrKite(WorldSnapshot world, int targetX, int targetY, boolean bossTarget, String target) {
        if (world.player().attackReady() || !shouldKite(world, bossTarget)) {
            return new Intent.AttackToward(targetX, targetY, "swinging at " + target);
        }
        return new Intent.StepAway(targetX, targetY, "kiting " + target + " between swings");
    }

    private boolean shouldKite(WorldSnapshot world, boolean bossTarget) {
        return config.kite()
                && (bossTarget || world.player().hpFraction() < config.kiteWhenBelowHpFraction());
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }
}
