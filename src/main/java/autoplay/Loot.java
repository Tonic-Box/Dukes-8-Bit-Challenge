package autoplay;

/**
 * One pickup lying on the floor: a key, an unopened chest, or an item. Only the kind is exposed —
 * the player sees a sprite, not stats, so the bot may not know what an item is until picked up.
 */
public record Loot(int x, int y, Kind kind) {

    public enum Kind { KEY, CHEST, ITEM }

    public int manhattanTo(int px, int py) {
        return Math.abs(x - px) + Math.abs(y - py);
    }
}
