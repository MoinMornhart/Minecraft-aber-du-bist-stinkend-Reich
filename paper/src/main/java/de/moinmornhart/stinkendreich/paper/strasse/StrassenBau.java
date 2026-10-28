package de.moinmornhart.stinkendreich.paper.strasse;

import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import de.moinmornhart.stinkendreich.paper.block.Zustand;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Baut eine Straße Stück für Stück (wie {@code StrassenBau} in der Mod, für den Paper-Server übertragen): in jedem Tick
 * einige Schritte mit Zeitbudget, 7 Blöcke breit, grauer Asphalt, weiße Mittellinie in Strichen, Bordstein aus glattem
 * Stein, alle 32 Blöcke eine Laterne. Jeder Block neben der Mittellinie bekommt sein Material nach dem Abstand zur Linie
 * (Fahrbahn bis 2,5, Bordstein bis 3,5, Seitenstreifen bis 4,5), so bleibt die Straße auch schräg und in Kurven gleich
 * breit. Ein Block wird erst gesetzt, wenn die Linie an ihm vorbei ist. Sie folgt sanft dem Gelände (höchstens ein
 * Block Höhe auf drei Blöcke, Übergänge mit Stufen), schneidet offen durch Hügel, schüttet kleine Senken auf und führt
 * als Brücke mit Geländer und Pfeilern über Wasser und tiefe Täler.
 *
 * <p>Nie angerührt werden: die Villen, Blöcke der Mod und alles, was nicht natürlich ist (Häuser im Dorf,
 * Spielerbauten). Liegt so etwas im Weg, biegt die Straße aus. Die Straße fährt mehrere Wegpunkte nacheinander an
 * (seitlich an einer Villa vorbei, dann zur Zufahrt der nächsten).</p>
 */
final class StrassenBau {
    static final int HALB = 3;
    private static final int LATERNE_ALLE = 32;
    private static final int STRICH = 4;
    private static final int FERTIG_NACH = 12;
    private static final int AUFSCHUETTEN_BIS = 6;
    private static final int PRO_TICK = 8;
    private static final long BUDGET = 25_000_000L;
    private static final float KURVE = 4.0F;
    private static final int UMKREIS = 2;

    static final BlockData ASPHALT = Material.GRAY_CONCRETE.createBlockData();
    private static final BlockData ASPHALT_STUFE = Material.POLISHED_DEEPSLATE_SLAB.createBlockData();
    private static final BlockData LINIE = Material.WHITE_CONCRETE.createBlockData();
    private static final BlockData BORD = Material.SMOOTH_STONE.createBlockData();
    private static final BlockData BORD_STUFE = Material.SMOOTH_STONE_SLAB.createBlockData();
    private static final BlockData UNTERBAU = Material.STONE_BRICKS.createBlockData();
    private static final BlockData GELAENDER = Material.STONE_BRICK_WALL.createBlockData();
    private static final BlockData MAST = Material.POLISHED_BLACKSTONE_WALL.createBlockData();
    private static final BlockData LAMPE = Material.LANTERN.createBlockData();
    private static final BlockData LUFT = Material.AIR.createBlockData();

    private final StinkendReichPaper plugin;
    private final World welt;
    private final String name;
    /** Wegpunkte (x, z), der letzte ist das Ziel. */
    private final List<double[]> ziele;
    private final int maxSchritte;
    private double x, z;
    private float richtung;
    private int y;
    private int schritte, letzteAenderung;
    private boolean fertig;
    /** Mittellinie (x, y, z je Schritt; y = Fahrbahnblock). */
    private final List<int[]> punkte = new ArrayList<>();
    private final List<double[]> linie = new ArrayList<>();
    /** Noch offene Spalten: Abstand zur Linie, nächster Schritt, zuletzt berührt. */
    private final Map<Long, double[]> offen = new HashMap<>();
    private final Map<Long, Double> gebaut = new HashMap<>();
    private final List<int[]> laternen = new ArrayList<>();
    private final Map<Long, int[]> bodenCache = new HashMap<>();
    private final double startAbstand;
    private String letzteSperre = "";
    private final Set<Long> gehalten = new HashSet<>();
    private final Set<Long> angefordert = new HashSet<>();
    private int wartet;

    StrassenBau(StinkendReichPaper plugin, World welt, String name, double startX, double startY, double startZ, float richtung,
                List<double[]> ziele) {
        this.plugin = plugin;
        this.welt = welt;
        this.name = name;
        this.x = startX;
        this.z = startZ;
        this.y = (int) Math.floor(startY) - 1;
        this.richtung = richtung;
        this.ziele = new ArrayList<>(ziele);
        double weg = 0, px = x, pz = z;
        for (double[] p : ziele) {
            weg += Math.hypot(p[0] - px, p[1] - pz);
            px = p[0];
            pz = p[1];
        }
        this.startAbstand = weg;
        // Im Gebirge schlängelt sie sich (Kehren statt steiler Rampen): reichlich Schritte erlauben
        this.maxSchritte = (int) (weg * 2.6) + 300;
    }

    String name() {
        return name;
    }

    boolean fertig() {
        return fertig;
    }

    /** Mittellinie: x, y (Fahrbahnblock), z je Schritt. */
    List<int[]> punkte() {
        return punkte;
    }

    int prozent() {
        if (fertig) {
            return 100;
        }
        double rest = 0, px = x, pz = z;
        for (double[] p : ziele) {
            rest += Math.hypot(p[0] - px, p[1] - pz);
            px = p[0];
            pz = p[1];
        }
        return (int) Math.max(0, Math.min(99, 100 - rest * 100 / Math.max(1, startAbstand)));
    }

    // ------------------------------------------------------------------
    // Ablauf
    // ------------------------------------------------------------------

    void tick() {
        long start = System.nanoTime();
        for (int i = 0; i < PRO_TICK && !fertig && System.nanoTime() - start < BUDGET; i++) {
            if (!umgebungBereit()) {
                return;
            }
            schritt();
        }
    }

    /** Fordert die Chunks rund um die Spitze im Hintergrund an (mit Lade-Ticket) und meldet, ob alle schon da sind. */
    private boolean umgebungBereit() {
        int cx0 = (int) Math.floor(x) >> 4, cz0 = (int) Math.floor(z) >> 4;
        boolean alle = true;
        for (int cx = cx0 - UMKREIS; cx <= cx0 + UMKREIS; cx++) {
            for (int cz = cz0 - UMKREIS; cz <= cz0 + UMKREIS; cz++) {
                long k = schluessel(cx, cz);
                if (angefordert.add(k)) {
                    int fx = cx, fz = cz;
                    welt.getChunkAtAsync(cx, cz).thenAccept(c -> {
                        if (angefordert.contains(schluessel(fx, fz)) && !fertig) {
                            c.addPluginChunkTicket(plugin);
                            gehalten.add(schluessel(fx, fz));
                        }
                    });
                }
                if (!welt.isChunkLoaded(cx, cz)) {
                    alle = false;
                }
            }
        }
        // Weit hinten liegende Chunks wieder freigeben (die Straße dahinter ist fertig)
        angefordert.removeIf(k -> {
            int cx = (int) (k >> 32), cz = (int) (long) k;
            if (Math.max(Math.abs(cx - cx0), Math.abs(cz - cz0)) > UMKREIS + 2) {
                if (gehalten.remove(k)) {
                    welt.removePluginChunkTicket(cx, cz, plugin);
                }
                return true;
            }
            return false;
        });
        if (alle) {
            wartet = 0;
            return true;
        }
        return ++wartet > 20 * 30;
    }

    private static long schluessel(int a, int b) {
        return ((long) a << 32) | (b & 0xFFFFFFFFL);
    }

    void freigeben() {
        for (long k : gehalten) {
            welt.removePluginChunkTicket((int) (k >> 32), (int) (long) k, plugin);
        }
        gehalten.clear();
        angefordert.clear();
    }

    private void schritt() {
        double[] ziel = ziele.getFirst();
        double rest = Math.hypot(ziel[0] - x, ziel[1] - z);
        if (rest < 5 && ziele.size() > 1) {
            ziele.removeFirst();                            // Wegpunkt erreicht: weiter zum nächsten
            plugin.getLogger().info(String.format(Locale.ROOT, "Straße %s: Wegpunkt erreicht bei %d %d %d", name, (int) x, y, (int) z));
            return;
        }
        if (rest < 5 || schritte > maxSchritte) {
            ende(rest < 5 ? "am Ziel" : "zu viele Schritte");
            return;
        }
        float zum = gier(x, z, ziel[0], ziel[1]);
        float wunsch = richtung + clamp(wrap(zum - richtung), -KURVE, KURVE);
        Float frei = null;
        double besteKosten = Double.MAX_VALUE;
        // Steile Hänge umfahren wie eine Bergstraße – aber nicht endlos: Je länger der Weg schon ist, desto eher geht es
        // geradeaus durch den Berg (offener Einschnitt) bzw. als Brücke übers Tal
        double strenge = Math.max(0.1, 1.0 - schritte / (startAbstand * 1.4));
        for (float abw : new float[]{0, 15, -15, 30, -30, 50, -50, 75, -75}) {
            float r = wunsch + Math.copySign(Math.min(Math.abs(abw), 90), abw);
            if (!frei(r)) {
                continue;
            }
            double steigung = Math.max(0, steigung(r) - 3);
            double kosten = steigung * 4 * strenge + Math.abs(abw) * 0.12;
            if (kosten < besteKosten) {
                besteKosten = kosten;
                frei = r;
            }
            if (steigung == 0) {
                break;
            }
        }
        if (frei == null) {
            ende("kein freier Weg (Rest " + (int) rest + ", Sperre " + letzteSperre + ")");
            return;
        }
        richtung = richtung + clamp(wrap(frei - richtung), -1.5F * KURVE, 1.5F * KURVE);
        x += -Math.sin(Math.toRadians(richtung));
        z += Math.cos(Math.toRadians(richtung));

        // Höhe: Mittel des Geländes der nächsten Blöcke, höchstens ein Block Wechsel auf drei Blöcke
        double summe = 0;
        int n = 0;
        int ueberWasser = Integer.MIN_VALUE;
        for (int d = 0; d <= 12; d += 2) {
            int bx = (int) Math.floor(x - Math.sin(Math.toRadians(richtung)) * d);
            int bz = (int) Math.floor(z + Math.cos(Math.toRadians(richtung)) * d);
            int[] b = boden(bx, bz);
            summe += b[1] == 1 ? b[0] + 1 : b[0];
            n++;
            if (b[1] == 1) {
                ueberWasser = Math.max(ueberWasser, b[0] + 1);
            }
        }
        double soll = Math.max(summe / n, ueberWasser);
        if (schritte - letzteAenderung >= 3 && Math.abs(soll - y) > 0.75) {
            y += soll > y ? 1 : -1;
            letzteAenderung = schritte;
        }
        weiter();
        schritte++;
    }

    private int steigung(float r) {
        double rx = -Math.sin(Math.toRadians(r)), rz = Math.cos(Math.toRadians(r));
        int max = 0;
        for (int d = 4; d <= 16; d += 4) {
            int[] b = boden((int) Math.floor(x + rx * d), (int) Math.floor(z + rz * d));
            int boden = b[1] == 1 ? b[0] + 1 : b[0];
            max = Math.max(max, Math.abs(boden - y) - d / 3);
        }
        return max;
    }

    /** Ist die Richtung frei (die nächsten zehn Blöcke kein geschützter Block in Fahrbahnhöhe)? */
    private boolean frei(float r) {
        double rx = -Math.sin(Math.toRadians(r)), rz = Math.cos(Math.toRadians(r));
        for (int d = 2; d <= 10; d += 2) {
            for (int o = -HALB; o <= HALB; o += HALB) {
                int bx = (int) Math.floor(x + rx * d + rz * o), bz = (int) Math.floor(z + rz * d - rx * o);
                for (int h = 0; h <= 4; h++) {
                    Block b = welt.getBlockAt(bx, y + h, bz);
                    if (h >= 1 && o == 0 && unterbauOderFahrbahn(b.getType())) {
                        letzteSperre = "andere Straße darüber bei " + bx + " " + (y + h) + " " + bz;
                        return false;
                    }
                    if (geschuetzt(b)) {
                        letzteSperre = b.getType().getKey().getKey() + " bei " + bx + " " + (y + h) + " " + bz;
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void weiter() {
        int i = punkte.size();
        punkte.add(new int[]{(int) Math.floor(x), y, (int) Math.floor(z)});
        linie.add(new double[]{x, z});
        if (i > 0) {
            double[] a = linie.get(i - 1), b = linie.get(i);
            int r = HALB + 2;
            for (int bx = (int) Math.floor(Math.min(a[0], b[0])) - r; bx <= (int) Math.floor(Math.max(a[0], b[0])) + r; bx++) {
                for (int bz = (int) Math.floor(Math.min(a[1], b[1])) - r; bz <= (int) Math.floor(Math.max(a[1], b[1])) + r; bz++) {
                    long k = schluessel(bx, bz);
                    double vx = b[0] - a[0], vz = b[1] - a[1], wx = bx + 0.5 - a[0], wz = bz + 0.5 - a[1];
                    double t = (wx * vx + wz * vz) / Math.max(1e-6, vx * vx + vz * vz);
                    if (i == 1 && t < 0) {
                        continue;
                    }
                    t = Math.max(0, Math.min(1, t));
                    double d = Math.hypot(wx - vx * t, wz - vz * t);
                    Double schon = gebaut.get(k);
                    if (d > HALB + 1.5 || (schon != null && schon <= d + 0.01)) {
                        continue;
                    }
                    int naechster = t < 0.5 ? i - 1 : i;
                    double[] alt = offen.get(k);
                    if (alt == null || d < alt[0]) {
                        offen.put(k, new double[]{d, naechster, i});
                    } else {
                        alt[2] = i;
                    }
                }
            }
        }
        if (i % LATERNE_ALLE == LATERNE_ALLE / 2) {
            double rx = -Math.sin(Math.toRadians(richtung)), rz = Math.cos(Math.toRadians(richtung));
            int seite = (i / LATERNE_ALLE) % 2 == 0 ? 1 : -1;
            laternen.add(new int[]{i, (int) Math.floor(x + rz * (HALB + 1) * seite), (int) Math.floor(z - rx * (HALB + 1) * seite)});
        }
        bauen(i - FERTIG_NACH);
    }

    private void bauen(int bis) {
        var it = offen.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            double[] v = e.getValue();
            if (v[2] <= bis) {
                long k = e.getKey();
                spalte((int) (k >> 32), (int) (long) k, v[0], (int) v[1]);
                gebaut.put(k, v[0]);
                it.remove();
            }
        }
        while (!laternen.isEmpty() && laternen.getFirst()[0] <= bis - FERTIG_NACH) {
            int[] l = laternen.removeFirst();
            laterne(l[1], l[2]);
        }
    }

    private void ende(String grund) {
        plugin.getLogger().info(String.format(Locale.ROOT, "Straße %s: Ende nach %d Schritten bei %d %d %d: %s", name, schritte,
                (int) x, y, (int) z, grund));
        fertig = true;
        bauen(Integer.MAX_VALUE);
        freigeben();
    }

    /** Eine Spalte im Abstand {@code d} zur Mittellinie (nächster Schritt {@code i}). */
    private void spalte(int bx, int bz, double d, int i) {
        int y = punkte.get(i)[1];
        int[] b = boden(bx, bz);
        int grund = b[0];
        boolean wasser = b[1] == 1;
        boolean fahrbahn = d <= HALB - 0.5, bord = !fahrbahn && d <= HALB + 0.5, seite = !fahrbahn && !bord;
        boolean bruecke = wasser || y - grund > AUFSCHUETTEN_BIS;
        Block oben = welt.getBlockAt(bx, y, bz);
        Material vorher = oben.getType();
        boolean andereFahrbahn = vorher == Material.GRAY_CONCRETE || vorher == Material.WHITE_CONCRETE
                || vorher == Material.POLISHED_DEEPSLATE_SLAB;
        boolean stufe = (i + 1 < punkte.size() && punkte.get(i + 1)[1] > y) || (i > 0 && punkte.get(i - 1)[1] > y);
        boolean gelaender = bord && bruecke;
        if (fahrbahn) {
            Material ueber = oben.getRelative(BlockFace.UP).getType();
            if (ueber == Material.STONE_BRICK_WALL || ueber == Material.SMOOTH_STONE_SLAB || ueber == Material.POLISHED_BLACKSTONE_WALL) {
                for (int h = 1; h <= 4 && istStrasse(oben.getRelative(0, h, 0).getType()); h++) {
                    oben.getRelative(0, h, 0).setBlockData(LUFT, false);
                }
            }
        }
        if (fahrbahn || (bord && !andereFahrbahn)) {
            boolean strich = d < 0.5 && (i / STRICH) % 2 == 0;
            setzen(oben, bord ? BORD : (strich && !stufe ? LINIE : ASPHALT));
            if (bruecke) {
                if (gelaender && i % 8 == 0) {
                    for (int h = y - 1; h >= Math.max(grund - (wasser ? 8 : 0), y - 64); h--) {
                        if (!setzen(welt.getBlockAt(bx, h, bz), UNTERBAU)) {
                            break;
                        }
                    }
                }
            } else {
                for (int h = y - 1; h > grund; h--) {
                    setzen(welt.getBlockAt(bx, h, bz), UNTERBAU);
                }
            }
            Block drueber = oben.getRelative(BlockFace.UP);
            if (gelaender) {
                setzen(drueber, GELAENDER);
            } else if (stufe) {
                abtragen(drueber);
            }
            if (!gelaender && stufe && (drueber.getType().isAir() || drueber.isReplaceable())) {
                setzen(drueber, bord ? BORD_STUFE : ASPHALT_STUFE);
            }
        } else if (seite && !bruecke && !andereFahrbahn && !istStrasse(vorher)) {
            for (int h = grund; h > y; h--) {
                abtragen(welt.getBlockAt(bx, h, bz));
            }
            if (grund < y && y - grund <= 3) {
                for (int h = grund + 1; h < y; h++) {
                    setzen(welt.getBlockAt(bx, h, bz), Material.DIRT.createBlockData());
                }
            }
            if ((grund >= y || y - grund <= 3) && Natur.natuerlich(oben.getType()) && !geschuetzt(oben)) {
                setzen(oben, Material.GRASS_BLOCK.createBlockData());
            }
        }
        // Darüber freiräumen: offener Einschnitt bis zum Himmel (höchstens 48 Blöcke), Bäume über der Fahrbahn ganz
        boolean besetzt = gelaender || (stufe && !seite);
        int himmel = Math.min(y + 48, Math.max(y + 6, welt.getHighestBlockYAt(bx, bz, HeightMap.WORLD_SURFACE)));
        for (int h = y + (besetzt ? 2 : 1); h <= himmel; h++) {
            abtragen(welt.getBlockAt(bx, h, bz));
        }
        if (!bruecke) {
            wasserAbdichten(bx, bz, y, seite, himmel);
        }
        if (!seite) {
            for (int h = y + 7; h <= y + 20; h++) {
                Block p = welt.getBlockAt(bx, h, bz);
                if (Tag.LEAVES.isTagged(p.getType()) || Tag.LOGS.isTagged(p.getType())) {
                    abtragen(p);
                }
            }
        }
    }

    private void laterne(int bx, int bz) {
        Double d = gebaut.get(schluessel(bx, bz));
        if (d != null && d <= HALB + 0.5) {
            return;
        }
        int y = hoeheBei(bx, bz);
        Block fuss = welt.getBlockAt(bx, y, bz);
        if (geschuetzt(fuss)) {
            return;
        }
        setzen(fuss, BORD);
        for (int h = y - 1; h > boden(bx, bz)[0] && h > y - 8; h--) {
            setzen(welt.getBlockAt(bx, h, bz), UNTERBAU);
        }
        for (int h = 1; h <= 3; h++) {
            setzen(fuss.getRelative(0, h, 0), MAST);
        }
        setzen(fuss.getRelative(0, 4, 0), LAMPE);
    }

    private int hoeheBei(int bx, int bz) {
        int[] best = punkte.getLast();
        double bd = Double.MAX_VALUE;
        for (int[] p : punkte) {
            double d = (p[0] - bx) * (double) (p[0] - bx) + (p[2] - bz) * (double) (p[2] - bz);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best[1];
    }

    // ------------------------------------------------------------------
    // Blöcke
    // ------------------------------------------------------------------

    /** Höhe des Bodens: oberster fester Block ohne Blätter, Stämme und Pflanzen. [1] = 1 bei Wasser oder Lava. */
    private int[] boden(int bx, int bz) {
        return bodenCache.computeIfAbsent(schluessel(bx, bz), k -> {
            int top = welt.getHighestBlockYAt(bx, bz, HeightMap.WORLD_SURFACE);
            for (int h = top; h > top - 48; h--) {
                Block b = welt.getBlockAt(bx, h, bz);
                Material m = b.getType();
                if (Natur.fluessig(m)) {
                    return new int[]{h, 1};
                }
                if (m.isAir() || Tag.LEAVES.isTagged(m) || Tag.LOGS.isTagged(m) || b.isReplaceable() || b.isPassable()) {
                    continue;
                }
                return new int[]{h, 0};
            }
            return new int[]{top, 0};
        });
    }

    /** Darf hier nichts verändert werden? Villen, Blöcke der Mod (außer Landminen) und alles Nicht-Natürliche. */
    private boolean geschuetzt(Block b) {
        // Freigehaltene Plätze für künftige Villen: drumherum, auch durch die Luft darüber (außer die Straße will genau
        // dorthin, etwa in ein Dorf)
        int[] platz = plugin.bauleitung().reservierterPlatz(welt, b.getX(), b.getZ());
        if (platz != null && !zielBei(platz)) {
            return true;
        }
        Material m = b.getType();
        if (m.isAir() || Natur.fluessig(m)) {
            return false;
        }
        Zustand z = plugin.bloecke().zustand(b);
        if (z != null) {
            return !z.name().equals("landmine");        // über Minen im Rasen der Festung darf die Straße hinweg
        }
        if (plugin.villen().bei(b.getLocation(), 0) != null) {
            return true;
        }
        return !Natur.natuerlich(m) && !istStrasse(m);
    }

    /** Liegt das Ziel dieser Straße auf (oder dicht an) einem freigehaltenen Platz? Dann darf sie hinein. */
    private boolean zielBei(int[] platz) {
        double[] ziel = ziele.getLast();
        int rand = 48;
        return ziel[0] >= platz[0] - rand && ziel[0] < platz[2] + rand && ziel[1] >= platz[1] - rand && ziel[1] < platz[3] + rand;
    }

    private static boolean unterbauOderFahrbahn(Material m) {
        return m == Material.GRAY_CONCRETE || m == Material.WHITE_CONCRETE || m == Material.SMOOTH_STONE || m == Material.STONE_BRICKS;
    }

    /** Teil einer (anderen) Straße: darf überbaut werden (Kreuzungen). */
    static boolean istStrasse(Material m) {
        return unterbauOderFahrbahn(m) || m == Material.STONE_BRICK_WALL || m == Material.POLISHED_DEEPSLATE_SLAB
                || m == Material.SMOOTH_STONE_SLAB || m == Material.POLISHED_BLACKSTONE_WALL || m == Material.LANTERN;
    }

    private boolean setzen(Block b, BlockData d) {
        if (geschuetzt(b)) {
            return false;
        }
        if (plugin.bloecke().zustand(b) != null) {
            plugin.bloecke().entfernen(b);                  // Landmine unter der Fahrbahn fällt weg
        }
        b.setBlockData(d, false);
        return true;
    }

    /** Wasser und Lava neben einer Straße im Einschnitt: Stützmauer, damit nichts auf die Fahrbahn läuft. */
    private void wasserAbdichten(int bx, int bz, int y, boolean seite, int bis) {
        for (int h = y; h <= Math.max(y + 6, bis); h++) {
            Block p = welt.getBlockAt(bx, h, bz);
            for (BlockFace r : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                Block n = p.getRelative(r);
                long spalte = schluessel(n.getX(), n.getZ());
                if (n.isLiquid() && !gebaut.containsKey(spalte) && !offen.containsKey(spalte)) {
                    setzen(n, UNTERBAU);
                }
            }
            if (h > y && p.isLiquid() && !geschuetzt(p)) {
                setzen(p, seite ? UNTERBAU : LUFT);
            }
        }
    }

    /** Räumt einen natürlichen Block weg (Wasser bleibt, die Straße führt dann als Brücke darüber). */
    private void abtragen(Block b) {
        Material m = b.getType();
        if (m.isAir() || Natur.fluessig(m) || geschuetzt(b) || istStrasse(m)) {
            return;
        }
        b.setBlockData(LUFT, false);
    }

    /** Blickrichtung wie Minecraft (0° = Süden, +z) von a nach b. */
    static float gier(double ax, double az, double bx, double bz) {
        return (float) Math.toDegrees(Math.atan2(-(bx - ax), bz - az));
    }

    static float wrap(float w) {
        w %= 360.0F;
        if (w > 180.0F) {
            w -= 360.0F;
        } else if (w < -180.0F) {
            w += 360.0F;
        }
        return w;
    }

    private static float clamp(float v, float a, float b) {
        return Math.max(a, Math.min(b, v));
    }
}
