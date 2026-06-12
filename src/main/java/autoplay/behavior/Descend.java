package autoplay.behavior;

import java.util.Optional;

import autoplay.BotMemory;
import autoplay.Mode;
import autoplay.TileType;
import autoplay.Tiles;
import autoplay.WorldSnapshot;
import autoplay.intent.Intent;

/**
 * The floor is done (no frontier left, nothing above claimed the tick): walk onto the down stairs.
 * On boss floors the stairs stay sealed until the boss falls, but the hunt behavior engages the boss
 * before this rule is ever reached. The terminal fallback of the decision tree.
 */
public final class Descend implements Behavior {

    @Override
    public String name() {
        return "Descend";
    }

    @Override
    public Optional<Intent> decide(WorldSnapshot world, BotMemory memory) {
        if (world.mode() != Mode.PLAYING) {
            return Optional.of(new Intent.Idle("nothing to do in " + world.mode()));
        }
        Tiles tiles = world.tiles();
        for (int y = 0; y < tiles.height(); y++) {
            for (int x = 0; x < tiles.width(); x++) {
                if (tiles.explored(x, y) && tiles.tile(x, y) == TileType.DOWN_STAIRS
                        && !memory.isUnreachable(x, y)) {
                    return Optional.of(new Intent.MoveTo(x, y, "floor done, descending"));
                }
            }
        }
        return Optional.of(new Intent.Idle("no frontier and no usable stairs"));
    }
}
