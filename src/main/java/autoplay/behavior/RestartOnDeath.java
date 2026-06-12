package autoplay.behavior;

import java.util.Optional;

import autoplay.BotMemory;
import autoplay.Key;
import autoplay.Mode;
import autoplay.WorldSnapshot;
import autoplay.intent.Intent;

/** On the death screen, immediately start the next run. */
public final class RestartOnDeath implements Behavior {

    @Override
    public String name() {
        return "Restart";
    }

    @Override
    public Optional<Intent> decide(WorldSnapshot world, BotMemory memory) {
        if (world.mode() != Mode.DEAD) {
            return Optional.empty();
        }
        return Optional.of(new Intent.Press(Key.RESTART, "death screen"));
    }
}
