package de.moinmornhart.stinkendreich.mechanik;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;

/** Schalter für den Kamin: zündet alle Lagerfeuer im Umkreis von 7 Blöcken an oder löscht sie (der Schalter im Wohnzimmer sitzt 6 Blöcke vom linken Feuer). */
public class KaminsteuerungBlock extends MechanikBlock {
    public KaminsteuerungBlock(Properties eigenschaften) {
        super(eigenschaften, Ausrichtung.WAAGERECHT, true, false);
    }

    @Override
    protected Property<?>[] grundEigenschaften() {
        return new Property<?>[]{Eigenschaften.RICHTUNG};
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState zustand, Level welt, BlockPos pos, Player spieler, BlockHitResult treffer) {
        if (!welt.isClientSide()) {
            Boolean an = null;
            for (BlockPos p : BlockPos.betweenClosed(pos.offset(-7, -2, -7), pos.offset(7, 2, 7))) {
                BlockState s = welt.getBlockState(p);
                if (s.getBlock() instanceof CampfireBlock) {
                    if (an == null) {
                        an = !s.getValue(CampfireBlock.LIT);
                    }
                    welt.setBlock(p, s.setValue(CampfireBlock.LIT, an), 3);
                }
            }
        }
        return InteractionResult.SUCCESS;
    }
}
