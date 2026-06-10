plugins {
    java
}

repositories {
    mavenCentral()
    maven { url = uri("https://www.jitpack.io") }
}

dependencies {
    implementation("com.github.Tonic-Box:YABR:main-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

configurations.all {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}

tasks.test {
    useJUnitPlatform()
}
