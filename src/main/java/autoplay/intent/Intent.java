package autoplay.intent;

import autoplay.Key;

/**
 * A declarative decision returned by a behavior. Behaviors never touch the controls; the
 * {@code IntentExecutor} turns the winning intent into key holds and presses.
 */
public sealed interface Intent {

    /** Walk to the tile (the tile itself is the goal, e.g. loot, stairs, a locked door to bump open). */
    record MoveTo(int x, int y, String reason) implements Intent {}

    /** Walk until cardinally adjacent to the tile (chests and the merchant are interacted with from beside). */
    record MoveAdjacentTo(int x, int y, String reason) implements Intent {}

    /** Hold the attack while closing on / facing the target tile. */
    record AttackToward(int x, int y, String reason) implements Intent {}

    /** Step onto the neighbor that increases distance from the given point (kite, flee, dodge). */
    record StepAway(int fromX, int fromY, String reason) implements Intent {}

    /** Walk toward the nearest frontier tile (explored ground bordering unexplored ground). */
    record Explore(String reason) implements Intent {}

    /** A one-shot key press (menus, interactions, restart). */
    record Press(Key key, String reason) implements Intent {}

    /** Do nothing this tick. */
    record Idle(String reason) implements Intent {}

    String reason();
}
