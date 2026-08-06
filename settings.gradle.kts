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

rootProject.name = "ledger-engine"

include(
    ":app",
    ":core:domain",
    ":core:crypto",
    ":core:database",
    ":core:network",
    ":core:sync",
    ":core:ui",
    ":feature:ledger",
)
