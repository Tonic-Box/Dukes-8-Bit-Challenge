package autoplay.behavior;

import java.util.List;
import java.util.Optional;

import autoplay.BotConfig;
import autoplay.BotMemory;
import autoplay.Item;
import autoplay.Key;
import autoplay.Mode;
import autoplay.WorldSnapshot;
import autoplay.intent.Intent;

/**
 * Gear management. A fresh pickup arms an equip check (via {@link BotMemory}); once the pickup has
 * visually settled and no more loot waits nearby, this behavior opens the inventory, walks the
 * selection to the best upgrade and equips it, drops everything outclassed by the worn gear (which
 * includes the item a swap just replaced), and closes. One key per tick — the screen itself is the
 * state machine, re-evaluated from the live selection each tick.
 */
public final class InventoryRoutine implements Behavior {

    private final BotConfig config;

    public InventoryRoutine(BotConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "Inventory";
    }

    @Override
    public Optional<Intent> decide(WorldSnapshot world, BotMemory memory) {
        if (world.mode() == Mode.INVENTORY) {
            return Optional.of(act(world, memory));
        }
        boolean pickupSettled = memory.millisSincePickup() >= config.equipReviewDelayMillis();
        boolean inventoryFull = world.inventory().size() >= world.inventoryCapacity();
        boolean lootCanWait = lootNearby(world, memory) && !inventoryFull;
        if (world.mode() == Mode.PLAYING && memory.pendingEquipCheck()
                && pickupSettled && !lootCanWait && noEnemyNear(world)) {
            return Optional.of(new Intent.Press(Key.INVENTORY, "new pickup, reviewing gear"));
        }
        return Optional.empty();
    }

    /** More pickups within reach: keep looting first and review everything in one pass afterward. */
    private static boolean lootNearby(WorldSnapshot world, BotMemory memory) {
        for (var loot : world.loot()) {
            if (world.tiles().explored(loot.x(), loot.y()) && !memory.isUnreachable(loot.x(), loot.y())
                    && loot.manhattanTo(world.player().x(), world.player().y()) <= 6) {
                return true;
            }
        }
        return false;
    }

    private static boolean noEnemyNear(WorldSnapshot world) {
        var nearest = world.nearestEnemy();
        return nearest == null || nearest.manhattanTo(world.player().x(), world.player().y()) > 6;
    }

    private Intent act(WorldSnapshot world, BotMemory memory) {
        List<Item> items = world.inventory();
        int upgrade = bestUpgradeIndex(world, items);
        if (upgrade >= 0) {
            return stepSelectionThen(world, upgrade, Key.INTERACT,
                    "equip " + items.get(upgrade).name() + " (" + items.get(upgrade).slot() + ")");
        }
        int junk = junkIndex(world, items);
        if (junk >= 0) {
            return stepSelectionThen(world, junk, Key.DROP,
                    "dropping outclassed " + items.get(junk).name());
        }
        memory.equipCheckDone();
        return new Intent.Press(Key.CANCEL, "gear reviewed, closing inventory");
    }

    /** An item the worn gear outclasses is dead weight (no selling exists), so it is dropped. */
    private static int junkIndex(WorldSnapshot world, List<Item> items) {
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).outranks(world.player().equipped(items.get(i).slot()))) {
                return i;
            }
        }
        return -1;
    }

    private static Intent stepSelectionThen(WorldSnapshot world, int targetIndex, Key action, String reason) {
        int selection = world.inventorySelection();
        if (selection < targetIndex) {
            return new Intent.Press(Key.MENU_DOWN, reason);
        }
        if (selection > targetIndex) {
            return new Intent.Press(Key.MENU_UP, reason);
        }
        return new Intent.Press(action, reason);
    }

    private static int bestUpgradeIndex(WorldSnapshot world, List<Item> items) {
        int best = -1;
        for (int i = 0; i < items.size(); i++) {
            Item candidate = items.get(i);
            if (candidate.outranks(world.player().equipped(candidate.slot()))
                    && (best < 0 || candidate.outranks(items.get(best)))) {
                best = i;
            }
        }
        return best;
    }
}
