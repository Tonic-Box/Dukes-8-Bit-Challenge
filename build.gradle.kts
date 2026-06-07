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

// ProGuard minifies in place, so feeding already-minified classes back in would double-minify and drift the
// byte count. Clean-recompile each run to keep the minify input pristine and `size` deterministic.
tasks.named<JavaCompile>("compileJava") {
    outputs.upToDateWhen { false }
    doFirst { destinationDirectory.get().asFile.deleteRecursively() }
}

// Inlines the winning single-call methods listed in inline-allowlist.txt, which tools/tune-inline.sh generates
// by measuring each candidate's real post-ProGuard size (a static gate can't predict ProGuard's optimizer).
// Override with -Pinline=Game#a,Renderer#b for one-off experiments.
val allowlistFile = layout.projectDirectory.file("inline-allowlist.txt").asFile
val explicitInline: List<String>? = (findProperty("inline") as String?)
    ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
val inlineMethods = tasks.register("inlineMethods") {
    dependsOn("compileJava")
    outputs.upToDateWhen { false }
    val classesDir = layout.buildDirectory.dir("classes/java/main").get().asFile
    doLast {
        val allowlist = explicitInline ?: if (allowlistFile.exists())
            allowlistFile.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("//") }
        else emptyList()
        val count = dukes.build.MethodInliner.inline(classesDir, allowlist)
        println("inlineMethods: inlined $count method(s) before packaging.")
    }
}

// Lists every private single-call candidate (on the freshly compiled, pre-inlining classes) for the tuner.
tasks.register("listInlineCandidates") {
    dependsOn("compileJava")
    val classesDir = layout.buildDirectory.dir("classes/java/main").get().asFile
    doLast {
        dukes.build.MethodInliner.discoverCandidates(classesDir).sorted().forEach { println(it) }
    }
}

// No runtime deps, so the plain jar is already runnable. Force a repackage each run so ProGuard minifies
// freshly-compiled classes rather than re-minifying a previous jar.
tasks.jar {
    archiveBaseName = "DukesDescent"
    manifest { attributes["Main-Class"] = "Main" }
    dependsOn(inlineMethods)
    outputs.upToDateWhen { false }
}

tasks.register<ProGuardTask>("proguard") {
    group = "build"
    description = "Minify in place (shrink + shorten members, keep class names): the jar and compiled classes."
    dependsOn("jar")
    // Always re-run: it overwrites the compile/jar outputs, which would confuse up-to-date checks.
    outputs.upToDateWhen { false }

    val jdk = System.getProperty("java.home")
    val jarFile = layout.buildDirectory.file("libs/DukesDescent-$version.jar").get().asFile
    val classesDir = layout.buildDirectory.dir("classes/java/main").get().asFile
    val tmpJar = layout.buildDirectory.file("tmp/proguard-min.jar").get().asFile

    injars(jarFile)
    outjars(tmpJar)
    libraryjars("$jdk/jmods/java.base.jmod")
    libraryjars("$jdk/jmods/java.desktop.jmod")

    // Keep the launch entry point; shrinking may still drop anything unused.
    keep("public class Main { public static void main(java.lang.String[]); static void main(); }")

    // Keep class names readable; members may shorten. Two optimizations are off because they grow this build:
    // unique-method inlining (cascades the program into Main, ~+13 KB) and parameter propagation (~+17 B).
    // 8 passes is where the optimizer converges.
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
