package de.moinmornhart.stinkendreich.mechanik;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ein Feld eines beweglichen Abschlusses: Tor, Garagentor, Rollladen, Schiebetür, Geheimregal,
 * Poolabdeckung, Panzertür oder Gitterfalle. Offen ist es unsichtbar und begehbar, geschlossen fest.
 * Gesteuert wird es von {@link TorsteuerungBlock}, {@link GeheimschalterBlock} oder {@link FallenplatteBlock}.
 */
public class TorfluegelBlock extends Block {
    private final VoxelShape formZu;

    /**
     * @param offenAmAnfang ob der Abschluss beim Setzen offen ist
     * @param flach         nur 2 Pixel hoch (Poolabdeckung) statt ganzer Block
     */
    public TorfluegelBlock(Properties eigenschaften, boolean offenAmAnfang, boolean flach) {
        super(eigenschaften);
        this.formZu = flach ? Block.box(0, 0, 0, 16, 2, 16) : Shapes.block();
        registerDefaultState(stateDefinition.any().setValue(Eigenschaften.OFFEN, offenAmAnfang));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(Eigenschaften.OFFEN);
    }

    @Override
    protected VoxelShape getShape(BlockState zustand, BlockGetter welt, BlockPos pos, CollisionContext kontext) {
        return zustand.getValue(Eigenschaften.OFFEN) ? Shapes.empty() : formZu;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState zustand, BlockGetter welt, BlockPos pos, CollisionContext kontext) {
        return zustand.getValue(Eigenschaften.OFFEN) ? Shapes.empty() : formZu;
    }
}
