/*
 * Copyright 2022 Google LLC
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
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

@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.google.devtools.ksp.gradle
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskProvider
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.process.ExecOperations
import org.gradle.work.InputChanges
import org.gradle.workers.WorkerExecutor
import org.jetbrains.kotlin.buildtools.api.SourcesChanges
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2MetadataCompilerArguments
import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompilerOptionsDefault
import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompilerOptionsHelper
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptionsDefault
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptionsHelper
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformCommonCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformCommonCompilerOptionsDefault
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformCommonCompilerOptionsHelper
import org.jetbrains.kotlin.gradle.dsl.KotlinNativeCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinNativeCompilerOptionsDefault
import org.jetbrains.kotlin.gradle.dsl.KotlinNativeCompilerOptionsHelper
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilationInfo
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompileTool
import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileCommon
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile
import org.jetbrains.kotlin.gradle.tasks.TaskOutputsBackup
import org.jetbrains.kotlin.gradle.tasks.configuration.BaseKotlin2JsCompileConfig
import org.jetbrains.kotlin.gradle.tasks.configuration.KotlinCompileCommonConfig
import org.jetbrains.kotlin.gradle.tasks.configuration.KotlinCompileConfig
import org.jetbrains.kotlin.konan.target.HostManager
import java.io.File
import java.nio.file.Paths
import javax.inject.Inject

/**
 * TODO: Replace with KGP's Kotlin*Factory after:
 * https://youtrack.jetbrains.com/issue/KT-54986/KGP-API-to-toggle-incremental-compilation
 * https://youtrack.jetbrains.com/issue/KT-55031/KGP-API-to-create-compilation-tasks-of-JS-Metadata-and-Native
 */
class KotlinFactories {
    companion object {
        fun registerKotlinJvmCompileTask(
            project: Project,
            taskName: String,
            kotlinCompilation: KotlinCompilation<*>,
        ): TaskProvider<out KspTaskJvm> {
            return project.tasks.register(taskName, KspTaskJvm::class.java).also { kspTaskProvider ->
                @Suppress("UNCHECKED_CAST")
                KotlinCompileConfig(KotlinCompilationInfo(kotlinCompilation))
                    .execute(kspTaskProvider as TaskProvider<KotlinCompile>)

                // useClasspathSnapshot isn't configurable per task.
                // Workaround: enable the other path and ignore irrelevant changes
                // See [KotlinCompileConfig] in for details.
                // FIXME: make it configurable in upstream or support useClasspathSnapshot == true, if possible.
                kspTaskProvider.configure {
                    val compilerOptions = kotlinCompilation.compilerOptions.options as KotlinJvmCompilerOptions
                    KotlinJvmCompilerOptionsHelper.syncOptionsAsConvention(
                        from = compilerOptions,
                        into = it.compilerOptions
                    )

                    if (it.classpathSnapshotProperties.useClasspathSnapshot.get()) {
                        it.classpathSnapshotProperties.classpath.from(project.provider { it.libraries })
                    }
                }
            }
        }

        fun registerKotlinJSCompileTask(
            project: Project,
            taskName: String,
            kotlinCompilation: KotlinCompilation<*>,
        ): TaskProvider<out KspTaskJS> {
            return project.tasks.register(taskName, KspTaskJS::class.java).also { kspTaskProvider ->
                @Suppress("UNCHECKED_CAST")
                BaseKotlin2JsCompileConfig<Kotlin2JsCompile>(KotlinCompilationInfo(kotlinCompilation))
                    .execute(kspTaskProvider as TaskProvider<Kotlin2JsCompile>)
                kspTaskProvider.configure {
                    val compilerOptions = kotlinCompilation.compilerOptions.options as KotlinJsCompilerOptions
                    KotlinJsCompilerOptionsHelper.syncOptionsAsConvention(
                        from = compilerOptions,
                        into = it.compilerOptions
                    )

                    it.incrementalJsKlib = false
                }
            }
        }

        fun registerKotlinMetadataCompileTask(
            project: Project,
            taskName: String,
            kotlinCompilation: KotlinCompilation<*>,
        ): TaskProvider<out KspTaskMetadata> {
            return project.tasks.register(taskName, KspTaskMetadata::class.java).also { kspTaskProvider ->
                @Suppress("UNCHECKED_CAST")
                KotlinCompileCommonConfig(KotlinCompilationInfo(kotlinCompilation))
                    .execute(kspTaskProvider as TaskProvider<KotlinCompileCommon>)

                kspTaskProvider.configure {
                    val compilerOptions =
                        kotlinCompilation.compilerOptions.options as KotlinMultiplatformCommonCompilerOptions
                    KotlinMultiplatformCommonCompilerOptionsHelper.syncOptionsAsConvention(
                        from = compilerOptions,
                        into = it.compilerOptions
                    )
                }
            }
        }

        fun registerKotlinNativeCompileTask(
            project: Project,
            taskName: String,
            kotlinCompilation: KotlinCompilation<*>
        ): TaskProvider<out KspTaskNative> {
            return project.tasks.register(
                taskName,
                KspTaskNative::class.java,
                KotlinCompilationInfo(kotlinCompilation)
            ).apply {
                configure { kspTask ->
                    val compilerOptions = kotlinCompilation.compilerOptions.options as KotlinNativeCompilerOptions
                    KotlinNativeCompilerOptionsHelper.syncOptionsAsConvention(
                        from = compilerOptions,
                        into = kspTask.compilerOptions
                    )
                    kspTask.produceUnpackagedKlib.set(false)
                    kspTask.onlyIf {
                        // KonanTarget is not properly serializable, hence we should check by name
                        // see https://youtrack.jetbrains.com/issue/KT-61657.
                        val konanTargetName = kspTask.konanTarget.name
                        HostManager().enabled.any {
                            it.name == konanTargetName
                        }
                    }
                }
            }
        }
    }
}

interface KspTask : Task {
    @get:Internal
    val options: ListProperty<SubpluginOption>

    @Deprecated("KSP is going to use compilation tasks created by KGP and no longer support this property.")
    @get:Nested
    val commandLineArgumentProviders: ListProperty<CommandLineArgumentProvider>

    @get:Internal
    val incrementalChangesTransformers: ListProperty<(SourcesChanges) -> List<SubpluginOption>>
}

@CacheableTask
abstract class KspTaskJvm @Inject constructor(
    workerExecutor: WorkerExecutor,
    objectFactory: ObjectFactory
) : KotlinCompile(
        objectFactory.newInstance(KotlinJvmCompilerOptionsDefault::class.java),
        workerExecutor,
        objectFactory
    ),
    KspTask {
    @get:OutputDirectory
    abstract val destination: Property<File>

    // Override incrementalProps to exclude irrelevant changes
    override val incrementalProps: List<FileCollection>
        get() = listOf(
            sources,
            javaSources,
            commonSourceSet,
            classpathSnapshotProperties.classpath,
        )

    // Overrding an internal function is hacky.
    // TODO: Ask upstream to open it.
    @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER", "EXPOSED_PARAMETER_TYPE", "FunctionName", "unused")
    fun `callCompilerAsync$kotlin_gradle_plugin_common`(
        args: K2JVMCompilerArguments,
        inputChanges: InputChanges,
        taskOutputsBackup: TaskOutputsBackup?
    ) {
        val changedFiles = getChangedFiles(inputChanges, incrementalProps)
        val extraOptions = incrementalChangesTransformers.get().flatMap {
            it(changedFiles)
        }
        args.addPluginOptions(extraOptions)
        super.callCompilerAsync(args, inputChanges, taskOutputsBackup)
    }

    override fun skipCondition(): Boolean = sources.isEmpty && javaSources.isEmpty

    @get:InputFiles
    @get:SkipWhenEmpty
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    override val javaSources: FileCollection = super.javaSources.filter {
        !destination.get().isParentOf(it)
    }
}

@CacheableTask
abstract class KspTaskJS @Inject constructor(
    objectFactory: ObjectFactory,
    workerExecutor: WorkerExecutor
) : Kotlin2JsCompile(
        objectFactory.newInstance(KotlinJsCompilerOptionsDefault::class.java),
        objectFactory,
        workerExecutor
    ),
    KspTask {

    // Overrding an internal function is hacky.
    // TODO: Ask upstream to open it.
    @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER", "EXPOSED_PARAMETER_TYPE", "FunctionName", "unused")
    fun `callCompilerAsync$kotlin_gradle_plugin_common`(
        args: K2JSCompilerArguments,
        inputChanges: InputChanges,
        taskOutputsBackup: TaskOutputsBackup?
    ) {
        val changedFiles = getChangedFiles(inputChanges, incrementalProps)
        val extraOptions = incrementalChangesTransformers.get().flatMap {
            it(changedFiles)
        }
        args.addPluginOptions(extraOptions)
        super.callCompilerAsync(args, inputChanges, taskOutputsBackup)
    }
}

@CacheableTask
abstract class KspTaskMetadata @Inject constructor(
    workerExecutor: WorkerExecutor,
    objectFactory: ObjectFactory
) : KotlinCompileCommon(
        objectFactory.newInstance(KotlinMultiplatformCommonCompilerOptionsDefault::class.java),
        workerExecutor,
        objectFactory
    ),
    KspTask {

    // Overrding an internal function is hacky.
    // TODO: Ask upstream to open it.
    @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER", "EXPOSED_PARAMETER_TYPE", "FunctionName", "unused")
    fun `callCompilerAsync$kotlin_gradle_plugin_common`(
        args: K2MetadataCompilerArguments,
        inputChanges: InputChanges,
        taskOutputsBackup: TaskOutputsBackup?
    ) {
        val changedFiles = getChangedFiles(inputChanges, incrementalProps)
        val extraOptions = incrementalChangesTransformers.get().flatMap {
            it(changedFiles)
        }
        args.addPluginOptions(extraOptions)
        super.callCompilerAsync(args, inputChanges, taskOutputsBackup)
    }
}

@CacheableTask
abstract class KspTaskNative @Inject internal constructor(
    compilation: KotlinCompilationInfo,
    objectFactory: ObjectFactory,
    providerFactory: ProviderFactory,
    execOperations: ExecOperations
) : KotlinNativeCompile(
        compilation,
        objectFactory.newInstance(KotlinNativeCompilerOptionsDefault::class.java),
        objectFactory,
        providerFactory,
        execOperations
    ),
    KspTask

internal fun SubpluginOption.toArg() = "plugin:${KspGradleSubplugin.KSP_PLUGIN_ID}:$key=$value"

internal fun CommonCompilerArguments.addPluginOptions(options: List<SubpluginOption>) {
    pluginOptions = (options.map { it.toArg() } + pluginOptions!!).toTypedArray()
}

internal fun File.isParentOf(childCandidate: File): Boolean {
    val parentPath = Paths.get(this.absolutePath).normalize()
    val childCandidatePath = Paths.get(childCandidate.absolutePath).normalize()

    return childCandidatePath.startsWith(parentPath)
}

internal fun disableRunViaBuildToolsApi(kspTask: AbstractKotlinCompileTool<*>) {
    kspTask.runViaBuildToolsApi.value(false).disallowChanges()
}
