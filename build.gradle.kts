// Build-Skript, das Stonecutter für jede Minecraft-Version einzeln ausführt.
// sc.current ist die Version des gerade gebauten Knotens.
plugins {
    // Wählt die passende Loom-Variante je nach Minecraft-Version
    id("dev.kikugie.loom-back-compat")
}

version = "${property("mod.version")}+mc${sc.current.version}"
base.archivesName = property("mod.id") as String

// Minecraft 26.x braucht Java 25, alle 1.21.x Java 21
val benoetigtesJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    else -> JavaVersion.VERSION_21
}

repositories {
    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) { name = alias } }
        filter { groups.forEach(::includeGroup) }
    }
    strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    // Mojangs offizielle Namen auch für die verschleierten 1.21.x-Versionen
    loomx.applyMojangMappings()

    // mod…-Konfigurationen funktionieren dank loom-back-compat auch ab 26.1
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
}

loom {
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")
    accessWidenerPath = sc.process(
        rootProject.file("src/main/resources/stinkendreich.ct"),
        "build/processed.ct"
    )

    decompilerOptions.named("vineflower") {
        options.put("mark-corresponding-synthetics", "1")
    }

    runConfigs.all {
        preferGradleTask = true
        generateRunConfig = true
        // Eigener Ordner je Version, weil Welten nicht abwärtskompatibel sind
        runDirectory = project.file("run")
        jvmArguments.add("-Dmixin.debug.export=true")
    }
}

java {
    withSourcesJar()
    targetCompatibility = benoetigtesJava
    sourceCompatibility = benoetigtesJava

    toolchain {
        languageVersion = JavaLanguageVersion.of(benoetigtesJava.majorVersion)
    }
}

tasks {
    processResources {
        fun MutableMap<String, String>.register(key: String, value: String) {
            inputs.property(key, value)
            set(key, value)
        }

        val props = buildMap {
            register("id", sc.properties["mod.id"])
            register("name", sc.properties["mod.name"])
            register("version", project.version.toString())
            register("minecraft", sc.properties["mod.mc_compat"])
            register("loader", Regex("\\d+\\.\\d+").find(sc.properties.get<String>("deps.fabric_loader"))!!.value)
        }

        filesMatching("fabric.mod.json") { expand(props) }

        val mixinJava = "JAVA_${benoetigtesJava.majorVersion}"
        filesMatching("*.mixins.json") { expand("java" to mixinJava) }
    }

    // Sammelt die fertige .jar jeder Version in build/libs/<modversion>/ im Hauptordner
    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Baut die .jar und kopiert sie nach build/libs/<modversion>/"

        inputs.property("version", project.property("mod.version"))
        from(loomx.modJar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
    }
}
