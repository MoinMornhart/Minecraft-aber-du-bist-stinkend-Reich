package de.moinmornhart.stinkendreich.villa;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.moinmornhart.stinkendreich.StinkendReich;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Bauplan-Daten der Villa aus {@code villa.json} (vom Generator in tools/villa erzeugt):
 * Größe, Bodenhöhe und Markierungen wie Spawnpunkt, Aufzug, Garage.
 * Alle Koordinaten sind relativ zur unteren Nordwest-Ecke der Strukturdatei.
 */
public final class VillaDaten {
    private static VillaDaten instanz;

    public final int breiteX;
    public final int hoehe;
    public final int breiteZ;
    /** Y der Grasschicht in der Strukturdatei. */
    public final int bodenY;
    private final JsonObject marken;

    private VillaDaten(JsonObject json) {
        JsonArray g = json.getAsJsonArray("groesse");
        breiteX = g.get(0).getAsInt();
        hoehe = g.get(1).getAsInt();
        breiteZ = g.get(2).getAsInt();
        bodenY = json.get("boden_y").getAsInt();
        marken = json.getAsJsonObject("marken");
    }

    public static VillaDaten get() {
        if (instanz == null) {
            String pfad = "/data/" + StinkendReich.MOD_ID + "/structure/villa/villa.json";
            try (InputStream in = VillaDaten.class.getResourceAsStream(pfad)) {
                if (in == null) {
                    throw new IllegalStateException("Fehlt in der Mod: " + pfad);
                }
                instanz = new VillaDaten(new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class));
            } catch (Exception e) {
                throw new IllegalStateException("Villa-Daten konnten nicht gelesen werden", e);
            }
        }
        return instanz;
    }

    /** Alle Einträge einer Markierung, z. B. „aufzug“ oder „monitor“. */
    public List<JsonArray> marke(String name) {
        JsonElement e = marken.get(name);
        if (e == null) {
            return Collections.emptyList();
        }
        List<JsonArray> liste = new ArrayList<>();
        for (JsonElement eintrag : e.getAsJsonArray()) {
            liste.add(eintrag.getAsJsonArray());
        }
        return liste;
    }

    /** Erster Eintrag einer Markierung. */
    public JsonArray ersteMarke(String name) {
        List<JsonArray> liste = marke(name);
        if (liste.isEmpty()) {
            throw new IllegalStateException("Markierung fehlt: " + name);
        }
        return liste.get(0);
    }

    public Map<String, JsonElement> alleMarken() {
        return marken.asMap();
    }
}
