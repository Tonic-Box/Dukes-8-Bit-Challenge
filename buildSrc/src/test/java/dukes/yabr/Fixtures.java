package dukes.yabr;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Compiles small default-package Java fixtures into an isolated directory and loads them as {@link CompiledClasses},
 * so the bytecode passes can be tested against real compiled classes shaped like the game's without dragging in the
 * whole build.
 */
public final class Fixtures {

    private Fixtures() {
    }

    /** Compiles {@code sources} (class name to source text) under {@code dir} and loads the resulting classes. */
    public static CompiledClasses compile(Path dir, Map<String, String> sources) throws Exception {
        Path sourceDir = Files.createDirectories(dir.resolve("src"));
        Path classesDir = Files.createDirectories(dir.resolve("classes"));
        List<File> files = new ArrayList<>();
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path file = sourceDir.resolve(source.getKey() + ".java");
            Files.writeString(file, source.getValue());
            files.add(file.toFile());
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, null, null)) {
            Iterable<? extends JavaFileObject> units = manager.getJavaFileObjectsFromFiles(files);
            boolean compiled = compiler.getTask(null, manager, null,
                    List.of("-d", classesDir.toString()), null, units).call();
            if (!compiled) {
                throw new IllegalStateException("fixture compilation failed");
            }
        }
        return CompiledClasses.load(classesDir.toFile());
    }
}
