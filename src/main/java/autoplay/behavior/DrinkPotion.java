package autoplay.behavior;

import java.util.Optional;

import autoplay.BotConfig;
import autoplay.BotMemory;
import autoplay.Key;
import autoplay.Mode;
import autoplay.WorldSnapshot;
import autoplay.intent.Intent;

/** Quaffs a potion when hp drops below the configured fraction. */
public final class DrinkPotion implements Behavior {

    private final BotConfig config;

    public DrinkPotion(BotConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "Heal";
    }

    @Override
    public Optional<Intent> decide(WorldSnapshot world, BotMemory memory) {
        if (world.mode() != Mode.PLAYING || world.player().potions() == 0
                || world.player().hpFraction() >= config.healAtHpFraction()
                || memory.millisSinceDrink() < config.healCooldownMillis()) {
            return Optional.empty();
        }
        return Optional.of(new Intent.Press(Key.CANCEL,
                "hp " + world.player().hp() + "/" + world.player().maxHp() + ", drinking potion"));
    }
}
