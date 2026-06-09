package dukes.yabr.pack;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;

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
        // An empty pool avoids loading the JDK: the rename and descriptor collapse are constant-pool edits and the
        // frame strip drops an attribute, so no code is rewritten and no JDK type resolution is needed. The class
        // is renamed to "G" in the blob (the Main loader defines it under that name); the resource file stays "Game".
        ClassFile node = new ClassPool(true).loadClass(Files.readAllBytes(classFile.toPath()));
        renameClassInPlace(node, "Game", "G");
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
