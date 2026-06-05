plugins {
    application
    id("com.gradleup.shadow") version "8.3.6"
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

tasks.named<Jar>("shadowJar") {
    archiveBaseName = "DukesDescent"
    archiveClassifier = ""
}

tasks.register("size") {
    group = "verification"
    description = "Reports total runtime size (compiled classes + resources) and the packaged shadow jar size."
    dependsOn("classes", "shadowJar")
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

        val jarFile = tasks.named<Jar>("shadowJar").get().archiveFile.get().asFile
        if (jarFile.exists()) {
            println("Shadow jar: %d B (%.2f KB)  %s".format(jarFile.length(), jarFile.length() / 1024.0, jarFile.name))
        }
    }
}
