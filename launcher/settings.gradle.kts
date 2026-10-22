pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "livingroom-home"
include(":app")
// Build-time tool that makes the home screen photo's layers (run by scripts/fetch-home-photo.sh); not part of the app.
include(":photoprep")
