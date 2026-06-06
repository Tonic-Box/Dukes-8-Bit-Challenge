import proguard.gradle.ProGuardTask
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

buildscript {
    repositories { mavenCentral() }
    dependencies { classpath("com.guardsquare:proguard-gradle:7.9.1") }
}

plugins {
    application
}

group = "com.tonicbox.dukes8bit"
version = "1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

application {
    mainClass = "Main"
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.isDebug = false
    options.encoding = "UTF-8"
}

// ProGuard minifies in place, overwriting build/classes/java/main. Without this, a later run whose
// sources are unchanged would leave those already-minified classes in place and feed them back through
// ProGuard, double-minifying into a different byte count. Forcing a clean recompile each time keeps the
// minify input pristine, so `size` is deterministic.
tasks.named<JavaCompile>("compileJava") {
    outputs.upToDateWhen { false }
    doFirst { destinationDirectory.get().asFile.deleteRecursively() }
}

// No runtime dependencies, so the plain jar is already a complete runnable jar. ProGuard later
// overwrites this jar in place; force a repackage each run so it always reflects the freshly-compiled
// (non-minified) classes rather than serving back a previously-minified jar (which would double-minify).
tasks.jar {
    archiveBaseName = "DukesDescent"
    manifest { attributes["Main-Class"] = "Main" }
    outputs.upToDateWhen { false }
}

tasks.register<ProGuardTask>("proguard") {
    group = "build"
    description = "Minify in place (shrink + shorten member names, keep class names): the jar and build/classes/java/main."
    dependsOn("jar")
    // Always re-run: the task overwrites the compile/jar outputs, which would otherwise confuse up-to-date checks.
    outputs.upToDateWhen { false }

    val jdk = System.getProperty("java.home")
    val jarFile = layout.buildDirectory.file("libs/DukesDescent-$version.jar").get().asFile
    val classesDir = layout.buildDirectory.dir("classes/java/main").get().asFile
    val tmpJar = layout.buildDirectory.file("tmp/proguard-min.jar").get().asFile

    injars(jarFile)
    outjars(tmpJar)
    libraryjars("$jdk/jmods/java.base.jmod")
    libraryjars("$jdk/jmods/java.desktop.jmod")

    // Keep the launch entry point (Java 25 no-arg main); shrinking may still drop anything truly unused.
    keep("public class Main { public static void main(java.lang.String[]); static void main(); }")

    // Keep all class names readable (Game/Renderer/Sound/Main); members may be shortened. Two optimizer
    // passes are turned off because they grow this codebase rather than shrink it: single-caller inlining
    // (it inlines the whole program into the Main entry class, ~+13 KB) and method-parameter propagation
    // (measured ~+17 B here). Pass count is raised to 8, where the optimizer converges. Everything else stays on.
    keepnames("class **")
    overloadaggressively()
    allowaccessmodification()
    optimizationpasses(8)
    optimizations("!class/merging/*,!method/inlining/unique,!method/propagation/*")
    dontwarn()
    dontnote()

    doLast {
        // Replace the jar with the minified one.
        tmpJar.copyTo(jarFile, overwrite = true)
        // Replace the compiled classes with the minified ones (this is what the size metric measures).
        classesDir.walkTopDown().filter { it.isFile && it.extension == "class" }.forEach { it.delete() }
        var classBytes = 0L
        ZipFile(tmpJar).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.endsWith(".class")) {
                    val dest = File(classesDir, entry.name)
                    dest.parentFile.mkdirs()
                    zip.getInputStream(entry).use { ins ->
                        FileOutputStream(dest).use { outs -> ins.copyTo(outs) }
                    }
                    classBytes += dest.length()
                }
            }
        }
        tmpJar.delete()
        println("Minified in place -> classes %d B (%.2f KB), jar %d B  %s"
            .format(classBytes, classBytes / 1024.0, jarFile.length(), jarFile.name))
    }
}

// Build and run both go through ProGuard, so the jar and the classes that ship/execute are minified.
tasks.named("build") { dependsOn("proguard") }
tasks.named("run") { dependsOn("proguard") }

tasks.register("size") {
    group = "verification"
    description = "Reports total runtime size (minified classes + resources) and the packaged jar size."
    dependsOn("proguard")
    doLast {
        val measured = listOf(
            layout.buildDirectory.dir("classes/java/main").get().asFile,
            layout.buildDirectory.dir("resources/main").get().asFile,
        )
        var totalBytes = 0L
        var fileCount = 0
        measured.filter { it.exists() }.forEach { root ->
            root.walkTopDown().filter { it.isFile }.sortedBy { it.path }.forEach { file ->
                totalBytes += file.length()
                fileCount++
                println("%9d B  %s".format(file.length(), file.relativeTo(root.parentFile)))
            }
        }
        val kilobytes = totalBytes / 1024.0
        println("-".repeat(48))
        println("Files: %d".format(fileCount))
        println("Total: %d B (%.2f KB)".format(totalBytes, kilobytes))

        val jarFile = tasks.named<Jar>("jar").get().archiveFile.get().asFile
        if (jarFile.exists()) {
            println("Jar: %d B (%.2f KB)  %s".format(jarFile.length(), jarFile.length() / 1024.0, jarFile.name))
        }
    }
}
