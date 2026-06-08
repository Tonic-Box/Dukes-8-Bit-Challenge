plugins {
    java
}

repositories {
    mavenCentral()
    maven { url = uri("https://www.jitpack.io") }
}

dependencies {
    // ASM 9.8 is the first release that supports Java 25 class files (major version 69).
    implementation("org.ow2.asm:asm:9.8")
    implementation("org.ow2.asm:asm-tree:9.8")
    implementation("org.ow2.asm:asm-util:9.8")

    implementation("com.github.Tonic-Box:YABR:main-SNAPSHOT")
}
