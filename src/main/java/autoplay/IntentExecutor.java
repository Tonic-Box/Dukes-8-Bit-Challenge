package autoplay;

import java.util.EnumSet;
import java.util.Set;

import autoplay.intent.Intent;

/**
 * Turns the winning intent into concrete input. The executor is the only component that touches
 * {@link Controls}; it tracks which keys it holds and releases everything that the current intent
 * no longer wants, so behaviors can change their minds without leaking held keys.
 */
public final class IntentExecutor {

    /** What executing an intent revealed, so the orchestrator can react (e.g. blacklist a target). */
    public enum Outcome { ACTING, ARRIVED, UNREACHABLE }

    private final Controls controls;
    private final Navigator navigator;
    private final Set<Key> heldKeys = EnumSet.noneOf(Key.class);

    public IntentExecutor(Controls controls, Navigator navigator) {
        this.controls = controls;
        this.navigator = navigator;
    }

    public Outcome execute(Intent intent, WorldSnapshot world) {
        return switch (intent) {
            case Intent.MoveTo(int x, int y, String ignored) -> follow(navigator.toward(world, x, y));
            case Intent.MoveAdjacentTo(int x, int y, String ignored) -> follow(navigator.adjacentTo(world, x, y));
            case Intent.AttackToward(int x, int y, String ignored) -> attackToward(world, x, y);
            case Intent.StepAway(int fromX, int fromY, String ignored) ->
                    follow(navigator.awayFrom(world, fromX, fromY));
            case Intent.Explore(String ignored) -> follow(navigator.towardNearest(world,
                    i -> world.tiles().frontier(i % world.tiles().width(), i / world.tiles().width())));
            case Intent.Press(Key key, String ignored) -> press(key);
            case Intent.Idle(String ignored) -> idle();
        };
    }

    public void releaseEverything() {
        heldKeys.clear();
        controls.releaseAll();
    }

    private Outcome follow(Navigator.Step step) {
        return switch (step.kind()) {
            case MOVE -> {
                desire(EnumSet.of(step.direction()));
                yield Outcome.ACTING;
            }
            case SMASH -> {
                desire(EnumSet.of(Key.ATTACK));
                yield Outcome.ACTING;
            }
            case ARRIVED -> {
                desire(EnumSet.noneOf(Key.class));
                yield Outcome.ARRIVED;
            }
            case UNREACHABLE -> {
                desire(EnumSet.noneOf(Key.class));
                yield Outcome.UNREACHABLE;
            }
        };
    }

    private Outcome attackToward(WorldSnapshot world, int targetX, int targetY) {
        EnumSet<Key> desired = EnumSet.of(Key.ATTACK);
        Key facing = facingKey(world.player().x(), world.player().y(), targetX, targetY);
        if (facing != null) {
            desired.add(facing);
        }
        desire(desired);
        return Outcome.ACTING;
    }

    private Outcome press(Key key) {
        desire(EnumSet.noneOf(Key.class));
        controls.pressOnce(key);
        return Outcome.ACTING;
    }

    private Outcome idle() {
        desire(EnumSet.noneOf(Key.class));
        return Outcome.ACTING;
    }

    private void desire(Set<Key> desired) {
        for (Key key : Key.values()) {
            boolean want = desired.contains(key);
            boolean have = heldKeys.contains(key);
            if (want && !have) {
                controls.hold(key);
                heldKeys.add(key);
            } else if (!want && have) {
                controls.release(key);
                heldKeys.remove(key);
            }
        }
    }

    private static Key facingKey(int fromX, int fromY, int toX, int toY) {
        int dx = toX - fromX;
        int dy = toY - fromY;
        if (dx == 0 && dy == 0) {
            return null;
        }
        if (Math.abs(dx) >= Math.abs(dy)) {
            return dx > 0 ? Key.RIGHT : Key.LEFT;
        }
        return dy > 0 ? Key.DOWN : Key.UP;
    }
}
