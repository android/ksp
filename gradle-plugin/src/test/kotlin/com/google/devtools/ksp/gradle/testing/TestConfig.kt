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
package com.google.devtools.ksp.gradle.testing

import java.io.File
import java.util.*

/**
 * Test configuration passed down from the main KSP build.
 * See the `prepareTestConfiguration` task in the build.gradle.kts file in the `gradle-plugin`.
 */
data class TestConfig(
    /**
     * The root directory of the main KSP project
     */
    val kspProjectDir: File,
    /**
     * The classpath that can be used to load processors.
     * The testing infra allows loading processors from the test classpath of the gradle-plugin.
     * This classpath is the output of the test compilation in the main KSP project.
     */
    val processorClasspath: String,
    /**
     * The local maven repository that can be used while running tests
     */
    val mavenRepoDir: File,
    /**
     * The version of KSP.
     */
    val kspVersion: String
) {
    private val kspProjectProperties by lazy {
        Properties().also { props ->
            kspProjectDir.resolve("gradle.properties").inputStream().use {
                props.load(it)
            }
        }
    }
    val kotlinBaseVersion by lazy {
        kspProjectProperties["kotlinBaseVersion"] as String
    }

    val androidBaseVersion by lazy {
        kspProjectProperties["agpBaseVersion"] as String
    }

    val mavenRepoPath = mavenRepoDir.path.replace(File.separatorChar, '/')

    companion object {
        /**
         * Loads the test configuration from resources.
         */
        fun read(): TestConfig {
            val props = Properties()
            TestConfig::class.java.classLoader.getResourceAsStream("testprops.properties").use {
                props.load(it)
            }
            return TestConfig(
                kspProjectDir = File(props.get("kspProjectRootDir") as String),
                processorClasspath = props.get("processorClasspath") as String,
                mavenRepoDir = File(props.get("mavenRepoDir") as String),
                kspVersion = props.get("kspVersion") as String
            )
        }
    }
}
