package de.moinmornhart.stinkendreich.mechanik;

import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.bodyguard.BodyguardEntity;
import de.moinmornhart.stinkendreich.butler.ButlerEntity;
import de.moinmornhart.stinkendreich.club.TaenzerinEntity;
import de.moinmornhart.stinkendreich.register.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Festung (Sicherheitsstufe 4, totaler Lockdown): Minen ({@link LandmineBlock}) und Abwehrgeschütze
 * ({@link AbwehrgeschuetzBlock}) treffen alle Monster und Eindringlinge, nie Bewohner, Kartenbesitzer, das Personal,
 * Tiere oder Dorfbewohner. Explosionen beschädigen keine Blöcke.
 */
public final class Festung {
    private Festung() {
    }

    /**
     * Für die Animation beim Einschalten: nach wie vielen Ticks ein Teil der Festung hochfährt. {@code umlauf}: Welle
     * einmal rund um die Villa (Geschütze, Himmelslaser), sonst von innen nach außen (Minen).
     */
    public static long verzoegerung(BlockPos pos, boolean umlauf) {
        var villa = de.moinmornhart.stinkendreich.villa.VillaPlatzierung.villa();
        if (villa.isEmpty()) {
            return 0;
        }
        BlockPos u = villa.get().ursprung();
        double mx = u.getX() + villa.get().plan().breiteX / 2.0, mz = u.getZ() + villa.get().plan().breiteZ / 2.0;
        double dx = pos.getX() + 0.5 - mx, dz = pos.getZ() + 0.5 - mz;
        if (umlauf) {
            return (long) ((Math.atan2(dz, dx) + Math.PI) / (2 * Math.PI) * 60);       // 3 Sekunden rundherum
        }
        return (long) Mth.clamp((Math.sqrt(dx * dx + dz * dz) - 56) * 2.0, 0, 100);    // nach außen, bis 5 Sekunden
    }

    /** Ist dieser Teil der Festung schon hochgefahren? */
    public static boolean bereit(VillaZustand z, BlockPos pos, boolean umlauf) {
        return z.festung() && z.festungTicks() >= verzoegerung(pos, umlauf);
    }

    /** Ist diese Figur ein Ziel der Festung? */
    public static boolean ziel(Entity e, VillaZustand z) {
        if (!(e instanceof LivingEntity l) || !l.isAlive() || e.isSpectator()) {
            return false;
        }
        if (e instanceof Player p) {
            return z.istEindringling(p);
        }
        if (e instanceof ButlerEntity || e instanceof BodyguardEntity || e instanceof TaenzerinEntity) {
            return false;
        }
        return e instanceof Enemy;
    }

    /** Explosion einer Mine: Feuer, Rauch und Knall, Schaden nur für Ziele in 4 Blöcken, keine Blockschäden. */
    public static void explosion(ServerLevel welt, Vec3 mitte, VillaZustand z) {
        welt.sendParticles(ParticleTypes.EXPLOSION_EMITTER, mitte.x, mitte.y + 0.3, mitte.z, 1, 0, 0, 0, 0);
        welt.sendParticles(ParticleTypes.EXPLOSION, mitte.x, mitte.y + 0.6, mitte.z, 8, 1.0, 0.6, 1.0, 0);
        welt.sendParticles(ParticleTypes.LARGE_SMOKE, mitte.x, mitte.y + 0.5, mitte.z, 30, 0.9, 0.7, 0.9, 0.05);
        welt.sendParticles(ParticleTypes.FLAME, mitte.x, mitte.y + 0.3, mitte.z, 25, 0.7, 0.3, 0.7, 0.1);
        welt.playSound(null, BlockPos.containing(mitte), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 3.0F, 0.9F);
        for (LivingEntity e : welt.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(mitte, 8, 6, 8), e -> ziel(e, z))) {
            double abstand = e.position().distanceTo(mitte);
            if (abstand > 4.0) {
                continue;
            }
            Kompat.treffen(e, welt, welt.damageSources().explosion(null, null), (float) (60.0 * (1.0 - abstand / 5.0)));
            Vec3 weg = e.position().subtract(mitte);
            weg = weg.lengthSqr() < 1.0E-4 ? Vec3.ZERO : weg.normalize().scale(1.2);
            e.setDeltaMovement(weg.add(0, 0.9, 0));
            Kompat.bewegungSenden(e);
        }
    }

    /**
     * Ein Schuss des Abwehrgeschützes: nächstes Ziel in Reichweite mit freier Sicht, roter Laserstrahl, Schaden und
     * Feuer. Gibt zurück, ob geschossen wurde.
     */
    public static boolean schiessen(ServerLevel welt, BlockPos turm, VillaZustand z, double reichweite, float schaden) {
        Vec3 von = Vec3.atCenterOf(turm).add(0, 0.65, 0);
        LivingEntity bestes = null;
        double besterAbstand = reichweite * reichweite;
        for (LivingEntity e : welt.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(von, reichweite * 2, reichweite * 2, reichweite * 2),
                e -> ziel(e, z))) {
            Vec3 auf = e.getBoundingBox().getCenter();
            double d = auf.distanceToSqr(von);
            if (d >= besterAbstand || !freieSicht(welt, von, auf)) {
                continue;
            }
            bestes = e;
            besterAbstand = d;
        }
        if (bestes == null) {
            return false;
        }
        Vec3 auf = bestes.getBoundingBox().getCenter();
        Vec3 weg = auf.subtract(von);
        int n = Mth.clamp((int) (weg.length() / 0.35), 1, 200);
        for (int i = 0; i <= n; i++) {
            Vec3 p = von.add(weg.scale((double) i / n));
            welt.sendParticles(DustParticleOptions.REDSTONE, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        welt.sendParticles(ParticleTypes.ELECTRIC_SPARK, auf.x, auf.y, auf.z, 10, 0.2, 0.2, 0.2, 0.1);
        welt.playSound(null, turm, ModSounds.LASER, SoundSource.BLOCKS, 1.6F, 0.8F);
        Kompat.treffen(bestes, welt, welt.damageSources().magic(), schaden);
        bestes.igniteForSeconds(3.0F);
        return true;
    }

    private static boolean freieSicht(ServerLevel welt, Vec3 von, Vec3 bis) {
        return welt.clip(new ClipContext(von, bis, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()))
                .getType() == HitResult.Type.MISS;
    }
}
