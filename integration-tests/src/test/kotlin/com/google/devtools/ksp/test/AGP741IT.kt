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

package com.google.devtools.ksp.test

import org.gradle.testkit.runner.GradleRunner
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class AGP741IT(useKSP2: Boolean) {
    @Rule
    @JvmField
    val project: TemporaryTestProject = TemporaryTestProject("playground-android-multi", "playground", useKSP2)

    @Test
    fun testDependencyResolutionCheck() {
        val gradleRunner = GradleRunner.create().withProjectDir(project.root).withGradleVersion("7.6.3")

        File(project.root, "gradle.properties").appendText("\nagpVersion=7.4.1")
        gradleRunner.withArguments(":workload:compileDebugKotlin").build().let { result ->
            Assert.assertFalse(result.output.contains("was resolved during configuration time."))
        }
    }

    /**
     * Similar to ProcessorClasspathConfigurationsTest.testConfigurationsForAndroidApp(), but need to test with AGP
     * version < 8.0.0 too because we use AGP's legacy Variant API in that case.
     */
    @Test
    fun testConfigurationsForAndroidApp() {
        val gradleRunner = GradleRunner.create().withProjectDir(project.root).withGradleVersion("7.6.3")

        File(project.root, "gradle.properties").appendText("\nagpVersion=7.4.1")
        File(project.root, "workload/build.gradle.kts").appendText(
            """
                android {
                    flavorDimensions += listOf("tier", "region")

                    productFlavors {
                        create("free") {
                            dimension = "tier"
                        }
                        create("premium") {
                            dimension = "tier"
                        }
                        create("us") {
                            dimension = "region"
                        }
                        create("eu") {
                            dimension = "region"
                        }
                    }
                }
                configurations.matching { it.name.startsWith("ksp") && !it.name.endsWith("ProcessorClasspath") }.all {
                    // Make sure ksp configs are not empty.
                    project.dependencies.add(name, "androidx.room:room-compiler:2.4.2")
                }
                tasks.register("testConfigurations") {
                    // Resolve all tasks to trigger classpath config creation
                    dependsOn(tasks["tasks"])
                    doLast {
                        val freeUsDebugConfig = configurations["kspFreeUsDebugKotlinProcessorClasspath"]
                        val testFreeUsDebugConfig = configurations["kspFreeUsDebugUnitTestKotlinProcessorClasspath"]
                        val androidTestFreeUsDebugConfig =
                            configurations["kspFreeUsDebugAndroidTestKotlinProcessorClasspath"]
                        val freeUsDebugParentConfigs =
                            setOf(
                                "ksp",
                                "kspDebug",
                                "kspFree",
                                "kspUs",
                                "kspFreeUs",
                                "kspFreeUsDebug"
                            )
                        val testFreeUsDebugParentConfigs =
                            setOf(
                                "ksp",
                                "kspTest",
                                "kspTestDebug",
                                "kspTestFree",
                                "kspTestUs",
                                "kspTestFreeUs",
                                "kspTestFreeUsDebug"
                            )
                        val androidTestFreeUsDebugParentConfigs =
                            setOf(
                                "ksp",
                                "kspAndroidTest",
                                "kspAndroidTestDebug",
                                "kspAndroidTestFree",
                                "kspAndroidTestUs",
                                "kspAndroidTestFreeUs",
                                "kspAndroidTestFreeUsDebug"
                            )
                        require(freeUsDebugConfig.extendsFrom.map { it.name }.toSet() == freeUsDebugParentConfigs)
                        require(
                            testFreeUsDebugConfig.extendsFrom.map { it.name }.toSet() == testFreeUsDebugParentConfigs
                        )
                        require(
                            androidTestFreeUsDebugConfig.extendsFrom.map { it.name }.toSet() == androidTestFreeUsDebugParentConfigs
                        )
                    }
                }
            """.trimIndent()
        )

        gradleRunner.withArguments(":workload:testConfigurations").build()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "KSP2={0}")
        fun params() = listOf(arrayOf(true), arrayOf(false))
    }
}
