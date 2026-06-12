package autoplay;

/**
 * One gear item, decoded from the game's packed int form at snapshot time.
 *
 * @param packed the raw packed value (template id + rarity + depth bits)
 * @param id     the template id, which determines the slot
 * @param rarity 0 common, 1 rare, 2 legendary
 * @param value  the effective attack or defense contribution at the current depth
 * @param name   the display name, for logs
 */
public record Item(int packed, int id, int rarity, int value, String name) {

    public enum Slot { WEAPON, ARMOR, TRINKET }

    public Slot slot() {
        return id < 8 ? Slot.WEAPON : id < 14 ? Slot.ARMOR : Slot.TRINKET;
    }

    /** Ranking used for equip and drop decisions: rarity first, then effective value. */
    public boolean outranks(Item other) {
        if (other == null) {
            return true;
        }
        return rarity != other.rarity ? rarity > other.rarity : value > other.value;
    }
}
