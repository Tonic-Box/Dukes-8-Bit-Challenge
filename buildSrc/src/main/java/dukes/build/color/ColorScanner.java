package dukes.build.color;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only analysis: finds each {@code new Color(constants)} in a class's {@code <clinit>} stored straight
 * into a static field. Colours built from non-constant arguments are left untouched.
 */
final class ColorScanner {

    private ColorScanner() {
    }

    static List<PackedColor> scan(ClassNode owner, MethodNode staticInit) {
        List<PackedColor> colors = new ArrayList<>();
        for (AbstractInsnNode insn = staticInit.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof TypeInsnNode newColor && insn.getOpcode() == Opcodes.NEW
                    && newColor.desc.equals(PackedColor.TYPE)) {
                PackedColor color = readColor(owner, newColor);
                if (color != null) {
                    colors.add(color);
                }
            }
        }
        return colors;
    }

    /** Reads one {@code NEW Color; DUP; <constants>; INVOKESPECIAL; PUTSTATIC field} sequence, or null. */
    private static PackedColor readColor(ClassNode owner, TypeInsnNode newColor) {
        AbstractInsnNode cursor = newColor.getNext();
        if (cursor == null || cursor.getOpcode() != Opcodes.DUP) {
            return null;
        }
        List<Integer> components = new ArrayList<>();
        for (cursor = cursor.getNext(); cursor != null && !isColorInit(cursor); cursor = cursor.getNext()) {
            Integer value = constantInt(cursor);
            if (value == null) {
                return null;
            }
            components.add(value);
        }
        if (cursor == null || components.size() < 3 || components.size() > 4) {
            return null;
        }
        if (!(cursor.getNext() instanceof FieldInsnNode put) || put.getOpcode() != Opcodes.PUTSTATIC || !put.owner.equals(owner.name) || !put.desc.equals(PackedColor.DESCRIPTOR)) {
            return null;
        }
        int red = components.get(0), green = components.get(1), blue = components.get(2);
        int alpha = components.size() > 3 ? components.get(3) : 255;
        if (!isByte(red) || !isByte(green) || !isByte(blue) || !isByte(alpha)) {
            return null;
        }
        return new PackedColor(put.name, red, green, blue, alpha, newColor, put);
    }

    private static boolean isColorInit(AbstractInsnNode insn) {
        return insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL && call.owner.equals(PackedColor.TYPE) && call.name.equals("<init>");
    }

    private static boolean isByte(int value) {
        return value >= 0 && value <= 255;
    }

    private static Integer constantInt(AbstractInsnNode insn) {
        int opcode = insn.getOpcode();
        if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5) {
            return opcode - Opcodes.ICONST_0;
        }
        if (insn instanceof IntInsnNode push && (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH)) {
            return push.operand;
        }
        if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof Integer value) {
            return value;
        }
        return null;
    }
}
