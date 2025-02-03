/*
 * Copyright 2021 Google LLC
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.devtools.ksp.gradle

import com.android.build.api.dsl.CommonExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.api.SourceKind
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.internal.KaptTask
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmAndroidCompilation
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly
import java.io.File
import java.util.concurrent.Callable

/**
 * This helper class handles communication with the android plugin.
 * It is isolated in a separate class to avoid adding dependency on the android plugin.
 * Instead, we add a compileOnly dependency to the Android Plugin, which means we can still function
 * without the Android plugin. The downside is that we need to ensure never to access Android
 * plugin APIs directly without checking its existence (we have tests covering that case).
 */
@Suppress("UnstableApiUsage") // some android APIs are unsable.
object AndroidPluginIntegration {

    fun forEachAndroidSourceSet(project: Project, onSourceSet: (String) -> Unit) {
        project.pluginManager.withPlugin("com.android.base") {
            // for android modules, we need a configuration per source set
            decorateAndroidExtension(project, onSourceSet)
        }
    }

    private fun decorateAndroidExtension(project: Project, onSourceSet: (String) -> Unit) {
        val sourceSets = when (val androidExt = project.extensions.getByName("android")) {
            is BaseExtension -> androidExt.sourceSets
            is CommonExtension<*, *, *, *, *, *> -> androidExt.sourceSets
            else -> throw RuntimeException("Unsupported Android Gradle plugin version.")
        }
        sourceSets.all {
            onSourceSet(it.name)
        }
    }

    fun getCompilationSourceSets(kotlinCompilation: KotlinJvmAndroidCompilation): List<String> {
        return kotlinCompilation.androidVariant
            .sourceSets
            .map { it.name }
    }

    /**
     * Support KspTaskJvm and KspAATask tasks
     */
    @Suppress("DEPRECATION")
    private fun tryUpdateKspWithAndroidSourceSets(
        project: Project,
        kotlinCompilation: KotlinJvmAndroidCompilation,
        kspTaskProvider: TaskProvider<*>
    ) {
        val kaptProvider: TaskProvider<Task>? =
            project.locateTask(kotlinCompilation.compileTaskProvider.kaptTaskName)

        val sources = kotlinCompilation.androidVariant.getSourceFolders(SourceKind.JAVA)
        kspTaskProvider.configure { task ->
            // this is workaround for KAPT generator that prevents circular dependency
            val filteredSources = Callable {
                val destinationProperty = (kaptProvider?.get() as? KaptTask)?.destinationDir
                val dir = destinationProperty?.get()?.asFile
                sources.filter { dir?.isParentOf(it.dir) != true }
            }
            when (task) {
                is KspTaskJvm -> {
                    task.setSource(filteredSources)
                    task.dependsOn(filteredSources)
                }

                is KspAATask -> {
                    task.kspConfig.javaSourceRoots.from(filteredSources)
                    task.dependsOn(filteredSources)
                }

                else -> Unit
            }
        }
    }

    // same logic as in Kapt name generation method
    private val TaskProvider<*>.kaptTaskName: String
        get() {
            val prefix = "kapt"
            return if (name.startsWith("compile")) {
                name.replaceFirst("compile", prefix)
            } else {
                "$prefix${name.capitalizeAsciiOnly()}"
            }
        }

    private fun registerGeneratedSources(
        project: Project,
        kotlinCompilation: KotlinJvmAndroidCompilation,
        kspTaskProvider: TaskProvider<*>,
        javaOutputDir: File,
        kotlinOutputDir: File,
        classOutputDir: File,
        resourcesOutputDir: FileCollection,
    ) {
        val kspJavaOutput = project.fileTree(javaOutputDir).builtBy(kspTaskProvider)
        val kspKotlinOutput = project.fileTree(kotlinOutputDir).builtBy(kspTaskProvider)
        val kspClassOutput = project.fileTree(classOutputDir).builtBy(kspTaskProvider)
        kspJavaOutput.include("**/*.java")
        kspKotlinOutput.include("**/*.kt")
        kspClassOutput.include("**/*.class")
        kotlinCompilation.androidVariant.registerExternalAptJavaOutput(kspJavaOutput)
        kotlinCompilation.androidVariant.addJavaSourceFoldersToModel(kspKotlinOutput.dir)
        kotlinCompilation.androidVariant.registerPreJavacGeneratedBytecode(kspClassOutput)
        kotlinCompilation.androidVariant.registerPostJavacGeneratedBytecode(resourcesOutputDir)
    }

    fun syncSourceSets(
        project: Project,
        kotlinCompilation: KotlinJvmAndroidCompilation,
        kspTaskProvider: TaskProvider<*>,
        javaOutputDir: File,
        kotlinOutputDir: File,
        classOutputDir: File,
        resourcesOutputDir: FileCollection
    ) {
        // Order is important here as we update task with AGP generated sources and
        // then update AGP with source that KSP will generate.
        // Mixing this up will cause circular dependency in Gradle
        tryUpdateKspWithAndroidSourceSets(project, kotlinCompilation, kspTaskProvider)

        registerGeneratedSources(
            project,
            kotlinCompilation,
            kspTaskProvider,
            javaOutputDir,
            kotlinOutputDir,
            classOutputDir,
            resourcesOutputDir
        )
    }

    /**
     * Returns false for AGP versions 8.10.0-alpha01 or higher.
     *
     * Returns true for older AGP versions or when AGP version cannot be determined.
     */
    val useLegacyVariantApi: Boolean by lazy {
        val agpVersion = try {
            val versionClass = Class.forName("com.android.Version")
            val versionField = versionClass.getField("ANDROID_GRADLE_PLUGIN_VERSION")
            versionField.get(null) as String
        } catch (e: Exception) {
            // AGP not applied or version field not found
            null
        }

        // Fall back to using the legacy Variant API if can't determine AGP version for now
        if (agpVersion == null) {
            return@lazy true
        }
        val agpMajorVersion = agpVersion.split(".").getOrNull(0)?.toIntOrNull() ?: 0
        val agpMinorVersion = agpVersion.split(".").getOrNull(1)?.toIntOrNull() ?: 0
        return@lazy agpMajorVersion < 8 || agpMajorVersion == 8 && agpMinorVersion < 10
    }
}
