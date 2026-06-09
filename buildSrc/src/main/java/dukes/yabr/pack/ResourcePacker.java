package dukes.yabr.pack;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.zip.Inflater;

/**
 * Packs a compiled class into a compressed resource blob and removes the original .class. The StackMapTable
 * frames are stripped (only the JVM's verifier reads them) and the frameless class is compressed with
 * {@link OptimalDeflate}; the {@code Main} loader inflates it and defines it into the trusted, unverified
 * bootstrap loader at startup, so the frames are never needed.
 */
public final class ResourcePacker {

    private ResourcePacker() {
    }

    /** Strips frames from {@code classFile}, compresses it into {@code resourceFile}, and deletes the class file. */
    public static int pack(File classFile, File resourceFile) throws Exception {
        // An empty pool avoids loading the JDK: collapsing descriptors and stripping frames are constant-pool/
        // attribute edits only, so no code is rewritten, no frame regeneration runs, and no type resolution is
        // needed.
        ClassFile node = new ClassPool(true).loadClass(Files.readAllBytes(classFile.toPath()));
        DescriptorCollapser.collapse(node);
        node.stripStackMapTables();
        byte[] frameless = node.write();

        byte[] compressed = OptimalDeflate.compress(frameless);
        verifyRoundTrip(frameless, compressed);

        resourceFile.getParentFile().mkdirs();
        Files.write(resourceFile.toPath(), compressed);
        Files.delete(classFile.toPath());
        return compressed.length;
    }

    /** Inflates the blob with the stock JDK inflater and confirms it matches, so a bad encode fails the build. */
    private static void verifyRoundTrip(byte[] original, byte[] compressed) throws Exception {
        byte[] restored = new byte[original.length];
        boolean ok;
        try (Inflater inflater = new Inflater(true)) {
            inflater.setInput(compressed);
            int produced = inflater.inflate(restored);
            ok = produced == original.length && inflater.finished() && Arrays.equals(original, restored);
        }
        if (!ok) {
            throw new IllegalStateException("OptimalDeflate round-trip mismatch: the packed game would not inflate correctly");
        }
    }
}
