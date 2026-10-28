package de.moinmornhart.stinkendreich.paper.bedrock;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Baut aus dem Java-Ressourcenpaket des Plugins ein Bedrock-Paket für Spieler über Geyser.
 *
 * <p>Alle Modelle, die unsere Anzeige-Figuren zeigen (Item-Definitionen unter assets/stinkendreich/items), werden zu
 * Bedrock-Geometrie. Bedrock-Spieler sehen statt der Anzeige-Figur (die Geyser nicht kennt) eine eigene Figur
 * {@value #FIGUR}: welches Modell, sagt die Variante; Lage, Drehung und Größe kommen über Eigenschaften der Figur und eine
 * Animation, die sie auf drei Knochen überträgt (y außen, dann x, dann z mit Maßstab – dieselbe Reihenfolge wie die
 * Zerlegung der Drehung in {@link Lage}).
 */
public final class BedrockPaket {
    /** Bedrock-Figur für alle Modelle. */
    public static final String FIGUR = "stinkendreich:anzeige";
    /** Eigenschaften der Figur (Bedrock-Werte: Grad bzw. Pixel). */
    public static final List<String> EIGENSCHAFTEN = List.of("rx", "ry", "rz", "tx", "ty", "tz", "sx", "sy", "sz");

    /** Texturen aus Minecraft, die unsere Modelle benutzen → gleiche Textur im Bedrock-Spiel (nicht mitgeliefert). */
    private static final Map<String, String> SPIEL_TEXTUREN = Map.of(
            "minecraft:block/black_concrete", "textures/blocks/concrete_black",
            "minecraft:block/gold_block", "textures/blocks/gold_block",
            "minecraft:block/grass_block_top", "textures/blocks/grass_carried",
            "minecraft:block/dirt", "textures/blocks/dirt",
            "minecraft:block/oak_planks", "textures/blocks/planks_oak",
            "minecraft:block/bookshelf", "textures/blocks/bookshelf");

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final Logger log;
    private final Map<String, byte[]> java;
    private final Map<String, byte[]> paket = new TreeMap<>();
    /** Item-Modell („stinkendreich:anzeige/…“) → Variante der Bedrock-Figur. */
    private final Map<String, Integer> varianten = new LinkedHashMap<>();
    private final Map<String, Modellwandler.Groesse> groessen = new HashMap<>();
    private final Map<String, Boolean> durchscheinend = new HashMap<>();
    private UUID kopfId;
    private byte[] daten;

    /** @param java Dateien des Java-Ressourcenpakets (Pfad „assets/…“ → Inhalt) */
    public BedrockPaket(Logger log, Map<String, byte[]> java) {
        this.log = log;
        this.java = java;
    }

    public Map<String, Integer> varianten() {
        return varianten;
    }

    public UUID kennung() {
        return kopfId;
    }

    /** Baut das Paket und schreibt es als .mcpack nach {@code ziel}. */
    public void bauen(Path ziel) throws IOException {
        Modellwandler wandler = new Modellwandler(java::get, this::groesse);
        // 1. Alle Item-Definitionen in unserem Namensraum, sortiert (gleiche Nummern bei gleichem Inhalt)
        TreeMap<String, String> modelle = new TreeMap<>();
        for (var e : java.entrySet()) {
            String p = e.getKey();
            if (p.startsWith("assets/stinkendreich/items/") && p.endsWith(".json")) {
                String item = "stinkendreich:" + p.substring("assets/stinkendreich/items/".length(), p.length() - 5);
                String modell = modellAus(JsonParser.parseString(new String(e.getValue(), StandardCharsets.UTF_8)));
                if (modell != null) {
                    modelle.put(item, modell);
                }
            }
        }
        // 2. Umwandeln: je Modell eine Variante, je Textur ein Teil
        List<List<String[]>> teileJeVariante = new ArrayList<>();      // [geometrie, textur, material]
        JsonObject geometrien = new JsonObject();
        JsonObject texturen = new JsonObject();
        Map<String, String> texturNamen = new HashMap<>();
        geometrien.addProperty("leer", "geometry.sr.leer");
        texturen.addProperty("leer", "textures/sr/leer");
        paket.put("models/entity/sr/leer.geo.json", json(geometrie("geometry.sr.leer", 16, 16, 1, new JsonArray())));
        paket.put("textures/sr/leer.png", leeresBild());
        int fehler = 0;
        for (var e : modelle.entrySet()) {
            Modellwandler.Ergebnis erg;
            try {
                erg = wandler.wandeln(e.getValue());
            } catch (RuntimeException ex) {
                fehler++;
                log.fine("Bedrock: Modell " + e.getValue() + " nicht umgewandelt: " + ex);
                continue;
            }
            if (erg == null || erg.teile().isEmpty()) {
                continue;
            }
            int v = varianten.size();
            varianten.put(e.getKey(), v);
            List<String[]> teile = new ArrayList<>();
            for (int k = 0; k < erg.teile().size(); k++) {
                Modellwandler.Teil t = erg.teile().get(k);
                String bedrockTextur = texturPfad(t.textur());
                String tName = texturNamen.computeIfAbsent(bedrockTextur, b -> "t" + texturNamen.size());
                texturen.addProperty(tName, bedrockTextur);
                Modellwandler.Groesse g = groesse(t.textur());
                String gName = "v" + v + "_" + k;
                String gId = "geometry.sr." + gName;
                geometrien.addProperty(gName, gId);
                double radius = erg.radius() / 16.0;
                paket.put("models/entity/sr/" + gName + ".geo.json", json(geometrie(gId, g.breite(), g.hoehe(), radius, t.wuerfel())));
                teile.add(new String[]{gName, tName, durchscheinend(t.textur()) ? "blend" : "test"});
            }
            teileJeVariante.add(teile);
        }
        // 3. Zeichen-Regeln: je Teil-Platz und Material eine (Bedrock wählt Geometrie und Textur nach der Variante)
        JsonObject regeln = new JsonObject();
        List<String> regelNamen = new ArrayList<>();
        int plaetze = teileJeVariante.stream().mapToInt(List::size).max().orElse(0);
        for (int i = 0; i < plaetze; i++) {
            for (String material : new String[]{"test", "blend"}) {
                JsonArray g = new JsonArray(), t = new JsonArray();
                boolean benutzt = false;
                for (List<String[]> teile : teileJeVariante) {
                    String[] teil = i < teile.size() && teile.get(i)[2].equals(material) ? teile.get(i) : null;
                    g.add("Geometry." + (teil == null ? "leer" : teil[0]));
                    t.add("Texture." + (teil == null ? "leer" : teil[1]));
                    benutzt |= teil != null;
                }
                if (!benutzt) {
                    continue;
                }
                String name = "controller.render.sr.anzeige_" + material + "_" + i;
                JsonObject r = new JsonObject();
                JsonObject arrays = new JsonObject();
                JsonObject ag = new JsonObject();
                ag.add("Array.g", g);
                JsonObject at = new JsonObject();
                at.add("Array.t", t);
                arrays.add("geometries", ag);
                arrays.add("textures", at);
                r.add("arrays", arrays);
                r.addProperty("geometry", "Array.g[query.variant]");
                JsonArray mats = new JsonArray();
                JsonObject m = new JsonObject();
                m.addProperty("*", material.equals("blend") ? "Material.blend" : "Material.default");
                mats.add(m);
                r.add("materials", mats);
                JsonArray tex = new JsonArray();
                tex.add("Array.t[query.variant]");
                r.add("textures", tex);
                regeln.add(name, r);
                regelNamen.add(name);
            }
        }
        JsonObject rc = new JsonObject();
        rc.addProperty("format_version", "1.8.0");
        rc.add("render_controllers", regeln);
        paket.put("render_controllers/sr_anzeige.render_controllers.json", json(rc));
        paket.put("animations/sr_anzeige.animation.json", json(animation()));
        paket.put("entity/sr_anzeige.entity.json", json(figur(geometrien, texturen, regelNamen)));
        // 4. Beschreibung: Version aus dem Inhalt, damit Bedrock ein geändertes Paket neu lädt
        byte[] inhalt = inhaltsSumme();
        kopfId = UUID.nameUUIDFromBytes("stinkendreich-bedrock".getBytes(StandardCharsets.UTF_8));
        int stand = ByteBuffer.wrap(inhalt).getInt() & 0x7FFFFFFF;
        paket.put("manifest.json", json(manifest(kopfId, stand)));
        daten = zip();
        Files.createDirectories(ziel.getParent());
        Files.write(ziel, daten);
        log.info("Bedrock-Paket: " + varianten.size() + " Modelle, " + texturNamen.size() + " Texturen, "
                + regelNamen.size() + " Zeichen-Regeln, " + daten.length / 1024 + " KB" + (fehler > 0 ? ", " + fehler + " Modelle fehlerhaft" : ""));
    }

    // ------------------------------------------------------------------
    // Bausteine
    // ------------------------------------------------------------------

    /** Modell aus einer Item-Definition: „minecraft:model“ direkt, sonst das erste darin (z. B. Rückfall eines Auswahl-Modells). */
    private static String modellAus(JsonElement e) {
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("type") && o.get("type").getAsString().endsWith("model") && o.has("model") && o.get("model").isJsonPrimitive()) {
                return o.get("model").getAsString();
            }
            for (var k : o.entrySet()) {
                String m = modellAus(k.getValue());
                if (m != null) {
                    return m;
                }
            }
        } else if (e.isJsonArray()) {
            for (JsonElement k : e.getAsJsonArray()) {
                String m = modellAus(k);
                if (m != null) {
                    return m;
                }
            }
        }
        return null;
    }

    private static JsonObject geometrie(String id, int breite, int hoehe, double radius, JsonArray wuerfel) {
        JsonObject beschreibung = new JsonObject();
        beschreibung.addProperty("identifier", id);
        beschreibung.addProperty("texture_width", breite);
        beschreibung.addProperty("texture_height", hoehe);
        // Sichtgrenzen großzügig: Drehung, Verschiebung und Maßstab kommen erst über die Animation dazu
        double b = Math.ceil(2 * radius + 4);
        beschreibung.addProperty("visible_bounds_width", b);
        beschreibung.addProperty("visible_bounds_height", b);
        beschreibung.add("visible_bounds_offset", Modellwandler.arr(0, 0, 0));
        JsonArray knochen = new JsonArray();
        knochen.add(knochen("sr_y", null, null));
        knochen.add(knochen("sr_x", "sr_y", null));
        knochen.add(knochen("sr_z", "sr_x", wuerfel));
        JsonObject g = new JsonObject();
        g.add("description", beschreibung);
        g.add("bones", knochen);
        JsonArray liste = new JsonArray();
        liste.add(g);
        JsonObject datei = new JsonObject();
        datei.addProperty("format_version", "1.21.0");
        datei.add("minecraft:geometry", liste);
        return datei;
    }

    private static JsonObject knochen(String name, String eltern, JsonArray wuerfel) {
        JsonObject k = new JsonObject();
        k.addProperty("name", name);
        if (eltern != null) {
            k.addProperty("parent", eltern);
        }
        k.add("pivot", Modellwandler.arr(0, 0, 0));
        if (wuerfel != null) {
            k.add("cubes", wuerfel);
        }
        return k;
    }

    /** Lage aus den Eigenschaften: y außen (mit Verschiebung), dann x, innen z mit Maßstab. */
    private static JsonObject animation() {
        JsonObject bones = new JsonObject();
        bones.add("sr_y", kanal("rotation", "0", p("ry"), "0", "position", p("tx"), p("ty"), p("tz")));
        bones.add("sr_x", kanal("rotation", p("rx"), "0", "0", null, null, null, null));
        bones.add("sr_z", kanal("rotation", "0", "0", p("rz"), "scale", p("sx"), p("sy"), p("sz")));
        JsonObject anim = new JsonObject();
        anim.addProperty("loop", true);
        anim.add("bones", bones);
        JsonObject anims = new JsonObject();
        anims.add("animation.sr.anzeige.lage", anim);
        JsonObject datei = new JsonObject();
        datei.addProperty("format_version", "1.8.0");
        datei.add("animations", anims);
        return datei;
    }

    private static String p(String name) {
        return "query.property('stinkendreich:" + name + "')";
    }

    private static JsonObject kanal(String a, String ax, String ay, String az, String b, String bx, String by, String bz) {
        JsonObject k = new JsonObject();
        JsonArray va = new JsonArray();
        va.add(ax);
        va.add(ay);
        va.add(az);
        k.add(a, va);
        if (b != null) {
            JsonArray vb = new JsonArray();
            vb.add(bx);
            vb.add(by);
            vb.add(bz);
            k.add(b, vb);
        }
        return k;
    }

    private static JsonObject figur(JsonObject geometrien, JsonObject texturen, List<String> regeln) {
        JsonObject beschreibung = new JsonObject();
        beschreibung.addProperty("identifier", FIGUR);
        JsonObject materialien = new JsonObject();
        materialien.addProperty("default", "entity_alphatest");
        materialien.addProperty("blend", "entity_alphablend");
        beschreibung.add("materials", materialien);
        beschreibung.add("textures", texturen);
        beschreibung.add("geometry", geometrien);
        JsonObject anims = new JsonObject();
        anims.addProperty("lage", "animation.sr.anzeige.lage");
        beschreibung.add("animations", anims);
        JsonObject skripte = new JsonObject();
        JsonArray animiere = new JsonArray();
        animiere.add("lage");
        skripte.add("animate", animiere);
        beschreibung.add("scripts", skripte);
        JsonArray rl = new JsonArray();
        regeln.forEach(rl::add);
        beschreibung.add("render_controllers", rl);
        JsonObject inhalt = new JsonObject();
        inhalt.add("description", beschreibung);
        JsonObject datei = new JsonObject();
        datei.addProperty("format_version", "1.10.0");
        datei.add("minecraft:client_entity", inhalt);
        return datei;
    }

    private static JsonObject manifest(UUID kopf, int stand) {
        JsonArray version = new JsonArray();
        version.add(1);
        version.add(0);
        version.add(stand % 100000);
        JsonArray motor = new JsonArray();
        motor.add(1);
        motor.add(21);
        motor.add(0);
        JsonObject header = new JsonObject();
        header.addProperty("name", "Minecraft, aber du bist stinkend reich");
        header.addProperty("description", "Villa, Möbel und Fahrzeuge für Bedrock-Spieler (über Geyser)");
        header.addProperty("uuid", kopf.toString());
        header.add("version", version);
        header.add("min_engine_version", motor);
        JsonObject modul = new JsonObject();
        modul.addProperty("type", "resources");
        modul.addProperty("uuid", UUID.nameUUIDFromBytes("stinkendreich-bedrock-modul".getBytes(StandardCharsets.UTF_8)).toString());
        modul.add("version", version.deepCopy());
        JsonArray module = new JsonArray();
        module.add(modul);
        JsonObject m = new JsonObject();
        m.addProperty("format_version", 2);
        m.add("header", header);
        m.add("modules", module);
        return m;
    }

    // ------------------------------------------------------------------
    // Texturen
    // ------------------------------------------------------------------

    /** Bedrock-Pfad der Textur (ohne .png); eigene Texturen werden dabei ins Paket kopiert. */
    private String texturPfad(String textur) {
        String spiel = SPIEL_TEXTUREN.get(textur);
        if (spiel != null) {
            return spiel;
        }
        String[] t = textur.split(":", 2);
        byte[] png = java.get("assets/" + t[0] + "/textures/" + t[1] + ".png");
        if (png == null) {
            log.fine("Bedrock: Textur " + textur + " fehlt");
            return "textures/sr/leer";
        }
        String pfad = "textures/sr/" + t[0] + "/" + t[1];
        paket.put(pfad + ".png", png);
        return pfad;
    }

    private Modellwandler.Groesse groesse(String textur) {
        return groessen.computeIfAbsent(textur, t -> {
            BufferedImage bild = bild(t);
            if (bild == null) {
                return new Modellwandler.Groesse(16, 16, 16);
            }
            int w = bild.getWidth(), h = bild.getHeight();
            String[] teile = t.split(":", 2);
            boolean animiert = java.containsKey("assets/" + teile[0] + "/textures/" + teile[1] + ".png.mcmeta") && h > w;
            return new Modellwandler.Groesse(w, h, animiert ? w : h);
        });
    }

    /** Hat die Textur halb durchsichtige Pixel (Glas, Wasser)? Dann wird sie überblendet statt ausgestanzt gezeichnet. */
    private boolean durchscheinend(String textur) {
        return durchscheinend.computeIfAbsent(textur, t -> {
            BufferedImage bild = bild(t);
            if (bild == null) {
                return false;
            }
            for (int y = 0; y < bild.getHeight(); y++) {
                for (int x = 0; x < bild.getWidth(); x++) {
                    int a = bild.getRGB(x, y) >>> 24;
                    if (a > 8 && a < 247) {
                        return true;
                    }
                }
            }
            return false;
        });
    }

    private BufferedImage bild(String textur) {
        if (SPIEL_TEXTUREN.containsKey(textur)) {
            return null;
        }
        String[] t = textur.split(":", 2);
        byte[] png = java.get("assets/" + t[0] + "/textures/" + t[1] + ".png");
        if (png == null) {
            return null;
        }
        try {
            return ImageIO.read(new ByteArrayInputStream(png));
        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] leeresBild() {
        try {
            ByteArrayOutputStream aus = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png", aus);
            return aus.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------
    // Datei
    // ------------------------------------------------------------------

    private byte[] inhaltsSumme() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            for (var e : paket.entrySet()) {
                md.update(e.getKey().getBytes(StandardCharsets.UTF_8));
                md.update(e.getValue());
            }
            return md.digest();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] zip() throws IOException {
        ByteArrayOutputStream puffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(puffer)) {
            for (var e : paket.entrySet()) {
                ZipEntry eintrag = new ZipEntry(e.getKey());
                eintrag.setTime(0);
                zip.putNextEntry(eintrag);
                zip.write(e.getValue());
                zip.closeEntry();
            }
        }
        return puffer.toByteArray();
    }

    private static byte[] json(JsonObject o) {
        return GSON.toJson(o).getBytes(StandardCharsets.UTF_8);
    }
}
