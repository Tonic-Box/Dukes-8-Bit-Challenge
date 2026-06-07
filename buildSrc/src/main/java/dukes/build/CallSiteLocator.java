package dukes.build;

import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Read-only call-site analysis: resolves a method and finds the single in-class call it may be inlined into.
 * The inlinability policy lives here - exactly one plain call, in the declaring class, no method-handle use.
 */
record CallSiteLocator(CompiledClasses classes) {

    /** Finds the one method matching {@code name} (and {@code descriptor}, if given), or fails the build. */
    MethodNode resolveCallee(String declaringClass, String name, String descriptor) {
        ClassNode ownerClass = classes.get(declaringClass);
        if (ownerClass == null) {
            throw new IllegalStateException("Class not found for inlining: " + declaringClass);
        }
        MethodNode callee = null;
        for (MethodNode candidate : ownerClass.methods) {
            if (candidate.name.equals(name) && (descriptor == null || candidate.desc.equals(descriptor))) {
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

    /** Returns the sole in-class call site, or fails the build explaining exactly why it cannot be inlined. */
    CallSite locate(String declaringClass, MethodNode callee) {
        Scan scan = scan(declaringClass, callee);
        if (scan.handleReferenced()) {
            throw new IllegalStateException("Cannot inline " + declaringClass + "#" + callee.name + ": it is referenced via a method handle (e.g. a method reference)");
        }
        if (scan.callCount() != 1) {
            throw new IllegalStateException("Cannot inline " + declaringClass + "#" + callee.name + ": found " + scan.callCount() + " call sites across all classes (allowlist requires exactly 1)");
        }
        if (scan.inClassCall() == null) {
            throw new IllegalStateException("Cannot inline " + declaringClass + "#" + callee.name + ": its single call site is in another class (cross-class inlining is not supported)");
        }
        return scan.inClassCall();
    }

    /** The sole in-class call site if {@code callee} is safely inlinable, or null if it is not a candidate. */
    CallSite findSoleCallSite(String declaringClass, MethodNode callee) {
        Scan scan = scan(declaringClass, callee);
        return !scan.handleReferenced() && scan.callCount() == 1 ? scan.inClassCall() : null;
    }

    /** Whole-program tally of how {@code callee} is referenced: total calls, the in-class one, and handle use. */
    private Scan scan(String declaringClass, MethodNode callee) {
        ClassNode ownerClass = classes.get(declaringClass);
        CallSite inClassCall = null;
        int callCount = 0;
        boolean handleReferenced = false;
        for (ClassNode classNode : classes.all()) {
            for (MethodNode method : classNode.methods) {
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn instanceof MethodInsnNode call && callsMethod(call, declaringClass, callee)) {
                        callCount++;
                        if (classNode == ownerClass) {
                            inClassCall = new CallSite(method, call);
                        }
                    } else if (referencesAsHandle(insn, declaringClass, callee)) {
                        handleReferenced = true;
                    }
                }
            }
        }
        return new Scan(callCount, inClassCall, handleReferenced);
    }

    private record Scan(int callCount, CallSite inClassCall, boolean handleReferenced) {
    }

    private static boolean callsMethod(MethodInsnNode call, String owner, MethodNode callee) {
        return call.owner.equals(owner) && call.name.equals(callee.name) && call.desc.equals(callee.desc);
    }

    private static boolean referencesAsHandle(AbstractInsnNode insn, String owner, MethodNode callee) {
        if (!(insn instanceof InvokeDynamicInsnNode indy)) {
            return false;
        }
        for (Object bootstrapArgument : indy.bsmArgs) {
            if (bootstrapArgument instanceof Handle handle && handle.getOwner().equals(owner) && handle.getName().equals(callee.name) && handle.getDesc().equals(callee.desc)) {
                return true;
            }
        }
        return false;
    }
}
