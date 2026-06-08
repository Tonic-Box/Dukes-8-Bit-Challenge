package dukes.yabr.merge;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.ALoadInstruction;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.analysis.instruction.NewInstruction;
import com.tonic.analysis.instruction.PutFieldInstruction;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import dukes.yabr.Instructions;

import java.util.List;
import java.util.function.Predicate;

/** Constant-pool and constructor edits shared by the merge steps. */
final class Bytecode {

    private Bytecode() {
    }

    /** The first method named {@code name} in {@code classFile}, or null. */
    static MethodEntry method(ClassFile classFile, String name) {
        for (MethodEntry method : classFile.getMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        return null;
    }

    /**
     * Removes a {@code new <newType>(); ... putfield <putOwner>.<field>} field-initializer sequence (from the
     * {@code aload} that produced the receiver through the {@code putfield}) from {@code constructor}, where the
     * stored field's name satisfies {@code fieldMatches}. A no-op if the constructor is null or no such sequence
     * is present.
     */
    static void removeFieldInitializer(MethodEntry constructor, String newType, String putOwner,
                                       Predicate<String> fieldMatches) throws Exception {
        if (constructor == null) {
            return;
        }
        CodeWriter writer = new CodeWriter(constructor);
        List<Instruction> insns = Instructions.toList(writer.getInstructions());
        for (int i = 0; i < insns.size(); i++) {
            if (insns.get(i) instanceof NewInstruction nw && nw.resolveClass().equals(newType)) {
                int end = i;
                while (end < insns.size() && !(insns.get(end) instanceof PutFieldInstruction put && !put.isStatic()
                        && put.getOwnerClass().equals(putOwner) && fieldMatches.test(put.getFieldName()))) {
                    end++;
                }
                int start = i - 1;
                if (end < insns.size() && start >= 0 && insns.get(start) instanceof ALoadInstruction) {
                    for (int k = start; k <= end; k++) {
                        writer.removeInstruction(insns.get(k));
                    }
                    writer.write();
                }
                return;
            }
        }
    }
}
