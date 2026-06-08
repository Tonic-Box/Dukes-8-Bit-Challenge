package dukes.build;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The compiled classes one inliner run operates over. The only type that touches the filesystem or the ASM
 * read/write/verify machinery; analysis and transform code work against the {@link ClassNode}s it hands out.
 */
public final class CompiledClasses {

    private final Map<String, ClassNode> byName;
    private final Map<String, Path> fileByName;
    private final ClassLoader typeLoader;
    private final Set<String> modified = new LinkedHashSet<>();

    private CompiledClasses(Map<String, ClassNode> byName, Map<String, Path> fileByName, ClassLoader typeLoader) {
        this.byName = byName;
        this.fileByName = fileByName;
        this.typeLoader = typeLoader;
    }

    /** Reads every compiled class under {@code classesDir} into a tree, so call sites resolve program-wide. */
    public static CompiledClasses load(File classesDir) throws Exception {
        // Frame computation and verification resolve types through the compiled classes plus the JDK
        // (the build JVM already has java.base/java.desktop on its module path, via the parent loader).
        URLClassLoader typeLoader = new URLClassLoader(new URL[]{classesDir.toURI().toURL()}, CompiledClasses.class.getClassLoader());

        Map<String, ClassNode> byName = new LinkedHashMap<>();
        Map<String, Path> fileByName = new HashMap<>();
        List<Path> classFiles;
        try (Stream<Path> tree = Files.walk(classesDir.toPath())) {
            classFiles = tree.filter(path -> path.toString().endsWith(".class")).toList();
        }
        for (Path classFile : classFiles) {
            ClassNode classNode = new ClassNode();
            new ClassReader(Files.readAllBytes(classFile)).accept(classNode, ClassReader.SKIP_FRAMES);
            byName.put(classNode.name, classNode);
            fileByName.put(classNode.name, classFile);
        }
        return new CompiledClasses(byName, fileByName, typeLoader);
    }

    /** The class registered under its internal name (e.g. {@code "Game"}), or null if absent. */
    public ClassNode get(String internalName) {
        return byName.get(internalName);
    }

    /** Every loaded class, for whole-program scans. */
    public Collection<ClassNode> all() {
        return byName.values();
    }

    /** Removes {@code method} from a class and marks the class for rewriting. */
    public void removeMethod(String internalName, MethodNode method) {
        byName.get(internalName).methods.remove(method);
        markModified(internalName);
    }

    /** Drops a class entirely: deletes its compiled file and forgets it (used when merging it into another). */
    public void removeClass(String internalName) throws Exception {
        Files.deleteIfExists(fileByName.get(internalName));
        byName.remove(internalName);
        fileByName.remove(internalName);
        modified.remove(internalName);
    }

    /** Records that a class was mutated and must be rewritten on {@link #writeModified()}. */
    public void markModified(String internalName) {
        modified.add(internalName);
    }

    /**
     * Computes stack sizes (not frames), verifies, and writes back every modified class. Frames are left out
     * deliberately: ProGuard preverifies the classes it reads and the shipped Game is frame-stripped anyway,
     * so recomputing them here (the only step needing whole-program type resolution) would be wasted work.
     */
    public void writeModified() throws Exception {
        for (String className : modified) {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            byName.get(className).accept(writer);
            byte[] transformed = writer.toByteArray();
            verify(transformed, className);
            Files.write(fileByName.get(className), transformed);
        }
    }

    private void verify(byte[] bytes, String className) {
        StringWriter report = new StringWriter();
        CheckClassAdapter.verify(new ClassReader(bytes), typeLoader, false, new PrintWriter(report));
        if (!report.toString().isEmpty()) {
            throw new IllegalStateException("Bytecode verification failed after inlining into " + className + ":\n" + report);
        }
    }
}
