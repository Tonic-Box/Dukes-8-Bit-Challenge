package autoplay.behavior;

import java.util.Optional;

import autoplay.BotMemory;
import autoplay.Mode;
import autoplay.TileType;
import autoplay.Tiles;
import autoplay.WorldSnapshot;
import autoplay.intent.Intent;

/**
 * Farms breakable scenery: walks to the nearest known prop and smashes it (the spin hits the whole
 * surrounding ring, so any adjacency works). Broken props sometimes drop loot, which the collect
 * behavior then picks up.
 */
public final class BreakScenery implements Behavior {

    @Override
    public String name() {
        return "Smash";
    }

    @Override
    public Optional<Intent> decide(WorldSnapshot world, BotMemory memory) {
        if (world.mode() != Mode.PLAYING || world.bossFloor() || memory.rushingFloor()) {
            return Optional.empty();
        }
        int px = world.player().x();
        int py = world.player().y();
        Tiles tiles = world.tiles();
        int targetX = -1;
        int targetY = -1;
        int targetDistance = Integer.MAX_VALUE;
        for (int y = 0; y < tiles.height(); y++) {
            for (int x = 0; x < tiles.width(); x++) {
                if (tiles.tile(x, y) != TileType.SCENERY || !tiles.explored(x, y)
                        || memory.isUnreachable(x, y)) {
                    continue;
                }
                int distance = Math.abs(x - px) + Math.abs(y - py);
                if (distance < targetDistance) {
                    targetDistance = distance;
                    targetX = x;
                    targetY = y;
                }
            }
        }
        if (targetX < 0) {
            return Optional.empty();
        }
        if (Math.max(Math.abs(targetX - px), Math.abs(targetY - py)) <= 1) {
            return Optional.of(new Intent.AttackToward(targetX, targetY,
                    "smashing scenery at " + targetX + "," + targetY));
        }
        return Optional.of(new Intent.MoveAdjacentTo(targetX, targetY,
                "heading to scenery at " + targetX + "," + targetY));
    }
}
