package com.google.devtools.ksp.test

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import java.io.File

class IncrementalCPIT {
    @Rule
    @JvmField
    val project: TemporaryTestProject = TemporaryTestProject("incremental-classpath")

    val src2Dirty = listOf(
        "l1/src/main/kotlin/p1/L1.kt" to setOf(
            "w: [ksp] p1/K1.kt",
        ),
        "l2/src/main/kotlin/p1/L2.kt" to setOf(
            "w: [ksp] p1/K1.kt",
            "w: [ksp] p1/K2.kt",
        ),
        "l3/src/main/kotlin/p1/L3.kt" to setOf(
            "w: [ksp] p1/K3.kt",
        ),
    )

    @Test
    fun testCPChanges() {
        val gradleRunner = GradleRunner.create().withProjectDir(project.root)

        gradleRunner.withArguments("clean", "assemble").build().let { result ->
            Assert.assertEquals(TaskOutcome.SUCCESS, result.task(":workload:assemble")?.outcome)
        }

        src2Dirty.forEach { (src, expectedDirties) ->
            File(project.root, src).appendText("\n\n")
            gradleRunner.withArguments("assemble").build().let { result ->
                // Trivial changes should not result in re-processing.
                Assert.assertEquals(TaskOutcome.UP_TO_DATE, result.task(":workload:kspKotlin")?.outcome)
            }
        }

        var i = 100
        src2Dirty.forEach { (src, expectedDirties) ->
            File(project.root, src).appendText("\n{ val v$i = ${i++} }\n")
            gradleRunner.withArguments("assemble").build().let { result ->
                Assert.assertEquals(TaskOutcome.SUCCESS, result.task(":workload:kspKotlin")?.outcome)
                val dirties = result.output.split("\n").filter { it.startsWith("w: [ksp]") }.toSet()
                Assert.assertEquals(expectedDirties, dirties)
            }
        }

        src2Dirty.forEach { (src, expectedDirties) ->
            File(project.root, src).appendText("\n\nclass C${i++}\n")
            gradleRunner.withArguments("assemble").build().let { result ->
                Assert.assertEquals(TaskOutcome.SUCCESS, result.task(":workload:kspKotlin")?.outcome)
                val dirties = result.output.split("\n").filter { it.startsWith("w: [ksp]") }.toSet()
                // Non-signature changes should not affect anything.
                Assert.assertEquals(emptySet<String>(), dirties)
            }
        }
    }

    private fun toggleFlags(vararg extras: String) {
        val gradleRunner = GradleRunner.create().withProjectDir(project.root).withDebug(true)

        gradleRunner.withArguments(
            *extras,
            "--rerun-tasks",
            "-Pksp.incremental=true",
            "-Pksp.incremental.intermodule=true",
            "assemble"
        ).build().let { result ->
            Assert.assertEquals(TaskOutcome.SUCCESS, result.task(":workload:kspKotlin")?.outcome)
        }

        gradleRunner.withArguments(
            *extras,
            "--rerun-tasks",
            "-Pksp.incremental=false",
            "-Pksp.incremental.intermodule=true",
            "assemble"
        ).build().let { result ->
            Assert.assertEquals(TaskOutcome.SUCCESS, result.task(":workload:kspKotlin")?.outcome)
        }

        gradleRunner.withArguments(
            *extras,
            "--rerun-tasks",
            "-Pksp.incremental=true",
            "-Pksp.incremental.intermodule=false",
            "assemble"
        ).build().let { result ->
            Assert.assertEquals(TaskOutcome.SUCCESS, result.task(":workload:kspKotlin")?.outcome)
        }

        gradleRunner.withArguments(
            *extras,
            "--rerun-tasks",
            "-Pksp.incremental=false",
            "-Pksp.incremental.intermodule=false",
            "assemble"
        ).build().let { result ->
            Assert.assertEquals(TaskOutcome.SUCCESS, result.task(":workload:kspKotlin")?.outcome)
        }
    }

    @Test
    fun toggleIncrementalFlagsWithoutConfigurationCache() {
        toggleFlags("--no-configuration-cache")
    }

    @Test
    fun toggleIncrementalFlagsWithConfigurationCache() {
        toggleFlags("--configuration-cache")
    }
}
