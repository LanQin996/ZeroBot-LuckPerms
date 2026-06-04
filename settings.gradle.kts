pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        maven {
            url = uri("https://nexus.jsdu.cn/repository")
        }
        mavenCentral()
    }
}

rootProject.name = "zerobot-plugin-template"
