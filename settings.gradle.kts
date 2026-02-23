pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net")
    }

    val loom_version: String by settings
    val kotlin_version: String by settings

    plugins {
        id("fabric-loom") version loom_version
        kotlin("jvm") version kotlin_version
    }
}

rootProject.name = "quoi"