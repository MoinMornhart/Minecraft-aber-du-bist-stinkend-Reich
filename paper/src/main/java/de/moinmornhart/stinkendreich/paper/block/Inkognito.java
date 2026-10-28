package de.moinmornhart.stinkendreich.paper.block;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import de.moinmornhart.stinkendreich.paper.villa.Villen;
import io.papermc.paper.entity.TeleportFlag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.BoundingBox;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Inkognito-Modus wie in der Mod: Die ganze Villa samt Grundstück fährt Block für Block in die Erde, Möbel, Autos,
 * Figuren und Spieler fahren mit. Unten schiebt sich von den Rändern her eine Wiese über das Loch, darauf wachsen
 * Tarnbäume. Beim Auftauchen verschwinden die Bäume, die Wiese öffnet sich von der Mitte aus, und die Villa fährt mit
 * allem, was unten verändert wurde, wieder hoch.
 *
 * <p>Für Paper so gebaut, dass es schnell genug geht: Alle Spalten werden einmal (über Chunk-Abbilder) gelesen, danach
 * rechnet die Fahrt nur noch im Zwischenspeicher und setzt nur Blöcke, die sich wirklich ändern. Inhalte von Truhen,
 * Schildern, Bannern … werden vorher gesichert und am Ziel wieder eingesetzt. Die Mod-Blöcke ruhen während der Fahrt
 * ({@link VirtuelleBloecke#sperren}); ihre Anzeige-Figuren gleiten mit.
 */
public final class Inkognito implements Listener {
    /** Erdschichten über dem höchsten Punkt der versenkten Villa (oberste davon ist Gras). */
    private static final int DECKE = 3;
    private static final int TICKS_JE_SCHRITT = 4, RINGE_JE_TICK = 2, BAEUME = 18, TICKS_JE_BAUM = 3, LADE_TICKS = 40;
    /** So viel Luft über der Villa fährt mit (Hubschrauber auf dem Landeplatz). */
    private static final int LUFT_OBEN = 4;
    /** Rechenzeit je Tick beim Einlesen. */
    private static final long BUDGET_NS = 25_000_000L;

    private enum Phase { LADEN_RUNTER, RUNTER, DECKEL_ZU, BAEUME_HOCH, BAEUME_WEG, DECKEL_AUF, LADEN_HOCH, HOCH }

    /** Eine laufende Fahrt. */
    private final class Fahrt {
        final Villen.Villa v;
        final World w;
        final int tiefe, bx, bz, h;
        Phase phase;
        int schritt, teil, gewartet, gelesen;
        /** Zwischenspeicher je Spalte (x * bz + z): Blöcke von {@link #yUnten} aufwärts. */
        BlockData[][] spalten;
        int yUnten;
        /** Gesicherte Blöcke mit Inhalt (Truhen, Schilder …) an ihrer Lage beim Start. */
        final List<BlockState> inhalte = new ArrayList<>();
        /** Höhe jeder mitfahrenden Figur am Anfang des Schritts. */
        final Map<Entity, Double> startHoehen = new HashMap<>();
        /** Figuren, deren Schwerkraft für die Fahrt abgeschaltet wurde. */
        final Set<Entity> ohneSchwerkraft = new HashSet<>();
        /** Anzeige-Figuren und ihre Gleitzeit vor der Fahrt. */
        final Map<Display, Integer> gleiten = new HashMap<>();
        /** Spieler, die beim Auftauchen oben auf der Wiese stehen (fahren nicht mit). */
        final Set<Entity> draussen = new HashSet<>();
        BoundingBox gesperrt;

        Fahrt(Villen.Villa v, Phase phase) {
            this.v = v;
            this.w = v.world();
            this.phase = phase;
            this.tiefe = tiefe();
            this.bx = plugin.bauplan().breiteX;
            this.bz = plugin.bauplan().breiteZ;
            this.h = plugin.bauplan().hoehe;
        }

        /** Bereich der Villa um versatz verschoben, samt Luft darüber. */
        BoundingBox bereich(int versatz) {
            return new BoundingBox(v.x(), v.y() + versatz, v.z(), v.x() + bx, v.y() + versatz + h + LUFT_OBEN, v.z() + bz);
        }
    }

    private final StinkendReichPaper plugin;
    private final VirtuelleBloecke bloecke;
    private final Path datei;
    /** Versteckte Villen (Nummer → true). */
    private final Map<Integer, Boolean> unten = new HashMap<>();
    /** Sicherheitsstufe von vor dem Abtauchen (kommt beim Auftauchen zurück; bei Stufe 0 stünden die Tore immer offen). */
    private final Map<Integer, Integer> stufeVorher = new HashMap<>();
    private final Map<Integer, Fahrt> fahrten = new HashMap<>();
    private final Map<Integer, Boolean> wunsch = new HashMap<>();
    /** Blöcke, die Inhalte tragen (Truhen, Schilder, Banner …), einmal ermittelt. */
    private final Set<Material> mitInhalt = EnumSet.noneOf(Material.class);
    private final Map<BlockData, BlockData> einmal = new HashMap<>();
    private long tick;

    public Inkognito(StinkendReichPaper plugin, VirtuelleBloecke bloecke) {
        this.plugin = plugin;
        this.bloecke = bloecke;
        this.datei = plugin.getDataFolder().toPath().resolve("inkognito.json");
        try {
            if (Files.exists(datei)) {
                JsonObject j = JsonParser.parseString(Files.readString(datei, StandardCharsets.UTF_8)).getAsJsonObject();
                j.entrySet().forEach(e -> {
                    if (e.getKey().startsWith("stufe_")) {
                        stufeVorher.put(Integer.parseInt(e.getKey().substring(6)), e.getValue().getAsInt());
                    } else {
                        unten.put(Integer.parseInt(e.getKey()), e.getValue().getAsBoolean());
                    }
                });
            }
        } catch (Exception e) {
            plugin.getLogger().warning("inkognito.json konnte nicht gelesen werden: " + e.getMessage());
        }
        for (Material m : Material.values()) {
            if (m.isBlock() && !m.isLegacy()) {
                try {
                    if (m.createBlockData().createBlockState() instanceof TileState) {
                        mitInhalt.add(m);
                    }
                } catch (Exception ignoriert) {
                    // Blöcke ohne eigenen Zustand
                }
            }
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::takt, 1L, 1L);
    }

    /** Ist die Villa versteckt oder fährt sie gerade? Dann ruhen Graben, Zugbrücke usw. */
    public boolean aktiv(Villen.Villa v) {
        return v != null && (fahrten.containsKey(v.nr()) || unten.getOrDefault(v.nr(), false));
    }

    public boolean versteckt(Villen.Villa v) {
        return v != null && unten.getOrDefault(v.nr(), false);
    }

    private int tiefe() {
        return plugin.bauplan().hoehe - plugin.bauplan().bodenY + DECKE - 1;
    }

    private void speichern() {
        JsonObject j = new JsonObject();
        unten.forEach((n, b) -> j.addProperty(Integer.toString(n), b));
        stufeVorher.forEach((n, s) -> j.addProperty("stufe_" + n, s));
        try {
            Files.createDirectories(datei.getParent());
            Files.writeString(datei, new GsonBuilder().setPrettyPrinting().create().toJson(j), StandardCharsets.UTF_8);
        } catch (Exception e) {
            plugin.getLogger().warning("inkognito.json konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Schalten
    // ------------------------------------------------------------------

    /** Handy und Befehl: an = versenken, aus = auftauchen. Liefert einen Hinweis für den Spieler. */
    public String schalten(Villen.Villa v, boolean an) {
        if (v == null || v.world() == null) {
            return "Keine Villa.";
        }
        if (fahrten.containsKey(v.nr())) {
            wunsch.put(v.nr(), an);                      // nach der Fahrt ausführen
            return "Gemerkt – kommt nach der laufenden Fahrt.";
        }
        wunsch.remove(v.nr());
        if (an == unten.getOrDefault(v.nr(), false)) {
            return an ? "Die Villa ist schon versteckt." : "Die Villa ist schon oben.";
        }
        if (an && v.y() - tiefe() <= v.world().getMinHeight() + 1) {
            return "Hier ist es zu tief für das Versteck.";
        }
        plugin.getLogger().info("Inkognito: Villa " + v.nr() + " fährt " + (an ? "runter" : "hoch") + " (Tiefe " + tiefe() + ")");
        laden(v, true);
        if (an) {
            stufeVorher.put(v.nr(), plugin.sicherheit().stufe(v));
            plugin.sicherheit().stufeFuerInkognito(v);   // keine Laser, Minen oder Lava im Versteck
        }
        fahrten.put(v.nr(), new Fahrt(v, an ? Phase.LADEN_RUNTER : Phase.BAEUME_WEG));
        unten.put(v.nr(), an);
        speichern();
        v.world().playSound(mitte(v, plugin.bauplan().bodenY), Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.BLOCKS, 4.0F, an ? 0.6F : 1.2F);
        return an ? "Inkognito: Die Villa verschwindet …" : "Die Villa taucht wieder auf …";
    }

    private Location mitte(Villen.Villa v, double ly) {
        return v.ort(plugin.bauplan(), plugin.bauplan().breiteX / 2.0, ly, plugin.bauplan().breiteZ / 2.0);
    }

    /** Hält alle Chunks des Grundstücks geladen (an) oder gibt sie frei. */
    private void laden(Villen.Villa v, boolean an) {
        int x0 = v.x() >> 4, z0 = v.z() >> 4, x1 = (v.x() + plugin.bauplan().breiteX - 1) >> 4, z1 = (v.z() + plugin.bauplan().breiteZ - 1) >> 4;
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                if (an) {
                    v.world().addPluginChunkTicket(cx, cz, plugin);
                } else {
                    v.world().removePluginChunkTicket(cx, cz, plugin);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Ablauf
    // ------------------------------------------------------------------

    private void takt() {
        tick++;
        for (Fahrt f : List.copyOf(fahrten.values())) {
            weiter(f, false);
            if (f.phase == null) {
                fahrten.remove(f.v.nr());
                Boolean neu = wunsch.remove(f.v.nr());
                if (neu != null) {
                    schalten(f.v, neu);
                }
            }
        }
    }

    /** Server fährt herunter: laufende Fahrten ohne Animation fertig machen. */
    public void stoppen() {
        for (Fahrt f : List.copyOf(fahrten.values())) {
            int sicherung = 100_000;
            while (f.phase != null && sicherung-- > 0) {
                weiter(f, true);
            }
        }
        fahrten.clear();
    }

    private void weiter(Fahrt f, boolean sofort) {
        int ringe = (Math.min(f.bx, f.bz) + 1) / 2;
        switch (f.phase) {
            case LADEN_RUNTER, LADEN_HOCH -> {
                boolean runter = f.phase == Phase.LADEN_RUNTER;
                if (!sofort && ++f.gewartet < LADE_TICKS) {
                    return;
                }
                // Spalten einlesen (über mehrere Ticks), dann losfahren
                if (f.spalten == null) {
                    int versatz = runter ? 0 : -f.tiefe;
                    f.yUnten = f.v.y() + versatz - (runter ? f.tiefe + 1 : 0);
                    f.spalten = new BlockData[f.bx * f.bz][];
                    f.gelesen = 0;
                    inhalteSichern(f, versatz);
                    if (!runter) {
                        int dach = f.v.y() - f.tiefe + f.h;
                        f.draussen.clear();
                        for (Entity e : f.w.getNearbyEntities(f.bereich(-f.tiefe))) {
                            if (e instanceof Player && e.getLocation().getY() >= dach) {
                                f.draussen.add(e);
                            }
                        }
                    }
                }
                if (!einlesen(f, sofort)) {
                    return;
                }
                f.gesperrt = f.bereich(-f.tiefe).union(f.bereich(0));
                bloecke.sperren(f.w, f.gesperrt);
                f.phase = runter ? Phase.RUNTER : Phase.HOCH;
                f.schritt = 0;
                f.teil = 0;
            }
            case RUNTER -> {
                if (f.teil == 0) {
                    effekte(f, plugin.bauplan().bodenY - f.schritt, true, sofort);
                    figurenMerken(f, -f.schritt, false);
                }
                for (int t = f.teil; t <= (sofort ? TICKS_JE_SCHRITT - 1 : f.teil); t++) {
                    schieben(f, -f.schritt, -1, t);
                }
                if (sofort || ++f.teil == TICKS_JE_SCHRITT) {
                    // Jede Figur landet genau einen Block unter ihrer Höhe vom Schrittanfang
                    for (Map.Entry<Entity, Double> e : f.startHoehen.entrySet()) {
                        versetzen(e.getKey(), e.getValue() - 1 - e.getKey().getLocation().getY());
                    }
                    f.startHoehen.clear();
                    f.teil = 0;
                    if (++f.schritt == f.tiefe) {
                        ankommen(f, -f.tiefe);
                        f.phase = Phase.DECKEL_ZU;
                        f.schritt = 0;
                    }
                }
            }
            case DECKEL_ZU -> {
                for (int i = 0; i < (sofort ? ringe : RINGE_JE_TICK) && f.schritt < ringe; i++, f.schritt++) {
                    deckelRing(f, f.schritt, true, sofort);
                }
                if (f.schritt >= ringe) {
                    f.phase = Phase.BAEUME_HOCH;
                    f.schritt = 0;
                    f.w.playSound(mitte(f.v, plugin.bauplan().bodenY), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 4.0F, 0.8F);
                }
            }
            case BAEUME_HOCH -> {
                if (sofort || tick % TICKS_JE_BAUM == 0) {
                    tarnung(f, f.schritt, true, sofort);
                    f.schritt++;
                }
                if (f.schritt >= BAEUME) {
                    plugin.getLogger().info("Inkognito: Villa " + f.v.nr() + " ist unten");
                    melden(f.v, "Inkognito: Die Villa ist versteckt.");
                    f.phase = null;
                    laden(f.v, false);
                }
            }
            case BAEUME_WEG -> {
                tarnung(f, f.schritt, false, sofort);
                if (++f.schritt >= BAEUME) {
                    f.phase = Phase.DECKEL_AUF;
                    f.schritt = 0;
                }
            }
            case DECKEL_AUF -> {
                for (int i = 0; i < (sofort ? ringe : RINGE_JE_TICK) && f.schritt < ringe; i++, f.schritt++) {
                    deckelRing(f, ringe - 1 - f.schritt, false, sofort);
                }
                if (f.schritt >= ringe) {
                    f.phase = Phase.LADEN_HOCH;
                    f.schritt = 0;
                    f.gewartet = 0;
                }
            }
            case HOCH -> {
                if (f.teil == 0) {
                    // Erst die Figuren anheben, dann rückt der Boden unter ihnen nach
                    figurenMerken(f, f.schritt - f.tiefe, true);
                    for (Map.Entry<Entity, Double> e : f.startHoehen.entrySet()) {
                        versetzen(e.getKey(), e.getValue() - e.getKey().getLocation().getY());
                    }
                    effekte(f, plugin.bauplan().bodenY - f.tiefe + f.schritt, false, sofort);
                } else {
                    halten(f);
                }
                for (int t = f.teil; t <= (sofort ? TICKS_JE_SCHRITT - 1 : f.teil); t++) {
                    schieben(f, f.schritt - f.tiefe, 1, t);
                }
                if (sofort || ++f.teil == TICKS_JE_SCHRITT) {
                    f.teil = 0;
                    halten(f);
                    f.startHoehen.clear();
                    if (++f.schritt == f.tiefe) {
                        ankommen(f, f.tiefe);
                        // Wer draußen stand, steht jetzt oben auf dem, was unter ihm aufgetaucht ist
                        for (Entity e : f.draussen) {
                            if (e.isValid()) {
                                Location l = e.getLocation();
                                int boden = f.w.getHighestBlockYAt(l.getBlockX(), l.getBlockZ(), HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
                                l.setY(Math.max(l.getY(), boden));
                                e.teleport(l);
                                e.setFallDistance(0);
                            }
                        }
                        f.draussen.clear();
                        laden(f.v, false);
                        plugin.getLogger().info("Inkognito: Villa " + f.v.nr() + " ist oben");
                        // Stufe von vorher zurück (ohne gemerkte Stufe: Grundstufe 1) – sonst blieben Tor und Garagentor offen
                        Integer vorher = stufeVorher.remove(f.v.nr());
                        plugin.sicherheit().stufeNachInkognito(f.v, vorher != null ? vorher : 1);
                        speichern();
                        melden(f.v, "Die Villa ist wieder da.");
                        f.phase = null;
                        f.w.playSound(mitte(f.v, plugin.bauplan().bodenY), Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 4.0F, 1.0F);
                    }
                }
            }
        }
    }

    /** Am Ziel: Inhalte zurück, Schwerkraft zurück, Mod-Blöcke wieder frei (Verzeichnis um dy verschoben). */
    private void ankommen(Fahrt f, int dy) {
        for (BlockState st : f.inhalte) {
            try {
                st.copy(st.getLocation().add(0, dy, 0)).update(true, false);
            } catch (Exception e) {
                plugin.getLogger().fine("Inhalt nicht zurückgelegt: " + e.getMessage());
            }
        }
        f.inhalte.clear();
        for (Entity e : f.ohneSchwerkraft) {
            if (e.isValid()) {
                e.setGravity(true);
            }
        }
        f.ohneSchwerkraft.clear();
        f.gleiten.forEach((d, dauer) -> {
            if (d.isValid()) {
                d.setTeleportDuration(dauer);
            }
        });
        f.gleiten.clear();
        bloecke.entsperren(f.w, f.gesperrt);
        bloecke.verschoben(f.w, f.bereich(dy > 0 ? -f.tiefe : 0), dy);
        f.spalten = null;
        einmal.clear();
    }

    // ------------------------------------------------------------------
    // Blöcke
    // ------------------------------------------------------------------

    /** Sichert alle Blöcke mit Inhalt (Truhen leert es gleich, damit beim Verschieben nichts herausfällt). */
    private void inhalteSichern(Fahrt f, int versatz) {
        f.inhalte.clear();
        int y0 = f.v.y() + versatz;
        for (int x = 0; x < f.bx; x++) {
            for (int z = 0; z < f.bz; z++) {
                for (int y = y0; y < y0 + f.h; y++) {
                    Block b = f.w.getBlockAt(f.v.x() + x, y, f.v.z() + z);
                    if (!mitInhalt.contains(b.getType())) {
                        continue;
                    }
                    BlockState st = b.getState();
                    f.inhalte.add(st);
                    if (b.getState(false) instanceof Container c) {
                        c.getInventory().clear();
                    }
                }
            }
        }
        plugin.getLogger().info("Inkognito: " + f.inhalte.size() + " Blöcke mit Inhalt gesichert");
    }

    /** Liest die Spalten (über Chunk-Abbilder) in den Zwischenspeicher; true, wenn fertig. */
    private boolean einlesen(Fahrt f, boolean sofort) {
        long ende = System.nanoTime() + BUDGET_NS;
        int hoehe = f.h + f.tiefe + 2;
        Map<Long, ChunkSnapshot> abbilder = new HashMap<>();
        while (f.gelesen < f.spalten.length) {
            int x = f.gelesen / f.bz, z = f.gelesen % f.bz;
            int wx = f.v.x() + x, wz = f.v.z() + z;
            long k = ((long) (wx >> 4) << 32) | ((wz >> 4) & 0xffffffffL);
            ChunkSnapshot s = abbilder.computeIfAbsent(k, n -> {
                Chunk c = f.w.getChunkAt(wx >> 4, wz >> 4);
                return c.getChunkSnapshot(false, false, false);
            });
            BlockData[] spalte = new BlockData[hoehe];
            for (int i = 0; i < hoehe; i++) {
                int y = f.yUnten + i;
                BlockData d = y < f.w.getMinHeight() || y >= f.w.getMaxHeight() ? Material.AIR.createBlockData()
                        : s.getBlockData(wx & 15, y, wz & 15);
                spalte[i] = einmal.computeIfAbsent(d, n -> n);
            }
            f.spalten[f.gelesen++] = spalte;
            if (!sofort && System.nanoTime() > ende) {
                return false;
            }
        }
        return true;
    }

    /**
     * Verschiebt einen Streifen der Spalten um einen Block (richtung -1 runter, +1 hoch). Die Villa liegt gerade um
     * versatz verschoben. Gerechnet wird im Zwischenspeicher, gesetzt nur, was sich ändert.
     */
    private void schieben(Fahrt f, int versatz, int richtung, int t) {
        int unten = f.v.y() + versatz, oben = unten + f.h;                   // [unten, oben)
        int x0 = f.bx * t / TICKS_JE_SCHRITT, x1 = f.bx * (t + 1) / TICKS_JE_SCHRITT;
        BlockData luft = einmal.computeIfAbsent(Material.AIR.createBlockData(), n -> n);
        for (int x = x0; x < x1; x++) {
            for (int z = 0; z < f.bz; z++) {
                BlockData[] s = f.spalten[x * f.bz + z];
                int wx = f.v.x() + x, wz = f.v.z() + z;
                if (richtung < 0) {
                    for (int y = unten - 1; y < oben; y++) {
                        BlockData neu = y + 1 < oben ? s[y + 1 - f.yUnten] : luft;
                        setzen(f, s, wx, y, wz, neu);
                    }
                } else {
                    for (int y = oben; y >= unten; y--) {
                        BlockData neu = y > unten ? s[y - 1 - f.yUnten]
                                : einmal.computeIfAbsent((y < 0 ? Material.DEEPSLATE : Material.STONE).createBlockData(), n -> n);
                        setzen(f, s, wx, y, wz, neu);
                    }
                }
            }
        }
    }

    private void setzen(Fahrt f, BlockData[] s, int wx, int y, int wz, BlockData neu) {
        int i = y - f.yUnten;
        if (i < 0 || i >= s.length || s[i].equals(neu)) {
            return;
        }
        s[i] = neu;
        f.w.getBlockAt(wx, y, wz).setBlockData(neu, false);
    }

    // ------------------------------------------------------------------
    // Figuren
    // ------------------------------------------------------------------

    /** Alle Figuren im Bereich: Höhe am Schrittanfang merken (hoch: schon eine höher), Schwerkraft aus. */
    private void figurenMerken(Fahrt f, int versatz, boolean hoch) {
        f.startHoehen.clear();
        for (Entity e : f.w.getNearbyEntities(f.bereich(versatz))) {
            if (e.isInsideVehicle() || f.draussen.contains(e) || (e instanceof Player p && p.getGameMode() == GameMode.SPECTATOR)) {
                continue;
            }
            if (!(e instanceof Player) && e.hasGravity()) {
                e.setGravity(false);
                f.ohneSchwerkraft.add(e);
            }
            if (e instanceof Display d) {
                f.gleiten.putIfAbsent(d, d.getTeleportDuration());
                d.setTeleportDuration(TICKS_JE_SCHRITT);              // gleitet flüssig mit
            }
            f.startHoehen.put(e, e.getLocation().getY() + (hoch ? 1 : 0));
        }
    }

    /** Beim Hochfahren: alle Figuren genau auf ihrer Zielhöhe halten (ohne Fallen). */
    private void halten(Fahrt f) {
        for (Map.Entry<Entity, Double> e : f.startHoehen.entrySet()) {
            Entity en = e.getKey();
            if (en.isValid() && !(en instanceof Display) && Math.abs(en.getLocation().getY() - e.getValue()) > 1.0E-3) {
                versetzen(en, e.getValue() - en.getLocation().getY());
            }
            en.setFallDistance(0);
        }
    }

    private static void versetzen(Entity e, double dy) {
        if (!e.isValid() || Math.abs(dy) < 1.0E-6) {
            return;
        }
        Location l = e.getLocation().add(0, dy, 0);
        e.teleport(l, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        e.setVelocity(new org.bukkit.util.Vector());
        e.setFallDistance(0);
    }

    /** Wer mitfährt, nimmt keinen Schaden durch Wände oder Fallen. */
    @EventHandler(ignoreCancelled = true)
    public void beimSchaden(EntityDamageEvent e) {
        if (fahrten.isEmpty() || (e.getCause() != EntityDamageEvent.DamageCause.SUFFOCATION && e.getCause() != EntityDamageEvent.DamageCause.FALL)) {
            return;
        }
        for (Fahrt f : fahrten.values()) {
            if ((f.phase == Phase.RUNTER || f.phase == Phase.HOCH) && f.gesperrt != null
                    && f.gesperrt.contains(e.getEntity().getLocation().toVector())) {
                e.setCancelled(true);
            }
        }
    }

    // ------------------------------------------------------------------
    // Wiese und Tarnbäume
    // ------------------------------------------------------------------

    /** Ein Ring der Wiese über dem Loch: schließen (Erde, oben Gras) oder öffnen (Luft). */
    private void deckelRing(Fahrt f, int ring, boolean zu, boolean sofort) {
        int gras = plugin.bauplan().bodenY, von = gras - DECKE + 1, gesetzt = 0;
        BlockData luft = Material.AIR.createBlockData(), erde = Material.DIRT.createBlockData(), rasen = Material.GRASS_BLOCK.createBlockData();
        for (int x = 0; x < f.bx; x++) {
            for (int z = 0; z < f.bz; z++) {
                if (Math.min(Math.min(x, z), Math.min(f.bx - 1 - x, f.bz - 1 - z)) != ring) {
                    continue;
                }
                for (int y = von; y <= gras; y++) {
                    f.v.block(x, y, z).setBlockData(!zu ? luft : y == gras ? rasen : erde, false);
                }
                if (!sofort && ++gesetzt % 7 == 0) {
                    Location l = f.v.ort(plugin.bauplan(), x + 0.5, gras + 1.1, z + 0.5);
                    f.w.spawnParticle(zu ? Particle.CLOUD : Particle.END_ROD, l, 1, 0.2, 0.1, 0.2, 0.02);
                }
            }
        }
        if (!sofort && ring % 4 == 0) {
            f.w.playSound(mitte(f.v, gras), zu ? Sound.BLOCK_PISTON_CONTRACT : Sound.BLOCK_PISTON_EXTEND, SoundCategory.BLOCKS, 3.0F, 0.6F + ring * 0.01F);
        }
    }

    /** Ein Tarnbaum (Eiche oder Birke) mit Gras und Blumen setzen oder wieder entfernen (Lage aus festem Zufall). */
    private void tarnung(Fahrt f, int nr, boolean setzen, boolean sofort) {
        Random zufall = new Random(((long) f.v.x() * 31 + f.v.y()) * 31 + f.v.z() + nr * 7919L);
        int gras = plugin.bauplan().bodenY;
        int x = 8 + zufall.nextInt(f.bx - 16), z = 8 + zufall.nextInt(f.bz - 16);
        boolean birke = zufall.nextInt(3) == 0;
        int hoehe = 4 + zufall.nextInt(3);
        BlockData stamm = (birke ? Material.BIRCH_LOG : Material.OAK_LOG).createBlockData();
        Leaves laub = (Leaves) (birke ? Material.BIRCH_LEAVES : Material.OAK_LEAVES).createBlockData();
        laub.setPersistent(true);
        List<Map.Entry<Block, BlockData>> teile = new ArrayList<>();
        for (int dy = hoehe - 2; dy <= hoehe + 1; dy++) {
            int r = dy >= hoehe ? 1 : 2;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    boolean ecke = Math.abs(dx) == r && Math.abs(dz) == r;
                    if ((dx != 0 || dz != 0 || dy > hoehe) && !(ecke && (r == 1 || zufall.nextBoolean()))) {
                        teile.add(Map.entry(f.v.block(x + dx, gras + 1 + dy, z + dz), laub));
                    }
                }
            }
        }
        for (int dy = 0; dy < hoehe + 1; dy++) {
            teile.add(Map.entry(f.v.block(x, gras + 1 + dy, z), stamm));
        }
        Material[] deko = {Material.SHORT_GRASS, Material.SHORT_GRASS, Material.DANDELION, Material.POPPY, Material.OXEYE_DAISY};
        for (int i = 0; i < 6; i++) {
            int bx = Math.max(1, Math.min(f.bx - 2, x + zufall.nextInt(11) - 5));
            int bz = Math.max(1, Math.min(f.bz - 2, z + zufall.nextInt(11) - 5));
            teile.add(Map.entry(f.v.block(bx, gras + 1, bz), deko[zufall.nextInt(deko.length)].createBlockData()));
        }
        for (Map.Entry<Block, BlockData> t : teile) {
            Block b = t.getKey();
            if (setzen ? b.getType().isAir() : b.getType() == t.getValue().getMaterial()) {
                b.setBlockData(setzen ? t.getValue() : Material.AIR.createBlockData(), false);
            }
        }
        if (!sofort) {
            Location fuss = f.v.ort(plugin.bauplan(), x + 0.5, gras + 3, z + 0.5);
            f.w.spawnParticle(setzen ? Particle.HAPPY_VILLAGER : Particle.POOF, fuss, 25, 1.5, 2.0, 1.5, 0.05);
            f.w.playSound(fuss, setzen ? Sound.BLOCK_GRASS_PLACE : Sound.BLOCK_GRASS_BREAK, SoundCategory.BLOCKS, 1.5F, 0.8F);
        }
    }

    /** Staub am Rand des Lochs, Kolben- und Maschinengeräusche je Schritt. */
    private void effekte(Fahrt f, int hoehe, boolean runter, boolean sofort) {
        if (sofort) {
            return;
        }
        Random r = new Random();
        for (int i = 0; i < 24; i++) {
            int seite = r.nextInt(4);
            int x = seite == 0 ? 0 : seite == 1 ? f.bx - 1 : r.nextInt(f.bx);
            int z = seite == 2 ? 0 : seite == 3 ? f.bz - 1 : r.nextInt(f.bz);
            f.w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, f.v.ort(plugin.bauplan(), x + 0.5, plugin.bauplan().bodenY + 1.0, z + 0.5),
                    1, 0.3, 0.2, 0.3, 0.01);
        }
        Location m = mitte(f.v, hoehe);
        f.w.playSound(m, runter ? Sound.BLOCK_PISTON_CONTRACT : Sound.BLOCK_PISTON_EXTEND, SoundCategory.BLOCKS, 3.0F, 0.5F + r.nextFloat() * 0.2F);
        if (hoehe % 4 == 0) {
            f.w.playSound(m, "stinkendreich:laser", SoundCategory.BLOCKS, 1.0F, 0.5F);
        }
    }

    private void melden(Villen.Villa v, String text) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.zugang().istBewohner(p, v)) {
                p.sendActionBar(Component.text(text, NamedTextColor.DARK_AQUA));
            }
        }
    }
}
