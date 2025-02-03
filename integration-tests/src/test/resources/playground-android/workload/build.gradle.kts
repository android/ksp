val testRepo: String by project

plugins {
    // DO NOT CHANGE THE ORDER.
    id("com.google.devtools.ksp")
    id("com.android.application")
    kotlin("android")
}

version = "1.0-SNAPSHOT"

repositories {
    maven(testRepo)
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap/")
    google()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":test-processor"))
    ksp(project(":test-processor"))
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 34
    defaultConfig {
        applicationId = "org.gradle.kotlin.dsl.samples.androidstudio"
        minSdk = 34
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        getByName("release") {
            // For regression testing https://github.com/google/ksp/pull/467
            proguardFiles.add(file("proguard-rules.pro"))
            isMinifyEnabled = true
        }
    }
}

ksp {
    arg("option1", "value1")
    arg("option2", "value2")
}
