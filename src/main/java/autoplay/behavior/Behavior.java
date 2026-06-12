package autoplay.behavior;

import java.util.Optional;

import autoplay.BotMemory;
import autoplay.WorldSnapshot;
import autoplay.intent.Intent;

/**
 * One rule of the decision tree. Behaviors are pure: they inspect the snapshot (and the brain's
 * memory) and either claim the tick by returning an intent or pass with empty. The orchestrator
 * asks each in priority order and executes the first claim.
 */
public interface Behavior {

    String name();

    Optional<Intent> decide(WorldSnapshot world, BotMemory memory);
}
