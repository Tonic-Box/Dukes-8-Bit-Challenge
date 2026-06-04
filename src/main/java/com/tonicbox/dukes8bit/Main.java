package com.tonicbox.dukes8bit;

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
import java.awt.image.BufferedImage;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;

/**
 * Window, render loop, and keyboard input for Duke's Descent. The game is drawn at a fixed logical
 * resolution into an off-screen buffer, which is then stretched to fill a resizable canvas, so the
 * whole picture scales with the window without seams. The loop redraws every frame and advances the
 * game's time-based movement animation; turn logic itself only fires when a step commits.
 */
public final class Main extends Frame implements KeyEventDispatcher {

    private final Canvas canvas = new Canvas();
    private final BufferStrategy strategy;
    private final Game game = new Game();
    private final Renderer renderer = new Renderer();
    private final BufferedImage scene =
            new BufferedImage(Game.VIEW_WIDTH, Game.VIEW_HEIGHT, BufferedImage.TYPE_INT_RGB);
    private final Graphics2D sceneGraphics = scene.createGraphics();

    private Main() {
        setTitle("Duke's Descent");
        setIgnoreRepaint(true);
        canvas.setIgnoreRepaint(true);
        canvas.setBackground(Color.BLACK);
        // The canvas fills the frame's content area; packing makes it exactly the logical size to start.
        canvas.setPreferredSize(new Dimension(Game.VIEW_WIDTH, Game.VIEW_HEIGHT));
        add(canvas);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
        canvas.createBufferStrategy(2);
        strategy = canvas.getBufferStrategy();
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);
        enableEvents(AWTEvent.WINDOW_EVENT_MASK);
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
     * The frame loop: advance the game's clocks by the elapsed time, redraw the fixed-size scene and
     * blit it scaled to the current canvas, honor a quit request, then pace to roughly 60 frames per second.
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
            renderer.render(sceneGraphics, game);

            int width = canvas.getWidth();
            int height = canvas.getHeight();
            Graphics graphics = strategy.getDrawGraphics();
            if (width > 0 && height > 0) {
                graphics.drawImage(scene, 0, 0, width, height, null);
            }
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
