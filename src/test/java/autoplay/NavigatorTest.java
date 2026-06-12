package autoplay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The navigator must honor the game's movement rules: traps avoided unless they are the only way,
 * pits and walls never crossed, scenery smashed only when it blocks the route, and stairs entered
 * only as the explicit goal (stepping on them changes floors).
 */
class NavigatorTest {

    private final Navigator navigator = new Navigator();

    @Test
    void stepsAlongTheShortestPath() {
        WorldSnapshot world = new WorldBuilder().map(
                "#####",
                "#@..#",
                "#####").build();
        Navigator.Step step = navigator.toward(world, 3, 1);
        assertEquals(Navigator.Step.Kind.MOVE, step.kind());
        assertEquals(Key.RIGHT, step.direction());
    }

    @Test
    void detoursAroundATrapWhenPossible() {
        WorldSnapshot world = new WorldBuilder().map(
                "#####",
                "#@T.#",
                "#...#",
                "#####").build();
        Navigator.Step step = navigator.toward(world, 3, 1);
        assertEquals(Navigator.Step.Kind.MOVE, step.kind());
        assertEquals(Key.DOWN, step.direction());
    }

    @Test
    void crossesATrapWhenItIsTheOnlyRoute() {
        WorldSnapshot world = new WorldBuilder().map(
                "#####",
                "#@T.#",
                "#####").build();
        Navigator.Step step = navigator.toward(world, 3, 1);
        assertEquals(Navigator.Step.Kind.MOVE, step.kind());
        assertEquals(Key.RIGHT, step.direction());
    }

    @Test
    void neverCrossesAPit() {
        WorldSnapshot world = new WorldBuilder().map(
                "#####",
                "#@P.#",
                "#####").build();
        assertEquals(Navigator.Step.Kind.UNREACHABLE, navigator.toward(world, 3, 1).kind());
    }

    @Test
    void smashesSceneryThatBlocksTheOnlyRoute() {
        WorldSnapshot world = new WorldBuilder().map(
                "#####",
                "#@S.#",
                "#####").build();
        Navigator.Step step = navigator.toward(world, 3, 1);
        assertEquals(Navigator.Step.Kind.SMASH, step.kind());
    }

    @Test
    void entersStairsOnlyAsTheGoal() {
        WorldSnapshot world = new WorldBuilder().map(
                "#####",
                "#@>.#",
                "#####").build();
        assertEquals(Navigator.Step.Kind.UNREACHABLE, navigator.toward(world, 3, 1).kind());
        Navigator.Step ontoStairs = navigator.toward(world, 2, 1);
        assertEquals(Navigator.Step.Kind.MOVE, ontoStairs.kind());
        assertEquals(Key.RIGHT, ontoStairs.direction());
    }

    @Test
    void doesNotPathIntoUnexploredGround() {
        WorldSnapshot world = new WorldBuilder().map(
                "#####",
                "#@?.#",
                "#####").build();
        assertEquals(Navigator.Step.Kind.UNREACHABLE, navigator.toward(world, 3, 1).kind());
    }

    @Test
    void adjacentGoalArrivesBesideTheTarget() {
        WorldSnapshot world = new WorldBuilder().map(
                "#####",
                "#@..#",
                "#####").build();
        assertEquals(Navigator.Step.Kind.ARRIVED, navigator.adjacentTo(world, 2, 1).kind());
        assertEquals(Navigator.Step.Kind.MOVE, navigator.adjacentTo(world, 3, 1).kind());
    }

    @Test
    void stepsAwayFromAThreat() {
        WorldSnapshot world = new WorldBuilder().map(
                "#####",
                "#.@.#",
                "#####").build();
        Navigator.Step step = navigator.awayFrom(world, 1, 1);
        assertEquals(Navigator.Step.Kind.MOVE, step.kind());
        assertEquals(Key.RIGHT, step.direction());
    }
}
