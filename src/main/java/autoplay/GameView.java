package autoplay;

/** Read port: the brain's only window into the game. Implemented by the game-side adapter. */
public interface GameView {

    /** Captures the current game state as an immutable snapshot. */
    WorldSnapshot snapshot();
}
