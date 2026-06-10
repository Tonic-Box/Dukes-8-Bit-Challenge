package dukes.yabr.merge;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.analysis.instruction.NewInstruction;
import com.tonic.parser.ClassFile;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import dukes.yabr.CompiledClasses;
import dukes.yabr.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Pins the regression that shipped once: a referrer holding a source-typed field initialised with
 * {@code new Source()} must have that field and its initializer removed. The removals match on the <em>source</em>
 * descriptor/operand, so they have to run before {@code redirectOwner} canonicalises Source to the target - if not,
 * the field survives rewritten to the target type and the constructor is left building {@code new Target()}, an
 * infinite-recursion {@code <init>}.
 */
class ReferenceRedirectorTest {

    @Test
    void stripsSelfReferentialFieldAndItsInitializer(@TempDir Path dir) throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("Source", """
                public class Source {
                    public Source() {}
                }
                """);
        sources.put("Referrer", """
                public class Referrer {
                    Source helper = new Source();
                    int run() { return 1; }
                }
                """);
        CompiledClasses classes = Fixtures.compile(dir, sources);

        ReferenceRedirector.redirect(classes, classes.get("Source"), "Target");

        ClassFile referrer = classes.get("Referrer");
        for (FieldEntry field : referrer.getFields()) {
            assertNotEquals("LSource;", field.getDesc(), "source-typed field must be removed");
            assertNotEquals("LTarget;", field.getDesc(),
                    "field must be removed, not left rewritten to the target type");
        }

        MethodEntry constructor = referrer.getMethod("<init>");
        assertNotNull(constructor, "constructor should still exist");
        for (Instruction instruction : new CodeWriter(constructor).getInstructionList()) {
            if (instruction instanceof NewInstruction allocation) {
                String allocated = allocation.resolveClass();
                assertNotEquals("Source", allocated, "the new Source() initializer must be removed");
                assertNotEquals("Target", allocated,
                        "the initializer must not be rewritten to new Target() - that is the recursion bug");
            }
        }
    }
}
