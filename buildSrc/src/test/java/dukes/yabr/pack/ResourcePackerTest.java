package dukes.yabr.pack;

import com.tonic.parser.ClassFile;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;
import dukes.yabr.CompiledClasses;
import dukes.yabr.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.Adler32;
import java.util.zip.InflaterInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The two hand-rolled transforms the shipped blob depends on but the other pack tests don't cover: the zlib framing
 * that {@code Main}'s {@link InflaterInputStream} decodes at startup (a wrong header or Adler-32 would fail the game
 * to load on the runtime JDK), and the Game-to-G constant-pool rename (an orphaned reference would corrupt the class).
 * {@link OptimalDeflateTest} only exercises the raw (nowrap) DEFLATE inside this wrapper, and the build-time
 * round-trip check needs a full pack200 build - these run in plain {@code test}.
 */
class ResourcePackerTest {

    @Test
    void zlibWrapDecodesThroughTheStartupInflaterInputStream() throws Exception {
        for (byte[] data : new byte[][] {filled((byte) 'x', 4096),
                "the quick brown fox ".repeat(40).getBytes(), realClassBytes()}) {
            byte[] blob = ResourcePacker.zlibWrap(data, OptimalDeflate.compress(data));

            assertEquals((byte) 0x78, blob[0], "zlib header byte 0");
            assertEquals((byte) 0x9C, blob[1], "zlib header byte 1");

            // Decode exactly as Main does - a default InflaterInputStream reads the header and verifies the Adler-32,
            // so a malformed wrapper or a wrong-endian checksum throws here rather than reaching the player.
            byte[] restored;
            try (InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(blob))) {
                restored = in.readAllBytes();
            }
            assertArrayEquals(data, restored, "round-trip for length " + data.length);

            Adler32 adler = new Adler32();
            adler.update(data);
            long sum = adler.getValue();
            int tail = blob.length - 4;
            assertEquals(sum & 0xFFFFFFFFL,
                    ((blob[tail] & 0xFFL) << 24) | ((blob[tail + 1] & 0xFFL) << 16)
                            | ((blob[tail + 2] & 0xFFL) << 8) | (blob[tail + 3] & 0xFFL),
                    "big-endian Adler-32 trailer for length " + data.length);
        }
    }

    @Test
    void renameClassInPlaceMovesEveryReferenceAndStaysLoadable(@TempDir Path dir) throws Exception {
        // A self-typed field and a factory exercise the class-name constant, the L<name>; descriptor, and a `new` ref -
        // every place the rename must reach for the result to verify.
        CompiledClasses classes = Fixtures.compile(dir, Map.of("Renamee", """
                public class Renamee {
                    Renamee self;
                    Renamee make() { return new Renamee(); }
                }
                """));
        ClassFile node = classes.get("Renamee");

        ResourcePacker.renameClassInPlace(node, "Renamee", "R");
        byte[] renamed = node.write();

        for (Item<?> item : node.getConstPool().getItems()) {
            if (item instanceof Utf8Item utf8) {
                assertFalse(utf8.getValue().equals("Renamee") || utf8.getValue().contains("LRenamee;"),
                        "orphaned reference to the old name: " + utf8.getValue());
            }
        }
        Class<?> loaded = new ByteLoader().define("R", renamed);
        assertEquals("R", loaded.getName(), "renamed class should define and verify under its new name");
    }

    private static byte[] realClassBytes() throws Exception {
        try (var in = OptimalDeflate.class.getResourceAsStream("OptimalDeflate.class")) {
            return in.readAllBytes();
        }
    }

    private static byte[] filled(byte value, int count) {
        byte[] out = new byte[count];
        Arrays.fill(out, value);
        return out;
    }

    /** Defines class bytes so the renamed class is actually verified by the JVM, not just inspected. */
    private static final class ByteLoader extends ClassLoader {
        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
