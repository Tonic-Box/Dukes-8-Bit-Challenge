package dukes.build;

import dukes.build.color.ColorPacker;
import dukes.build.inline.MethodInliner;
import dukes.build.merge.ClassMerger;
import java.io.File;
import java.util.List;

/**
 * The single entry point the build calls to run every build-time bytecode pass. Loads the compiled classes
 * once, applies each pass to the shared in-memory tree in order, writes the result once, and reports what each
 * pass did - so adding a pass is a line here rather than another Gradle task.
 */
public final class BuildPasses {

    private BuildPasses() {
    }

    /** Runs each pass over {@code classesDir} in order, reporting what each did. */
    public static void apply(File classesDir, List<String> inlineAllowlist) throws Exception {
        CompiledClasses classes = CompiledClasses.load(classesDir);
        int coloursPacked = ColorPacker.pack(classes);
        int methodsInlined = MethodInliner.inline(classes, inlineAllowlist);
        int methodsMerged = ClassMerger.merge(classes, "Game", "Renderer");
        methodsMerged += ClassMerger.merge(classes, "Game", "Sound");
        methodsMerged += ClassMerger.merge(classes, "Game", "Main");
        classes.writeModified();
        System.out.println("build passes: packed " + coloursPacked + " colour(s), inlined " + methodsInlined + " method(s), merged " + methodsMerged + " method(s) into Game");
    }
}
