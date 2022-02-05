import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

evaluationDependsOn(":common-util")
evaluationDependsOn(":compiler-plugin")

val kotlinBaseVersion: String by project
val signingKey: String? by project
val signingPassword: String? by project

plugins {
    kotlin("jvm")
    id("com.github.johnrengelman.shadow") version "6.0.0"
    `maven-publish`
    signing
}

val packedJars by configurations.creating

dependencies {
    packedJars(project(":compiler-plugin")) { isTransitive = false }
    packedJars(project(":common-util")) { isTransitive = false }
}

tasks.withType<ShadowJar>() {
    archiveClassifier.set("")
    // ShadowJar picks up the `compile` configuration by default and pulls stdlib in.
    // Therefore, specifying another configuration instead.
    configurations = listOf(packedJars)
}

tasks {
    publish {
        dependsOn(shadowJar)
        dependsOn(project(":compiler-plugin").tasks["dokkaJavadocJar"])
        dependsOn(project(":compiler-plugin").tasks["sourcesJar"])
    }
}

publishing {
    publications {
        create<MavenPublication>("shadow") {
            artifactId = "symbol-processing-cmdline"
            artifact(tasks["shadowJar"])
            artifact(project(":compiler-plugin").tasks["dokkaJavadocJar"])
            artifact(project(":compiler-plugin").tasks["sourcesJar"])
            pom {
                name.set("com.google.devtools.ksp:symbol-processing-cmdline")
                description.set("Symbol processing for K/N and command line")
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
                        addDependency("org.jetbrains.kotlin", "kotlin-compiler", kotlinBaseVersion)
                        addDependency("com.google.devtools.ksp", "symbol-processing-api", version)
                    }
                }
            }
        }
    }
}

signing {
    isRequired = hasProperty("signingKey") && !gradle.taskGraph.hasTask("publishToMavenLocal")
    sign(extensions.getByType<PublishingExtension>().publications)
}
