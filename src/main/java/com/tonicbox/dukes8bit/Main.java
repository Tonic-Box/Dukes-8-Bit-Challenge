package com.tonicbox.dukes8bit;

import java.awt.Frame;
import java.awt.Graphics;
import java.awt.KeyboardFocusManager;
import java.awt.KeyEventDispatcher;
import java.awt.image.BufferStrategy;
import java.awt.event.KeyEvent;

/**
 * Window, render loop, and keyboard input for Duke's Descent. The loop redraws
 * every frame and advances the game's time-based movement animation; turn logic
 * itself only fires when a step commits. Input forwards held-key state to the
 * game so movement glides continuously while a direction is held.
 */
public final class Main extends Frame implements KeyEventDispatcher {

    private final BufferStrategy strategy;
    private final Game game = new Game();
    private final Renderer renderer = new Renderer();

    private Main() {
        setTitle("Duke's Descent");
        setResizable(false);
        setSize(Game.VIEW_WIDTH, Game.VIEW_HEIGHT);
        setLocationRelativeTo(null);
        setVisible(true);
        createBufferStrategy(2);
        strategy = getBufferStrategy();
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);
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

    /**
     * The frame loop: advance the game's clocks by the elapsed time, redraw, honor a
     * quit request, then pace to roughly 60 frames per second.
     */
    private void run() {
        long last = System.nanoTime();
        while (true) {
            long now = System.nanoTime();
            long deltaMillis = (now - last) / 1_000_000L;
            last = now;
            game.update(deltaMillis);
            if (game.quitRequested) {
                System.exit(0);
            }
            Graphics graphics = strategy.getDrawGraphics();
            renderer.render(graphics, game);
            graphics.dispose();
            strategy.show();
            try {
                Thread.sleep(16);
            }
            //Broad exception saves 11 bytes here
            catch (Exception _) {
            }
        }
    }

    static void main() {
        new Main().run();
    }
}
