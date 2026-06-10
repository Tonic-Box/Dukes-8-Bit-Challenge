import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.zip.InflaterInputStream;

public final class Main {
    private Main() {}

    public static void main(String[] args) throws Throwable {
        // The "G" resource is a DEFLATE-wrapped raw pack200 stream. Inflate to the .pack, then reconstruct the game
        // class with the JDK's built-in Pack200 unpacker. Pack200 was removed from the JDK in 14, so it is reached by
        // reflection (this class compiles on JDK 25) and bound at runtime, where the jar must run on JDK <= 13.
        byte[] pack = new InflaterInputStream(Main.class.getResourceAsStream("G")).readAllBytes();
        ByteArrayOutputStream jarBytes = new ByteArrayOutputStream();
        try (JarOutputStream out = new JarOutputStream(jarBytes)) {
            Object unpacker = Class.forName("java.util.jar.Pack200").getMethod("newUnpacker").invoke(null);
            Class.forName("java.util.jar.Pack200$Unpacker")
                    .getMethod("unpack", InputStream.class, JarOutputStream.class)
                    .invoke(unpacker, new ByteArrayInputStream(pack), out);
        }
        byte[] gameClass = null;
        try (JarInputStream in = new JarInputStream(new ByteArrayInputStream(jarBytes.toByteArray()))) {
            for (JarEntry entry; (entry = in.getNextJarEntry()) != null; ) {
                if (entry.getName().endsWith(".class")) {
                    gameClass = in.readAllBytes();
                    break;
                }
            }
        }
        MethodHandles.lookup().defineClass(gameClass).getDeclaredMethod("main").invoke(null);
    }
}
