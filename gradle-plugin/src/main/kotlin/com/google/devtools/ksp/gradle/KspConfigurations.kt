package com.google.devtools.ksp.gradle

import com.google.devtools.ksp.gradle.AndroidPluginIntegration.setUpKspClasspathsForAndroidVariants
import com.google.devtools.ksp.gradle.AndroidPluginIntegration.useLegacyVariantApi
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinCommonCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmAndroidCompilation

/**
 * Creates and retrieves ksp-related configurations.
 */
class KspConfigurations(private val project: Project) {
    companion object {
        private const val PREFIX = "ksp"

        /**
         * Returns the Android sourceSet-specific KSP configuration name given a [kotlinTarget] and [sourceSet].
         *
         * For single-platform, [kotlinTarget] can be null.
         */
        fun getAndroidConfigurationName(kotlinTarget: KotlinTarget?, sourceSet: String): String {
            val isMain = sourceSet.endsWith("main", ignoreCase = true)
            val nameWithoutMain = when {
                isMain -> sourceSet.substring(0, sourceSet.length - 4)
                else -> sourceSet
            }
            // Note: on single-platform, target name is conveniently set to "".
            return configurationNameOf(PREFIX, kotlinTarget?.name ?: "", nameWithoutMain)
        }

        private fun configurationNameOf(vararg parts: String): String {
            return parts.joinToString("") { part ->
                part.replaceFirstChar { it.uppercase() }
            }.replaceFirstChar { it.lowercase() }
        }
    }

    private val allowAllTargetConfiguration =
        project.providers.gradleProperty("ksp.allow.all.target.configuration")
            .orNull
            ?.toBoolean()
            ?: true

    // The "ksp" configuration, applied to every compilation.
    private val configurationForAll = project.configurations.create(PREFIX).apply {
        isCanBeConsumed = false
        isCanBeResolved = false
        isVisible = false
    }


    /**
     * Returns a new or existing [Configuration] with the given [name], with applied properties.
     */
    private fun createConfiguration(
        name: String,
        readableSetName: String,
    ): Configuration {
        return project.configurations.maybeCreate(name).apply {
            description = "KSP dependencies for the '$readableSetName' source set."
            isCanBeResolved = false // we'll resolve the processor classpath config
            isCanBeConsumed = false
            isVisible = false
        }
    }

    private fun getKotlinConfigurationName(compilation: KotlinCompilation<*>, sourceSet: KotlinSourceSet): String {
        val isMain = compilation.name == KotlinCompilation.MAIN_COMPILATION_NAME
        val isDefault = sourceSet.name == compilation.defaultSourceSet.name && compilation !is KotlinCommonCompilation
        // Note: on single-platform, target name is conveniently set to "".
        val name = if (isMain && isDefault) {
            // For js(IR), js(LEGACY), the target "js" is created.
            //
            // When js(BOTH) is used, target "jsLegacy" and "jsIr" are created.
            // Both targets share the same source set. Therefore, configurations other than main compilation
            // are shared. E.g., "kspJsTest".
            // For simplicity and consistency, let's not distinguish them.
            when (val targetName = compilation.target.name) {
                "jsLegacy", "jsIr" -> "js"
                else -> targetName
            }
        } else if (compilation is KotlinCommonCompilation) {
            sourceSet.name + compilation.target.name.replaceFirstChar(Char::uppercaseChar)
        } else {
            sourceSet.name
        }
        return configurationNameOf(PREFIX, name)
    }

    init {
        project.plugins.withType(KotlinBasePluginWrapper::class.java).configureEach {
            // 1.6.0: decorateKotlinProject(project.kotlinExtension)?
            decorateKotlinProject(project.extensions.getByName("kotlin") as KotlinProjectExtension, project)
        }
        // Create sourceSet-specific KSP configurations for the case when the KotlinBaseApiPlugin is applied instead
        // of the KotlinBasePluginWrapper (e.g., when AGP's built-in Kotlin support is enabled).
        project.plugins.withType(KotlinBaseApiPlugin::class.java) {
            // FIXME: After KT-70897 is fixed and AGP's built-in Kotlin support adds a `kotlin` extension, call
            //  decorateKotlinProject here instead.
            createAndroidSourceSetConfigurations(project, kotlinTarget = null)
        }
        if (!useLegacyVariantApi) {
            setUpKspClasspathsForAndroidVariants(project)
        }
    }

    private fun decorateKotlinProject(kotlin: KotlinProjectExtension, project: Project) {
        when (kotlin) {
            is KotlinSingleTargetExtension<*> -> decorateKotlinTarget(kotlin.target)
            is KotlinMultiplatformExtension -> {
                kotlin.targets.configureEach(::decorateKotlinTarget)

                var reported = false
                configurationForAll.dependencies.whenObjectAdded {
                    if (!reported) {
                        reported = true
                        val msg = "The 'ksp' configuration is deprecated in Kotlin Multiplatform projects. " +
                            "Please use target-specific configurations like 'kspJvm' instead."

                        if (allowAllTargetConfiguration) {
                            project.logger.warn(msg)
                        } else {
                            throw InvalidUserCodeException(msg)
                        }
                    }
                }
            }
        }
    }

    /**
     * Decorate the [KotlinSourceSet]s belonging to [target] to create one KSP configuration per source set,
     * named ksp<SourceSet>. The only exception is the main source set, for which we avoid using the
     * "main" suffix (so what would be "kspJvmMain" becomes "kspJvm").
     *
     * For Android, we prefer to use AndroidSourceSets from AGP rather than [KotlinSourceSet]s.
     * Even though the Kotlin Plugin does create [KotlinSourceSet]s out of AndroidSourceSets
     * ( https://kotlinlang.org/docs/mpp-configure-compilations.html#compilation-of-the-source-set-hierarchy ),
     * there are slight differences between the two - Kotlin creates some extra sets with unexpected word ordering,
     * and things get worse when you add product flavors. So, we use AGP sets as the source of truth.
     */
    private fun decorateKotlinTarget(target: KotlinTarget) {
        if (target.platformType == KotlinPlatformType.androidJvm) {
            createAndroidSourceSetConfigurations(target.project, target)
        } else {
            target.compilations.configureEach { compilation ->
                compilation.kotlinSourceSetsObservable.forAll { sourceSet ->
                    createConfiguration(
                        name = getKotlinConfigurationName(compilation, sourceSet),
                        readableSetName = sourceSet.name
                    )
                }
            }
        }
    }

    /**
     * Returns the user-facing configurations involved in the given compilation.
     * We use [KotlinCompilation.kotlinSourceSets], not [KotlinCompilation.allKotlinSourceSets] for a few reasons:
     * 1) consistency with how we created the configurations. For example, all* can return user-defined sets
     *    that don't belong to any compilation, like user-defined intermediate source sets (e.g. iosMain).
     *    These do not currently have their own ksp configuration.
     * 2) all* can return sets belonging to other [KotlinCompilation]s
     *
     * See test: SourceSetConfigurationsTest.configurationsForMultiplatformApp_doesNotCrossCompilationBoundaries
     */
    fun find(compilation: KotlinCompilation<*>): Set<Configuration> {
        val results = mutableListOf<String>()
        if (compilation is KotlinCommonCompilation) {
            results.add(getKotlinConfigurationName(compilation, compilation.defaultSourceSet))
        }
        compilation.kotlinSourceSets.mapTo(results) {
            getKotlinConfigurationName(compilation, it)
        }
        if (compilation.platformType == KotlinPlatformType.androidJvm && useLegacyVariantApi) {
            compilation as KotlinJvmAndroidCompilation
            AndroidPluginIntegration.getCompilationSourceSets(compilation).mapTo(results) {
                getAndroidConfigurationName(compilation.target, it)
            }
        }

        // Include the `ksp` configuration, if it exists, for all compilations.
        if (allowAllTargetConfiguration) {
            results.add(configurationForAll.name)
        }

        return results.mapNotNull {
            compilation.target.project.configurations.findByName(it)
        }.toSet()
    }

    /**
     * Creates the Android sourceSet-specific KSP configurations for the given [project] and [kotlinTarget]
     *
     * For single-platform, [kotlinTarget] can be null.
     */
    private fun createAndroidSourceSetConfigurations(project: Project, kotlinTarget: KotlinTarget?) {
        AndroidPluginIntegration.forEachAndroidSourceSet(project) { sourceSet ->
            createConfiguration(
                name = getAndroidConfigurationName(kotlinTarget, sourceSet),
                readableSetName = "$sourceSet (Android)"
            )
        }
    }
}
