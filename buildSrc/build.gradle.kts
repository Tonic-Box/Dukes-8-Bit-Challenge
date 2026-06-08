plugins {
    java
}

repositories {
    mavenCentral()
    maven { url = uri("https://www.jitpack.io") }
}

dependencies {
    implementation("com.github.Tonic-Box:YABR:main-SNAPSHOT")
}

configurations.all {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}
