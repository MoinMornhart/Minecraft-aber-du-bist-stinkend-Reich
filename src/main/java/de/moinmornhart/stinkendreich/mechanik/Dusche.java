package de.moinmornhart.stinkendreich.mechanik;

import de.moinmornhart.stinkendreich.register.ModBloecke;
import de.moinmornhart.stinkendreich.register.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Regendusche: Duschkopf ({@link DuschkopfBlock}) und Armatur ({@link DuscharmaturBlock}).
 * <p>
 * Läuft die Dusche, regnet es unter dem Kopf bis zum Boden (Wassertropfen, Spritzer am Boden), es rauscht, und wer
 * darunter steht, wird nass: Feuer geht aus. Es gibt keinen echten Wasserblock mehr. Nach {@link #LAUFZEIT} Ticks
 * dreht sich die Dusche von selbst ab. Die Armatur schaltet den nächsten Duschkopf im Umkreis von
 * {@link #ARMATUR_REICHWEITE} Blöcken; ihr Griff zeigt an, ob das Wasser läuft.
 */
public final class Dusche {
    /** So lange läuft die Dusche nach dem Aufdrehen (60 Sekunden). */
    public static final int LAUFZEIT = 60 * 20;
    /** Takt des Duschkopfs in Ticks (Tropfen werden so oft nachgeliefert). */
    public static final int TAKT = 2;
    /** Umkreis, in dem eine Armatur ihren Duschkopf sucht (und mitschaltet). */
    public static final int ARMATUR_REICHWEITE = 4;
    /** So weit sucht der Wasserstrahl nach unten den Boden. */
    private static final int MAX_FALLHOEHE = 8;
    /** Unterkante der Brauseplatte im Block (10 von 16 Pixeln). */
    private static final double PLATTE_UNTEN = 10.0 / 16.0;
    /** Halbe Breite des Regens (die Brauseplatte ist 14 Pixel breit). */
    private static final double HALBE_BREITE = 0.4;

    private Dusche() {
    }

    /** Dreht die Dusche an oder ab und stellt die Griffe der Armaturen in der Nähe passend. */
    public static void schalten(ServerLevel welt, BlockPos kopf, boolean an) {
        BlockState zustand = welt.getBlockState(kopf);
        if (!zustand.is(ModBloecke.DUSCHKOPF)) {
            return;
        }
        if (zustand.getValue(Eigenschaften.LAEUFT) != an) {
            welt.setBlock(kopf, zustand.setValue(Eigenschaften.LAEUFT, an), Block.UPDATE_ALL);
        }
        MechanikBlockEntity d = MechanikBlock.daten(welt, kopf);
        if (d != null) {
            d.setzeStill("rest", an ? LAUFZEIT : 0);
            d.setzeStill("takt", 0);
        }
        if (an) {
            // Erster Schwall sofort, nicht erst beim nächsten Takt
            welt.playSound(null, kopf, ModSounds.DUSCHE, SoundSource.BLOCKS, 0.8F, 1.0F);
        }
        // Griffe der Armaturen, die zu diesem Kopf gehören, mitdrehen
        for (BlockPos p : BlockPos.betweenClosed(kopf.offset(-ARMATUR_REICHWEITE, -ARMATUR_REICHWEITE, -ARMATUR_REICHWEITE),
                kopf.offset(ARMATUR_REICHWEITE, ARMATUR_REICHWEITE, ARMATUR_REICHWEITE))) {
            BlockState s = welt.getBlockState(p);
            if (s.is(ModBloecke.DUSCHARMATUR) && s.getValue(Eigenschaften.LAEUFT) != an && kopf.equals(naechsterKopf(welt, p))) {
                welt.setBlock(p.immutable(), s.setValue(Eigenschaften.LAEUFT, an), Block.UPDATE_ALL);
            }
        }
    }

    /** Der nächste Duschkopf im Umkreis der Armatur oder null. */
    public static BlockPos naechsterKopf(ServerLevel welt, BlockPos armatur) {
        BlockPos bester = null;
        double abstand = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(armatur.offset(-ARMATUR_REICHWEITE, -ARMATUR_REICHWEITE, -ARMATUR_REICHWEITE),
                armatur.offset(ARMATUR_REICHWEITE, ARMATUR_REICHWEITE, ARMATUR_REICHWEITE))) {
            if (welt.getBlockState(p).is(ModBloecke.DUSCHKOPF)) {
                double a = p.distSqr(armatur);
                if (a < abstand) {
                    abstand = a;
                    bester = p.immutable();
                }
            }
        }
        return bester;
    }

    /** Takt des laufenden Duschkopfs: Regen, Rauschen, nass machen, nach Ablauf der Zeit abdrehen. */
    static void takt(ServerLevel welt, BlockPos kopf, BlockState zustand, MechanikBlockEntity d) {
        if (!zustand.getValue(Eigenschaften.LAEUFT)) {
            return;
        }
        int rest = d.zahl("rest", LAUFZEIT) - TAKT;
        if (rest <= 0) {
            schalten(welt, kopf, false);
            welt.playSound(null, kopf, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4F, 0.8F);
            return;
        }
        d.setzeStill("rest", rest);
        int takt = d.zahl("takt", 0) + TAKT;
        d.setzeStill("takt", takt);

        double boden = bodenHoehe(welt, kopf);
        double oben = kopf.getY() + PLATTE_UNTEN - 0.03;
        double mx = kopf.getX() + 0.5;
        double mz = kopf.getZ() + 0.5;
        RandomSource zufall = welt.getRandom();
        // Regen aus den Düsen: fallende Tropfen, die am Boden zerspritzen
        for (int i = 0; i < 7; i++) {
            double x = mx + (zufall.nextDouble() * 2 - 1) * HALBE_BREITE;
            double z = mz + (zufall.nextDouble() * 2 - 1) * HALBE_BREITE;
            welt.sendParticles(ParticleTypes.FALLING_WATER, x, oben, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        // Mitten im Strahl ein paar schnelle Regentropfen, unten Spritzer
        welt.sendParticles(ParticleTypes.RAIN, mx, (oben + boden) / 2, mz, 3, 0.25, (oben - boden) / 4, 0.25, 0.0);
        welt.sendParticles(ParticleTypes.SPLASH, mx, boden + 0.05, mz, 5, 0.3, 0.0, 0.3, 0.0);

        // Rauschen: das Geräusch ist gut 2 s lang und wird lückenlos nachgelegt
        if (takt % 40 == 0) {
            welt.playSound(null, kopf, ModSounds.DUSCHE, SoundSource.BLOCKS, 0.8F, 0.95F + zufall.nextFloat() * 0.1F);
        }
        // Wer unter der Brause steht, wird nass: Feuer aus
        if (takt % 10 == 0) {
            AABB bereich = new AABB(mx - 0.6, boden, mz - 0.6, mx + 0.6, oben, mz + 0.6);
            for (Entity e : welt.getEntitiesOfClass(Entity.class, bereich, Entity::isOnFire)) {
                e.clearFire();
                welt.playSound(null, e.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 1.4F);
                welt.sendParticles(ParticleTypes.CLOUD, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 4, 0.2, 0.3, 0.2, 0.01);
            }
        }
    }

    /** Höhe des Bodens unter dem Duschkopf (Oberkante des ersten festen Blocks oder Wassers). */
    private static double bodenHoehe(ServerLevel welt, BlockPos kopf) {
        BlockPos.MutableBlockPos p = kopf.mutable();
        for (int i = 0; i < MAX_FALLHOEHE; i++) {
            p.move(0, -1, 0);
            BlockState s = welt.getBlockState(p);
            if (!s.getFluidState().isEmpty()) {
                return p.getY() + 0.9;
            }
            var form = s.getCollisionShape(welt, p);
            if (!form.isEmpty()) {
                return p.getY() + form.max(net.minecraft.core.Direction.Axis.Y);
            }
        }
        return kopf.getY() - MAX_FALLHOEHE;
    }
}
