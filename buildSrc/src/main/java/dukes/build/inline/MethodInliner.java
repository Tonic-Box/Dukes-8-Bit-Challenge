package dukes.build.inline;

import dukes.build.CompiledClasses;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the build-time inliner: each allowlisted single-call method is spliced into its one caller and
 * deleted, reclaiming per-method overhead before ProGuard while the source stays decomposed. Reading and
 * writing live in {@link CompiledClasses}, call-site analysis in {@link CallSiteLocator}, the relocation in
 * {@link MethodSplicer}. Allowlist entries are {@code "ClassName#methodName"} (or {@code "...#descriptor"}).
 */
public final class MethodInliner {

    private MethodInliner() {
    }

    /** Inlines each allowlisted method into the loaded class set; returns how many were inlined. */
    public static int inline(CompiledClasses classes, List<String> allowlist) {
        if (allowlist.isEmpty()) {
            return 0;
        }
        CallSiteLocator locator = new CallSiteLocator(classes);
        for (String entry : allowlist) {
            String[] parts = entry.split("#");
            String declaringClass = parts[0];
            MethodNode callee = locator.resolveCallee(declaringClass, parts[1], parts.length > 2 ? parts[2] : null);
            CallSite site = locator.locate(declaringClass, callee);

            MethodSplicer.splice(site.caller(), site.instruction(), callee);
            classes.removeMethod(declaringClass, callee);
        }
        return allowlist.size();
    }

    /**
     * Lists every {@code "Class#method"} private single-call candidate. Whether inlining each shrinks the build
     * is decided by the real post-ProGuard measurement in {@code tools/tune-inline.sh}, not here.
     */
    public static List<String> discoverCandidates(File classesDir) throws Exception {
        CompiledClasses classes = CompiledClasses.load(classesDir);
        CallSiteLocator locator = new CallSiteLocator(classes);
        List<String> candidates = new ArrayList<>();
        for (ClassNode owner : List.copyOf(classes.all())) {
            for (MethodNode callee : owner.methods) {
                if (isCandidate(callee) && locator.findSoleCallSite(owner.name, callee) != null) {
                    candidates.add(owner.name + "#" + callee.name);
                }
            }
        }
        return candidates;
    }

    /** Only private, non-synthetic, non-constructor methods are eligible; private guarantees class-local calls. */
    private static boolean isCandidate(MethodNode method) {
        return (method.access & Opcodes.ACC_PRIVATE) != 0
                && (method.access & Opcodes.ACC_SYNTHETIC) == 0
                && !method.name.equals("<init>")
                && !method.name.equals("<clinit>");
    }
}
