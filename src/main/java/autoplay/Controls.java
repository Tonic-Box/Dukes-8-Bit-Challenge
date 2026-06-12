package autoplay;

/** Write port: the brain's only way to act. Implemented by the game-side adapter. */
public interface Controls {

    /** Starts holding a key until {@link #release} (movement and ATTACK are held-key driven). */
    void hold(Key key);

    void release(Key key);

    /** A single press-and-release edge, for one-shot actions (INTERACT, CANCEL, menu keys, RESTART). */
    void pressOnce(Key key);

    void releaseAll();
}
