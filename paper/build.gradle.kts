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
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApi")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
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
        into("mod")
    }
}

tasks.jar {
    archiveFileName.set("stinkendreich-paper-$modVersion+mc26.3.jar")
}
