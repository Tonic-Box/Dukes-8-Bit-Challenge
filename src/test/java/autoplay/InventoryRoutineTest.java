package autoplay;

import org.junit.jupiter.api.Test;

import autoplay.behavior.InventoryRoutine;
import autoplay.intent.Intent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** The gear screen routine: walk to the best upgrade, equip it, shed outclassed gear, close when clean. */
class InventoryRoutineTest {

    private static final BotConfig CONFIG = new BotConfig(0.35f, 0.25f, 3, 8, true, 4000, 0, 0, 0, 0, 0.75f, 0, 0);

    private static final String[] ROOM = {
            "###",
            "#@#",
            "###"};

    private final InventoryRoutine routine = new InventoryRoutine(CONFIG);

    @Test
    void walksSelectionDownToTheUpgrade() {
        WorldSnapshot world = new WorldBuilder().map(ROOM).mode(Mode.INVENTORY)
                .equippedWeapon(WorldBuilder.weapon(0, 2, "Stick"))
                .carrying(WorldBuilder.weapon(0, 3, "Club"), WorldBuilder.weapon(2, 9, "Axe"))
                .selection(0)
                .build();
        assertEquals(Key.MENU_DOWN, decidedKey(world, new BotMemory()));
    }

    @Test
    void equipsOnceSelected() {
        WorldSnapshot world = new WorldBuilder().map(ROOM).mode(Mode.INVENTORY)
                .equippedWeapon(WorldBuilder.weapon(0, 2, "Stick"))
                .carrying(WorldBuilder.weapon(0, 3, "Club"), WorldBuilder.weapon(2, 9, "Axe"))
                .selection(1)
                .build();
        assertEquals(Key.INTERACT, decidedKey(world, new BotMemory()));
    }

    @Test
    void dropsGearOutclassedByTheWornItem() {
        WorldSnapshot world = new WorldBuilder().map(ROOM).mode(Mode.INVENTORY)
                .equippedWeapon(WorldBuilder.weapon(2, 9, "Axe"))
                .carrying(WorldBuilder.weapon(0, 1, "Twig"))
                .selection(0)
                .build();
        assertEquals(Key.DROP, decidedKey(world, new BotMemory()));
    }

    @Test
    void closesWhenNothingIsLeftToDo() {
        BotMemory memory = new BotMemory();
        memory.observeInventory(1, 0);
        WorldSnapshot world = new WorldBuilder().map(ROOM).mode(Mode.INVENTORY)
                .equippedWeapon(WorldBuilder.weapon(2, 9, "Axe"))
                .build();
        assertEquals(Key.CANCEL, decidedKey(world, memory));
        assertFalse(memory.pendingEquipCheck());
    }

    private Key decidedKey(WorldSnapshot world, BotMemory memory) {
        Intent intent = routine.decide(world, memory).orElseThrow();
        return ((Intent.Press) intent).key();
    }
}
