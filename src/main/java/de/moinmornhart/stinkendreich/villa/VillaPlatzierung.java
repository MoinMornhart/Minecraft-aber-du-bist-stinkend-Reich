package de.moinmornhart.stinkendreich.villa;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.Konfig;
import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.butler.ButlerVerwaltung;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Baut die Villa beim ersten Start einer neuen Welt am Spawn:
 * Gelände ebnen, Übergang ins natürliche Gelände anblenden, Strukturdatei setzen,
 * Spawnpunkt vor den Eingang legen. In einer Welt von vorher (ohne Villa) entsteht die erste Villa beim ersten Betreten
 * im Hintergrund ({@link VillaBaustelle}) auf dem nächsten Platz ohne Spielerbauten rund um den Spawn; wer sie auslöst,
 * wird ihr Herr.
 */
public final class VillaPlatzierung {
    /** Merkdatei im Weltordner: ob und wo die Villa gebaut wurde. */
    private static final String DATEI = StinkendReich.MOD_ID + "-villa.json";
    private static final String TAG_ANGEKOMMEN = StinkendReich.MOD_ID + ".angekommen";
    /** Hat sein goldenes Handy schon bekommen (seit 0.3, auch in Welten von vorher). */
    private static final String TAG_HANDY = StinkendReich.MOD_ID + ".handy";
    /** Wurde in seine eigene Nachbarvilla gebracht (seit 0.4). */
    private static final String TAG_EIGENE_VILLA = StinkendReich.MOD_ID + ".eigene_villa";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Alle Villen der geladenen Welt; Nummer 0 ist die Villa am Spawn, dahinter die Nachbarn (je Spieler eine). */
    private static final List<VillaInfo> VILLEN = new java.util.concurrent.CopyOnWriteArrayList<>();
    /** Merkdatei der laufenden Welt. */
    private static Path merkdatei;
    /** Welt von vorher ohne Villa: Die erste Villa entsteht beim ersten Betreten (sofern die Config es erlaubt). */
    private static boolean ersteVillaOffen;
    /** Weltspawn beim Start: Mitte der Plätze für die erste Villa in einer Welt von vorher. */
    private static BlockPos weltspawn = BlockPos.ZERO;
    /** In den ersten 10 Spielminuten einer Villa-Welt werden Reste zerfallender Blätter weggeräumt. */
    private static final long AUFRAEUMEN_BIS = 20L * 60 * 10;

    private VillaPlatzierung() {
    }

    public static void registrieren() {
        ServerLifecycleEvents.SERVER_STARTED.register(VillaPlatzierung::beimStart);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> VILLEN.clear());
        ServerTickEvents.END_SERVER_TICK.register(VillaPlatzierung::aufraeumen);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> beimBetreten(handler.getPlayer(), server));
    }

    /** Die Villa am Spawn (Nummer 0), falls vorhanden. */
    public static Optional<VillaInfo> villa() {
        return VILLEN.isEmpty() ? Optional.empty() : Optional.of(VILLEN.get(0));
    }

    /** Alle Villen der Welt (Nummer 0 zuerst). */
    public static List<VillaInfo> alle() {
        return VILLEN;
    }

    /** Die Villa mit dieser Nummer oder null. */
    public static VillaInfo nr(int nr) {
        return nr >= 0 && nr < VILLEN.size() ? VILLEN.get(nr) : null;
    }

    /**
     * Die Villa, zu deren Gelände diese Stelle gehört (Grundstück samt Minengürtel und Notausgang, waagerecht), oder
     * null. Die Villen liegen weit genug auseinander, dass es immer höchstens eine ist.
     */
    public static VillaInfo bei(BlockPos pos) {
        for (VillaInfo v : VILLEN) {
            if (v.inDerNaehe(pos, 72)) {
                return v;
            }
        }
        return null;
    }

    /** Name des Herrn einer Villa: bei Nachbarvillen der Besitzer, bei der Spawn-Villa ihr erster Bewohner. */
    public static String herrName(MinecraftServer server, VillaInfo v) {
        if (!v.besitzerName().isEmpty()) {
            return v.besitzerName();
        }
        var bewohner = de.moinmornhart.stinkendreich.mechanik.VillaZustand.get(server, v.nr()).bewohner();
        return bewohner.isEmpty() ? "" : bewohner.values().iterator().next();
    }

    /**
     * Die Villa, deren Herr im Text vorkommt („Villa von Max“, „fahr zu Max“, „Max's villa“), oder null. Längere Namen
     * gewinnen (damit „Maxi“ nicht bei „Max“ landet).
     */
    public static VillaInfo mitHerrnImText(MinecraftServer server, String text) {
        String n = " " + de.moinmornhart.stinkendreich.butler.Sprache.normalisieren(text) + " ";
        VillaInfo treffer = null;
        int laenge = 0;
        for (VillaInfo v : VILLEN) {
            String herr = de.moinmornhart.stinkendreich.butler.Sprache.normalisieren(herrName(server, v));
            if (!herr.isEmpty() && n.contains(" " + herr + " ") && herr.length() > laenge) {
                treffer = v;
                laenge = herr.length();
            }
        }
        return treffer;
    }

    /** Die Villa, die diesem Spieler gehört, oder null. */
    public static VillaInfo vonBesitzer(java.util.UUID spieler) {
        for (VillaInfo v : VILLEN) {
            if (spieler.equals(v.besitzer())) {
                return v;
            }
        }
        return null;
    }

    private static void beimStart(MinecraftServer server) {
        Path datei = server.getWorldPath(LevelResource.ROOT).resolve(DATEI);
        merkdatei = datei;
        VILLEN.clear();
        ersteVillaOffen = false;
        weltspawn = Kompat.spawnPunkt(server.overworld());
        boolean nachholenErlaubt = Konfig.get().villaBauen && Konfig.get().villaInBestehendenWelten;
        try {
            if (Files.exists(datei)) {
                JsonObject j = GSON.fromJson(Files.readString(datei, StandardCharsets.UTF_8), JsonObject.class);
                VillaInfo haupt = VillaInfo.ausJson(j, 0);
                if (haupt == null && nachholenErlaubt) {
                    // Welt ohne Villa (von vorher oder damals abgeschaltet): die erste Villa beim ersten Betreten bauen
                    ersteVillaOffen = true;
                    StinkendReich.LOGGER.info("Welt ohne Villa: die erste Villa entsteht beim ersten Betreten");
                }
                if (haupt != null) {
                    VILLEN.add(haupt);
                    if (j.has("nachbarn")) {
                        for (var e : j.getAsJsonArray("nachbarn")) {
                            VillaInfo n = VillaInfo.ausJson(e.getAsJsonObject(), VILLEN.size());
                            if (n != null) {
                                VILLEN.add(n);
                            }
                        }
                    }
                }
                return;
            }
            ServerLevel welt = server.overworld();
            JsonObject info = new JsonObject();
            if (welt.getGameTime() > 0 || !Konfig.get().villaBauen) {
                // Bestehende Welt (oder abgeschaltet): jetzt nichts verändern, nur merken; in einer Welt von vorher
                // entsteht die erste Villa beim ersten Betreten im Hintergrund auf einem freien Platz
                info.addProperty("gebaut", false);
                info.addProperty("grund", welt.getGameTime() > 0 ? "bestehende Welt" : "in der Config abgeschaltet");
                ersteVillaOffen = nachholenErlaubt;
                StinkendReich.LOGGER.info("Villa wird jetzt nicht gebaut ({}){}", info.get("grund").getAsString(),
                        ersteVillaOffen ? ", sie entsteht beim ersten Betreten" : "");
            } else {
                long start = System.currentTimeMillis();
                VillaInfo haupt = bauen(server, welt);
                VILLEN.add(haupt);
                info = haupt.alsJson();
                StinkendReich.LOGGER.info("Villa gebaut bei {} in {} ms", haupt.ursprung(), System.currentTimeMillis() - start);
            }
            Files.writeString(datei, GSON.toJson(info), StandardCharsets.UTF_8);
        } catch (Exception e) {
            StinkendReich.LOGGER.error("Villa konnte nicht gebaut werden", e);
        }
    }

    /**
     * Steht in dieser Welt (von vorher) noch keine Villa, soll aber eine entstehen? Dann baut die Baustelle die erste
     * Villa rund um den Spawn statt einer Nachbarvilla.
     */
    public static boolean ersteVillaOffen() {
        return ersteVillaOffen && VILLEN.isEmpty();
    }

    /** Beim allerersten Betreten einer Welt mit Villa: Spieler vor den Eingang stellen. */
    private static void beimBetreten(ServerPlayer spieler, MinecraftServer server) {
        if (VILLEN.isEmpty()) {
            if (ersteVillaOffen()) {
                ersteVillaBestellen(spieler, server);
            }
            return;
        }
        // Das goldene Handy bekommt jeder Bewohner einmal, auch wer schon vor 0.3 hier wohnte
        if (!Kompat.tags(spieler).contains(TAG_HANDY)) {
            spieler.addTag(TAG_HANDY);
            spieler.getInventory().add(new net.minecraft.world.item.ItemStack(de.moinmornhart.stinkendreich.register.ModItems.HANDY));
        }
        // Nachbarschaft: Wer neu dazukommt, während die Spawn-Villa schon jemand anderem gehört, bekommt seine eigene
        VillaInfo eigene = vonBesitzer(spieler.getUUID());
        if (eigene != null) {
            // Vor 0.4 wurde die Nachbarvilla beim ersten Betreten gebaut und der Spieler gleich hingebracht
            einziehen(spieler, eigene, server, !Kompat.tags(spieler).contains(TAG_ANGEKOMMEN));
            return;
        }
        if (brauchtEigeneVilla(spieler, server)) {
            nachbarBestellen(server, spieler);
        }
        if (!Konfig.get().villaSpawnSetzen || Kompat.tags(spieler).contains(TAG_ANGEKOMMEN) || VillaBaustelle.wartet(spieler.getUUID())) {
            return;
        }
        spieler.addTag(TAG_ANGEKOMMEN);
        VillaInfo ziel = VILLEN.get(0);
        // Willkommensgeschenk: eine Zugangskarte für Tore und Kartenleser der eigenen Villa
        spieler.getInventory().add(de.moinmornhart.stinkendreich.mechanik.Zugang.karte(ziel));
        BlockPos p = ziel.spawn();
        Kompat.befehl(server, String.format(java.util.Locale.ROOT, "tp %s %.1f %d %.1f %.1f 0",
                spieler.getScoreboardName(), p.getX() + 0.5, p.getY(), p.getZ() + 0.5, ziel.spawnBlick()));
    }

    /**
     * Welt von vorher ohne Villa: Wer sie als Erster betritt, bekommt die erste Villa (Nummer 0, setzt den Weltspawn) und
     * wird ihr Herr. Wer dazukommt, während sie entsteht, wartet; danach bekommt er wie gewohnt seine eigene.
     */
    private static void ersteVillaBestellen(ServerPlayer spieler, MinecraftServer server) {
        if (VillaBaustelle.ersteVillaImBau()) {
            if (!VillaBaustelle.wartet(spieler.getUUID())) {
                Kompat.nachricht(spieler, net.minecraft.network.chat.Component.translatable("stinkendreich.villa.erstewarten")
                        .withStyle(net.minecraft.ChatFormatting.GOLD), false);
            }
            return;
        }
        VillaBaustelle.bestellen(spieler.getUUID(), spieler.getScoreboardName(), villa -> ersteVillaFertig(server));
        Kompat.nachricht(spieler, net.minecraft.network.chat.Component.translatable("stinkendreich.villa.erstevilla")
                .withStyle(net.minecraft.ChatFormatting.GOLD), false);
    }

    /**
     * Die erste Villa steht: Straßen zu Dorf, Mine & Co. starten, und jeder, der gerade da ist, wird behandelt wie beim
     * Betreten – der Herr zieht ein (Handy, Karte, vor die Tür), die anderen bekommen ihre eigene Villa.
     */
    private static void ersteVillaFertig(MinecraftServer server) {
        de.moinmornhart.stinkendreich.strasse.Strassennetz.automatikStarten(server);
        for (ServerPlayer p : new java.util.ArrayList<>(server.getPlayerList().getPlayers())) {
            beimBetreten(p, server);
        }
    }

    /** Neu hier, mehrere Villen erlaubt und die Spawn-Villa gehört schon einem anderen? */
    private static boolean brauchtEigeneVilla(ServerPlayer spieler, MinecraftServer server) {
        if (!Konfig.get().mehrereVillen) {
            return false;
        }
        de.moinmornhart.stinkendreich.mechanik.VillaZustand haupt = de.moinmornhart.stinkendreich.mechanik.VillaZustand.get(server, 0);
        // Wer der Herr der Spawn-Villa (oder ihr Bewohner) ist, braucht keine eigene
        return !haupt.bewohner().isEmpty() && !haupt.bewohner().containsKey(spieler.getUUID());
    }

    /** Lässt dem Spieler seine Villa bauen; er wird hingebracht, sobald sie steht (oder beim nächsten Betreten). */
    private static void nachbarBestellen(MinecraftServer server, ServerPlayer spieler) {
        if (VillaBaustelle.wartet(spieler.getUUID())) {
            return;
        }
        java.util.UUID wer = spieler.getUUID();
        VillaBaustelle.bestellen(wer, spieler.getScoreboardName(), villa -> {
            ServerPlayer p = server.getPlayerList().getPlayer(wer);
            if (p != null) {
                einziehen(p, villa, server, true);
            }
        });
        Kompat.nachricht(spieler, net.minecraft.network.chat.Component.translatable("stinkendreich.villa.baubeginn")
                .withStyle(net.minecraft.ChatFormatting.GOLD), false);
    }

    /**
     * Macht den Spieler zum Bewohner seiner Villa und bringt ihn beim ersten Mal hin: Zugangskarte, Teleport vor den
     * Eingang, Spawnpunkt dort.
     */
    private static void einziehen(ServerPlayer spieler, VillaInfo villa, MinecraftServer server, boolean hinbringen) {
        de.moinmornhart.stinkendreich.mechanik.VillaZustand z = de.moinmornhart.stinkendreich.mechanik.VillaZustand.get(server, villa.nr());
        if (!z.bewohner().containsKey(spieler.getUUID())) {
            z.bewohnerHinzufuegen(spieler);
        }
        if (!hinbringen || Kompat.tags(spieler).contains(TAG_EIGENE_VILLA)) {
            return;
        }
        spieler.addTag(TAG_EIGENE_VILLA);
        spieler.addTag(TAG_ANGEKOMMEN);
        spieler.getInventory().add(de.moinmornhart.stinkendreich.mechanik.Zugang.karte(villa));
        BlockPos p = villa.spawn();
        Kompat.befehl(server, String.format(java.util.Locale.ROOT, "tp %s %.1f %d %.1f %.1f 0",
                spieler.getScoreboardName(), p.getX() + 0.5, p.getY(), p.getZ() + 0.5, villa.spawnBlick()));
        Kompat.befehl(server, String.format(java.util.Locale.ROOT, "spawnpoint %s %d %d %d",
                spieler.getScoreboardName(), p.getX(), p.getY(), p.getZ()));
        Kompat.nachricht(spieler, net.minecraft.network.chat.Component.translatable("stinkendreich.villa.willkommen",
                spieler.getScoreboardName()).withStyle(net.minecraft.ChatFormatting.GOLD), false);
    }

    /**
     * Baut die nächste Nachbarvilla im Raster über viele Ticks verteilt (Testbefehl /villa nachbar [name]).
     * fertig läuft, sobald sie steht.
     */
    public static void nachbarBauen(java.util.UUID besitzer, String name, java.util.function.Consumer<VillaInfo> fertig) {
        VillaBaustelle.bestellen(besitzer, name, fertig);
    }

    /**
     * Ende der gepflasterten Zufahrt südlich des Tors (ein Block über der Fahrbahn, auf der Mittelachse), oder null. Dort
     * schließt die Straße einer Nachbarvilla an.
     */
    public static BlockPos zufahrtEnde(ServerLevel welt, VillaInfo v) {
        Block fahrbahn = Kompat.blockNachId(net.minecraft.resources.Identifier.withDefaultNamespace("gray_concrete"));
        int mitte = v.ursprung().getX() + (ZUFAHRT_X0 + ZUFAHRT_X1) / 2;
        int start = v.ursprung().getZ() + v.plan().breiteZ;
        BlockPos ende = null;
        for (int i = 0; i < anblendung() + 44; i++) {
            int z = start + i;
            welt.getChunk(mitte >> 4, z >> 4);
            int oben = welt.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, mitte, z) - 1;
            BlockState s = welt.getBlockState(new BlockPos(mitte, oben, z));
            if (s.is(fahrbahn) || s.is(Blocks.POLISHED_ANDESITE_SLAB)) {
                ende = new BlockPos(mitte, s.is(fahrbahn) ? oben + 1 : oben, z);
            } else if (ende != null) {
                break;
            }
        }
        return ende;
    }

    /**
     * Freie Plätze im Raster um die Spawn-Villa (Nordwest-Ecke x, z und Ring), von innen nach außen. Belegt ist ein
     * Platz, wenn dort schon eine Villa steht (näher als der halbe Villenabstand). Gibt es noch keine Villa (Welt von
     * vorher), sind es die Plätze für die erste: der Spawn selbst und Ringe darum, dicht an dicht.
     */
    static List<int[]> freiePlaetze(int anzahl) {
        if (VILLEN.isEmpty()) {
            return ersteVillaPlaetze(anzahl);
        }
        int abstand = Math.max(240, Konfig.get().villenAbstand);
        BlockPos spawnVilla = VILLEN.get(0).ursprung();
        List<int[]> frei = new java.util.ArrayList<>();
        for (int k = 1; frei.size() < anzahl && k < 400; k++) {
            int[] platz = platz(k);
            int x = spawnVilla.getX() + platz[0] * abstand, z = spawnVilla.getZ() + platz[1] * abstand;
            boolean belegt = VILLEN.stream().anyMatch(v -> Math.max(Math.abs(v.ursprung().getX() - x),
                    Math.abs(v.ursprung().getZ() - z)) < abstand / 2);
            if (!belegt) {
                frei.add(new int[]{x, z, Math.max(Math.abs(platz[0]), Math.abs(platz[1]))});
            }
        }
        return frei;
    }

    /**
     * Plätze für die erste Villa in einer Welt von vorher: zuerst mitten auf dem Spawn, dann Ringe darum, so dicht, dass
     * Grundstück samt Anblendung zweier Nachbarplätze sich nicht berühren (Ring als dritter Wert, für die Wertung).
     */
    private static List<int[]> ersteVillaPlaetze(int anzahl) {
        VillaDaten plan = VillaDaten.get();
        int schritt = Math.max(plan.breiteX, plan.breiteZ) + 2 * anblendung() + 48;
        int x0 = weltspawn.getX() - plan.breiteX / 2, z0 = weltspawn.getZ() - plan.breiteZ / 2;
        List<int[]> plaetze = new java.util.ArrayList<>();
        plaetze.add(new int[]{x0, z0, 0});
        for (int k = 1; plaetze.size() < anzahl; k++) {
            int[] p = platz(k);
            plaetze.add(new int[]{x0 + p[0] * schritt, z0 + p[1] * schritt, Math.max(Math.abs(p[0]), Math.abs(p[1]))});
        }
        return plaetze;
    }

    /** Freigehalten wird ein Platz rundum mit Anblendung + 8 Blöcken Rand, nach Süden zusätzlich für die Zufahrt. */
    static int reserveRand() {
        return anblendung() + 8;
    }

    static int reserveSued() {
        return anblendung() + 48;
    }

    /**
     * Der freigehaltene Platz für eine künftige Villa (Grundstück x0, z0, x1, z1), in dessen Fläche diese Stelle liegt, sonst
     * null. Freigehalten werden die beiden inneren Ringe des Rasters um die Spawn-Villa – genau die Plätze, unter denen
     * die Baustelle für jeden neuen Spieler wählt –, solange dort keine Villa steht und der Platz nicht wegen
     * Spielerbauten verworfen wurde. Straßen fahren darum herum (eine Straße über einem Platz machte ihn unbrauchbar).
     */
    public static int[] reservierterPlatz(int x, int z) {
        if (VILLEN.isEmpty() || !Konfig.get().mehrereVillen) {
            return null;
        }
        VillaDaten plan = VillaDaten.get();
        int abstand = Math.max(240, Konfig.get().villenAbstand);
        BlockPos u = VILLEN.get(0).ursprung();
        int i = Math.round((float) (x - u.getX() - plan.breiteX / 2) / abstand);
        int j = Math.round((float) (z - u.getZ() - plan.breiteZ / 2) / abstand);
        int ring = Math.max(Math.abs(i), Math.abs(j));
        if (ring < 1 || ring > 2) {
            return null;
        }
        int x0 = u.getX() + i * abstand, z0 = u.getZ() + j * abstand;
        int rand = reserveRand();
        if (x < x0 - rand || x >= x0 + plan.breiteX + rand || z < z0 - rand || z >= z0 + plan.breiteZ + reserveSued()) {
            return null;
        }
        for (VillaInfo v : VILLEN) {
            if (Math.max(Math.abs(v.ursprung().getX() - x0), Math.abs(v.ursprung().getZ() - z0)) < abstand / 2) {
                return null;                                // dort steht schon eine Villa (die schützt sich selbst)
            }
        }
        return VillaBaustelle.verworfen(x0, z0) ? null : new int[]{x0, z0, x0 + plan.breiteX, z0 + plan.breiteZ};
    }

    /** Die Baustelle meldet eine fertige Villa: in die Liste, in die Merkdatei, 3 Minuten Pflanzenreste wegräumen. */
    static void fertigGebaut(MinecraftServer server, VillaInfo villa) {
        VILLEN.add(villa);
        merken();
        NACHBARN_AUFRAEUMEN.put(villa.nr(), server.overworld().getGameTime() + 20L * 60 * 3);
    }

    /** Platz der Villa Nummer nr im Raster um die Spawn-Villa: Ringe von innen nach außen, je Ring im Kreis. */
    static int[] platz(int nr) {
        int n = 0;
        for (int ring = 1; ; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == ring && ++n == nr) {
                        return new int[]{dx, dz};
                    }
                }
            }
        }
    }

    /** Schreibt alle Villen in die Merkdatei der Welt. */
    private static void merken() {
        if (merkdatei == null || VILLEN.isEmpty()) {
            return;
        }
        try {
            JsonObject j = VILLEN.get(0).alsJson();
            JsonArray nachbarn = new JsonArray();
            for (int i = 1; i < VILLEN.size(); i++) {
                nachbarn.add(VILLEN.get(i).alsJson());
            }
            j.add("nachbarn", nachbarn);
            Files.writeString(merkdatei, GSON.toJson(j), StandardCharsets.UTF_8);
        } catch (Exception e) {
            StinkendReich.LOGGER.error("Villen konnten nicht gemerkt werden", e);
        }
    }

    // ------------------------------------------------------------------
    // Bauen
    // ------------------------------------------------------------------

    private static VillaInfo bauen(MinecraftServer server, ServerLevel welt) {
        VillaDaten plan = VillaDaten.get();
        BlockPos spawn = Kompat.spawnPunkt(welt);
        return bauenBei(server, welt, spawn.getX() - plan.breiteX / 2, spawn.getZ() - plan.breiteZ / 2, 0, null);
    }

    /**
     * Baut eine Villa mit der Nordwest-Ecke bei x0/z0. Nummer 0 ist die Spawn-Villa (setzt den Weltspawn); Nachbarn
     * gehören gleich ihrem Besitzer (Butler mit seinem Namen).
     */
    private static VillaInfo bauenBei(MinecraftServer server, ServerLevel welt, int x0, int z0, int nr, ServerPlayer besitzer) {
        VillaDaten plan = VillaDaten.get();
        int rand = anblendung();

        // Alle betroffenen Chunks laden (bzw. erzeugen)
        for (int cx = (x0 - rand) >> 4; cx <= (x0 + plan.breiteX + rand) >> 4; cx++) {
            for (int cz = (z0 - rand) >> 4; cz <= (z0 + plan.breiteZ + rand) >> 4; cz++) {
                welt.getChunk(cx, cz);
            }
        }

        // Zielhöhe: Median der Geländehöhe auf dem Grundstück
        int[] hoehen = new int[plan.breiteX * plan.breiteZ];
        for (int x = 0; x < plan.breiteX; x++) {
            hoehenReihe(welt, x0, z0, plan, x, hoehen);
        }
        int y0 = zielhoehe(welt, plan, hoehen);

        for (int x = x0 - rand; x < x0 + plan.breiteX + rand; x++) {
            gelaendeReihe(welt, x, x0, z0, plan.breiteX, plan.breiteZ, y0, rand);
        }
        for (int x = x0 - rand - 8; x < x0 + plan.breiteX + rand + 8; x++) {
            blaetterReihe(welt, x, z0, plan.breiteZ, rand + 8);
        }
        tiereEntfernen(welt, x0, y0, z0, plan);

        // Strukturdatei setzen: Grasschicht der Datei liegt auf y0 - 1
        BlockPos ursprung = ursprung(x0, y0, z0, plan);
        vorlage(server).placeInWorld(welt, ursprung, ursprung, new StructurePlaceSettings(), welt.getRandom(), Block.UPDATE_CLIENTS);
        return ausstatten(server, welt, ursprung, y0, nr, besitzer == null ? null : besitzer.getUUID(),
                besitzer == null ? "" : besitzer.getScoreboardName());
    }

    // ------------------------------------------------------------------
    // Bauschritte (auch für die Baustelle, die eine Nachbarvilla über viele Ticks verteilt baut)
    // ------------------------------------------------------------------

    /** Breite des angeblendeten Geländestreifens rund ums Grundstück. */
    static int anblendung() {
        return Math.max(4, Konfig.get().villaAnblendung);
    }

    /** Die Strukturdatei der Villa. */
    static StructureTemplate vorlage(MinecraftServer server) {
        return Kompat.strukturen(server).get(StinkendReich.id("villa/villa"))
                .orElseThrow(() -> new IllegalStateException("Strukturdatei villa/villa.nbt fehlt"));
    }

    /** Bodenhöhen einer Reihe x (0 … breiteX − 1) des Grundstücks, für den Median. */
    static void hoehenReihe(ServerLevel welt, int x0, int z0, VillaDaten plan, int x, int[] hoehen) {
        for (int z = 0; z < plan.breiteZ; z++) {
            hoehen[x * plan.breiteZ + z] = bodenHoehe(welt, x0 + x, z0 + z);
        }
    }

    /** Zielhöhe der Villa: Median der Bodenhöhen, nicht unter dem Meeresspiegel und innerhalb der Welt. */
    static int zielhoehe(ServerLevel welt, VillaDaten plan, int[] hoehen) {
        int[] sortiert = hoehen.clone();
        Arrays.sort(sortiert);
        int y0 = Math.max(sortiert[sortiert.length / 2], welt.getSeaLevel() + 1);
        return Mth.clamp(y0, Kompat.minY(welt) + plan.bodenY + 2, Kompat.maxY(welt) - (plan.hoehe - plan.bodenY) - 2);
    }

    /** Ursprung der Strukturdatei: Ihre Grasschicht liegt auf y0 − 1. */
    static BlockPos ursprung(int x0, int y0, int z0, VillaDaten plan) {
        return new BlockPos(x0, y0 - 1 - plan.bodenY, z0);
    }

    /**
     * Alles nach dem Setzen des Bauplans: Zufahrt, Festung (Minengürtel, Notausgang), Spawnpunkt, Butler, Fernseher,
     * Drehteller, Waffenkammer, Hubschrauber und Fuhrpark. Gibt die fertige Villa zurück.
     */
    static VillaInfo ausstatten(MinecraftServer server, ServerLevel welt, BlockPos ursprung, int y0, int nr,
                                java.util.UUID besitzer, String besitzerName) {
        VillaDaten plan = VillaDaten.get();
        zufahrtBauen(welt, ursprung, plan, y0, anblendung());
        // Festung außerhalb des Bauplans: Minengürtel rund ums Grundstück, Notausgang bis zur versteckten Falltür
        int minen = Festungsbau.minenfeld(welt, ursprung, plan, ZUFAHRT_X0, ZUFAHRT_X1);
        BlockPos luke = Festungsbau.notausgang(welt, ursprung, plan);
        StinkendReich.LOGGER.info("Festung: {} Minen, Notausgang bei {}", minen, luke);

        VillaInfo villa = new VillaInfo(ursprung, plan, nr, besitzer, besitzer == null ? "" : besitzerName);
        if (nr == 0) {
            Kompat.setzeSpawnPunkt(welt, villa.spawn(), villa.spawnBlick());
            Kompat.befehl(server, Kompat.spawnRadiusBefehl(0));
        }
        // Der Butler wartet in der Halle auf seinen Herrn
        JsonArray b = plan.ersteMarke("butler");
        ButlerVerwaltung.villaButlerErzeugen(welt, villa.weltPos(b.get(0).getAsDouble(), b.get(1).getAsDouble(), b.get(2).getAsDouble()),
                besitzer, besitzerName);
        // XXL-Fernseher (Penthouse, Gästezimmer, Bunker; die Marke kann Breite und Höhe in Blöcken mitbringen)
        for (JsonArray m : plan.marke("fernseher")) {
            de.moinmornhart.stinkendreich.fernseher.FernseherEntity tv = de.moinmornhart.stinkendreich.register.ModEntities.FERNSEHER.create(welt
                    //? if >=1.21.2
                    , net.minecraft.world.entity.EntitySpawnReason.STRUCTURE
            );
            if (tv != null) {
                float blick = (float) m.get(3).getAsDouble();
                Kompat.versetzen(tv, ursprung.getX() + m.get(0).getAsDouble(), ursprung.getY() + m.get(1).getAsDouble(),
                        ursprung.getZ() + m.get(2).getAsDouble(), blick, 0.0F);
                tv.setYBodyRot(blick);
                if (m.size() >= 6) {
                    tv.setzeGroesse((float) m.get(4).getAsDouble(), (float) m.get(5).getAsDouble());
                }
                welt.addFreshEntity(tv);
            }
        }
        // Drehteller am Fuß der Garagenrampe
        for (JsonArray m : plan.marke("drehteller")) {
            de.moinmornhart.stinkendreich.auto.DrehtellerEntity teller = de.moinmornhart.stinkendreich.register.ModEntities.DREHTELLER.create(welt
                    //? if >=1.21.2
                    , net.minecraft.world.entity.EntitySpawnReason.STRUCTURE
            );
            if (teller != null) {
                Kompat.versetzen(teller, ursprung.getX() + m.get(0).getAsDouble(), ursprung.getY() + m.get(1).getAsDouble(),
                        ursprung.getZ() + m.get(2).getAsDouble(), 0.0F, 0.0F);
                welt.addFreshEntity(teller);
            }
        }
        // Waffenkammer im Bunker: Rüstungsständer mit den Sets
        for (JsonArray m : plan.marke("ruestung")) {
            de.moinmornhart.stinkendreich.ausruestung.Waffenkammer.aufstellen(welt, ursprung.getX() + m.get(0).getAsDouble(),
                    ursprung.getY() + m.get(1).getAsDouble(), ursprung.getZ() + m.get(2).getAsDouble(),
                    (float) m.get(3).getAsDouble(), m.get(4).getAsInt());
        }
        // Der Hubschrauber steht auf dem Landeplatz auf dem Penthouse-Dach
        for (JsonArray m : plan.marke("hubschrauber")) {
            abstellen(welt, de.moinmornhart.stinkendreich.auto.Autotyp.HUBSCHRAUBER, 0, ursprung.getX() + m.get(0).getAsDouble(),
                    ursprung.getY() + m.get(1).getAsDouble(), ursprung.getZ() + m.get(2).getAsDouble(), (float) m.get(3).getAsDouble());
        }
        // Der Fuhrpark steht in der Tiefgarage (Stellplätze aus dem Bauplan)
        for (JsonArray platz : plan.marke("stellplatz")) {
            int stelle = platz.get(4).getAsInt();
            if (stelle < FUHRPARK.length) {
                abstellen(welt, FUHRPARK[stelle].art(), FUHRPARK[stelle].farbe(), ursprung.getX() + platz.get(0).getAsDouble(),
                        ursprung.getY() + platz.get(1).getAsDouble(), ursprung.getZ() + platz.get(2).getAsDouble(),
                        (float) platz.get(3).getAsDouble());
            }
        }
        return villa;
    }

    /**
     * Stellplatz eines Wagens dieser Art in der Tiefgarage einer Villa (Weltkoordinaten x, y, z und Blickrichtung),
     * der erste passende aus dem Fuhrpark; null, wenn es dort keinen Wagen dieser Art gibt.
     */
    public static double[] stellplatz(VillaInfo v, de.moinmornhart.stinkendreich.auto.Autotyp art) {
        for (JsonArray platz : v.plan().marke("stellplatz")) {
            int stelle = platz.get(4).getAsInt();
            if (stelle < FUHRPARK.length && FUHRPARK[stelle].art() == art) {
                return new double[]{v.ursprung().getX() + platz.get(0).getAsDouble(), v.ursprung().getY() + platz.get(1).getAsDouble(),
                        v.ursprung().getZ() + platz.get(2).getAsDouble(), platz.get(3).getAsDouble()};
            }
        }
        return null;
    }

    /** Ein Wagen im Fuhrpark: Typ und Lackfarbe (Index in AutoEntity.FARBEN). */
    private record Wagen(de.moinmornhart.stinkendreich.auto.Autotyp art, int farbe) {
    }

    /** Belegung der Stellplätze 0–9 (Süd-Reihe, dann Nord-Reihe). */
    private static final Wagen[] FUHRPARK = {
            new Wagen(de.moinmornhart.stinkendreich.auto.Autotyp.BONZENAUTO, 4),
            new Wagen(de.moinmornhart.stinkendreich.auto.Autotyp.SUPERSPORTWAGEN, 3),
            new Wagen(de.moinmornhart.stinkendreich.auto.Autotyp.GELAENDEWAGEN, 0),
            new Wagen(de.moinmornhart.stinkendreich.auto.Autotyp.SUPERSPORTWAGEN, 6),
            new Wagen(de.moinmornhart.stinkendreich.auto.Autotyp.BONZENAUTO, 7),
            new Wagen(de.moinmornhart.stinkendreich.auto.Autotyp.GELAENDEWAGEN, 1),
            new Wagen(de.moinmornhart.stinkendreich.auto.Autotyp.SUPERSPORTWAGEN, 2),
            new Wagen(de.moinmornhart.stinkendreich.auto.Autotyp.BONZENAUTO, 0),
            new Wagen(de.moinmornhart.stinkendreich.auto.Autotyp.BONZENAUTO, 5),
            new Wagen(de.moinmornhart.stinkendreich.auto.Autotyp.STRETCHLIMO, 0),
    };

    private static void abstellen(ServerLevel welt, de.moinmornhart.stinkendreich.auto.Autotyp art, int farbe,
                                  double x, double y, double z, float blick) {
        de.moinmornhart.stinkendreich.auto.AutoEntity auto = de.moinmornhart.stinkendreich.register.ModEntities.auto(art).create(welt
                //? if >=1.21.2
                , net.minecraft.world.entity.EntitySpawnReason.STRUCTURE
        );
        if (auto != null) {
            auto.teleportTo(x, y, z);
            auto.setYRot(blick);
            auto.setYHeadRot(blick);
            auto.yBodyRot = blick;
            auto.setzeFarbe(farbe);
            welt.addFreshEntity(auto);
        }
    }

    /** Höhe des natürlichen Bodens (erste Luft über Erde/Stein), ohne Bäume und Pflanzen. */
    static int bodenHoehe(ServerLevel welt, int x, int z) {
        int y = welt.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        int min = Kompat.minY(welt);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, y, z);
        while (y > min) {
            p.setY(y - 1);
            BlockState s = welt.getBlockState(p);
            if (!istBewuchs(s) && s.getFluidState().isEmpty()) {
                break;
            }
            y--;
        }
        return y;
    }

    private static boolean istBewuchs(BlockState s) {
        return s.isAir() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.canBeReplaced()
                || s.is(BlockTags.FLOWERS) || s.is(Blocks.BAMBOO) || s.is(Blocks.CACTUS) || s.is(Blocks.SUGAR_CANE)
                || s.is(Blocks.BROWN_MUSHROOM_BLOCK) || s.is(Blocks.RED_MUSHROOM_BLOCK) || s.is(Blocks.MUSHROOM_STEM);
    }

    /**
     * Ebnet das Grundstück auf y0 und blendet ringsum über {@code rand} Blöcke weich ins Gelände über.
     * Bäume und Pflanzen auf der Fläche werden entfernt; Wasser außerhalb des Grundstücks bleibt Wasser.
     */
    /** Zufahrt vor der Brücke (Strukturkoordinaten): Fahrbahn x 52–59, Bordsteine bei 51 und 60. */
    private static final int ZUFAHRT_X0 = 50, ZUFAHRT_X1 = 59;

    /**
     * Gepflasterte Zufahrt vom Ende der Brücke nach Süden ins Gelände. Sie steigt oder fällt höchstens
     * einen Block pro Block (so kommen Autos und der Chauffeur hinauf), wird bei Bedarf aufgeschüttet und
     * endet, sobald sie das natürliche Gelände erreicht hat (mindestens so lang wie die Anblendung).
     */
    private static void zufahrtBauen(ServerLevel welt, BlockPos ursprung, VillaDaten plan, int y0, int rand) {
        BlockState fahrbahn = Kompat.blockNachId(net.minecraft.resources.Identifier.withDefaultNamespace("gray_concrete")).defaultBlockState();
        BlockState bord = Blocks.POLISHED_ANDESITE.defaultBlockState();
        BlockState stein = Blocks.STONE.defaultBlockState();
        BlockState luft = Blocks.AIR.defaultBlockState();
        int flags = Block.UPDATE_CLIENTS;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int start = ursprung.getZ() + plan.breiteZ;
        int mitte = ursprung.getX() + (ZUFAHRT_X0 + ZUFAHRT_X1) / 2;
        int hoehe = y0;     // begehbare Höhe der Fahrbahn
        int vorher = y0;
        BlockState stufe = Blocks.POLISHED_ANDESITE_SLAB.defaultBlockState();
        for (int i = 0; i < rand + 40; i++) {
            int z = start + i;
            int natur = bodenHoehe(welt, mitte, z);
            if (i >= rand + 2 && natur == hoehe) {
                break;  // im Gelände angekommen
            }
            vorher = hoehe;
            hoehe += Mth.clamp(natur - hoehe, -1, 1);
            for (int x = ursprung.getX() + ZUFAHRT_X0; x <= ursprung.getX() + ZUFAHRT_X1; x++) {
                boolean rand_ = x == ursprung.getX() + ZUFAHRT_X0 || x == ursprung.getX() + ZUFAHRT_X1;
                // Unterbau bis zum Boden (höchstens 12 Blöcke), Belag, darüber vier Blöcke frei
                for (int y = hoehe - 2; y > hoehe - 14; y--) {
                    p.set(x, y, z);
                    BlockState alt = welt.getBlockState(p);
                    if (!alt.isAir() && alt.getFluidState().isEmpty() && !alt.canBeReplaced()) {
                        break;
                    }
                    welt.setBlock(p, stein, flags);
                }
                welt.setBlock(p.set(x, hoehe - 1, z), rand_ ? bord : fahrbahn, flags);
                // Offener Einschnitt bis zur Oberfläche (höchstens 48 Blöcke): im Hang kein Tunnel
                int himmel = Math.min(hoehe + 48, Math.max(hoehe + 5, welt.getHeight(Heightmap.Types.WORLD_SURFACE, x, z)));
                for (int y = hoehe; y < himmel; y++) {
                    if (!welt.getBlockState(p.set(x, y, z)).isAir()) {
                        welt.setBlock(p, luft, flags);
                    }
                }
            }
            // Stufen mit einer halben Platte auf dem tieferen Feld weicher machen
            if (hoehe != vorher) {
                int zTief = hoehe > vorher ? z - 1 : z, yTief = Math.min(hoehe, vorher);
                for (int x = ursprung.getX() + ZUFAHRT_X0 + 1; x < ursprung.getX() + ZUFAHRT_X1; x++) {
                    if (welt.getBlockState(p.set(x, yTief, zTief)).isAir()) {
                        welt.setBlock(p, stufe, flags);
                    }
                }
            }
            // Laternen am Rand
            if (i % 6 == 3) {
                for (int x : new int[]{ursprung.getX() + ZUFAHRT_X0 - 1, ursprung.getX() + ZUFAHRT_X1 + 1}) {
                    welt.setBlock(p.set(x, hoehe - 1, z), bord, flags);
                    welt.setBlock(p.set(x, hoehe, z), Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState(), Block.UPDATE_ALL);
                    welt.setBlock(p.set(x, hoehe + 1, z), Blocks.LANTERN.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    /** Ebnet eine Reihe x des Grundstücks samt angeblendetem Rand (x von x0 − rand bis x0 + bx + rand − 1). */
    static void gelaendeReihe(ServerLevel welt, int x, int x0, int z0, int bx, int bz, int y0, int rand) {
        BlockState gras = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState erde = Blocks.DIRT.defaultBlockState();
        BlockState stein = Blocks.STONE.defaultBlockState();
        BlockState luft = Blocks.AIR.defaultBlockState();
        BlockState wasser = Blocks.WATER.defaultBlockState();
        int flags = Block.UPDATE_CLIENTS;
        int meer = welt.getSeaLevel();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();

        for (int z = z0 - rand; z < z0 + bz + rand; z++) {
            // Abstand zum Grundstücksrand (0 = innen)
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
            int oben = Math.max(welt.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), ziel) + 1;

            // Auffüllen bis zur Zielhöhe
            for (int y = Math.min(boden, ziel) - 4; y < ziel; y++) {
                p.set(x, y, z);
                BlockState alt = welt.getBlockState(p);
                BlockState neu = y == ziel - 1 ? gras : (y >= ziel - 4 ? erde : stein);
                if (y < boden && !alt.isAir() && alt.getFluidState().isEmpty() && y < ziel - 1) {
                    continue; // vorhandenen Untergrund behalten
                }
                if (alt != neu) {
                    welt.setBlock(p, neu, flags);
                }
            }
            // Alles darüber räumen (Bäume, Hügel); unter dem Meeresspiegel außerhalb bleibt Wasser
            for (int y = ziel; y <= oben; y++) {
                p.set(x, y, z);
                BlockState alt = welt.getBlockState(p);
                if (alt.isAir()) {
                    continue;
                }
                boolean nass = abstand > 0 && y < meer && !alt.getFluidState().isEmpty();
                welt.setBlock(p, nass ? wasser : luft, flags);
            }
        }
    }

    /**
     * Blätter gefällter Bäume am Rand zerfallen noch eine Weile und lassen Stöcke, Setzlinge
     * und Äpfel fallen; Laubstreu, Blumen und Gräser (ab 1.21.5 liegt in Wäldern Laubstreu) fallen
     * ab, wenn ihnen beim Ebnen der Boden fehlt. Solcher Pflanzenabfall wird in den ersten
     * Minuten auf dem Grundstück weggeräumt – nur herrenlose Gegenstände, die schon ein paar
     * Sekunden liegen, damit nichts verschwindet, was ein Spieler wirft oder gleich aufhebt.
     */
    private static void aufraeumen(MinecraftServer server) {
        long zeit = server.overworld().getGameTime();
        if (zeit % 20 != 0) {
            return;
        }
        for (VillaInfo v : VILLEN) {
            // Spawn-Villa: die ersten 10 Minuten der Welt; Nachbarvillen: 3 Minuten nach dem Bau
            long bis = v.nr() == 0 ? AUFRAEUMEN_BIS : NACHBARN_AUFRAEUMEN.getOrDefault(v.nr(), -1L);
            if (zeit > bis) {
                continue;
            }
            BlockPos u = v.ursprung();
            VillaDaten plan = v.plan();
            int r = Konfig.get().villaAnblendung + 8;
            AABB bereich = new AABB(u.getX() - r, u.getY(), u.getZ() - r,
                    u.getX() + plan.breiteX + r, u.getY() + plan.hoehe + 40, u.getZ() + plan.breiteZ + r);
            for (ItemEntity e : server.overworld().getEntitiesOfClass(ItemEntity.class, bereich, i -> true)) {
                if (e.getAge() > 100 && e.getOwner() == null && pflanzenabfall(e.getItem())) {
                    e.discard();
                }
            }
        }
    }

    /** Bis zu welcher Spielzeit um eine frisch gebaute Nachbarvilla aufgeräumt wird (Nummer → Spielzeit). */
    private static final java.util.Map<Integer, Long> NACHBARN_AUFRAEUMEN = new java.util.concurrent.ConcurrentHashMap<>();

    private static boolean pflanzenabfall(ItemStack stapel) {
        if (stapel.is(Items.STICK) || stapel.is(ItemTags.SAPLINGS) || stapel.is(Items.APPLE) || stapel.is(Items.WHEAT_SEEDS)) {
            return true;
        }
        if (!(stapel.getItem() instanceof BlockItem bi)) {
            return false;
        }
        BlockState s = bi.getBlock().defaultBlockState();
        return s.is(BlockTags.LEAVES) || s.is(BlockTags.FLOWERS) || s.canBeReplaced()
                //? if >=1.21.5 {
                || s.is(Blocks.LEAF_LITTER)
                //?}
                ;
    }

    /**
     * Blätter, deren Baumstamm beim Ebnen entfernt wurde, würden noch minutenlang zerfallen.
     * Alle nicht festen Blätter ohne Stamm in der Nähe werden deshalb gleich entfernt.
     */
    static void blaetterReihe(ServerLevel welt, int x, int z0, int bz, int rand) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState luft = Blocks.AIR.defaultBlockState();
        for (int z = z0 - rand; z < z0 + bz + rand; z++) {
            int oben = welt.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            for (int y = oben - 1; y > oben - 24; y--) {
                p.set(x, y, z);
                BlockState s = welt.getBlockState(p);
                if (s.is(BlockTags.LEAVES) && !(s.hasProperty(LeavesBlock.PERSISTENT) && s.getValue(LeavesBlock.PERSISTENT))
                        && !stammInDerNaehe(welt, p)) {
                    welt.setBlock(p, luft, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static boolean stammInDerNaehe(ServerLevel welt, BlockPos mitte) {
        for (BlockPos q : BlockPos.betweenClosed(mitte.offset(-4, -4, -4), mitte.offset(4, 4, 4))) {
            if (welt.getBlockState(q).is(BlockTags.LOGS)) {
                return true;
            }
        }
        return false;
    }

    /** Entfernt Tiere, die nach dem Bauen in Wänden oder im Graben stecken würden. */
    static void tiereEntfernen(ServerLevel welt, int x0, int y0, int z0, VillaDaten plan) {
        AABB bereich = new AABB(x0, y0 - plan.bodenY - 1, z0, x0 + plan.breiteX, y0 + plan.hoehe, z0 + plan.breiteZ);
        for (Entity e : welt.getEntitiesOfClass(Mob.class, bereich, m -> true)) {
            e.discard();
        }
    }

    /**
     * Lage einer gebauten Villa: Ursprung, Bauplan, Nummer (0 = Spawn-Villa) und Besitzer (bei der Spawn-Villa
     * leer: dort ist der erste Bewohner der Herr, siehe VillaZustand).
     */
    public record VillaInfo(BlockPos ursprung, VillaDaten plan, int nr, java.util.UUID besitzer, String besitzerName) {
        public VillaInfo(BlockPos ursprung, VillaDaten plan) {
            this(ursprung, plan, 0, null, "");
        }

        /** Waagerecht höchstens {@code rand} Blöcke vom Grundstück entfernt? */
        public boolean inDerNaehe(BlockPos pos, int rand) {
            return pos.getX() >= ursprung.getX() - rand && pos.getX() < ursprung.getX() + plan.breiteX + rand
                    && pos.getZ() >= ursprung.getZ() - rand && pos.getZ() < ursprung.getZ() + plan.breiteZ + rand;
        }

        public BlockPos weltPos(double lx, double ly, double lz) {
            return ursprung.offset((int) Math.floor(lx), (int) Math.floor(ly), (int) Math.floor(lz));
        }

        public BlockPos spawn() {
            JsonArray m = plan.ersteMarke("spawn");
            return weltPos(m.get(0).getAsDouble(), m.get(1).getAsDouble(), m.get(2).getAsDouble());
        }

        public float spawnBlick() {
            return plan.ersteMarke("spawn").get(3).getAsFloat();
        }

        /** Mitte des Hubschrauber-Landeplatzes auf dem Penthouse-Dach (oder der Spawn, falls es keinen gibt). */
        public BlockPos landeplatz() {
            List<JsonArray> marken = plan.marke("hubschrauber");
            if (marken.isEmpty()) {
                return spawn();
            }
            JsonArray m = marken.get(0);
            return weltPos(m.get(0).getAsDouble(), m.get(1).getAsDouble(), m.get(2).getAsDouble());
        }

        /** Liegt die Position innerhalb des Grundstücks (inkl. Bunker)? */
        public boolean enthaelt(BlockPos pos) {
            return pos.getX() >= ursprung.getX() && pos.getX() < ursprung.getX() + plan.breiteX
                    && pos.getZ() >= ursprung.getZ() && pos.getZ() < ursprung.getZ() + plan.breiteZ
                    && pos.getY() >= ursprung.getY() && pos.getY() < ursprung.getY() + plan.hoehe;
        }

        JsonObject alsJson() {
            JsonObject j = new JsonObject();
            j.addProperty("gebaut", true);
            JsonArray u = new JsonArray();
            u.add(ursprung.getX());
            u.add(ursprung.getY());
            u.add(ursprung.getZ());
            j.add("ursprung", u);
            if (besitzer != null) {
                j.addProperty("besitzer", besitzer.toString());
                j.addProperty("besitzerName", besitzerName);
            }
            return j;
        }

        static VillaInfo ausJson(JsonObject j, int nr) {
            if (!j.has("gebaut") || !j.get("gebaut").getAsBoolean()) {
                return null;
            }
            JsonArray u = j.getAsJsonArray("ursprung");
            return new VillaInfo(new BlockPos(u.get(0).getAsInt(), u.get(1).getAsInt(), u.get(2).getAsInt()), VillaDaten.get(), nr,
                    j.has("besitzer") ? java.util.UUID.fromString(j.get("besitzer").getAsString()) : null,
                    j.has("besitzerName") ? j.get("besitzerName").getAsString() : "");
        }
    }
}
