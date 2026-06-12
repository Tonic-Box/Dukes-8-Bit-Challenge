package autoplay.behavior;

import java.util.Optional;

import autoplay.BotMemory;
import autoplay.Mode;
import autoplay.TileType;
import autoplay.WorldSnapshot;
import autoplay.intent.Intent;

/**
 * Pushes the fog of war back: walk toward the nearest explored tile that borders unexplored ground.
 * Boss floors are not worth mapping out — once the way down is uncovered, exploration stops and the
 * floor is just kill, loot, move on (the hunt and collect rules outrank the descend fallback).
 */
public final class ExploreFrontier implements Behavior {

    @Override
    public String name() {
        return "Explore";
    }

    @Override
    public Optional<Intent> decide(WorldSnapshot world, BotMemory memory) {
        if (world.mode() != Mode.PLAYING || memory.frontierExhausted() || !world.tiles().hasFrontier()) {
            return Optional.empty();
        }
        boolean stairsKnown = world.tiles().anyExplored(TileType.DOWN_STAIRS);
        if (stairsKnown && (world.bossFloor() || memory.rushingFloor())) {
            return Optional.empty();
        }
        return Optional.of(new Intent.Explore("pushing the frontier"));
    }
}
