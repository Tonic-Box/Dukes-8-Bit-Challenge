package dukes.yabr.pack;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link OptimalDeflate} is a from-scratch DEFLATE encoder whose output ships as the game blob and is decoded by
 * the stock JDK inflater at startup. A silent encoder bug would corrupt the shipped game, so these tests assert
 * the two things that matter: the stock inflater reproduces the input exactly, and the encoder never loses to the
 * stock library at the same (raw) format.
 */
class OptimalDeflateTest {

    @Test
    void roundTripsThroughTheStockInflater() throws Exception {
        for (byte[] input : sampleInputs()) {
            byte[] compressed = OptimalDeflate.compress(input);
            assertArrayEquals(input, inflate(compressed, input.length),
                    "stock inflater did not reproduce an input of length " + input.length);
        }
    }

    @Test
    void neverLargerThanStockOnCompressibleData() throws Exception {
        // The optimal parse earns its keep on real, compressible data (the ~17 KB game blob); on trivial or
        // incompressible inputs the block framing's fixed overhead can lose to stock, which is fine - that is not
        // what it ships to compress. A real compiled class file stands in for the structured game bytes.
        byte[] realClass;
        try (var in = OptimalDeflate.class.getResourceAsStream("OptimalDeflate.class")) {
            assertNotNull(in, "OptimalDeflate.class resource should be on the test classpath");
            realClass = in.readAllBytes();
        }
        byte[][] compressible = {
                filled((byte) 0, 4096),
                filled((byte) 'x', 4096),
                "the quick brown fox jumps over the lazy dog ".repeat(50).getBytes(),
                realClass,
        };
        for (byte[] input : compressible) {
            int ours = OptimalDeflate.compress(input).length;
            int stock = stockDeflate(input).length;
            assertTrue(ours <= stock,
                    "OptimalDeflate produced " + ours + " B vs stock " + stock + " B for length " + input.length);
        }
    }

    @Test
    void roundTripsFuzzedInputs() throws Exception {
        // Regression guard for the length-limiting bug: skewed code-length distributions used to over-subscribe the
        // 7-bit code-length-code tree, so the stock inflater rejected the stream ("invalid code lengths set"). Seed
        // 20260610 trial 28 (~2.8 KB, one repeated byte with ~25% noise) was the original repro and must stay green.
        Random random = new Random(20260610L);
        for (int trial = 0; trial < 300; trial++) {
            byte[] input = new byte[random.nextInt(4096) + 1];
            // A mix of repeated and random bytes so both the match-finding and literal paths of the parse run.
            byte run = (byte) random.nextInt(256);
            for (int i = 0; i < input.length; i++) {
                input[i] = random.nextInt(4) == 0 ? (byte) random.nextInt(256) : run;
            }
            byte[] compressed = OptimalDeflate.compress(input);
            assertArrayEquals(input, inflate(compressed, input.length), "fuzz trial " + trial);
        }
    }

    @Test
    void roundTripsDegenerateInputs() throws Exception {
        byte[][] degenerate = {
                new byte[] {0},
                new byte[] {(byte) 0xFF},
                new byte[65536],
                filled((byte) 'A', 1000),
        };
        for (byte[] input : degenerate) {
            assertArrayEquals(input, inflate(OptimalDeflate.compress(input), input.length));
        }
    }

    private static byte[][] sampleInputs() {
        byte[] randomBytes = new byte[8192];
        new Random(1).nextBytes(randomBytes);
        return new byte[][] {
                new byte[] {42},
                filled((byte) 0, 4096),
                filled((byte) 'x', 4096),
                "the quick brown fox jumps over the lazy dog ".repeat(50).getBytes(),
                randomBytes,
        };
    }

    private static byte[] filled(byte value, int count) {
        byte[] out = new byte[count];
        Arrays.fill(out, value);
        return out;
    }

    private static byte[] inflate(byte[] compressed, int expectedLength) throws Exception {
        try (Inflater inflater = new Inflater(true)) {
            inflater.setInput(compressed);
            byte[] out = new byte[Math.max(1, expectedLength)];
            int produced = inflater.inflate(out);
            assertTrue(inflater.finished(), "raw inflater did not finish - malformed deflate stream");
            return Arrays.copyOf(out, produced);
        }
    }

    private static byte[] stockDeflate(byte[] input) {
        try (Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true)) {
            deflater.setInput(input);
            deflater.finish();
            byte[] buffer = new byte[input.length + 64];
            int total = 0;
            while (!deflater.finished()) {
                if (total == buffer.length) {
                    buffer = Arrays.copyOf(buffer, buffer.length * 2);
                }
                total += deflater.deflate(buffer, total, buffer.length - total);
            }
            return Arrays.copyOf(buffer, total);
        }
    }
}
