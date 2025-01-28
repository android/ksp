import com.google.devtools.ksp.RelativizingInternalPathProvider
import kotlin.math.max

val junitVersion: String by project
val kotlinBaseVersion: String by project
val agpTestVersion: String by project
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

tasks.withType<Test> {
    maxParallelForks = max(1, Runtime.getRuntime().availableProcessors() / 2)
    systemProperty("kotlinVersion", kotlinBaseVersion)
    systemProperty("kspVersion", version)
    systemProperty("agpVersion", agpTestVersion)
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
