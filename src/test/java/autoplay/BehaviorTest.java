package autoplay;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import autoplay.behavior.DrinkPotion;
import autoplay.behavior.FightTarget;
import autoplay.behavior.FleeWhenCritical;
import autoplay.behavior.RestartOnDeath;
import autoplay.intent.Intent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The survival and combat rules fire exactly under their intended conditions. */
class BehaviorTest {

    private static final BotConfig CONFIG = new BotConfig(0.35f, 0.25f, 3, 8, true, 4000, 0, 0, 0, 0, 0.75f, 0, 0);

    private static final String[] OPEN_ROOM = {
            "#####",
            "#.@.#",
            "#...#",
            "#####"};

    @Test
    void restartClaimsTheDeathScreen() {
        WorldSnapshot world = new WorldBuilder().map(OPEN_ROOM).mode(Mode.DEAD).build();
        Optional<Intent> intent = new RestartOnDeath().decide(world, new BotMemory());
        assertTrue(intent.isPresent());
        assertEquals(Key.RESTART, ((Intent.Press) intent.get()).key());
    }

    @Test
    void drinksWhenHurtWithPotions() {
        WorldSnapshot hurt = new WorldBuilder().map(OPEN_ROOM).hp(9).potions(2).build();
        assertTrue(new DrinkPotion(CONFIG).decide(hurt, new BotMemory()).isPresent());
    }

    @Test
    void neverDrinksHealthyOrDry() {
        WorldSnapshot healthy = new WorldBuilder().map(OPEN_ROOM).hp(25).potions(2).build();
        WorldSnapshot dry = new WorldBuilder().map(OPEN_ROOM).hp(9).potions(0).build();
        assertTrue(new DrinkPotion(CONFIG).decide(healthy, new BotMemory()).isEmpty());
        assertTrue(new DrinkPotion(CONFIG).decide(dry, new BotMemory()).isEmpty());
    }

    @Test
    void swingsAtAnAdjacentEnemyWhenReady() {
        WorldSnapshot world = new WorldBuilder().map(OPEN_ROOM)
                .enemy(3, 2, EnemyType.BUG).attackReady(true).build();
        Optional<Intent> intent = new FightTarget(CONFIG).decide(world, new BotMemory());
        assertTrue(intent.isPresent());
        assertInstanceOf(Intent.AttackToward.class, intent.get());
    }

    @Test
    void kitesWhileTheSwingRecoversWhenHurt() {
        WorldSnapshot world = new WorldBuilder().map(OPEN_ROOM).hp(15)
                .enemy(3, 2, EnemyType.BUG).attackReady(false).build();
        Optional<Intent> intent = new FightTarget(CONFIG).decide(world, new BotMemory());
        assertTrue(intent.isPresent());
        assertInstanceOf(Intent.StepAway.class, intent.get());
    }

    @Test
    void tradesHitsWhileComfortablyHealthy() {
        WorldSnapshot world = new WorldBuilder().map(OPEN_ROOM).hp(30)
                .enemy(3, 2, EnemyType.BUG).attackReady(false).build();
        Optional<Intent> intent = new FightTarget(CONFIG).decide(world, new BotMemory());
        assertTrue(intent.isPresent());
        assertInstanceOf(Intent.AttackToward.class, intent.get());
    }

    @Test
    void tradesInsteadOfKitingWhenDisabled() {
        BotConfig noKite = new BotConfig(0.35f, 0.25f, 3, 8, false, 4000, 0, 0, 0, 0, 0.75f, 0, 0);
        WorldSnapshot world = new WorldBuilder().map(OPEN_ROOM).hp(15)
                .enemy(3, 2, EnemyType.BUG).attackReady(false).build();
        Optional<Intent> intent = new FightTarget(noKite).decide(world, new BotMemory());
        assertTrue(intent.isPresent());
        assertInstanceOf(Intent.AttackToward.class, intent.get());
    }

    @Test
    void fleesOnlyWhenCriticalAndDry() {
        WorldSnapshot critical = new WorldBuilder().map(OPEN_ROOM)
                .hp(5).potions(0).enemy(3, 1, EnemyType.DEADLOCK).build();
        WorldSnapshot stocked = new WorldBuilder().map(OPEN_ROOM)
                .hp(5).potions(1).enemy(3, 1, EnemyType.DEADLOCK).build();
        assertTrue(new FleeWhenCritical(CONFIG).decide(critical, new BotMemory()).isPresent());
        assertTrue(new FleeWhenCritical(CONFIG).decide(stocked, new BotMemory()).isEmpty());
    }
}
