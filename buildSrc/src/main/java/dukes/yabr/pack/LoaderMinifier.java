package dukes.yabr.pack;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;

import java.io.File;
import java.nio.file.Files;

/**
 * Strips the dead default constructor from the loader class. {@code Main} is final, never instantiated, and its
 * entry point is static, so its {@code <init>} is pure weight - and {@code Main} ships raw (uncompressed), so every
 * byte removed from it counts directly against the measured size.
 */
public final class LoaderMinifier {

    private LoaderMinifier() {
    }

    /** Removes the no-arg constructor from {@code loaderClass} in place; returns the rewritten class size in bytes. */
    public static int minify(File loaderClass) throws Exception {
        ClassFile node = new ClassPool(true).loadClass(Files.readAllBytes(loaderClass.toPath()));
        node.removeMethod("<init>", "()V");
        byte[] out = node.write();
        Files.write(loaderClass.toPath(), out);
        return out.length;
    }
}
