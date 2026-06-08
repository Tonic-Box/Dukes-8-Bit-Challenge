package dukes.build.inline;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.HashMap;
import java.util.Map;

/**
 * The bytecode transform: replaces a call with a relocated copy of the callee's body. Pure ASM tree work -
 * no files, no knowledge of the class set; driven only by the caller, call, and callee given to {@link #splice}.
 */
final class MethodSplicer {

    private MethodSplicer() {
    }

    /** Replaces {@code call} in {@code caller} with {@code callee}'s relocated body. */
    static void splice(MethodNode caller, MethodInsnNode call, MethodNode callee) {
        int baseLocal = caller.maxLocals;
        LabelNode endLabel = new LabelNode();
        Map<LabelNode, LabelNode> labelMap = freshLabels(callee);

        InsnList inlinedBody = new InsnList();
        inlinedBody.add(argumentStores(callee, baseLocal));
        inlinedBody.add(cloneRemappedBody(callee, baseLocal, labelMap, endLabel));
        inlinedBody.add(endLabel);
        appendTryCatchBlocks(caller, callee, labelMap);

        caller.instructions.insertBefore(call, inlinedBody);
        caller.instructions.remove(call);
        caller.maxLocals = Math.max(caller.maxLocals, baseLocal + callee.maxLocals);
    }

    /** One fresh label per source label, so the inlined copy never aliases the caller's own labels. */
    private static Map<LabelNode, LabelNode> freshLabels(MethodNode callee) {
        Map<LabelNode, LabelNode> labelMap = new HashMap<>();
        for (AbstractInsnNode insn = callee.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof LabelNode label) {
                labelMap.put(label, new LabelNode());
            }
        }
        return labelMap;
    }

    /**
     * Prologue that pops the call's operands - [receiver?, arg0, ..., argN-1] on the stack - into the local
     * slots the cloned body reads from. The callee's locals map one-to-one onto caller slots from
     * {@code baseLocal} up, so the receiver lands at {@code baseLocal} and arguments follow it.
     */
    private static InsnList argumentStores(MethodNode callee, int baseLocal) {
        boolean isStatic = (callee.access & Opcodes.ACC_STATIC) != 0;
        Type[] argumentTypes = Type.getArgumentTypes(callee.desc);
        int[] argumentSlots = new int[argumentTypes.length];
        int nextSlot = isStatic ? baseLocal : baseLocal + 1;
        for (int i = 0; i < argumentTypes.length; i++) {
            argumentSlots[i] = nextSlot;
            nextSlot += argumentTypes[i].getSize();
        }

        InsnList stores = new InsnList();
        for (int i = argumentTypes.length - 1; i >= 0; i--) {
            stores.add(new VarInsnNode(argumentTypes[i].getOpcode(Opcodes.ISTORE), argumentSlots[i]));
        }
        if (!isStatic) {
            stores.add(new VarInsnNode(Opcodes.ASTORE, baseLocal));
        }
        return stores;
    }

    /**
     * The callee's instructions cloned with fresh labels, every local index shifted up by {@code baseLocal},
     * and every return rewritten as a jump to {@code endLabel} (leaving any return value on the stack, exactly
     * what the caller expects after the call). Frames and line numbers are dropped - nothing downstream reads
     * frames (the classes are written frameless and ProGuard re-preverifies) and debug info is stripped anyway.
     */
    private static InsnList cloneRemappedBody(MethodNode callee, int baseLocal, Map<LabelNode, LabelNode> labelMap, LabelNode endLabel) {
        InsnList body = new InsnList();
        for (AbstractInsnNode insn = callee.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            // Frames are written out stripped (recomputed downstream) and line numbers are stripped, so drop both.
            if (insn instanceof FrameNode || insn instanceof LineNumberNode) {
                continue;
            }
            int opcode = insn.getOpcode();
            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                body.add(new JumpInsnNode(Opcodes.GOTO, endLabel));
            } else if (insn instanceof VarInsnNode varInsn) {
                body.add(new VarInsnNode(varInsn.getOpcode(), varInsn.var + baseLocal));
            } else if (insn instanceof IincInsnNode iincInsn) {
                body.add(new IincInsnNode(iincInsn.var + baseLocal, iincInsn.incr));
            } else {
                body.add(insn.clone(labelMap));
            }
        }
        return body;
    }

    /** Copies the callee's exception handlers onto the caller, retargeted to the cloned labels. */
    private static void appendTryCatchBlocks(MethodNode caller, MethodNode callee, Map<LabelNode, LabelNode> labelMap) {
        if (callee.tryCatchBlocks == null) {
            return;
        }
        for (TryCatchBlockNode handler : callee.tryCatchBlocks) {
            caller.tryCatchBlocks.add(new TryCatchBlockNode(
                    labelMap.get(handler.start),
                    labelMap.get(handler.end),
                    labelMap.get(handler.handler),
                    handler.type
            ));
        }
    }
}
