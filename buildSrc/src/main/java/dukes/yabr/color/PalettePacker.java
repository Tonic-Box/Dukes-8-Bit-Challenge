package dukes.yabr.color;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.AALoadInstruction;
import com.tonic.analysis.instruction.BipushInstruction;
import com.tonic.analysis.instruction.GetFieldInstruction;
import com.tonic.analysis.instruction.IConstInstruction;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.analysis.instruction.SipushInstruction;
import com.tonic.builder.CodeBuilder;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.tonic.utill.Opcode.AALOAD;
import static com.tonic.utill.Opcode.BIPUSH;
import static com.tonic.utill.Opcode.GETSTATIC;
import static com.tonic.utill.Opcode.ICONST_0;
import static com.tonic.utill.Opcode.SIPUSH;

/**
 * Swaps the scanned per-colour fields for one {@code Color[]} decoded from a packed string in {@code <clinit>},
 * and rewrites every colour reference into an array load. The decode loop is built as a detached snippet
 * ({@link CodeBuilder#detached()}) and spliced in: its exit is a continuation label that binds to whatever now
 * starts {@code <clinit>}, and {@code max_stack} is recomputed over the control-flow graph automatically.
 */
final class PalettePacker {

    private static final int ACC_PRIVATE_STATIC = 0x0002 | 0x0008;
    private static final String PALETTE = "$PALETTE";
    private static final String PALETTE_DESCRIPTOR = "[" + PackedColor.DESCRIPTOR;

    private PalettePacker() {
    }

    static void pack(ClassFile owner, CodeWriter staticInit, List<PackedColor> colors) throws Exception {
        ConstPool constPool = owner.getConstPool();
        Map<String, Integer> indexByField = new HashMap<>();
        for (int i = 0; i < colors.size(); i++) {
            indexByField.put(colors.get(i).field(), i);
        }
        Set<String> packedFields = indexByField.keySet();

        // Cut each colour-construction sequence out of <clinit>.
        List<Instruction> ordered = staticInit.getInstructionList();
        Set<Instruction> toRemove = Collections.newSetFromMap(new IdentityHashMap<>());
        for (PackedColor color : colors) {
            int from = identityIndex(ordered, color.construction());
            int to = identityIndex(ordered, color.store());
            for (int k = from; k <= to; k++) {
                toRemove.add(ordered.get(k));
            }
        }
        for (Instruction insn : toRemove) {
            staticInit.removeInstruction(insn);
        }

        // Create the palette field up front so the decode loop can reference it.
        owner.createNewField(ACC_PRIVATE_STATIC, PALETTE, PALETTE_DESCRIPTOR, new ArrayList<>());

        // Splice the decode loop before whatever now starts <clinit>; the loop's exit is the snippet's tail
        // continuation label, which binds to that instruction on insertBefore. relink computes branch targets and
        // max_stack/max_locals, so no manual bookkeeping is needed.
        Instruction exitTarget = staticInit.getInstructions().iterator().next();
        int base = staticInit.getMaxLocals();
        staticInit.insertBefore(exitTarget, buildDecodeLoop(owner, colors, base));
        staticInit.write();

        // Swap the individual Color fields for the single palette array (already created above).
        for (String field : packedFields) {
            owner.removeField(field, PackedColor.DESCRIPTOR);
        }

        // Rewrite every `getstatic <colourField>` into `getstatic palette; <index>; aaload`.
        int paletteRef = constPool.findOrAddField(owner.getClassName(), PALETTE, PALETTE_DESCRIPTOR).getIndex(constPool);
        for (MethodEntry method : owner.getMethods()) {
            if (method.getCodeAttribute() != null) {
                rewriteColorReads(method, owner.getClassName(), packedFields, indexByField, paletteRef, constPool);
            }
        }
    }

    /**
     * Emits {@code palette = new Color[N]} then a loop filling it from a packed string of RGBA bytes (four chars
     * per colour): {@code for (i, p = 0; i < N; i++, p += 4) palette[i] = new Color(d[p..p+3])}. The loop uses
     * three scratch locals above the static initializer's own. The {@code "done"} tail label is the continuation:
     * the {@code if_icmpge} exit binds to the splice point (the start of the remaining {@code <clinit>}).
     */
    private static CodeWriter.ClonedRange buildDecodeLoop(ClassFile owner, List<PackedColor> colors, int base) {
        StringBuilder packed = new StringBuilder(colors.size() * 4);
        for (PackedColor color : colors) {
            packed.append((char) color.red()).append((char) color.green()).append((char) color.blue()).append((char) color.alpha());
        }
        int count = colors.size();
        int indexLocal = base + 1;
        int posLocal = base + 2;
        String type = PackedColor.TYPE;
        String name = owner.getClassName();

        CodeBuilder loop = CodeBuilder.detached()
                .iconst(count).anewarray(type).putstatic(name, PALETTE, PALETTE_DESCRIPTOR)
                .ldc(packed.toString()).astore(base)
                .iconst(0).istore(indexLocal)
                .iconst(0).istore(posLocal)
                .label("top")
                .iload(indexLocal).iconst(count).if_icmpge("done")
                .getstatic(name, PALETTE, PALETTE_DESCRIPTOR).iload(indexLocal)
                .new_(type).dup();
        charAt(loop, base, posLocal, 0);
        charAt(loop, base, posLocal, 1);
        charAt(loop, base, posLocal, 2);
        charAt(loop, base, posLocal, 3);
        loop.invokespecial(type, "<init>", "(IIII)V")
                .aastore()
                .iinc(posLocal, 4).iinc(indexLocal, 1)
                .goto_("top")
                .label("done");
        return loop.assemble(owner);
    }

    /** Appends {@code data.charAt(p + offset)}, leaving the (int-widened) char on the stack. */
    private static void charAt(CodeBuilder loop, int dataLocal, int posLocal, int offset) {
        loop.aload(dataLocal).iload(posLocal);
        if (offset > 0) {
            loop.iconst(offset).iadd();
        }
        loop.invokevirtual("java/lang/String", "charAt", "(I)C");
    }

    /** Replaces every {@code getstatic <packed colour field>} in {@code method} with a palette array load. */
    private static void rewriteColorReads(MethodEntry method, String owner, Set<String> packedFields,
                                          Map<String, Integer> indexByField, int paletteRef, ConstPool constPool) throws Exception {
        CodeWriter writer = new CodeWriter(method);
        List<Instruction> reads = new ArrayList<>();
        for (Instruction insn : writer.getInstructions()) {
            if (insn instanceof GetFieldInstruction get && get.isStatic() && get.getOwnerClass().equals(owner)
                    && get.getFieldDescriptor().equals(PackedColor.DESCRIPTOR) && packedFields.contains(get.getFieldName())) {
                reads.add(insn);
            }
        }
        if (reads.isEmpty()) {
            return;
        }
        for (Instruction read : reads) {
            int index = indexByField.get(((GetFieldInstruction) read).getFieldName());
            GetFieldInstruction paletteLoad = new GetFieldInstruction(constPool, GETSTATIC.getCode(), 0, paletteRef);
            writer.replaceInstruction(read, paletteLoad);
            writer.insertAfter(paletteLoad, List.of(intConstant(index), new AALoadInstruction(AALOAD.getCode(), 0)));
        }
        writer.write();
    }

    private static Instruction intConstant(int value) {
        if (value >= -1 && value <= 5) {
            return new IConstInstruction(ICONST_0.getCode() + value, 0, value);
        }
        if (value >= -128 && value <= 127) {
            return new BipushInstruction(BIPUSH.getCode(), 0, value);
        }
        return new SipushInstruction(SIPUSH.getCode(), 0, value);
    }

    private static int identityIndex(List<Instruction> list, Instruction target) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == target) {
                return i;
            }
        }
        throw new IllegalStateException("Colour landmark not found in <clinit>");
    }
}
