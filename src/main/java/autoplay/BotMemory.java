package autoplay;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** The small mutable state the brain carries between ticks; reset per run and partly per floor. */
public final class BotMemory {

    private final Set<Integer> unreachableTargets = new HashSet<>();
    private final Map<Integer, Loot> rememberedLoot = new HashMap<>();
    private final Set<Integer> droppedItemTiles = new HashSet<>();
    private final Random random = new Random();
    private boolean pendingEquipCheck;
    private long millisSincePickup;
    private long millisSinceDrink = Long.MAX_VALUE / 2;
    private boolean frontierExhausted;
    private boolean rushingFloor;
    private int knownFloor = -1;
    private int knownInventorySize;
    private int lastPlayerX = -1;
    private int lastPlayerY = -1;
    private long millisSinceProgress;

    /** Rolls per-floor knowledge when the floor changes; returns true when it did. */
    public boolean enteredFloor(int floor, float rushChance) {
        if (floor == knownFloor) {
            return false;
        }
        knownFloor = floor;
        unreachableTargets.clear();
        rememberedLoot.clear();
        droppedItemTiles.clear();
        frontierExhausted = false;
        millisSinceProgress = 0;
        rushingFloor = random.nextFloat() < rushChance;
        return true;
    }

    /** Impatience rolled on arrival: a rushed floor is played for the stairs, not the clear. */
    public boolean rushingFloor() {
        return rushingFloor;
    }

    /** Set when the navigator proved no frontier tile is reachable, so exploration yields to descending. */
    public void markFrontierExhausted() {
        frontierExhausted = true;
    }

    public boolean frontierExhausted() {
        return frontierExhausted;
    }

    /**
     * Forgets every negative finding on this floor (unreachable targets, exhausted frontier). Called
     * when the world changed in a way that may have unblocked routes (a kill freed a corridor) or as
     * the watchdog's last resort, so a stale blacklist can never deadlock a run.
     */
    public void forgetFloorObstructions() {
        unreachableTargets.clear();
        frontierExhausted = false;
    }

    /** Tracks inventory growth so a fresh pickup triggers one equip evaluation, and how long ago it was. */
    public void observeInventory(int size, long deltaMillis) {
        if (size > knownInventorySize) {
            pendingEquipCheck = true;
            millisSincePickup = 0;
        } else if (pendingEquipCheck) {
            millisSincePickup += deltaMillis;
        }
        knownInventorySize = size;
    }

    public boolean pendingEquipCheck() {
        return pendingEquipCheck;
    }

    /** How long ago the pending pickup happened, so the gear review can wait for it to settle visually. */
    public long millisSincePickup() {
        return millisSincePickup;
    }

    public void equipCheckDone() {
        pendingEquipCheck = false;
    }

    public void markUnreachable(int x, int y) {
        unreachableTargets.add(key(x, y));
    }

    public boolean isUnreachable(int x, int y) {
        return unreachableTargets.contains(key(x, y));
    }

    /** Remembers a pickup spotted on a visible tile, the way a player remembers loot after the fog returns. */
    public void rememberLoot(Loot loot) {
        rememberedLoot.put(key(loot.x(), loot.y()), loot);
    }

    public void forgetLootAt(int x, int y) {
        rememberedLoot.remove(key(x, y));
    }

    /** Every pickup sighted on this floor that has not been observed gone, including currently fogged ones. */
    public Collection<Loot> rememberedLoot() {
        return rememberedLoot.values();
    }

    /** Remembers where an unwanted item was dropped, so it is not walked back to and re-collected. */
    public void rememberDropAt(int x, int y) {
        droppedItemTiles.add(key(x, y));
    }

    public boolean droppedAt(int x, int y) {
        return droppedItemTiles.contains(key(x, y));
    }

    /** A potion was just drunk; the next one waits out the human fumble-for-the-bottle gap. */
    public void observeDrink() {
        millisSinceDrink = 0;
    }

    public long millisSinceDrink() {
        return millisSinceDrink;
    }

    /** Advances the stuck clock; any tile commit resets it. Returns the stalled time. */
    public long trackProgress(int playerX, int playerY, long deltaMillis, boolean pathing) {
        millisSinceDrink += deltaMillis;
        if (playerX != lastPlayerX || playerY != lastPlayerY || !pathing) {
            lastPlayerX = playerX;
            lastPlayerY = playerY;
            millisSinceProgress = 0;
        } else {
            millisSinceProgress += deltaMillis;
        }
        return millisSinceProgress;
    }

    public void resetForNewRun() {
        unreachableTargets.clear();
        rememberedLoot.clear();
        droppedItemTiles.clear();
        pendingEquipCheck = false;
        frontierExhausted = false;
        rushingFloor = false;
        knownFloor = -1;
        knownInventorySize = 0;
        lastPlayerX = -1;
        lastPlayerY = -1;
        millisSinceProgress = 0;
    }

    private static int key(int x, int y) {
        return y * 1024 + x;
    }
}
