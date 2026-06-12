package autoplay;

/** One live enemy on the floor. */
public record Enemy(int x, int y, EnemyType type) {

    /** Chebyshev distance to a point; the sword's spin hits at distance 1 (2 with a reach weapon). */
    public int chebyshevTo(int px, int py) {
        return Math.max(Math.abs(x - px), Math.abs(y - py));
    }

    public int manhattanTo(int px, int py) {
        return Math.abs(x - px) + Math.abs(y - py);
    }
}
