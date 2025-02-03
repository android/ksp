buildscript {
    val testRepo: String by project

    repositories {
        maven(testRepo)
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap/")
        google()
    }
}
