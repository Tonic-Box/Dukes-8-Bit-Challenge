package dukes.yabr.pack;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.Adler32;
import java.util.zip.Inflater;

/**
 * Packs a compiled class into the {@code G} resource blob and removes the original .class. The StackMapTable
 * frames are stripped and the class is re-stamped to version 49 (inference-verified, no frames needed). The frameless
 * class is then run through the JDK {@code pack200} tool, whose structure-aware transform beats DEFLATE on class
 * files; the raw {@code .pack} stream is DEFLATE-compressed for shipping. At runtime the {@code Main} loader inflates
 * it and reconstructs the class with the JDK-11 built-in Pack200 unpacker (which ships in the runtime, for free).
 */
public final class ResourcePacker {

    private ResourcePacker() {
    }

    /** Frame-strips {@code classFile}, pack200-packs and compresses it into {@code resourceFile}, deletes the class. */
    public static int pack(File classFile, File resourceFile, String pack200Exe) throws Exception {
        // An empty pool avoids loading the JDK: the rename is a constant-pool edit and the frame strip drops an
        // attribute, so no code is rewritten and no JDK type resolution is needed. The class is renamed to "G" in
        // the blob (the Main loader defines it under that name); the resource file stays "G".
        ClassFile node = new ClassPool(true).loadClass(Files.readAllBytes(classFile.toPath()));
        renameClassInPlace(node, "Game", "G");
        node.stripStackMapTables();
        byte[] frameless = node.write();
        // Stamp class-file version 49 (minor 0): versions below 50 are checked by the JVM's type-inference verifier,
        // which needs no StackMapTables - so the frames stay stripped and a plain loader can verify and define it.
        frameless[4] = 0;
        frameless[5] = 0;
        frameless[6] = 0;
        frameless[7] = 49;

        // Pack with the JDK pack200 tool (its structural transform crushes class files far below DEFLATE), then
        // DEFLATE the raw .pack and zlib-wrap it as before. Main inflates to the .pack and lets the JDK-11 runtime's
        // built-in Pack200 unpacker rebuild the class - the unpacker ships in the JDK, so it costs zero shipped bytes.
        byte[] pack = pack200(frameless, pack200Exe);
        byte[] blob = zlibWrap(pack, OptimalDeflate.compress(pack));
        verifyRoundTrip(pack, blob);

        resourceFile.getParentFile().mkdirs();
        Files.write(resourceFile.toPath(), blob);
        Files.delete(classFile.toPath());
        return blob.length;
    }

    /** Runs the v49 class through the {@code pack200} tool (no gzip) and returns the raw {@code .pack} bytes. */
    private static byte[] pack200(byte[] frameless, String pack200Exe) throws Exception {
        File dir = Files.createTempDirectory("p200").toFile();
        File jar = new File(dir, "g.jar");
        File packFile = new File(dir, "g.pack");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar))) {
            JarEntry entry = new JarEntry("G.class");
            // pack200 keeps the entry's mtime, so pin it to a constant to keep the packed blob byte-deterministic.
            entry.setTime(0L);
            jos.putNextEntry(entry);
            jos.write(frameless);
            jos.closeEntry();
        }
        Process process = new ProcessBuilder(pack200Exe, "--no-gzip", "--effort=9",
                packFile.getPath(), jar.getPath()).redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        if (process.waitFor() != 0) {
            throw new IllegalStateException("pack200 failed for the game class");
        }
        byte[] pack = Files.readAllBytes(packFile.toPath());
        jar.delete();
        packFile.delete();
        dir.delete();
        return pack;
    }

    /** Frames a raw DEFLATE stream as zlib: {@code 0x78 0x9C} header, the deflate body, then the big-endian Adler-32. */
    static byte[] zlibWrap(byte[] uncompressed, byte[] deflated) {
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
    static void renameClassInPlace(ClassFile node, String from, String to) {
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
