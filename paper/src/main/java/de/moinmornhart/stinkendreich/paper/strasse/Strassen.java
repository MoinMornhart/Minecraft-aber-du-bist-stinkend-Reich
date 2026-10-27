package de.moinmornhart.stinkendreich.paper.strasse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import de.moinmornhart.stinkendreich.paper.villa.Ausstattung;
import de.moinmornhart.stinkendreich.paper.villa.Bauplan;
import de.moinmornhart.stinkendreich.paper.villa.Villen;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Straßen zwischen den Villen auf dem Paper-Server (wie das Straßennetz der Mod): Sobald eine Nachbarvilla steht, baut
 * der Server eine Straße vom Ende der Zufahrt der Spawn-Villa bis zum Ende ihrer Zufahrt – liegt das Ziel hinter einer
 * Villa, erst seitlich an ihr vorbei. Gebaut wird immer nur eine Straße zur Zeit, Stück für Stück. Die Mittellinien
 * fertiger Straßen werden gespeichert ({@code strassen.json}); auf ihnen fährt James als Chauffeur.
 */
public final class Strassen {
    /** Eine fertige Straße: Name, Nummer der Villa am Ende, Mittellinie (x, y = Fahrbahnblock, z). */
    public record Strasse(String name, int villa, List<int[]> punkte) {
    }

    private static final Gson GSON = new GsonBuilder().create();

    private final StinkendReichPaper plugin;
    private final Path datei;
    private final List<Strasse> strassen = new ArrayList<>();
    private final ArrayDeque<int[]> warteschlange = new ArrayDeque<>();
    private StrassenBau aktiv;
    private int aktivVilla = -1;

    public Strassen(StinkendReichPaper plugin) {
        this.plugin = plugin;
        this.datei = plugin.getDataFolder().toPath().resolve("strassen.json");
        laden();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 1L);
        // Villen von früher ohne Straße (etwa vor diesem Update gebaut) bekommen ihre nach dem Start
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.villen().alle().forEach(this::zurVilla), 20L * 30);
    }

    public List<Strasse> alle() {
        return List.copyOf(strassen);
    }

    /** Die Straße, die zu dieser Villa führt, oder null. */
    public Strasse zur(int villa) {
        return strassen.stream().filter(s -> s.villa() == villa).findFirst().orElse(null);
    }

    /** Stand für /villa strassen. */
    public String uebersicht() {
        StringBuilder sb = new StringBuilder();
        strassen.forEach(s -> sb.append(s.name()).append(" (").append(s.punkte().size()).append(" Punkte); "));
        if (aktiv != null) {
            sb.append("im Bau: ").append(aktiv.name()).append(" ").append(aktiv.prozent()).append(" %; ");
        }
        sb.append(warteschlange.size()).append(" in der Warteschlange");
        return sb.toString();
    }

    /** Merkt die Straße zu einer Villa vor (nicht zur Spawn-Villa selbst, nicht doppelt). */
    public void zurVilla(Villen.Villa v) {
        if (v.nr() == 0 || zur(v.nr()) != null || aktivVilla == v.nr()
                || warteschlange.stream().anyMatch(w -> w[0] == v.nr())) {
            return;
        }
        warteschlange.add(new int[]{v.nr()});
        plugin.getLogger().info("Straßen: Straße zur " + v.name() + " vorgemerkt");
    }

    /** Baut die Straße zu einer Villa neu (Testhilfe: alte Mittellinie vergessen). */
    public void neu(Villen.Villa v) {
        strassen.removeIf(s -> s.villa() == v.nr());
        zurVilla(v);
    }

    private void tick() {
        if (aktiv == null) {
            int[] naechste = warteschlange.poll();
            if (naechste == null) {
                return;
            }
            aktiv = starten(naechste[0]);
            aktivVilla = aktiv == null ? -1 : naechste[0];
            return;
        }
        aktiv.tick();
        if (aktiv.fertig()) {
            Villen.Villa v = plugin.villen().nr(aktivVilla);
            strassen.add(new Strasse(aktiv.name(), aktivVilla, List.copyOf(aktiv.punkte())));
            speichern();
            plugin.getLogger().info("Straßen: " + aktiv.name() + " fertig, " + aktiv.punkte().size() + " Punkte"
                    + (v == null ? "" : ""));
            aktiv = null;
            aktivVilla = -1;
        }
    }

    /** Wegpunkte von der Spawn-Villa zur Villa {@code nr}, seitlich an den Villen vorbei, wo nötig. */
    private StrassenBau starten(int nr) {
        Villen.Villa start = plugin.villen().nr(0), ziel = plugin.villen().nr(nr);
        if (start == null || ziel == null || start.world() == null || !start.welt().equals(ziel.welt())) {
            return null;
        }
        Bauplan plan = plugin.bauplan();
        Location a = villaZiel(start), b = villaZiel(ziel);
        List<double[]> wege = new ArrayList<>();
        double cx = a.getX(), cz = a.getZ();
        double sued0 = start.z() + plan.breiteZ;
        if (b.getZ() < sued0 - 10) {
            // Ziel liegt hinter der Spawn-Villa: erst seitlich an ihr vorbei
            cx = b.getX() < start.x() + plan.breiteX / 2.0 ? start.x() - 64 : start.x() + plan.breiteX + 64;
            cz = sued0 + 24;
            wege.add(new double[]{cx, cz});
        }
        double suedZiel = ziel.z() + plan.breiteZ;
        if (cz < suedZiel - 10) {
            // Die Straße käme von hinten an die Zielvilla: seitlich herum zur Zufahrt
            double sx = cx < ziel.x() + plan.breiteX / 2.0 ? ziel.x() - 64 : ziel.x() + plan.breiteX + 64;
            wege.add(new double[]{sx, suedZiel + 24});
        }
        wege.add(new double[]{b.getX(), b.getZ()});
        String name = "Straße zur " + ziel.name();
        plugin.getLogger().info(String.format(Locale.ROOT, "Straßen: baue %s von %d %d %d über %d Wegpunkte", name,
                a.getBlockX(), a.getBlockY(), a.getBlockZ(), wege.size()));
        return new StrassenBau(plugin, a.getWorld(), name, a.getX(), a.getY(), a.getZ(), 0.0F, wege);
    }

    /** Anfang der Straße vor einer Villa: drei Blöcke hinter dem Ende ihrer Zufahrt, auf Fahrbahnhöhe. */
    public Location villaZiel(Villen.Villa v) {
        World w = v.world();
        Bauplan plan = plugin.bauplan();
        int mitte = v.x() + (Ausstattung.ZUFAHRT_X0 + Ausstattung.ZUFAHRT_X1) / 2;
        int start = v.z() + plan.breiteZ;
        Location ende = null;
        for (int i = 0; i < 80; i++) {
            int z = start + i;
            w.getChunkAt(mitte >> 4, z >> 4).load();
            int oben = w.getHighestBlockYAt(mitte, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Block s = w.getBlockAt(mitte, oben, z);
            if (s.getType() == Material.GRAY_CONCRETE || s.getType() == Material.POLISHED_ANDESITE_SLAB) {
                ende = new Location(w, mitte + 0.5, s.getType() == Material.GRAY_CONCRETE ? oben + 1 : oben, z + 3.5);
            } else if (ende != null) {
                break;
            }
        }
        return ende != null ? ende : v.spawn(plan);
    }

    public void beenden() {
        if (aktiv != null) {
            aktiv.freigeben();
        }
    }

    // ------------------------------------------------------------------
    // Speichern
    // ------------------------------------------------------------------

    private void laden() {
        if (!Files.exists(datei)) {
            return;
        }
        try {
            JsonObject j = GSON.fromJson(Files.readString(datei, StandardCharsets.UTF_8), JsonObject.class);
            for (var e : j.getAsJsonArray("strassen")) {
                JsonObject s = e.getAsJsonObject();
                List<int[]> punkte = new ArrayList<>();
                JsonArray p = s.getAsJsonArray("punkte");
                for (int i = 0; i + 2 < p.size(); i += 3) {
                    punkte.add(new int[]{p.get(i).getAsInt(), p.get(i + 1).getAsInt(), p.get(i + 2).getAsInt()});
                }
                strassen.add(new Strasse(s.get("name").getAsString(), s.get("villa").getAsInt(), punkte));
            }
        } catch (IOException | RuntimeException ex) {
            plugin.getLogger().warning("Straßen: strassen.json nicht lesbar: " + ex.getMessage());
        }
    }

    private void speichern() {
        JsonArray liste = new JsonArray();
        for (Strasse s : strassen) {
            JsonObject j = new JsonObject();
            j.addProperty("name", s.name());
            j.addProperty("villa", s.villa());
            JsonArray p = new JsonArray();
            for (int[] q : s.punkte()) {
                p.add(q[0]);
                p.add(q[1]);
                p.add(q[2]);
            }
            j.add("punkte", p);
            liste.add(j);
        }
        JsonObject alles = new JsonObject();
        alles.add("strassen", liste);
        try {
            Files.createDirectories(datei.getParent());
            Files.writeString(datei, GSON.toJson(alles), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plugin.getLogger().warning("Straßen: strassen.json nicht geschrieben: " + ex.getMessage());
        }
    }
}
