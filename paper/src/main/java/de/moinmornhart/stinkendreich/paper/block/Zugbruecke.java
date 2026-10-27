package de.moinmornhart.stinkendreich.paper.block;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import de.moinmornhart.stinkendreich.paper.villa.Villen;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Zugbrücke über den Wassergraben am Tor wie in der Mod (Marke „bruecke“: Kasten x0 y0 z0 x1 y1 z1 mit Fahrbahn und
 * Geländer, dann die Richtung dx dz, in die sie einfährt).
 *
 * <p>Brückenschalter, Handy (Smart Home) und {@code /villa bruecke ein|aus} schalten die <i>Sicherung</i>: Ist sie an,
 * ist die Brücke eingefahren und kommt nur heraus, wenn ein Bewohner, Gast oder Kartenbesitzer davorsteht (zu Fuß oder im
 * Auto). Ist acht Sekunden niemand mehr da, fährt sie wieder ein.
 *
 * <p>Animation mit Block-Anzeigen: Geländer fahren in die Fahrbahn, die Brücke senkt sich aufs Wasser und gleitet mit
 * Dampf und Funken unter das Tor, an den Ufern wachsen Mauern aus dem Boden. Ausfahren läuft rückwärts. Die echten
 * Blöcke merkt sich {@code zugbruecke.json}, damit sie nach einem Neustart zurückkommen.
 */
public final class Zugbruecke implements Listener {
    private static final String TAG = "stinkendreich_bruecke";
    private static final int GELAENDER = 16, SENKEN = 14, GLEITEN = 60, MAUER = 16, WARTEN_TICKS = 160;
    private static final double ZU_FUSS = 7, IM_AUTO = 14, LEUCHTEN_ABSTAND = 20;
    private static final Material[] MAUER_BLOECKE = {Material.SMOOTH_QUARTZ, Material.GLASS};

    /** Lage der Brücke in der Welt. */
    private record Ort(World welt, int ax, int ay, int az, int bx, int by, int bz, int dx, int dz) {
        double mitteX() {
            return (ax + bx + 1) / 2.0;
        }

        double mitteZ() {
            return (az + bz + 1) / 2.0;
        }

        Location mitte() {
            return new Location(welt, mitteX(), ay, mitteZ());
        }

        /** Ganz unter dem Tor: Länge der Brücke in Fahrtrichtung plus ein Block. */
        int strecke() {
            return (dz != 0 ? bz - az : bx - ax) + 2;
        }

        BoundingBox kasten() {
            return new BoundingBox(ax, ay, az, bx + 1, by + 1, bz + 1);
        }

        boolean geladen() {
            return welt.isChunkLoaded(ax >> 4, az >> 4) && welt.isChunkLoaded(bx >> 4, bz >> 4);
        }

        /** Die beiden Ufermauern quer zur Fahrtrichtung, zwischen den Geländern. */
        List<Teil> mauer() {
            List<Teil> liste = new ArrayList<>();
            for (int h = 0; h < MAUER_BLOECKE.length; h++) {
                int y = ay + 1 + h;
                BlockData d = MAUER_BLOECKE[h].createBlockData();
                if (dz != 0) {
                    for (int x = ax + 1; x < bx; x++) {
                        liste.add(new Teil(welt.getBlockAt(x, y, az - 1), d, 0));
                        liste.add(new Teil(welt.getBlockAt(x, y, bz + 1), d, 0));
                    }
                } else {
                    for (int z = az + 1; z < bz; z++) {
                        liste.add(new Teil(welt.getBlockAt(ax - 1, y, z), d, 0));
                        liste.add(new Teil(welt.getBlockAt(bx + 1, y, z), d, 0));
                    }
                }
            }
            return liste;
        }
    }

    /** Ein Block der Brücke und seine Höhe über der Fahrbahn. */
    private record Teil(Block block, BlockData daten, int stufe) {
    }

    /** Die laufende Fahrt (es fährt immer nur eine Brücke). */
    private static final class Lauf {
        final int villa;
        final boolean einfahren;
        final List<Teil> teile;
        final Ort ort;
        final int stufen;
        final Map<Integer, List<BlockDisplay>> anzeigen = new HashMap<>();
        final List<BlockDisplay> mauer = new ArrayList<>();
        int tick;

        Lauf(int villa, boolean einfahren, List<Teil> teile, Ort ort) {
            this.villa = villa;
            this.einfahren = einfahren;
            this.teile = teile;
            this.ort = ort;
            this.stufen = ort.by() - ort.ay() + 1;
        }
    }

    /** Gespeicherter Zustand je Villa. */
    private static final class Stand {
        boolean gesichert, eingefahren;
        List<String> bloecke = new ArrayList<>();
    }

    private final StinkendReichPaper plugin;
    private final Path datei;
    private final Map<Integer, Stand> staende = new HashMap<>();
    private final Map<Integer, Long> zuletztJemandDa = new HashMap<>();
    private Lauf lauf;
    private long tick;

    public Zugbruecke(StinkendReichPaper plugin, Mechanik mechanik) {
        this.plugin = plugin;
        this.datei = plugin.getDataFolder().toPath().resolve("zugbruecke.json");
        try {
            if (Files.exists(datei)) {
                JsonObject j = JsonParser.parseString(Files.readString(datei, StandardCharsets.UTF_8)).getAsJsonObject();
                j.entrySet().forEach(e -> {
                    JsonObject o = e.getValue().getAsJsonObject();
                    Stand s = new Stand();
                    s.gesichert = o.get("gesichert").getAsBoolean();
                    s.eingefahren = o.get("eingefahren").getAsBoolean();
                    o.getAsJsonArray("bloecke").forEach(b -> s.bloecke.add(b.getAsString()));
                    staende.put(Integer.parseInt(e.getKey()), s);
                });
            }
        } catch (Exception e) {
            plugin.getLogger().warning("zugbruecke.json konnte nicht gelesen werden: " + e.getMessage());
        }
        mechanik.anmelden("brueckenschalter", (p, b, z) -> {
            umschalten(p, plugin.villen().bei(b.getLocation(), 16));
            return true;
        });
        Bukkit.getScheduler().runTaskTimer(plugin, this::takt, 1L, 1L);
    }

    private Stand stand(int villa) {
        return staende.computeIfAbsent(villa, n -> new Stand());
    }

    private void speichern() {
        JsonObject j = new JsonObject();
        staende.forEach((nr, s) -> {
            JsonObject o = new JsonObject();
            o.addProperty("gesichert", s.gesichert);
            o.addProperty("eingefahren", s.eingefahren);
            JsonArray a = new JsonArray();
            s.bloecke.forEach(a::add);
            o.add("bloecke", a);
            j.add(Integer.toString(nr), o);
        });
        try {
            Files.createDirectories(datei.getParent());
            Files.writeString(datei, new GsonBuilder().setPrettyPrinting().create().toJson(j), StandardCharsets.UTF_8);
        } catch (Exception e) {
            plugin.getLogger().warning("zugbruecke.json konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    private Ort ort(Villen.Villa v) {
        JsonArray m = v == null || v.world() == null ? null : plugin.bauplan().ersteMarke("bruecke");
        if (m == null) {
            return null;
        }
        Block a = v.block(m.get(0).getAsInt(), m.get(1).getAsInt(), m.get(2).getAsInt());
        Block b = v.block(m.get(3).getAsInt(), m.get(4).getAsInt(), m.get(5).getAsInt());
        return new Ort(v.world(), a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ(), m.get(6).getAsInt(), m.get(7).getAsInt());
    }

    /** Soll eine Warnleuchte blinken, weil die Brücke in der Nähe fährt? */
    public boolean warnen(Block leuchte) {
        Lauf l = lauf;
        return l != null && leuchte.getWorld().equals(l.ort.welt())
                && leuchte.getLocation().add(0.5, 0.5, 0.5).distanceSquared(l.ort.mitte()) < LEUCHTEN_ABSTAND * LEUCHTEN_ABSTAND;
    }

    public boolean gesichert(Villen.Villa v) {
        return v != null && stand(v.nr()).gesichert;
    }

    // ------------------------------------------------------------------
    // Bedienen
    // ------------------------------------------------------------------

    /** Brückenschalter und Handy: Sicherung umschalten (nur Bewohner). */
    public void umschalten(Player p, Villen.Villa v) {
        if (v == null) {
            v = plugin.villen().vonBesitzer(p.getUniqueId());
        }
        if (v == null || !plugin.zugang().istBewohner(p, v)) {
            p.sendActionBar(Component.text("Nur Bewohner der Villa können die Zugbrücke bedienen.", NamedTextColor.RED));
            return;
        }
        boolean an = !stand(v.nr()).gesichert;
        if (!sichern(v, an)) {
            p.sendActionBar(Component.text("Diese Villa hat keine Zugbrücke.", NamedTextColor.RED));
            return;
        }
        p.sendMessage(Component.text(an ? "Zugbrücke gesichert: Sie fährt ein und kommt nur für Bewohner und Besitzer einer Zugangskarte heraus."
                : "Zugbrücke offen: Sie fährt aus und bleibt draußen.", NamedTextColor.GOLD));
    }

    /** Sicherung an (fährt ein, sobald niemand mehr davorsteht) oder aus (fährt aus und bleibt draußen). */
    public boolean sichern(Villen.Villa v, boolean an) {
        if (ort(v) == null) {
            return false;
        }
        stand(v.nr()).gesichert = an;
        speichern();
        if (an) {
            zuletztJemandDa.remove(v.nr());               // gleich prüfen, nicht erst acht Sekunden warten
        }
        pruefen(v);
        plugin.getLogger().info("Zugbrücke: Villa " + v.nr() + (an ? " gesichert" : " offen"));
        return true;
    }

    // ------------------------------------------------------------------
    // Automatik
    // ------------------------------------------------------------------

    private void takt() {
        tick++;
        if (lauf != null) {
            fahren();
        } else if (tick % 10 == 0) {
            for (Villen.Villa v : plugin.villen().alle()) {
                pruefen(v);
                if (lauf != null) {
                    return;
                }
            }
        }
    }

    private void pruefen(Villen.Villa v) {
        if (lauf != null || plugin.inkognito().aktiv(v)) {
            return;
        }
        Ort o = ort(v);
        if (o == null || !o.geladen()) {
            return;
        }
        Stand s = stand(v.nr());
        boolean jemand = jemandDa(o, v);
        if (jemand) {
            zuletztJemandDa.put(v.nr(), tick);
        }
        boolean festung = plugin.sicherheit().stufe(v) >= Sicherheit.FESTUNG;
        boolean sollDrin = (s.gesichert || festung) && !jemand && tick - zuletztJemandDa.getOrDefault(v.nr(), Long.MIN_VALUE / 2) >= WARTEN_TICKS;
        if (sollDrin != s.eingefahren) {
            starten(v, o, sollDrin, jemand);
        }
    }

    /** Steht ein Berechtigter vor der Brücke (zu Fuß 7 Blöcke, im Auto 14)? */
    private boolean jemandDa(Ort o, Villen.Villa v) {
        BoundingBox zuFuss = o.kasten().clone().expand(ZU_FUSS, 4, ZU_FUSS), imAuto = o.kasten().clone().expand(IM_AUTO, 6, IM_AUTO);
        for (Player p : o.welt().getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR || !plugin.zugang().darfRein(p, v)) {
                continue;
            }
            if ((p.isInsideVehicle() ? imAuto : zuFuss).contains(p.getLocation().toVector())) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Ablauf
    // ------------------------------------------------------------------

    private void starten(Villen.Villa v, Ort o, boolean einfahren, boolean fuerJemand) {
        Stand s = stand(v.nr());
        List<Teil> teile = new ArrayList<>();
        if (einfahren) {
            List<String> gemerkt = new ArrayList<>();
            for (int x = o.ax(); x <= o.bx(); x++) {
                for (int y = o.ay(); y <= o.by(); y++) {
                    for (int z = o.az(); z <= o.bz(); z++) {
                        Block b = o.welt().getBlockAt(x, y, z);
                        if (b.getType().isAir() || b.isLiquid()) {
                            continue;
                        }
                        teile.add(new Teil(b, b.getBlockData(), y - o.ay()));
                        gemerkt.add(x + " " + y + " " + z + "|" + b.getBlockData().getAsString());
                    }
                }
            }
            if (teile.isEmpty()) {
                return;
            }
            // Gleich merken: wird der Server mitten in der Fahrt beendet, fehlen die Blöcke sonst
            s.eingefahren = true;
            s.bloecke = gemerkt;
            speichern();
        } else {
            for (String eintrag : s.bloecke) {
                Teil t = teilAusText(o, eintrag);
                if (t != null) {
                    teile.add(t);
                }
            }
            if (teile.isEmpty()) {
                s.eingefahren = false;
                speichern();
                return;
            }
        }
        Lauf l = new Lauf(v.nr(), einfahren, teile, o);
        lauf = l;
        for (Teil t : teile) {
            l.anzeigen.computeIfAbsent(t.stufe(), k -> new ArrayList<>())
                    .add(anzeige(t, t.stufe() == 0, einfahren ? new double[]{0, 0, 0} : versteckt(o, t.stufe())));
        }
        if (einfahren) {
            for (Teil t : teile) {
                t.block().setType(Material.AIR, false);
            }
        } else {
            // Die Ufermauern versinken zuerst wieder im Boden
            for (Teil m : o.mauer()) {
                if (m.block().getType() == m.daten().getMaterial()) {
                    m.block().setType(Material.AIR, false);
                    l.mauer.add(anzeige(m, true, new double[]{0, 0, 0}));
                }
            }
        }
        ton(o, "block.beacon.activate", 1.0F, 1.0F);
        ton(o, einfahren ? "block.piston.contract" : "block.piston.extend", 0.8F, 0.7F);
        melden(v, o, einfahren ? "Achtung: Die Zugbrücke fährt ein …"
                : fuerJemand ? "Willkommen! Die Zugbrücke fährt für dich aus …" : "Die Zugbrücke fährt aus …", NamedTextColor.GOLD);
        plugin.getLogger().info("Zugbrücke: Villa " + v.nr() + (einfahren ? " fährt ein" : " fährt aus"));
    }

    private void fahren() {
        Lauf l = lauf;
        Ort o = l.ort;
        if (!o.geladen()) {
            return;
        }
        int t = l.tick++;
        if (l.einfahren) {
            int t1 = 2, t2 = t1 + GELAENDER + 2, t3 = t2 + SENKEN + 2, t4 = t3 + GLEITEN + 2, t5 = t4 + 2, ende = t5 + MAUER + 2;
            if (t == t1) {
                bewegen(l, 1, GELAENDER);
                ton(o, "block.iron_trapdoor.close", 1.0F, 0.7F);
            } else if (t == t2) {
                bewegen(l, 2, SENKEN);
                ton(o, "block.piston.contract", 1.0F, 0.6F);
                spritzer(o);
            } else if (t == t3) {
                bewegen(l, 3, GLEITEN);
                ton(o, "block.piston.contract", 1.0F, 0.5F);
            } else if (t == t4) {
                // Die Brücke steckt unter dem Tor: Anzeigen weg, die Mauern kommen aus dem Boden
                l.anzeigen.values().forEach(liste -> liste.forEach(Entity::remove));
                l.anzeigen.clear();
                for (Teil m : o.mauer()) {
                    if (m.block().getType().isAir()) {
                        l.mauer.add(anzeige(m, true, new double[]{0, -MAUER_BLOECKE.length, 0}));
                    }
                }
            } else if (t == t5) {
                mauerBewegen(l, 0, MAUER);
                ton(o, "block.piston.extend", 1.0F, 0.8F);
                staub(o);
            }
            if (t > t3 && t < t4) {
                gleiten(o, t - t3);
            }
            if (t >= ende) {
                abschliessen();
            }
        } else {
            int t1 = 2, t2 = t1 + MAUER + 2, t3 = t2 + GLEITEN + 2, t4 = t3 + SENKEN + 2, ende = t4 + GELAENDER + 2;
            if (t == t1) {
                mauerBewegen(l, -MAUER_BLOECKE.length, MAUER);
                ton(o, "block.piston.contract", 1.0F, 0.8F);
                staub(o);
            } else if (t == t2) {
                l.mauer.forEach(Entity::remove);
                l.mauer.clear();
                bewegen(l, 2, GLEITEN);
                ton(o, "block.piston.extend", 1.0F, 0.5F);
            } else if (t == t3) {
                bewegen(l, 1, SENKEN);
                ton(o, "block.piston.extend", 1.0F, 0.6F);
                spritzer(o);
            } else if (t == t4) {
                bewegen(l, 0, GELAENDER);
                ton(o, "block.iron_trapdoor.open", 1.0F, 0.7F);
            }
            if (t > t2 && t < t3) {
                gleiten(o, t - t2);
            }
            if (t >= ende) {
                abschliessen();
            }
        }
    }

    /** Ende der Fahrt: echte Blöcke setzen (Brücke bzw. Mauern), Anzeigen weg, Zustand speichern. */
    private void abschliessen() {
        Lauf l = lauf;
        lauf = null;
        Ort o = l.ort;
        Stand s = stand(l.villa);
        if (l.einfahren) {
            for (Teil m : o.mauer()) {
                if (m.block().getType().isAir()) {
                    m.block().setBlockData(m.daten(), false);
                }
            }
        } else {
            for (Teil t : l.teile) {
                t.block().setBlockData(t.daten(), false);
            }
            s.eingefahren = false;
            s.bloecke = new ArrayList<>();
            speichern();
            nachObenSchieben(o);
        }
        l.anzeigen.values().forEach(liste -> liste.forEach(Entity::remove));
        l.mauer.forEach(Entity::remove);
        ton(o, "block.anvil.land", 0.4F, 0.7F);
        ton(o, "block.beacon.deactivate", 0.8F, 1.2F);
        melden(plugin.villen().nr(l.villa), o, l.einfahren ? "Zugbrücke eingefahren. Niemand kommt mehr über den Graben."
                : "Zugbrücke ausgefahren. Der Weg ist frei.", NamedTextColor.GREEN);
        plugin.getLogger().info("Zugbrücke: Villa " + l.villa + (l.einfahren ? " ist eingefahren" : " ist ausgefahren"));
    }

    /** Beim Beenden des Servers: laufende Fahrt sofort fertig machen. */
    public void stoppen() {
        if (lauf != null) {
            abschliessen();
        }
    }

    /** Wer in den zurückkehrenden Blöcken steckt (z. B. aus dem Graben), kommt oben auf die Brücke. */
    private void nachObenSchieben(Ort o) {
        for (Entity e : o.welt().getNearbyEntities(o.kasten().clone().expand(0, 2, 0, 0, 0, 0))) {
            if (e instanceof LivingEntity && !e.isInsideVehicle() && e.getLocation().getBlock().getType().isSolid()) {
                Location l = e.getLocation();
                l.setY(o.ay() + 1);
                e.teleport(l);
            }
        }
    }

    /** Übrig gebliebene Anzeigen (Server mitten in der Fahrt abgestürzt) beim Laden entfernen. */
    @EventHandler
    public void beimLaden(EntitiesLoadEvent e) {
        if (lauf != null) {
            return;
        }
        for (Entity ent : e.getEntities()) {
            if (ent instanceof BlockDisplay && ent.getScoreboardTags().contains(TAG)) {
                ent.remove();
            }
        }
    }

    // ------------------------------------------------------------------
    // Anzeigen
    // ------------------------------------------------------------------

    private BlockDisplay anzeige(Teil t, boolean gross, double[] start) {
        return t.block().getWorld().spawn(t.block().getLocation(), BlockDisplay.class, d -> {
            d.setBlock(t.daten());
            d.setPersistent(false);
            d.setViewRange(2.0F);
            d.addScoreboardTag(TAG);
            d.setTransformation(trafo(gross, start[0], start[1], start[2]));
        });
    }

    /** Lagen: 0 = wie die echte Brücke, 1 = Geländer in der Fahrbahn, 2 = einen Block tiefer, 3 = unter dem Tor. */
    private void bewegen(Lauf l, int lage, int dauer) {
        l.anzeigen.forEach((stufe, liste) -> {
            double[] v = switch (lage) {
                case 0 -> new double[]{0, 0, 0};
                case 1 -> new double[]{0, -stufe, 0};
                case 2 -> new double[]{0, -stufe - 1, 0};
                default -> versteckt(l.ort, stufe);
            };
            for (BlockDisplay d : liste) {
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(dauer);
                d.setTransformation(trafo(stufe == 0, v[0], v[1], v[2]));
            }
        });
    }

    private void mauerBewegen(Lauf l, double y, int dauer) {
        for (BlockDisplay d : l.mauer) {
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(dauer);
            d.setTransformation(trafo(true, 0, y, 0));
        }
    }

    private static double[] versteckt(Ort o, int stufe) {
        return new double[]{o.dx() * o.strecke(), -stufe - 1, o.dz() * o.strecke()};
    }

    /** Etwas kleiner gezeichnet und mittig verschoben, damit nichts mit den Nachbarflächen flimmert. */
    private static Transformation trafo(boolean gross, double x, double y, double z) {
        float s = gross ? 0.998F : 0.99F, rand = (1 - s) / 2;
        return new Transformation(new Vector3f((float) (rand + x), (float) (rand + y), (float) (rand + z)), new AxisAngle4f(),
                new Vector3f(s, s, s), new AxisAngle4f());
    }

    // ------------------------------------------------------------------
    // Effekte
    // ------------------------------------------------------------------

    private void gleiten(Ort o, int schritt) {
        if (schritt % 8 == 1) {
            ton(o, "block.grindstone.use", 0.6F, 0.5F);
        }
        if (schritt % 2 == 0) {
            double nahtX = o.dx() < 0 ? o.ax() : o.dx() > 0 ? o.bx() + 1 : o.mitteX();
            double nahtZ = o.dz() < 0 ? o.az() : o.dz() > 0 ? o.bz() + 1 : o.mitteZ();
            double breiteX = o.dx() == 0 ? (o.bx() - o.ax() + 1) / 2.5 : 0.1;
            double breiteZ = o.dz() == 0 ? (o.bz() - o.az() + 1) / 2.5 : 0.1;
            double y = o.ay() - 0.4;
            o.welt().spawnParticle(Particle.CLOUD, nahtX, y, nahtZ, 5, breiteX, 0.25, breiteZ, 0.02);
            o.welt().spawnParticle(Particle.ELECTRIC_SPARK, nahtX, y + 0.2, nahtZ, 4, breiteX, 0.2, breiteZ, 0.3);
        }
    }

    private static void spritzer(Ort o) {
        double bx = (o.bx() - o.ax() + 1) / 2.5, bz = (o.bz() - o.az() + 1) / 2.5;
        o.welt().spawnParticle(Particle.SPLASH, o.mitteX(), o.ay() + 0.1, o.mitteZ(), 120, bx, 0.1, bz, 0.2);
        o.welt().spawnParticle(Particle.CLOUD, o.mitteX(), o.ay() + 0.3, o.mitteZ(), 25, bx, 0.2, bz, 0.02);
    }

    private static void staub(Ort o) {
        for (Teil m : o.mauer()) {
            if (m.block().getY() == o.ay() + 1 && m.block().getX() % 2 == 0) {
                o.welt().spawnParticle(Particle.CLOUD, m.block().getX() + 0.5, m.block().getY() + 0.1, m.block().getZ() + 0.5, 3, 0.3, 0.05, 0.3, 0.01);
            }
        }
    }

    private static void ton(Ort o, String ton, float lautstaerke, float hoehe) {
        o.welt().playSound(o.mitte(), "minecraft:" + ton, SoundCategory.BLOCKS, lautstaerke * 2.0F, hoehe);
    }

    /** Nachricht über der Leiste an Bewohner in der Nähe der Brücke. */
    private void melden(Villen.Villa v, Ort o, String text, NamedTextColor farbe) {
        for (Player p : o.welt().getPlayers()) {
            if (plugin.zugang().istBewohner(p, v) && p.getLocation().distanceSquared(o.mitte()) < 80 * 80) {
                p.sendActionBar(Component.text(text, farbe));
            }
        }
    }

    private static Teil teilAusText(Ort o, String eintrag) {
        String[] teile = eintrag.split("\\|", 2);
        String[] xyz = teile[0].split(" ");
        if (teile.length < 2 || xyz.length < 3) {
            return null;
        }
        try {
            int y = Integer.parseInt(xyz[1]);
            return new Teil(o.welt().getBlockAt(Integer.parseInt(xyz[0]), y, Integer.parseInt(xyz[2])), Bukkit.createBlockData(teile[1]), y - o.ay());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Für die Warnleuchten in {@link Sicherheit}: läuft gerade eine Fahrt? */
    public boolean faehrt() {
        return lauf != null;
    }
}
