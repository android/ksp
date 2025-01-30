import com.google.devtools.ksp.RelativizingInternalPathProvider
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import kotlin.math.max

val junitVersion: String by project
val kotlinBaseVersion: String by project
val agpBaseVersion: String by project
val aaCoroutinesVersion: String by project

plugins {
    kotlin("jvm")
}

dependencies {
    testImplementation("junit:junit:$junitVersion")
    testImplementation(gradleTestKit())
    testImplementation("org.jetbrains.kotlin:kotlin-compiler:$kotlinBaseVersion")
    testImplementation(project(":api"))
    testImplementation(project(":gradle-plugin"))
    testImplementation(project(":symbol-processing"))
    testImplementation(project(":symbol-processing-cmdline"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$aaCoroutinesVersion")
}

fun Test.configureCommonSettings() {
    systemProperty("kotlinVersion", kotlinBaseVersion)
    systemProperty("kspVersion", version)
    systemProperty("agpVersion", agpBaseVersion)
    jvmArgumentProviders.add(
        RelativizingInternalPathProvider(
            "testRepo",
            rootProject.layout.buildDirectory.dir("repos/test").get().asFile
        )
    )
    dependsOn(":api:publishAllPublicationsToTestRepository")
    dependsOn(":gradle-plugin:publishAllPublicationsToTestRepository")
    dependsOn(":common-deps:publishAllPublicationsToTestRepository")
    dependsOn(":symbol-processing:publishAllPublicationsToTestRepository")
    dependsOn(":symbol-processing-cmdline:publishAllPublicationsToTestRepository")
    dependsOn(":symbol-processing-aa-embeddable:publishAllPublicationsToTestRepository")
}

val agpCompatibilityTestClasses = listOf("**/AGP731IT.class", "**/AGP741IT.class")

// Create a new test task for the AGP compatibility tests
val agpCompatibilityTest by tasks.registering(Test::class) {
    description = "Runs AGP compatibility tests with maxParallelForks = 1"
    group = "verification"

    // Include only the AGP compatibility tests
    include(agpCompatibilityTestClasses)

    // Set maxParallelForks to 1 to avoid race conditions when downloading SDKs with old AGPs
    maxParallelForks = 1

    // Apply common settings
    configureCommonSettings()
}

tasks.named<Test>("test") {
    maxParallelForks = max(1, Runtime.getRuntime().availableProcessors() / 2)

    // Exclude test classes from agpCompatibilityTest
    exclude(agpCompatibilityTestClasses)

    // Apply common settings
    configureCommonSettings()

    // Ensure that 'test' depends on 'compatibilityTest'
    dependsOn(agpCompatibilityTest)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}
