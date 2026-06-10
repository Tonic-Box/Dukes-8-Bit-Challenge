plugins {
    // Auto-provisions a JDK 11 toolchain (used to pack with pack200 and to run the jar) when the machine lacks one,
    // so `./gradlew build` and `./gradlew run` work without a manually installed legacy JDK.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "dukes8bit"
