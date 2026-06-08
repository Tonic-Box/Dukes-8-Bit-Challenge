package dukes.yabr.color;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.AALoadInstruction;
import com.tonic.analysis.instruction.AAStoreInstruction;
import com.tonic.analysis.instruction.ALoadInstruction;
import com.tonic.analysis.instruction.ANewArrayInstruction;
import com.tonic.analysis.instruction.ArithmeticInstruction;
import com.tonic.analysis.instruction.AStoreInstruction;
import com.tonic.analysis.instruction.BipushInstruction;
import com.tonic.analysis.instruction.ConditionalBranchInstruction;
import com.tonic.analysis.instruction.DupInstruction;
import com.tonic.analysis.instruction.GetFieldInstruction;
import com.tonic.analysis.instruction.GotoInstruction;
import com.tonic.analysis.instruction.IConstInstruction;
import com.tonic.analysis.instruction.IIncInstruction;
import com.tonic.analysis.instruction.ILoadInstruction;
import com.tonic.analysis.instruction.IStoreInstruction;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.analysis.instruction.InvokeSpecialInstruction;
import com.tonic.analysis.instruction.InvokeVirtualInstruction;
import com.tonic.analysis.instruction.LdcInstruction;
import com.tonic.analysis.instruction.LdcWInstruction;
import com.tonic.analysis.instruction.NewInstruction;
import com.tonic.analysis.instruction.PutFieldInstruction;
import com.tonic.analysis.instruction.SipushInstruction;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import dukes.yabr.Instructions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.tonic.utill.Opcode.AALOAD;
import static com.tonic.utill.Opcode.AASTORE;
import static com.tonic.utill.Opcode.ALOAD;
import static com.tonic.utill.Opcode.ANEWARRAY;
import static com.tonic.utill.Opcode.ASTORE;
import static com.tonic.utill.Opcode.BIPUSH;
import static com.tonic.utill.Opcode.DUP;
import static com.tonic.utill.Opcode.GETSTATIC;
import static com.tonic.utill.Opcode.GOTO;
import static com.tonic.utill.Opcode.IADD;
import static com.tonic.utill.Opcode.ICONST_0;
import static com.tonic.utill.Opcode.IF_ICMPGE;
import static com.tonic.utill.Opcode.IINC;
import static com.tonic.utill.Opcode.ILOAD;
import static com.tonic.utill.Opcode.INVOKESPECIAL;
import static com.tonic.utill.Opcode.INVOKEVIRTUAL;
import static com.tonic.utill.Opcode.ISTORE;
import static com.tonic.utill.Opcode.LDC;
import static com.tonic.utill.Opcode.LDC_W;
import static com.tonic.utill.Opcode.NEW;
import static com.tonic.utill.Opcode.PUTSTATIC;
import static com.tonic.utill.Opcode.SIPUSH;

/**
 * Swaps the scanned per-colour fields for one {@code Color[]} decoded from a packed string in {@code <clinit>},
 * and rewrites every colour reference into an array load.
 */
final class PalettePacker {

    private static final int ACC_PRIVATE_STATIC = 0x0002 | 0x0008;
    private static final int DECODE_LOOP_MAX_STACK = 10;
    private static final String PALETTE = "$PALETTE";
    private static final String PALETTE_DESCRIPTOR = "[" + PackedColor.DESCRIPTOR;

    private PalettePacker() {
    }

    /** A built decode loop plus the handles whose branch targets must be registered before splicing. */
    private record DecodeLoop(List<Instruction> instructions, Instruction top, Instruction exit, Instruction back) {
    }

    static void pack(ClassFile owner, CodeWriter staticInit, List<PackedColor> colors) throws Exception {
        ConstPool constPool = owner.getConstPool();
        Map<String, Integer> indexByField = new HashMap<>();
        for (int i = 0; i < colors.size(); i++) {
            indexByField.put(colors.get(i).field(), i);
        }
        Set<String> packedFields = indexByField.keySet();

        // Cut each colour-construction sequence out of <clinit>.
        List<Instruction> ordered = Instructions.toList(staticInit.getInstructions());
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

        // Prepend the palette-decode loop before whatever now starts <clinit>; register its branch targets first
        // so the relink lays them out correctly rather than resolving the placeholder offsets.
        Instruction exitTarget = staticInit.getInstructions().iterator().next();
        int base = staticInit.getMaxLocals();
        DecodeLoop loop = buildDecodeLoop(constPool, owner.getClassName(), colors, base);
        staticInit.setBranchTarget(loop.exit(), exitTarget);
        staticInit.setBranchTarget(loop.back(), loop.top());
        staticInit.insertBefore(exitTarget, loop.instructions());
        staticInit.write();
        CodeAttribute clinitCode = staticInit.getMethodEntry().getCodeAttribute();
        clinitCode.setMaxLocals(Math.max(clinitCode.getMaxLocals(), base + 3));
        // The decode loop's peak depth is building new Color(charAt, charAt, charAt, charAt); set it explicitly
        // since YABR's linear stack analysis can under-count across the loop's back-edge.
        clinitCode.setMaxStack(Math.max(clinitCode.getMaxStack(), DECODE_LOOP_MAX_STACK));

        // Swap the individual Color fields for the single palette array.
        for (String field : packedFields) {
            owner.removeField(field, PackedColor.DESCRIPTOR);
        }
        owner.createNewField(ACC_PRIVATE_STATIC, PALETTE, PALETTE_DESCRIPTOR, new ArrayList<>());

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
     * three scratch locals above the static initializer's own, so it never clashes with existing code.
     */
    private static DecodeLoop buildDecodeLoop(ConstPool constPool, String owner, List<PackedColor> colors, int base) {
        StringBuilder packed = new StringBuilder(colors.size() * 4);
        for (PackedColor color : colors) {
            packed.append((char) color.red()).append((char) color.green()).append((char) color.blue()).append((char) color.alpha());
        }
        int count = colors.size();
        int indexLocal = base + 1;
        int posLocal = base + 2;

        int colorClass = constPool.findOrAddClass(PackedColor.TYPE).getIndex(constPool);
        int paletteRef = constPool.findOrAddField(owner, PALETTE, PALETTE_DESCRIPTOR).getIndex(constPool);
        int colorInit = constPool.findOrAddMethodRef(PackedColor.TYPE, "<init>", "(IIII)V").getIndex(constPool);
        int charAt = constPool.findOrAddMethodRef("java/lang/String", "charAt", "(I)C").getIndex(constPool);
        int dataString = constPool.findOrAddString(packed.toString()).getIndex(constPool);

        List<Instruction> code = new ArrayList<>();
        code.add(intConstant(count));
        code.add(new ANewArrayInstruction(constPool, ANEWARRAY.getCode(), 0, colorClass, 0));
        code.add(new PutFieldInstruction(constPool, PUTSTATIC.getCode(), 0, paletteRef));
        code.add(ldcString(constPool, dataString));
        code.add(new AStoreInstruction(ASTORE.getCode(), 0, base));
        code.add(new IConstInstruction(ICONST_0.getCode(), 0, 0));
        code.add(new IStoreInstruction(ISTORE.getCode(), 0, indexLocal));
        code.add(new IConstInstruction(ICONST_0.getCode(), 0, 0));
        code.add(new IStoreInstruction(ISTORE.getCode(), 0, posLocal));
        Instruction top = new ILoadInstruction(ILOAD.getCode(), 0, indexLocal);
        code.add(top);
        code.add(intConstant(count));
        ConditionalBranchInstruction exit = new ConditionalBranchInstruction(IF_ICMPGE.getCode(), 0, (short) 0);
        code.add(exit);
        code.add(new GetFieldInstruction(constPool, GETSTATIC.getCode(), 0, paletteRef));
        code.add(new ILoadInstruction(ILOAD.getCode(), 0, indexLocal));
        code.add(new NewInstruction(constPool, NEW.getCode(), 0, colorClass));
        code.add(new DupInstruction(DUP.getCode(), 0));
        appendCharAt(code, constPool, base, posLocal, 0, charAt);
        appendCharAt(code, constPool, base, posLocal, 1, charAt);
        appendCharAt(code, constPool, base, posLocal, 2, charAt);
        appendCharAt(code, constPool, base, posLocal, 3, charAt);
        code.add(new InvokeSpecialInstruction(constPool, INVOKESPECIAL.getCode(), 0, colorInit));
        code.add(new AAStoreInstruction(AASTORE.getCode(), 0));
        code.add(new IIncInstruction(IINC.getCode(), 0, posLocal, 4));
        code.add(new IIncInstruction(IINC.getCode(), 0, indexLocal, 1));
        GotoInstruction back = new GotoInstruction(GOTO.getCode(), 0, (short) 0);
        code.add(back);
        return new DecodeLoop(code, top, exit, back);
    }

    /** Appends {@code data.charAt(p + offset)}, leaving the (int-widened) char on the stack. */
    private static void appendCharAt(List<Instruction> code, ConstPool constPool, int dataLocal, int posLocal, int offset, int charAt) {
        code.add(new ALoadInstruction(ALOAD.getCode(), 0, dataLocal));
        code.add(new ILoadInstruction(ILOAD.getCode(), 0, posLocal));
        if (offset > 0) {
            code.add(intConstant(offset));
            code.add(new ArithmeticInstruction(IADD.getCode(), 0));
        }
        code.add(new InvokeVirtualInstruction(constPool, INVOKEVIRTUAL.getCode(), 0, charAt));
    }

    /** Replaces every {@code getstatic <packed colour field>} in {@code method} with a palette array load. */
    private static void rewriteColorReads(MethodEntry method, String owner, Set<String> packedFields,
                                          Map<String, Integer> indexByField, int paletteRef, ConstPool constPool) throws Exception {
        CodeWriter writer = new CodeWriter(method);
        int originalMaxStack = writer.getMaxStack();
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
        // Each rewritten read adds one transient slot (the array index above the array); bump maxStack by that
        // safe upper bound, since YABR's linear analysis can under-count it across branches.
        CodeAttribute code = method.getCodeAttribute();
        code.setMaxStack(Math.max(code.getMaxStack(), originalMaxStack + reads.size()));
    }

    private static Instruction ldcString(ConstPool constPool, int stringIndex) {
        return stringIndex <= 255
                ? new LdcInstruction(constPool, LDC.getCode(), 0, stringIndex)
                : new LdcWInstruction(constPool, LDC_W.getCode(), 0, stringIndex);
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
