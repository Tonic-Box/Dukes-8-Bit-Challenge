import java.awt.AWTEvent;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyboardFocusManager;
import java.awt.KeyEventDispatcher;
import java.awt.image.BufferStrategy;
import java.awt.image.VolatileImage;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;

/**
 * Window, render loop, and keyboard input for Duke's Descent. The game is drawn at a fixed logical
 * resolution into an off-screen buffer, which is then stretched to fill a resizable canvas, so the
 * whole picture scales with the window without seams. The loop redraws every frame and advances the
 * game's time-based movement animation; turn logic itself only fires when a step commits.
 */
public final class App extends Frame implements KeyEventDispatcher {

    private final Canvas canvas = new Canvas();
    private final BufferStrategy strategy;
    private final Game game = new Game();
    private final Renderer renderer = new Renderer();
    // VRAM-backed scene buffer: drawing into and stretching from a VolatileImage keeps the scaled
    // blit on the GPU, so the loop allocates no per-frame pixel rasters the way a BufferedImage would.
    private VolatileImage scene;

    private App() {
        setTitle("Duke's Descent");
        setIgnoreRepaint(true);
        canvas.setIgnoreRepaint(true);
        canvas.setBackground(Color.BLACK);
        // The canvas fills the frame's content area; packing makes it exactly the logical size to start.
        canvas.setPreferredSize(new Dimension(Game.VIEW_WIDTH, Game.VIEW_HEIGHT));
        add(canvas);
        pack();
        setVisible(true);
        canvas.createBufferStrategy(2);
        strategy = canvas.getBufferStrategy();
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);
        enableEvents(AWTEvent.WINDOW_EVENT_MASK);
        setFocusTraversalKeysEnabled(false);
        canvas.setFocusTraversalKeysEnabled(false);
    }

    /**
     * Tracks held movement keys without requiring window focus; Escape quits.
     * Movement itself is driven by the loop's update, not by the key event.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getID() == KeyEvent.KEY_PRESSED) {
            game.keyDown(event.getKeyCode());
        } else if (event.getID() == KeyEvent.KEY_RELEASED) {
            game.keyUp(event.getKeyCode());
        }
        return false;
    }

    /** Closing the window from the title-bar X exits the game, like choosing Quit from the pause menu. */
    @Override
    protected void processWindowEvent(WindowEvent event) {
        if (event.getID() == WindowEvent.WINDOW_CLOSING) {
            System.exit(0);
        }
        super.processWindowEvent(event);
    }

    /**
     * The frame loop: advance the game's clocks by the elapsed time, redraw the fixed-size scene and blit it
     * scaled to the current canvas, honor a quit request, then pace to a fixed 60 FPS deadline.
     *
     * <p>Pacing design: a flat {@code sleep(16)} per frame makes the period "work + sleep", which floats with
     * the work done and with the OS timer granularity - Windows' default ~15.6 ms tick rounds a 16 ms sleep up
     * to ~31 ms and the motion visibly stutters. Instead each frame is paced to an absolute deadline: sleep the
     * bulk of the remaining time, then busy-spin the final sub-millisecond so the frame lands on the deadline
     * exactly, decoupling the cadence from both the work and the sleep's imprecision at negligible CPU cost.
     *
     * <p>The classic "perpetually-sleeping daemon to force a 1 ms global timer" trick is deliberately omitted:
     * JDK 25 already backs {@code Thread.sleep} with a high-resolution waitable timer (measured accurate to
     * ~1 ms here), so the spin alone suffices and the daemon's capturing lambda would only add bytes for no gain.
     */
    private void run() {
        long frameNanos = 1_000_000_000L / 60;
        long last = System.nanoTime();
        long deadline = last;
        while (true) {
            long now = System.nanoTime();
            long deltaMillis = (now - last) / 1_000_000L;
            last = now;
            game.update(deltaMillis);
            Sound.pump(System.currentTimeMillis());
            if (game.quitRequested) {
                System.exit(0);
            }

            // Re-render and re-blit until the VolatileImage stays valid for a whole frame; a lost or
            // incompatible surface (display change, GPU reclaim) is simply recreated and redrawn.
            int width = canvas.getWidth();
            int height = canvas.getHeight();

            // A VolatileImage's VRAM surface can be reclaimed by the GPU while we render into it, so contentsLost() is only
            // meaningful to check after the render-and-blit. do/while guarantees we always draw the frame once and then repeat
            // only if that surface was invalidated mid-draw, which a plain while (checking before the body) can't express.
            do {
                if (scene == null || scene.validate(getGraphicsConfiguration()) == VolatileImage.IMAGE_INCOMPATIBLE) {
                    scene = createVolatileImage(Game.VIEW_WIDTH, Game.VIEW_HEIGHT);
                }
                Graphics2D sceneGraphics = scene.createGraphics();
                renderer.render(sceneGraphics, game);
                sceneGraphics.dispose();

                Graphics graphics = strategy.getDrawGraphics();
                if (width > 0 && height > 0) {
                    graphics.drawImage(scene, 0, 0, width, height, null);
                }
                graphics.dispose();
                strategy.show();
            } while (scene.contentsLost());

            // Pace to the next frame deadline: sleep the bulk of the remaining time but leave a ~2 ms margin
            // for timer overshoot, then busy-spin the tail so the frame lands on the deadline exactly. If a
            // frame ran more than a whole period long, resync the deadline to now rather than chasing a spiral.
            deadline += frameNanos;
            long remaining = deadline - System.nanoTime();
            if (remaining < -frameNanos) {
                deadline = System.nanoTime();
            } else {
                if (remaining > 2_000_000L) {
                    try {
                        Thread.sleep((remaining - 2_000_000L) / 1_000_000L);
                    } catch (Exception _) {
                    }
                }
                while (System.nanoTime() < deadline) {
                    Thread.onSpinWait();
                }
            }
        }
    }

    static void main() {
        new App().run();
    }
}
