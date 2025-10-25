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
        google()        // Repositorio de Google (Firebase, Play Services)
        mavenCentral()
    }
}

rootProject.name = "Proyecto F2"
include(":app")
