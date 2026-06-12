package autoplay;

/**
 * The abstract inputs the bot can produce. The game-side adapter maps each to a concrete key code,
 * keeping the brain free of AWT types.
 */
public enum Key {
    UP,
    DOWN,
    LEFT,
    RIGHT,
    /** Held to swing on every cooldown; the spin hits all adjacent tiles regardless of facing. */
    ATTACK,
    /** Context action: open shop when beside the merchant, otherwise open an adjacent chest; buys in the shop. */
    INTERACT,
    /** Quaffs a potion in the world; leaves the shop; closes the inventory. */
    CANCEL,
    /** Toggles the inventory screen. */
    INVENTORY,
    /** Drops the selected inventory item. */
    DROP,
    /** Menu selection up / down (W / S in menus). */
    MENU_UP,
    MENU_DOWN,
    /** Restarts a run from the death screen. */
    RESTART
}
