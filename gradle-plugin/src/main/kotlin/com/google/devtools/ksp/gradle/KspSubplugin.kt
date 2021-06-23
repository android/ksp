/*
 * Copyright 2020 Google LLC
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

@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.google.devtools.ksp.gradle

import com.google.devtools.ksp.gradle.model.builder.KspModelBuilder
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.jetbrains.kotlin.cli.common.arguments.Argument
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptionsImpl
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension
import org.jetbrains.kotlin.gradle.internal.CompilerArgumentsContributor
import org.jetbrains.kotlin.gradle.internal.compilerArgumentsConfigurationFlags
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmAndroidCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinWithJavaCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.KotlinCompilationData
import org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.SourceRoots
import org.jetbrains.kotlin.incremental.ChangedFiles
import org.jetbrains.kotlin.incremental.destinationAsFile
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import java.io.File
import java.util.*
import java.util.concurrent.Callable
import javax.inject.Inject
import kotlin.collections.LinkedHashSet
import kotlin.reflect.KProperty1

class KspGradleSubplugin @Inject internal constructor(private val registry: ToolingModelBuilderRegistry) :
        KotlinCompilerPluginSupportPlugin {
    companion object {
        const val KSP_MAIN_CONFIGURATION_NAME = "ksp"
        const val KSP_ARTIFACT_NAME = "symbol-processing"
        const val KSP_PLUGIN_ID = "com.google.devtools.ksp.symbol-processing"

        @JvmStatic
        fun getKspOutputDir(project: Project, sourceSetName: String) =
                File(project.project.buildDir, "generated/ksp/$sourceSetName")

        @JvmStatic
        fun getKspClassOutputDir(project: Project, sourceSetName: String) =
                File(getKspOutputDir(project, sourceSetName), "classes")

        @JvmStatic
        fun getKspJavaOutputDir(project: Project, sourceSetName: String) =
                File(getKspOutputDir(project, sourceSetName), "java")

        @JvmStatic
        fun getKspKotlinOutputDir(project: Project, sourceSetName: String) =
                File(getKspOutputDir(project, sourceSetName), "kotlin")

        @JvmStatic
        fun getKspResourceOutputDir(project: Project, sourceSetName: String) =
                File(getKspOutputDir(project, sourceSetName), "resources")

        @JvmStatic
        fun getKspCachesDir(project: Project, sourceSetName: String) =
                File(project.project.buildDir, "kspCaches/$sourceSetName")

        @JvmStatic
        private fun getSubpluginOptions(
            project: Project,
            kspExtension: KspExtension,
            nonEmptyKspConfigurations: List<Configuration>,
            sourceSetName: String
        ): List<SubpluginOption> {
            val options = mutableListOf<SubpluginOption>()
            options += SubpluginOption("classOutputDir", getKspClassOutputDir(project, sourceSetName).path)
            options += SubpluginOption("javaOutputDir", getKspJavaOutputDir(project, sourceSetName).path)
            options += SubpluginOption("kotlinOutputDir", getKspKotlinOutputDir(project, sourceSetName).path)
            options += SubpluginOption("resourceOutputDir", getKspResourceOutputDir(project, sourceSetName).path)
            options += SubpluginOption("cachesDir", getKspCachesDir(project, sourceSetName).path)
            options += SubpluginOption("kspOutputDir", getKspOutputDir(project, sourceSetName).path)
            options += SubpluginOption("incremental", project.findProperty("ksp.incremental")?.toString() ?: "true")
            options += SubpluginOption("incrementalLog", project.findProperty("ksp.incremental.log")?.toString() ?: "false")
            options += SubpluginOption("projectBaseDir", project.project.projectDir.canonicalPath)
            options += FilesSubpluginOption("apclasspath", nonEmptyKspConfigurations.flatten())

            kspExtension.apOptions.forEach {
                options += SubpluginOption("apoption", "${it.key}=${it.value}")
            }
            return options
        }
    }

    private val androidIntegration by lazy {
        AndroidPluginIntegration(this)
    }

    @OptIn(ExperimentalStdlibApi::class)
    private val KotlinSourceSet.kspConfigurationName: String
        get() {
            return if (name == SourceSet.MAIN_SOURCE_SET_NAME) {
                KSP_MAIN_CONFIGURATION_NAME
            } else {
                "$KSP_MAIN_CONFIGURATION_NAME${name.capitalize(Locale.US)}"
            }
        }

    private fun KotlinSourceSet.kspConfiguration(project: Project): Configuration? {
        return project.configurations.findByName(kspConfigurationName)
    }

    override fun apply(project: Project) {
        project.extensions.create("ksp", KspExtension::class.java)
        // Always include the main `ksp` configuration.
        // TODO: multiplatform
        project.configurations.create(KSP_MAIN_CONFIGURATION_NAME)
        project.plugins.withType(KotlinPluginWrapper::class.java) {
            // kotlin extension has the compilation target that we need to look for to create configurations
            decorateKotlinExtension(project)
        }
        androidIntegration.applyIfAndroidProject(project)
        registry.register(KspModelBuilder())
    }

    private fun decorateKotlinExtension(project:Project) {
        project.extensions.configure(KotlinSingleTargetExtension::class.java) { kotlinExtension ->
            kotlinExtension.target.compilations.createKspConfigurations(project) { kotlinCompilation ->
                kotlinCompilation.kotlinSourceSets.map {
                    it.kspConfigurationName
                }
            }
        }
    }

    /**
     * Creates a KSP configuration for each element in the object container.
     */
    internal fun<T> NamedDomainObjectContainer<T>.createKspConfigurations(
        project: Project,
        getKspConfigurationNames : (T)-> List<String>
    ) {
        val mainConfiguration = project.configurations.maybeCreate(KSP_MAIN_CONFIGURATION_NAME)
        all {
            getKspConfigurationNames(it).forEach { kspConfigurationName ->
                if (kspConfigurationName != KSP_MAIN_CONFIGURATION_NAME) {
                    val existing = project.configurations.findByName(kspConfigurationName)
                    if (existing == null) {
                        project.configurations.create(kspConfigurationName) {
                            it.extendsFrom(mainConfiguration)
                        }
                    }
                }
            }
        }
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        val project = kotlinCompilation.target.project
        val kspVersion = javaClass.`package`.implementationVersion
        val kotlinVersion = project.getKotlinPluginVersion() ?: "N/A"

        // Check version and show warning by default.
        val noVersionCheck = project.findProperty("ksp.version.check")?.toString()?.toBoolean() == false
        if (!noVersionCheck && !kspVersion.startsWith(kotlinVersion))
            project.logger.warn("ksp-$kspVersion might not work with kotlin-$kotlinVersion properly. Please pick the same version of ksp and kotlin plugins.")

        return true
    }

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val kotlinCompileProvider: TaskProvider<AbstractKotlinCompile<CommonCompilerArguments>> =
            project.locateTask(kotlinCompilation.compileKotlinTaskName) ?: return project.provider { emptyList() }
        val javaCompile = findJavaTaskForKotlinCompilation(kotlinCompilation)?.get()
        val kspExtension = project.extensions.getByType(KspExtension::class.java)
        val kspConfigurations = LinkedHashSet<Configuration>()
        kotlinCompilation.allKotlinSourceSets.forEach {
            it.kspConfiguration(project)?.let {
                kspConfigurations.add(it)
            }
        }
        // Always include the main `ksp` configuration.
        // TODO: multiplatform
        project.configurations.findByName(KSP_MAIN_CONFIGURATION_NAME)?.let {
            kspConfigurations.add(it)
        }
        val nonEmptyKspConfigurations = kspConfigurations.filter { it.dependencies.isNotEmpty() }
        if (nonEmptyKspConfigurations.isEmpty()) {
            return project.provider { emptyList() }
        }

        val sourceSetName = kotlinCompilation.compilationName
        val classOutputDir = getKspClassOutputDir(project, sourceSetName)
        val javaOutputDir = getKspJavaOutputDir(project, sourceSetName)
        val kotlinOutputDir = getKspKotlinOutputDir(project, sourceSetName)
        val resourceOutputDir = getKspResourceOutputDir(project, sourceSetName)
        val kspOutputDir = getKspOutputDir(project, sourceSetName)

        if (javaCompile != null) {
            val generatedJavaSources = javaCompile.project.fileTree(javaOutputDir)
            generatedJavaSources.include("**/*.java")
            javaCompile.source(generatedJavaSources)
            javaCompile.classpath += project.files(classOutputDir)
        }

        assert(kotlinCompileProvider.name.startsWith("compile"))
        val kspTaskName = kotlinCompileProvider.name.replaceFirst("compile", "ksp")

        val kotlinCompileTask = kotlinCompileProvider.get()
        val kspTaskClass = when (kotlinCompileTask) {
            is KotlinCompile -> KspTaskJvm::class.java
            else -> return project.provider { emptyList() }
        }

        val kspTaskProvider = project.tasks.register(kspTaskName, kspTaskClass) { kspTask ->
            kspTask.configure(kotlinCompilation as KotlinCompilationData<*>, kotlinCompileTask)
            // TODO: Move into Configurator.
            kspTask.destinationDir = kspOutputDir
            kspTask.options = getSubpluginOptions(project, kspExtension, nonEmptyKspConfigurations, sourceSetName)
            kspTask.classpath = kotlinCompileTask.project.files(Callable { kotlinCompileTask.classpath })
            kspTask.destination = kspOutputDir
            kspTask.outputs.dirs(kotlinOutputDir, javaOutputDir, classOutputDir, resourceOutputDir)
            kspTask.blockOtherCompilerPlugins = kspExtension.blockOtherCompilerPlugins
            kspTask.pluginConfigurationName = kotlinCompilation.pluginConfigurationName

            // depends on the processor; if the processor changes, it needs to be reprocessed.
            val processorClasspath = project.configurations.maybeCreate("${kspTaskName}ProcessorClasspath")
                .extendsFrom(*nonEmptyKspConfigurations.toTypedArray())
            kspTask.processorClasspath.from(processorClasspath)

            nonEmptyKspConfigurations.forEach {
                kspTask.dependsOn(it.buildDependencies)
            }
        }.apply {
            configure {
                kotlinCompilation.allKotlinSourceSets.forEach { sourceSet -> it.source(sourceSet.kotlin) }
                kotlinCompilation.output.classesDirs.from(classOutputDir)
            }
        }

        kotlinCompileProvider.configure { kotlinCompile ->
            kotlinCompile.dependsOn(kspTaskProvider)
            kotlinCompile.source(kotlinOutputDir, javaOutputDir)
            kotlinCompile.classpath += project.files(classOutputDir)
        }

        val processResourcesTaskName = (kotlinCompilation as? KotlinCompilationWithResources)?.processResourcesTaskName ?: "processResources"
        project.locateTask<ProcessResources>(processResourcesTaskName)?.let { provider ->
            provider.configure { resourcesTask ->
                resourcesTask.dependsOn(kspTaskProvider)
                resourcesTask.from(resourceOutputDir)
            }
        }
        if (kotlinCompilation is KotlinJvmAndroidCompilation) {
            androidIntegration.registerGeneratedJavaSources(
                project = project,
                kotlinCompilation = kotlinCompilation,
                kspTaskProvider = kspTaskProvider as TaskProvider<KspTaskJvm>,
                javaOutputDir = javaOutputDir,
                classOutputDir = classOutputDir,
                resourcesOutputDir = project.files(resourceOutputDir)
            )
        }

        return project.provider { emptyList() }
    }

    override fun getCompilerPluginId() = KSP_PLUGIN_ID
    override fun getPluginArtifact(): SubpluginArtifact =
            SubpluginArtifact(groupId = "com.google.devtools.ksp", artifactId = KSP_ARTIFACT_NAME, version = javaClass.`package`.implementationVersion)
}

// Copied from kotlin-gradle-plugin, because they are internal.
internal inline fun <reified T : Task> Project.locateTask(name: String): TaskProvider<T>? =
        try {
            tasks.withType(T::class.java).named(name)
        } catch (e: UnknownTaskException) {
            null
        }

// Copied from kotlin-gradle-plugin, because they are internal.
internal fun findJavaTaskForKotlinCompilation(compilation: KotlinCompilation<*>): TaskProvider<out JavaCompile>? =
        when (compilation) {
          is KotlinJvmAndroidCompilation -> compilation.compileJavaTaskProvider
          is KotlinWithJavaCompilation -> compilation.compileJavaTaskProvider
          is KotlinJvmCompilation -> compilation.compileJavaTaskProvider // may be null for Kotlin-only JVM target in MPP
            else -> null
        }

interface KspTask : Task {
    @get:Internal
    var options: List<SubpluginOption>

    @get:Internal
    var destination: File

    @get:Internal
    var pluginConfigurationName: String

    @get:Input
    var blockOtherCompilerPlugins: Boolean

    @Input
    open fun getApOptions(): Map<String, String> {
        return project.extensions.getByType(KspExtension::class.java).apOptions
    }

    @get:InputFiles
    abstract val processorClasspath: ConfigurableFileCollection

    @Internal
    fun configure(kotlinCompilation: KotlinCompilationData<*>, kotlinCompile: AbstractKotlinCompile<*>)
}

abstract class KspTaskJvm : KotlinCompile(KotlinJvmOptionsImpl()), KspTask {
    @Internal
    override fun configure(kotlinCompilation: KotlinCompilationData<*>, kotlinCompile: AbstractKotlinCompile<*>) {
        AbstractKotlinCompile.Configurator<KspTaskJvm>(kotlinCompilation).configure(this)
        kotlinCompile as KotlinCompile
        val providerFactory = kotlinCompile.project.providers
        compileKotlinArgumentsContributor.set(
            providerFactory.provider {
                kotlinCompile.compilerArgumentsContributor
            }
        )
    }

    @get:Internal
    internal abstract val compileKotlinArgumentsContributor: Property<CompilerArgumentsContributor<K2JVMCompilerArguments>>

    init {
        // kotlinc's incremental compilation isn't compatible with symbol processing in a few ways:
        // * It doesn't consider private / internal changes when computing dirty sets.
        // * It compiles iteratively; Sources can be compiled in different rounds.
        incremental = false
    }

    override fun setupCompilerArgs(
            args: K2JVMCompilerArguments,
            defaultsOnly: Boolean,
            ignoreClasspathResolutionErrors: Boolean
    ) {
        // Start with / copy from kotlinCompile.
        compileKotlinArgumentsContributor.get().contributeArguments(args, compilerArgumentsConfigurationFlags(
            defaultsOnly,
            ignoreClasspathResolutionErrors
        ))
        if (blockOtherCompilerPlugins) {
            args.blockOtherPlugins(project, pluginConfigurationName)
        }
        args.addPluginOptions(options)
        args.destinationAsFile = destination
    }

    // Overrding an internal function is hacky.
    // TODO: Ask upstream to open it.
    @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER", "EXPOSED_PARAMETER_TYPE")
    fun `callCompilerAsync$kotlin_gradle_plugin`(
        args: K2JVMCompilerArguments,
        sourceRoots: SourceRoots,
        changedFiles: ChangedFiles
    ) {
        args.addChangedFiles(changedFiles)
        super.callCompilerAsync(args, sourceRoots, changedFiles)
    }
}

fun CommonCompilerArguments.addPluginOptions(options: List<SubpluginOption>) {
    fun SubpluginOption.toArg() = "plugin:${KspGradleSubplugin.KSP_PLUGIN_ID}:${key}=${value}"
    pluginOptions = (options.map { it.toArg() } + pluginOptions!!).toTypedArray()
}

fun CommonCompilerArguments.addChangedFiles(changedFiles: ChangedFiles) {
    if (changedFiles is ChangedFiles.Known) {
        val options = mutableListOf<SubpluginOption>()
        changedFiles.modified.ifNotEmpty { options += SubpluginOption("knownModified", map { it.path }.joinToString(File.pathSeparator)) }
        changedFiles.removed.ifNotEmpty { options += SubpluginOption("knownRemoved", map { it.path }.joinToString(File.pathSeparator)) }
        options.ifNotEmpty { addPluginOptions(this) }
    }
}

private fun CommonCompilerArguments.blockOtherPlugins(project: Project, pluginConfigurationName: String) {
    // FIXME: ask upstream to provide an API to make this not implementation-dependent.
    val cfg = project.configurations.getByName(pluginConfigurationName)
    val dep = cfg.dependencies.single { it.name == KspGradleSubplugin.KSP_ARTIFACT_NAME }
    pluginClasspaths = cfg.files(dep).map { it.canonicalPath }.toTypedArray()
    pluginOptions = arrayOf()

}

// TODO: Move into dumpArgs after the compiler supports local function in inline functions.
private inline fun <reified T: CommonCompilerArguments> T.toPair(property: KProperty1<T, *>): Pair<String, String> {
    @Suppress("UNCHECKED_CAST")
    val value = (property as KProperty1<T, *>).get(this)
    return property.name to if (value is Array<*>)
        value.asList().toString()
    else
        value.toString()
}

@Suppress("unused")
internal inline fun <reified T: CommonCompilerArguments> dumpArgs(args: T): Map<String, String> {
    @Suppress("UNCHECKED_CAST")
    val argumentProperties =
        args::class.members.mapNotNull { member ->
            (member as? KProperty1<T, *>)?.takeIf { it.annotations.any { ann -> ann is Argument } }
        }

    return argumentProperties.associate(args::toPair).toSortedMap()
}
