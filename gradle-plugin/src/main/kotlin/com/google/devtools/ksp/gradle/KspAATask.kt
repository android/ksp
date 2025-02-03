/*
 * Copyright 2023 Google LLC
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
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

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.*
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.LogLevel
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Optional
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.work.ChangeType
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinCommonCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmAndroidCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation
import org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompileTool
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile
import org.jetbrains.kotlin.konan.target.HostManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ObjectOutputStream
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import java.util.*
import java.util.concurrent.Callable
import javax.inject.Inject

@CacheableTask
abstract class KspAATask @Inject constructor(
    private val workerExecutor: WorkerExecutor,
) : DefaultTask() {
    @get:Classpath
    abstract val kspClasspath: ConfigurableFileCollection

    @get:Nested
    abstract val kspConfig: KspGradleConfig

    @get:Nested
    abstract val commandLineArgumentProviders: ListProperty<CommandLineArgumentProvider>

    @TaskAction
    fun execute(inputChanges: InputChanges) {
        // FIXME: Create a class loader with clean classpath instead of shadowing existing ones. It'll require either:
        //  1. passing arguments by data structures in stdlib, or
        //  2. hoisting and publishing KspGradleConfig into another package.

        val modifiedSources: MutableList<File> = mutableListOf()
        val removedSources: MutableList<File> = mutableListOf()

        if (inputChanges.isIncremental) {
            listOf(kspConfig.sourceRoots, kspConfig.javaSourceRoots, kspConfig.commonSourceRoots).forEach {
                inputChanges.getFileChanges(it).forEach {
                    when (it.changeType) {
                        ChangeType.ADDED, ChangeType.MODIFIED -> modifiedSources.add(it.file)
                        ChangeType.REMOVED -> removedSources.add(it.file)
                        else -> {}
                    }
                }
            }
        }

        val changedClasses = if (kspConfig.incremental.get()) {
            if (kspConfig.platformType.get() == KotlinPlatformType.jvm) {
                getCPChanges(
                    inputChanges,
                    listOf(
                        kspConfig.sourceRoots,
                        kspConfig.javaSourceRoots,
                        kspConfig.commonSourceRoots,
                        kspConfig.libraries
                    ),
                    kspConfig.cachesDir.asFile.get(),
                    kspConfig.classpathStructure,
                    kspConfig.libraries,
                    kspConfig.processorClasspath,
                )
            } else {
                if (
                    !inputChanges.isIncremental ||
                    inputChanges.getFileChanges(kspConfig.libraries).iterator().hasNext()
                )
                    kspConfig.cachesDir.get().asFile.deleteRecursively()
                emptyList()
            }
        } else {
            kspConfig.cachesDir.get().asFile.deleteRecursively()
            emptyList()
        }

        val workerQueue = workerExecutor.noIsolation()
        workerQueue.submit(KspAAWorkerAction::class.java) {
            it.config = kspConfig
            it.kspClasspath = kspClasspath
            it.modifiedSources = modifiedSources
            it.removedSources = removedSources
            it.isInputChangeIncremental = inputChanges.isIncremental
            it.changedClasses = changedClasses
        }
    }

    companion object {
        @Internal
        internal fun registerKspAATask(
            kotlinCompilation: KotlinCompilation<*>,
            kotlinCompileProvider: TaskProvider<AbstractKotlinCompileTool<*>>,
            processorClasspath: Configuration,
            kspExtension: KspExtension,
        ): TaskProvider<KspAATask> {
            val project = kotlinCompilation.target.project
            val target = kotlinCompilation.target.name
            val sourceSetName = kotlinCompilation.defaultSourceSet.name
            val kspTaskName = kotlinCompileProvider.name.replaceFirst("compile", "ksp")
            val kspAADepCfg = project.configurations.detachedConfiguration(
                project.dependencies.create("${KspGradleSubplugin.KSP_GROUP_ID}:symbol-processing-api:$KSP_VERSION"),
                project.dependencies.create(
                    "${KspGradleSubplugin.KSP_GROUP_ID}:symbol-processing-common-deps:$KSP_VERSION"
                ),
                project.dependencies.create(
                    "${KspGradleSubplugin.KSP_GROUP_ID}:symbol-processing-aa-embeddable:$KSP_VERSION"
                ),
                project.dependencies.create("org.jetbrains.kotlin:kotlin-stdlib:$KSP_KOTLIN_BASE_VERSION"),
                project.dependencies.create(
                    "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$KSP_COROUTINES_VERSION"
                ),
            ).apply {
                isTransitive = false
            }
            val kspTaskProvider = project.tasks.register(kspTaskName, KspAATask::class.java) { kspAATask ->
                kspAATask.kspClasspath.from(kspAADepCfg)
                kspAATask.kspConfig.let { cfg ->
                    cfg.processorClasspath.from(processorClasspath)
                    // Ref: https://github.com/JetBrains/kotlin/blob/6535f86dfe36effeba976802ebd56a5a56071f45/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/mpp/kotlinCompilations.kt#L92
                    val moduleName = when (val compilationName = kotlinCompilation.name) {
                        KotlinCompilation.MAIN_COMPILATION_NAME -> project.name
                        else -> "${project.name}_$compilationName"
                    }
                    cfg.moduleName.value(moduleName)
                    val kotlinOutputDir = KspGradleSubplugin.getKspKotlinOutputDir(project, sourceSetName, target)
                    val javaOutputDir = KspGradleSubplugin.getKspJavaOutputDir(project, sourceSetName, target)
                    kotlinCompilation.allKotlinSourceSetsObservable.forAll { sourceSet ->
                        cfg.sourceRoots.from(sourceSet.kotlin.sourceDirectories)
                        cfg.javaSourceRoots.from(sourceSet.kotlin.sourceDirectories)
                    }
                    if (kotlinCompilation is KotlinCommonCompilation) {
                        cfg.commonSourceRoots.from(kotlinCompilation.defaultSourceSet.kotlin)
                    }

                    if (kotlinCompilation is KotlinJvmAndroidCompilation) {
                        // Workaround of a dependency resolution issue of AGP.
                        // FIXME: figure out how to filter or set variant attributes correctly.
                        val kspOutputDir = KspGradleSubplugin.getKspOutputDir(project, sourceSetName, target)
                        cfg.libraries.from(
                            project.files(
                                Callable {
                                    kotlinCompileProvider.get().libraries.filter {
                                        !kspOutputDir.isParentOf(it) &&
                                            !(it.isDirectory && it.listFiles()?.isEmpty() == true)
                                    }
                                }
                            )
                        )
                    } else {
                        cfg.libraries.from(kotlinCompilation.compileDependencyFiles)
                    }

                    val compilerOptions = kotlinCompilation.compilerOptions.options
                    val langVer = compilerOptions.languageVersion.orNull?.version ?: KSP_KOTLIN_BASE_VERSION
                    val apiVer = compilerOptions.apiVersion.orNull?.version ?: KSP_KOTLIN_BASE_VERSION
                    cfg.languageVersion.value(langVer.split('.', '-').take(2).joinToString("."))
                    cfg.apiVersion.value(apiVer.split('.', '-').take(2).joinToString("."))

                    cfg.projectBaseDir.value(File(project.project.projectDir.canonicalPath))
                    cfg.cachesDir.value(KspGradleSubplugin.getKspCachesDir(project, sourceSetName, target))
                    cfg.outputBaseDir.value(KspGradleSubplugin.getKspOutputDir(project, sourceSetName, target))
                    cfg.kotlinOutputDir.value(kotlinOutputDir)
                    cfg.javaOutputDir.value(javaOutputDir)
                    cfg.classOutputDir.value(KspGradleSubplugin.getKspClassOutputDir(project, sourceSetName, target))
                    cfg.resourceOutputDir.value(
                        KspGradleSubplugin.getKspResourceOutputDir(
                            project,
                            sourceSetName,
                            target
                        )
                    )
                    cfg.processorOptions.putAll(kspExtension.apOptions)

                    fun ListProperty<CommandLineArgumentProvider>.mapArgProviders() =
                        map { providers ->
                            buildMap {
                                for (provider in providers) {
                                    provider.asArguments().forEach { argument ->
                                        val kv = Regex("(\\S+)=(\\S+)").matchEntire(argument)?.groupValues
                                        require(kv != null && kv.size == 3) {
                                            "Processor arguments not in the format \\S+=\\S+: $argument"
                                        }
                                        put(kv[1], kv[2])
                                    }
                                }
                            }
                        }

                    cfg.processorOptions.putAll(kspExtension.commandLineArgumentProviders.mapArgProviders())
                    cfg.processorOptions.putAll(kspAATask.commandLineArgumentProviders.mapArgProviders())

                    val logLevel = LogLevel.entries.first {
                        project.logger.isEnabled(it)
                    }
                    cfg.logLevel.value(logLevel)
                    cfg.allWarningsAsErrors.value(kspExtension.allWarningsAsErrors)
                    cfg.excludedProcessors.value(kspExtension.excludedProcessors)

                    cfg.incremental.value(
                        project.providers
                            .gradleProperty("ksp.incremental")
                            .map { it.toBoolean() }
                            .orElse(true)
                    )
                    cfg.incrementalLog.value(
                        project.providers
                            .gradleProperty("ksp.incremental.log")
                            .map { it.toBoolean() }
                            .orElse(false)
                    )

                    cfg.classpathStructure.from(getClassStructureFiles(project, cfg.libraries))

                    if (compilerOptions is KotlinJvmCompilerOptions) {
                        // TODO: set proper jdk home
                        cfg.jdkHome.value(File(System.getProperty("java.home")))

                        val jvmDefaultMode = compilerOptions.freeCompilerArgs
                            .map { args -> args.filter { it.startsWith("-Xjvm-default=") } }
                            .map { it.lastOrNull()?.substringAfter("=") ?: "disable" }

                        cfg.jvmDefaultMode.value(jvmDefaultMode)

                        cfg.jvmTarget.value(compilerOptions.jvmTarget.map { it.target })
                    }

                    cfg.platformType.value(kotlinCompilation.platformType)
                    if (kotlinCompilation is KotlinNativeCompilation) {
                        val konanTargetName = kotlinCompilation.target.konanTarget.name
                        cfg.konanTargetName.value(konanTargetName)
                        cfg.konanHome.value((kotlinCompileProvider.get() as KotlinNativeCompile).konanHome)
                        kspAATask.onlyIf {
                            HostManager().enabled.any {
                                it.name == konanTargetName
                            }
                        }
                    }

                    // TODO: pass targets of common
                }
            }

            return kspTaskProvider
        }
    }
}

abstract class KspGradleConfig @Inject constructor() {
    @get:Classpath
    abstract val processorClasspath: ConfigurableFileCollection

    @get:Input
    abstract val moduleName: Property<String>

    @get:InputFiles
    @get:SkipWhenEmpty
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceRoots: ConfigurableFileCollection

    @get:InputFiles
    @get:SkipWhenEmpty
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val commonSourceRoots: ConfigurableFileCollection

    @get:InputFiles
    @get:SkipWhenEmpty
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val javaSourceRoots: ConfigurableFileCollection

    @get:Incremental
    @get:Classpath
    abstract val libraries: ConfigurableFileCollection

    @get:Input
    @get:Optional
    abstract val jdkHome: Property<File>

    @get:Internal
    abstract val projectBaseDir: Property<File>

    @get:Internal
    abstract val outputBaseDir: Property<File>

    @get:LocalState
    abstract val cachesDir: DirectoryProperty

    @get:OutputDirectory
    abstract val kotlinOutputDir: Property<File>

    @get:OutputDirectory
    abstract val javaOutputDir: Property<File>

    @get:OutputDirectory
    abstract val classOutputDir: Property<File>

    @get:OutputDirectory
    abstract val resourceOutputDir: Property<File>

    @get:Input
    abstract val languageVersion: Property<String>

    @get:Input
    abstract val apiVersion: Property<String>

    @get:Input
    abstract val processorOptions: MapProperty<String, String>

    // Unfortunately, passing project.logger over is not possible.
    @get:Input
    abstract val logLevel: Property<LogLevel>

    @get:Input
    abstract val allWarningsAsErrors: Property<Boolean>

    @get:Input
    abstract val excludedProcessors: SetProperty<String>

    @get:Input
    @get:Optional
    abstract val jvmTarget: Property<String>

    @get:Input
    @get:Optional
    abstract val jvmDefaultMode: Property<String>

    @get:Input
    abstract val incremental: Property<Boolean>

    @get:Input
    abstract val incrementalLog: Property<Boolean>

    @get:Internal
    abstract val classpathStructure: ConfigurableFileCollection

    @get:Input
    abstract val platformType: Property<KotlinPlatformType>

    @get:Input
    @get:Optional
    abstract val konanTargetName: Property<String>

    @get:Input
    @get:Optional
    abstract val konanHome: Property<String>
}

interface KspAAWorkParameter : WorkParameters {
    var config: KspGradleConfig
    var kspClasspath: ConfigurableFileCollection
    var modifiedSources: List<File>
    var removedSources: List<File>
    var changedClasses: List<String>
    var isInputChangeIncremental: Boolean
}

var isolatedClassLoaderCache = mutableMapOf<String, URLClassLoader>()

abstract class KspAAWorkerAction : WorkAction<KspAAWorkParameter> {
    override fun execute() {
        val gradleCfg = parameters.config
        val kspClasspath = parameters.kspClasspath
        val key = kspClasspath.files.map { it.toURI().toURL() }.joinToString { it.path }
        synchronized(isolatedClassLoaderCache) {
            if (isolatedClassLoaderCache[key] == null) {
                isolatedClassLoaderCache[key] = URLClassLoader(
                    kspClasspath.files.map { it.toURI().toURL() }.toTypedArray(),
                    ClassLoader.getPlatformClassLoader()
                )
            }
        }
        val isolatedClassLoader = isolatedClassLoaderCache[key]!!

        // Clean stale files for now.
        // TODO: support incremental processing.
        gradleCfg.outputBaseDir.get().deleteRecursively()

        val processorClassloader = URLClassLoader(
            gradleCfg.processorClasspath.files.map { it.toURI().toURL() }.toTypedArray(),
            isolatedClassLoader
        )

        val excludedProcessors = gradleCfg.excludedProcessors.get()
        val processorProviders = ServiceLoader.load(
            processorClassloader.loadClass("com.google.devtools.ksp.processing.SymbolProcessorProvider"),
            processorClassloader
        ).filter {
            it.javaClass.name !in excludedProcessors
        }.toList()

        val kspGradleLogger = KspGradleLogger(gradleCfg.logLevel.get().ordinal)

        if (processorProviders.isEmpty()) {
            kspGradleLogger.error("No providers found in processor classpath.")
            throw Exception("KSP failed with exit code: ${KotlinSymbolProcessing.ExitCode.PROCESSING_ERROR}")
        } else {
            kspGradleLogger.info(
                "loaded provider(s): " +
                    processorProviders.joinToString(separator = ", ", prefix = "[", postfix = "]") { it.javaClass.name }
            )
        }

        fun KSPConfig.Builder.setupSuper() {
            moduleName = gradleCfg.moduleName.get()
            sourceRoots = gradleCfg.sourceRoots.files.toList()
            commonSourceRoots = gradleCfg.commonSourceRoots.files.toList()
            libraries = gradleCfg.libraries.files.toList()
            projectBaseDir = gradleCfg.projectBaseDir.get()
            outputBaseDir = gradleCfg.outputBaseDir.get()
            cachesDir = gradleCfg.cachesDir.get().asFile
            kotlinOutputDir = gradleCfg.kotlinOutputDir.get()
            classOutputDir = gradleCfg.classOutputDir.get()
            resourceOutputDir = gradleCfg.resourceOutputDir.get()

            languageVersion = gradleCfg.languageVersion.get()
            apiVersion = gradleCfg.apiVersion.get()

            // Gradle's MapProperty returns a Guava map and forces the deserializer to depend on Guava.
            // Let's convert to Kotlin's choice of map instead.
            processorOptions = gradleCfg.processorOptions.get().toMap()
            allWarningsAsErrors = gradleCfg.allWarningsAsErrors.get()

            incremental = gradleCfg.incremental.get()
            incrementalLog = gradleCfg.incrementalLog.get()

            modifiedSources = parameters.modifiedSources
            removedSources = parameters.removedSources
            changedClasses = parameters.changedClasses
        }

        val kspConfig = when (val platformType = gradleCfg.platformType.get()) {
            KotlinPlatformType.jvm, KotlinPlatformType.androidJvm -> {
                KSPJvmConfig.Builder().apply {
                    this.setupSuper()
                    javaSourceRoots = gradleCfg.javaSourceRoots.files.toList()
                    jdkHome = gradleCfg.jdkHome.get()
                    javaOutputDir = gradleCfg.javaOutputDir.get()
                    jvmTarget = gradleCfg.jvmTarget.get()
                    jvmDefaultMode = gradleCfg.jvmDefaultMode.get()
                }.build()
            }

            KotlinPlatformType.js, KotlinPlatformType.wasm -> {
                KSPJsConfig.Builder().apply {
                    this.setupSuper()
                    backend = if (platformType == KotlinPlatformType.js) "JS" else "WASM"
                }.build()
            }

            KotlinPlatformType.native -> {
                KSPNativeConfig.Builder().apply {
                    this.setupSuper()
                    target = gradleCfg.konanTargetName.get()

                    // Unlike other platforms, K/N sets up stdlib in the compiler, not KGP,
                    // meaning that KotlinNativeCompile doesn't have stdlib.
                    // FIXME: find a solution with KGP, K/N and AA owners
                    val konanHome = File(gradleCfg.konanHome.get())
                    val klib = File(konanHome, "klib")
                    val common = File(klib, "common")
                    val stdlib = File(common, "stdlib")
                    libraries += stdlib
                    val platform = File(klib, "platform")
                    val targetLibDir = File(platform, target)
                    targetLibDir.listFiles()?.let {
                        libraries += it
                    }
                }.build()
            }

            KotlinPlatformType.common -> {
                KSPCommonConfig.Builder().apply {
                    this.setupSuper()
                    // FIXME: targets
                    targets = emptyList()
                }.build()
            }
        }
        val byteArrayOutputStream = ByteArrayOutputStream()
        val objectOutputStream = ObjectOutputStream(byteArrayOutputStream)
        objectOutputStream.writeObject(kspConfig)

        val exitCode = try {
            val kspLoaderClass = isolatedClassLoader.loadClass("com.google.devtools.ksp.impl.KSPLoader")
            val runMethod = kspLoaderClass.getMethod(
                "loadAndRunKSP",
                ByteArray::class.java,
                List::class.java,
                Int::class.java
            )
            val returnCode = runMethod.invoke(
                null,
                byteArrayOutputStream.toByteArray(),
                processorProviders,
                gradleCfg.logLevel.get().ordinal
            ) as Int
            ExitCode.values()[returnCode]
        } catch (e: InvocationTargetException) {
            kspGradleLogger.exception(e.targetException)
            throw e.targetException
        } finally {
            processorClassloader.close()
        }

        if (exitCode != ExitCode.OK) {
            throw Exception("KSP failed with exit code: $exitCode")
        }
    }
}
