package de.moinmornhart.stinkendreich.mechanik;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Der sichtbare Laserstrahl. Hat keine Kollision und kann nicht angeklickt werden.
 * Prüft regelmäßig, ob noch ein eingeschalteter Sender auf ihn zielt, sonst verschwindet er.
 */
public class LaserstrahlBlock extends Block {
    public static final EnumProperty<Direction.Axis> ACHSE = BlockStateProperties.AXIS;

    public LaserstrahlBlock(Properties eigenschaften) {
        super(eigenschaften);
        registerDefaultState(stateDefinition.any().setValue(ACHSE, Direction.Axis.X));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACHSE);
    }

    @Override
    protected VoxelShape getShape(BlockState zustand, BlockGetter welt, BlockPos pos, CollisionContext kontext) {
        return Shapes.empty();
    }

    @Override
    protected void onPlace(BlockState zustand, Level welt, BlockPos pos, BlockState alt, boolean verschoben) {
        welt.scheduleTick(pos, this, 40);
    }

    @Override
    protected void tick(BlockState zustand, ServerLevel welt, BlockPos pos, RandomSource zufall) {
        if (senderVorhanden(welt, pos, zustand.getValue(ACHSE))) {
            welt.scheduleTick(pos, this, 40);
        } else {
            welt.removeBlock(pos, false);
        }
    }

    private static boolean senderVorhanden(ServerLevel welt, BlockPos pos, Direction.Axis achse) {
        for (Direction d : new Direction[]{Direction.fromAxisAndDirection(achse, Direction.AxisDirection.POSITIVE),
                Direction.fromAxisAndDirection(achse, Direction.AxisDirection.NEGATIVE)}) {
            BlockPos.MutableBlockPos p = pos.mutable();
            for (int i = 1; i <= LaserSenderBlock.MAX_REICHWEITE; i++) {
                p.move(d);
                BlockState s = welt.getBlockState(p);
                if (s.getBlock() instanceof LaserSenderBlock) {
                    return s.getValue(Eigenschaften.RICHTUNG_ALLE) == d.getOpposite();
                }
                if (!(s.getBlock() instanceof LaserstrahlBlock)) {
                    break;
                }
            }
        }
        return false;
    }
}
