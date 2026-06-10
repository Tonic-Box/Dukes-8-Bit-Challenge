import java.lang.invoke.MethodHandles;
import java.util.zip.InflaterInputStream;

public final class Main {
    private Main() {}

    static void main() throws Throwable {
        // The /Game blob is a zlib stream, so a plain single-arg InflaterInputStream decodes it - no `new Inflater`
        // needed. No try-with-resources either: the stream reads an in-jar resource and the JVM runs until exit, so
        // closing it earns nothing and only adds an exception table plus synthetic close/suppress handling here.
        MethodHandles.lookup().defineClass(new InflaterInputStream(
                Main.class.getResourceAsStream("/Game")).readAllBytes())
                .getDeclaredMethod("main").invoke(null);
    }
}
