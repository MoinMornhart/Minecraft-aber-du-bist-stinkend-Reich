package de.moinmornhart.stinkendreich.mechanik;

import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.hai.HaiEntity;
import de.moinmornhart.stinkendreich.register.ModBloecke;
import de.moinmornhart.stinkendreich.villa.VillaPlatzierung;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Haifischfalle im Wohnzimmer: Unter einem Boden aus dunklem Glas ({@link HaifalleBlock}) schwimmen Haie
 * ({@link HaiEntity}). Ein Bewohner drückt den roten Knopf ({@link HaifalleSchalterBlock}): Der nächste Eindringling
 * in 16 Blöcken wird mitten auf die Glasfläche versetzt, der Boden klappt für 5 Sekunden auf, und er fällt ins Becken.
 * <p>
 * Automatik: Greift ein Spieler, der kein Bewohner ist, auf dem Grundstück einen Bewohner an, löst der nächste Schalter
 * in 48 Blöcken mit dem Angreifer als Opfer aus.
 */
public final class Haifalle {
    /** In diesem Umkreis um den Schalter wird ein Opfer gesucht. */
    public static final int OPFER_RADIUS = 16;
    /** Alle Glasböden in diesem Umkreis um den Schalter gehören zu ihm. */
    public static final int FALLEN_RADIUS = 12;
    /** So weit sucht die Automatik nach einem Schalter. */
    public static final int AUTOMATIK_RADIUS = 48;
    /** So lange bleibt der Boden offen (5 Sekunden). */
    public static final int OFFEN_TICKS = 100;
    /** Ein Schalter löst erst wieder aus, wenn der Boden zu ist (plus eine Sekunde). */
    private static final int PAUSE_TICKS = OFFEN_TICKS + 20;

    /** Ergebnis eines Auslösens (für die Meldung an den Bewohner). */
    public enum Ergebnis { OK, KEINE_FALLE, KEIN_OPFER, LAEUFT }

    private Haifalle() {
    }

    public static void registrieren() {
        // Nur zuschauen: Der Schaden selbst wird nie verhindert
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((opfer, quelle, menge) -> {
            if (opfer instanceof ServerPlayer bewohner && quelle.getEntity() instanceof ServerPlayer angreifer && angreifer != bewohner) {
                automatik(bewohner, angreifer);
            }
            return true;
        });
    }

    // ------------------------------------------------------------------
    // Auslösen
    // ------------------------------------------------------------------

    /**
     * Löst die Falle am Schalter {@code schalter} aus. {@code opfer} null: der nächste Eindringling in 16 Blöcken
     * (Spieler vor Monstern).
     */
    public static Ergebnis ausloesen(ServerLevel welt, BlockPos schalter, LivingEntity opfer) {
        MechanikBlockEntity daten = MechanikBlock.daten(welt, schalter);
        long jetzt = welt.getGameTime();
        if (daten != null && jetzt - daten.zahl("zuletzt", -100000) < PAUSE_TICKS && jetzt >= daten.zahl("zuletzt", -100000)) {
            return Ergebnis.LAEUFT;
        }
        List<BlockPos> fallen = fallenUm(welt, schalter);
        if (fallen.isEmpty()) {
            return Ergebnis.KEINE_FALLE;
        }
        VillaZustand z = VillaZustand.fuer(welt.getServer(), schalter);
        if (opfer == null) {
            opfer = opferSuchen(welt, schalter, z);
        }
        if (opfer == null) {
            return Ergebnis.KEIN_OPFER;
        }
        if (daten != null) {
            daten.setzeStill("zuletzt", (int) jetzt);
        }

        // 1. Rein mit ihm: mitten auf die nächstgelegene Glasfläche
        BlockPos ziel = flaechenMitte(fallen, schalter);
        Vec3 von = opfer.position();
        welt.sendParticles(ParticleTypes.PORTAL, von.x, von.y + 1.0, von.z, 40, 0.4, 0.8, 0.4, 0.2);
        welt.sendParticles(ParticleTypes.CLOUD, von.x, von.y + 0.5, von.z, 12, 0.3, 0.4, 0.3, 0.02);
        welt.playSound(null, BlockPos.containing(von), SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 1.0F, 0.8F);
        opfer.stopRiding();
        opfer.teleportTo(ziel.getX() + 0.5, ziel.getY() + 1.0, ziel.getZ() + 0.5);
        opfer.setDeltaMovement(Vec3.ZERO);
        opfer.resetFallDistance();
        Kompat.bewegungSenden(opfer);
        Vec3 nach = Vec3.atBottomCenterOf(ziel.above());
        welt.sendParticles(ParticleTypes.PORTAL, nach.x, nach.y + 1.0, nach.z, 40, 0.4, 0.8, 0.4, 0.2);
        welt.playSound(null, ziel.above(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 1.0F, 1.0F);
        if (opfer instanceof Player p) {
            Kompat.nachricht(p, Component.translatable("stinkendreich.haifalle.opfer").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        }

        // 2. Boden auf, nach 5 Sekunden schließt der Schalter ihn wieder
        bodenAuf(welt, schalter, fallen, ziel);
        return Ergebnis.OK;
    }

    /** Öffnet alle Glasböden; der Schalter schließt sie nach 5 Sekunden wieder. */
    private static void bodenAuf(ServerLevel welt, BlockPos schalter, List<BlockPos> fallen, BlockPos ziel) {
        Vec3 nach = Vec3.atBottomCenterOf(ziel.above());
        for (BlockPos p : fallen) {
            BlockState s = welt.getBlockState(p);
            if (!s.getValue(Eigenschaften.OFFEN)) {
                welt.setBlock(p, s.setValue(Eigenschaften.OFFEN, true), 3);
            }
            // Sicherung, falls der Schalter inzwischen fehlt: der Boden schließt sich etwas später selbst
            welt.scheduleTick(p, ModBloecke.HAIFALLE, OFFEN_TICKS + 60);
        }
        welt.playSound(null, ziel, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 1.2F, 0.7F);
        welt.playSound(null, ziel, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.8F);
        welt.sendParticles(ParticleTypes.BUBBLE_POP, nach.x, nach.y - 1.0, nach.z, 30, 1.5, 0.2, 1.5, 0.05);
        welt.scheduleTick(schalter, ModBloecke.HAIFALLE_SCHALTER, OFFEN_TICKS);
    }

    /**
     * Smart-Home-App: auf = den nächsten Eindringling am Becken hineinwerfen; ist keiner da, geht der Glasboden trotzdem
     * für 5 Sekunden auf (wer darauf steht, fällt hinein). zu = sofort schließen. Gibt einen Fehlertext zurück oder null.
     */
    public static String smartHome(net.minecraft.server.MinecraftServer server, int nr, boolean auf) {
        var villa = VillaPlatzierung.nr(nr);
        if (villa == null || villa.plan().marke("haibecken").isEmpty()) {
            return "stinkendreich.haifalle.keine_falle";
        }
        var m = villa.plan().marke("haibecken").get(0);
        ServerLevel welt = server.overworld();
        BlockPos becken = villa.weltPos(m.get(0).getAsDouble(), m.get(1).getAsDouble(), m.get(2).getAsDouble());
        BlockPos schalter = naechsterSchalter(welt, becken);
        if (schalter == null) {
            return "stinkendreich.haifalle.keine_falle";
        }
        if (!auf) {
            schliessen(welt, schalter);
            anzeigen(server, nr, "stinkendreich.smarthome.haie.zu");
            return null;
        }
        Ergebnis e = ausloesen(welt, schalter, null);
        if (e == Ergebnis.KEIN_OPFER) {
            List<BlockPos> fallen = fallenUm(welt, schalter);
            bodenAuf(welt, schalter, fallen, flaechenMitte(fallen, schalter));
            e = Ergebnis.OK;
        }
        if (e == Ergebnis.LAEUFT) {
            return "stinkendreich.haifalle.laeuft";
        }
        anzeigen(server, nr, "stinkendreich.smarthome.haie.auf");
        return e == Ergebnis.OK ? null : "stinkendreich.haifalle.keine_falle";
    }

    private static void anzeigen(net.minecraft.server.MinecraftServer server, int nr, String text) {
        VillaZustand.get(server, nr).anBewohner(Component.translatable(text).withStyle(ChatFormatting.AQUA), true);
    }

    /**
     * Schließt die offenen Glasböden um den Schalter. Wo noch jemand im Weg ist (Kopf noch nicht unter der Kante),
     * bleibt der Boden offen; dann gibt es {@code true} zurück, und der Schalter versucht es gleich noch einmal.
     */
    public static boolean schliessen(ServerLevel welt, BlockPos schalter) {
        boolean geschlossen = false, blockiert = false;
        BlockPos mitte = null;
        for (BlockPos p : fallenUm(welt, schalter)) {
            BlockState s = welt.getBlockState(p);
            if (!s.getValue(Eigenschaften.OFFEN)) {
                continue;
            }
            if (imWeg(welt, p)) {
                blockiert = true;
                continue;
            }
            welt.setBlock(p, s.setValue(Eigenschaften.OFFEN, false), 3);
            geschlossen = true;
            mitte = p;
        }
        if (geschlossen) {
            welt.playSound(null, mitte, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.2F, 0.7F);
            welt.playSound(null, mitte, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 0.8F);
        }
        return blockiert;
    }

    /** Steckt gerade ein Lebewesen in diesem Feld (würde beim Schließen eingeklemmt)? */
    static boolean imWeg(ServerLevel welt, BlockPos p) {
        return !welt.getEntitiesOfClass(LivingEntity.class, new AABB(p), e -> e.isAlive() && !e.isSpectator()).isEmpty();
    }

    // ------------------------------------------------------------------
    // Automatik
    // ------------------------------------------------------------------

    private static void automatik(ServerPlayer bewohner, ServerPlayer angreifer) {
        if (!(bewohner.level() instanceof ServerLevel welt) || welt != welt.getServer().overworld() || angreifer.isSpectator()) {
            return;
        }
        var villa = VillaPlatzierung.bei(bewohner.blockPosition());
        if (villa == null || !villa.enthaelt(bewohner.blockPosition())) {
            return;
        }
        VillaZustand z = VillaZustand.get(welt.getServer(), villa.nr());
        if (!z.istBewohner(bewohner) || z.istBewohner(angreifer)) {
            return;
        }
        BlockPos schalter = naechsterSchalter(welt, angreifer.blockPosition());
        if (schalter == null) {
            return;
        }
        if (ausloesen(welt, schalter, angreifer) == Ergebnis.OK) {
            z.anBewohner(Component.translatable("stinkendreich.haifalle.automatik", angreifer.getScoreboardName())
                    .withStyle(ChatFormatting.GOLD), true);
        }
    }

    /** Der nächste Haifallen-Schalter in 48 Blöcken (gesucht über die Block-Entities der geladenen Chunks), oder null. */
    private static BlockPos naechsterSchalter(ServerLevel welt, BlockPos um) {
        BlockPos bester = null;
        double besterAbstand = (double) AUTOMATIK_RADIUS * AUTOMATIK_RADIUS;
        int r = (AUTOMATIK_RADIUS >> 4) + 1;
        for (int cx = (um.getX() >> 4) - r; cx <= (um.getX() >> 4) + r; cx++) {
            for (int cz = (um.getZ() >> 4) - r; cz <= (um.getZ() >> 4) + r; cz++) {
                LevelChunk chunk = welt.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockPos p : chunk.getBlockEntities().keySet()) {
                    double d = p.distSqr(um);
                    if (d < besterAbstand && welt.getBlockState(p).is(ModBloecke.HAIFALLE_SCHALTER)) {
                        besterAbstand = d;
                        bester = p.immutable();
                    }
                }
            }
        }
        return bester;
    }

    // ------------------------------------------------------------------
    // Hilfen
    // ------------------------------------------------------------------

    /** Alle Glasböden der Haifalle im Umkreis von 12 Blöcken um den Schalter. */
    static List<BlockPos> fallenUm(ServerLevel welt, BlockPos schalter) {
        List<BlockPos> liste = new ArrayList<>();
        int r = FALLEN_RADIUS;
        for (BlockPos p : BlockPos.betweenClosed(schalter.offset(-r, -r, -r), schalter.offset(r, r, r))) {
            if (p.distSqr(schalter) <= (double) r * r && welt.getBlockState(p).is(ModBloecke.HAIFALLE)) {
                liste.add(p.immutable());
            }
        }
        return liste;
    }

    /**
     * Mitte der Glasfläche, die dem Schalter am nächsten liegt: zusammenhängende Böden auf derselben Höhe; als Mitte
     * gilt das Feld, das dem Schwerpunkt am nächsten ist (so landet das Opfer auch bei einer L-Form auf Glas).
     */
    private static BlockPos flaechenMitte(List<BlockPos> fallen, BlockPos schalter) {
        BlockPos start = fallen.get(0);
        for (BlockPos p : fallen) {
            if (p.distSqr(schalter) < start.distSqr(schalter)) {
                start = p;
            }
        }
        Set<BlockPos> alle = new HashSet<>(fallen);
        Set<BlockPos> flaeche = new HashSet<>();
        ArrayDeque<BlockPos> offen = new ArrayDeque<>();
        offen.add(start);
        flaeche.add(start);
        while (!offen.isEmpty()) {
            BlockPos p = offen.poll();
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = p.relative(d);
                if (alle.contains(n) && flaeche.add(n)) {
                    offen.add(n);
                }
            }
        }
        double sx = 0, sz = 0;
        for (BlockPos p : flaeche) {
            sx += p.getX();
            sz += p.getZ();
        }
        sx /= flaeche.size();
        sz /= flaeche.size();
        BlockPos mitte = start;
        double beste = Double.MAX_VALUE;
        for (BlockPos p : flaeche) {
            double d = (p.getX() - sx) * (p.getX() - sx) + (p.getZ() - sz) * (p.getZ() - sz);
            if (d < beste) {
                beste = d;
                mitte = p;
            }
        }
        return mitte;
    }

    /**
     * Das nächste Opfer in 16 Blöcken: zuerst Spieler, die keine Bewohner sind (nicht im Kreativ- oder
     * Zuschauermodus), sonst ein Monster. Nie Bewohner, Personal (Butler, Mitarbeiter, Bodyguards, Tänzerinnen),
     * Haie, Tiere oder Dorfbewohner.
     */
    private static LivingEntity opferSuchen(ServerLevel welt, BlockPos schalter, VillaZustand z) {
        Vec3 mitte = Vec3.atCenterOf(schalter);
        AABB feld = AABB.ofSize(mitte, OPFER_RADIUS * 2, OPFER_RADIUS * 2, OPFER_RADIUS * 2);
        LivingEntity spieler = naechstes(welt.getEntitiesOfClass(Player.class, feld,
                p -> p.isAlive() && z.istEindringling(p)), mitte);
        if (spieler != null) {
            return spieler;
        }
        return naechstes(welt.getEntitiesOfClass(LivingEntity.class, feld,
                e -> !(e instanceof Player) && !(e instanceof HaiEntity) && Festung.ziel(e, z)), mitte);
    }

    private static LivingEntity naechstes(List<? extends LivingEntity> liste, Vec3 mitte) {
        LivingEntity bestes = null;
        double beste = (double) OPFER_RADIUS * OPFER_RADIUS;
        for (LivingEntity e : liste) {
            double d = e.position().distanceToSqr(mitte);
            if (d <= beste) {
                beste = d;
                bestes = e;
            }
        }
        return bestes;
    }
}
