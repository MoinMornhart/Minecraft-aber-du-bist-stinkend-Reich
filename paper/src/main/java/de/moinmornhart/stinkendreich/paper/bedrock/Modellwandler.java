package de.moinmornhart.stinkendreich.paper.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Wandelt Java-Modelle (Block- und Item-Modelle aus Elementen) in Bedrock-Geometrie um – so, wie eine Anzeige-Figur sie
 * zeigt: Modellmitte (8, 8, 8) auf der Figur, keine Anzeige-Einstellungen („none“).
 *
 * <p>Achsen wie in Blockbench (dem Werkzeug, mit dem Bedrock-Modelle üblicherweise gebaut werden): x wird gespiegelt
 * gespeichert, Drehungen um x und y wechseln das Vorzeichen, oben/unten werden die Bildausschnitte umgedreht. Bedrock
 * zeichnet Figuren um 180° gedreht – genau wie Minecraft Gegenstände in Anzeige-Figuren (ItemDisplayRenderer); beides hebt
 * sich auf, deshalb braucht es keine weitere Drehung.
 *
 * <p>Bedrock kann je Zeichnung nur eine Textur. Ein Modell wird deshalb in Teile zerlegt: je Textur ein Teil mit eigener
 * Geometrie.
 */
final class Modellwandler {
    /** Ein Teil des Modells: alle Würfel mit derselben Textur. */
    record Teil(String textur, JsonArray wuerfel) {
    }

    /** Ergebnis: Teile und der größte Abstand eines Eckpunkts von der Mitte (in Pixeln, für die Sichtgrenzen). */
    record Ergebnis(List<Teil> teile, double radius) {
    }

    /** Größe einer Textur in Pixeln (bei Animationen nur das erste Bild). */
    record Groesse(int breite, int hoehe, int bildHoehe) {
    }

    private final Function<String, byte[]> dateien;
    private final Function<String, Groesse> groesse;
    private final Map<String, JsonObject> zwischenspeicher = new HashMap<>();

    /**
     * @param dateien liest eine Datei des Java-Ressourcenpakets („assets/…“), null wenn es sie nicht gibt
     * @param groesse Größe einer Textur zu einer Textur-Kennung („stinkendreich:block/…“)
     */
    Modellwandler(Function<String, byte[]> dateien, Function<String, Groesse> groesse) {
        this.dateien = dateien;
        this.groesse = groesse;
    }

    /** Modell mit allen Eltern auflösen und umwandeln; null, wenn es keine Elemente hat. */
    Ergebnis wandeln(String modellId) {
        Map<String, String> texturen = new HashMap<>();
        JsonArray elemente = null;
        String id = modellId;
        for (int tiefe = 0; id != null && tiefe < 10; tiefe++) {
            JsonObject m = modell(id);
            if (m == null) {
                break;
            }
            if (m.has("textures")) {
                for (var e : m.getAsJsonObject("textures").entrySet()) {
                    texturen.putIfAbsent(e.getKey(), e.getValue().getAsString());
                }
            }
            if (elemente == null && m.has("elements")) {
                elemente = m.getAsJsonArray("elements");
            }
            id = m.has("parent") ? m.get("parent").getAsString() : null;
        }
        if (elemente == null || elemente.isEmpty()) {
            return null;
        }
        Map<String, JsonArray> teile = new LinkedHashMap<>();
        double radius = 0;
        for (JsonElement je : elemente) {
            JsonObject el = je.getAsJsonObject();
            double[] von = vec(el.getAsJsonArray("from")), bis = vec(el.getAsJsonArray("to"));
            radius = Math.max(radius, eckRadius(von, bis));
            JsonObject faces = el.getAsJsonObject("faces");
            if (faces == null) {
                continue;
            }
            // Je Textur ein eigener Würfel mit nur den Seiten dieser Textur
            Map<String, JsonObject> seitenJeTextur = new LinkedHashMap<>();
            for (var f : faces.entrySet()) {
                JsonObject face = f.getValue().getAsJsonObject();
                String textur = aufloesen(face.has("texture") ? face.get("texture").getAsString() : "#missing", texturen);
                if (textur == null) {
                    continue;
                }
                Groesse g = groesse.apply(textur);
                JsonObject uv = seite(f.getKey(), face, von, bis, g);
                seitenJeTextur.computeIfAbsent(textur, t -> new JsonObject()).add(f.getKey(), uv);
            }
            for (var e : seitenJeTextur.entrySet()) {
                teile.computeIfAbsent(e.getKey(), t -> new JsonArray()).add(wuerfel(el, von, bis, e.getValue()));
            }
        }
        List<Teil> liste = new ArrayList<>();
        teile.forEach((t, w) -> liste.add(new Teil(t, w)));
        return new Ergebnis(liste, radius);
    }

    // ------------------------------------------------------------------
    // Würfel und Seiten
    // ------------------------------------------------------------------

    private static JsonObject wuerfel(JsonObject el, double[] von, double[] bis, JsonObject uv) {
        JsonObject w = new JsonObject();
        double x0 = Math.min(von[0], bis[0]), y0 = Math.min(von[1], bis[1]), z0 = Math.min(von[2], bis[2]);
        double x1 = Math.max(von[0], bis[0]), y1 = Math.max(von[1], bis[1]), z1 = Math.max(von[2], bis[2]);
        // Mitte (8, 8, 8) auf den Ursprung, x gespiegelt: kleinste Ecke als Ursprung
        w.add("origin", arr(8 - x1, y0 - 8, z0 - 8));
        w.add("size", arr(x1 - x0, y1 - y0, z1 - z0));
        if (el.has("rotation")) {
            JsonObject r = el.getAsJsonObject("rotation");
            double[] o = r.has("origin") ? vec(r.getAsJsonArray("origin")) : new double[]{8, 8, 8};
            double rx = 0, ry = 0, rz = 0;
            if (r.has("axis")) {
                double winkel = r.get("angle").getAsDouble();
                switch (r.get("axis").getAsString()) {
                    case "x" -> rx = winkel;
                    case "y" -> ry = winkel;
                    default -> rz = winkel;
                }
            } else {
                rx = r.has("x") ? r.get("x").getAsDouble() : 0;
                ry = r.has("y") ? r.get("y").getAsDouble() : 0;
                rz = r.has("z") ? r.get("z").getAsDouble() : 0;
            }
            if (rx != 0 || ry != 0 || rz != 0) {
                w.add("pivot", arr(8 - o[0], o[1] - 8, o[2] - 8));
                w.add("rotation", arr(-rx, -ry, rz));
            }
        }
        w.add("uv", uv);
        return w;
    }

    /** Bildausschnitt einer Seite in Pixeln der Textur (fehlende UV wie Minecraft aus der Lage berechnet). */
    private static JsonObject seite(String name, JsonObject face, double[] von, double[] bis, Groesse g) {
        double[] uv;
        if (face.has("uv")) {
            uv = vec(face.getAsJsonArray("uv"));
        } else {
            uv = switch (name) {
                case "down" -> new double[]{von[0], 16 - bis[2], bis[0], 16 - von[2]};
                case "up" -> new double[]{von[0], von[2], bis[0], bis[2]};
                case "north" -> new double[]{16 - bis[0], 16 - bis[1], 16 - von[0], 16 - von[1]};
                case "south" -> new double[]{von[0], 16 - bis[1], bis[0], 16 - von[1]};
                case "west" -> new double[]{von[2], 16 - bis[1], bis[2], 16 - von[1]};
                default -> new double[]{16 - bis[2], 16 - bis[1], 16 - von[2], 16 - von[1]};
            };
        }
        double sx = g.breite() / 16.0, sy = g.bildHoehe() / 16.0;
        JsonObject o = new JsonObject();
        if (name.equals("up") || name.equals("down")) {
            // Oben/unten liest Bedrock den Ausschnitt andersherum (wie Blockbench: von der anderen Ecke aus)
            o.add("uv", arr(uv[2] * sx, uv[3] * sy));
            o.add("uv_size", arr((uv[0] - uv[2]) * sx, (uv[1] - uv[3]) * sy));
        } else {
            o.add("uv", arr(uv[0] * sx, uv[1] * sy));
            o.add("uv_size", arr((uv[2] - uv[0]) * sx, (uv[3] - uv[1]) * sy));
        }
        if (face.has("rotation") && face.get("rotation").getAsInt() % 360 != 0) {
            o.addProperty("uv_rotation", Math.floorMod(face.get("rotation").getAsInt(), 360));
        }
        return o;
    }

    // ------------------------------------------------------------------
    // Modelle und Texturen
    // ------------------------------------------------------------------

    /** „#name“ über die Texturliste auflösen; vollständige Kennung mit Namensraum oder null. */
    private static String aufloesen(String textur, Map<String, String> texturen) {
        for (int i = 0; i < 10 && textur != null && textur.startsWith("#"); i++) {
            textur = texturen.get(textur.substring(1));
        }
        if (textur == null || textur.startsWith("#")) {
            return null;
        }
        return textur.contains(":") ? textur : "minecraft:" + textur;
    }

    private JsonObject modell(String id) {
        String voll = id.contains(":") ? id : "minecraft:" + id;
        return zwischenspeicher.computeIfAbsent(voll, v -> {
            String[] t = v.split(":", 2);
            byte[] b = dateien.apply("assets/" + t[0] + "/models/" + t[1] + ".json");
            if (b != null) {
                return JsonParser.parseString(new String(b, StandardCharsets.UTF_8)).getAsJsonObject();
            }
            return Grundmodelle.modell(v);
        });
    }

    private static double eckRadius(double[] a, double[] b) {
        double r = 0;
        for (double x : new double[]{a[0], b[0]}) {
            for (double y : new double[]{a[1], b[1]}) {
                for (double z : new double[]{a[2], b[2]}) {
                    r = Math.max(r, Math.sqrt((x - 8) * (x - 8) + (y - 8) * (y - 8) + (z - 8) * (z - 8)));
                }
            }
        }
        return r;
    }

    private static double[] vec(JsonArray a) {
        double[] v = new double[a.size()];
        for (int i = 0; i < v.length; i++) {
            v[i] = a.get(i).getAsDouble();
        }
        return v;
    }

    static JsonArray arr(double... werte) {
        JsonArray a = new JsonArray();
        for (double w : werte) {
            // Kurz und ohne Rundungsrauschen (Bedrock liest Pixel als Gleitkommazahl)
            double r = Math.round(w * 10000.0) / 10000.0;
            if (r == Math.rint(r)) {
                a.add((long) r);
            } else {
                a.add(Double.parseDouble(String.format(Locale.ROOT, "%.4f", r)));
            }
        }
        return a;
    }
}
