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
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Only the one library built there: anything else missing from Google or Central must not
        // quietly resolve from an unvetted build service instead.
        maven {
            url = java.net.URI.create("https://jitpack.io")
            content { includeGroup("com.github.ZemerTeam") }
        }
    }
}

rootProject.name = "Wanda"
include(":app")
include(":baselineprofile")
