package dukes.yabr.inline;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import dukes.yabr.CompiledClasses;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Each allowlisted single-call method is spliced into its one caller and deleted, reclaiming per-method overhead
 * before ProGuard while the source stays decomposed. Call-site analysis lives in {@link CallSiteLocator}, the
 * relocation in {@link MethodSplicer}. Allowlist entries are {@code "ClassName#methodName"} (or
 * {@code "...#descriptor"}).
 */
public final class MethodInliner {

    private MethodInliner() {
    }

    /** Inlines each allowlisted method into the loaded class set; returns how many were inlined. */
    public static int inline(CompiledClasses classes, List<String> allowlist) throws Exception {
        if (allowlist.isEmpty()) {
            return 0;
        }
        CallSiteLocator locator = new CallSiteLocator(classes);
        for (String entry : allowlist) {
            String[] parts = entry.split("#");
            String declaringClass = parts[0];
            MethodEntry callee = locator.resolveCallee(declaringClass, parts[1], parts.length > 2 ? parts[2] : null);
            MethodEntry caller = locator.locate(declaringClass, callee);

            MethodSplicer.splice(declaringClass, caller, callee);
            classes.removeMethod(declaringClass, callee);
        }
        return allowlist.size();
    }

    /**
     * Lists every {@code "Class#method"} private single-call candidate (on the freshly compiled, pre-transform
     * classes) for the inline tuner. Whether inlining each shrinks the build is decided by the real post-ProGuard
     * measurement in {@code tools/tune-inline.sh}, not here.
     */
    public static List<String> discoverCandidates(File classesDir) throws Exception {
        CompiledClasses classes = CompiledClasses.load(classesDir);
        CallSiteLocator locator = new CallSiteLocator(classes);
        List<String> candidates = new ArrayList<>();
        for (ClassFile owner : List.copyOf(classes.all())) {
            for (MethodEntry callee : owner.getMethods()) {
                if (isCandidate(callee) && locator.soleCaller(owner.getClassName(), callee) != null) {
                    candidates.add(owner.getClassName() + "#" + callee.getName());
                }
            }
        }
        return candidates;
    }

    /** Only private, non-synthetic, non-constructor methods are eligible; private guarantees class-local calls. */
    private static boolean isCandidate(MethodEntry method) {
        int access = method.getAccess();
        return (access & 0x0002) != 0 && (access & 0x1000) == 0
                && !method.getName().equals("<init>") && !method.getName().equals("<clinit>");
    }
}
