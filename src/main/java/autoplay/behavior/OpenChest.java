package autoplay.behavior;

import java.util.Optional;

import autoplay.BotMemory;
import autoplay.Key;
import autoplay.Loot;
import autoplay.Mode;
import autoplay.WorldSnapshot;
import autoplay.intent.Intent;

/** Opens a chest standing beside it (mimics spring as enemies, which the fight behavior then handles). */
public final class OpenChest implements Behavior {

    @Override
    public String name() {
        return "OpenChest";
    }

    @Override
    public Optional<Intent> decide(WorldSnapshot world, BotMemory memory) {
        if (world.mode() != Mode.PLAYING || besideMerchant(world)) {
            return Optional.empty();
        }
        for (Loot loot : world.loot()) {
            if (loot.kind() == Loot.Kind.CHEST && loot.manhattanTo(world.player().x(), world.player().y()) == 1) {
                return Optional.of(new Intent.Press(Key.INTERACT, "opening chest at " + loot.x() + "," + loot.y()));
            }
        }
        return Optional.empty();
    }

    /** The game's interact key prefers the merchant over a chest, so beside both this press would open the shop. */
    private static boolean besideMerchant(WorldSnapshot world) {
        return world.merchantX() >= 0
                && Math.abs(world.merchantX() - world.player().x())
                + Math.abs(world.merchantY() - world.player().y()) == 1;
    }
}
