package dukes.yabr.pack;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;

import java.io.File;
import java.nio.file.Files;

/**
 * Trims the raw-shipped loader class. {@code Main} is final, never instantiated, and its entry point is static, so
 * its {@code <init>} is pure weight (and {@code Main} ships uncompressed, so each byte counts directly). It also runs
 * on the JDK-11 runtime - to reach the built-in Pack200 unpacker - so it is re-stamped to class-file version 55.
 */
public final class LoaderMinifier {

    private LoaderMinifier() {
    }

    /** Removes the no-arg constructor and stamps the class to v55 (Java 11) in place; returns the new size in bytes. */
    public static int minify(File loaderClass) throws Exception {
        ClassFile node = new ClassPool(true).loadClass(Files.readAllBytes(loaderClass.toPath()));
        node.removeMethod("<init>", "()V");
        // Main uses only <=11 APIs and no invokedynamic, so its release-25 bytecode is legal at version 55.
        node.setMajorVersion(55);
        node.setMinorVersion(0);
        byte[] out = node.write();
        Files.write(loaderClass.toPath(), out);
        return out.length;
    }
}
