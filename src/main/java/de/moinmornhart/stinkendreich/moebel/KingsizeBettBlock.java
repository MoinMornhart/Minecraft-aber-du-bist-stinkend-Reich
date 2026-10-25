package de.moinmornhart.stinkendreich.moebel;

import de.moinmornhart.stinkendreich.mechanik.Eigenschaften;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
//? if <26.2
/*import net.minecraft.world.level.block.entity.BlockEntity;*/
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
//? if >=1.21.2 {
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
//?} else
/*import net.minecraft.world.level.LevelAccessor;*/

/**
 * XXL-Kingsize-Bett: ein echtes Bett (schlafen, Spawnpunkt), aber mit eigenem Modell. Mehrere nebeneinander
 * verbinden sich zu einer durchgehenden Liegefläche mit einer Decke, Kanten nur außen (siehe {@link Verbinden}).
 * Im Penthouse liegen vier davon nebeneinander, dazu ein Bettende und eine Bettbank aus Deko-Blöcken.
 */
public class KingsizeBettBlock extends BedBlock {
    private static final VoxelShape FORM = Block.box(0, 0, 0, 16, 11, 16);

    public KingsizeBettBlock(Properties eigenschaften) {
        super(DyeColor.RED, eigenschaften);
        registerDefaultState(defaultBlockState().setValue(Eigenschaften.SPALTE, Eigenschaften.Spalte.EINZELN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(Eigenschaften.SPALTE);
    }

    /** Gezeichnet wird das Blockmodell, nicht das Vanilla-Bett. */
    @Override
    protected RenderShape getRenderShape(BlockState zustand) {
        return RenderShape.MODEL;
    }

    /** Kein Block-Entity: die Farbe des Vanilla-Betts brauchen wir nicht (ab 26.2 hat das Bett keins mehr). */
    //? if <26.2 {
    /*@Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState zustand) {
        return null;
    }
    *///?}

    @Override
    protected VoxelShape getShape(BlockState zustand, BlockGetter welt, BlockPos pos, CollisionContext kontext) {
        return FORM;
    }

    private BlockState verbunden(BlockState zustand, BlockGetter welt, BlockPos pos) {
        if (!zustand.is(this)) {
            return zustand;
        }
        Direction blick = zustand.getValue(FACING);
        BedPart teil = zustand.getValue(PART);
        return zustand.setValue(Eigenschaften.SPALTE, Verbinden.spalte(welt, pos, blick,
                n -> n.is(this) && n.getValue(FACING) == blick && n.getValue(PART) == teil));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext kontext) {
        BlockState zustand = super.getStateForPlacement(kontext);
        return zustand == null ? null : verbunden(zustand, kontext.getLevel(), kontext.getClickedPos());
    }

    //? if >=1.21.2 {
    @Override
    protected BlockState updateShape(BlockState zustand, LevelReader welt, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction richtung, BlockPos nachbarPos, BlockState nachbar, RandomSource zufall) {
        return verbunden(super.updateShape(zustand, welt, ticks, pos, richtung, nachbarPos, nachbar, zufall), welt, pos);
    }
    //?} else {
    /*@Override
    protected BlockState updateShape(BlockState zustand, Direction richtung, BlockState nachbar, LevelAccessor welt,
                                     BlockPos pos, BlockPos nachbarPos) {
        return verbunden(super.updateShape(zustand, richtung, nachbar, welt, pos, nachbarPos), welt, pos);
    }
    *///?}
}
