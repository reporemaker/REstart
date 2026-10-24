@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google()             // for Android dependencies
        gradlePluginPortal() // for plugins
        mavenCentral()       // for Kotlin stdlib
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google() // for Android dependencies
        gradlePluginPortal() // for plugins
        mavenCentral()       // for Kotlin stdlib
    }
}

include(":app")
rootProject.name = "REstart"