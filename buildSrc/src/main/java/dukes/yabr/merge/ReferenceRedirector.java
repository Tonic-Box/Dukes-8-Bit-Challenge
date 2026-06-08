package dukes.yabr.merge;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.GetFieldInstruction;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import dukes.yabr.Instructions;
import dukes.yabr.CompiledClasses;

import java.util.List;

import static com.tonic.utill.Opcode.GETFIELD;

/**
 * Repoints every class that referenced the source onto the target (in practice only App) - redirects its
 * source-typed field reads to the target field and removes the now-dead field and its initialiser.
 */
final class ReferenceRedirector {

    private ReferenceRedirector() {
    }

    static void redirect(CompiledClasses classes, ClassFile source, String targetName) throws Exception {
        String sourceDescriptor = "L" + source.getClassName() + ";";
        String targetDescriptor = "L" + targetName + ";";
        for (ClassFile referrer : List.copyOf(classes.all())) {
            if (referrer == source) {
                continue;
            }
            String sourceField = fieldNamed(referrer, sourceDescriptor);
            String targetField = fieldNamed(referrer, targetDescriptor);
            boolean changed = referrer.redirectOwner(source.getClassName(), targetName) > 0;
            if (targetField != null && sourceField != null) {
                changed |= redirectFieldReads(referrer, sourceField, targetField, targetDescriptor);
            }
            if (sourceField != null) {
                Bytecode.removeFieldInitializer(Bytecode.method(referrer, "<init>"), source.getClassName(),
                        referrer.getClassName(), sourceField::equals);
                referrer.removeField(sourceField, sourceDescriptor);
                changed = true;
            }
            if (changed) {
                classes.markModified(referrer.getClassName());
            }
        }
    }

    /** Redirects reads of the source-typed field to the target field; the initialiser's write is stripped separately. */
    private static boolean redirectFieldReads(ClassFile referrer, String sourceField, String targetField, String targetDescriptor) throws Exception {
        ConstPool constPool = referrer.getConstPool();
        int targetRef = constPool.findOrAddField(referrer.getClassName(), targetField, targetDescriptor).getIndex(constPool);
        boolean changed = false;
        for (MethodEntry method : referrer.getMethods()) {
            if (method.getCodeAttribute() == null) {
                continue;
            }
            CodeWriter writer = new CodeWriter(method);
            boolean methodChanged = false;
            for (Instruction insn : Instructions.toList(writer.getInstructions())) {
                if (insn instanceof GetFieldInstruction get && !get.isStatic()
                        && get.getOwnerClass().equals(referrer.getClassName()) && get.getFieldName().equals(sourceField)) {
                    writer.replaceInstruction(insn, new GetFieldInstruction(constPool, GETFIELD.getCode(), 0, targetRef));
                    methodChanged = true;
                }
            }
            if (methodChanged) {
                writer.write();
                changed = true;
            }
        }
        return changed;
    }

    private static String fieldNamed(ClassFile classFile, String descriptor) {
        for (FieldEntry field : classFile.getFields()) {
            if (field.getDesc().equals(descriptor)) {
                return field.getName();
            }
        }
        return null;
    }
}
