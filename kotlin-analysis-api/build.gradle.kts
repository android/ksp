import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.google.devtools.ksp.RelativizingPathProvider
import java.io.ByteArrayOutputStream

description = "Kotlin Symbol Processing implementation using Kotlin Analysis API"

val signingKey: String? by project
val signingPassword: String? by project

val kotlinBaseVersion: String by project

val junitVersion: String by project
val junit5Version: String by project
val junitPlatformVersion: String by project
val libsForTesting: Configuration by configurations.creating
val libsForTestingCommon: Configuration by configurations.creating

val aaKotlinBaseVersion: String by project
val aaIntellijVersion: String by project
val aaGuavaVersion: String by project
val aaAsmVersion: String by project
val aaFastutilVersion: String by project
val aaStax2Version: String by project
val aaAaltoXmlVersion: String by project
val aaStreamexVersion: String by project
val aaCoroutinesVersion: String by project

plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka")
    id("com.github.johnrengelman.shadow")
    `maven-publish`
    signing
}

val depSourceJars: Configuration by configurations.creating
val depJarsForCheck: Configuration by configurations.creating
val compilerJar: Configuration by configurations.creating

dependencies {
    listOf(
        "com.jetbrains.intellij.platform:util-rt",
        "com.jetbrains.intellij.platform:util-class-loader",
        "com.jetbrains.intellij.platform:util-text-matching",
        "com.jetbrains.intellij.platform:util",
        "com.jetbrains.intellij.platform:util-base",
        "com.jetbrains.intellij.platform:util-xml-dom",
        "com.jetbrains.intellij.platform:core",
        "com.jetbrains.intellij.platform:core-impl",
        "com.jetbrains.intellij.platform:extensions",
        "com.jetbrains.intellij.platform:diagnostic",
        "com.jetbrains.intellij.java:java-frontback-psi",
        "com.jetbrains.intellij.java:java-frontback-psi-impl",
        "com.jetbrains.intellij.java:java-psi",
        "com.jetbrains.intellij.java:java-psi-impl",
    ).forEach {
        implementation("$it:$aaIntellijVersion") { isTransitive = false }
        depSourceJars("$it:$aaIntellijVersion:sources") { isTransitive = false }
    }

    listOf(
        "org.jetbrains.kotlin:analysis-api-k2-for-ide",
        "org.jetbrains.kotlin:analysis-api-for-ide",
        "org.jetbrains.kotlin:low-level-api-fir-for-ide",
        "org.jetbrains.kotlin:analysis-api-platform-interface-for-ide",
        "org.jetbrains.kotlin:symbol-light-classes-for-ide",
        "org.jetbrains.kotlin:analysis-api-standalone-for-ide",
        "org.jetbrains.kotlin:analysis-api-impl-base-for-ide",
        "org.jetbrains.kotlin:kotlin-compiler-common-for-ide",
        "org.jetbrains.kotlin:kotlin-compiler-fir-for-ide",
        "org.jetbrains.kotlin:kotlin-compiler-fe10-for-ide",
        "org.jetbrains.kotlin:kotlin-compiler-ir-for-ide",
    ).forEach {
        implementation("$it:$aaKotlinBaseVersion") { isTransitive = false }
        depSourceJars("$it:$aaKotlinBaseVersion:sources") { isTransitive = false }
    }

    implementation("io.javaslang:javaslang:2.0.6")  // needed transitively for `kotlin-compiler-common-for-ide`
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:0.3.4")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    compileOnly(kotlin("stdlib", aaKotlinBaseVersion))

    implementation("com.google.guava:guava:$aaGuavaVersion")
    implementation("one.util:streamex:$aaStreamexVersion")
    implementation("org.jetbrains.intellij.deps:asm-all:$aaAsmVersion")
    implementation("org.codehaus.woodstox:stax2-api:$aaStax2Version") { isTransitive = false }
    implementation("com.fasterxml:aalto-xml:$aaAaltoXmlVersion") { isTransitive = false }
    implementation("com.github.ben-manes.caffeine:caffeine:2.9.3")
    implementation("org.jetbrains.intellij.deps.jna:jna:5.9.0.26") { isTransitive = false }
    implementation("org.jetbrains.intellij.deps.jna:jna-platform:5.9.0.26") { isTransitive = false }
    implementation("org.jetbrains.intellij.deps:trove4j:1.0.20200330") { isTransitive = false }
    implementation("org.jetbrains.intellij.deps:log4j:1.2.17.2") { isTransitive = false }
    implementation("org.jetbrains.intellij.deps:jdom:2.0.6") { isTransitive = false }
    implementation("io.javaslang:javaslang:2.0.6")
    implementation("javax.inject:javax.inject:1")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.6.10")
    implementation("org.lz4:lz4-java:1.7.1") { isTransitive = false }
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$aaCoroutinesVersion") { isTransitive = false }
    implementation(
        "org.jetbrains.intellij.deps.fastutil:intellij-deps-fastutil:$aaFastutilVersion"
    ) {
        isTransitive = false
    }
    implementation("org.jetbrains:annotations:24.1.0")

    compileOnly(project(":common-deps"))

    implementation(project(":api"))
    implementation(project(":common-util"))

    testImplementation(kotlin("stdlib", aaKotlinBaseVersion))
    testImplementation("junit:junit:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junit5Version")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junit5Version")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-params:$junit5Version")
    testRuntimeOnly("org.junit.platform:junit-platform-suite:$junitPlatformVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-compiler:$aaKotlinBaseVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-compiler-internal-test-framework:$aaKotlinBaseVersion")
    testImplementation(project(":common-deps"))
    testImplementation(project(":test-utils"))

    libsForTesting(kotlin("stdlib", aaKotlinBaseVersion))
    libsForTesting(kotlin("test", aaKotlinBaseVersion))
    libsForTesting(kotlin("script-runtime", aaKotlinBaseVersion))
    libsForTestingCommon(kotlin("stdlib-common", aaKotlinBaseVersion))

    depJarsForCheck("org.jetbrains.kotlin", "kotlin-stdlib", kotlinBaseVersion)
    depJarsForCheck(project(":api"))
    depJarsForCheck(project(":common-deps"))

    compilerJar("org.jetbrains.kotlin:kotlin-compiler-common-for-ide:$aaKotlinBaseVersion")
}

sourceSets.main {
    java.srcDirs("src/main/kotlin")
}

fun Project.javaPluginExtension(): JavaPluginExtension = the()
val JavaPluginExtension.testSourceSet: SourceSet
    get() = sourceSets.getByName("test")
val Project.testSourceSet: SourceSet
    get() = javaPluginExtension().testSourceSet

repositories {
    flatDir {
        dirs("${project.rootDir}/third_party/prebuilt/repo/")
    }
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-ide-plugin-dependencies")
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
}

tasks.withType<org.gradle.jvm.tasks.Jar> {
    archiveClassifier.set("real")
}

tasks.withType<ShadowJar>().configureEach {
    dependencies {
        exclude(project(":api"))
    }
    exclude("kotlin/**")
    exclude("kotlinx/coroutines/**")
    archiveClassifier.set("")
    minimize {
        exclude(dependency("org.lz4:lz4-java:.*"))
        exclude(dependency("com.github.ben-manes.caffeine:caffeine:.*"))
    }
    mergeServiceFiles()

    doLast {
        // Checks for missing dependencies
        val jarJar = archiveFile.get().asFile
        val depJars = depJarsForCheck.resolve().map(File::getPath)
        val stdout = ByteArrayOutputStream()
        try {
            exec {
                executable = "jdeps"
                args = listOf(
                    "--multi-release", "base",
                    "--missing-deps",
                    "-cp", depJars.joinToString(File.pathSeparator), jarJar.path
                )
                standardOutput = stdout
            }
        } catch (e: org.gradle.process.internal.ExecException) {
            logger.warn(e.message)
        }
        logger.warn(stdout.toString())
    }
}

tasks {
    val sourcesJar by creating(Jar::class) {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        archiveClassifier.set("sources")
        from(sourceSets.main.get().allSource)
        from(project(":common-util").sourceSets.main.get().allSource)
        depSourceJars.resolve().forEach {
            from(zipTree(it))
        }
    }
    val dokkaJavadocJar by creating(Jar::class) {
        dependsOn(dokkaJavadoc)
        from(dokkaJavadoc.flatMap { it.outputDirectory })
        archiveClassifier.set("javadoc")
    }
    publish {
        dependsOn(shadowJar)
        dependsOn(sourcesJar)
        dependsOn(dokkaJavadocJar)
    }
}

publishing {
    publications {
        create<MavenPublication>("shadow") {
            artifactId = "symbol-processing-aa"
            artifact(tasks["shadowJar"])
            artifact(tasks["dokkaJavadocJar"])
            artifact(tasks["sourcesJar"])
            pom {
                name.set("com.google.devtools.ksp:symbol-processing-aa")
                description.set("KSP implementation on Kotlin Analysis API")
                withXml {
                    fun groovy.util.Node.addDependency(
                        groupId: String,
                        artifactId: String,
                        version: String,
                        scope: String = "runtime"
                    ) {
                        appendNode("dependency").apply {
                            appendNode("groupId", groupId)
                            appendNode("artifactId", artifactId)
                            appendNode("version", version)
                            appendNode("scope", scope)
                        }
                    }

                    asNode().appendNode("dependencies").apply {
                        addDependency("org.jetbrains.kotlin", "kotlin-stdlib", kotlinBaseVersion)
                        addDependency("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", aaCoroutinesVersion)
                        addDependency("com.google.devtools.ksp", "symbol-processing-api", version)
                        addDependency("com.google.devtools.ksp", "symbol-processing-common-deps", version)
                    }
                }
            }
        }
    }
}

signing {
    isRequired = hasProperty("signingKey")
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(extensions.getByType<PublishingExtension>().publications)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-receivers")
    }
}

val copyLibsForTesting by tasks.registering(Copy::class) {
    from(configurations["libsForTesting"])
    into("dist/kotlinc/lib")
    val escaped = Regex.escape(aaKotlinBaseVersion)
    rename("(.+)-$escaped\\.jar", "$1.jar")
}

val copyLibsForTestingCommon by tasks.registering(Copy::class) {
    from(configurations["libsForTestingCommon"])
    into("dist/common")
    val escaped = Regex.escape(aaKotlinBaseVersion)
    rename("(.+)-$escaped\\.jar", "$1.jar")
}

tasks.test {
    dependsOn(copyLibsForTesting)
    dependsOn(copyLibsForTestingCommon)
    maxHeapSize = "2g"

    useJUnitPlatform()

    systemProperty("idea.is.unit.test", "true")
    systemProperty("java.awt.headless", "true")
    environment("NO_FS_ROOTS_ACCESS_CHECK", "true")

    testLogging {
        events("passed", "skipped", "failed")
    }

    val ideaHomeDir = layout.buildDirectory.dir("tmp/ideaHome")
        .get()
        .asFile
        .apply { if (!exists()) mkdirs() }
    jvmArgumentProviders.add(RelativizingPathProvider("idea.home.path", ideaHomeDir))
    jvmArgumentProviders.add(RelativizingPathProvider("java.io.tmpdir", temporaryDir))
}
