package dukes.yabr.merge;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.ConstPoolRemapper;
import com.tonic.analysis.MethodGrafter;
import com.tonic.analysis.instruction.ANewArrayInstruction;
import com.tonic.analysis.instruction.CheckCastInstruction;
import com.tonic.analysis.instruction.GetFieldInstruction;
import com.tonic.analysis.instruction.GotoInstruction;
import com.tonic.analysis.instruction.InstanceOfInstruction;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.analysis.instruction.InvokeSpecialInstruction;
import com.tonic.analysis.instruction.NewInstruction;
import com.tonic.analysis.instruction.NopInstruction;
import com.tonic.analysis.instruction.ReturnInstruction;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import dukes.yabr.Instructions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.tonic.utill.Opcode.ANEWARRAY;
import static com.tonic.utill.Opcode.CHECKCAST;
import static com.tonic.utill.Opcode.GOTO;
import static com.tonic.utill.Opcode.INSTANCEOF;
import static com.tonic.utill.Opcode.INVOKESPECIAL;
import static com.tonic.utill.Opcode.NEW;

/**
 * Pulls the source class into the target. Methods are grafted (their constant-pool references re-resolved into
 * the target) with {@link MethodGrafter}; the source's own references are then repointed onto the target with
 * {@link ClassFile#redirectOwner} plus a supplementary pass for the class-type operands {@code redirectOwner}
 * leaves alone. The source's {@code <clinit>} and {@code <init>} bodies are spliced in, and a field whose type
 * is the target itself collapses to {@code this}.
 */
final class MemberAbsorber {

    private MemberAbsorber() {
    }

    static int absorb(ClassFile target, ClassFile source) throws Exception {
        adoptHierarchy(target, source);
        List<String> selfFields = selfReferentialFields(source, target.getClassName());

        Set<String> targetSignatures = new HashSet<>();
        for (MethodEntry method : target.getMethods()) {
            targetSignatures.add(method.getName() + method.getDesc());
        }

        MethodEntry sourceStaticInit = null;
        MethodEntry sourceConstructor = null;
        int moved = 0;
        for (MethodEntry method : source.getMethods()) {
            switch (method.getName()) {
                case "<clinit>" -> sourceStaticInit = method;
                case "<init>" -> sourceConstructor = method;
                default -> {
                    if (!targetSignatures.add(method.getName() + method.getDesc())) {
                        throw new IllegalStateException("Merge collision: " + source.getClassName() + " and "
                                + target.getClassName() + " both declare " + method.getName() + method.getDesc()
                                + ". Rename one - a duplicate method is not rejected by the no-verify bootstrap"
                                + " load and fails only at call time.");
                    }
                    MethodGrafter.graftMethod(source, method, target);
                    moved++;
                }
            }
        }

        for (FieldEntry field : source.getFields()) {
            if (!selfFields.contains(field.getName())) {
                target.createNewField(field.getAccess(), field.getName(), field.getDesc(), new ArrayList<>());
            }
        }

        if (sourceStaticInit != null) {
            foldStaticInit(target, source, sourceStaticInit);
        }
        if (sourceConstructor != null) {
            foldConstructor(target, source, sourceConstructor, selfFields);
        }

        // Now that every source-derived body lives in the target, repoint the source class onto the target: member
        // references via redirectOwner, then the class-type operands it does not touch, then collapse self reads.
        target.redirectOwner(source.getClassName(), target.getClassName());
        repointTypeOperands(target, source.getClassName());
        dropSelfFieldReads(target, selfFields);
        return moved;
    }

    /**
     * Gives the target the source's superclass and interfaces, then retargets the target's own {@code super()}
     * call to the adopted superclass. A no-op for a plain {@code Object} source with no interfaces, so leaf
     * merges (Renderer, Sound) leave the target's header untouched.
     */
    private static void adoptHierarchy(ClassFile target, ClassFile source) throws Exception {
        boolean plainSuper = source.getSuperClassName().equals("java/lang/Object");
        List<String> sourceInterfaces = interfaceNames(source);
        if (plainSuper && sourceInterfaces.isEmpty()) {
            return;
        }
        String previousSuper = target.getSuperClassName();
        target.setSuperClassName(source.getSuperClassName());
        List<String> targetInterfaces = interfaceNames(target);
        for (String interfaceName : sourceInterfaces) {
            if (!targetInterfaces.contains(interfaceName)) {
                target.addInterface(interfaceName);
            }
        }
        MethodEntry constructor = Bytecode.method(target, "<init>");
        if (constructor != null) {
            CodeWriter writer = new CodeWriter(constructor);
            for (Instruction insn : writer.getInstructions()) {
                if (insn instanceof InvokeSpecialInstruction call && call.getMethodName().equals("<init>")
                        && call.getOwnerClass().equals(previousSuper)) {
                    int ref = target.getConstPool().findOrAddMethodRef(source.getSuperClassName(), "<init>", call.getMethodDescriptor()).getIndex(target.getConstPool());
                    writer.replaceInstruction(insn, new InvokeSpecialInstruction(target.getConstPool(), INVOKESPECIAL.getCode(), 0, ref));
                    break;
                }
            }
            writer.write();
        }
    }

    /** Resolved internal names of a class's interfaces. */
    private static List<String> interfaceNames(ClassFile classFile) {
        ConstPool constPool = classFile.getConstPool();
        List<String> names = new ArrayList<>();
        for (int index : classFile.getInterfaces()) {
            names.add(constPool.getClassName(index));
        }
        return names;
    }

    /** Names of the source's fields whose type is the target class - back-references to the merged object. */
    private static List<String> selfReferentialFields(ClassFile source, String targetName) {
        String selfDescriptor = "L" + targetName + ";";
        List<String> names = new ArrayList<>();
        for (FieldEntry field : source.getFields()) {
            if (field.getDesc().equals(selfDescriptor)) {
                names.add(field.getName());
            }
        }
        return names;
    }

    /** Rewrites class-type operands (NEW/CHECKCAST/INSTANCEOF/ANEWARRAY) referencing the source onto the target. */
    private static void repointTypeOperands(ClassFile target, String sourceName) throws Exception {
        ConstPool constPool = target.getConstPool();
        int targetClass = constPool.findOrAddClass(target.getClassName()).getIndex(constPool);
        for (MethodEntry method : target.getMethods()) {
            if (method.getCodeAttribute() == null) {
                continue;
            }
            CodeWriter writer = new CodeWriter(method);
            boolean changed = false;
            for (Instruction insn : Instructions.toList(writer.getInstructions())) {
                Instruction replacement = retargetType(insn, sourceName, constPool, targetClass);
                if (replacement != null) {
                    writer.replaceInstruction(insn, replacement);
                    changed = true;
                }
            }
            if (changed) {
                writer.write();
            }
        }
    }

    private static Instruction retargetType(Instruction insn, String sourceName, ConstPool constPool, int targetClass) {
        if (insn instanceof NewInstruction nw && nw.resolveClass().equals(sourceName)) {
            return new NewInstruction(constPool, NEW.getCode(), 0, targetClass);
        }
        if (insn instanceof CheckCastInstruction cast && cast.resolveClass().equals(sourceName)) {
            return new CheckCastInstruction(constPool, CHECKCAST.getCode(), 0, targetClass);
        }
        if (insn instanceof InstanceOfInstruction test && test.resolveClass().equals(sourceName)) {
            return new InstanceOfInstruction(constPool, INSTANCEOF.getCode(), 0, targetClass);
        }
        if (insn instanceof ANewArrayInstruction array && array.resolveClass().equals(sourceName)) {
            return new ANewArrayInstruction(constPool, ANEWARRAY.getCode(), 0, targetClass, 0);
        }
        return null;
    }

    /**
     * Drops every {@code getfield} of a self-referential field, leaving the {@code aload 0} that produced the
     * receiver: once the source is the target, {@code this.self} is just {@code this}.
     */
    private static void dropSelfFieldReads(ClassFile target, List<String> selfFields) throws Exception {
        if (selfFields.isEmpty()) {
            return;
        }
        for (MethodEntry method : target.getMethods()) {
            if (method.getCodeAttribute() == null) {
                continue;
            }
            CodeWriter writer = new CodeWriter(method);
            boolean changed = false;
            for (Instruction insn : Instructions.toList(writer.getInstructions())) {
                if (insn instanceof GetFieldInstruction get && !get.isStatic()
                        && get.getOwnerClass().equals(target.getClassName()) && selfFields.contains(get.getFieldName())) {
                    writer.removeInstruction(insn);
                    changed = true;
                }
            }
            if (changed) {
                writer.write();
            }
        }
    }

    /** Splices the source's static-init body into the target's {@code <clinit>}, before its terminating return. */
    private static void foldStaticInit(ClassFile target, ClassFile source, MethodEntry sourceStaticInit) throws Exception {
        MethodEntry targetStaticInit = Bytecode.method(target, "<clinit>");
        if (targetStaticInit == null) {
            MethodGrafter.graftMethod(source, sourceStaticInit, target);
            return;
        }
        CodeWriter sourceWriter = new CodeWriter(sourceStaticInit);
        List<Instruction> sourceBody = Instructions.toList(sourceWriter.getInstructions());
        foldBody(target, source, targetStaticInit, sourceWriter, sourceBody.getFirst(), sourceBody, sourceStaticInit);
    }

    /**
     * Splices the source constructor's body (everything after its {@code super()} call) into the target's
     * constructor, before its terminating return - the target's own {@code super()} already ran. The
     * self-referential field's initializer is dropped first; {@code this} is copied into the relocated frame.
     */
    private static void foldConstructor(ClassFile target, ClassFile source, MethodEntry sourceConstructor, List<String> selfFields) throws Exception {
        MethodEntry targetConstructor = Bytecode.method(target, "<init>");
        if (targetConstructor == null) {
            return;
        }
        if (!selfFields.isEmpty()) {
            Bytecode.removeFieldInitializer(sourceConstructor, target.getClassName(), source.getClassName(), selfFields::contains);
        }
        CodeWriter sourceWriter = new CodeWriter(sourceConstructor);
        List<Instruction> sourceBody = Instructions.toList(sourceWriter.getInstructions());
        Instruction start = afterSuperCall(sourceBody);
        foldBody(target, source, targetConstructor, sourceWriter, start, sourceBody, sourceConstructor);
    }

    /**
     * Clones {@code [start, last]} of the source method (re-resolving its constant pool into the target) and
     * splices it into {@code targetMethod} before that method's terminating return, dropping the clone's own
     * returns so control falls through. The body is cloned with no local-variable shift: its slots reuse the
     * target's (dead at the splice point, just before the return), and the source's {@code this} stays at local
     * 0 as the canonical {@code aload_0} that ProGuard's constructor final-field-init analysis requires.
     */
    private static void foldBody(ClassFile target, ClassFile source, MethodEntry targetMethod, CodeWriter sourceWriter,
                                 Instruction start, List<Instruction> sourceBody, MethodEntry sourceMethod) throws Exception {
        CodeWriter targetWriter = new CodeWriter(targetMethod);
        CodeWriter.ClonedRange cloned = sourceWriter.cloneRangeWithTargets(
                start, sourceBody.getLast(), 0, target.getConstPool(), new ConstPoolRemapper(source, target)::remap);

        // Insert the body followed by a NOP anchor at its end. The body's returns jump to the anchor so control
        // falls through to whatever follows this segment - a later folded body or the constructor's own return -
        // rather than to the shared terminating return, in front of which a subsequent fold would insert its own
        // body (turning the jump into one that skips it). ProGuard removes the anchor and the redundant jumps.
        Instruction targetReturn = lastReturn(targetWriter);
        NopInstruction anchor = new NopInstruction(0, 0);
        targetWriter.insertBefore(targetReturn, anchor);
        targetWriter.insertBefore(anchor, cloned);
        for (Instruction insn : cloned.instructions()) {
            if (insn instanceof ReturnInstruction) {
                GotoInstruction jump = new GotoInstruction(GOTO.getCode(), 0, (short) 0);
                targetWriter.setBranchTarget(jump, anchor);
                targetWriter.replaceInstruction(insn, jump);
            }
        }
        targetWriter.write();

        CodeAttribute code = targetMethod.getCodeAttribute();
        code.setMaxLocals(Math.max(code.getMaxLocals(), sourceMethod.getCodeAttribute().getMaxLocals()));
        code.setMaxStack(Math.max(code.getMaxStack(), sourceMethod.getCodeAttribute().getMaxStack()));
    }

    /** The instruction after the constructor's {@code super()} (or {@code this()}) call - the start of its body. */
    private static Instruction afterSuperCall(List<Instruction> body) {
        for (int i = 0; i < body.size(); i++) {
            if (body.get(i) instanceof InvokeSpecialInstruction call && call.getMethodName().equals("<init>")) {
                return body.get(i + 1);
            }
        }
        return body.getFirst();
    }

    /** The method's terminating {@code return}, scanning back from the end. */
    private static Instruction lastReturn(CodeWriter writer) {
        List<Instruction> insns = Instructions.toList(writer.getInstructions());
        for (int i = insns.size() - 1; i >= 0; i--) {
            if (insns.get(i) instanceof ReturnInstruction) {
                return insns.get(i);
            }
        }
        throw new IllegalStateException("Method has no terminating return to splice before");
    }
}
