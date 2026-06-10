import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.invoke.MethodHandles;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class Main {
    private Main() {}

    public static void main(String[] args) throws Throwable {
        // The "G" resource is a DEFLATE-wrapped raw pack200 stream. Inflate to the .pack, then reconstruct the game
        // class with the JDK's built-in Pack200 unpacker. Pack200 was removed from the JDK in 14; this loader compiles
        // on JDK 11 (where the API still exists, so the calls are direct rather than reflective) and runs on JDK 11..13.
        byte[] pack = new InflaterInputStream(Main.class.getResourceAsStream("G")).readAllBytes();
        ByteArrayOutputStream jarBytes = new ByteArrayOutputStream();
        try (JarOutputStream out = new JarOutputStream(jarBytes)) {
            Pack200.newUnpacker().unpack(new ByteArrayInputStream(pack), out);
        }
        byte[] gameClass = null;
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(jarBytes.toByteArray()))) {
            for (ZipEntry entry; (entry = in.getNextEntry()) != null; ) {
                if (entry.getName().endsWith(".class")) {
                    gameClass = in.readAllBytes();
                    break;
                }
            }
        }
        MethodHandles.lookup().defineClass(gameClass).getDeclaredMethod("main").invoke(null);
    }
}
