package de.moinmornhart.stinkendreich.paper;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.moinmornhart.stinkendreich.paper.villa.Villen;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Gästeliste je Villa (wie VillaZustand.gaeste in der Mod): Gäste kommen ohne Zugangskarte durch Tor und Türen.
 * Gespeichert in {@code gaeste.json} im Plugin-Ordner (Villa-Nummer → Namen).
 */
public final class Gaeste {
    private final Path datei;
    private final Map<Integer, Set<String>> liste = new HashMap<>();

    public Gaeste(StinkendReichPaper plugin) {
        datei = plugin.getDataFolder().toPath().resolve("gaeste.json");
        try {
            if (Files.exists(datei)) {
                JsonObject j = JsonParser.parseString(Files.readString(datei, StandardCharsets.UTF_8)).getAsJsonObject();
                for (var e : j.entrySet()) {
                    Set<String> namen = new TreeSet<>();
                    for (JsonElement n : e.getValue().getAsJsonArray()) {
                        namen.add(n.getAsString());
                    }
                    liste.put(Integer.parseInt(e.getKey()), namen);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("gaeste.json konnte nicht gelesen werden: " + e.getMessage());
        }
    }

    public Set<String> von(int villa) {
        return liste.getOrDefault(villa, Set.of());
    }

    public boolean darfRein(Villen.Villa v, Player p) {
        return v != null && von(v.nr()).contains(p.getName().toLowerCase(Locale.ROOT));
    }

    public void eintragen(int villa, String name) {
        liste.computeIfAbsent(villa, k -> new TreeSet<>()).add(name.toLowerCase(Locale.ROOT));
        speichern();
    }

    public void streichen(int villa, String name) {
        Set<String> s = liste.get(villa);
        if (s != null && s.remove(name.toLowerCase(Locale.ROOT))) {
            speichern();
        }
    }

    private void speichern() {
        JsonObject j = new JsonObject();
        liste.forEach((nr, namen) -> {
            JsonArray a = new JsonArray();
            namen.forEach(a::add);
            j.add(Integer.toString(nr), a);
        });
        try {
            Files.createDirectories(datei.getParent());
            Files.writeString(datei, new GsonBuilder().setPrettyPrinting().create().toJson(j), StandardCharsets.UTF_8);
        } catch (Exception ignoriert) {
            // nächster Versuch beim nächsten Eintrag
        }
    }
}
