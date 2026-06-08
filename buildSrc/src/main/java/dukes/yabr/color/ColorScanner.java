package dukes.yabr.color;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.BipushInstruction;
import com.tonic.analysis.instruction.DupInstruction;
import com.tonic.analysis.instruction.IConstInstruction;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.analysis.instruction.InvokeSpecialInstruction;
import com.tonic.analysis.instruction.NewInstruction;
import com.tonic.analysis.instruction.PutFieldInstruction;
import com.tonic.analysis.instruction.SipushInstruction;
import com.tonic.parser.ClassFile;
import dukes.yabr.Instructions;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only analysis that finds each {@code new Color(constants)} in a class's {@code <clinit>} stored straight
 * into a static field. Colours built from non-constant arguments are left untouched.
 */
final class ColorScanner {

    private ColorScanner() {
    }

    static List<PackedColor> scan(ClassFile owner, CodeWriter staticInit) {
        List<Instruction> insns = Instructions.toList(staticInit.getInstructions());
        List<PackedColor> colors = new ArrayList<>();
        for (int i = 0; i < insns.size(); i++) {
            if (insns.get(i) instanceof NewInstruction newColor && newColor.resolveClass().equals(PackedColor.TYPE)) {
                PackedColor color = readColor(owner, insns, i);
                if (color != null) {
                    colors.add(color);
                }
            }
        }
        return colors;
    }

    /** Reads one {@code NEW Color; DUP; <constants>; INVOKESPECIAL; PUTSTATIC field} sequence, or null. */
    private static PackedColor readColor(ClassFile owner, List<Instruction> insns, int newIndex) {
        int cursor = newIndex + 1;
        if (cursor >= insns.size() || !(insns.get(cursor) instanceof DupInstruction)) {
            return null;
        }
        List<Integer> components = new ArrayList<>();
        for (cursor++; cursor < insns.size() && !isColorInit(insns.get(cursor)); cursor++) {
            Integer value = constantInt(insns.get(cursor));
            if (value == null) {
                return null;
            }
            components.add(value);
        }
        int storeIndex = cursor + 1;
        if (cursor >= insns.size() || components.size() < 3 || components.size() > 4 || storeIndex >= insns.size()) {
            return null;
        }
        if (!(insns.get(storeIndex) instanceof PutFieldInstruction put) || !put.isStatic()
                || !put.getOwnerClass().equals(owner.getClassName()) || !put.getFieldDescriptor().equals(PackedColor.DESCRIPTOR)) {
            return null;
        }
        int red = components.get(0), green = components.get(1), blue = components.get(2);
        int alpha = components.size() > 3 ? components.get(3) : 255;
        if (!isByte(red) || !isByte(green) || !isByte(blue) || !isByte(alpha)) {
            return null;
        }
        return new PackedColor(put.getFieldName(), red, green, blue, alpha, insns.get(newIndex), insns.get(storeIndex));
    }

    private static boolean isColorInit(Instruction insn) {
        return insn instanceof InvokeSpecialInstruction call
                && call.getOwnerClass().equals(PackedColor.TYPE) && call.getMethodName().equals("<init>");
    }

    private static boolean isByte(int value) {
        return value >= 0 && value <= 255;
    }

    private static Integer constantInt(Instruction insn) {
        if (insn instanceof IConstInstruction iconst) {
            return iconst.getValue();
        }
        if (insn instanceof BipushInstruction bipush) {
            return (int) bipush.getValue();
        }
        if (insn instanceof SipushInstruction sipush) {
            return (int) sipush.getValue();
        }
        return null;
    }
}
