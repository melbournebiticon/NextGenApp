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
        // Add JitPack for MPAndroidChart
        maven(url = "https://jitpack.io")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
<<<<<<< HEAD
        // Add JitPack for MPAndroidChart and other GitHub libraries
=======
>>>>>>> origin/pushnyodito4
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "NextGen"
include(":app")