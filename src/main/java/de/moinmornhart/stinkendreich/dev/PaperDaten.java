package de.moinmornhart.stinkendreich.dev;

//? if >=26.3 {
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.register.ModDecken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Schreibt für das Paper-Plugin (paper/) die Eigenschaften aller Blöcke der Mod in eine Datei: Kollision, Umriss,
 * Leuchtkraft, Klasse und das Vorbild der Deckenblöcke. Auf einem Paper-Server gibt es keine eigenen Blöcke; das
 * Plugin stellt sie mit einem Block aus dem Spiel (Barriere, Licht, Vorbild) und einer Anzeige-Figur dar und braucht
 * dafür genau diese Angaben.
 *
 * Nur im Test: {@code STINKENDREICH_AUTOTEST=paperdaten} schreibt beim Serverstart
 * {@code paper/src/main/resources/bloecke.json} (Pfad aus {@code STINKENDREICH_PAPERDATEN}).
 */
public final class PaperDaten {
    private PaperDaten() {
    }

    public static void schreiben(MinecraftServer server) {
        String ziel = System.getenv("STINKENDREICH_PAPERDATEN");
        if (ziel == null || ziel.isBlank()) {
            return;
        }
        JsonObject bloecke = new JsonObject();
        Map<String, Integer> formen = new LinkedHashMap<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            var id = BuiltInRegistries.BLOCK.getKey(block);
            if (!id.getNamespace().equals(StinkendReich.MOD_ID)) {
                continue;
            }
            JsonObject b = new JsonObject();
            b.addProperty("klasse", block.getClass().getSimpleName());
            b.addProperty("blockentity", block instanceof EntityBlock);
            ModDecken.VORBILDER.forEach((name, vorbild) -> {
                if (id.getPath().equals("decke_" + name)) {
                    b.addProperty("vorbild", "minecraft:" + vorbild);
                }
            });
            JsonObject zustaende = new JsonObject();
            for (BlockState zustand : block.getStateDefinition().getPossibleStates()) {
                JsonObject z = new JsonObject();
                z.addProperty("k", form(formen, zustand.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)));
                z.addProperty("u", form(formen, zustand.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)));
                if (zustand.getLightEmission() > 0) {
                    z.addProperty("l", zustand.getLightEmission());
                }
                zustaende.add(schluessel(zustand), z);
            }
            b.add("zustaende", zustaende);
            bloecke.add(id.toString(), b);
        }
        JsonArray formListe = new JsonArray();
        formen.keySet().forEach(formListe::add);
        JsonObject alles = new JsonObject();
        alles.add("formen", formListe);
        alles.add("bloecke", bloecke);
        try {
            Path datei = Path.of(ziel);
            Files.createDirectories(datei.getParent());
            Files.writeString(datei, new GsonBuilder().create().toJson(alles), StandardCharsets.UTF_8);
            StinkendReich.LOGGER.info("Paper-Daten: {} Blöcke, {} Formen nach {}", bloecke.size(), formen.size(), datei);
        } catch (Exception e) {
            StinkendReich.LOGGER.error("Paper-Daten konnten nicht geschrieben werden", e);
        }
    }

    /** Eigenschaften wie im Blockzustand-Schlüssel, z. B. „facing=north,open=false“ (sortiert, leer = keine). */
    private static String schluessel(BlockState zustand) {
        return zustand.getProperties().stream()
                .sorted((a, b) -> a.getName().compareTo(b.getName()))
                .map(p -> p.getName() + "=" + wert(zustand, p))
                .collect(Collectors.joining(","));
    }

    private static <T extends Comparable<T>> String wert(BlockState zustand, Property<T> eigenschaft) {
        return eigenschaft.getName(zustand.getValue(eigenschaft));
    }

    /** Nummer der Form in der Formenliste (Kästen in Pixeln, „x0 y0 z0 x1 y1 z1;…“, leer = keine). */
    private static int form(Map<String, Integer> formen, VoxelShape form) {
        List<AABB> kaesten = form.toAabbs();
        String text = kaesten.stream().map(k -> zahl(k.minX) + " " + zahl(k.minY) + " " + zahl(k.minZ) + " "
                + zahl(k.maxX) + " " + zahl(k.maxY) + " " + zahl(k.maxZ)).collect(Collectors.joining(";"));
        return formen.computeIfAbsent(text, t -> formen.size());
    }

    private static String zahl(double wert) {
        double px = Math.round(wert * 16.0 * 100.0) / 100.0;
        return px == Math.rint(px) ? Long.toString((long) px) : Double.toString(px);
    }
}
//?}
