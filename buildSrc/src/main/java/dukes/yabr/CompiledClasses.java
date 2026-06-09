package dukes.yabr;

import com.tonic.analysis.verifier.VerificationError;
import com.tonic.analysis.verifier.VerificationResult;
import com.tonic.analysis.verifier.Verifier;
import com.tonic.analysis.verifier.VerifierConfig;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;

import java.io.File;
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
 * The compiled classes one transform run operates over - the only type that touches the filesystem. Classes are
 * parsed into a {@link ClassPool} and handed out as {@link ClassFile}s for the passes to edit.
 *
 * <p>The pool is created empty (no JDK classes): loading does not resolve types, and frame regeneration falls
 * back to {@code java/lang/Object} for any type it cannot find rather than failing, so the missing supertypes
 * never matter - the frames are stripped on write regardless.
 */
public final class CompiledClasses {

    private final ClassPool pool;
    private final Map<String, ClassFile> byName;
    private final Map<String, Path> fileByName;
    private final Set<String> modified = new LinkedHashSet<>();

    private CompiledClasses(ClassPool pool, Map<String, ClassFile> byName, Map<String, Path> fileByName) {
        this.pool = pool;
        this.byName = byName;
        this.fileByName = fileByName;
    }

    /** Reads every compiled class under {@code classesDir} into a pool, so call sites resolve program-wide. */
    public static CompiledClasses load(File classesDir) throws Exception {
        ClassPool pool = new ClassPool(true);
        Map<String, ClassFile> byName = new LinkedHashMap<>();
        Map<String, Path> fileByName = new HashMap<>();
        List<Path> classFiles;
        try (Stream<Path> tree = Files.walk(classesDir.toPath())) {
            classFiles = tree.filter(path -> path.toString().endsWith(".class")).toList();
        }
        for (Path classFile : classFiles) {
            ClassFile loaded = pool.loadClass(Files.readAllBytes(classFile));
            byName.put(loaded.getClassName(), loaded);
            fileByName.put(loaded.getClassName(), classFile);
        }
        return new CompiledClasses(pool, byName, fileByName);
    }

    /** The class registered under its internal name (e.g. {@code "Game"}), or null if absent. */
    public ClassFile get(String internalName) {
        return byName.get(internalName);
    }

    /** Every loaded class, for whole-program scans. */
    public Collection<ClassFile> all() {
        return byName.values();
    }

    /** Removes {@code method} from a class and marks the class for rewriting. */
    public void removeMethod(String internalName, MethodEntry method) {
        byName.get(internalName).getMethods().remove(method);
        markModified(internalName);
    }

    /** Drops a class entirely: deletes its compiled file and forgets it (used when merging it into another). */
    public void removeClass(String internalName) throws Exception {
        Files.deleteIfExists(fileByName.get(internalName));
        pool.remove(internalName);
        byName.remove(internalName);
        fileByName.remove(internalName);
        modified.remove(internalName);
    }

    /** Records that a class was mutated and must be rewritten on {@link #writeModified()}. */
    public void markModified(String internalName) {
        modified.add(internalName);
    }

    /**
     * Strips frames (not needed downstream - ProGuard re-preverifies and the shipped Game is frame-stripped) and
     * writes back every modified class.
     */
    public void writeModified() throws Exception {
        for (String className : modified) {
            ClassFile classFile = byName.get(className);
            classFile.stripStackMapTables();
            byte[] transformed = classFile.write();
            verify(classFile, className);
            Files.write(fileByName.get(className), transformed);
        }
    }

    /**
     * Build-time oracle: runs YABR's verifier over each transformed class and fails the build on malformed
     * bytecode, catching transform bugs at their source instead of at runtime under the no-verify loader. Only the
     * pool- and frame-independent checks are enabled - instruction encoding and branch/exception-table ranges -
     * because the pool carries no JDK types (so type/stack-map inference falls back to Object) and frames are
     * stripped before shipping, which would otherwise drown the result in false type/frame warnings.
     */
    private void verify(ClassFile classFile, String className) {
        VerifierConfig config = VerifierConfig.builder()
                .verifyStructure(true)
                .verifyControlFlow(true)
                .strictTypeChecking(false)
                .verifyStackMapTable(false)
                .collectAll()
                .build();
        VerificationResult result = Verifier.builder().classPool(pool).config(config).build().verify(classFile);
        if (result.hasErrors()) {
            StringBuilder message = new StringBuilder("Bytecode verification failed after transforming " + className + ":");
            for (VerificationError error : result.getErrors()) {
                message.append("\n  ").append(error.format());
            }
            throw new IllegalStateException(message.toString());
        }
    }
}
