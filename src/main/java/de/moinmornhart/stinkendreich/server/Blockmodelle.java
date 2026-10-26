package de.moinmornhart.stinkendreich.server;

//? if >=1.21.6 {
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.moinmornhart.stinkendreich.StinkendReich;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Liest die Blockzustands- und Modelldateien der Mod (assets/stinkendreich/blockstates, models) und sagt für jeden
 * Zustand eines Blocks, welches Modell er mit welcher Drehung zeigt und ob es ein voller Würfel ist. Die
 * Server-Version braucht das, weil Spieler ohne Mod die Blöcke nur über das Ressourcenpaket kennen.
 */
final class Blockmodelle {
    /** Ein Modell mit Drehung (wie im Blockzustand angegeben). */
    record Modell(Identifier id, int x, int y, boolean vollerWuerfel) {
    }

    /** Eltern-Modelle von Minecraft, die einen vollen Würfel ergeben. */
    private static final Set<String> WUERFEL = Set.of("minecraft:block/cube_all", "minecraft:block/cube",
            "minecraft:block/cube_column", "minecraft:block/cube_bottom_top", "minecraft:block/cube_column_horizontal",
            "minecraft:block/cube_directional", "minecraft:block/orientable", "minecraft:block/orientable_with_bottom",
            "block/cube_all", "block/cube", "block/cube_column", "block/cube_bottom_top", "block/orientable");

    private static final Map<Identifier, Boolean> WUERFEL_CACHE = new HashMap<>();

    private Blockmodelle() {
    }

    private static Optional<Path> datei(String pfad) {
        return FabricLoader.getInstance().getModContainer(StinkendReich.MOD_ID).flatMap(m -> m.findPath(pfad));
    }

    private static JsonObject lesen(String pfad) {
        return datei(pfad).map(p -> {
            try {
                return JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception e) {
                return null;
            }
        }).orElse(null);
    }

    /** Das Modell, das der Zustand zeigt (bei mehreren Varianten die erste), oder leer. */
    static Optional<Modell> modell(BlockState zustand) {
        Block block = zustand.getBlock();
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        JsonObject datei = lesen("assets/" + id.getNamespace() + "/blockstates/" + id.getPath() + ".json");
        if (datei == null || !datei.has("variants")) {
            return Optional.empty();
        }
        for (Map.Entry<String, JsonElement> e : datei.getAsJsonObject("variants").entrySet()) {
            if (passt(zustand, e.getKey())) {
                JsonElement wert = e.getValue();
                JsonObject v = wert.isJsonArray() ? wert.getAsJsonArray().get(0).getAsJsonObject() : wert.getAsJsonObject();
                Identifier modell = Identifier.parse(v.get("model").getAsString());
                return Optional.of(new Modell(modell, v.has("x") ? v.get("x").getAsInt() : 0,
                        v.has("y") ? v.get("y").getAsInt() : 0, vollerWuerfel(modell)));
            }
        }
        return Optional.empty();
    }

    /** Passt der Zustand zu einem Variantenschlüssel wie „facing=north,half=lower“ (leer = alle)? */
    private static boolean passt(BlockState zustand, String schluessel) {
        if (schluessel.isEmpty()) {
            return true;
        }
        for (String teil : schluessel.split(",")) {
            String[] kv = teil.split("=", 2);
            Property<?> eigenschaft = zustand.getBlock().getStateDefinition().getProperty(kv[0]);
            if (eigenschaft == null || kv.length < 2 || !wertName(zustand, eigenschaft).equals(kv[1])) {
                return false;
            }
        }
        return true;
    }

    private static <T extends Comparable<T>> String wertName(BlockState zustand, Property<T> eigenschaft) {
        return eigenschaft.getName(zustand.getValue(eigenschaft));
    }

    /** Ist das Modell (über seine Eltern) ein voller Würfel? */
    static boolean vollerWuerfel(Identifier modell) {
        return WUERFEL_CACHE.computeIfAbsent(modell, m -> {
            Identifier jetzt = m;
            for (int i = 0; i < 8; i++) {
                if (WUERFEL.contains(jetzt.toString())) {
                    return true;
                }
                if (!jetzt.getNamespace().equals(StinkendReich.MOD_ID)) {
                    return false;
                }
                JsonObject json = lesen("assets/" + jetzt.getNamespace() + "/models/" + jetzt.getPath() + ".json");
                if (json == null || json.has("elements") || !json.has("parent")) {
                    return false;
                }
                jetzt = Identifier.parse(json.get("parent").getAsString());
            }
            return false;
        });
    }
}
//?}
