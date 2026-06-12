package autoplay.behavior;

import java.util.Optional;

import autoplay.BossInfo;
import autoplay.BotMemory;
import autoplay.Mode;
import autoplay.WorldSnapshot;
import autoplay.intent.Intent;

/** Steps out of the boss's slam ring while the slam is winding up. */
public final class AvoidBossSlam implements Behavior {

    @Override
    public String name() {
        return "DodgeSlam";
    }

    @Override
    public Optional<Intent> decide(WorldSnapshot world, BotMemory memory) {
        BossInfo boss = world.boss();
        if (world.mode() != Mode.PLAYING || boss == null || !boss.slamImminent()
                || !boss.inSlamDanger(world.player().x(), world.player().y())) {
            return Optional.empty();
        }
        int centerX = boss.x() + boss.size() / 2;
        int centerY = boss.y() + boss.size() / 2;
        return Optional.of(new Intent.StepAway(centerX, centerY,
                "slam telegraphed, stepping out of the ring"));
    }
}
