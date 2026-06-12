package autoplay.behavior;

import java.util.Optional;

import autoplay.BotConfig;
import autoplay.BotMemory;
import autoplay.Key;
import autoplay.Mode;
import autoplay.WorldSnapshot;
import autoplay.intent.Intent;

/** Understocked on potions with gold to spend: walk to the merchant and open the shop. */
public final class VisitMerchant implements Behavior {

    private final BotConfig config;

    public VisitMerchant(BotConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "Merchant";
    }

    @Override
    public Optional<Intent> decide(WorldSnapshot world, BotMemory memory) {
        if (world.mode() != Mode.PLAYING || !world.merchantKnown()
                || world.player().gold() < world.potionCost()
                || world.player().potions() >= config.potionTarget()
                || memory.isUnreachable(world.merchantX(), world.merchantY())) {
            return Optional.empty();
        }
        int distance = Math.abs(world.merchantX() - world.player().x())
                + Math.abs(world.merchantY() - world.player().y());
        if (distance == 1) {
            return Optional.of(new Intent.Press(Key.INTERACT, "opening shop"));
        }
        return Optional.of(new Intent.MoveAdjacentTo(world.merchantX(), world.merchantY(),
                "visiting merchant, gold=" + world.player().gold()));
    }
}
