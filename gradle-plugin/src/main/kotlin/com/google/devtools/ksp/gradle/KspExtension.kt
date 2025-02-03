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

package com.google.devtools.ksp.gradle

import com.google.devtools.ksp.KspExperimental
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.process.CommandLineArgumentProvider
import javax.inject.Inject

abstract class KspExtension @Inject constructor(project: Project) {
    /**
     * Enables or disables KSP 2, defaults to the `ksp.useKsp2` gradle property or `false` if that's not set.
     *
     * This API is temporary and will be removed once KSP1 is removed.
     */
    @KspExperimental
    abstract val useKsp2: Property<Boolean>

    internal val apOptions = project.objects.mapProperty(String::class.java, String::class.java)
    internal val commandLineArgumentProviders = project.objects.listProperty(CommandLineArgumentProvider::class.java)
        .also { it.finalizeValueOnRead() }
    internal val excludedProcessors = project.objects.setProperty(String::class.java)
        .also { it.finalizeValueOnRead() }

    // Specify sources that should be excluded from KSP.
    // If you have a task that generates sources, you can call `ksp.excludedSources.from(task)`.
    // TODO - Deprecate and then remove this DSL?
    abstract val excludedSources: ConfigurableFileCollection

    open val arguments: Map<String, String> get() = apOptions.get()

    open fun arg(k: String, v: String) {
        if ('=' in k) {
            throw GradleException("'=' is not allowed in custom option's name.")
        }
        apOptions.put(k, v)
    }

    open fun arg(k: String, v: Provider<String>) {
        if ('=' in k) {
            throw GradleException("'=' is not allowed in custom option's name.")
        }
        apOptions.put(k, v)
    }

    open fun arg(arg: CommandLineArgumentProvider) {
        commandLineArgumentProviders.add(arg)
    }

    @Deprecated("KSP will stop supporting other compiler plugins in KSP's Gradle tasks after 1.0.8.")
    open var blockOtherCompilerPlugins: Boolean = true

    // Instruct KSP to pickup sources from compile tasks, instead of source sets.
    // Note that it depends on behaviors of other Gradle plugins, that may bring surprises and can be hard to debug.
    // Use your discretion.
    @Deprecated("This feature is broken in recent versions of Gradle and is no longer supported in KSP2.")
    open var allowSourcesFromOtherPlugins: Boolean = false

    // Treat all warning as errors.
    open var allWarningsAsErrors: Boolean = false

    // Keep processor providers from being called. Providers will still be loaded if they're in classpath.
    open fun excludeProcessor(fullyQualifiedName: String) {
        excludedProcessors.add(fullyQualifiedName)
    }
}
