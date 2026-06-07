package dukes.build.merge;

import dukes.build.CompiledClasses;
import org.objectweb.asm.tree.ClassNode;

/**
 * Merges one class entirely into another so two constant pools collapse into one: absorb the source's members,
 * repoint whoever referenced it, delete it. Fuses the stateless Renderer into Game (four source classes, three shipped).
 */
public final class ClassMerger {

    private ClassMerger() {
    }

    /** Moves everything from {@code sourceName} into {@code targetName}, then removes the source class. */
    public static int merge(CompiledClasses classes, String targetName, String sourceName) throws Exception {
        ClassNode target = classes.get(targetName);
        ClassNode source = classes.get(sourceName);
        if (target == null || source == null) {
            return 0;
        }
        int moved = MemberAbsorber.absorb(target, source);
        ReferenceRedirector.redirect(classes, source, targetName);
        classes.removeClass(sourceName);
        classes.markModified(targetName);
        return moved;
    }
}
