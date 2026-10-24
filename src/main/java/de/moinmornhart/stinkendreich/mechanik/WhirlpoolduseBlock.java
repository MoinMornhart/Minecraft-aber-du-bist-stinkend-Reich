package de.moinmornhart.stinkendreich.mechanik;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

/** Whirlpool-Düse im Boden: sprudelt und hebt Badende sanft an. Rechtsklick schaltet ein und aus. */
public class WhirlpoolduseBlock extends MechanikBlock {
    public WhirlpoolduseBlock(Properties eigenschaften) {
        super(eigenschaften, Ausrichtung.KEINE, false, false);
    }

    @Override
    protected Property<?>[] grundEigenschaften() {
        return new Property<?>[0];
    }

    @Override
    public int takt() {
        return 4;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState zustand, Level welt, BlockPos pos, Player spieler, BlockHitResult treffer) {
        MechanikBlockEntity d = daten(welt, pos);
        if (!welt.isClientSide() && d != null) {
            d.setze("aus", d.zahl("aus", 0) == 1 ? 0 : 1);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void takt(ServerLevel welt, BlockPos pos, BlockState zustand, MechanikBlockEntity d) {
        if (d.zahl("aus", 0) == 1) {
            return;
        }
        welt.sendParticles(ParticleTypes.BUBBLE, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 12, 1.2, 0.2, 1.2, 0.05);
        welt.sendParticles(ParticleTypes.BUBBLE_POP, pos.getX() + 0.5, pos.getY() + 2.0, pos.getZ() + 0.5, 6, 1.2, 0.1, 1.2, 0.02);
        // Gegenstände und Tiere im Becken sanft anheben (Spieler bewegen sich selbst)
        for (Entity e : welt.getEntitiesOfClass(Entity.class, new AABB(pos.above()).inflate(1.5, 1, 1.5),
                e -> e.isInWater() && !(e instanceof Player))) {
            e.setDeltaMovement(e.getDeltaMovement().add(0, 0.05, 0));
        }
    }
}
