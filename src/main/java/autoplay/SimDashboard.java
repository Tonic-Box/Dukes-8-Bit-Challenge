package autoplay;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Frame;
import java.awt.HeadlessException;
import java.awt.TextArea;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.Semaphore;

/**
 * The fast-forward sim's control panel: a Start button and a live readout of the current run's
 * vitals (floor, level, hp, purse, gear). Sessions are started by the button and the window stays
 * open after a session finishes so another can be started; closing the window exits.
 */
public final class SimDashboard {

    private final TextArea text = new TextArea("", 13, 46, TextArea.SCROLLBARS_NONE);
    private final Button startButton = new Button("Start");
    private final Semaphore startRequests = new Semaphore(0);

    /** Opens the panel, or returns null in a truly headless environment (the sim then runs unattended). */
    public static SimDashboard openIfPossible() {
        try {
            return new SimDashboard();
        } catch (HeadlessException headless) {
            return null;
        }
    }

    private SimDashboard() {
        Frame frame = new Frame("Duke's Descent — autopilot sim");
        text.setEditable(false);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        text.setText("press Start to begin a session");
        startButton.addActionListener(event -> {
            startButton.setEnabled(false);
            startRequests.release();
        });
        frame.setLayout(new BorderLayout());
        frame.add(text, BorderLayout.CENTER);
        frame.add(startButton, BorderLayout.SOUTH);
        frame.pack();
        frame.setVisible(true);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent ignored) {
                System.exit(0);
            }
        });
    }

    /** Blocks the sim thread until the Start button is pressed. */
    public void awaitStart() throws InterruptedException {
        startRequests.acquire();
    }

    /** Re-arms the Start button and shows the finished session's summary. */
    public void sessionFinished(String summary) {
        EventQueue.invokeLater(() -> {
            text.setText("session finished\n\n" + summary + "\n\npress Start to run another");
            startButton.setLabel("Start again");
            startButton.setEnabled(true);
        });
    }

    /** Pushes the latest state to the readout; safe to call from the sim thread (snapshots are immutable). */
    public void update(WorldSnapshot world, int run, long simulatedMillis) {
        if (world == null) {
            return;
        }
        String rendered = render(world, run, simulatedMillis);
        EventQueue.invokeLater(() -> text.setText(rendered));
    }

    private static String render(WorldSnapshot world, int run, long simulatedMillis) {
        PlayerState player = world.player();
        return "run      " + run + '\n'
                + "sim time " + simulatedMillis / 60000 + 'm' + simulatedMillis / 1000 % 60 + "s\n\n"
                + "floor    " + world.floor() + (world.bossFloor() ? "  (boss)" : "") + '\n'
                + "level    " + player.level() + '\n'
                + "hp       " + player.hp() + " / " + player.maxHp() + '\n'
                + "gold     " + player.gold() + '\n'
                + "potions  " + player.potions() + '\n'
                + "keys     " + player.keys() + "\n\n"
                + "weapon   " + gear(player.equippedWeapon()) + '\n'
                + "armor    " + gear(player.equippedArmor()) + '\n'
                + "trinket  " + gear(player.equippedTrinket()) + '\n';
    }

    private static String gear(Item item) {
        return item == null ? "-" : item.name();
    }
}
