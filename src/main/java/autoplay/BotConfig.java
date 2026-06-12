package autoplay;

/**
 * Every behavior tunable in one place. Defaults are overridable per run via system properties
 * (e.g. {@code -Dautoplay.healAt=0.4 -Dautoplay.kite=false}) so iteration doesn't need a recompile.
 *
 * @param healAtHpFraction drink a potion below this hp fraction
 * @param fleeAtHpFraction with no potions, retreat from enemies below this hp fraction
 * @param potionTarget     buy potions until carrying this many
 * @param huntRadius       chase visible enemies within this manhattan distance
 * @param kite             step away from the target while the swing recovers
 * @param stuckMillis      no tile commit for this long while pathing triggers the watchdog
 * @param menuDelayMillis      minimum gap between menu key presses (shop / inventory), so a human
 *                             watching the windowed mode can follow the gear decisions; 0 = full speed
 * @param menuCloseDelayMillis extra dwell before the closing press of a menu, leaving the end
 *                             result on screen for the viewer; 0 = no dwell
 * @param equipReviewDelayMillis wait after a pickup before opening the inventory to review gear,
 *                               so the pickup is visually complete first; 0 = immediately
 * @param reactionMillis    how long a change of plan takes to act on, like human reaction time —
 *                          telegraphed slams land more often, fleeing and healing start late; 0 = instant
 * @param kiteWhenBelowHpFraction kite between swings only when hp is below this fraction (or against
 *                          the boss) — a comfortable human trades hits instead of micro-stepping
 * @param healCooldownMillis minimum gap between potions — nobody chain-chugs a bottle per frame
 * @param rushChance probability a floor is rushed out of impatience: explore only until the stairs
 *                   turn up, fight only what's in the way, no prop farming. Rushing accumulates an
 *                   experience deficit, which is what actually kills good players in the deep floors
 */
public record BotConfig(float healAtHpFraction, float fleeAtHpFraction, int potionTarget,
                        int huntRadius, boolean kite, long stuckMillis,
                        long menuDelayMillis, long menuCloseDelayMillis, long equipReviewDelayMillis,
                        long reactionMillis, float kiteWhenBelowHpFraction, long healCooldownMillis,
                        float rushChance) {

    public static BotConfig fromSystemProperties() {
        return new BotConfig(
                floatProperty("autoplay.healAt", 0.25f),
                floatProperty("autoplay.fleeAt", 0.25f),
                intProperty("autoplay.potionTarget", 3),
                intProperty("autoplay.huntRadius", 8),
                booleanProperty("autoplay.kite", true),
                intProperty("autoplay.stuckMillis", 4000),
                intProperty("autoplay.menuDelay", 0),
                intProperty("autoplay.menuCloseDelay", 0),
                intProperty("autoplay.equipDelay", 0),
                intProperty("autoplay.reactionMillis", 400),
                floatProperty("autoplay.kiteBelow", 0.5f),
                intProperty("autoplay.healCooldown", 1500),
                floatProperty("autoplay.rushChance", 0.6f));
    }

    private static float floatProperty(String name, float fallback) {
        String value = System.getProperty(name);
        return value == null ? fallback : Float.parseFloat(value);
    }

    private static int intProperty(String name, int fallback) {
        String value = System.getProperty(name);
        return value == null ? fallback : Integer.parseInt(value);
    }

    private static boolean booleanProperty(String name, boolean fallback) {
        String value = System.getProperty(name);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }
}
