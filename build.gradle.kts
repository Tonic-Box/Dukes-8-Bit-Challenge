plugins {
    application
}

group = "com.tonicbox.dukes8bit"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

application {
    mainClass = "com.tonicbox.dukes8bit.Main"
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.setDebug(false)
    options.encoding = "UTF-8"
}

tasks.register("size") {
    group = "verification"
    description = "Reports total runtime size (compiled classes + runtime resources)."
    dependsOn("classes")
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
    }
}
