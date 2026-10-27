package de.moinmornhart.stinkendreich.paper.block;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Alles, was das Plugin über die Blöcke der Mod wissen muss: welches Modell ein Zustand zeigt (aus den
 * Blockzustand-Dateien der Mod), ob und wie man mit ihm zusammenstößt, wie hell er leuchtet und aus welcher Klasse
 * der Mod er stammt (aus {@code bloecke.json}, geschrieben von der Mod selbst, siehe {@code dev/PaperDaten}).
 */
public final class Blockdaten {
    /** Ein Kasten in Pixeln (0–16). */
    public record Kasten(double x0, double y0, double z0, double x1, double y1, double z1) {
        public boolean voll() {
            return x0 <= 0 && y0 <= 0 && z0 <= 0 && x1 >= 16 && y1 >= 16 && z1 >= 16;
        }
    }

    /**
     * So wird ein Zustand auf dem Paper-Server dargestellt.
     *
     * @param modell    Blockmodell (z. B. „stinkendreich:block/deko/sofa_weiss“) oder null (unsichtbar)
     * @param x         Drehung um x laut Blockzustand-Datei
     * @param y         Drehung um y laut Blockzustand-Datei
     * @param kollision Kästen, an denen man anstößt (leer = man läuft hindurch)
     * @param umriss    Kästen, die man anklicken kann
     * @param licht     Leuchtkraft 0–15
     * @param vorbild   Block aus dem Spiel, der stattdessen gesetzt wird (Deckenblöcke), sonst null
     */
    public record Darstellung(String modell, int x, int y, List<Kasten> kollision, List<Kasten> umriss, int licht,
                              String vorbild) {
        public boolean fest() {
            return !kollision.isEmpty();
        }
    }

    private record Blockinfo(String klasse, boolean blockEntity, String vorbild, Map<String, JsonObject> zustaende) {
    }

    private final Map<String, Blockinfo> bloecke = new HashMap<>();
    private final List<List<Kasten>> formen = new ArrayList<>();
    private final Map<String, JsonObject> blockzustandDateien = new HashMap<>();
    private final Map<Zustand, Darstellung> cache = new HashMap<>();
    /** Alle Modelle, die als Anzeige gebraucht werden (für das Ressourcenpaket). */
    private final Set<String> modelle = new TreeSet<>();

    public Blockdaten(StinkendReichPaper plugin) {
        JsonObject alles = json(plugin, "bloecke.json");
        if (alles == null) {
            plugin.getLogger().severe("bloecke.json fehlt im Plugin – Blöcke der Villa werden unsichtbar sein");
            return;
        }
        for (JsonElement f : alles.getAsJsonArray("formen")) {
            List<Kasten> kaesten = new ArrayList<>();
            String text = f.getAsString();
            if (!text.isEmpty()) {
                for (String k : text.split(";")) {
                    String[] z = k.trim().split(" ");
                    kaesten.add(new Kasten(Double.parseDouble(z[0]), Double.parseDouble(z[1]), Double.parseDouble(z[2]),
                            Double.parseDouble(z[3]), Double.parseDouble(z[4]), Double.parseDouble(z[5])));
                }
            }
            formen.add(List.copyOf(kaesten));
        }
        for (Map.Entry<String, JsonElement> e : alles.getAsJsonObject("bloecke").entrySet()) {
            JsonObject b = e.getValue().getAsJsonObject();
            Map<String, JsonObject> z = new HashMap<>();
            b.getAsJsonObject("zustaende").entrySet().forEach(ze -> z.put(ze.getKey(), ze.getValue().getAsJsonObject()));
            bloecke.put(e.getKey(), new Blockinfo(b.get("klasse").getAsString(), b.get("blockentity").getAsBoolean(),
                    b.has("vorbild") ? b.get("vorbild").getAsString() : null, z));
        }
        // Alle Modelle schon jetzt sammeln (das Ressourcenpaket entsteht beim Start)
        for (String block : bloecke.keySet()) {
            JsonObject datei = blockzustandDatei(plugin, block);
            if (datei != null && datei.has("variants")) {
                for (Map.Entry<String, JsonElement> v : datei.getAsJsonObject("variants").entrySet()) {
                    JsonElement w = v.getValue();
                    JsonObject o = w.isJsonArray() ? w.getAsJsonArray().get(0).getAsJsonObject() : w.getAsJsonObject();
                    modelle.add(o.get("model").getAsString());
                }
            }
        }
        plugin.getLogger().info("Blockdaten: " + bloecke.size() + " Blöcke der Mod, " + modelle.size() + " Modelle");
    }

    static JsonObject json(StinkendReichPaper plugin, String pfad) {
        try (InputStream ein = plugin.getResource(pfad)) {
            if (ein == null) {
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(ein, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    /** Eine JSON-Datei aus dem Plugin (Objekt oder Liste) oder null. */
    public static JsonElement jsonElement(StinkendReichPaper plugin, String pfad) {
        try (InputStream ein = plugin.getResource(pfad)) {
            return ein == null ? null : JsonParser.parseReader(new InputStreamReader(ein, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private JsonObject blockzustandDatei(StinkendReichPaper plugin, String block) {
        return blockzustandDateien.computeIfAbsent(block, b -> {
            String[] teile = b.split(":", 2);
            return json(plugin, "mod/assets/" + teile[0] + "/blockstates/" + teile[1] + ".json");
        });
    }

    public boolean kennt(String block) {
        return bloecke.containsKey(block);
    }

    public String klasse(String block) {
        Blockinfo i = bloecke.get(block);
        return i == null ? "" : i.klasse();
    }

    public Set<String> modelle() {
        return modelle;
    }

    /** Darstellung eines Zustands (zwischengespeichert). */
    public Darstellung darstellung(Zustand zustand) {
        return cache.computeIfAbsent(zustand, this::berechnen);
    }

    private Darstellung berechnen(Zustand zustand) {
        Blockinfo info = bloecke.get(zustand.block());
        List<Kasten> kollision = List.of(new Kasten(0, 0, 0, 16, 16, 16));
        List<Kasten> umriss = kollision;
        int licht = 0;
        if (info != null) {
            JsonObject z = info.zustaende().get(zustand.schluessel());
            if (z == null) {
                // Unbekannte Eigenschaften (alte Welt?): ersten Zustand nehmen
                z = info.zustaende().values().stream().findFirst().orElse(null);
            }
            if (z != null) {
                kollision = formen.get(z.get("k").getAsInt());
                umriss = formen.get(z.get("u").getAsInt());
                licht = z.has("l") ? z.get("l").getAsInt() : 0;
            }
        }
        String modell = null;
        int x = 0, y = 0;
        JsonObject datei = blockzustandDateien.get(zustand.block());
        if (datei != null && datei.has("variants")) {
            for (Map.Entry<String, JsonElement> v : datei.getAsJsonObject("variants").entrySet()) {
                if (passt(zustand, v.getKey())) {
                    JsonElement w = v.getValue();
                    JsonObject o = w.isJsonArray() ? w.getAsJsonArray().get(0).getAsJsonObject() : w.getAsJsonObject();
                    modell = o.get("model").getAsString();
                    x = o.has("x") ? o.get("x").getAsInt() : 0;
                    y = o.has("y") ? o.get("y").getAsInt() : 0;
                    break;
                }
            }
        }
        return new Darstellung(modell, x, y, kollision, umriss, licht, info == null ? null : info.vorbild());
    }

    /** Passt der Zustand zu einem Variantenschlüssel wie „facing=north,half=lower“ (leer = alle)? */
    private static boolean passt(Zustand zustand, String schluessel) {
        if (schluessel.isEmpty()) {
            return true;
        }
        for (String teil : schluessel.split(",")) {
            String[] kv = teil.split("=", 2);
            if (kv.length < 2 || !kv[1].equals(zustand.wert(kv[0]))) {
                return false;
            }
        }
        return true;
    }
}
