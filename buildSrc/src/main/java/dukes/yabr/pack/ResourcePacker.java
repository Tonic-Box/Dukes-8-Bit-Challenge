package dukes.yabr.pack;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.zip.Adler32;
import java.util.zip.Inflater;

/**
 * Packs a compiled class into a compressed resource blob and removes the original .class. The StackMapTable
 * frames are stripped and the class is re-stamped to version 49, whose type-inference verifier needs no frames;
 * the frameless class is compressed with {@link OptimalDeflate}, and the {@code Main} loader inflates and defines
 * it with a plain lookup at startup, so the frames are never needed.
 */
public final class ResourcePacker {

    private ResourcePacker() {
    }

    /** Strips frames from {@code classFile}, compresses it into {@code resourceFile}, and deletes the class file. */
    public static int pack(File classFile, File resourceFile) throws Exception {
        // An empty pool avoids loading the JDK: the rename is a constant-pool edit and the frame strip drops an
        // attribute, so no code is rewritten and no JDK type resolution is needed. The class is renamed to "G" in
        // the blob (the Main loader defines it under that name); the resource file stays "Game".
        ClassFile node = new ClassPool(true).loadClass(Files.readAllBytes(classFile.toPath()));
        renameClassInPlace(node, "Game", "G");
        node.stripStackMapTables();
        byte[] frameless = node.write();
        // Stamp class-file version 49 (minor 0): versions below 50 are checked by the JVM's type-inference
        // verifier, which needs no StackMapTables - so the frames stay stripped while a plain (non-Unsafe) loader
        // can verify and define the class. Descriptor collapse is intentionally not run here; the real verifier
        // would reject its synthetic "LA;" placeholder type.
        frameless[4] = 0;
        frameless[5] = 0;
        frameless[6] = 0;
        frameless[7] = 49;

        // Wrap the raw DEFLATE stream in a minimal zlib container (2-byte header + 4-byte Adler-32 trailer) so the
        // Main loader can inflate it with a plain single-arg InflaterInputStream - dropping `new Inflater(true)` from
        // the raw-shipped loader saves more than the 6 wrapper bytes cost on the already-compressed blob.
        byte[] blob = zlibWrap(frameless, OptimalDeflate.compress(frameless));
        verifyRoundTrip(frameless, blob);

        resourceFile.getParentFile().mkdirs();
        Files.write(resourceFile.toPath(), blob);
        Files.delete(classFile.toPath());
        return blob.length;
    }

    /** Frames a raw DEFLATE stream as zlib: {@code 0x78 0x9C} header, the deflate body, then the big-endian Adler-32. */
    private static byte[] zlibWrap(byte[] uncompressed, byte[] deflated) {
        Adler32 adler = new Adler32();
        adler.update(uncompressed);
        long sum = adler.getValue();
        byte[] out = new byte[2 + deflated.length + 4];
        out[0] = 0x78;
        out[1] = (byte) 0x9C;
        System.arraycopy(deflated, 0, out, 2, deflated.length);
        int tail = 2 + deflated.length;
        out[tail] = (byte) (sum >>> 24);
        out[tail + 1] = (byte) (sum >>> 16);
        out[tail + 2] = (byte) (sum >>> 8);
        out[tail + 3] = (byte) sum;
        return out;
    }

    /**
     * Renames the class in the blob by mutating its name and type-descriptor constants in place - the declaration,
     * every self-reference, and every {@code L<from>;} descriptor token move together, with nothing orphaned (no
     * constant-pool compaction is available). The {@code Main} loader defines the class under {@code to}; the
     * resource file keeps its original name.
     */
    private static void renameClassInPlace(ClassFile node, String from, String to) {
        String fromToken = "L" + from + ";";
        String toToken = "L" + to + ";";
        for (Item<?> item : node.getConstPool().getItems()) {
            if (item instanceof Utf8Item utf8) {
                String value = utf8.getValue();
                if (value.equals(from)) {
                    utf8.setValue(to);
                } else if (value.contains(fromToken)) {
                    utf8.setValue(value.replace(fromToken, toToken));
                }
            }
        }
    }

    /** Inflates the zlib blob with the stock JDK inflater and confirms it matches, so a bad encode fails the build. */
    private static void verifyRoundTrip(byte[] original, byte[] blob) throws Exception {
        byte[] restored = new byte[original.length];
        boolean ok;
        try (Inflater inflater = new Inflater()) {
            inflater.setInput(blob);
            int produced = inflater.inflate(restored);
            ok = produced == original.length && inflater.finished() && Arrays.equals(original, restored);
        }
        if (!ok) {
            throw new IllegalStateException("OptimalDeflate round-trip mismatch: the packed game would not inflate correctly");
        }
    }
}
