package de.moinmornhart.stinkendreich.paper.strasse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import de.moinmornhart.stinkendreich.strasse.WegweiserForm;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Wegweiser an den Straßen, wie in der Mod (Philip: „so richtig geile Straßenschilder mit Holzpfeilern“): ein Holzpfahl
 * mit Pfeilschildern ({@link WegweiserForm}) am Anfang der Straßen bei der Spawn-Villa und an jedem Abzweig (neue Straße,
 * weiter geradeaus, zurück zur Spawn-Villa). Gesetzt wird, sobald Chunk und Figuren geladen sind; gemerkt wird je
 * Wegweiser, mit welchen Schildern er steht ({@code wegweiser.json}).
 */
final class Wegweiser {
    static final String TAG = "stinkendreich_wegweiser";
    private static final double SEITE = 6.5, DAVOR = 4, ZUSAMMEN = 12;
    private static final Gson GSON = new GsonBuilder().create();

    private record Pfahl(String id, double x, double z, int strassenY, List<WegweiserForm.Schild> schilder) {
        String signatur() {
            StringBuilder sb = new StringBuilder("f" + WegweiserForm.FORM + ";");
            schilder.forEach(s -> sb.append(s.text()).append('@').append(Math.round(Math.toDegrees(Math.atan2(s.dz(), s.dx())))).append(';'));
            return sb.toString();
        }
    }

    private final StinkendReichPaper plugin;
    private final Path datei;
    private final Map<String, String> gesetzt = new HashMap<>();
    private List<Pfahl> plan = List.of();
    private int planFuer = -1;

    Wegweiser(StinkendReichPaper plugin) {
        this.plugin = plugin;
        this.datei = plugin.getDataFolder().toPath().resolve("wegweiser.json");
        try {
            if (Files.exists(datei)) {
                JsonObject o = GSON.fromJson(Files.readString(datei, StandardCharsets.UTF_8), JsonObject.class);
                o.entrySet().forEach(e -> gesetzt.put(e.getKey(), e.getValue().getAsString()));
            }
        } catch (IOException | RuntimeException ex) {
            plugin.getLogger().warning("Wegweiser: wegweiser.json nicht lesbar: " + ex.getMessage());
        }
    }

    /** Alle paar Sekunden: Plan an die Straßen anpassen, fehlende Wegweiser setzen, verwaiste abbauen. */
    void tick(World welt, List<Strassen.Strasse> strassen, String heim) {
        if (strassen.size() != planFuer) {
            plan = planen(strassen, heim);
            planFuer = strassen.size();
        }
        boolean geaendert = false;
        Map<String, Pfahl> soll = new HashMap<>();
        for (Pfahl p : plan) {
            soll.put(p.id(), p);
            if (!p.signatur().equals(gesetzt.get(p.id())) && geladen(welt, p.x(), p.z())) {
                aufstellen(welt, p);
                gesetzt.put(p.id(), p.signatur());
                geaendert = true;
            }
        }
        for (var it = gesetzt.keySet().iterator(); it.hasNext(); ) {
            String id = it.next();
            if (!soll.containsKey(id)) {
                String[] xz = id.substring(4).split("_(?=-?\\d+$)");
                if (xz.length == 2 && geladen(welt, Integer.parseInt(xz[0]), Integer.parseInt(xz[1]))) {
                    abbauen(welt, id, Integer.parseInt(xz[0]), Integer.parseInt(xz[1]));
                    it.remove();
                    geaendert = true;
                }
            }
        }
        if (geaendert) {
            speichern();
        }
    }

    /** Testhilfe (/villa wegweiser &lt;nr&gt;): Standort schräg vor dem ersten Schild, Blick auf den Wegweiser, oder null. */
    Location blick(World w, int nr) {
        if (nr < 0 || nr >= plan.size()) {
            return null;
        }
        Pfahl p = plan.get(nr);
        WegweiserForm.Schild s = p.schilder().get(0);
        double l = Math.hypot(s.dx(), s.dz()), dx = s.dx() / l, dz = s.dz() / l;
        int boden = w.getHighestBlockYAt((int) Math.floor(p.x()), (int) Math.floor(p.z()), HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
        int y = Math.abs(boden - (p.strassenY() + 1)) <= 4 ? boden : p.strassenY() + 1;
        Location ort = new Location(w, p.x() - dx * 4 - dz * 4, y + 2.5, p.z() - dz * 4 + dx * 4);
        ort.setDirection(new org.bukkit.util.Vector(p.x() + dx * 1.2 - ort.getX(), y + 2.4 - 1.62 - ort.getY(), p.z() + dz * 1.2 - ort.getZ()));
        return ort;
    }

    private static boolean geladen(World w, double x, double z) {
        int cx = (int) Math.floor(x) >> 4, cz = (int) Math.floor(z) >> 4;
        return w.isChunkLoaded(cx, cz) && w.getChunkAt(cx, cz).isEntitiesLoaded();
    }

    // ---------------- Planen (wie in der Mod) ----------------

    private List<Pfahl> planen(List<Strassen.Strasse> strassen, String heim) {
        Map<String, List<WegweiserForm.Schild>> schilder = new LinkedHashMap<>();
        List<double[]> orte = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (Strassen.Strasse s : strassen) {
            if (s.punkte().size() < 2) {
                continue;
            }
            Strassen.Strasse e = s.eltern() < 0 ? null : strassen.stream().filter(a -> a.villa() == s.eltern()).findFirst().orElse(null);
            if (s.eltern() >= 0 && (e == null || e.punkte().size() < 2)) {
                continue;
            }
            int[] j;
            double tx, tz;
            int i = 0;
            if (e == null) {
                // Anfang an der Spawn-Villa: Pfahl westlich am Ende der Zufahrt (die Straßen gehen nach Süden weg)
                j = s.punkte().get(0);
                tx = 0;
                tz = 1;
            } else {
                List<int[]> ep = e.punkte();
                i = Math.max(0, Math.min(s.abzweig(), ep.size() - 1));
                j = ep.get(i);
                int[] vor = ep.get(Math.min(i + 3, ep.size() - 1)), nach = ep.get(Math.max(i - 3, 0));
                tx = vor[0] - nach[0];
                tz = vor[2] - nach[2];
                double tl = Math.hypot(tx, tz);
                if (tl < 1e-6) {
                    continue;
                }
                tx /= tl;
                tz /= tl;
            }
            int[] b = s.punkte().get(Math.min(8, s.punkte().size() - 1));
            double bx = b[0] - j[0], bz = b[2] - j[2];
            double px, pz;
            if (e == null) {
                px = j[0] + 0.5 - SEITE;
                pz = j[2] + 0.5 - 6;
            } else {
                double nx = -tz, nz = tx, seite = bx * nx + bz * nz >= 0 ? 1 : -1;
                px = j[0] + 0.5 - seite * nx * SEITE - tx * DAVOR;
                pz = j[2] + 0.5 - seite * nz * SEITE - tz * DAVOR;
            }
            String id = null;
            for (int k = 0; k < ids.size(); k++) {
                if (Math.hypot(orte.get(k)[0] - px, orte.get(k)[1] - pz) < ZUSAMMEN) {
                    id = ids.get(k);
                    break;
                }
            }
            if (id == null) {
                id = String.format(Locale.ROOT, "srw_%d_%d", (int) Math.floor(px), (int) Math.floor(pz));
                schilder.put(id, new ArrayList<>());
                orte.add(new double[]{px, pz, j[1]});
                ids.add(id);
            }
            List<WegweiserForm.Schild> liste = schilder.get(id);
            hinzu(liste, new WegweiserForm.Schild(WegweiserForm.aufschrift(ziel(s.name()), laenge(s.punkte(), 0)), bx + (e == null ? 0.5 - (px - j[0]) : 0),
                    bz + (e == null ? 0.5 - (pz - j[2]) : 0)));
            if (e != null) {
                List<int[]> ep = e.punkte();
                if (i + 8 < ep.size()) {
                    int[] w = ep.get(i + 8);
                    hinzu(liste, new WegweiserForm.Schild(WegweiserForm.aufschrift(ziel(e.name()), laenge(ep, i)), w[0] - j[0], w[2] - j[2]));
                }
                if (heim != null) {
                    int[] h = ep.get(Math.max(i - 8, 0));
                    hinzu(liste, new WegweiserForm.Schild(WegweiserForm.aufschrift(heim, bisSpawn(strassen, e, i, 0)), h[0] - j[0], h[2] - j[2]));
                }
            }
        }
        List<Pfahl> plan = new ArrayList<>();
        for (int k = 0; k < ids.size(); k++) {
            plan.add(new Pfahl(ids.get(k), orte.get(k)[0], orte.get(k)[1], (int) orte.get(k)[2], List.copyOf(schilder.get(ids.get(k)))));
        }
        return plan;
    }

    /** Ziel auf dem Schild: „Straße zur Villa von Max“ → „Villa von Max“. */
    private static String ziel(String strasse) {
        return strasse.replaceFirst("^Straße (zur|zum|nach|ins) ", "");
    }

    private static void hinzu(List<WegweiserForm.Schild> liste, WegweiserForm.Schild s) {
        String ziel = s.text().substring(0, s.text().indexOf("  ·"));
        if (liste.stream().noneMatch(a -> a.text().startsWith(ziel + "  ·"))) {
            liste.add(s);
        }
    }

    private static double laenge(List<int[]> p, int ab) {
        double l = 0;
        for (int i = Math.max(ab, 0) + 1; i < p.size(); i++) {
            l += Math.hypot(Math.hypot(p.get(i)[0] - p.get(i - 1)[0], p.get(i)[2] - p.get(i - 1)[2]), p.get(i)[1] - p.get(i - 1)[1]);
        }
        return l;
    }

    private static double bisSpawn(List<Strassen.Strasse> strassen, Strassen.Strasse s, int bis, int tiefe) {
        double l = laenge(s.punkte().subList(0, Math.min(bis, s.punkte().size() - 1) + 1), 0);
        if (s.eltern() < 0 || tiefe > 16) {
            return l;
        }
        Strassen.Strasse e = strassen.stream().filter(a -> a.villa() == s.eltern()).findFirst().orElse(null);
        return e == null ? l : l + bisSpawn(strassen, e, s.abzweig(), tiefe + 1);
    }

    // ---------------- Aufstellen ----------------

    private void aufstellen(World w, Pfahl p) {
        abbauen(w, p.id(), p.x(), p.z());
        int boden = w.getHighestBlockYAt((int) Math.floor(p.x()), (int) Math.floor(p.z()), HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
        int y = Math.abs(boden - (p.strassenY() + 1)) <= 4 ? boden : p.strassenY() + 1;
        Location fuss = new Location(w, p.x(), y, p.z());
        for (WegweiserForm.Teil t : WegweiserForm.teile(p.schilder())) {
            Transformation trafo = new Transformation(new Vector3f(t.versatz()[0], t.versatz()[1], t.versatz()[2]),
                    new Quaternionf(t.drehung()[0], t.drehung()[1], t.drehung()[2], t.drehung()[3]),
                    new Vector3f(t.groesse()[0], t.groesse()[1], t.groesse()[2]), new Quaternionf());
            if (t.text() == null) {
                w.spawn(fuss, BlockDisplay.class, d -> {
                    d.setBlock(Bukkit.createBlockData(t.block()));
                    d.setTransformation(trafo);
                    d.setViewRange(1.5F);
                    markieren(d, p.id());
                });
            } else {
                w.spawn(fuss, TextDisplay.class, d -> {
                    d.text(Component.text(t.text(), TextColor.fromHexString(WegweiserForm.SCHRIFTFARBE)).decorate(TextDecoration.BOLD));
                    d.setBackgroundColor(Color.fromARGB(0));
                    d.setShadowed(true);
                    d.setLineWidth(1000);
                    d.setAlignment(TextDisplay.TextAlignment.CENTER);
                    d.setTransformation(trafo);
                    d.setViewRange(1.0F);
                    markieren(d, p.id());
                });
            }
        }
        plugin.getLogger().info("Wegweiser bei " + (int) Math.floor(p.x()) + " " + y + " " + (int) Math.floor(p.z()) + " (" + p.schilder().size() + " Schilder)");
    }

    private static void markieren(Entity e, String id) {
        e.addScoreboardTag(TAG);
        e.addScoreboardTag(id);
        e.setPersistent(true);
    }

    private static void abbauen(World w, String id, double x, double z) {
        for (Entity e : w.getNearbyEntities(new Location(w, x, w.getMinHeight() + (w.getMaxHeight() - w.getMinHeight()) / 2.0, z),
                2, (w.getMaxHeight() - w.getMinHeight()) / 2.0, 2)) {
            if (e.getScoreboardTags().contains(id) && e.getScoreboardTags().contains(TAG)) {
                e.remove();
            }
        }
    }

    private void speichern() {
        JsonObject o = new JsonObject();
        gesetzt.forEach(o::addProperty);
        try {
            Files.createDirectories(datei.getParent());
            Files.writeString(datei, GSON.toJson(o), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plugin.getLogger().warning("Wegweiser: wegweiser.json nicht geschrieben: " + ex.getMessage());
        }
    }
}
