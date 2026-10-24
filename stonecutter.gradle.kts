// Stonecutter-Steuerung: aktive Version und versionsabhängige Ersetzungen im Quelltext.
plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.3"

stonecutter parameters {
    swaps["mod_version"] = "\"${property("mod.version")}\";"
    swaps["minecraft"] = "\"${node.metadata.version}\";"
    dependencies["fapi"] = node.project.property("deps.fabric_api") as String

    replacements {
        // Mojang hat ResourceLocation in 1.21.11 in Identifier umbenannt
        string(current.parsed >= "1.21.11") {
            replace("ResourceLocation", "Identifier")
        }
        // Tippfehler im Methodennamen wurde in 1.21.9 korrigiert
        string(current.parsed >= "1.21.9") {
            replace("noCollission()", "noCollision()")
        }
        // Kopfzeile der Class-Tweaker-Datei
        string(current.parsed >= "26.1") {
            replace("classTweaker v2 named", "classTweaker v2 official")
        }
    }
}
