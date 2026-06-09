package dukes.yabr.pack;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.constpool.ConstantDynamicItem;
import com.tonic.parser.constpool.FieldRefItem;
import com.tonic.parser.constpool.InterfaceRefItem;
import com.tonic.parser.constpool.InvokeDynamicItem;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.MethodHandleItem;
import com.tonic.parser.constpool.MethodRefItem;
import com.tonic.parser.constpool.MethodTypeItem;
import com.tonic.parser.constpool.Utf8Item;
import com.tonic.utill.Modifiers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Collapses the object-type tokens in safely-collapsible method descriptors to a one-character placeholder,
 * exploiting that the no-verify bootstrap loader never resolves descriptor types - for execution it needs only
 * the descriptor's shape (arity and slot categories), not the named types.
 *
 * <p>The collapse is applied by mutating the shared descriptor {@link Utf8Item} in place: in a deduplicated pool a
 * method's declaration and every internal call site point at the same descriptor constant, so collapsing it once
 * moves them together with no new constants and nothing orphaned. A descriptor is collapsed only when every place
 * it appears is a collapsible method's own declaration or an internal call to it - never a field, a JDK/interface
 * call, an {@code invokedynamic} site, a {@code MethodType}, or an inherited-method call, all of which the loader
 * still matches by exact descriptor. Collapsible methods are private or static (never virtually dispatched, never
 * JDK-called) and not reached through a {@code MethodHandle}. A descriptor whose collapse would make two methods
 * share a name+descriptor is dropped, and a self-reference guard fails the build on any decl/ref mismatch.
 */
public final class DescriptorCollapser {

    private static final String PLACEHOLDER = "LA;";

    private DescriptorCollapser() {
    }

    /** Collapses eligible descriptors in place; returns the number of descriptor constants rewritten. */
    public static int collapse(ClassFile classFile) {
        String owner = classFile.getClassName();
        ConstPool constPool = classFile.getConstPool();
        List<MethodEntry> methods = classFile.getMethods();
        Set<String> handleBound = handleReferencedKeys(constPool, owner);

        Set<String> collapsibleKeys = new HashSet<>();
        for (MethodEntry method : methods) {
            if (isCollapsible(method, handleBound)) {
                collapsibleKeys.add(method.getName() + '|' + method.getDesc());
            }
        }

        Set<String> unsafe = unsafeDescriptors(classFile, constPool, owner, handleBound, collapsibleKeys);

        Set<String> safe = new HashSet<>();
        for (MethodEntry method : methods) {
            String desc = method.getDesc();
            if (isCollapsible(method, handleBound) && !unsafe.contains(desc)
                    && !collapseDescriptor(desc).equals(desc)) {
                safe.add(desc);
            }
        }
        dropCollisions(methods, safe);

        Set<String> declaredBefore = liveMethodKeys(methods, constPool);
        List<MethodRefItem> selfRefs = new ArrayList<>();
        for (Item<?> item : constPool.getItems()) {
            if (item instanceof MethodRefItem ref && owner.equals(ref.getOwner())
                    && declaredBefore.contains(ref.getName() + '|' + ref.getDescriptor())) {
                selfRefs.add(ref);
            }
        }

        int collapsed = 0;
        for (Item<?> item : constPool.getItems()) {
            if (item instanceof Utf8Item utf8 && safe.contains(utf8.getValue())) {
                utf8.setValue(collapseDescriptor(utf8.getValue()));
                collapsed++;
            }
        }

        Set<String> declaredAfter = liveMethodKeys(methods, constPool);
        for (MethodRefItem ref : selfRefs) {
            String key = ref.getName() + '|' + ref.getDescriptor();
            if (!declaredAfter.contains(key)) {
                throw new IllegalStateException("Descriptor collapse left a dangling self-ref: " + owner + '.' + key);
            }
        }
        return collapsed;
    }

    private static boolean isCollapsible(MethodEntry method, Set<String> handleBound) {
        int access = method.getAccess();
        if (!Modifiers.isPrivate(access) && !Modifiers.isStatic(access)) {
            return false;
        }
        String name = method.getName();
        if (name.equals("<init>") || name.equals("<clinit>")) {
            return false;
        }
        return !handleBound.contains(name + '|' + method.getDesc());
    }

    /**
     * Descriptors that must stay real: those of fields, non-collapsible methods, JDK/interface/indy/dynamic refs,
     * {@code MethodType} constants, and Game refs that target an inherited (non-collapsible) method.
     */
    private static Set<String> unsafeDescriptors(ClassFile classFile, ConstPool constPool, String owner,
                                                 Set<String> handleBound, Set<String> collapsibleKeys) {
        Set<String> unsafe = new HashSet<>();
        for (MethodEntry method : classFile.getMethods()) {
            if (!isCollapsible(method, handleBound)) {
                unsafe.add(method.getDesc());
            }
        }
        for (FieldEntry field : classFile.getFields()) {
            unsafe.add(field.getDesc());
        }
        for (Item<?> item : constPool.getItems()) {
            if (item instanceof MethodRefItem ref) {
                if (!owner.equals(ref.getOwner())
                        || !collapsibleKeys.contains(ref.getName() + '|' + ref.getDescriptor())) {
                    unsafe.add(ref.getDescriptor());
                }
            } else if (item instanceof FieldRefItem ref) {
                unsafe.add(ref.getDescriptor());
            } else if (item instanceof InterfaceRefItem ref) {
                unsafe.add(ref.getDescriptor());
            } else if (item instanceof InvokeDynamicItem indy) {
                unsafe.add(indy.getDescriptor());
            } else if (item instanceof ConstantDynamicItem dyn) {
                unsafe.add(dyn.getDescriptor());
            } else if (item instanceof MethodTypeItem type) {
                unsafe.add(((Utf8Item) constPool.getItem(type.getValue())).getValue());
            }
        }
        return unsafe;
    }

    /** Drops any descriptor whose collapse would make two methods share a name+descriptor (a duplicate member). */
    private static void dropCollisions(List<MethodEntry> methods, Set<String> safe) {
        boolean changed = true;
        while (changed) {
            changed = false;
            Map<String, Integer> keyCount = new HashMap<>();
            for (MethodEntry method : methods) {
                String desc = safe.contains(method.getDesc()) ? collapseDescriptor(method.getDesc()) : method.getDesc();
                keyCount.merge(method.getName() + '|' + desc, 1, Integer::sum);
            }
            for (MethodEntry method : methods) {
                String desc = method.getDesc();
                if (safe.contains(desc) && keyCount.get(method.getName() + '|' + collapseDescriptor(desc)) != 1) {
                    safe.remove(desc);
                    changed = true;
                }
            }
        }
    }

    /** Owned members reachable through a {@code MethodHandle} (invokedynamic bootstrap / lambda bodies). */
    private static Set<String> handleReferencedKeys(ConstPool constPool, String owner) {
        Set<String> keys = new HashSet<>();
        for (Item<?> item : constPool.getItems()) {
            if (!(item instanceof MethodHandleItem handle)) {
                continue;
            }
            Item<?> referenced = constPool.getItem(handle.getValue().getReferenceIndex());
            if (referenced instanceof MethodRefItem ref && owner.equals(ref.getOwner())) {
                keys.add(ref.getName() + '|' + ref.getDescriptor());
            } else if (referenced instanceof FieldRefItem ref && owner.equals(ref.getOwner())) {
                keys.add(ref.getName() + '|' + ref.getDescriptor());
            }
        }
        return keys;
    }

    /** Method name+descriptor keys read live from the constant pool, bypassing any cached descriptor string. */
    private static Set<String> liveMethodKeys(List<MethodEntry> methods, ConstPool constPool) {
        Set<String> keys = new HashSet<>();
        for (MethodEntry method : methods) {
            keys.add(method.getName() + '|' + ((Utf8Item) constPool.getItem(method.getDescIndex())).getValue());
        }
        return keys;
    }

    /** Replaces every {@code L...;} object-type token with the placeholder, leaving primitives and arity intact. */
    private static String collapseDescriptor(String descriptor) {
        return descriptor.replaceAll("L[^;]+;", PLACEHOLDER);
    }
}
