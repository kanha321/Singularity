enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "singularity"
include(":app")

includeBuild("/mnt/c/Users/CelestialSepian/AndroidStudioProjects/core") {
    name = "shared-core"
    dependencySubstitution {
        substitute(module("com.github.kanha321:core-framework")).using(project(":core"))
        substitute(module("com.github.kanha321:feature-settings")).using(project(":feature:settings"))
    }
}
 