package de.moinmornhart.stinkendreich.mechanik;

import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.register.ModBloecke;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Laser-Sender: Ab Sicherheitsstufe 3 spannt er einen roten Laserstrahl bis zur nächsten Wand
 * (höchstens 24 Blöcke). Eindringlinge im Strahl lösen Alarm aus und nehmen Schaden.
 * Bewohner und Spieler mit Zugangskarte können den Strahl gefahrlos durchqueren.
 */
public class LaserSenderBlock extends MechanikBlock {
    public static final int REICHWEITE = 24;

    public LaserSenderBlock(Properties eigenschaften) {
        super(eigenschaften, Ausrichtung.ALLE, false, false);
    }

    @Override
    protected Property<?>[] grundEigenschaften() {
        return new Property<?>[]{Eigenschaften.RICHTUNG_ALLE};
    }

    @Override
    public int takt() {
        return 5;
    }

    @Override
    public void takt(ServerLevel welt, BlockPos pos, BlockState zustand, MechanikBlockEntity daten) {
        VillaZustand z = VillaZustand.get(welt.getServer());
        boolean an = z.stufe() >= 3;
        Direction richtung = zustand.getValue(Eigenschaften.RICHTUNG_ALLE);
        BlockState strahl = ModBloecke.LASERSTRAHL.defaultBlockState().setValue(LaserstrahlBlock.ACHSE, richtung.getAxis());

        int laenge = 0;
        BlockPos.MutableBlockPos p = pos.mutable();
        for (int i = 1; i <= REICHWEITE; i++) {
            p.move(richtung);
            BlockState s = welt.getBlockState(p);
            if (s.is(ModBloecke.LASERSTRAHL)) {
                if (!an) {
                    welt.removeBlock(p, false);
                }
            } else if (s.isAir()) {
                if (an) {
                    welt.setBlock(p, strahl, 3);
                }
            } else {
                break;
            }
            laenge = i;
        }
        if (!an || laenge == 0) {
            return;
        }
        // Wer steht im Strahl?
        BlockPos ende = pos.relative(richtung, laenge);
        AABB bereich = new AABB(Vec3.atCenterOf(pos.relative(richtung)), Vec3.atCenterOf(ende)).inflate(0.3);
        for (LivingEntity e : welt.getEntitiesOfClass(LivingEntity.class, bereich, z::istEindringling)) {
            z.alarm(e.blockPosition(), Component.translatable("stinkendreich.alarm.grund.laser", e.getDisplayName()));
            Kompat.schaden(e, welt, 3.0F);
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 30, 0));
        }
    }
}
