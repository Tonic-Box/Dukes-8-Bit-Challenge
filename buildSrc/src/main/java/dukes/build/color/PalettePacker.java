package dukes.build.color;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The bytecode transform: swaps the scanned per-colour fields for one {@code Color[]} decoded from a packed
 * string in {@code <clinit>}, and rewrites every colour reference into an array load. Pure ASM tree work.
 */
final class PalettePacker {

    private static final String PALETTE = "$PALETTE";
    private static final String PALETTE_DESCRIPTOR = "[" + PackedColor.DESCRIPTOR;

    private PalettePacker() {
    }

    static void pack(ClassNode owner, MethodNode staticInit, List<PackedColor> colors) {
        Map<String, Integer> indexByField = new HashMap<>();
        for (int i = 0; i < colors.size(); i++) {
            indexByField.put(colors.get(i).field(), i);
        }
        Set<String> packedFields = indexByField.keySet();

        // Cut each colour-construction sequence out of <clinit>, then prepend the palette-decode loop.
        for (PackedColor color : colors) {
            removeRange(staticInit.instructions, color.construction(), color.store());
        }
        staticInit.instructions.insert(decodeLoop(owner.name, colors, staticInit.maxLocals));

        // Swap the individual Color fields for the single palette array.
        owner.fields.removeIf(f -> f.desc.equals(PackedColor.DESCRIPTOR) && packedFields.contains(f.name));
        owner.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, PALETTE, PALETTE_DESCRIPTOR, null, null));

        // Rewrite every `getstatic <colourField>` into `getstatic palette; <index>; aaload`.
        for (MethodNode method : owner.methods) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC && field.owner.equals(owner.name) && field.desc.equals(PackedColor.DESCRIPTOR) && packedFields.contains(field.name)) {
                    InsnList load = new InsnList();
                    load.add(new FieldInsnNode(Opcodes.GETSTATIC, owner.name, PALETTE, PALETTE_DESCRIPTOR));
                    load.add(intConstant(indexByField.get(field.name)));
                    load.add(new InsnNode(Opcodes.AALOAD));
                    method.instructions.insert(insn, load);
                    method.instructions.remove(insn);
                }
            }
        }
    }

    /**
     * Emits {@code palette = new Color[N]} then a loop filling it from a packed string of RGBA bytes (four
     * chars per colour): {@code for (i, p = 0; i < N; i++, p += 4) palette[i] = new Color(d[p..p+3])}. The loop
     * uses three scratch locals above the static initializer's own, so it never clashes with existing code.
     */
    private static InsnList decodeLoop(String owner, List<PackedColor> colors, int baseLocal) {
        StringBuilder packed = new StringBuilder(colors.size() * 4);
        for (PackedColor color : colors) {
            packed.append((char) color.red()).append((char) color.green()).append((char) color.blue()).append((char) color.alpha());
        }
        int count = colors.size();
        int indexLocal = baseLocal + 1;
        int posLocal = baseLocal + 2;

        LabelNode top = new LabelNode();
        LabelNode end = new LabelNode();
        InsnList code = new InsnList();
        code.add(intConstant(count));
        code.add(new TypeInsnNode(Opcodes.ANEWARRAY, PackedColor.TYPE));
        code.add(new FieldInsnNode(Opcodes.PUTSTATIC, owner, PALETTE, PALETTE_DESCRIPTOR));
        code.add(new LdcInsnNode(packed.toString()));
        code.add(new VarInsnNode(Opcodes.ASTORE, baseLocal));
        code.add(new InsnNode(Opcodes.ICONST_0));
        code.add(new VarInsnNode(Opcodes.ISTORE, indexLocal));
        code.add(new InsnNode(Opcodes.ICONST_0));
        code.add(new VarInsnNode(Opcodes.ISTORE, posLocal));
        code.add(top);
        code.add(new VarInsnNode(Opcodes.ILOAD, indexLocal));
        code.add(intConstant(count));
        code.add(new JumpInsnNode(Opcodes.IF_ICMPGE, end));
        code.add(new FieldInsnNode(Opcodes.GETSTATIC, owner, PALETTE, PALETTE_DESCRIPTOR));
        code.add(new VarInsnNode(Opcodes.ILOAD, indexLocal));
        code.add(new TypeInsnNode(Opcodes.NEW, PackedColor.TYPE));
        code.add(new InsnNode(Opcodes.DUP));
        appendCharAt(code, baseLocal, posLocal, 0);
        appendCharAt(code, baseLocal, posLocal, 1);
        appendCharAt(code, baseLocal, posLocal, 2);
        appendCharAt(code, baseLocal, posLocal, 3);
        code.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, PackedColor.TYPE, "<init>", "(IIII)V", false));
        code.add(new InsnNode(Opcodes.AASTORE));
        code.add(new IincInsnNode(posLocal, 4));
        code.add(new IincInsnNode(indexLocal, 1));
        code.add(new JumpInsnNode(Opcodes.GOTO, top));
        code.add(end);
        return code;
    }

    /** Appends {@code data.charAt(p + offset)}, leaving the (int-widened) char on the stack. */
    private static void appendCharAt(InsnList code, int dataLocal, int posLocal, int offset) {
        code.add(new VarInsnNode(Opcodes.ALOAD, dataLocal));
        code.add(new VarInsnNode(Opcodes.ILOAD, posLocal));
        if (offset > 0) {
            code.add(intConstant(offset));
            code.add(new InsnNode(Opcodes.IADD));
        }
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false));
    }

    private static void removeRange(InsnList list, AbstractInsnNode from, AbstractInsnNode to) {
        for (AbstractInsnNode cursor = from; cursor != null; ) {
            AbstractInsnNode next = cursor.getNext();
            list.remove(cursor);
            if (cursor == to) {
                return;
            }
            cursor = next;
        }
    }

    private static AbstractInsnNode intConstant(int value) {
        if (value >= -1 && value <= 5) {
            return new InsnNode(Opcodes.ICONST_0 + value);
        }
        if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            return new IntInsnNode(Opcodes.BIPUSH, value);
        }
        if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            return new IntInsnNode(Opcodes.SIPUSH, value);
        }
        return new LdcInsnNode(value);
    }
}
