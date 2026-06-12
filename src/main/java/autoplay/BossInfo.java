package autoplay;

/**
 * The floor boss, when active. The boss occupies a {@code size}x{@code size} footprint anchored at
 * its top-left tile; its telegraphed slam strikes the ring of tiles within {@code slamRadius} of
 * that footprint (mirroring {@code Game.bossSlamAttack}).
 *
 * @param telegraph 0 when idle, rising toward 1 while a slam winds up
 */
public record BossInfo(int x, int y, int size, int hp, int maxHp, float telegraph, int slamRadius) {

    /** Chebyshev distance from a point to the footprint; 0 inside, 1 when adjacent (melee + slam range). */
    public int footprintDistance(int px, int py) {
        int dx = Math.max(0, Math.max(x - px, px - (x + size - 1)));
        int dy = Math.max(0, Math.max(y - py, py - (y + size - 1)));
        return Math.max(dx, dy);
    }

    /** Whether a tile is struck if the winding-up slam lands. */
    public boolean inSlamDanger(int px, int py) {
        return footprintDistance(px, py) <= slamRadius;
    }

    public boolean slamImminent() {
        return telegraph > 0f;
    }
}
