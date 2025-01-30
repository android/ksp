import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

description = "Kotlin Symbol Processor"

val kotlinBaseVersion: String by project
val junitVersion: String by project
val googleTruthVersion: String by project
val agpBaseVersion: String by project
val signingKey: String? by project
val signingPassword: String? by project
val aaCoroutinesVersion: String? by project

tasks.withType<KotlinCompile> {
    compilerOptions.freeCompilerArgs.add("-Xjvm-default=all-compatibility")
}

plugins {
    kotlin("jvm")
    id("java-gradle-plugin")
    `maven-publish`
    signing
    id("org.jetbrains.dokka")
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin-api:$kotlinBaseVersion")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinBaseVersion")
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinBaseVersion")
    // replace AGP dependency w/ gradle-api when we have source registering API available.
    compileOnly("com.android.tools.build:gradle:$agpBaseVersion")
    compileOnly(gradleApi())
    compileOnly(project(":kotlin-analysis-api"))
    // Ensure stdlib version is not inconsistent due to kotlin plugin version.
    compileOnly(kotlin("stdlib", version = kotlinBaseVersion))
    implementation(project(":api"))
    implementation(project(":common-deps"))
    testImplementation(gradleApi())
    testImplementation(project(":api"))
    testImplementation("junit:junit:$junitVersion")
    testImplementation("com.google.truth:truth:$googleTruthVersion")
    testImplementation(gradleTestKit())
}

tasks.named("validatePlugins").configure {
    onlyIf {
        // while traversing classpath, this hits a class not found issue.
        // Disabled until gradle kotlin version and our kotlin version matches
        // java.lang.ClassNotFoundException: org/jetbrains/kotlin/compilerRunner/KotlinLogger
        false
    }
}

gradlePlugin {
    plugins {
        create("symbol-processing-gradle-plugin") {
            id = "com.google.devtools.ksp"
            displayName = "com.google.devtools.ksp.gradle.plugin"
            implementationClass = "com.google.devtools.ksp.gradle.KspGradleSubplugin"
            description = "Kotlin symbol processing integration for Gradle"
        }
    }
}

tasks {
    val sourcesJar by creating(Jar::class) {
        archiveClassifier.set("sources")
        from(project.sourceSets.main.get().allSource)
    }
}

val dokkaJavadocJar by tasks.register<Jar>("dokkaJavadocJar") {
    dependsOn(tasks.dokkaJavadoc)
    from(tasks.dokkaJavadoc.flatMap { it.outputDirectory })
    archiveClassifier.set("javadoc")
}

publishing {
    publications {
        // the name of this publication should match the name java-gradle-plugin looks up
        // https://github.com/gradle/gradle/blob/master/subprojects/plugin-development/src/main/java/org/gradle/plugin/devel/plugins/MavenPluginPublishPlugin.java#L73
        this.create<MavenPublication>("pluginMaven") {
            artifactId = "symbol-processing-gradle-plugin"
            artifact(tasks["sourcesJar"])
            artifact(tasks["dokkaJavadocJar"])
            pom {
                name.set("symbol-processing-gradle-plugin")
                description.set("Kotlin symbol processing integration for Gradle")
            }
        }
    }
}

signing {
    isRequired = hasProperty("signingKey")
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(extensions.getByType<PublishingExtension>().publications)
}

/**
 * Create a properties file with that can be read from the gradle-plugin tests to setup test
 * projects.
 */
val testPropsOutDir = layout.buildDirectory.dir("test-config")
val writeTestPropsTask = tasks.register<WriteProperties>("prepareTestConfiguration") {
    description = "Generates a properties file with the current environment for gradle integration tests"
    destinationFile = testPropsOutDir.map { it.file("testprops.properties") }
    property("kspVersion", version)
    property("mavenRepoDir", rootProject.layout.buildDirectory.dir("repos/test").get().asFile.absolutePath)
    property("kspProjectRootDir", rootDir.absolutePath)
    property("processorClasspath", project.tasks["compileTestKotlin"].outputs.files.asPath)
}

normalization {
    runtimeClasspath {
        properties("**/testprops.properties") {
            ignoreProperty("kspProjectRootDir")
            ignoreProperty("mavenRepoDir")
            ignoreProperty("processorClasspath")
        }
    }
}

java {
    sourceSets {
        test {
            resources.srcDir(testPropsOutDir)
        }
    }
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.named("compileTestKotlin").configure {
    dependsOn(writeTestPropsTask)
}

tasks.named("processTestResources").configure {
    dependsOn(writeTestPropsTask)
}

tasks.named<Test>("test").configure {
    dependsOn(":api:publishAllPublicationsToTestRepository")
    dependsOn(":common-deps:publishAllPublicationsToTestRepository")
    dependsOn(":gradle-plugin:publishAllPublicationsToTestRepository")
    dependsOn(":symbol-processing:publishAllPublicationsToTestRepository")
    dependsOn(":symbol-processing-aa-embeddable:publishAllPublicationsToTestRepository")
}

abstract class WriteVersionSrcTask : DefaultTask() {
    @get:Input
    abstract val kspVersion: Property<String>
    @get:Input
    abstract val kotlinVersion: Property<String>
    @get:Input
    abstract val coroutinesVersion: Property<String>

    @get:OutputDirectory
    abstract val outputSrcDir: DirectoryProperty

    @TaskAction
    fun generate() {
        outputSrcDir.file("KSPVersions.kt").get().asFile.writeText(
            """
            package com.google.devtools.ksp.gradle
            val KSP_KOTLIN_BASE_VERSION = "${kotlinVersion.get()}"
            val KSP_VERSION = "${kspVersion.get()}"
            val KSP_COROUTINES_VERSION = "${coroutinesVersion.get()}"
            """.trimIndent()
        )
    }
}

val writeVersionSrcTask = tasks.register<WriteVersionSrcTask>("generateKSPVersions") {
    kspVersion = version.toString()
    kotlinVersion = kotlinBaseVersion
    coroutinesVersion = aaCoroutinesVersion
    outputSrcDir = layout.buildDirectory.dir("generated/ksp-versions")
}

kotlin {
    sourceSets {
        main {
            kotlin.srcDir(writeVersionSrcTask)
        }
    }
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}
