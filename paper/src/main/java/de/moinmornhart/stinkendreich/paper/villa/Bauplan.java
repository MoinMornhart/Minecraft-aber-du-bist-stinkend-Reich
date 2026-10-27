package de.moinmornhart.stinkendreich.paper.villa;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.moinmornhart.stinkendreich.paper.Nbt;
import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import de.moinmornhart.stinkendreich.paper.block.Zustand;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Der Bauplan der Villa: dieselbe Strukturdatei wie in der Mod ({@code villa.nbt} aus tools/villa/) samt Marken
 * ({@code villa.json}: Stellplätze, Butler, Fernseher …). Blöcke aus dem Spiel werden direkt zu BlockData, Blöcke der
 * Mod bleiben ein {@link Zustand} und werden später als virtuelle Blöcke gesetzt.
 */
public final class Bauplan {
    /** Ein Block des Bauplans: Position, Eintrag in der Palette, Block-Daten (Truhen, Laser …) oder null. */
    public record Eintrag(int x, int y, int z, int palette, Map<String, Object> daten) {
    }

    /** Eine Figur des Bauplans (Bild, Rahmen, Fisch, Tänzerin …): genaue Position und ihre Daten. */
    public record Figur(double x, double y, double z, Map<String, Object> daten) {
    }

    public final int breiteX, hoehe, breiteZ, bodenY;
    /** Datenversion von Minecraft, mit der der Bauplan geschrieben wurde (für Truheninhalte und Figuren). */
    public final int datenVersion;
    /** Palette: BlockData (Spiel) oder Zustand (Mod). */
    public final Object[] palette;
    public final List<Eintrag> bloecke = new ArrayList<>();
    public final List<Figur> figuren = new ArrayList<>();
    private final JsonObject marken;

    public Bauplan(StinkendReichPaper plugin) throws Exception {
        Map<String, Object> nbt;
        try (InputStream ein = plugin.getResource("mod/data/stinkendreich/structure/villa/villa.nbt")) {
            if (ein == null) {
                throw new IllegalStateException("villa.nbt fehlt im Plugin");
            }
            nbt = Nbt.lesenGzip(ein);
        }
        datenVersion = Nbt.zahl(nbt, "DataVersion", 3953);
        List<Object> groesse = Nbt.liste(nbt, "size");
        breiteX = ((Number) groesse.get(0)).intValue();
        hoehe = ((Number) groesse.get(1)).intValue();
        breiteZ = ((Number) groesse.get(2)).intValue();
        List<Object> pal = Nbt.liste(nbt, "palette");
        palette = new Object[pal.size()];
        int unbekannt = 0;
        for (int i = 0; i < pal.size(); i++) {
            @SuppressWarnings("unchecked") Map<String, Object> e = (Map<String, Object>) pal.get(i);
            String name = Nbt.text(e, "Name", "minecraft:air");
            StringBuilder text = new StringBuilder(name);
            Map<String, Object> eigenschaften = Nbt.compound(e, "Properties");
            if (!eigenschaften.isEmpty()) {
                text.append('[');
                boolean erste = true;
                for (Map.Entry<String, Object> p : eigenschaften.entrySet()) {
                    if (!erste) {
                        text.append(',');
                    }
                    text.append(p.getKey()).append('=').append(p.getValue());
                    erste = false;
                }
                text.append(']');
            }
            if (name.startsWith("minecraft:")) {
                BlockData d;
                try {
                    d = Bukkit.createBlockData(umbenannt(text.toString()));
                } catch (IllegalArgumentException ex) {
                    d = Bukkit.createBlockData("minecraft:air");
                    unbekannt++;
                    plugin.getLogger().warning("Bauplan: unbekannter Blockzustand " + text);
                }
                palette[i] = d;
            } else {
                palette[i] = Zustand.lesen(text.toString());
            }
        }
        for (Object o : Nbt.liste(nbt, "blocks")) {
            @SuppressWarnings("unchecked") Map<String, Object> b = (Map<String, Object>) o;
            List<Object> pos = Nbt.liste(b, "pos");
            @SuppressWarnings("unchecked") Map<String, Object> daten = b.get("nbt") instanceof Map ? (Map<String, Object>) b.get("nbt") : null;
            bloecke.add(new Eintrag(((Number) pos.get(0)).intValue(), ((Number) pos.get(1)).intValue(),
                    ((Number) pos.get(2)).intValue(), Nbt.zahl(b, "state", 0), daten));
        }
        // Erst von unten nach oben setzen (Fackeln, Teppiche und Türen brauchen ihren Untergrund)
        bloecke.sort((a, b) -> a.y() != b.y() ? Integer.compare(a.y(), b.y())
                : a.x() != b.x() ? Integer.compare(a.x(), b.x()) : Integer.compare(a.z(), b.z()));
        for (Object o : Nbt.liste(nbt, "entities")) {
            @SuppressWarnings("unchecked") Map<String, Object> f = (Map<String, Object>) o;
            List<Object> pos = Nbt.liste(f, "pos");
            figuren.add(new Figur(((Number) pos.get(0)).doubleValue(), ((Number) pos.get(1)).doubleValue(),
                    ((Number) pos.get(2)).doubleValue(), Nbt.compound(f, "nbt")));
        }
        try (InputStream ein = plugin.getResource("mod/data/stinkendreich/structure/villa/villa.json")) {
            JsonObject j = JsonParser.parseReader(new InputStreamReader(ein, StandardCharsets.UTF_8)).getAsJsonObject();
            bodenY = j.get("boden_y").getAsInt();
            marken = j.getAsJsonObject("marken");
        }
        plugin.getLogger().info("Bauplan: " + breiteX + "×" + hoehe + "×" + breiteZ + ", " + bloecke.size() + " Blöcke, "
                + figuren.size() + " Figuren" + (unbekannt > 0 ? ", " + unbekannt + " unbekannte Blockzustände" : ""));
    }

    /**
     * Der Bauplan ist im Format von Minecraft 1.21 gespeichert; einige Blöcke heißen seitdem anders (z. B. die Kette ab
     * 1.21.9 „iron_chain“). Minecraft selbst rechnet das beim Laden um, hier passiert es von Hand.
     */
    private static String umbenannt(String zustand) {
        if (zustand.startsWith("minecraft:chain[") || zustand.equals("minecraft:chain")) {
            return "minecraft:iron_chain" + zustand.substring("minecraft:chain".length());
        }
        return zustand;
    }

    /** Alle Marken einer Art (z. B. „stellplatz“): je eine Liste von Zahlen/Text. */
    public List<JsonArray> marke(String art) {
        List<JsonArray> liste = new ArrayList<>();
        if (marken.has(art)) {
            for (JsonElement e : marken.getAsJsonArray(art)) {
                liste.add(e.getAsJsonArray());
            }
        }
        return liste;
    }

    public JsonArray ersteMarke(String art) {
        List<JsonArray> l = marke(art);
        return l.isEmpty() ? null : l.get(0);
    }
}
