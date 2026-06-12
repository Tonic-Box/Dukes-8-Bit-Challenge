package autoplay;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Structured, grep-friendly decision logging to the console and {@code logs/autoplay.log}.
 * Lines share one shape: {@code [run R|floor F|hp H/M|gold G|pot P|t=Ts] KIND detail...}.
 * Repeated identical decisions are throttled to one line per change or per two seconds.
 */
public final class BotLogger {

    private static final long DECIDE_REPEAT_MILLIS = 2000;
    private static final long BEHAVIOR_REPEAT_MILLIS = 10_000;
    private static final int DECIDES_PER_SECOND = 20;

    private final PrintWriter file;
    private long simulatedMillis;
    private int run = 1;
    private String lastBehavior = "";
    private long lastDecisionAtMillis;
    private String lastError = "";
    private long suppressedErrors;
    private String lastWarn = "";
    private long lastWarnAtMillis;
    private long decideBudgetSecond = -1;
    private int decideBudget;
    private long suppressedDecides;

    public BotLogger() {
        this.file = openLogFile();
    }

    private static PrintWriter openLogFile() {
        try {
            Path path = Path.of("logs", "autoplay.log");
            Files.createDirectories(path.getParent());
            Writer writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return new PrintWriter(writer, true);
        } catch (IOException error) {
            System.err.println("autoplay: file logging disabled (" + error.getMessage() + ")");
            return null;
        }
    }

    public void advance(long deltaMillis) {
        simulatedMillis += deltaMillis;
    }

    public void runStarted(int run) {
        this.run = run;
        write("RUN_START");
        lastBehavior = "";
    }

    /**
     * Logs a decision, heavily de-noised: a change of behavior logs immediately, but the same
     * behavior re-deciding (even with shifting detail, e.g. a hunt target's distance) repeats at
     * most every ten seconds. Changing decisions are additionally capped per simulated second so an
     * oscillating pair of behaviors (a livelock) cannot flood the log — the suppressed count is
     * reported instead.
     */
    public void decide(WorldSnapshot world, String behavior, String detail) {
        String line = "DECIDE " + behavior + (detail.isEmpty() ? "" : " " + detail);
        boolean sameBehavior = behavior.equals(lastBehavior);
        if (sameBehavior && simulatedMillis - lastDecisionAtMillis < BEHAVIOR_REPEAT_MILLIS) {
            return;
        }
        lastBehavior = behavior;
        long second = simulatedMillis / 1000;
        if (second != decideBudgetSecond) {
            decideBudgetSecond = second;
            if (suppressedDecides > 0) {
                write(prefix(world) + "WARN decision churn, suppressed " + suppressedDecides + " lines");
                suppressedDecides = 0;
            }
            decideBudget = DECIDES_PER_SECOND;
        }
        if (decideBudget == 0) {
            suppressedDecides++;
            return;
        }
        decideBudget--;
        lastDecisionAtMillis = simulatedMillis;
        write(prefix(world) + line);
    }

    public void event(WorldSnapshot world, String detail) {
        write(prefix(world) + "EVENT " + detail);
    }

    /** Logs a warning, with consecutive repeats of the same message throttled like decisions. */
    public void warn(WorldSnapshot world, String detail) {
        String line = "WARN " + detail;
        if (line.equals(lastWarn) && simulatedMillis - lastWarnAtMillis < DECIDE_REPEAT_MILLIS) {
            return;
        }
        lastWarn = line;
        lastWarnAtMillis = simulatedMillis;
        write(prefix(world) + line);
    }

    public void runEnded(WorldSnapshot world, RunStats stats, String cause) {
        write(prefix(world) + "RUN_END level=" + world.player().level() + " " + stats.summary()
                + " cause=" + cause + " | " + stats.sessionSummary(world.floor()));
    }

    /** Logs a failure with its trace; consecutive repeats of the same failure collapse into a counter. */
    public void error(Throwable error) {
        String description = String.valueOf(error);
        if (description.equals(lastError)) {
            suppressedErrors++;
            if (Long.bitCount(suppressedErrors) == 1) {
                write("ERROR repeated x" + suppressedErrors + ": " + description);
            }
            return;
        }
        lastError = description;
        suppressedErrors = 0;
        write("ERROR " + description);
        for (StackTraceElement element : error.getStackTrace()) {
            write("  at " + element);
        }
    }

    public void session(String summary) {
        write("SESSION " + summary.replace('\n', '|'));
    }

    private String prefix(WorldSnapshot world) {
        PlayerState player = world.player();
        return "[run " + run + "|floor " + world.floor() + "|hp " + player.hp() + "/" + player.maxHp()
                + "|gold " + player.gold() + "|pot " + player.potions()
                + "|t=" + simulatedMillis / 1000 + "s] ";
    }

    private void write(String line) {
        System.out.println(line);
        if (file != null) {
            file.println(line);
        }
    }
}
