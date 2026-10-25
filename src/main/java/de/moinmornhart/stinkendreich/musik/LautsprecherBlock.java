package de.moinmornhart.stinkendreich.musik;

import de.moinmornhart.stinkendreich.mechanik.Eigenschaften;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * Lautsprecher: Deko und zusätzliche Schallquelle. Jeder Lautsprecher im Umkreis
 * {@code lautsprecherRadius} um eine Musikanlage spielt deren Musik mit; die Lautstärke richtet
 * sich nach dem Abstand zur nächsten Box oder zum Pult. Die Membran zeigt zum Spieler, und
 * solange Musik läuft, fliegen Noten heraus (siehe MusikKlient).
 */
public class LautsprecherBlock extends Block {
    public LautsprecherBlock(Properties eigenschaften) {
        super(eigenschaften);
        registerDefaultState(stateDefinition.any().setValue(Eigenschaften.RICHTUNG, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(Eigenschaften.RICHTUNG);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext kontext) {
        return defaultBlockState().setValue(Eigenschaften.RICHTUNG, kontext.getHorizontalDirection().getOpposite());
    }
}
