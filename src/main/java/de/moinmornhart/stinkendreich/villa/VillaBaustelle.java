package de.moinmornhart.stinkendreich.villa;

import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.mixin.StrukturZugriff;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Baut eine Nachbarvilla über viele Server-Ticks verteilt, damit der Server dabei nicht hängt (in einem Rutsch dauerte
 * das gut 18 Sekunden, in denen niemand spielen konnte).
 * <p>
 * Ablauf: den flachsten freien Platz in den inneren Ringen suchen (nur geschätzt, ohne Chunks), Chunks im Hintergrund erzeugen lassen (Lade-Ticket; Minecraft erzeugt sie auf seinen eigenen Threads),
 * Bodenhöhe messen, Gelände ebnen, lose Blätter wegräumen, den Bauplan Block für Block setzen, danach die Formen
 * angleichen (Zäune, Scheiben und Treppen verbinden sich wie beim normalen Setzen einer Struktur), Figuren aufstellen und
 * zuletzt Zufahrt, Festung, Butler, Fernseher und Fahrzeuge. Jeder Tick arbeitet höchstens {@link #BUDGET_NS}.
 * Mehrere Aufträge (zwei neue Spieler gleichzeitig) warten in einer Schlange.
 */
public final class VillaBaustelle {
    /** Arbeitszeit je Tick (ein Tick hat 50 ms, der Rest bleibt dem Spiel). */
    private static final long BUDGET_NS = 20_000_000L;
    /** So lange wird höchstens auf die Chunks gewartet; danach werden die fehlenden direkt geladen. */
    private static final int LADEN_MAX_TICKS = 20 * 120;
    /** Rand ums Grundstück, der fertig erzeugt sein muss: Anblendung, Zufahrt, Minengürtel (40) und Notausgang (~55). */
    private static final int RAND_LADEN = 64;
    /** Setzen ohne Nachbar-Updates; die Formen werden danach in einem eigenen Schritt angeglichen. */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private enum Schritt { SUCHEN, LADEN, MESSEN, GELAENDE, BLAETTER, BLOECKE, FORMEN, FIGUREN, AUSSTATTEN }

    /** So viele freie Plätze im Raster werden verglichen (die beiden innersten Ringe: 8 + 16). */
    private static final int KANDIDATEN = 24;
    /** So viele Chunks dürfen gleichzeitig unterwegs sein (alle auf einmal ließen den Server beim Fertigstellen stocken). */
    private static final int CHUNKS_GLEICHZEITIG = 40;
    /** Schätzpunkte je Platz in jeder Richtung (5 × 5) und Zeit dafür je Tick. */
    private static final int RASTER = 5;
    private static final long SUCH_BUDGET_NS = 15_000_000L;
    /** Die Strukturdatei der Villa, im Hintergrund geladen (das erste Laden dauert einige Sekunden). */
    private static java.util.concurrent.CompletableFuture<StructureTemplate> vorlage;

    /** Ein Chunk (eigener Datensatz: ChunkPos hat ab 26.1 keine öffentlichen Felder mehr). */
    private record Chunk(int x, int z) {
    }

    /** Ein Auftrag: für wen (null = Testvilla ohne Besitzer) und was danach passieren soll. */
    private record Auftrag(UUID besitzer, String name, Consumer<VillaPlatzierung.VillaInfo> fertig) {
    }

    private static final Deque<Auftrag> SCHLANGE = new ArrayDeque<>();
    private static VillaBaustelle aktuell;

    private final MinecraftServer server;
    private final ServerLevel welt;
    private final Auftrag auftrag;
    private final VillaDaten plan = VillaDaten.get();
    private final int nr, rand;
    private int x0, z0;
    /** Freie Plätze im Raster (x0, z0, Ring) und der beste bisher (Wertung: je kleiner, desto flacher und näher). */
    private List<int[]> kandidaten;
    private int[] bester;
    private int besteWertung = Integer.MAX_VALUE;
    /** Stand der Schätzung beim aktuellen Kandidaten: nächster Punkt, kleinste/größte Höhe, Punkte unter Wasser. */
    private int punkt, tiefster = Integer.MAX_VALUE, hoechster = Integer.MIN_VALUE, nass;
    private final List<Chunk> chunks = new ArrayList<>();
    private final long start = System.currentTimeMillis();
    private Schritt schritt = Schritt.SUCHEN;
    private int ticks, zeiger, y0;
    /** Längste Arbeitszeit in einem Tick (Nanosekunden), fürs Log. */
    private long laengsterTick;
    private int[] hoehen;
    private BlockPos ursprung;
    private List<StructureTemplate.StructureBlockInfo> bloecke;

    private VillaBaustelle(MinecraftServer server, Auftrag auftrag) {
        this.server = server;
        this.welt = server.overworld();
        this.auftrag = auftrag;
        this.nr = VillaPlatzierung.alle().size();
        this.rand = VillaPlatzierung.anblendung();
        this.kandidaten = VillaPlatzierung.freiePlaetze(KANDIDATEN);
    }

    /**
     * Schätzt einen Punkt des Kandidaten, ohne Chunks zu erzeugen (der Weltgenerator rechnet nur die Höhe aus; in
     * älteren Versionen kostet das einige Millisekunden je Punkt). true, wenn der Kandidat damit fertig ist.
     */
    private boolean schaetzen(int[] k) {
        var quelle = welt.getChunkSource();
        int rx = plan.breiteX + 2 * rand, rz = plan.breiteZ + 2 * rand;
        int i = punkt / RASTER, j = punkt % RASTER;
        int x = k[0] - rand + rx * i / (RASTER - 1), z = k[1] - rand + rz * j / (RASTER - 1);
        int h = quelle.getGenerator().getBaseHeight(x, z, net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG,
                welt, quelle.randomState());
        tiefster = Math.min(tiefster, h);
        hoechster = Math.max(hoechster, h);
        if (h < welt.getSeaLevel() - 1) {
            nass++;
        }
        return ++punkt >= RASTER * RASTER;
    }

    /** Wertung eines fertig geschätzten Kandidaten: Höhenunterschied + 12 je Punkt unter Wasser + 12 je Ring nach außen. */
    private int wertung(int[] k) {
        int w = (hoechster - tiefster) + 12 * nass + 12 * k[2];
        punkt = 0;
        tiefster = Integer.MAX_VALUE;
        hoechster = Integer.MIN_VALUE;
        nass = 0;
        return w;
    }

    /** Platz steht fest: Chunks im Hintergrund anfordern. */
    private void platzGewaehlt() {
        x0 = bester[0];
        z0 = bester[1];
        for (int cx = (x0 - RAND_LADEN) >> 4; cx <= (x0 + plan.breiteX + RAND_LADEN) >> 4; cx++) {
            for (int cz = (z0 - RAND_LADEN) >> 4; cz <= (z0 + plan.breiteZ + RAND_LADEN) >> 4; cz++) {
                chunks.add(new Chunk(cx, cz));
            }
        }
        StinkendReich.LOGGER.info("Baustelle: Villa {} für {} bei {} {} (Wertung {}, {} Chunks werden erzeugt)", nr,
                auftrag.besitzer() == null ? "(Test)" : auftrag.name(), x0, z0, besteWertung, chunks.size());
    }

    /**
     * Lädt die Strukturdateien der Villa im Hintergrund (Minecrafts Ablage dafür ist thread-sicher). Beim ersten Bau
     * nach einem Neustart hing der Server sonst 5–6 s an dieser Stelle.
     */
    private static java.util.concurrent.CompletableFuture<StructureTemplate> vorlageLaden(MinecraftServer server) {
        if (vorlage == null) {
            vorlage = java.util.concurrent.CompletableFuture.supplyAsync(() -> VillaPlatzierung.vorlage(server));
        }
        return vorlage;
    }

    public static void registrieren() {
        ServerTickEvents.END_SERVER_TICK.register(VillaBaustelle::tick);
        // Mit mehreren Villen gleich nach dem Start vorladen, dann wartet der erste Neue nicht darauf
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            if (de.moinmornhart.stinkendreich.Konfig.get().mehrereVillen && !VillaPlatzierung.alle().isEmpty()) {
                vorlageLaden(s);
            }
        });
        // Beim Beenden nichts erzwungen geladen zurücklassen; die halbe Villa wird beim nächsten Start neu gebaut
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            if (aktuell != null) {
                aktuell.freigeben();
            }
            aktuell = null;
            SCHLANGE.clear();
            vorlage = null;
        });
    }

    /**
     * Bestellt eine Nachbarvilla. besitzer null = Testvilla. fertig läuft auf dem Server-Thread, sobald die Villa steht
     * (auch wenn der Besitzer inzwischen offline ist).
     */
    public static void bestellen(UUID besitzer, String name, Consumer<VillaPlatzierung.VillaInfo> fertig) {
        if (besitzer != null && wartet(besitzer)) {
            return;
        }
        SCHLANGE.add(new Auftrag(besitzer, name, fertig));
    }

    /** Wird für diesen Spieler gerade gebaut oder wartet sein Auftrag noch? */
    public static boolean wartet(UUID besitzer) {
        if (aktuell != null && besitzer.equals(aktuell.auftrag.besitzer())) {
            return true;
        }
        return SCHLANGE.stream().anyMatch(a -> besitzer.equals(a.besitzer()));
    }

    /** Fortschritt der Baustelle dieses Spielers in Prozent, -1 wenn sein Auftrag noch in der Schlange wartet. */
    public static int fortschritt(UUID besitzer) {
        return aktuell != null && besitzer.equals(aktuell.auftrag.besitzer()) ? aktuell.prozent() : -1;
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 == 0) {
            anzeigen(server);
        }
        if (aktuell == null) {
            Auftrag a = SCHLANGE.poll();
            if (a == null || VillaPlatzierung.alle().isEmpty()) {
                return;
            }
            aktuell = new VillaBaustelle(server, a);
        }
        try {
            long t0 = System.nanoTime();
            boolean fertig = aktuell.arbeiten();
            aktuell.laengsterTick = Math.max(aktuell.laengsterTick, System.nanoTime() - t0);
            if (fertig) {
                aktuell = null;
            }
        } catch (RuntimeException e) {
            StinkendReich.LOGGER.error("Baustelle: Villa {} konnte nicht gebaut werden", aktuell.nr, e);
            aktuell.freigeben();
            aktuell = null;
        }
    }

    /** Jede Sekunde: Wer auf seine Villa wartet, sieht den Fortschritt über der Schnellleiste. */
    private static void anzeigen(MinecraftServer server) {
        List<Auftrag> alle = new ArrayList<>(SCHLANGE);
        if (aktuell != null) {
            alle.add(aktuell.auftrag);
        }
        for (Auftrag a : alle) {
            net.minecraft.server.level.ServerPlayer p = a.besitzer() == null ? null : server.getPlayerList().getPlayer(a.besitzer());
            if (p != null) {
                int prozent = fortschritt(a.besitzer());
                de.moinmornhart.stinkendreich.Kompat.nachricht(p, prozent < 0
                        ? net.minecraft.network.chat.Component.translatable("stinkendreich.villa.warteschlange")
                        : net.minecraft.network.chat.Component.translatable("stinkendreich.villa.baustelle", prozent), true);
            }
        }
    }

    /** Ein Tick Arbeit; true, wenn die Villa fertig ist. */
    private boolean arbeiten() {
        long ende = System.nanoTime() + BUDGET_NS;
        ticks++;
        while (System.nanoTime() < ende) {
            long t0 = System.nanoTime();
            Schritt vorher = schritt;
            int zeigerVorher = zeiger;
            switch (schritt) {
                case SUCHEN -> {
                    // Ein Kandidat je Tick: flachster freier Platz in den inneren Ringen
                    if (zeiger >= kandidaten.size() && bester == null && kandidaten.size() < 200) {
                        kandidaten = VillaPlatzierung.freiePlaetze(kandidaten.size() + 24);  // alle belegt: weiter außen suchen
                    }
                    if (zeiger < kandidaten.size()) {
                        int[] k = kandidaten.get(zeiger);
                        // Plätze, über die schon eine Straße führt (samt Minengürtel), kommen nicht in Frage
                        if (punkt == 0 && de.moinmornhart.stinkendreich.strasse.Strassennetz.get(server).strasseIn(k[0] - RAND_LADEN,
                                k[1] - RAND_LADEN, k[0] + plan.breiteX + RAND_LADEN, k[1] + plan.breiteZ + RAND_LADEN)) {
                            zeiger++;
                            return false;
                        }
                        // So viele Punkte, wie in 15 ms passen; der Rest im nächsten Tick
                        long suchEnde = System.nanoTime() + SUCH_BUDGET_NS;
                        while (System.nanoTime() < suchEnde) {
                            if (schaetzen(k)) {
                                int w = wertung(k);
                                if (w < besteWertung) {
                                    besteWertung = w;
                                    bester = k;
                                }
                                zeiger++;
                                break;
                            }
                        }
                        return false;
                    }
                    zeiger = 0;
                    platzGewaehlt();
                    weiter(Schritt.LADEN);
                    return false;
                }
                case LADEN -> {
                    if (!alleGeladen()) {
                        return false;                          // nächsten Tick wieder nachsehen
                    }
                    StinkendReich.LOGGER.info("Baustelle: Chunks fertig nach {} s", (System.currentTimeMillis() - start) / 1000);
                    hoehen = new int[plan.breiteX * plan.breiteZ];
                    weiter(Schritt.MESSEN);
                }
                case MESSEN -> {
                    VillaPlatzierung.hoehenReihe(welt, x0, z0, plan, zeiger, hoehen);
                    if (++zeiger >= plan.breiteX) {
                        y0 = VillaPlatzierung.zielhoehe(welt, plan, hoehen);
                        ursprung = VillaPlatzierung.ursprung(x0, y0, z0, plan);
                        zeiger = x0 - rand;
                        weiter(Schritt.GELAENDE);
                    }
                }
                case GELAENDE -> {
                    VillaPlatzierung.gelaendeReihe(welt, zeiger, x0, z0, plan.breiteX, plan.breiteZ, y0, rand);
                    if (++zeiger >= x0 + plan.breiteX + rand) {
                        zeiger = x0 - rand - 8;
                        weiter(Schritt.BLAETTER);
                    }
                }
                case BLAETTER -> {
                    if (zeiger < x0 + plan.breiteX + rand + 8) {
                        VillaPlatzierung.blaetterReihe(welt, zeiger++, z0, plan.breiteZ, rand + 8);
                    } else {
                        if (!vorlageLaden(server).isDone()) {
                            return false;                      // Strukturdatei lädt noch im Hintergrund
                        }
                        VillaPlatzierung.tiereEntfernen(welt, x0, y0, z0, plan);
                        bloecke = ((StrukturZugriff) vorlageLaden(server).join()).stinkendreich$paletten().get(0).blocks();
                        zeiger = 0;
                        weiter(Schritt.BLOECKE);
                    }
                }
                case BLOECKE -> {
                    // Ein paar hundert Blöcke am Stück, dann wieder auf die Uhr schauen
                    for (int i = 0; i < 256 && zeiger < bloecke.size(); i++) {
                        setzen(bloecke.get(zeiger++));
                    }
                    if (zeiger >= bloecke.size()) {
                        zeiger = 0;
                        weiter(Schritt.FORMEN);
                    }
                }
                case FORMEN -> {
                    for (int i = 0; i < 256 && zeiger < bloecke.size(); i++) {
                        formen(bloecke.get(zeiger++));
                    }
                    if (zeiger >= bloecke.size()) {
                        weiter(Schritt.FIGUREN);
                    }
                }
                case FIGUREN -> {
                    StrukturZugriff vorlage = (StrukturZugriff) vorlageLaden(server).join();
                    //? if >=1.21.6 {
                    vorlage.stinkendreich$figurenAufstellen(welt, ursprung, net.minecraft.world.level.block.Mirror.NONE,
                            net.minecraft.world.level.block.Rotation.NONE, BlockPos.ZERO, null, false,
                            net.minecraft.util.ProblemReporter.DISCARDING);
                    //?} else {
                    /*vorlage.stinkendreich$figurenAufstellen(welt, ursprung, net.minecraft.world.level.block.Mirror.NONE,
                            net.minecraft.world.level.block.Rotation.NONE, BlockPos.ZERO, null, false);
                    *///?}
                    weiter(Schritt.AUSSTATTEN);
                    return false;                              // Ausstatten bekommt einen eigenen Tick
                }
                case AUSSTATTEN -> {
                    VillaPlatzierung.VillaInfo villa = VillaPlatzierung.ausstatten(server, welt, ursprung, y0, nr,
                            auftrag.besitzer(), auftrag.name());
                    freigeben();
                    StinkendReich.LOGGER.info("Baustelle: Villa {} fertig bei {} nach {} s ({} Ticks, längster Tick {} ms)", nr, ursprung,
                            (System.currentTimeMillis() - start) / 1000, ticks, laengsterTick / 1_000_000);
                    VillaPlatzierung.fertigGebaut(server, villa);
                    // Straße vom Netz der Spawn-Villa bis vor das Tor der neuen Villa
                    if (de.moinmornhart.stinkendreich.Konfig.get().strassenBauen) {
                        de.moinmornhart.stinkendreich.strasse.Strassennetz.get(server).villaAnbinden(villa.nr());
                    }
                    auftrag.fertig().accept(villa);
                    return true;
                }
            }
            long dauer = System.nanoTime() - t0;
            if (dauer > 250_000_000L) {
                StinkendReich.LOGGER.warn("Baustelle: langsamer Schritt {} bei Zeiger {}: {} ms", vorher, zeigerVorher, dauer / 1_000_000);
            }
        }
        // Während des Setzens geplante Fall- und Fließ-Ticks löschen: Beim Setzen in einem Rutsch gäbe es sie auch
        // nicht (Sand fiele sonst, solange der Block darunter noch fehlt)
        if (schritt == Schritt.BLOECKE) {
            ruhe();
        }
        return false;
    }

    private void weiter(Schritt neu) {
        if (schritt == Schritt.BLOECKE) {
            ruhe();
        }
        StinkendReich.LOGGER.info("Baustelle: {} → {} nach {} Ticks", schritt, neu, ticks);
        schritt = neu;
    }

    /** Wie viele Chunks schon ein Lade-Ticket haben (die ersten so vielen der Liste). */
    private int angefordert;

    /**
     * Fordert die Chunks in Gruppen an (Lade-Ticket, kein blockierendes Laden; setChunkForced würde jeden Chunk sofort
     * erzeugen) und meldet, ob alle da sind.
     */
    private boolean alleGeladen() {
        int unterwegs = 0;
        for (int i = 0; i < angefordert; i++) {
            if (welt.getChunkSource().getChunkNow(chunks.get(i).x(), chunks.get(i).z()) == null) {
                unterwegs++;
            }
        }
        while (unterwegs < CHUNKS_GLEICHZEITIG && angefordert < chunks.size()) {
            Chunk c = chunks.get(angefordert++);
            welt.getChunkSource().updateChunkForced(new net.minecraft.world.level.ChunkPos(c.x(), c.z()), true);
            unterwegs++;
        }
        if (angefordert < chunks.size()) {
            return false;
        }
        if (ticks > LADEN_MAX_TICKS) {
            StinkendReich.LOGGER.warn("Baustelle: Chunks kamen nicht rechtzeitig, die restlichen werden direkt geladen");
            for (Chunk c : chunks) {
                welt.getChunk(c.x(), c.z());
            }
            return true;
        }
        for (Chunk c : chunks) {
            if (welt.getChunkSource().getChunkNow(c.x(), c.z()) == null) {
                return false;
            }
        }
        return true;
    }

    /** Ein Block aus dem Bauplan, samt Inhalt (Truhen, Schilder, Mechanik-Blöcke). */
    private void setzen(StructureTemplate.StructureBlockInfo info) {
        BlockPos p = ursprung.offset(info.pos());
        if (info.nbt() != null && welt.getBlockEntity(p) instanceof Clearable inhalt) {
            inhalt.clearContent();                             // nichts aus dem Gelände fällt heraus
        }
        welt.setBlock(p, info.state(), FLAGS);
        if (info.nbt() != null) {
            BlockEntity be = welt.getBlockEntity(p);
            if (be != null) {
                CompoundTag daten = info.nbt().copy();
                //? if >=1.21.6 {
                be.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(
                        net.minecraft.util.ProblemReporter.DISCARDING, welt.registryAccess(), daten));
                //?} else
                /*be.loadWithComponents(daten, welt.registryAccess());*/
                be.setChanged();
            }
        }
    }

    /** Wie Minecraft nach dem Setzen einer Struktur: Form an die Nachbarn anpassen und Nachbarn benachrichtigen. */
    private void formen(StructureTemplate.StructureBlockInfo info) {
        if (info.state().isAir()) {
            return;
        }
        BlockPos p = ursprung.offset(info.pos());
        BlockState alt = welt.getBlockState(p);
        BlockState neu = Block.updateFromNeighbourShapes(alt, welt, p);
        if (neu != alt) {
            welt.setBlock(p, neu, FLAGS);
        }
        //? if >=1.21.5 {
        welt.updateNeighborsAt(p, neu.getBlock());
        //?} else
        /*welt.blockUpdated(p, neu.getBlock());*/
    }

    private void ruhe() {
        BoundingBox box = new BoundingBox(ursprung.getX() - 1, ursprung.getY() - 1, ursprung.getZ() - 1,
                ursprung.getX() + plan.breiteX, ursprung.getY() + plan.hoehe, ursprung.getZ() + plan.breiteZ);
        welt.getBlockTicks().clearArea(box);
        welt.getFluidTicks().clearArea(box);
    }

    private void freigeben() {
        for (Chunk c : chunks.subList(0, angefordert)) {
            welt.getChunkSource().updateChunkForced(new net.minecraft.world.level.ChunkPos(c.x(), c.z()), false);
        }
    }

    /** Grober Fortschritt in Prozent (Platz 0–2, Chunks 2–30, Gelände 30–45, Blöcke 45–85, Formen 85–98, Rest 98–100). */
    private int prozent() {
        return switch (schritt) {
            case SUCHEN -> 2 * zeiger / Math.max(1, kandidaten.size());
            case LADEN -> {
                long da = chunks.stream().filter(c -> welt.getChunkSource().getChunkNow(c.x(), c.z()) != null).count();
                yield 2 + (int) (28 * da / Math.max(1, chunks.size()));
            }
            case MESSEN -> 30 + 5 * zeiger / Math.max(1, plan.breiteX);
            case GELAENDE -> 35 + 7 * (zeiger - x0 + rand) / Math.max(1, plan.breiteX + 2 * rand);
            case BLAETTER -> 42 + 3 * (zeiger - x0 + rand + 8) / Math.max(1, plan.breiteX + 2 * rand + 16);
            case BLOECKE -> 45 + 40 * zeiger / Math.max(1, bloecke.size());
            case FORMEN -> 85 + 13 * zeiger / Math.max(1, bloecke.size());
            case FIGUREN, AUSSTATTEN -> 98;
        };
    }
}
