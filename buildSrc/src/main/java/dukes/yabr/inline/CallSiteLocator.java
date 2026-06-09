package dukes.yabr.inline;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.analysis.instruction.InvokeInsn;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.constpool.InterfaceRefItem;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.MethodHandleItem;
import com.tonic.parser.constpool.MethodRefItem;
import dukes.yabr.CompiledClasses;

/**
 * Read-only call-site analysis that resolves a method and finds the single in-class call it may be inlined into.
 * The inlinability policy lives here - exactly one plain call, in the declaring class, no method-handle use.
 * Inline candidates are private, so every call to one lives in its declaring class; the scan stays within that
 * class rather than the whole program.
 */
record CallSiteLocator(CompiledClasses classes) {

    /** Finds the one method matching {@code name} (and {@code descriptor}, if given), or fails the build. */
    MethodEntry resolveCallee(String declaringClass, String name, String descriptor) {
        ClassFile ownerClass = classes.get(declaringClass);
        if (ownerClass == null) {
            throw new IllegalStateException("Class not found for inlining: " + declaringClass);
        }
        MethodEntry callee = null;
        for (MethodEntry candidate : ownerClass.getMethods()) {
            if (candidate.getName().equals(name) && (descriptor == null || candidate.getDesc().equals(descriptor))) {
                if (callee != null) {
                    throw new IllegalStateException("Ambiguous method " + declaringClass + "#" + name + " - specify a descriptor in the allowlist");
                }
                callee = candidate;
            }
        }
        if (callee == null) {
            throw new IllegalStateException("Method to inline not found: " + declaringClass + "#" + name);
        }
        return callee;
    }

    /** Returns the method holding the sole in-class call site, or fails the build explaining why it cannot be inlined. */
    MethodEntry locate(String declaringClass, MethodEntry callee) {
        ClassFile ownerClass = classes.get(declaringClass);
        if (referencedAsHandle(ownerClass, callee)) {
            throw new IllegalStateException("Cannot inline " + declaringClass + "#" + callee.getName() + ": it is referenced via a method handle (e.g. a method reference)");
        }
        Scan scan = scan(ownerClass, declaringClass, callee);
        if (scan.callCount() != 1) {
            throw new IllegalStateException("Cannot inline " + declaringClass + "#" + callee.getName() + ": found " + scan.callCount() + " call sites in its declaring class (allowlist requires exactly 1)");
        }
        return scan.caller();
    }

    /** The method holding the sole in-class call, or null if {@code callee} is not a single-call, handle-free target. */
    MethodEntry soleCaller(String declaringClass, MethodEntry callee) {
        ClassFile ownerClass = classes.get(declaringClass);
        if (referencedAsHandle(ownerClass, callee)) {
            return null;
        }
        Scan scan = scan(ownerClass, declaringClass, callee);
        return scan.callCount() == 1 ? scan.caller() : null;
    }

    private record Scan(int callCount, MethodEntry caller) {
    }

    /** Tallies the plain in-class calls to {@code callee} and remembers the last caller seen. */
    private Scan scan(ClassFile ownerClass, String declaringClass, MethodEntry callee) {
        MethodEntry caller = null;
        int callCount = 0;
        for (MethodEntry method : ownerClass.getMethods()) {
            if (method.getCodeAttribute() == null) {
                continue;
            }
            for (Instruction insn : new CodeWriter(method).getInstructions()) {
                if (callsMethod(insn, declaringClass, callee)) {
                    callCount++;
                    caller = method;
                }
            }
        }
        return new Scan(callCount, caller);
    }

    /** True when {@code insn} is a plain invoke of {@code callee} declared in {@code owner}. */
    static boolean callsMethod(Instruction insn, String owner, MethodEntry callee) {
        return insn instanceof InvokeInsn call && owner.equals(call.getOwnerClass())
                && callee.getName().equals(call.getMethodName()) && callee.getDesc().equals(call.getMethodDescriptor());
    }

    /** True if any method handle in the class's constant pool targets {@code callee} (a {@code ::} method reference). */
    private static boolean referencedAsHandle(ClassFile ownerClass, MethodEntry callee) {
        ConstPool constPool = ownerClass.getConstPool();
        for (Item<?> item : constPool.getItems()) {
            if (!(item instanceof MethodHandleItem handle)) {
                continue;
            }
            Item<?> referenced = constPool.getItem(handle.getValue().getReferenceIndex());
            String owner;
            String name;
            String descriptor;
            if (referenced instanceof MethodRefItem ref) {
                owner = ref.getOwner();
                name = ref.getName();
                descriptor = ref.getDescriptor();
            } else if (referenced instanceof InterfaceRefItem ref) {
                owner = ref.getOwner();
                name = ref.getName();
                descriptor = ref.getDescriptor();
            } else {
                continue;
            }
            if (ownerClass.getClassName().equals(owner) && callee.getName().equals(name) && callee.getDesc().equals(descriptor)) {
                return true;
            }
        }
        return false;
    }
}
