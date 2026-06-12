package autoplay;

/** Enemy species, mirrored from {@code Game}'s type constants (enum order matches the int ids). */
public enum EnemyType {
    BUG,
    NULL_POINTER,
    MEMORY_LEAK,
    FORK_BOMB,
    DEADLOCK,
    MIMIC;

    private static final EnemyType[] BY_ID = values();

    public static EnemyType fromId(int id) {
        return BY_ID[id];
    }
}
