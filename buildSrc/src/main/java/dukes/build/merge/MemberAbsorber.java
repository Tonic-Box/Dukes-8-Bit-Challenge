package dukes.build.merge;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Pulls the source class into the target: adopts its class hierarchy, moves its fields and methods
 * (repointing their own references), and folds its {@code <clinit>} and {@code <init>} in. A field whose
 * type is the target itself is a back-reference to the merged object and collapses to {@code this}.
 */
final class MemberAbsorber {

    private MemberAbsorber() {
    }

    static int absorb(ClassNode target, ClassNode source) {
        adoptHierarchy(target, source);
        List<String> selfFields = selfReferentialFields(source, target.name);

        MethodNode sourceStaticInit = null;
        MethodNode sourceConstructor = null;
        int moved = 0;
        for (MethodNode method : source.methods) {
            repointOwner(method, source.name, target.name);
            dropSelfFieldReads(method, target.name, selfFields);
            switch (method.name) {
                case "<clinit>" -> sourceStaticInit = method;
                case "<init>" -> sourceConstructor = method;
                default -> {
                    target.methods.add(method);
                    moved++;
                }
            }
        }
        for (FieldNode field : source.fields) {
            if (!selfFields.contains(field.name)) {
                target.fields.add(field);
            }
        }
        if (sourceStaticInit != null) {
            foldStaticInit(target, sourceStaticInit);
        }
        if (sourceConstructor != null) {
            foldConstructor(target, sourceConstructor, selfFields);
        }
        return moved;
    }

    /**
     * Gives the target the source's superclass, interfaces, and public visibility, then retargets the target's
     * own {@code super()} call to the adopted superclass. A no-op for a plain {@code Object} source with no
     * interfaces, so leaf merges (Renderer, Sound) leave the target's header untouched.
     */
    private static void adoptHierarchy(ClassNode target, ClassNode source) {
        boolean plainSuper = source.superName.equals("java/lang/Object");
        List<String> sourceInterfaces = source.interfaces == null ? List.of() : source.interfaces;
        if (plainSuper && sourceInterfaces.isEmpty()) {
            return;
        }
        String previousSuper = target.superName;
        target.superName = source.superName;
        for (String interfaceName : sourceInterfaces) {
            if (!target.interfaces.contains(interfaceName)) {
                target.interfaces.add(interfaceName);
            }
        }
        target.access |= source.access & Opcodes.ACC_PUBLIC;
        MethodNode targetConstructor = Bytecode.method(target, "<init>");
        if (targetConstructor != null) {
            for (AbstractInsnNode insn = targetConstructor.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL
                        && call.name.equals("<init>") && call.owner.equals(previousSuper)) {
                    call.owner = source.superName;
                    break;
                }
            }
        }
    }

    /** Names of the source's fields whose type is the target class - back-references to the merged object. */
    private static List<String> selfReferentialFields(ClassNode source, String targetName) {
        String selfDescriptor = "L" + targetName + ";";
        List<String> names = new ArrayList<>();
        for (FieldNode field : source.fields) {
            if (field.desc.equals(selfDescriptor)) {
                names.add(field.name);
            }
        }
        return names;
    }

    /**
     * Drops every {@code getfield} of a self-referential field, leaving the {@code aload 0} that produced the
     * receiver: once the source is the target, {@code this.self} is just {@code this}.
     */
    private static void dropSelfFieldReads(MethodNode method, String targetName, List<String> selfFields) {
        if (selfFields.isEmpty()) {
            return;
        }
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; ) {
            AbstractInsnNode next = insn.getNext();
            if (insn instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD
                    && field.owner.equals(targetName) && selfFields.contains(field.name)) {
                method.instructions.remove(insn);
            }
            insn = next;
        }
    }

    /** Rewrites references to the source class (owner, type, or lambda/method-ref handle) onto the target. */
    private static void repointOwner(MethodNode method, String from, String to) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            switch (insn) {
                case FieldInsnNode field when field.owner.equals(from) -> field.owner = to;
                case MethodInsnNode call when call.owner.equals(from) -> call.owner = to;
                case TypeInsnNode type when type.desc.equals(from) -> type.desc = to;
                case InvokeDynamicInsnNode indy -> repointHandles(indy, from, to);
                default -> {
                }
            }
        }
    }

    /** Repoints any bootstrap-argument handle (a lambda or method-reference target) from source to target. */
    private static void repointHandles(InvokeDynamicInsnNode indy, String from, String to) {
        for (int i = 0; i < indy.bsmArgs.length; i++) {
            if (indy.bsmArgs[i] instanceof Handle handle && handle.getOwner().equals(from)) {
                indy.bsmArgs[i] = new Handle(handle.getTag(), to, handle.getName(), handle.getDesc(),
                        handle.isInterface());
            }
        }
    }

    /** Splices the source's static-init body into the target's {@code <clinit>}, before its terminating return. */
    private static void foldStaticInit(ClassNode target, MethodNode sourceStaticInit) {
        MethodNode targetStaticInit = Bytecode.method(target, "<clinit>");
        if (targetStaticInit == null) {
            target.methods.add(sourceStaticInit);
            return;
        }
        int base = targetStaticInit.maxLocals;
        AbstractInsnNode targetReturn = lastReturn(targetStaticInit);
        for (AbstractInsnNode insn = sourceStaticInit.instructions.getFirst(); insn != null; ) {
            AbstractInsnNode next = insn.getNext();
            if (insn.getOpcode() == Opcodes.RETURN || insn instanceof FrameNode || insn instanceof LineNumberNode) {
                insn = next;
                continue;
            }
            if (insn instanceof VarInsnNode var) {
                var.var += base;
            } else if (insn instanceof IincInsnNode iinc) {
                iinc.var += base;
            }
            sourceStaticInit.instructions.remove(insn);
            targetStaticInit.instructions.insertBefore(targetReturn, insn);
            insn = next;
        }
        // The handler labels moved with the instructions above, so the try/catch entries stay valid.
        targetStaticInit.tryCatchBlocks.addAll(sourceStaticInit.tryCatchBlocks);
        targetStaticInit.maxLocals = Math.max(targetStaticInit.maxLocals, base + sourceStaticInit.maxLocals);
        targetStaticInit.maxStack = Math.max(targetStaticInit.maxStack, sourceStaticInit.maxStack);
    }

    /**
     * Splices the source constructor's body (everything after its {@code super()} call) into the target's
     * constructor, before its terminating return - the target's own {@code super()} already ran. The
     * self-referential field's {@code new Target()} initializer is dropped first; local 0 ({@code this}) is
     * shared, so only locals 1 and up are offset past the target's frame.
     */
    private static void foldConstructor(ClassNode target, MethodNode sourceConstructor, List<String> selfFields) {
        MethodNode targetConstructor = Bytecode.method(target, "<init>");
        if (targetConstructor == null) {
            return;
        }
        stripSelfFieldInit(sourceConstructor, target.name, selfFields);

        AbstractInsnNode body = afterSuperCall(sourceConstructor);
        AbstractInsnNode targetReturn = lastReturn(targetConstructor);
        int base = targetConstructor.maxLocals;
        for (AbstractInsnNode insn = body; insn != null; ) {
            AbstractInsnNode next = insn.getNext();
            if (insn.getOpcode() == Opcodes.RETURN || insn instanceof FrameNode || insn instanceof LineNumberNode) {
                insn = next;
                continue;
            }
            if (insn instanceof VarInsnNode var && var.var != 0) {
                var.var += base - 1;
            } else if (insn instanceof IincInsnNode iinc && iinc.var != 0) {
                iinc.var += base - 1;
            }
            sourceConstructor.instructions.remove(insn);
            targetConstructor.instructions.insertBefore(targetReturn, insn);
            insn = next;
        }
        targetConstructor.tryCatchBlocks.addAll(sourceConstructor.tryCatchBlocks);
        targetConstructor.maxLocals = Math.max(targetConstructor.maxLocals, base + Math.max(0, sourceConstructor.maxLocals - 1));
        targetConstructor.maxStack = Math.max(targetConstructor.maxStack, sourceConstructor.maxStack);
    }

    /** Removes the {@code this.self = new Target()} initializer sequence from the constructor, if present. */
    private static void stripSelfFieldInit(MethodNode constructor, String targetName, List<String> selfFields) {
        for (AbstractInsnNode insn = constructor.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof TypeInsnNode type && insn.getOpcode() == Opcodes.NEW && type.desc.equals(targetName)) {
                AbstractInsnNode end = insn;
                while (end != null && !(end instanceof FieldInsnNode put && put.getOpcode() == Opcodes.PUTFIELD
                        && put.owner.equals(targetName) && selfFields.contains(put.name))) {
                    end = end.getNext();
                }
                AbstractInsnNode start = insn.getPrevious();
                if (end != null && start != null && start.getOpcode() == Opcodes.ALOAD) {
                    Bytecode.removeRange(constructor.instructions, start, end);
                    return;
                }
            }
        }
    }

    /** The instruction after the constructor's {@code super()} (or {@code this()}) call - the start of its body. */
    private static AbstractInsnNode afterSuperCall(MethodNode constructor) {
        for (AbstractInsnNode insn = constructor.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL
                    && call.name.equals("<init>")) {
                return insn.getNext();
            }
        }
        return constructor.instructions.getFirst();
    }

    /** The method's terminating {@code return}, scanning back from the end. */
    private static AbstractInsnNode lastReturn(MethodNode method) {
        AbstractInsnNode insn = method.instructions.getLast();
        while (insn != null && insn.getOpcode() != Opcodes.RETURN) {
            insn = insn.getPrevious();
        }
        return insn;
    }
}
