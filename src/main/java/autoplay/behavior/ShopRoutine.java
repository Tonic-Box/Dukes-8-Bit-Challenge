package autoplay.behavior;

import java.util.Optional;

import autoplay.BotMemory;
import autoplay.Key;
import autoplay.Mode;
import autoplay.WorldSnapshot;
import autoplay.intent.Intent;

/** Inside the shop: potions are the only thing gold buys, so buy as many as the purse allows, then leave. */
public final class ShopRoutine implements Behavior {

    @Override
    public String name() {
        return "Shop";
    }

    @Override
    public Optional<Intent> decide(WorldSnapshot world, BotMemory memory) {
        if (world.mode() != Mode.SHOP) {
            return Optional.empty();
        }
        if (world.player().gold() >= world.potionCost() && world.player().potions() < world.potionCap()) {
            return Optional.of(new Intent.Press(Key.INTERACT,
                    "buy potion #" + (world.player().potions() + 1) + ", gold=" + world.player().gold()));
        }
        String why = world.player().potions() >= world.potionCap() ? "belt full" : "purse empty";
        return Optional.of(new Intent.Press(Key.CANCEL, why + ", leaving shop"));
    }
}
