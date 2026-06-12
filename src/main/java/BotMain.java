/**
 * Launches the game with the autopilot attached: a normal window in which Duke plays himself.
 * Menu interactions are paced like a person at the keyboard — ~0.7 s between actions and a short
 * dwell before closing so the result stays visible (override with {@code -Dautoplay.menuDelay} /
 * {@code -Dautoplay.menuCloseDelay}, 0 = full speed). The keyboard is ignored except T / M muting.
 */
public final class BotMain {

    private BotMain() {
    }

    static void main() {
        System.setProperty("autoplay", "true");
        defaultProperty("autoplay.menuDelay", "700");
        defaultProperty("autoplay.menuCloseDelay", "500");
        defaultProperty("autoplay.equipDelay", "600");
        App.main();
    }

    private static void defaultProperty(String name, String value) {
        if (System.getProperty(name) == null) {
            System.setProperty(name, value);
        }
    }
}
