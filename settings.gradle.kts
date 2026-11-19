pluginManagement {
    includeBuild("build-logic")

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
        maven("https://repo.itextsupport.com/android")
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "io.objectbox") {
                useModule("io.objectbox:objectbox-gradle-plugin:${requested.version}")
            }
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        mavenLocal()
    }
}

rootProject.name = "rikkahub"
include(":app")
include(":app:baselineprofile")

// 目录按职责分组，project path 保持不变，避免 CI 和 ./gradlew :ai:... 等命令失效。
val relocatedModules = mapOf(
    ":ai" to "core/ai",
    ":common" to "core/common",
    ":document" to "core/document",
    ":material3" to "core/material3",
    ":ui" to "core/ui",
    ":mediagen" to "integrations/mediagen",
    ":oauth" to "integrations/oauth",
    ":search" to "integrations/search",
    ":speech" to "integrations/speech",
    ":terminal" to "platform/terminal",
    ":web" to "platform/web",
    ":workspace" to "platform/workspace",
)

relocatedModules.forEach { (path, dir) ->
    include(path)
    project(path).projectDir = file(dir)
}
