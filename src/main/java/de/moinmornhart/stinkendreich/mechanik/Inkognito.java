package de.moinmornhart.stinkendreich.mechanik;

import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.register.ModSounds;
import de.moinmornhart.stinkendreich.villa.VillaPlatzierung;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

/**
 * Inkognito-Modus: Die ganze Villa samt Grundstück fährt wie ein Aufzug in die Erde. Block für Block rückt alles nach
 * unten, Möbel, Autos, Figuren und Spieler fahren mit. Unten angekommen schiebt sich von den Rändern her eine Wiese über
 * das Loch. Die Villa bleibt dort unten vollständig erhalten: Wer sich ein paar Blöcke hinuntergräbt, landet in ihr.
 * Beim Auftauchen öffnet sich die Wiese von der Mitte aus, und die Villa fährt mit allem, was unten verändert wurde,
 * wieder hoch.
 * <p>
 * Während der Fahrt tragen die Blöcke ihre Inhalte (Truhen, Schubladen …) nicht mit. Deshalb wird der ganze Bereich vorher
 * als Strukturdatei im Weltordner gespeichert (generated/stinkendreich/structures/inkognito/villa) und am Ziel einmal
 * darübergelegt. Endet das Spiel mitten in der Fahrt, wird sie vorher ohne Animation zu Ende gebracht.
 */
public final class Inkognito {
    /** Erdschichten über dem höchsten Punkt der versenkten Villa (oberste davon ist Gras). */
    private static final int DECKE = 3;
    /** Ticks je Schritt nach unten oder oben; die Spalten eines Schritts werden auf diese Ticks verteilt. */
    private static final int TICKS_JE_SCHRITT = 4;
    /** Ringe der Wiese, die sich je Tick schließen oder öffnen. */
    private static final int RINGE_JE_TICK = 2;
    /** Bäume, die als Tarnung auf der Wiese wachsen (je Baum noch etwas Gras und Blumen). */
    private static final int BAEUME = 18;
    /** Ticks je wachsendem Baum. */
    private static final int TICKS_JE_BAUM = 3;
    /** An Clients senden, keine Nachbar-Reaktionen, nichts fallen lassen. */
    private static final int FLAGS = 2 | 16 | 32;

    private enum Phase { RUNTER, DECKEL_ZU, BAEUME_HOCH, BAEUME_WEG, DECKEL_AUF, HOCH }

    /** Laufende Animation oder null. */
    private static Phase phase;
    /** Fortschritt: Schritte (RUNTER/HOCH) bzw. Ringe (Deckel). */
    private static int schritt;
    /** Höhe jeder Figur am Anfang des laufenden Schritts nach unten. */
    private static final java.util.Map<Entity, Double> startHoehen = new java.util.HashMap<>();
    /** Teil der Spalten innerhalb eines Schritts (0 bis TICKS_JE_SCHRITT - 1). */
    private static int teil;

    private Inkognito() {
    }

    public static void registrieren() {
        ServerTickEvents.END_SERVER_TICK.register(Inkognito::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(Inkognito::zuEndeBringen);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> phase = null);
        // Wer während der Fahrt drinnen steht, fährt mit: kein Schaden durch Wände oder Fallen solange
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((wer, quelle, menge) ->
                !(phase == Phase.RUNTER || phase == Phase.HOCH)
                        || !(quelle.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)
                        || quelle.is(net.minecraft.world.damagesource.DamageTypes.FALL)));
    }

    /** Ist die Villa versteckt oder fährt sie gerade? Dann ruhen Graben, Zugbrücke, Rutsche und Smart Home. */
    public static boolean aktiv(MinecraftServer server) {
        return phase != null || VillaZustand.get(server).smart("inkognito", false);
    }

    /** So tief fährt die Villa: ihre Höhe über dem Gras plus die Erdschichten darüber. */
    private static int tiefe(VillaPlatzierung.VillaInfo villa) {
        return villa.plan().hoehe - villa.plan().bodenY + DECKE - 1;
    }

    /** Befehl aus der Smart-Home-App oder von /villa inkognito: an = versenken, aus = auftauchen. */
    public static String schalten(MinecraftServer server, boolean an) {
        var villa = VillaPlatzierung.villa().orElse(null);
        if (villa == null) {
            return "stinkendreich.villa.keine";
        }
        if (phase != null) {
            return "stinkendreich.inkognito.laeuft";
        }
        VillaZustand z = VillaZustand.get(server);
        if (an == z.smart("inkognito", false)) {
            return null;                                         // ist schon so
        }
        ServerLevel welt = server.overworld();
        if (an && villa.ursprung().getY() - tiefe(villa) <= Kompat.minY(welt) + 1) {
            return "stinkendreich.inkognito.zutief";
        }
        if (an) {
            z.setzeStufe(0);                                     // keine Laser, Minen oder Lava im Versteck
            speichern(welt, villa, 0);
            phase = Phase.RUNTER;
        } else {
            speichern(welt, villa, -tiefe(villa));               // samt allem, was unten verändert wurde
            phase = Phase.BAEUME_WEG;
        }
        schritt = 0;
        teil = 0;
        z.setzeSmart("inkognito", an);
        welt.playSound(null, mitte(villa, villa.plan().bodenY), SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 4.0F,
                an ? 0.6F : 1.2F);
        return null;
    }

    private static Identifier datei() {
        return StinkendReich.id("inkognito/villa");
    }

    private static BlockPos mitte(VillaPlatzierung.VillaInfo villa, double y) {
        return villa.weltPos(villa.plan().breiteX / 2.0, y, villa.plan().breiteZ / 2.0);
    }

    /** Speichert die ganze Villa (ohne Figuren, die fahren selbst mit), um versatz Blöcke nach oben/unten verschoben. */
    private static void speichern(ServerLevel welt, VillaPlatzierung.VillaInfo villa, int versatz) {
        var dateien = Kompat.strukturen(welt.getServer());
        StructureTemplate vorlage = dateien.getOrCreate(datei());
        BlockPos ecke = villa.ursprung().above(versatz);
        Vec3i groesse = new Vec3i(villa.plan().breiteX, villa.plan().hoehe, villa.plan().breiteZ);
        //? if >=1.21.6 {
        vorlage.fillFromWorld(welt, ecke, groesse, false, java.util.List.of(Blocks.STRUCTURE_VOID));
        //?} else
        /*vorlage.fillFromWorld(welt, ecke, groesse, false, Blocks.STRUCTURE_VOID);*/
        dateien.save(datei());
    }

    /** Legt die gespeicherte Villa (mit Truhen-Inhalten) um versatz verschoben in die Welt. */
    private static void einsetzen(ServerLevel welt, VillaPlatzierung.VillaInfo villa, int versatz) {
        BlockPos ecke = villa.ursprung().above(versatz);
        StructurePlaceSettings ohneFiguren = new StructurePlaceSettings().setIgnoreEntities(true);
        Kompat.strukturen(welt.getServer()).get(datei())
                .ifPresent(v -> v.placeInWorld(welt, ecke, ecke, ohneFiguren, welt.getRandom(), 2 | 16));
    }

    private static void tick(MinecraftServer server) {
        if (phase != null) {
            weiter(server, false);
        }
    }

    /** Server fährt herunter: die Fahrt ohne Animation fertig machen, damit nichts halb verschoben gespeichert wird. */
    private static void zuEndeBringen(MinecraftServer server) {
        while (phase != null) {
            weiter(server, true);
        }
    }

    /** Ein Tick der Animation (sofort = alle Spalten eines Schritts auf einmal, ohne Effekte). */
    private static void weiter(MinecraftServer server, boolean sofort) {
        var villa = VillaPlatzierung.villa().orElse(null);
        if (villa == null) {
            phase = null;
            return;
        }
        ServerLevel welt = server.overworld();
        int tiefe = tiefe(villa);
        int ringe = (Math.min(villa.plan().breiteX, villa.plan().breiteZ) + 1) / 2;
        switch (phase) {
            case RUNTER -> {
                if (teil == 0) {
                    effekte(welt, villa, villa.plan().bodenY - schritt, true, sofort);
                    startHoehen.clear();
                    for (Entity e : figuren(welt, villa, -schritt)) {
                        startHoehen.put(e, e.getY());
                    }
                }
                for (int t = teil; t <= (sofort ? TICKS_JE_SCHRITT - 1 : teil); t++) {
                    spaltenSchieben(welt, villa, -schritt, -1, t);
                }
                if (sofort || ++teil == TICKS_JE_SCHRITT) {
                    // Jede Figur landet genau einen Block unter ihrer Höhe vom Schrittanfang (auch wer kurz fiel)
                    for (var e : startHoehen.entrySet()) {
                        if (e.getKey() instanceof net.minecraft.world.entity.decoration.HangingEntity) {
                            verschieben(e.getKey(), -1, -1);
                        } else {
                            verschieben(e.getKey(), e.getValue() - 1 - e.getKey().getY(), -1);
                            e.getKey().setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                        }
                    }
                    startHoehen.clear();
                    teil = 0;
                    if (++schritt == tiefe) {
                        einsetzen(welt, villa, -tiefe);
                        phase = Phase.DECKEL_ZU;
                        schritt = 0;
                    }
                }
            }
            case DECKEL_ZU -> {
                for (int i = 0; i < (sofort ? ringe : RINGE_JE_TICK) && schritt < ringe; i++, schritt++) {
                    deckelRing(welt, villa, schritt, true, sofort);
                }
                if (schritt >= ringe) {
                    phase = Phase.BAEUME_HOCH;
                    schritt = 0;
                    welt.playSound(null, mitte(villa, villa.plan().bodenY), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS,
                            4.0F, 0.8F);
                }
            }
            case BAEUME_HOCH -> {
                // Ein Baum nach dem anderen wächst auf der Wiese, damit niemand das Versteck vermutet
                if (sofort || server.getTickCount() % TICKS_JE_BAUM == 0) {
                    tarnung(welt, villa, schritt, true, sofort);
                    schritt++;
                }
                if (schritt >= BAEUME) {
                    phase = null;
                }
            }
            case BAEUME_WEG -> {
                // Vor dem Auftauchen verschwinden die Bäume wieder, schnell nacheinander
                tarnung(welt, villa, schritt, false, sofort);
                if (++schritt >= BAEUME) {
                    phase = Phase.DECKEL_AUF;
                    schritt = 0;
                }
            }
            case DECKEL_AUF -> {
                for (int i = 0; i < (sofort ? ringe : RINGE_JE_TICK) && schritt < ringe; i++, schritt++) {
                    deckelRing(welt, villa, ringe - 1 - schritt, false, sofort);
                }
                if (schritt >= ringe) {
                    phase = Phase.HOCH;
                    schritt = 0;
                }
            }
            case HOCH -> {
                if (teil == 0) {
                    // Erst die Figuren anheben, dann rückt der Boden unter ihnen nach; ihre Zielhöhe merken, damit
                    // niemand in den Schritt-Ticks zurückfällt und vom nachrückenden Boden eingeklemmt wird
                    startHoehen.clear();
                    for (Entity e : figuren(welt, villa, schritt - tiefe)) {
                        startHoehen.put(e, e.getY() + 1);
                        verschieben(e, 1, 1);
                    }
                    effekte(welt, villa, villa.plan().bodenY - tiefe + schritt, false, sofort);
                } else {
                    halten();
                }
                for (int t = teil; t <= (sofort ? TICKS_JE_SCHRITT - 1 : teil); t++) {
                    spaltenSchieben(welt, villa, schritt - tiefe, 1, t);
                }
                if (sofort || ++teil == TICKS_JE_SCHRITT) {
                    teil = 0;
                    halten();
                    startHoehen.clear();
                    if (++schritt == tiefe) {
                        einsetzen(welt, villa, 0);
                        phase = null;
                        welt.playSound(null, mitte(villa, villa.plan().bodenY), SoundEvents.BEACON_ACTIVATE,
                                SoundSource.BLOCKS, 4.0F, 1.0F);
                    }
                }
            }
        }
    }

    /**
     * Verschiebt einen Teil der Spalten (Streifen in x-Richtung) um einen Block. Die Villa liegt gerade um versatz
     * verschoben; richtung -1 = nach unten, +1 = nach oben. Je Spalte wird nur gesetzt, was sich wirklich ändert –
     * Wände und Erdreich bleiben stehen, nur ihre Enden wandern.
     */
    private static void spaltenSchieben(ServerLevel welt, VillaPlatzierung.VillaInfo villa, int versatz, int richtung, int t) {
        int breiteX = villa.plan().breiteX, breiteZ = villa.plan().breiteZ;
        int unten = villa.ursprung().getY() + versatz, oben = unten + villa.plan().hoehe;    // [unten, oben)
        int x0 = breiteX * t / TICKS_JE_SCHRITT, x1 = breiteX * (t + 1) / TICKS_JE_SCHRITT;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(), q = new BlockPos.MutableBlockPos();
        BlockState luft = Blocks.AIR.defaultBlockState();
        for (int x = x0; x < x1; x++) {
            for (int z = 0; z < breiteZ; z++) {
                int wx = villa.ursprung().getX() + x, wz = villa.ursprung().getZ() + z;
                if (richtung < 0) {
                    // Von unten nach oben: jeder Block bekommt den von darüber, ganz oben Luft
                    for (int y = unten - 1; y < oben; y++) {
                        BlockState neu = y + 1 < oben ? welt.getBlockState(q.set(wx, y + 1, wz)) : luft;
                        setzen(welt, p.set(wx, y, wz), neu);
                    }
                } else {
                    // Von oben nach unten: jeder Block bekommt den von darunter, ganz unten Gestein
                    for (int y = oben; y >= unten; y--) {
                        BlockState neu = y > unten ? welt.getBlockState(q.set(wx, y - 1, wz))
                                : (y < 0 ? Blocks.DEEPSLATE : Blocks.STONE).defaultBlockState();
                        setzen(welt, p.set(wx, y, wz), neu);
                    }
                }
            }
        }
    }

    private static void setzen(ServerLevel welt, BlockPos p, BlockState neu) {
        BlockState alt = welt.getBlockState(p);
        if (alt == neu) {
            return;
        }
        if (welt.getBlockEntity(p) instanceof Clearable inhalt) {
            inhalt.clearContent();                               // Inhalte stecken in der Datei, nichts fällt heraus
        }
        welt.setBlock(p, neu, FLAGS);
    }

    /** Figuren und Spieler auf dem Grundstück (Mitfahrer sitzen schon in ihrem Fahrzeug). */
    private static java.util.List<Entity> figuren(ServerLevel welt, VillaPlatzierung.VillaInfo villa, int versatz) {
        BlockPos a = villa.ursprung().above(versatz - 1);
        AABB bereich = new AABB(a.getX(), a.getY(), a.getZ(), a.getX() + villa.plan().breiteX,
                a.getY() + villa.plan().hoehe + 3, a.getZ() + villa.plan().breiteZ);
        return welt.getEntitiesOfClass(Entity.class, bereich, e -> !e.isPassenger() && !e.isSpectator());
    }

    /** Alle Figuren fahren mit (nach oben vor dem Schritt, damit der Boden sie nicht einklemmt). */
    private static void figurenSchieben(ServerLevel welt, VillaPlatzierung.VillaInfo villa, int versatz, int richtung) {
        for (Entity e : figuren(welt, villa, versatz)) {
            verschieben(e, richtung, richtung);
        }
    }

    /** Beim Hochfahren: alle Figuren genau auf ihrer Zielhöhe des Schritts halten (ohne Fallen). */
    private static void halten() {
        for (var e : startHoehen.entrySet()) {
            Entity f = e.getKey();
            if (f.isAlive() && !(f instanceof net.minecraft.world.entity.decoration.HangingEntity)
                    && Math.abs(f.getY() - e.getValue()) > 1.0E-3) {
                f.teleportTo(f.getX(), e.getValue(), f.getZ());
            }
            f.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            f.resetFallDistance();
        }
    }

    /** Eine Figur um dy versetzen; Bilder hängen an ihrem Block, James merkt sich seine Plätze mit. */
    private static void verschieben(Entity e, double dy, int richtung) {
        if (!e.isAlive()) {
            return;
        }
        if (e instanceof net.minecraft.world.entity.decoration.HangingEntity bild) {
            // Bilder hängen an einem Block: genau diesen Block verschieben. Über die Mitte (getX/getY) gerechnet
            // wanderten zwei Blöcke breite Bilder bei jedem Schritt einen Block zur Seite.
            BlockPos haken = bild.getPos().above((int) Math.round(dy));
            bild.setPos(haken.getX() + 0.5, haken.getY() + 0.5, haken.getZ() + 0.5);
            // Bilder schicken von sich aus keine Bewegungen an die Spieler: einmal neu anmelden
            if (e.level() instanceof ServerLevel sl) {
                sl.getChunkSource().removeEntity(e);
                sl.getChunkSource().addEntity(e);
            }
        } else {
            e.teleportTo(e.getX(), e.getY() + dy, e.getZ());
        }
        e.resetFallDistance();
        if (e instanceof de.moinmornhart.stinkendreich.butler.ButlerEntity butler) {
            butler.hoeheVerschieben(richtung);
        }
    }

    /**
     * Ein Tarnbaum (Eiche oder Birke) mit Gras und Blumen drumherum setzen oder wieder entfernen. Die Lage kommt aus
     * einem Zufall, der nur von der Villa abhängt: so findet das Entfernen genau dieselben Blöcke, auch nach einem
     * Neustart. Entfernt wird nur, was noch genau so dasteht (Spielerbauten bleiben).
     */
    private static void tarnung(ServerLevel welt, VillaPlatzierung.VillaInfo villa, int nr, boolean setzen, boolean sofort) {
        java.util.Random zufall = new java.util.Random(villa.ursprung().asLong() * 31 + nr);
        int breiteX = villa.plan().breiteX, breiteZ = villa.plan().breiteZ, gras = villa.plan().bodenY;
        int x = 8 + zufall.nextInt(breiteX - 16), z = 8 + zufall.nextInt(breiteZ - 16);
        boolean birke = zufall.nextInt(3) == 0;
        int hoehe = 4 + zufall.nextInt(3);
        BlockState stamm = (birke ? Blocks.BIRCH_LOG : Blocks.OAK_LOG).defaultBlockState();
        BlockState laub = (birke ? Blocks.BIRCH_LEAVES : Blocks.OAK_LEAVES).defaultBlockState()
                .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
        java.util.List<java.util.Map.Entry<BlockPos, BlockState>> teile = new java.util.ArrayList<>();
        for (int dy = hoehe - 2; dy <= hoehe + 1; dy++) {
            int r = dy >= hoehe ? 1 : 2;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    boolean ecke = Math.abs(dx) == r && Math.abs(dz) == r;
                    if ((dx != 0 || dz != 0 || dy > hoehe) && !(ecke && (r == 1 || zufall.nextBoolean()))) {
                        teile.add(java.util.Map.entry(villa.weltPos(x + dx, gras + 1 + dy, z + dz), laub));
                    }
                }
            }
        }
        for (int dy = 0; dy < hoehe + 1; dy++) {
            teile.add(java.util.Map.entry(villa.weltPos(x, gras + 1 + dy, z), stamm));
        }
        BlockState[] bodendeko = {Blocks.SHORT_GRASS.defaultBlockState(), Blocks.SHORT_GRASS.defaultBlockState(),
                Blocks.DANDELION.defaultBlockState(), Blocks.POPPY.defaultBlockState(), Blocks.OXEYE_DAISY.defaultBlockState()};
        for (int i = 0; i < 6; i++) {
            int bx = Math.max(1, Math.min(breiteX - 2, x + zufall.nextInt(11) - 5));
            int bz = Math.max(1, Math.min(breiteZ - 2, z + zufall.nextInt(11) - 5));
            teile.add(java.util.Map.entry(villa.weltPos(bx, gras + 1, bz), bodendeko[zufall.nextInt(bodendeko.length)]));
        }
        for (var t : teile) {
            BlockState jetzt = welt.getBlockState(t.getKey());
            if (setzen ? jetzt.isAir() : jetzt.is(t.getValue().getBlock())) {
                welt.setBlock(t.getKey(), setzen ? t.getValue() : Blocks.AIR.defaultBlockState(), FLAGS);
            }
        }
        if (!sofort) {
            BlockPos fuss = villa.weltPos(x, gras + 1, z);
            welt.sendParticles(setzen ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.POOF, fuss.getX() + 0.5, fuss.getY() + 2,
                    fuss.getZ() + 0.5, 25, 1.5, 2.0, 1.5, 0.05);
            welt.playSound(null, fuss, setzen ? SoundEvents.GRASS_PLACE : SoundEvents.GRASS_BREAK, SoundSource.BLOCKS, 1.5F, 0.8F);
        }
    }

    /** Ein Ring der Wiese über dem Loch: schließen (Erde, oben Gras) oder öffnen (Luft). */
    private static void deckelRing(ServerLevel welt, VillaPlatzierung.VillaInfo villa, int ring, boolean zu, boolean sofort) {
        int breiteX = villa.plan().breiteX, breiteZ = villa.plan().breiteZ, gras = villa.plan().bodenY;
        int von = gras - DECKE + 1;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int gesetzt = 0;
        for (int x = 0; x < breiteX; x++) {
            for (int z = 0; z < breiteZ; z++) {
                if (Math.min(Math.min(x, z), Math.min(breiteX - 1 - x, breiteZ - 1 - z)) != ring) {
                    continue;
                }
                for (int y = von; y <= gras; y++) {
                    BlockState neu = !zu ? Blocks.AIR.defaultBlockState()
                            : (y == gras ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState();
                    BlockPos w = villa.weltPos(x, y, z);
                    setzen(welt, p.set(w.getX(), w.getY(), w.getZ()), neu);
                }
                if (!sofort && ++gesetzt % 7 == 0) {
                    BlockPos w = villa.weltPos(x, gras, z);
                    welt.sendParticles(zu ? ParticleTypes.CLOUD : ParticleTypes.END_ROD, w.getX() + 0.5, w.getY() + 1.1,
                            w.getZ() + 0.5, 1, 0.2, 0.1, 0.2, 0.02);
                }
            }
        }
        if (!sofort && ring % 4 == 0) {
            welt.playSound(null, mitte(villa, gras), zu ? SoundEvents.PISTON_CONTRACT : SoundEvents.PISTON_EXTEND,
                    SoundSource.BLOCKS, 3.0F, 0.6F + ring * 0.01F);
        }
    }

    /** Staub am Rand des Lochs, Kolben- und Maschinengeräusche je Schritt (hoehe = jetzige Lage des alten Rasens). */
    private static void effekte(ServerLevel welt, VillaPlatzierung.VillaInfo villa, int hoehe, boolean runter, boolean sofort) {
        if (sofort) {
            return;
        }
        int breiteX = villa.plan().breiteX, breiteZ = villa.plan().breiteZ;
        for (int i = 0; i < 24; i++) {
            int seite = welt.getRandom().nextInt(4);
            int x = seite == 0 ? 0 : seite == 1 ? breiteX - 1 : welt.getRandom().nextInt(breiteX);
            int z = seite == 2 ? 0 : seite == 3 ? breiteZ - 1 : welt.getRandom().nextInt(breiteZ);
            BlockPos w = villa.weltPos(x, villa.plan().bodenY, z);
            welt.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, w.getX() + 0.5, w.getY() + 1.0, w.getZ() + 0.5, 1,
                    0.3, 0.2, 0.3, 0.01);
        }
        BlockPos m = mitte(villa, hoehe);
        welt.playSound(null, m, runter ? SoundEvents.PISTON_CONTRACT : SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 3.0F,
                0.5F + welt.getRandom().nextFloat() * 0.2F);
        if (hoehe % 4 == 0) {
            welt.playSound(null, m, ModSounds.LASER, SoundSource.BLOCKS, 1.0F, 0.5F);
        }
    }

    /** Overlay-Hinweis für den Spieler (nicht im Chat). */
    static void melden(ServerPlayer p, boolean an) {
        Kompat.nachricht(p, Component.translatable(an ? "stinkendreich.inkognito.an" : "stinkendreich.inkognito.aus")
                .withStyle(ChatFormatting.DARK_AQUA), true);
    }
}
