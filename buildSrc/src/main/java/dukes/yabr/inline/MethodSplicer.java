package dukes.yabr.inline;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.AStoreInstruction;
import com.tonic.analysis.instruction.DStoreInstruction;
import com.tonic.analysis.instruction.FStoreInstruction;
import com.tonic.analysis.instruction.GotoInstruction;
import com.tonic.analysis.instruction.IStoreInstruction;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.analysis.instruction.LStoreInstruction;
import com.tonic.analysis.instruction.NopInstruction;
import com.tonic.analysis.instruction.ReturnInstruction;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.table.ExceptionTableEntry;
import com.tonic.utill.Modifiers;
import dukes.yabr.Instructions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.tonic.utill.Opcode.ASTORE;
import static com.tonic.utill.Opcode.DSTORE;
import static com.tonic.utill.Opcode.FSTORE;
import static com.tonic.utill.Opcode.GOTO;
import static com.tonic.utill.Opcode.ISTORE;
import static com.tonic.utill.Opcode.LSTORE;

/**
 * Replaces a call with a relocated copy of the callee's body. The callee's locals are shifted to fresh caller
 * slots, its returns become jumps past the inlined block, a prologue pops the call's operands into those slots,
 * and any exception handlers are relocated onto the caller - so behavior is unchanged while the call and the
 * separate method both disappear.
 */
final class MethodSplicer {

    private MethodSplicer() {
    }

    /**
     * A callee exception handler captured as positions in the cloned body, resolved to offsets after splicing.
     * Positions (not handles) are used because branch instructions are rebuilt on every relink - a try block's
     * end-exclusive instruction is typically a {@code goto} - so a held handle would go stale.
     * {@code endIndex} is null when the range ran to the callee's end (its end maps to the continuation).
     */
    private record RelocatedHandler(int startIndex, Integer endIndex, int handlerIndex, int catchType) {
    }

    /** Inlines {@code callee} into its sole call site in {@code caller} (both in {@code declaringClass}). */
    static void splice(String declaringClass, MethodEntry caller, MethodEntry callee) throws Exception {
        CodeWriter callerWriter = new CodeWriter(caller);
        int callerMaxStack = callerWriter.getMaxStack();
        int base = callerWriter.getMaxLocals();
        Instruction call = findCall(callerWriter, declaringClass, callee);

        // Clone the callee's body with its locals shifted onto fresh caller slots from baseLocal up. Callee and
        // caller share the class constant pool, so operands stay valid without remapping.
        CodeWriter calleeWriter = new CodeWriter(callee);
        List<Instruction> calleeBody = Instructions.toList(calleeWriter.getInstructions());
        Map<Integer, Integer> indexByOffset = new HashMap<>();
        for (int i = 0; i < calleeBody.size(); i++) {
            indexByOffset.put(calleeBody.get(i).getOffset(), i);
        }
        // Clone via cloneRangeWithTargets + the ClonedRange splice so any switch in the body carries its targets
        // by identity into the caller - plain cloneRange is not alignment-correct for a switch spliced to a
        // different 4-byte boundary (its padding would be miscomputed and corrupt the tableswitch operands).
        CodeWriter.ClonedRange cloned = calleeWriter.cloneRangeWithTargets(
                calleeBody.getFirst(), calleeBody.getLast(), base, null, null);
        List<Instruction> body = cloned.instructions();

        // Splice [argument stores][cloned body][anchor] in place of the call. The NOP anchor is the continuation:
        // relocated returns jump to it and it falls through to whatever followed the call. It must be a stable,
        // non-branch handle - a branch there (e.g. the `ifeq` of `if (callee())`) is rebuilt on every relink, so
        // a handle to it would go stale across the return rewrites and the gotos would resolve to themselves
        // (an infinite self-loop). ProGuard removes the anchor and the redundant jumps.
        callerWriter.insertBefore(call, argumentStores(callee, base));
        callerWriter.insertBefore(call, cloned);
        Instruction continuation = new NopInstruction(0, 0);
        callerWriter.insertBefore(call, continuation);

        List<RelocatedHandler> handlers = new ArrayList<>();
        for (ExceptionTableEntry entry : callee.getCodeAttribute().getExceptionTable()) {
            handlers.add(new RelocatedHandler(indexByOffset.get(entry.getStartPc()),
                    indexByOffset.get(entry.getEndPc()), indexByOffset.get(entry.getHandlerPc()), entry.getCatchType()));
        }

        // Rewrite each relocated return to jump to the continuation, leaving any return value on the stack -
        // exactly what the caller expects after the call. Returns keep their identity across relinks, so the
        // remaining ones in the body list are still valid handles as we go.
        for (Instruction insn : body) {
            if (insn instanceof ReturnInstruction) {
                GotoInstruction jump = new GotoInstruction(GOTO.getCode(), 0, (short) 0);
                callerWriter.setBranchTarget(jump, continuation);
                callerWriter.replaceInstruction(insn, jump);
            }
        }
        callerWriter.removeInstruction(call);
        callerWriter.write();

        // Resolve handler positions to final offsets. After the splice the body is the contiguous run of its own
        // length immediately before the continuation, so indexing it picks up the current (possibly rebuilt)
        // instruction objects with their final offsets.
        CodeAttribute code = caller.getCodeAttribute();
        if (!handlers.isEmpty()) {
            List<Instruction> finalInsns = Instructions.toList(callerWriter.getInstructions());
            int end = finalInsns.indexOf(continuation);
            List<Instruction> bodyFinal = finalInsns.subList(end - body.size(), end);
            for (RelocatedHandler handler : handlers) {
                int endPc = handler.endIndex() != null
                        ? bodyFinal.get(handler.endIndex()).getOffset() : continuation.getOffset();
                code.getExceptionTable().add(new ExceptionTableEntry(
                        bodyFinal.get(handler.startIndex()).getOffset(), endPc,
                        bodyFinal.get(handler.handlerIndex()).getOffset(), handler.catchType()));
            }
        }

        // relink already grows maxLocals to cover the relocated slots; set both maxima explicitly as well, since
        // over-estimating is valid and free and ProGuard recomputes them precisely downstream.
        code.setMaxLocals(Math.max(code.getMaxLocals(), base + calleeWriter.getMaxLocals()));
        code.setMaxStack(Math.max(code.getMaxStack(), callerMaxStack + calleeWriter.getMaxStack()));
    }

    private static Instruction findCall(CodeWriter writer, String declaringClass, MethodEntry callee) {
        for (Instruction insn : writer.getInstructions()) {
            if (CallSiteLocator.callsMethod(insn, declaringClass, callee)) {
                return insn;
            }
        }
        throw new IllegalStateException("Call to " + declaringClass + "#" + callee.getName() + " vanished before splicing");
    }

    /**
     * Prologue that pops the call's operands - [receiver?, arg0, ..., argN-1] on the stack - into the local slots
     * the cloned body reads from. The callee's locals map one-to-one onto caller slots from {@code base} up, so
     * the receiver lands at {@code base} and arguments follow it.
     */
    private static List<Instruction> argumentStores(MethodEntry callee, int base) {
        boolean isStatic = Modifiers.isStatic(callee.getAccess());
        List<Character> kinds = argumentKinds(callee.getDesc());
        int[] slots = new int[kinds.size()];
        int nextSlot = isStatic ? base : base + 1;
        for (int i = 0; i < kinds.size(); i++) {
            slots[i] = nextSlot;
            nextSlot += (kinds.get(i) == 'J' || kinds.get(i) == 'D') ? 2 : 1;
        }

        List<Instruction> stores = new ArrayList<>();
        for (int i = kinds.size() - 1; i >= 0; i--) {
            stores.add(storeFor(kinds.get(i), slots[i]));
        }
        if (!isStatic) {
            stores.add(new AStoreInstruction(ASTORE.getCode(), 0, base));
        }
        return stores;
    }

    private static Instruction storeFor(char kind, int slot) {
        return switch (kind) {
            case 'J' -> new LStoreInstruction(LSTORE.getCode(), 0, slot);
            case 'F' -> new FStoreInstruction(FSTORE.getCode(), 0, slot);
            case 'D' -> new DStoreInstruction(DSTORE.getCode(), 0, slot);
            case 'A' -> new AStoreInstruction(ASTORE.getCode(), 0, slot);
            default -> new IStoreInstruction(ISTORE.getCode(), 0, slot);
        };
    }

    /** One kind char per parameter: {@code I/F/J/D} for primitives (others fold to {@code I}) and {@code A} for references. */
    private static List<Character> argumentKinds(String descriptor) {
        List<Character> kinds = new ArrayList<>();
        int i = descriptor.indexOf('(') + 1;
        while (descriptor.charAt(i) != ')') {
            char c = descriptor.charAt(i);
            if (c == '[') {
                while (descriptor.charAt(i) == '[') {
                    i++;
                }
                if (descriptor.charAt(i) == 'L') {
                    i = descriptor.indexOf(';', i);
                }
                kinds.add('A');
                i++;
            } else if (c == 'L') {
                i = descriptor.indexOf(';', i) + 1;
                kinds.add('A');
            } else {
                kinds.add(c == 'J' || c == 'F' || c == 'D' ? c : 'I');
                i++;
            }
        }
        return kinds;
    }
}
