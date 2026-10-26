package de.moinmornhart.stinkendreich.moebel;

import de.moinmornhart.stinkendreich.mechanik.DekoBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/** Nachttisch mit Schublade: Rechtsklick zieht sie auf (neun Plätze, siehe {@link SchubladeBlockEntity}). */
public class NachttischBlock extends DekoBlock implements EntityBlock {
    public NachttischBlock(Properties eigenschaften, List<double[]> kaesten, boolean fest, double sitz) {
        super(eigenschaften, kaesten, fest, sitz);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState zustand) {
        return new SchubladeBlockEntity(pos, zustand);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState zustand, Level welt, BlockPos pos, Player spieler, BlockHitResult treffer) {
        if (!welt.isClientSide() && welt.getBlockEntity(pos) instanceof SchubladeBlockEntity schublade) {
            spieler.openMenu(schublade);
            welt.playSound(null, pos, SoundEvents.CHISELED_BOOKSHELF_PICKUP, SoundSource.BLOCKS, 0.8F, 1.2F);
        }
        return InteractionResult.SUCCESS;
    }

    //? if <1.21.5 {
    /*@Override
    protected void onRemove(BlockState alt, Level welt, BlockPos pos, BlockState neu, boolean bewegt) {
        // Inhalt fällt heraus, wenn der Nachttisch abgebaut wird (ab 1.21.5 macht das die Block-Entity selbst)
        net.minecraft.world.Containers.dropContentsOnDestroy(alt, neu, welt, pos);
        super.onRemove(alt, welt, pos, neu, bewegt);
    }
    *///?}
}
