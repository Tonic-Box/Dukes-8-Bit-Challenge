package autoplay.behavior;

import java.util.Optional;

import autoplay.BotMemory;
import autoplay.Loot;
import autoplay.Mode;
import autoplay.WorldSnapshot;
import autoplay.intent.Intent;

/**
 * Heads for the nearest remembered pickup: keys and items are walked over, chests are approached
 * (the open happens via {@code OpenChest} once adjacent). Knowledge is player-fair — targets come
 * from loot the bot has actually seen ({@link BotMemory}), an item's identity is unknown until it is
 * picked up, and tiles where the bot dropped its own discards are not walked back to. Item pickups
 * are skipped while the inventory is full; unreachable targets are skipped for the floor.
 */
public final class CollectLoot implements Behavior {

    @Override
    public String name() {
        return "Collect";
    }

    @Override
    public Optional<Intent> decide(WorldSnapshot world, BotMemory memory) {
        if (world.mode() != Mode.PLAYING) {
            return Optional.empty();
        }
        boolean inventoryFull = world.inventory().size() >= world.inventoryCapacity();
        Loot target = null;
        for (Loot loot : memory.rememberedLoot()) {
            if (memory.isUnreachable(loot.x(), loot.y())) {
                continue;
            }
            if (loot.kind() == Loot.Kind.ITEM
                    && (inventoryFull || memory.droppedAt(loot.x(), loot.y()))) {
                continue;
            }
            if (target == null || loot.manhattanTo(world.player().x(), world.player().y())
                    < target.manhattanTo(world.player().x(), world.player().y())) {
                target = loot;
            }
        }
        if (target == null) {
            return Optional.empty();
        }
        String reason = "collecting " + target.kind().toString().toLowerCase()
                + " at " + target.x() + "," + target.y();
        if (target.kind() == Loot.Kind.CHEST) {
            return Optional.of(new Intent.MoveAdjacentTo(target.x(), target.y(), reason));
        }
        return Optional.of(new Intent.MoveTo(target.x(), target.y(), reason));
    }
}
