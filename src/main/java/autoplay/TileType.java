package autoplay;

/** A dungeon tile kind, mirrored from {@code Game}'s tile constants (the enum order matches the int ids). */
public enum TileType {
    WALL,
    FLOOR,
    DOWN_STAIRS,
    UP_STAIRS,
    TRAP,
    LOCKED_DOOR,
    PIT,
    SCENERY;

    private static final TileType[] BY_ID = values();

    public static TileType fromId(int id) {
        return BY_ID[id];
    }
}
