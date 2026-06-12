package autoplay;

import java.util.ArrayList;
import java.util.List;

import autoplay.behavior.AvoidBossSlam;
import autoplay.behavior.Behavior;
import autoplay.behavior.BreakScenery;
import autoplay.behavior.CollectLoot;
import autoplay.behavior.Descend;
import autoplay.behavior.DrinkPotion;
import autoplay.behavior.ExploreFrontier;
import autoplay.behavior.FightTarget;
import autoplay.behavior.FleeWhenCritical;
import autoplay.behavior.HuntEnemy;
import autoplay.behavior.InventoryRoutine;
import autoplay.behavior.OpenChest;
import autoplay.behavior.RestartOnDeath;
import autoplay.behavior.ShopRoutine;
import autoplay.behavior.UnlockVault;
import autoplay.behavior.VisitMerchant;
import autoplay.intent.Intent;

/**
 * The bot's main loop body: once per frame it snapshots the world, derives loggable events from the
 * change since last tick, asks the behavior chain for a decision, and executes the winning intent.
 * A tick can never crash the host loop — failures are logged and inputs released.
 */
public final class AutoPilot {

    private final GameView view;
    private final IntentExecutor executor;
    private final BotConfig config;
    private final BotLogger logger;
    private final BotMemory memory = new BotMemory();
    private final RunStats stats = new RunStats();
    private final List<Behavior> behaviors;

    private WorldSnapshot previous;
    private long simulatedMillis;
    private long lastHeartbeatMillis;
    private long millisSinceMenuPress;
    private int shopEntryGold;
    private int shopEntryPotions;
    private String currentBehavior = "";
    private Intent currentIntent = new Intent.Idle("starting");
    private String pendingBehavior;
    private long millisSincePendingBehavior;

    public AutoPilot(GameView view, Controls controls, BotConfig config, BotLogger logger) {
        this.view = view;
        this.executor = new IntentExecutor(controls, new Navigator());
        this.config = config;
        this.logger = logger;
        this.behaviors = List.of(
                new RestartOnDeath(),
                new ShopRoutine(),
                new InventoryRoutine(config),
                new DrinkPotion(config),
                new AvoidBossSlam(),
                new FightTarget(config),
                new FleeWhenCritical(config),
                new HuntEnemy(config),
                new OpenChest(),
                new CollectLoot(),
                new UnlockVault(),
                new VisitMerchant(config),
                new BreakScenery(),
                new ExploreFrontier(),
                new Descend());
        logger.runStarted(1);
    }

    public void tick(long deltaMillis) {
        try {
            WorldSnapshot world = view.snapshot();
            logger.advance(deltaMillis);
            stats.advance(deltaMillis);
            simulatedMillis += deltaMillis;
            if (simulatedMillis - lastHeartbeatMillis >= 60_000) {
                lastHeartbeatMillis = simulatedMillis;
                logger.event(world, "progress " + stats.summary());
            }
            observeTransitions(world);
            stats.observe(previous, world);
            memory.observeInventory(world.inventory().size(), deltaMillis);
            if (memory.enteredFloor(world.floor(), config.rushChance()) && memory.rushingFloor()) {
                logger.event(world, "rushing this floor");
            }
            updateLootMemory(world);

            Decision decision = decide(world);
            logger.decide(world, decision.behavior().name(), decision.intent().reason());
            Intent intent = applyReactionTime(world, decision, deltaMillis);
            intent = paceMenuPresses(world, intent, deltaMillis);
            IntentExecutor.Outcome outcome = executor.execute(intent, world);
            handleOutcome(world, intent, outcome);
            watchdog(world, intent, deltaMillis);

            previous = world;
        } catch (Exception error) {
            logger.error(error);
            executor.releaseEverything();
        }
    }

    public int runsCompleted() {
        return stats.runs();
    }

    /** The most recent snapshot, for live displays; immutable, so safe to hand across threads. */
    public WorldSnapshot lastSnapshot() {
        return previous;
    }

    public void logSession() {
        logger.session(sessionSummary());
    }

    /** Session aggregates plus the still-running run's vitals — without these, an undying bot reports zeros. */
    public String sessionSummary() {
        if (previous == null) {
            return stats.sessionSummary(0);
        }
        String session = stats.sessionSummary(previous.floor());
        if (previous.mode() != Mode.DEAD) {
            session += "\ncurrent run: floor=" + previous.floor() + " level=" + previous.player().level()
                    + " hp=" + previous.player().hp() + "/" + previous.player().maxHp()
                    + " " + stats.summary();
        }
        return session;
    }

    private record Decision(Behavior behavior, Intent intent) {}

    /**
     * Maintains the player-fair loot memory: the snapshot only carries pickups on visible tiles
     * (what a player sees), so sighted loot is remembered for when the fog returns, and remembered
     * entries are forgotten once their tile is visible again without the loot.
     */
    private void updateLootMemory(WorldSnapshot world) {
        for (Loot loot : world.loot()) {
            memory.rememberLoot(loot);
        }
        var stale = new ArrayList<Loot>();
        for (Loot remembered : memory.rememberedLoot()) {
            boolean tileVisible = world.tiles().visible(remembered.x(), remembered.y());
            if (tileVisible && !world.loot().contains(remembered)) {
                stale.add(remembered);
            }
        }
        for (Loot gone : stale) {
            memory.forgetLootAt(gone.x(), gone.y());
        }
    }

    /**
     * Models human reaction time: when the winning behavior changes mid-play, the previous intent
     * keeps executing for {@code reactionMillis} before the new plan takes over — so a telegraphed
     * slam may land before the dodge starts, fleeing begins late, and a heal comes a beat behind the
     * hit that warranted it. Same-behavior decisions (path updates, swing/kite micro) flow through
     * instantly, and menus / the death screen are exempt.
     */
    private Intent applyReactionTime(WorldSnapshot world, Decision decision, long deltaMillis) {
        String behavior = decision.behavior().name();
        if (config.reactionMillis() == 0 || world.mode() != Mode.PLAYING
                || currentBehavior.isEmpty() || behavior.equals(currentBehavior)) {
            commitBehavior(decision);
            return decision.intent();
        }
        if (!behavior.equals(pendingBehavior)) {
            pendingBehavior = behavior;
            millisSincePendingBehavior = 0;
        }
        millisSincePendingBehavior += deltaMillis;
        if (millisSincePendingBehavior >= config.reactionMillis()) {
            commitBehavior(decision);
            return decision.intent();
        }
        return currentIntent;
    }

    private void commitBehavior(Decision decision) {
        currentBehavior = decision.behavior().name();
        currentIntent = decision.intent();
        pendingBehavior = null;
    }

    /**
     * Paces the shop and inventory screens like a person at the keyboard: a beat after the screen
     * opens, {@code menuDelayMillis} between actions, and a longer {@code menuCloseDelayMillis}
     * dwell before the closing press so the end result stays visible. Inactive at the defaults of 0
     * (the headless sim) and outside menu screens.
     */
    private Intent paceMenuPresses(WorldSnapshot world, Intent intent, long deltaMillis) {
        millisSinceMenuPress += deltaMillis;
        boolean menuMode = world.mode() == Mode.SHOP || world.mode() == Mode.INVENTORY;
        if (config.menuDelayMillis() == 0 || !menuMode) {
            return intent;
        }
        boolean justOpened = previous != null
                && previous.mode() != Mode.SHOP && previous.mode() != Mode.INVENTORY;
        if (justOpened) {
            millisSinceMenuPress = 0;
        }
        if (!(intent instanceof Intent.Press(Key key, String ignored))) {
            return intent;
        }
        boolean closing = key == Key.CANCEL || key == Key.INVENTORY;
        long requiredGap = closing && config.menuCloseDelayMillis() > 0
                ? config.menuCloseDelayMillis()
                : config.menuDelayMillis();
        if (millisSinceMenuPress < requiredGap) {
            return new Intent.Idle("pacing menu presses for the viewer");
        }
        millisSinceMenuPress = 0;
        return intent;
    }

    private Decision decide(WorldSnapshot world) {
        for (Behavior behavior : behaviors) {
            var intent = behavior.decide(world, memory);
            if (intent.isPresent()) {
                return new Decision(behavior, intent.get());
            }
        }
        throw new IllegalStateException("no behavior claimed the tick");
    }

    private void observeTransitions(WorldSnapshot world) {
        if (previous == null) {
            return;
        }
        if (previous.mode() != Mode.DEAD && world.mode() == Mode.DEAD) {
            stats.runEnded(world.floor());
            logger.runEnded(world, stats, deathCause(previous));
        }
        if (previous.mode() == Mode.INVENTORY && world.inventory().size() < previous.inventory().size()) {
            memory.rememberDropAt(world.player().x(), world.player().y());
        }
        if (world.player().potions() < previous.player().potions() && world.mode() == Mode.PLAYING) {
            memory.observeDrink();
        }
        if (previous.mode() != Mode.SHOP && world.mode() == Mode.SHOP) {
            shopEntryGold = world.player().gold();
            shopEntryPotions = world.player().potions();
        }
        if (previous.mode() == Mode.SHOP && world.mode() != Mode.SHOP) {
            int bought = world.player().potions() - shopEntryPotions;
            if (bought > 0) {
                logger.event(world, "shopped bought=" + bought
                        + " spent=" + (shopEntryGold - world.player().gold()));
            }
        }
        if (previous.mode() == Mode.DEAD && world.mode() == Mode.PLAYING) {
            stats.reset();
            memory.resetForNewRun();
            executor.releaseEverything();
            logger.runStarted(stats.runs() + 1);
        }
        if (world.mode() == Mode.PLAYING && previous.mode() == Mode.PLAYING) {
            observePlayingEvents(world);
        }
        if (world.mode() != Mode.DEAD) {
            logEquipChange(world, Item.Slot.WEAPON);
            logEquipChange(world, Item.Slot.ARMOR);
            logEquipChange(world, Item.Slot.TRINKET);
        }
    }

    private void observePlayingEvents(WorldSnapshot world) {
        if (world.floor() != previous.floor()) {
            logger.event(world, stats.floorFinished(previous.floor()));
            return;
        }
        observeKills(world);
        observeChests(world);
        int sceneryLeft = world.tiles().count(TileType.SCENERY);
        if (sceneryLeft < previous.tiles().count(TileType.SCENERY)) {
            logger.event(world, "smash sceneryLeft=" + sceneryLeft);
        }
        if (world.inventory().size() > previous.inventory().size()) {
            Item newest = world.inventory().getLast();
            logger.event(world, "pickup item=\"" + newest.name() + "\" rarity=" + newest.rarity()
                    + " value=" + newest.value());
        }
        logEquipChange(world, Item.Slot.WEAPON);
        logEquipChange(world, Item.Slot.ARMOR);
        logEquipChange(world, Item.Slot.TRINKET);
        logSlamOutcome(world);
    }

    /**
     * A kill is only counted when it was witnessed: the enemy's tile is still visible and no enemy
     * of its type stands on or beside it (enemies step one tile per turn). An enemy merely lost to
     * the fog stays uncounted — the player couldn't know its fate either.
     */
    private void observeKills(WorldSnapshot world) {
        for (Enemy before : previous.enemies()) {
            if (world.tiles().visible(before.x(), before.y()) && survivorOf(world, before) == null) {
                stats.recordKill();
                logger.event(world, "kill type=" + before.type() + " at " + before.x() + "," + before.y());
                memory.forgetFloorObstructions();
            }
        }
    }

    private static Enemy survivorOf(WorldSnapshot world, Enemy before) {
        for (Enemy now : world.enemies()) {
            if (now.type() == before.type()
                    && Math.abs(now.x() - before.x()) <= 1 && Math.abs(now.y() - before.y()) <= 1) {
                return now;
            }
        }
        return null;
    }

    /** A chest opening is likewise only counted when its (static) tile stayed visible through the open. */
    private void observeChests(WorldSnapshot world) {
        for (Loot before : previous.loot()) {
            if (before.kind() == Loot.Kind.CHEST && world.tiles().visible(before.x(), before.y())
                    && !world.loot().contains(before)) {
                stats.recordChestOpened();
                logger.event(world, "chest opened at " + before.x() + "," + before.y());
            }
        }
    }

    private void logEquipChange(WorldSnapshot world, Item.Slot slot) {
        Item now = world.player().equipped(slot);
        Item before = previous.player().equipped(slot);
        if (now != null && (before == null || now.packed() != before.packed())) {
            logger.event(world, "equip " + slot + "=\"" + now.name() + "\" value=" + now.value()
                    + (before == null ? "" : " replaced=\"" + before.name() + "\""));
        }
    }

    private void logSlamOutcome(WorldSnapshot world) {
        BossInfo before = previous.boss();
        BossInfo now = world.boss();
        if (before == null || !before.slamImminent() || (now != null && now.slamImminent())) {
            return;
        }
        int hpLost = previous.player().hp() - world.player().hp();
        logger.event(world, "slam " + (hpLost > 0 ? "hit hpLost=" + hpLost : "dodged"));
    }

    private void handleOutcome(WorldSnapshot world, Intent intent, IntentExecutor.Outcome outcome) {
        if (outcome != IntentExecutor.Outcome.UNREACHABLE) {
            return;
        }
        switch (intent) {
            case Intent.MoveTo(int x, int y, String reason) -> blacklist(world, x, y, reason);
            case Intent.MoveAdjacentTo(int x, int y, String reason) -> blacklist(world, x, y, reason);
            case Intent.Explore(String ignored) -> {
                memory.markFrontierExhausted();
                logger.warn(world, "frontier unreachable, switching to descend");
            }
            default -> logger.warn(world, "unreachable: " + intent.reason());
        }
    }

    private void blacklist(WorldSnapshot world, int x, int y, String reason) {
        memory.markUnreachable(x, y);
        logger.warn(world, "target unreachable, blacklisting " + x + "," + y + " (" + reason + ")");
    }

    private void watchdog(WorldSnapshot world, Intent intent, long deltaMillis) {
        boolean stuckEligible = world.mode() == Mode.PLAYING && switch (intent) {
            case Intent.MoveTo _, Intent.MoveAdjacentTo _, Intent.Explore _, Intent.Idle _ -> true;
            default -> false;
        };
        long stalled = memory.trackProgress(world.player().x(), world.player().y(), deltaMillis, stuckEligible);
        if (stalled < config.stuckMillis()) {
            return;
        }
        logger.warn(world, "stuck for " + stalled + "ms on: " + intent.reason());
        switch (intent) {
            case Intent.MoveTo(int x, int y, String ignored) -> memory.markUnreachable(x, y);
            case Intent.MoveAdjacentTo(int x, int y, String ignored) -> memory.markUnreachable(x, y);
            case Intent.Explore(String ignored) -> memory.markFrontierExhausted();
            case Intent.Idle(String ignored) -> memory.forgetFloorObstructions();
            default -> { }
        }
        executor.releaseEverything();
        memory.trackProgress(-1, -1, 0, false);
    }

    private String deathCause(WorldSnapshot beforeDeath) {
        BossInfo boss = beforeDeath.boss();
        if (boss != null && boss.footprintDistance(beforeDeath.player().x(), beforeDeath.player().y()) <= 2) {
            return "boss";
        }
        Enemy nearest = beforeDeath.nearestEnemy();
        if (nearest != null && nearest.manhattanTo(beforeDeath.player().x(), beforeDeath.player().y()) <= 2) {
            return nearest.type() + "@" + nearest.x() + "," + nearest.y();
        }
        return "environment(trap/pit/fall)";
    }
}
