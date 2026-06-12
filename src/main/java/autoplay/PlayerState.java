package autoplay;

/**
 * Duke's state this tick.
 *
 * @param attackReady    whether the spin attack is off cooldown (a held ATTACK fires immediately)
 * @param stepInProgress whether a tile-to-tile slide is still animating
 * @param equippedWeapon equipped items; null when the slot is empty
 */
public record PlayerState(int x, int y, int hp, int maxHp, int level, int gold, int potions, int keys,
                          Item equippedWeapon, Item equippedArmor, Item equippedTrinket,
                          boolean attackReady, boolean stepInProgress) {

    public float hpFraction() {
        return maxHp == 0 ? 0f : (float) hp / maxHp;
    }

    public Item equipped(Item.Slot slot) {
        return switch (slot) {
            case WEAPON -> equippedWeapon;
            case ARMOR -> equippedArmor;
            case TRINKET -> equippedTrinket;
        };
    }
}
