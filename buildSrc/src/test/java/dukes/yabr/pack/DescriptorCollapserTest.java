package dukes.yabr.pack;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import dukes.yabr.CompiledClasses;
import dukes.yabr.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DescriptorCollapser} shortens the object-type tokens of private/static method descriptors (which the
 * no-verify loader never resolves) while leaving everything the JVM still matches by exact descriptor untouched.
 * The descriptors are checked against the re-read bytes, since the collapse mutates the constant in place and the
 * member's cached descriptor string can lag.
 */
class DescriptorCollapserTest {

    @Test
    void collapsesPrivateDescriptorsButLeavesJdkAndOverridesReal(@TempDir Path dir) throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("Sample", """
                public class Sample {
                    private int helper(String s, int n) { return s.length() + n; }
                    public int run(String s) { return helper(s, 3); }
                    public String toString() { return "x"; }
                }
                """);
        CompiledClasses classes = Fixtures.compile(dir, sources);
        ClassFile sample = classes.get("Sample");

        int collapsed = DescriptorCollapser.collapse(sample);
        assertTrue(collapsed > 0, "expected at least one descriptor to collapse");

        ClassFile reloaded = new ClassPool(true).loadClass(sample.write());
        assertEquals("(LA;I)I", descriptorOf(reloaded, "helper"),
                "a private method's object-type parameter should collapse to the placeholder");
        assertEquals("(Ljava/lang/String;)I", descriptorOf(reloaded, "run"),
                "a non-private/static method's descriptor must stay real");
        assertEquals("()Ljava/lang/String;", descriptorOf(reloaded, "toString"),
                "an override's descriptor must stay real for virtual dispatch");
    }

    private static String descriptorOf(ClassFile classFile, String name) {
        for (MethodEntry method : classFile.getMethods()) {
            if (method.getName().equals(name)) {
                return method.getDesc();
            }
        }
        throw new AssertionError("no method named " + name);
    }
}
