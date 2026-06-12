plugins {
    java
}

group = "com.tonicbox.dukes8bit"
version = "1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // Constructing Game calls Sound.init(); point the MIDI synth at a name that does not resolve so the synth
    // fails to open and Sound becomes a no-op - otherwise its non-daemon scheduler keeps the test JVM alive.
    systemProperty("javax.sound.midi.Synthesizer", "#none")
    systemProperty("java.awt.headless", "true")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
}

// runBot opens the game window with the autopilot attached (same as plain `run`); runBotSim runs headless at
// full CPU speed for rapid brain tuning. Bot tunables pass through as -P properties (-Pruns=20,
// -PsimMinutes=60, -Pautoplay.kite=false).
tasks.register("clearLog") {
    group = "autoplay"
    description = "Delete logs/autoplay.log for a fresh session."
    outputs.upToDateWhen { false }
    doLast {
        val log = layout.projectDirectory.file("logs/autoplay.log").asFile
        println(if (log.delete()) "Cleared logs/autoplay.log" else "logs/autoplay.log already clear")
    }
}

listOf("runBot" to "BotMain", "runBotSim" to "BotSim").forEach { (taskName, entryClass) ->
    tasks.register<JavaExec>(taskName) {
        group = "autoplay"
        description = if (taskName == "runBot") "Watch the autopilot play in a window."
            else "Run headless fast-forward autopilot runs and log decisions."
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass = entryClass
        systemProperties(project.properties
            .filterKeys { it == "runs" || it == "simMinutes" || it.startsWith("autoplay.") }
            .mapValues { it.value.toString() })
    }
}
