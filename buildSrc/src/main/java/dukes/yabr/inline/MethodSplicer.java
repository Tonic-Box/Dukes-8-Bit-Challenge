package dukes.yabr.inline;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.AStoreInstruction;
import com.tonic.analysis.instruction.DStoreInstruction;
import com.tonic.analysis.instruction.FStoreInstruction;
import com.tonic.analysis.instruction.IStoreInstruction;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.analysis.instruction.LStoreInstruction;
import com.tonic.parser.MethodEntry;
import com.tonic.utill.Modifiers;

import java.util.ArrayList;
import java.util.List;

import static com.tonic.utill.Opcode.ASTORE;
import static com.tonic.utill.Opcode.DSTORE;
import static com.tonic.utill.Opcode.FSTORE;
import static com.tonic.utill.Opcode.ISTORE;
import static com.tonic.utill.Opcode.LSTORE;

/**
 * Replaces a call with a relocated copy of the callee's body. The callee's locals are shifted to fresh caller
 * slots, a prologue pops the call's operands into those slots, and the callee's returns are redirected to the
 * call's successor via {@link CodeWriter.ClonedRange#redirectReturns()} - so behavior is unchanged while the call
 * and the separate method both disappear. The clone carries the callee's branch/switch targets and exception table
 * by identity, and relink recomputes offsets and {@code max_stack}/{@code max_locals}, so there is no manual
 * bookkeeping.
 */
final class MethodSplicer {

    private MethodSplicer() {
    }

    /** Inlines {@code callee} into its sole call site in {@code caller} (both in {@code declaringClass}). */
    static void splice(String declaringClass, MethodEntry caller, MethodEntry callee) throws Exception {
        CodeWriter callerWriter = new CodeWriter(caller);
        int base = callerWriter.getMaxLocals();
        Instruction call = findCall(callerWriter, declaringClass, callee);

        // Clone the callee's body with its locals shifted onto fresh caller slots from base up; callee and caller
        // share the class constant pool, so operands stay valid without remapping. redirectReturns rewrites the
        // body's returns into continuation branches that bind to the splice successor.
        CodeWriter calleeWriter = new CodeWriter(callee);
        List<Instruction> calleeBody = calleeWriter.getInstructionList();
        CodeWriter.ClonedRange body = calleeWriter
                .cloneRangeWithTargets(calleeBody.getFirst(), calleeBody.getLast(), base, null, null)
                .redirectReturns();

        // Prologue (pops [receiver?, args] into the body's slots) before the call; body after it so its redirected
        // returns flow to the call's successor; then the call is removed. Final order: [prologue][body][successor].
        callerWriter.insertBefore(call, argumentStores(callee, base));
        callerWriter.insertAfter(call, body);
        callerWriter.removeInstruction(call);
        callerWriter.write();
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
