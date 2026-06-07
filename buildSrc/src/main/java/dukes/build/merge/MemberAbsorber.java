package dukes.build.merge;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Pulls the source class into the target: moves its fields and methods (repointing their own references) and
 * folds its {@code <clinit>} in. The source constructor is dropped; returns how many methods moved.
 */
final class MemberAbsorber {

    private MemberAbsorber() {
    }

    static int absorb(ClassNode target, ClassNode source) {
        target.fields.addAll(source.fields);

        MethodNode sourceStaticInit = null;
        int moved = 0;
        for (MethodNode method : source.methods) {
            if (method.name.equals("<init>")) {
                continue;
            }
            repointOwner(method, source.name, target.name);
            if (method.name.equals("<clinit>")) {
                sourceStaticInit = method;
            } else {
                target.methods.add(method);
                moved++;
            }
        }
        if (sourceStaticInit != null) {
            foldStaticInit(target, sourceStaticInit);
        }
        return moved;
    }

    /** Rewrites references to the source class (as an owner or a type) onto the target within one method. */
    private static void repointOwner(MethodNode method, String from, String to) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            switch (insn) {
                case FieldInsnNode field when field.owner.equals(from) -> field.owner = to;
                case MethodInsnNode call when call.owner.equals(from) -> call.owner = to;
                case TypeInsnNode type when type.desc.equals(from) -> type.desc = to;
                default -> {
                }
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
        AbstractInsnNode targetReturn = targetStaticInit.instructions.getLast();
        while (targetReturn != null && targetReturn.getOpcode() != Opcodes.RETURN) {
            targetReturn = targetReturn.getPrevious();
        }
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
        targetStaticInit.maxLocals = Math.max(targetStaticInit.maxLocals, base + sourceStaticInit.maxLocals);
        targetStaticInit.maxStack = Math.max(targetStaticInit.maxStack, sourceStaticInit.maxStack);
    }
}
