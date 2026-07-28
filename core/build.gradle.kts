import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    // A target bytecode version, not a toolchain requirement: the compiler
    // just emits JVM 17 class files using whatever JDK is already running
    // Gradle, no separate JDK 17 install/download needed (unlike
    // jvmToolchain(), which forces Gradle to locate or fetch one).
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// The Kotlin JVM plugin also wires up a (source-less, here) Java compile
// task, which otherwise defaults to whatever JDK runs Gradle; keep it in
// step with compilerOptions.jvmTarget above so the two don't disagree.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
