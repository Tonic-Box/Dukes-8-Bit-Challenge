package dukes.build.merge;

import dukes.build.CompiledClasses;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * Repoints every class that referenced the source onto the target (in practice only Main): redirects its
 * source-typed field reads to the target field and removes the now-dead field and its initialiser.
 */
final class ReferenceRedirector {

    private ReferenceRedirector() {
    }

    static void redirect(CompiledClasses classes, ClassNode source, String targetName) {
        String sourceDescriptor = "L" + source.name + ";";
        String targetDescriptor = "L" + targetName + ";";
        for (ClassNode referrer : classes.all()) {
            if (referrer == source) {
                continue;
            }
            String sourceField = Bytecode.fieldNamed(referrer, sourceDescriptor);
            String targetField = Bytecode.fieldNamed(referrer, targetDescriptor);
            boolean changed = false;
            for (MethodNode method : referrer.methods) {
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn instanceof MethodInsnNode call && call.owner.equals(source.name)) {
                        call.owner = targetName;
                        changed = true;
                    } else if (targetField != null && insn instanceof FieldInsnNode field
                            && field.getOpcode() == Opcodes.GETFIELD
                            && field.owner.equals(referrer.name) && field.name.equals(sourceField)) {
                        // Redirect reads of the source field to the target field; the lone write (the field's
                        // initialiser) is stripped by removeFieldInit, so leave PUTFIELD alone.
                        field.name = targetField;
                        field.desc = targetDescriptor;
                        changed = true;
                    }
                }
            }
            if (sourceField != null) {
                removeFieldInit(referrer, source.name, sourceField);
                referrer.fields.removeIf(f -> f.name.equals(sourceField) && f.desc.equals(sourceDescriptor));
                changed = true;
            }
            if (changed) {
                classes.markModified(referrer.name);
            }
        }
    }

    /** Removes a {@code new Source(); ... putfield} field-initialiser sequence from a class's constructor. */
    private static void removeFieldInit(ClassNode owner, String sourceName, String fieldName) {
        MethodNode constructor = Bytecode.method(owner, "<init>");
        if (constructor == null) {
            return;
        }
        for (AbstractInsnNode insn = constructor.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof TypeInsnNode type && insn.getOpcode() == Opcodes.NEW && type.desc.equals(sourceName)) {
                AbstractInsnNode start = insn.getPrevious();   // the ALOAD receiving the putfield
                AbstractInsnNode end = insn;
                while (end != null && !(end instanceof FieldInsnNode f && f.getOpcode() == Opcodes.PUTFIELD
                        && f.owner.equals(owner.name) && f.name.equals(fieldName))) {
                    end = end.getNext();
                }
                if (start != null && start.getOpcode() == Opcodes.ALOAD && end != null) {
                    Bytecode.removeRange(constructor.instructions, start, end);
                }
                return;
            }
        }
    }
}
