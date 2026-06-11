import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.invoke.MethodHandles;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipInputStream;

public final class Main {
    private Main() {}

    public static void main(String[] args) throws Throwable {
        // The "G" resource is a DEFLATE-wrapped raw pack200 stream. Inflate it straight into the JDK's built-in
        // Pack200 unpacker (Pack200 was removed in JDK 14, so this loader compiles on JDK 11 and runs on 11..13),
        // then read the single packed class out of the rebuilt jar and define + launch it. The jar carries no
        // manifest, so the game class is its first entry.
        ByteArrayOutputStream jarBytes = new ByteArrayOutputStream();
        JarOutputStream out = new JarOutputStream(jarBytes);
        Pack200.newUnpacker().unpack(new InflaterInputStream(Main.class.getResourceAsStream("G")), out);
        out.close();
        ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(jarBytes.toByteArray()));
        in.getNextEntry();
        MethodHandles.lookup().defineClass(in.readAllBytes()).getDeclaredMethod("main").invoke(null);
    }
}
