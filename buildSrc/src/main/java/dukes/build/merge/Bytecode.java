package dukes.build.merge;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

/** Small ASM-tree lookups and edits shared by the merge steps. */
final class Bytecode {

    private Bytecode() {
    }

    /** The method named {@code name} in {@code classNode}, or null. */
    static MethodNode method(ClassNode classNode, String name) {
        for (MethodNode method : classNode.methods) {
            if (method.name.equals(name)) {
                return method;
            }
        }
        return null;
    }

    /** The name of the first field in {@code classNode} with the given descriptor, or null. */
    static String fieldNamed(ClassNode classNode, String descriptor) {
        for (FieldNode field : classNode.fields) {
            if (field.desc.equals(descriptor)) {
                return field.name;
            }
        }
        return null;
    }

    /** Removes every instruction from {@code from} to {@code to} inclusive. */
    static void removeRange(InsnList list, AbstractInsnNode from, AbstractInsnNode to) {
        for (AbstractInsnNode cursor = from; cursor != null; ) {
            AbstractInsnNode next = cursor.getNext();
            list.remove(cursor);
            if (cursor == to) {
                return;
            }
            cursor = next;
        }
    }
}
