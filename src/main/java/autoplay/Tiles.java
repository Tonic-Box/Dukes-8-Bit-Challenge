package autoplay;

/**
 * An immutable view of the floor grid: tile kinds plus the explored / visible fog-of-war masks.
 * Arrays are defensively copied at snapshot time and never exposed.
 */
public final class Tiles {

    private final int width;
    private final int height;
    private final int[] map;
    private final boolean[] explored;
    private final boolean[] visible;

    public Tiles(int width, int height, int[] map, boolean[] explored, boolean[] visible) {
        this.width = width;
        this.height = height;
        this.map = map.clone();
        this.explored = explored.clone();
        this.visible = visible.clone();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    public TileType tile(int x, int y) {
        return TileType.fromId(map[y * width + x]);
    }

    public boolean explored(int x, int y) {
        return explored[y * width + x];
    }

    public boolean visible(int x, int y) {
        return visible[y * width + x];
    }

    /** Whether at least one tile of the given kind has been uncovered. */
    public boolean anyExplored(TileType kind) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (explored(x, y) && tile(x, y) == kind) {
                    return true;
                }
            }
        }
        return false;
    }

    /** How many tiles of the given kind exist on the floor (explored or not). */
    public int count(TileType kind) {
        int total = 0;
        for (int value : map) {
            if (TileType.fromId(value) == kind) {
                total++;
            }
        }
        return total;
    }

    /** Whether any explored tile borders unexplored ground, i.e. there is still a frontier to push. */
    public boolean hasFrontier() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (frontier(x, y)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** An explored, non-wall tile with at least one unexplored cardinal neighbor. */
    public boolean frontier(int x, int y) {
        if (!explored(x, y) || tile(x, y) == TileType.WALL) {
            return false;
        }
        return (inBounds(x + 1, y) && !explored(x + 1, y))
                || (inBounds(x - 1, y) && !explored(x - 1, y))
                || (inBounds(x, y + 1) && !explored(x, y + 1))
                || (inBounds(x, y - 1) && !explored(x, y - 1));
    }
}
