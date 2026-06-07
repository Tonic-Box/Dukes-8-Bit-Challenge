import sun.misc.Unsafe;

import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.security.ProtectionDomain;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Bootstrap launcher. The game ships as a frame-stripped, deflate-compressed blob (the "Game" resource)
 * instead of a class file. This inflates it and defines it into the bootstrap class loader, which is trusted
 * and runs no bytecode verification - the only consumer of the StackMapTable that was stripped - then calls
 * Game.main.
 */
public final class Main {

    private Main() {
    }

    static void main() throws Throwable {
        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        Unsafe unsafe = (Unsafe) theUnsafe.get(null);

        MethodHandles.Lookup lookup = (MethodHandles.Lookup) read(unsafe, MethodHandles.Lookup.class, "IMPL_LOOKUP");
        Class<?> internalUnsafeType = Class.forName("jdk.internal.misc.Unsafe");
        Object internalUnsafe = read(unsafe, internalUnsafeType, "theUnsafe");
        MethodHandle defineClass = lookup.findVirtual(internalUnsafeType, "defineClass", MethodType.methodType(
                Class.class, String.class, byte[].class, int.class,
                int.class, ClassLoader.class, ProtectionDomain.class
        ));

        byte[] bytecode;
        try (InputStream in = new InflaterInputStream(Main.class.getResourceAsStream("/Game"), new Inflater(true))) {
            bytecode = in.readAllBytes();
        }

        lookup.findStatic(
                (Class<?>) defineClass.invoke(internalUnsafe, "Game", bytecode, 0, bytecode.length, null, null),
                "main",
                MethodType.methodType(void.class)
        ).invoke();
    }

    /** Reads a static field, bypassing access control, via Unsafe's raw object-field access. */
    private static Object read(Unsafe unsafe, Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        return unsafe.getObject(unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field));
    }
}
