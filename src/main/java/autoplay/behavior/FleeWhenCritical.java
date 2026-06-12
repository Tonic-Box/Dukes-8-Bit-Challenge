package autoplay.behavior;

import java.util.Optional;

import autoplay.BotConfig;
import autoplay.BotMemory;
import autoplay.Enemy;
import autoplay.Mode;
import autoplay.WorldSnapshot;
import autoplay.intent.Intent;

/** Out of potions and critically hurt: back away from the nearest threat instead of trading. */
public final class FleeWhenCritical implements Behavior {

    private final BotConfig config;

    public FleeWhenCritical(BotConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "Flee";
    }

    @Override
    public Optional<Intent> decide(WorldSnapshot world, BotMemory memory) {
        if (world.mode() != Mode.PLAYING || world.player().potions() > 0
                || world.player().hpFraction() >= config.fleeAtHpFraction()) {
            return Optional.empty();
        }
        Enemy nearest = world.nearestEnemy();
        if (nearest == null || nearest.manhattanTo(world.player().x(), world.player().y()) > 2) {
            return Optional.empty();
        }
        return Optional.of(new Intent.StepAway(nearest.x(), nearest.y(),
                "critical hp, no potions, fleeing " + nearest.type()));
    }
}
