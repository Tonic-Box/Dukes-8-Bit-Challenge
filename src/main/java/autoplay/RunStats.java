package autoplay;

/**
 * Counters for the current run plus session-wide aggregates across runs. Updated by the orchestrator
 * by observing consecutive snapshots (a kill is an enemy-count drop on the same floor, and so on).
 */
public final class RunStats {

    private int kills;
    private int chestsOpened;
    private int potionsDrunk;
    private int potionsBought;
    private long runMillis;
    private long floorStartMillis;
    private int floorKills;

    private int runs;
    private int bestFloor;
    private long totalFloors;

    public void advance(long deltaMillis) {
        runMillis += deltaMillis;
    }

    /** Derives potion movements from the difference between two consecutive snapshots. */
    public void observe(WorldSnapshot previous, WorldSnapshot current) {
        if (previous == null || current.mode() == Mode.DEAD) {
            return;
        }
        if (current.player().potions() < previous.player().potions()) {
            potionsDrunk += previous.player().potions() - current.player().potions();
        }
        if (previous.floor() == current.floor() && current.player().potions() > previous.player().potions()
                && current.player().gold() < previous.player().gold()) {
            potionsBought += current.player().potions() - previous.player().potions();
        }
    }

    /** Kills and chest openings are observed by the orchestrator (sighting-verified), not inferred here. */
    public void recordKill() {
        kills++;
        floorKills++;
    }

    public void recordChestOpened() {
        chestsOpened++;
    }

    /** Marks a floor transition and returns a loggable summary of the floor just finished. */
    public String floorFinished(int finishedFloor) {
        String summary = "descend floor=" + (finishedFloor + 1)
                + " clearedIn=" + (runMillis - floorStartMillis) / 1000 + "s kills=" + floorKills;
        floorStartMillis = runMillis;
        floorKills = 0;
        return summary;
    }

    /** Closes the run, folds it into the session aggregates, and resets the per-run counters. */
    public void runEnded(int floorReached) {
        runs++;
        bestFloor = Math.max(bestFloor, floorReached);
        totalFloors += floorReached;
    }

    public void reset() {
        kills = 0;
        chestsOpened = 0;
        potionsDrunk = 0;
        potionsBought = 0;
        runMillis = 0;
        floorStartMillis = 0;
        floorKills = 0;
    }

    public int runs() {
        return runs;
    }

    public String summary() {
        return "kills=" + kills + " chests=" + chestsOpened + " potionsDrunk=" + potionsDrunk
                + " potionsBought=" + potionsBought + " duration=" + runMillis / 1000 + "s";
    }

    /** Session aggregates; the live run's floor counts toward best, since a run may never die. */
    public String sessionSummary(int liveRunFloor) {
        float average = runs == 0 ? 0f : (float) totalFloors / runs;
        return "session runs=" + runs + " best=" + Math.max(bestFloor, liveRunFloor)
                + " avgFloor=" + String.format("%.1f", average);
    }
}
