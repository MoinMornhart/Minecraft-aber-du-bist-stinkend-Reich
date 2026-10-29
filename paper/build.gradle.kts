// Paper-Plugin: dieselbe Villa, derselbe Butler, dieselben Autos – auf einem Paper-Server, für Spieler ohne Mod.
// Paper kennt keine eigenen Blöcke; das Plugin stellt alles mit Blöcken aus dem Spiel, Anzeige-Figuren und einem
// Ressourcenpaket dar. Bilder, Modelle, Töne und der Bauplan der Villa kommen aus der Mod (../src/main/resources).
plugins {
    java
}

// Versionsnummer wie die Mod (stonecutter.properties.toml, „mod.version“)
val modVersion: String = Regex("""mod\.version\s*=\s*"([^"]+)"""")
    .find(file("../stonecutter.properties.toml").readText())?.groupValues?.get(1) ?: "0.0.0"
version = modVersion
group = "de.moinmornhart"

/** Paper-Version, gegen die gebaut wird (das Plugin läuft auf Paper 26.3). */
val paperApi = "26.3.build.226-stable"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") { name = "PaperMC" }
    // Geyser (Bedrock-Spieler): nur die Schnittstelle zum Übersetzen, Geyser selbst installiert der Betreiber
    maven("https://repo.opencollab.dev/main/") { name = "GeyserMC" }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApi")
    compileOnly("org.geysermc.geyser:api:2.12.1-SNAPSHOT")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
}

// Aus der Mod übernommen (reines Java ohne Minecraft): die Stücke des Konzertflügels
sourceSets {
    main {
        java {
            srcDir("../src/main/java")
            include("de/moinmornhart/stinkendreich/paper/**", "de/moinmornhart/stinkendreich/klavier/Lieder.java",
                    "de/moinmornhart/stinkendreich/strasse/WegweiserForm.java")
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.processResources {
    inputs.property("version", modVersion)
    filesMatching("plugin.yml") { expand("version" to modVersion) }
    // Aus der Mod: Bilder, Modelle, Töne, Texte (fürs Ressourcenpaket), Deko-Daten und der Bauplan der Villa
    from("../src/main/resources") {
        include("assets/stinkendreich/**")
        include("assets/minecraft/**")
        include("deko_bloecke.json")
        include("data/stinkendreich/structure/villa/**")
        include("data/stinkendreich/painting_variant/**")
        into("mod")
    }
}

tasks.jar {
    // Bewusst nicht „stinkendreich-…+mc26.3.jar“: so heißen die Dateien der Mod, und die Update-Funktion der Mod
    // würde sonst das Plugin als Update anbieten
    archiveFileName.set("stinkendreich_paper-$modVersion+paper26.3.jar")
}
