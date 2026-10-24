package de.moinmornhart.stinkendreich.mechanik;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;

/** Überwachungsmonitor: Rechtsklick schaltet den Blick auf die Kameras in der Nähe. */
public class MonitorBlock extends MechanikBlock {
    public MonitorBlock(Properties eigenschaften) {
        super(eigenschaften, Ausrichtung.WAAGERECHT, false, false);
    }

    @Override
    protected Property<?>[] grundEigenschaften() {
        return new Property<?>[]{Eigenschaften.RICHTUNG};
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState zustand, Level welt, BlockPos pos, Player spieler, BlockHitResult treffer) {
        if (welt.isClientSide()) {
            KlientBruecke.monitor.accept(pos);
        }
        return InteractionResult.SUCCESS;
    }
}
