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
 * der Server eine Straße bis zum Ende ihrer Zufahrt – vom Ende der Zufahrt der Spawn-Villa oder, wenn eine fertige
 * Straße schon deutlich näher am Ziel vorbeiführt, als Abzweig von dieser (sonst liefen zwei Straßen nebeneinander her
 * und die zweite bliebe an der ersten hängen). Liegt das Ziel hinter einer Villa, geht es erst seitlich an ihr vorbei.
 * Gebaut wird immer nur eine Straße zur Zeit, Stück für Stück. Die Mittellinien fertiger Straßen werden gespeichert
 * ({@code strassen.json}); auf ihnen fährt James als Chauffeur.
 */
public final class Strassen {
    /**
     * Eine fertige Straße: Name, Nummer der Villa am Ende, Straße, von der sie abzweigt (Nummer ihrer Villa, -1 = beginnt
     * an der Spawn-Villa), Punkt des Abzweigs auf jener, Mittellinie (x, y = Fahrbahnblock, z).
     */
    public record Strasse(String name, int villa, int eltern, int abzweig, List<int[]> punkte) {
    }

    private static final Gson GSON = new GsonBuilder().create();

    private final StinkendReichPaper plugin;
    private final Path datei;
    private final List<Strasse> strassen = new ArrayList<>();
    private final ArrayDeque<int[]> warteschlange = new ArrayDeque<>();
    private StrassenBau aktiv;
    private int aktivVilla = -1, aktivEltern = -1, aktivAbzweig;

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
            strassen.add(new Strasse(aktiv.name(), aktivVilla, aktivEltern, aktivAbzweig, List.copyOf(aktiv.punkte())));
            speichern();
            plugin.getLogger().info("Straßen: " + aktiv.name() + " fertig, " + aktiv.punkte().size() + " Punkte"
                    + (v == null ? "" : ""));
            aktiv = null;
            aktivVilla = -1;
        }
    }

    /** Wegpunkte von der Spawn-Villa (bzw. vom Abzweig) zur Villa {@code nr}, seitlich an den Villen vorbei, wo nötig. */
    private StrassenBau starten(int nr) {
        Villen.Villa start = plugin.villen().nr(0), ziel = plugin.villen().nr(nr);
        if (start == null || ziel == null || start.world() == null || !start.welt().equals(ziel.welt())) {
            return null;
        }
        Bauplan plan = plugin.bauplan();
        Location a = villaZiel(start), b = villaZiel(ziel);
        // Abzweig: der Punkt einer fertigen Straße, der dem Ziel am nächsten liegt – wenn er deutlich näher ist als der
        // Anfang an der Spawn-Villa (nicht auf einem Grundstück und nicht auf einem freigehaltenen Platz)
        aktivEltern = -1;
        aktivAbzweig = 0;
        double ax = a.getX(), ay = a.getY(), az = a.getZ();
        double beste = Math.hypot(b.getX() - ax, b.getZ() - az) - 50;
        for (Strasse s : strassen) {
            Villen.Villa sv = plugin.villen().nr(s.villa());
            if (sv == null || !sv.welt().equals(ziel.welt())) {
                continue;
            }
            List<int[]> pkt = s.punkte();
            for (int j = 8; j < pkt.size() - 8; j += 4) {
                int[] q = pkt.get(j);
                double d = Math.hypot(q[0] + 0.5 - b.getX(), q[2] + 0.5 - b.getZ());
                if (d < beste && abzweigErlaubt(a.getWorld(), q)) {
                    beste = d;
                    aktivEltern = s.villa();
                    aktivAbzweig = j;
                    ax = q[0] + 0.5;
                    ay = q[1] + 1;
                    az = q[2] + 0.5;
                }
            }
        }
        List<double[]> wege = new ArrayList<>();
        double cx = ax, cz = az;
        double sued0 = start.z() + plan.breiteZ;
        if (aktivEltern < 0 && b.getZ() < sued0 - 10) {
            // Ziel liegt hinter der Spawn-Villa: erst seitlich an ihr vorbei
            boolean westen = b.getX() < start.x() + plan.breiteX / 2.0;
            cx = westen ? start.x() - 64 : start.x() + plan.breiteX + 64;
            cz = sued0 + 24;
            wege.add(new double[]{cx, cz, sued0 + 12, westen ? start.x() - 8 : start.x() + plan.breiteX + 8, westen ? -1 : 1});
        }
        double suedZiel = ziel.z() + plan.breiteZ;
        if (cz < suedZiel - 10) {
            // Die Straße käme von hinten an die Zielvilla: seitlich herum zur Zufahrt
            boolean westen = cx < ziel.x() + plan.breiteX / 2.0;
            double sx = westen ? ziel.x() - 64 : ziel.x() + plan.breiteX + 64;
            wege.add(new double[]{sx, suedZiel + 24, suedZiel + 12, westen ? ziel.x() - 8 : ziel.x() + plan.breiteX + 8, westen ? -1 : 1});
        }
        wege.add(new double[]{b.getX(), b.getZ()});
        String name = "Straße zur " + ziel.name();
        plugin.getLogger().info(String.format(Locale.ROOT, "Straßen: baue %s von %d %d %d über %d Wegpunkte%s", name,
                (int) Math.floor(ax), (int) Math.floor(ay), (int) Math.floor(az), wege.size(),
                aktivEltern < 0 ? "" : " (Abzweig von der Straße zur Villa " + aktivEltern + ", Punkt " + aktivAbzweig + ")"));
        // Richtung am Anfang: an der Spawn-Villa nach Süden (die Zufahrt), am Abzweig zum ersten Wegpunkt
        float richtung = aktivEltern < 0 ? 0.0F
                : (float) Math.toDegrees(Math.atan2(-(wege.getFirst()[0] - ax), wege.getFirst()[1] - az));
        // Fahrbahnhöhe am Ziel: villaZiel liefert die Stehhöhe über dem Ende der Zufahrt, drei Blöcke dahinter (ohne
        // gefundene Zufahrt den Spawnpunkt: Höhe unbekannt)
        Block ende = b.getWorld().getBlockAt(b.getBlockX(), b.getBlockY(), (int) Math.floor(b.getZ() - 3));
        double zielY = ende.getRelative(0, -1, 0).getType() == Material.GRAY_CONCRETE
                || ende.getType() == Material.POLISHED_ANDESITE_SLAB ? b.getBlockY() - 1 : Double.NaN;
        plugin.getLogger().info(String.format(Locale.ROOT, "Straßen: Ziel %d %d %d, Fahrbahnhöhe dort %s", b.getBlockX(),
                b.getBlockY(), b.getBlockZ(), Double.isNaN(zielY) ? "unbekannt" : String.valueOf((int) zielY)));
        return new StrassenBau(plugin, a.getWorld(), name, ax, ay, az, richtung, wege, zielY);
    }

    /** Darf hier eine neue Straße abzweigen? Nicht auf einem Grundstück (samt Rand) und nicht auf einem freien Platz. */
    private boolean abzweigErlaubt(World w, int[] q) {
        Location l = new Location(w, q[0] + 0.5, q[1], q[2] + 0.5);
        return plugin.villen().bei(l, 16) == null && plugin.bauleitung().reservierterPlatz(w, q[0], q[2]) == null;
    }

    /**
     * Ganze Strecke von der Spawn-Villa bis vor die Villa {@code nr}: bei einem Abzweig erst die Straße, von der sie
     * abgeht, bis zum Abzweig, dann sie selbst. Leer für die Spawn-Villa, null ohne Straße.
     */
    public List<int[]> strecke(int nr) {
        return strecke(nr, 0);
    }

    private List<int[]> strecke(int nr, int tiefe) {
        if (nr == 0) {
            return new ArrayList<>();
        }
        Strasse s = zur(nr);
        if (s == null) {
            return null;
        }
        List<int[]> weg = new ArrayList<>();
        Strasse e = s.eltern() < 0 || tiefe > 16 ? null : zur(s.eltern());
        List<int[]> vorher = e == null ? null : strecke(s.eltern(), tiefe + 1);
        if (vorher != null) {
            // Die Strecke der Elternstraße endet mit ihrer eigenen Mittellinie: bis zum Abzweigpunkt nehmen
            int bis = vorher.size() - e.punkte().size() + Math.min(s.abzweig(), e.punkte().size() - 1);
            weg.addAll(vorher.subList(0, bis + 1));
        }
        weg.addAll(s.punkte());
        return weg;
    }

    /**
     * Fahrweg von der Villa {@code von} zur Villa {@code nach} über das Netz: zurück bis dorthin, wo sich die beiden
     * Strecken trennen, dann hin. Null, wenn eine der beiden Villen noch keine Straße hat.
     */
    public List<int[]> fahrweg(int von, int nach) {
        List<int[]> a = strecke(von), b = strecke(nach);
        if (a == null || b == null) {
            return null;
        }
        int gemeinsam = 0;
        while (gemeinsam < a.size() && gemeinsam < b.size() && java.util.Arrays.equals(a.get(gemeinsam), b.get(gemeinsam))) {
            gemeinsam++;
        }
        List<int[]> weg = new ArrayList<>();
        for (int i = a.size() - 1; i >= Math.max(0, gemeinsam - 1); i--) {
            weg.add(a.get(i));
        }
        for (int i = gemeinsam; i < b.size(); i++) {
            weg.add(b.get(i));
        }
        return weg;
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
                // Straßen von vor 0.4.2 haben keinen Abzweig: sie beginnen an der Spawn-Villa
                strassen.add(new Strasse(s.get("name").getAsString(), s.get("villa").getAsInt(),
                        s.has("eltern") ? s.get("eltern").getAsInt() : -1, s.has("abzweig") ? s.get("abzweig").getAsInt() : 0, punkte));
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
            j.addProperty("eltern", s.eltern());
            j.addProperty("abzweig", s.abzweig());
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
