package dukes.yabr.merge;

import com.tonic.parser.ClassFile;
import dukes.yabr.CompiledClasses;
import dukes.yabr.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the public merge entry point: the source class's members land in the target and the source class is
 * removed from the pool. (The self-referential-field regression is exercised at its locus in
 * {@link ReferenceRedirectorTest}, since the source-typed field lives in a referrer rather than the target.)
 */
class ClassMergerTest {

    @Test
    void absorbsSourceMembersAndRemovesSource(@TempDir Path dir) throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("Source", """
                public class Source {
                    public Source() {}
                    int compute() { return 42; }
                }
                """);
        sources.put("Target", """
                public class Target {
                    int run() { return new Source().compute(); }
                }
                """);
        CompiledClasses classes = Fixtures.compile(dir, sources);

        int moved = ClassMerger.merge(classes, "Target", "Source");

        assertTrue(moved > 0, "expected members to move from Source into Target");
        assertNull(classes.get("Source"), "Source should be removed from the pool after the merge");

        ClassFile target = classes.get("Target");
        assertNotNull(target.getMethod("compute"), "Source.compute should be absorbed into Target");
        assertNotNull(target.getMethod("run"), "Target's own method should survive the merge");
    }
}
