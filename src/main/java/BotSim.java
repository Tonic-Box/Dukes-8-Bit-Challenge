import autoplay.AutoPilot;
import autoplay.BotConfig;
import autoplay.BotLogger;
import autoplay.SimDashboard;

/**
 * Fast-forward harness: no game window, no frame pacing, no viewer delays, audio hard-muted — the
 * game and the autopilot are ticked with synthetic 16 ms deltas as fast as the CPU allows (minutes
 * of gameplay per second). A small control panel shows the current run's vitals; sessions start from
 * its Start button and the panel stays open afterward so another session can be started. A session
 * stops after {@code -Druns=N} completed runs (default 10) or {@code -DsimMinutes=M} simulated
 * minutes (default 240), whichever comes first — a run that refuses to die can't hold a session
 * hostage. The rapid tuning loop: run, read {@code logs/autoplay.log}, adjust, repeat.
 */
public final class BotSim {

    private static final long FRAME_MILLIS = 16;
    private static final long DASHBOARD_REFRESH_MILLIS = 100;

    private BotSim() {
    }

    static void main() throws InterruptedException {
        int runsPerSession = Integer.getInteger("runs", 10);
        long sessionBudgetMillis = Integer.getInteger("simMinutes", 240) * 60_000L;

        Game game = new Game();
        Sound.toggleMute();
        BotBridge bridge = new BotBridge(game);
        AutoPilot pilot = new AutoPilot(bridge, bridge, BotConfig.fromSystemProperties(), new BotLogger());
        SimDashboard dashboard = SimDashboard.openIfPossible();

        if (dashboard == null) {
            runSession(game, pilot, null, runsPerSession, sessionBudgetMillis);
            System.exit(0);
        }
        while (!Thread.currentThread().isInterrupted()) {
            dashboard.awaitStart();
            runSession(game, pilot, dashboard, runsPerSession, sessionBudgetMillis);
            dashboard.sessionFinished(pilot.sessionSummary());
        }
    }

    private static void runSession(Game game, AutoPilot pilot, SimDashboard dashboard,
                                   int runsPerSession, long sessionBudgetMillis) {
        int targetRuns = pilot.runsCompleted() + runsPerSession;
        long simulated = 0;
        long nextRefresh = 0;
        while (pilot.runsCompleted() < targetRuns && simulated < sessionBudgetMillis) {
            game.update(FRAME_MILLIS);
            pilot.tick(FRAME_MILLIS);
            simulated += FRAME_MILLIS;
            long now = System.currentTimeMillis();
            if (dashboard != null && now >= nextRefresh) {
                nextRefresh = now + DASHBOARD_REFRESH_MILLIS;
                dashboard.update(pilot.lastSnapshot(), pilot.runsCompleted() + 1, simulated);
            }
        }
        pilot.logSession();
    }
}
