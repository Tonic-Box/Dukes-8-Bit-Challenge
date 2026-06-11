package dukes.yabr.pack;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.ExceptionsAttribute;
import com.tonic.parser.attribute.InnerClassesAttribute;
import com.tonic.parser.constpool.ClassRefItem;
import com.tonic.parser.constpool.FieldRefItem;
import com.tonic.parser.constpool.InterfaceRefItem;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.MethodRefItem;
import com.tonic.parser.constpool.NameAndTypeRefItem;
import com.tonic.parser.constpool.StringRefItem;
import com.tonic.parser.constpool.Utf8Item;

import java.io.File;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

/**
 * Trims the raw-shipped loader class. {@code Main} is final, never instantiated, and its entry point is static, so
 * its {@code <init>} is pure weight (and {@code Main} ships uncompressed, so each byte counts directly). It has no
 * invokedynamic, so it is re-stamped to class-file version 49 and its StackMapTables are stripped - the JDK-11 runtime
 * verifies pre-50 classes by type inference, needing no frames, exactly as the game blob does.
 */
public final class LoaderMinifier {

    private LoaderMinifier() {
    }

    /** Strips the dead constructor, frames, and runtime-irrelevant attributes, stamps v49; returns the new size. */
    public static int minify(File loaderClass) throws Exception {
        ClassFile node = new ClassPool(true).loadClass(Files.readAllBytes(loaderClass.toPath()));
        node.removeMethod("<init>", "()V");
        node.stripStackMapTables();
        // Main skips ProGuard (compiled standalone on JDK 11), so javac's metadata survives. The JVM ignores it at
        // run time: drop the InnerClasses entry (present only because Main names Pack200.Unpacker / Lookup) and the
        // main method's Exceptions ("throws Throwable") attribute.
        node.getClassAttributes().removeIf(attribute -> attribute instanceof InnerClassesAttribute);
        for (MethodEntry method : node.getMethods()) {
            method.getAttributes().removeIf(attribute -> attribute instanceof ExceptionsAttribute);
        }
        blankOrphanUtf8s(node);
        node.setMajorVersion(49);
        node.setMinorVersion(0);
        byte[] out = node.write();
        Files.write(loaderClass.toPath(), out);
        return out.length;
    }

    /**
     * Blanks Utf8 entries left unreferenced by the removals above (the dropped attributes, the dead {@code <init>},
     * the stripped frames). YABR doesn't compact the pool, so removing entries would shift every index; instead the
     * dead strings are set empty - the pool keeps its size and indices, no bytecode operand is rewritten, and only
     * the orphaned string bytes are reclaimed. A Utf8 is live if it names a *referenced* class, a name-and-type, a
     * string literal, a member, or the {@code Code} attribute.
     */
    private static void blankOrphanUtf8s(ClassFile node) {
        ConstPool constPool = node.getConstPool();
        // The JVM validates every CONSTANT_Class name even when the entry is unused, so a dead Class entry's name
        // can't simply be blanked. Find the classes actually referenced (this/super/an interface, or the owner of a
        // member ref) and repoint every other Class entry - e.g. Throwable, orphaned once its Exceptions attribute
        // was stripped - to this class's own (valid) name, freeing its original name Utf8 to be blanked below.
        Set<String> liveOwners = new HashSet<>();
        liveOwners.add(internalName(node.getClassName()));
        liveOwners.add(internalName(node.getSuperClassName()));
        for (Integer interfaceIndex : node.getInterfaces()) {
            if (constPool.getItem(interfaceIndex) instanceof ClassRefItem interfaceClass) {
                liveOwners.add(internalName(interfaceClass.getClassName()));
            }
        }
        for (Item<?> item : constPool.getItems()) {
            if (item instanceof MethodRefItem methodRef) {
                liveOwners.add(internalName(methodRef.getClassName()));
            } else if (item instanceof InterfaceRefItem interfaceRef) {
                liveOwners.add(internalName(interfaceRef.getOwner()));
            } else if (item instanceof FieldRefItem fieldRef) {
                liveOwners.add(internalName(fieldRef.getClassName()));
            }
        }
        int validNameIndex = ((ClassRefItem) constPool.getItem(node.getThisClass())).getNameIndex();
        for (Item<?> item : constPool.getItems()) {
            if (item instanceof ClassRefItem classRef && !liveOwners.contains(internalName(classRef.getClassName()))) {
                classRef.setNameIndex(validNameIndex);
            }
        }

        Set<String> live = new HashSet<>();
        for (Item<?> item : constPool.getItems()) {
            if (item instanceof ClassRefItem classRef) {
                live.add(classRef.getClassName());
            } else if (item instanceof NameAndTypeRefItem nameAndType) {
                live.add(nameAndType.getName());
                live.add(nameAndType.getDescriptor());
            } else if (item instanceof StringRefItem stringRef
                    && constPool.getItem(stringRef.getValue()) instanceof Utf8Item literal) {
                live.add(literal.getValue());
            }
        }
        for (MethodEntry method : node.getMethods()) {
            live.add(method.getName());
            live.add(method.getDesc());
            if (method.getCodeAttribute() != null) {
                live.add("Code");
            }
        }
        for (FieldEntry field : node.getFields()) {
            live.add(field.getName());
            live.add(field.getDesc());
        }
        for (Item<?> item : constPool.getItems()) {
            if (item instanceof Utf8Item utf8 && !utf8.getValue().isEmpty() && !live.contains(utf8.getValue())) {
                utf8.setValue("");
            }
        }
    }

    /** Normalizes a class name to internal (slash-separated) form, so owner names compare equal regardless of the
     *  dotted/slashed convention a given YABR accessor returns. */
    private static String internalName(String className) {
        return className.replace('.', '/');
    }
}
