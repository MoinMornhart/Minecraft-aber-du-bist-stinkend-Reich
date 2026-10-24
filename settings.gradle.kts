// Gradle-Einstellungen: Plugin-Quellen und die Liste aller Minecraft-Versionen,
// für die je eine eigene .jar gebaut wird.
pluginManagement {
    repositories {
        // Fabric- und Stonecutter-Plugins nur aus ihren eigenen Quellen holen
        exclusiveContent {
            forRepository { maven("https://maven.fabricmc.net/") { name = "Fabric" } }
            filter { includeGroupByRegex("net\\.fabricmc.*"); includeGroup("fabric-loom") }
        }
        exclusiveContent {
            forRepository { maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" } }
            filter { includeGroupByRegex("dev\\.kikugie.*") }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Stonecutter: eine Codebasis, ein Build-Knoten pro Minecraft-Version
    id("dev.kikugie.stonecutter") version "0.9.8"
    // Wählt pro Version die richtige Loom-Variante (verschleiert bis 1.21.11, unverschleiert ab 26.1)
    id("dev.kikugie.loom-back-compat") version "0.4.2"
}

stonecutter {
    create(rootProject) {
        // Jede Release-Version ab 1.21 bis zur neuesten (Stand: 24.10.2026)
        versions(
            "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8",
            "1.21.9", "1.21.10", "1.21.11",
            "26.1", "26.1.1", "26.1.2", "26.2", "26.3",
        )
        // Der Quelltext in src/ ist für diese Version geschrieben
        vcsVersion = "26.3"
    }
}

rootProject.name = "stinkendreich"
