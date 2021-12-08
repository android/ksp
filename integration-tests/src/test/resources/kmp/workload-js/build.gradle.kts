plugins {
    kotlin("multiplatform")
    id("com.google.devtools.ksp")
}

version = "1.0-SNAPSHOT"

kotlin {
    js(BOTH) {
        browser()
        nodejs()
    }
    sourceSets {
        val commonMain by getting
    }
}

dependencies {
    add("kspMetadata", project(":test-processor"))
    add("kspJs", project(":test-processor"))
    add("kspJsTest", project(":test-processor"))
}
