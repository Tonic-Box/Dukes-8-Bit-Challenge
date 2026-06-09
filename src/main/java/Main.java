import sun.misc.Unsafe;
import java.lang.invoke.*;
import java.lang.reflect.Field;
import java.security.ProtectionDomain;
import java.util.zip.*;

public final class Main {
    private Main() {}

    static void main() throws Throwable {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Unsafe unsafe = (Unsafe) field.get(null);

        MethodHandles.Lookup lookup = (MethodHandles.Lookup) read(unsafe, MethodHandles.Lookup.class, "IMPL_LOOKUP");
        Class<?> internalUnsafeType = Class.forName("jdk.internal.misc.Unsafe");
        Object internalUnsafe = read(unsafe, internalUnsafeType, "theUnsafe");
        MethodHandle defineClass = lookup.findVirtual(internalUnsafeType, "defineClass", MethodType.methodType(
                Class.class, String.class, byte[].class,
                int.class, int.class, ClassLoader.class, ProtectionDomain.class));

        byte[] bytecode;
        try (var inputStream = new InflaterInputStream(Main.class.getResourceAsStream("/Game"), new Inflater(true))) {
            bytecode = inputStream.readAllBytes();
        }

        lookup.findStatic(
                (Class<?>) defineClass.invoke(internalUnsafe, "G", bytecode, 0, bytecode.length, null, null),
                "main", MethodType.methodType(void.class)
        ).invoke();
    }

    private static Object read(Unsafe unsafe, Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        return unsafe.getObject(unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field));
    }
}