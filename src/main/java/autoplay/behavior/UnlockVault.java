package autoplay.behavior;

import java.util.Optional;

import autoplay.BotMemory;
import autoplay.Mode;
import autoplay.TileType;
import autoplay.Tiles;
import autoplay.WorldSnapshot;
import autoplay.intent.Intent;

/** Holding a key with a known vault door: walk into the door to unlock it. */
public final class UnlockVault implements Behavior {

    @Override
    public String name() {
        return "Unlock";
    }

    @Override
    public Optional<Intent> decide(WorldSnapshot world, BotMemory memory) {
        if (world.mode() != Mode.PLAYING || world.player().keys() == 0) {
            return Optional.empty();
        }
        Tiles tiles = world.tiles();
        for (int y = 0; y < tiles.height(); y++) {
            for (int x = 0; x < tiles.width(); x++) {
                if (tiles.explored(x, y) && tiles.tile(x, y) == TileType.LOCKED_DOOR
                        && !memory.isUnreachable(x, y)) {
                    return Optional.of(new Intent.MoveTo(x, y, "key in hand, unlocking vault at " + x + "," + y));
                }
            }
        }
        return Optional.empty();
    }
}
