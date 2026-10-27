package de.moinmornhart.stinkendreich.paper.villa;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Alle gebauten Villen des Servers (Merkdatei {@code villen.json} im Plugin-Ordner). Nummer 0 ist die Villa am Spawn;
 * weitere Villen gehören je einem Spieler und stehen im Raster um die erste herum.
 */
public final class Villen {
    /** Eine gebaute Villa: Welt, Ursprung des Bauplans (Nordwest-Ecke unten), Nummer, Besitzer. */
    public record Villa(UUID welt, int x, int y, int z, int nr, UUID besitzer, String besitzerName) {
        public World world() {
            return Bukkit.getWorld(welt);
        }

        /** Weltposition einer Stelle im Bauplan. */
        public Location ort(Bauplan plan, double lx, double ly, double lz) {
            return new Location(world(), x + lx, y + ly, z + lz);
        }

        public Block block(double lx, double ly, double lz) {
            return world().getBlockAt(x + (int) Math.floor(lx), y + (int) Math.floor(ly), z + (int) Math.floor(lz));
        }

        /** Haustür: wo neue Spieler ankommen (Marke „spawn“). */
        public Location spawn(Bauplan plan) {
            JsonArray m = plan.ersteMarke("spawn");
            Location l = ort(plan, m.get(0).getAsDouble(), m.get(1).getAsDouble(), m.get(2).getAsDouble());
            l.setYaw(m.get(3).getAsFloat());
            return l;
        }

        public boolean enthaelt(Bauplan plan, Location l) {
            return l.getWorld() != null && l.getWorld().getUID().equals(welt)
                    && l.getBlockX() >= x && l.getBlockX() < x + plan.breiteX
                    && l.getBlockZ() >= z && l.getBlockZ() < z + plan.breiteZ
                    && l.getBlockY() >= y && l.getBlockY() < y + plan.hoehe;
        }

        /** Waagerecht höchstens {@code rand} Blöcke vom Grundstück entfernt? */
        public boolean inDerNaehe(Bauplan plan, Location l, int rand) {
            return l.getWorld() != null && l.getWorld().getUID().equals(welt)
                    && l.getBlockX() >= x - rand && l.getBlockX() < x + plan.breiteX + rand
                    && l.getBlockZ() >= z - rand && l.getBlockZ() < z + plan.breiteZ + rand;
        }

        /** Anzeigename, z. B. „Villa von Aircrafter“ (die Spawn-Villa heißt nur „Villa“). */
        public String name() {
            return besitzerName == null || besitzerName.isEmpty() ? "Villa" : "Villa von " + besitzerName;
        }
    }

    private final StinkendReichPaper plugin;
    private final List<Villa> villen = new ArrayList<>();

    public Villen(StinkendReichPaper plugin) {
        this.plugin = plugin;
        laden();
    }

    private Path datei() {
        return plugin.getDataFolder().toPath().resolve("villen.json");
    }

    private void laden() {
        try {
            if (!Files.exists(datei())) {
                return;
            }
            JsonArray liste = JsonParser.parseString(Files.readString(datei(), StandardCharsets.UTF_8)).getAsJsonArray();
            for (JsonElement e : liste) {
                JsonObject j = e.getAsJsonObject();
                villen.add(new Villa(UUID.fromString(j.get("welt").getAsString()), j.get("x").getAsInt(), j.get("y").getAsInt(),
                        j.get("z").getAsInt(), j.get("nr").getAsInt(),
                        j.has("besitzer") ? UUID.fromString(j.get("besitzer").getAsString()) : null,
                        j.has("besitzerName") ? j.get("besitzerName").getAsString() : ""));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("villen.json konnte nicht gelesen werden: " + e.getMessage());
        }
    }

    private void speichern() {
        JsonArray liste = new JsonArray();
        for (Villa v : villen) {
            JsonObject j = new JsonObject();
            j.addProperty("welt", v.welt().toString());
            j.addProperty("x", v.x());
            j.addProperty("y", v.y());
            j.addProperty("z", v.z());
            j.addProperty("nr", v.nr());
            if (v.besitzer() != null) {
                j.addProperty("besitzer", v.besitzer().toString());
                j.addProperty("besitzerName", v.besitzerName());
            }
            liste.add(j);
        }
        try {
            Files.createDirectories(datei().getParent());
            Files.writeString(datei(), new GsonBuilder().setPrettyPrinting().create().toJson(liste), StandardCharsets.UTF_8);
        } catch (Exception e) {
            plugin.getLogger().warning("villen.json konnte nicht geschrieben werden: " + e.getMessage());
        }
    }

    public List<Villa> alle() {
        return List.copyOf(villen);
    }

    public Villa nr(int nr) {
        return villen.stream().filter(v -> v.nr() == nr).findFirst().orElse(null);
    }

    public Villa spawnVilla(World welt) {
        return villen.stream().filter(v -> v.nr() == 0 && v.welt().equals(welt.getUID())).findFirst().orElse(null);
    }

    public Villa vonBesitzer(UUID spieler) {
        return villen.stream().filter(v -> spieler.equals(v.besitzer())).findFirst().orElse(null);
    }

    /** Die Villa, auf deren Grundstück die Stelle liegt (mit Rand), oder null. */
    public Villa bei(Location l, int rand) {
        Bauplan plan = plugin.bauplan();
        return villen.stream().filter(v -> v.inDerNaehe(plan, l, rand)).findFirst().orElse(null);
    }

    /** Trägt den Besitzer einer Villa ein (der erste Spieler wird Herr der Spawn-Villa). */
    public void besitzerSetzen(Villa v, UUID besitzer, String name) {
        int i = villen.indexOf(v);
        if (i >= 0) {
            villen.set(i, new Villa(v.welt(), v.x(), v.y(), v.z(), v.nr(), besitzer, name));
            speichern();
        }
    }

    public int naechsteNummer() {
        return villen.stream().mapToInt(Villa::nr).max().orElse(-1) + 1;
    }

    public void hinzufuegen(Villa v) {
        villen.add(v);
        speichern();
    }

    /** Platz Nummer nr im Raster um die Spawn-Villa: Ringe von innen nach außen (wie in der Mod). */
    public static int[] platz(int nr) {
        int n = 0;
        for (int ring = 1; ; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == ring && ++n == nr) {
                        return new int[]{dx, dz};
                    }
                }
            }
        }
    }

    /** Erster freier Rasterplatz (Nordwest-Ecke x, z) für eine neue Villa. */
    public int[] freierPlatz(Villa spawnVilla, int abstand) {
        for (int k = 1; k < 400; k++) {
            int[] p = platz(k);
            int x = spawnVilla.x() + p[0] * abstand, z = spawnVilla.z() + p[1] * abstand;
            boolean belegt = villen.stream().anyMatch(v -> v.welt().equals(spawnVilla.welt())
                    && Math.max(Math.abs(v.x() - x), Math.abs(v.z() - z)) < abstand / 2);
            if (!belegt) {
                return new int[]{x, z};
            }
        }
        return new int[]{spawnVilla.x() + 400 * abstand, spawnVilla.z()};
    }
}
