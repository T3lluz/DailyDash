pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Termux's terminal emulator and its renderer (Apache 2.0), for the console
        maven("https://jitpack.io") { content { includeGroup("com.termux.termux-app") } }
    }
}

rootProject.name = "DailyDash"
include(":app")
