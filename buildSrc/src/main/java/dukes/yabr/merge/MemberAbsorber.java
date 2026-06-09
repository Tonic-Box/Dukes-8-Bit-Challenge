package dukes.yabr.merge;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.ConstPoolRemapper;
import com.tonic.analysis.MethodGrafter;
import com.tonic.analysis.instruction.GetFieldInstruction;
import com.tonic.analysis.instruction.GotoInstruction;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.analysis.instruction.InvokeSpecialInstruction;
import com.tonic.analysis.instruction.NopInstruction;
import com.tonic.analysis.instruction.ReturnInstruction;
import com.tonic.parser.ClassFile;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.tonic.utill.Opcode.GOTO;
import static com.tonic.utill.Opcode.INVOKESPECIAL;

/**
 * Pulls the source class into the target. Methods are grafted (their constant-pool references re-resolved into
 * the target) with {@link MethodGrafter}; every reference to the source - member-ref owners, class-type operands,
 * and descriptors - is then repointed onto the target with {@link ClassFile#redirectOwner}. The source's
 * {@code <clinit>} and {@code <init>} bodies are spliced in, and a field whose type is the target itself collapses
 * to {@code this}.
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

        // Now that every source-derived body lives in the target, repoint every reference to the source class onto
        // the target - redirectOwner rewrites member-ref owners, class-type operands, and descriptors - then
        // collapse self reads.
        target.redirectOwner(source.getClassName(), target.getClassName());
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
        List<String> sourceInterfaces = source.getInterfaceNames();
        if (plainSuper && sourceInterfaces.isEmpty()) {
            return;
        }
        String previousSuper = target.getSuperClassName();
        target.setSuperClassName(source.getSuperClassName());
        List<String> targetInterfaces = target.getInterfaceNames();
        for (String interfaceName : sourceInterfaces) {
            if (!targetInterfaces.contains(interfaceName)) {
                target.addInterface(interfaceName);
            }
        }
        MethodEntry constructor = target.getMethod("<init>");
        if (constructor != null) {
            CodeWriter writer = new CodeWriter(constructor);
            for (Instruction insn : writer.getInstructions()) {
                if (insn instanceof InvokeSpecialInstruction call && call.getMethodName().equals("<init>") && call.getOwnerClass().equals(previousSuper)) {
                    int ref = target.getConstPool().findOrAddMethodRef(source.getSuperClassName(), "<init>", call.getMethodDescriptor()).getIndex(target.getConstPool());
                    writer.replaceInstruction(insn, new InvokeSpecialInstruction(target.getConstPool(), INVOKESPECIAL.getCode(), 0, ref));
                    break;
                }
            }
            writer.write();
        }
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
            for (Instruction insn : writer.getInstructionList()) {
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
        MethodEntry targetStaticInit = target.getStaticInitializer();
        if (targetStaticInit == null) {
            MethodGrafter.graftMethod(source, sourceStaticInit, target);
            return;
        }
        CodeWriter sourceWriter = new CodeWriter(sourceStaticInit);
        List<Instruction> sourceBody = sourceWriter.getInstructionList();
        foldBody(target, source, targetStaticInit, sourceWriter, sourceBody.getFirst(), sourceBody);
    }

    /**
     * Splices the source constructor's body (everything after its {@code super()} call) into the target's
     * constructor, before its terminating return - the target's own {@code super()} already ran. The
     * self-referential field's initializer is dropped first; {@code this} is copied into the relocated frame.
     */
    private static void foldConstructor(ClassFile target, ClassFile source, MethodEntry sourceConstructor, List<String> selfFields) throws Exception {
        MethodEntry targetConstructor = target.getMethod("<init>");
        if (targetConstructor == null) {
            return;
        }
        if (!selfFields.isEmpty()) {
            Bytecode.removeFieldInitializer(sourceConstructor, target.getClassName(), source.getClassName(), selfFields::contains);
        }
        CodeWriter sourceWriter = new CodeWriter(sourceConstructor);
        List<Instruction> sourceBody = sourceWriter.getInstructionList();
        Instruction start = afterSuperCall(sourceBody);
        foldBody(target, source, targetConstructor, sourceWriter, start, sourceBody);
    }

    /**
     * Clones {@code [start, last]} of the source method (re-resolving its constant pool into the target) and
     * splices it into {@code targetMethod} before that method's terminating return, dropping the clone's own
     * returns so control falls through. The body is cloned with no local-variable shift: its slots reuse the
     * target's (dead at the splice point, just before the return), and the source's {@code this} stays at local
     * 0 as the canonical {@code aload_0} that ProGuard's constructor final-field-init analysis requires.
     */
    private static void foldBody(ClassFile target, ClassFile source, MethodEntry targetMethod, CodeWriter sourceWriter,
                                 Instruction start, List<Instruction> sourceBody) throws Exception {
        CodeWriter targetWriter = new CodeWriter(targetMethod);
        CodeWriter.ClonedRange cloned = sourceWriter.cloneRangeWithTargets(
                start, sourceBody.getLast(), 0, target.getConstPool(), new ConstPoolRemapper(source, target)::remap);

        // Insert the body followed by a NOP anchor at its end. The body's returns jump to the anchor so control
        // falls through to whatever follows this segment - a later folded body or the constructor's own return -
        // rather than to the shared terminating return, in front of which a subsequent fold would insert its own
        // body (turning the jump into one that skips it). The anchor chains the per-source folds (which arrive
        // across separate merge calls) statelessly; ProGuard removes the anchor and the redundant jumps. relink
        // recomputes max_stack/max_locals (and ProGuard again downstream), so there is no manual bump.
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
        List<Instruction> insns = writer.getInstructionList();
        for (int i = insns.size() - 1; i >= 0; i--) {
            if (insns.get(i) instanceof ReturnInstruction) {
                return insns.get(i);
            }
        }
        throw new IllegalStateException("Method has no terminating return to splice before");
    }
}
