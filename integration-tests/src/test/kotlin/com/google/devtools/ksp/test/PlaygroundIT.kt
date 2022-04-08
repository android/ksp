package com.google.devtools.ksp.test

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.jar.*

class PlaygroundIT {
    @Rule
    @JvmField
    val project: TemporaryTestProject = TemporaryTestProject("playground")

    private fun GradleRunner.buildAndCheck(vararg args: String, extraCheck: (BuildResult) -> Unit = {}) {
        val result = this.withArguments(*args).build()

        Assert.assertEquals(TaskOutcome.SUCCESS, result.task(":workload:build")?.outcome)

        val artifact = File(project.root, "workload/build/libs/workload-1.0-SNAPSHOT.jar")
        Assert.assertTrue(artifact.exists())

        JarFile(artifact).use { jarFile ->
            Assert.assertTrue(jarFile.getEntry("TestProcessor.log").size > 0)
            Assert.assertTrue(jarFile.getEntry("hello/HELLO.class").size > 0)
            Assert.assertTrue(jarFile.getEntry("g/G.class").size > 0)
            Assert.assertTrue(jarFile.getEntry("com/example/AClassBuilder.class").size > 0)
        }

        extraCheck(result)
    }

    @Test
    fun testPlayground() {
        val gradleRunner = GradleRunner.create().withProjectDir(project.root)
        gradleRunner.buildAndCheck("clean", "build")
        gradleRunner.buildAndCheck("clean", "build")
    }

    // TODO: add another plugin and see if it is blocked.
    // Or use a project that depends on a builtin plugin like all-open and see if the build fails
    @Test
    fun testBlockOtherCompilerPlugins() {
        val gradleRunner = GradleRunner.create().withProjectDir(project.root)

        File(project.root, "workload/build.gradle.kts")
            .appendText("\nksp {\n  blockOtherCompilerPlugins = true\n}\n")
        gradleRunner.buildAndCheck("clean", "build")
        gradleRunner.buildAndCheck("clean", "build")
        project.restore("workload/build.gradle.kts")
    }

    @Test
    fun testAllowSourcesFromOtherPlugins() {
        fun checkGBuilder() {
            val artifact = File(project.root, "workload/build/libs/workload-1.0-SNAPSHOT.jar")

            JarFile(artifact).use { jarFile ->
                Assert.assertTrue(jarFile.getEntry("g/GBuilder.class").size > 0)
            }
        }

        val gradleRunner = GradleRunner.create().withProjectDir(project.root)

        File(project.root, "workload/build.gradle.kts")
            .appendText("\nksp {\n  allowSourcesFromOtherPlugins = true\n}\n")
        gradleRunner.buildAndCheck("clean", "build") { checkGBuilder() }
        gradleRunner.buildAndCheck("clean", "build") { checkGBuilder() }
        project.restore("workload/build.gradle.kts")
    }

    /** Regression test for https://github.com/google/ksp/issues/518. */
    @Test
    fun testBuildWithConfigureOnDemand() {
        val gradleRunner = GradleRunner.create().withProjectDir(project.root)
        gradleRunner.buildAndCheck("--configure-on-demand", ":workload:build")
    }

    @Test
    fun testBuildCache() {
        val gradleRunner = GradleRunner.create().withProjectDir(project.root)
        gradleRunner.buildAndCheck("--build-cache", ":workload:clean", "build") {
            Assert.assertEquals(TaskOutcome.SUCCESS, it.task(":workload:kspKotlin")?.outcome)
        }
        gradleRunner.buildAndCheck("--build-cache", ":workload:clean", "build") {
            Assert.assertEquals(TaskOutcome.FROM_CACHE, it.task(":workload:kspKotlin")?.outcome)
        }
    }

    @Test
    fun testAllWarningsAsErrors() {
        File(project.root, "workload/build.gradle.kts")
            .appendText("\nksp {\n  allWarningsAsErrors = true\n}\n")
        val gradleRunner = GradleRunner.create().withProjectDir(project.root)
        gradleRunner.withArguments("build").buildAndFail().let { result ->
            Assert.assertTrue(result.output.contains("This is a harmless warning."))
        }
    }

    // Compiler's test infra report this kind of error before KSP, so it is not testable there.
    @Test
    fun testNoFunctionName() {
        val gradleRunner = GradleRunner.create().withProjectDir(project.root)

        fun buildAndFileAndCheck() {
            gradleRunner.withArguments("build").buildAndFail().let { result ->
                Assert.assertTrue(result.output.contains("Function declaration must have a name"))
            }
        }

        File(project.root, "workload/src/main/java/com/example/A.kt").appendText("\n{}\n")
        buildAndFileAndCheck()
        project.restore("workload/src/main/java/com/example/A.kt")

        File(project.root, "workload/src/main/java/com/example/A.kt").appendText("\nfun() = {0}\n")
        buildAndFileAndCheck()
        project.restore("workload/src/main/java/com/example/A.kt")
    }

    @Test
    fun testRewriteFile() {
        File(
            project.root,
            "test-processor/src/main/resources/META-INF/services/" +
                "com.google.devtools.ksp.processing.SymbolProcessorProvider"
        ).writeText("RewriteProcessorProvider")
        val gradleRunner = GradleRunner.create().withProjectDir(project.root)
        gradleRunner.withArguments("build").buildAndFail().let { result ->
            Assert.assertTrue(result.output.contains("kotlin.io.FileAlreadyExistsException"))
        }
    }

    // Test -Xuse-fir for compilation; KSP still uses FE1.0
    @Test
    fun testFirPreview() {
        val gradleProperties = File(project.root, "gradle.properties")
        gradleProperties.appendText("\nkotlin.useFir=true")
        val gradleRunner = GradleRunner.create().withProjectDir(project.root)
        gradleRunner.buildAndCheck("clean", "build") { result ->
            Assert.assertTrue(result.output.contains("This build uses in-dev FIR"))
            Assert.assertTrue(result.output.contains("-Xuse-fir"))
        }
        project.restore(gradleProperties.path)
    }
}
