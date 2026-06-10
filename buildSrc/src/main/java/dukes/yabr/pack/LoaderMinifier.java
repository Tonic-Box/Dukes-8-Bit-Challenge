package dukes.yabr.pack;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;

import java.io.File;
import java.nio.file.Files;

/**
 * Trims the raw-shipped loader class. {@code Main} is final, never instantiated, and its entry point is static, so
 * its {@code <init>} is pure weight (and {@code Main} ships uncompressed, so each byte counts directly). It has no
 * invokedynamic, so it is re-stamped to class-file version 49 and its StackMapTables are stripped - the JDK-11 runtime
 * verifies pre-50 classes by type inference, needing no frames, exactly as the game blob does.
 */
public final class LoaderMinifier {

    private LoaderMinifier() {
    }

    /** Removes the no-arg constructor, strips frames, and stamps the class to v49 in place; returns the new size. */
    public static int minify(File loaderClass) throws Exception {
        ClassFile node = new ClassPool(true).loadClass(Files.readAllBytes(loaderClass.toPath()));
        node.removeMethod("<init>", "()V");
        node.stripStackMapTables();
        node.setMajorVersion(49);
        node.setMinorVersion(0);
        byte[] out = node.write();
        Files.write(loaderClass.toPath(), out);
        return out.length;
    }
}
