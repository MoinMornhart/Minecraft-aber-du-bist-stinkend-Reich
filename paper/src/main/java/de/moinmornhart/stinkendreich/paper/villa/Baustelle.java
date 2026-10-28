package de.moinmornhart.stinkendreich.paper.villa;

import de.moinmornhart.stinkendreich.paper.Nbt;
import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import de.moinmornhart.stinkendreich.paper.block.Zustand;
import de.moinmornhart.stinkendreich.paper.strasse.Natur;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Baut eine Villa über viele Ticks verteilt, damit der Server dabei nicht stehen bleibt (wie {@code VillaBaustelle}
 * in der Mod): Chunks im Hintergrund laden, Bodenhöhe messen, Platz auf Spielerbauten prüfen, Gelände ebnen und
 * anblenden, Laub wegräumen, Bauplan setzen (je Tick höchstens {@link #BUDGET_NS}), Figuren aufstellen, ausstatten.
 * Den Auftrag (Platz, Besitzer, Fortschritt) führt die {@link Bauleitung}; ist die Bodenhöhe schon bekannt, war der
 * Bau vor einem Neustart angefangen und geht ohne Messen und Prüfen an derselben Stelle weiter.
 */
public final class Baustelle {
    private enum Schritt { LADEN, MESSEN, PRUEFEN, GELAENDE, BLAETTER, BLOECKE, FIGUREN, AUSSTATTEN, FERTIG }

    /** Ab so vielen Spalten mit gebauten Blöcken über dem Boden gilt ein Platz als bebaut. */
    private static final int BEBAUT_SPALTEN = 4;
    /** Ab so vielen eindeutigen Spielerblöcken (Fackeln, Werkbank …) unter dem Grundstück gilt ein Platz als bebaut. */
    private static final int BEBAUT_UNTEN = 3;

    /** Rechenzeit je Tick (Tests können sie mit STINKENDREICH_BAUZEIT_MS kürzen, um einen Bau mittendrin zu stoppen). */
    private static final long BUDGET_NS = budget();

    private final StinkendReichPaper plugin;
    private final Bauleitung leitung;
    private final Bauleitung.Auftrag auftrag;
    private final Bauplan plan;
    private final World welt;
    private final int x0, z0, nr, rand;
    private final UUID besitzer;
    private final String besitzerName;
    private final List<Chunk> geladen = new ArrayList<>();
    private final long start = System.currentTimeMillis();
    private Schritt schritt = Schritt.LADEN;
    private int angefordert, zeiger, y0;
    private int[] hoehen;
    /** Prüfung: Spalten mit gebauten Blöcken über dem Boden, Spielerblöcke darunter. */
    private int bebautOben, bebautUnten;
    /** Erster gefundener gebauter Block (fürs Protokoll). */
    private Block beispiel;
    private long laengsterTick;
    private BukkitTask aufgabe;

    private static long budget() {
        try {
            String ms = System.getenv("STINKENDREICH_BAUZEIT_MS");
            return ms == null ? 20_000_000L : Math.max(1, Long.parseLong(ms)) * 1_000_000L;
        } catch (NumberFormatException e) {
            return 20_000_000L;
        }
    }

    Baustelle(StinkendReichPaper plugin, Bauleitung leitung, Bauleitung.Auftrag auftrag) {
        this.plugin = plugin;
        this.leitung = leitung;
        this.auftrag = auftrag;
        this.plan = plugin.bauplan();
        this.welt = auftrag.world();
        this.x0 = auftrag.x0;
        this.z0 = auftrag.z0;
        this.nr = auftrag.nr;
        this.besitzer = auftrag.besitzer;
        this.besitzerName = auftrag.name;
        this.rand = Math.max(4, plugin.getConfig().getInt("villa.anblendung", 12));
    }

    public void starten() {
        plugin.getLogger().info("Baustelle: Villa " + nr + (besitzerName.isEmpty() ? "" : " für " + besitzerName)
                + " bei " + x0 + " " + z0 + (auftrag.y0 != null ? " (geht nach Neustart weiter)" : ""));
        aufgabe = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    /** Server fährt herunter: aufhören, ohne die Bauleitung zu benachrichtigen (der Auftrag bleibt gemerkt). */
    void abbrechen() {
        schritt = Schritt.FERTIG;
        freigeben();
    }

    /** Fortschritt 0–100 (für die Anzeige über der Leiste). */
    public int prozent() {
        return switch (schritt) {
            case LADEN -> angefordert == 0 ? 0 : 10 * geladen.size() / angefordert;
            case MESSEN -> 12;
            case PRUEFEN -> 13;
            case GELAENDE -> 15 + 15 * zeiger / Math.max(1, plan.breiteX + 2 * rand);
            case BLAETTER -> 30;
            case BLOECKE -> 32 + 60 * zeiger / Math.max(1, plan.bloecke.size());
            case FIGUREN -> 94;
            case AUSSTATTEN -> 97;
            case FERTIG -> 100;
        };
    }

    private void tick() {
        long t0 = System.nanoTime(), ende = t0 + BUDGET_NS;
        try {
            switch (schritt) {
                case LADEN -> laden();
                case MESSEN -> messen(ende);
                case PRUEFEN -> pruefen(ende);
                case GELAENDE -> gelaende(ende);
                case BLAETTER -> blaetter(ende);
                case BLOECKE -> bloecke(ende);
                case FIGUREN -> figuren();
                case AUSSTATTEN -> ausstatten();
                case FERTIG -> { }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Baustelle: Fehler in " + schritt + ": " + e);
            e.printStackTrace();
            schritt = Schritt.FERTIG;
            freigeben();
            leitung.fehler(auftrag);
        }
        laengsterTick = Math.max(laengsterTick, System.nanoTime() - t0);
    }

    private void weiter(Schritt naechster) {
        plugin.getLogger().info("Baustelle: " + schritt + " → " + naechster + " nach " + (System.currentTimeMillis() - start) / 1000 + " s");
        schritt = naechster;
        zeiger = 0;
    }

    // ------------------------------------------------------------------
    // Schritte
    // ------------------------------------------------------------------

    private void laden() {
        if (angefordert == 0) {
            int r = rand + 8;
            for (int cx = (x0 - r) >> 4; cx <= (x0 + plan.breiteX + r) >> 4; cx++) {
                for (int cz = (z0 - r) >> 4; cz <= (z0 + plan.breiteZ + 40 + r) >> 4; cz++) {
                    angefordert++;
                    welt.getChunkAtAsync(cx, cz, true, chunk -> {
                        chunk.addPluginChunkTicket(plugin);
                        geladen.add(chunk);
                    });
                }
            }
            return;
        }
        // Erst weiter, wenn auch die Figuren der Chunks geladen sind: sonst legt ein fortgesetzter Bau die Anzeigen
        // der schon gesetzten Blöcke ein zweites Mal an
        if (geladen.size() >= angefordert && geladen.stream().allMatch(Chunk::isEntitiesLoaded)) {
            if (auftrag.y0 != null) {
                y0 = auftrag.y0;                        // nach Neustart: Gelände wurde schon angefasst
                weiter(Schritt.GELAENDE);
            } else {
                weiter(Schritt.MESSEN);
            }
        }
    }

    private void messen(long ende) {
        if (hoehen == null) {
            hoehen = new int[plan.breiteX * plan.breiteZ];
        }
        while (zeiger < plan.breiteX && System.nanoTime() < ende) {
            for (int z = 0; z < plan.breiteZ; z++) {
                hoehen[zeiger * plan.breiteZ + z] = bodenHoehe(welt, x0 + zeiger, z0 + z);
            }
            zeiger++;
        }
        if (zeiger >= plan.breiteX) {
            int[] sortiert = hoehen.clone();
            Arrays.sort(sortiert);
            int y = Math.max(sortiert[sortiert.length / 2], welt.getSeaLevel() + 1);
            y0 = Math.clamp(y, welt.getMinHeight() + plan.bodenY + 2, welt.getMaxHeight() - (plan.hoehe - plan.bodenY) - 2);
            plugin.getLogger().info("Baustelle: Bodenhöhe " + y0);
            if (auftrag.pruefen) {
                weiter(Schritt.PRUEFEN);
            } else {
                gelaendeAnfangen();
            }
        }
    }

    /**
     * Sucht Spielerbauten auf dem Platz: über dem Boden in allem, was Gelände, Zufahrt und Anblendung verändern,
     * darunter (Bunker, Garage) nach eindeutigen Spuren wie Fackeln oder einer Werkbank. Dörfer und andere Bauwerke
     * zählen mit – auch sie werden nicht überbaut.
     */
    private void pruefen(long ende) {
        int breite = plan.breiteX + 2 * rand;
        while (zeiger < breite && System.nanoTime() < ende) {
            int x = x0 - rand + zeiger;
            for (int z = z0 - rand; z < z0 + plan.breiteZ + rand + 40; z++) {
                Block fund = spalteBebaut(x, z);
                if (fund != null) {
                    bebautOben++;
                    if (beispiel == null) {
                        beispiel = fund;
                    }
                }
                boolean grundstueck = x >= x0 && x < x0 + plan.breiteX && z >= z0 && z < z0 + plan.breiteZ;
                if (grundstueck) {
                    for (int y = ursprungY(); y < y0 - 1; y++) {
                        Block b = welt.getBlockAt(x, y, z);
                        if (Natur.spielerSpur(b.getType())) {
                            bebautUnten++;
                            if (beispiel == null) {
                                beispiel = b;
                            }
                        }
                    }
                }
            }
            zeiger++;
            if (bebautOben >= BEBAUT_SPALTEN || bebautUnten >= BEBAUT_UNTEN) {
                freigeben();
                schritt = Schritt.FERTIG;
                // Ein Beispiel ins Protokoll (welcher Block den Platz sperrt), damit man es im Spiel nachsehen kann
                String wo = beispiel == null ? "" : ", z. B. " + beispiel.getType().getKey().getKey() + " bei "
                        + beispiel.getX() + " " + beispiel.getY() + " " + beispiel.getZ();
                leitung.platzBebaut(auftrag, (bebautOben >= BEBAUT_SPALTEN ? "Bauten über dem Boden" : "Spuren unter dem Boden") + wo);
                return;
            }
        }
        if (zeiger >= breite) {
            plugin.getLogger().info("Baustelle: Platz ist frei");
            gelaendeAnfangen();
        }
    }

    /** Der oberste gebaute Block dieser Spalte zwischen Boden und Himmel, oder null. */
    private Block spalteBebaut(int x, int z) {
        int oben = welt.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
        int unten = Math.max(bodenHoehe(welt, x, z) - 1, oben - 32);
        for (int y = oben; y >= unten; y--) {
            Block b = welt.getBlockAt(x, y, z);
            if (Natur.gebaut(b.getType())) {
                return b;
            }
        }
        return null;
    }

    /** Ab hier wird die Welt verändert: Bodenhöhe bei der Bauleitung merken (Neustart geht hier weiter). */
    private void gelaendeAnfangen() {
        auftrag.y0 = y0;
        leitung.gelaendeBeginnt(auftrag);
        weiter(Schritt.GELAENDE);
    }

    private void gelaende(long ende) {
        int reihen = plan.breiteX + 2 * rand;
        while (zeiger < reihen && System.nanoTime() < ende) {
            gelaendeReihe(x0 - rand + zeiger);
            zeiger++;
        }
        if (zeiger >= reihen) {
            weiter(Schritt.BLAETTER);
        }
    }

    private void blaetter(long ende) {
        int r = rand + 8, reihen = plan.breiteX + 2 * r;
        while (zeiger < reihen && System.nanoTime() < ende) {
            blaetterReihe(x0 - r + zeiger, r);
            zeiger++;
        }
        if (zeiger >= reihen) {
            // Tiere, die gleich in Wänden stecken würden
            int uy = ursprungY();
            for (Entity e : welt.getNearbyEntities(new org.bukkit.util.BoundingBox(x0, uy, z0, x0 + plan.breiteX,
                    uy + plan.hoehe, z0 + plan.breiteZ), e -> e instanceof Mob)) {
                e.remove();
            }
            weiter(Schritt.BLOECKE);
        }
    }

    /** Ursprung des Bauplans: seine Grasschicht liegt auf y0 − 1. */
    private int ursprungY() {
        return y0 - 1 - plan.bodenY;
    }

    private void bloecke(long ende) {
        int uy = ursprungY();
        while (zeiger < plan.bloecke.size() && System.nanoTime() < ende) {
            Bauplan.Eintrag e = plan.bloecke.get(zeiger++);
            Block b = welt.getBlockAt(x0 + e.x(), uy + e.y(), z0 + e.z());
            Object p = plan.palette[e.palette()];
            if (p instanceof BlockData d) {
                b.setBlockData(d, false);
                if (e.daten() != null) {
                    inhalt(b, e.daten());
                }
            } else if (p instanceof Zustand z) {
                plugin.bloecke().setzen(b, z);
                if (e.daten() != null) {
                    plugin.bloecke().datenSetzen(b, e.daten());
                }
            }
        }
        if (zeiger >= plan.bloecke.size()) {
            weiter(auftrag.figurenFertig ? Schritt.AUSSTATTEN : Schritt.FIGUREN);
        }
    }

    /** Truhen und Fässer füllen (Gegenstände wie im Bauplan, über das Datenformat von Minecraft). */
    private void inhalt(Block b, Map<String, Object> daten) {
        if (!(b.getState(false) instanceof Container behaelter)) {
            return;
        }
        for (Object o : Nbt.liste(daten, "Items")) {
            @SuppressWarnings("unchecked") Map<String, Object> ding = new HashMap<>((Map<String, Object>) o);
            int platz = Nbt.zahl(ding, "Slot", -1);
            ding.remove("Slot");
            ding.put("DataVersion", plan.datenVersion);
            try {
                ItemStack stapel = modGegenstand(String.valueOf(ding.get("id")));
                if (stapel == null) {
                    stapel = ItemStack.deserializeBytes(Nbt.schreibenGzip(ding));
                }
                if (platz >= 0 && platz < behaelter.getInventory().getSize()) {
                    behaelter.getInventory().setItem(platz, stapel);
                }
            } catch (Exception ex) {
                plugin.getLogger().fine("Truheninhalt übersprungen: " + ex.getMessage());
            }
        }
    }

    /**
     * Gegenstände der Mod gibt es auf Paper nicht: Zugangskarten werden Karten dieser Villa, die Agenten-Ausrüstung
     * kommt vom Plugin. {@code null} = Gegenstand aus dem Spiel.
     */
    private ItemStack modGegenstand(String id) {
        if (!id.startsWith("stinkendreich:")) {
            return null;
        }
        String name = id.substring("stinkendreich:".length());
        if (name.equals("zugangskarte")) {
            return plugin.zugang().karte(new Villen.Villa(welt.getUID(), x0, ursprungY(), z0, nr, besitzer, besitzerName));
        }
        ItemStack s = plugin.agent().item(name);
        if (s == null) {
            throw new IllegalArgumentException("unbekannt: " + id);
        }
        return s;
    }

    private void figuren() {
        int uy = ursprungY();
        int n = 0;
        for (Bauplan.Figur f : plan.figuren) {
            Location ort = new Location(welt, x0 + f.x(), uy + f.y(), z0 + f.z());
            if (plugin.figurenUebersetzen().aufstellen(f, ort)) {
                n++;
            }
        }
        plugin.getLogger().info("Baustelle: " + n + " von " + plan.figuren.size() + " Figuren aufgestellt");
        leitung.figurenAufgestellt(auftrag);
        weiter(Schritt.AUSSTATTEN);
    }

    private void ausstatten() {
        Villen.Villa villa = new Villen.Villa(welt.getUID(), x0, ursprungY(), z0, nr, besitzer, besitzerName);
        plugin.ausstattung().ausstatten(villa, y0, rand);
        plugin.villen().hinzufuegen(villa);
        weiter(Schritt.FERTIG);
        freigeben();
        plugin.getLogger().info("Baustelle: Villa " + nr + " fertig nach " + (System.currentTimeMillis() - start) / 1000
                + " s (längster Tick " + laengsterTick / 1_000_000 + " ms)");
        leitung.fertig(auftrag, villa);
    }

    private void freigeben() {
        if (aufgabe != null) {
            aufgabe.cancel();
        }
        for (Chunk c : geladen) {
            c.removePluginChunkTicket(plugin);
        }
        geladen.clear();
    }

    // ------------------------------------------------------------------
    // Gelände (wie VillaPlatzierung in der Mod)
    // ------------------------------------------------------------------

    /** Höhe des natürlichen Bodens (erste Luft über Erde/Stein), ohne Bäume und Pflanzen. */
    public static int bodenHoehe(World welt, int x, int z) {
        int y = welt.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE) + 1;
        int min = welt.getMinHeight();
        while (y > min) {
            Block b = welt.getBlockAt(x, y - 1, z);
            if (!istBewuchs(b) && !b.isLiquid()) {
                break;
            }
            y--;
        }
        return y;
    }

    private static boolean istBewuchs(Block b) {
        Material m = b.getType();
        return m.isAir() || Tag.LEAVES.isTagged(m) || Tag.LOGS.isTagged(m) || b.isReplaceable() || Tag.FLOWERS.isTagged(m)
                || m == Material.BAMBOO || m == Material.CACTUS || m == Material.SUGAR_CANE || m == Material.BROWN_MUSHROOM_BLOCK
                || m == Material.RED_MUSHROOM_BLOCK || m == Material.MUSHROOM_STEM;
    }

    /** Ebnet eine Reihe x des Grundstücks samt angeblendetem Rand. */
    private void gelaendeReihe(int x) {
        int bx = plan.breiteX, bz = plan.breiteZ, meer = welt.getSeaLevel();
        for (int z = z0 - rand; z < z0 + bz + rand; z++) {
            int dx = Math.max(Math.max(x0 - x, x - (x0 + bx - 1)), 0);
            int dz = Math.max(Math.max(z0 - z, z - (z0 + bz - 1)), 0);
            double abstand = Math.sqrt(dx * dx + dz * dz);
            if (abstand >= rand) {
                continue;
            }
            int boden = bodenHoehe(welt, x, z);
            double t = abstand / rand;
            double weich = t * t * (3 - 2 * t);
            int ziel = (int) Math.round(y0 + (boden - y0) * weich);
            int oben = Math.max(welt.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE) + 1, ziel) + 1;
            for (int y = Math.min(boden, ziel) - 4; y < ziel; y++) {
                Block b = welt.getBlockAt(x, y, z);
                Material neu = y == ziel - 1 ? Material.GRASS_BLOCK : (y >= ziel - 4 ? Material.DIRT : Material.STONE);
                if (y < boden && !b.getType().isAir() && !b.isLiquid() && y < ziel - 1) {
                    continue;
                }
                if (b.getType() != neu) {
                    b.setType(neu, false);
                }
            }
            for (int y = ziel; y <= oben; y++) {
                Block b = welt.getBlockAt(x, y, z);
                if (b.getType().isAir()) {
                    continue;
                }
                boolean nass = abstand > 0 && y < meer && b.isLiquid();
                b.setType(nass ? Material.WATER : Material.AIR, false);
            }
        }
    }

    /** Lose Blätter gefällter Bäume am Rand entfernen (sonst zerfallen sie und lassen Setzlinge fallen). */
    private void blaetterReihe(int x, int r) {
        for (int z = z0 - r; z < z0 + plan.breiteZ + r; z++) {
            int oben = welt.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE) + 1;
            for (int y = oben - 1; y > oben - 24; y--) {
                Block b = welt.getBlockAt(x, y, z);
                if (Tag.LEAVES.isTagged(b.getType()) && b.getBlockData() instanceof Leaves l && !l.isPersistent()
                        && !stammInDerNaehe(b)) {
                    b.setType(Material.AIR, false);
                }
            }
        }
    }

    private static boolean stammInDerNaehe(Block mitte) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    if (Tag.LOGS.isTagged(mitte.getRelative(dx, dy, dz).getType())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
