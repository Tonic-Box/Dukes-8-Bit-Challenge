package dukes.build.pack;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.zip.Inflater;

/**
 * Packs a compiled class into a compressed resource blob and removes the original .class. The StackMapTable
 * frames are stripped (only the JVM's verifier reads them) and the frameless class is compressed with
 * {@link OptimalDeflate}; the {@code Main} loader inflates it and defines it into the trusted, unverified
 * bootstrap loader at startup, so the frames are never needed. The class file version is left untouched.
 */
public final class ResourcePacker {

    private ResourcePacker() {
    }

    /** Strips frames from {@code classFile}, compresses it into {@code resourceFile}, and deletes the class file. */
    public static int pack(File classFile, File resourceFile) throws Exception {
        ClassNode node = new ClassNode();
        new ClassReader(Files.readAllBytes(classFile.toPath())).accept(node, ClassReader.SKIP_FRAMES);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        byte[] frameless = writer.toByteArray();

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
