package dukes.yabr;

import com.tonic.analysis.instruction.Instruction;

import java.util.ArrayList;
import java.util.List;

/** Small shared helpers for working with YABR instruction streams. */
public final class Instructions {

    private Instructions() {
    }

    /** Collects a method's instruction iterable into a random-access list. */
    public static List<Instruction> toList(Iterable<Instruction> instructions) {
        List<Instruction> list = new ArrayList<>();
        instructions.forEach(list::add);
        return list;
    }
}
