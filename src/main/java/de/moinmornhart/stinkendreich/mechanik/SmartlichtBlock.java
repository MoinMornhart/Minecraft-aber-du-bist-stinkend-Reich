package de.moinmornhart.stinkendreich.mechanik;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Smart-Home-Hängelampe: an der Kette unter der Decke, mit farbigem Glasschirm. Die Villa schaltet alle Lampen über
 * die Smart-Home-App im Handy und die Automatik ({@link SmartHome}): an/aus, acht Farben, Disco. Rechtsklick schaltet
 * eine einzelne Lampe an oder aus.
 */
public class SmartlichtBlock extends Block {
    private static final VoxelShape FORM = Block.box(4, 1, 4, 12, 16, 12);

    public SmartlichtBlock(Properties eigenschaften) {
        super(eigenschaften);
        registerDefaultState(stateDefinition.any().setValue(Eigenschaften.LEUCHTET, true)
                .setValue(Eigenschaften.LICHTFARBE, Eigenschaften.Lichtfarbe.WARM));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(Eigenschaften.LEUCHTET, Eigenschaften.LICHTFARBE);
    }

    @Override
    protected VoxelShape getShape(BlockState zustand, BlockGetter welt, BlockPos pos, CollisionContext kontext) {
        return FORM;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState zustand, Level welt, BlockPos pos, Player spieler, BlockHitResult treffer) {
        if (!welt.isClientSide()) {
            welt.setBlock(pos, zustand.cycle(Eigenschaften.LEUCHTET), 3);
            welt.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.5F, 1.6F);
        }
        return InteractionResult.SUCCESS;
    }
}
