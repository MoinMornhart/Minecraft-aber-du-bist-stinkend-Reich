package de.moinmornhart.stinkendreich.moebel;

import de.moinmornhart.stinkendreich.mechanik.DekoBlock;
import de.moinmornhart.stinkendreich.mechanik.Eigenschaften;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
//? if >=1.21.2 {
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
//?} else
/*import net.minecraft.world.level.LevelAccessor;*/

import java.util.List;

/**
 * Deko-Möbel, das sich mit gleichen Nachbarn (gleiche Art, gleiche Richtung) verbindet: Sofa, Bettende,
 * Bettbank. Die Spalte (links, Mitte, rechts, einzeln) wählt das passende Modell mit oder ohne Seitenteil.
 */
public class VerbundDekoBlock extends DekoBlock {
    public VerbundDekoBlock(Properties eigenschaften, List<double[]> kaesten, boolean fest, double sitz) {
        super(eigenschaften, kaesten, fest, sitz);
        registerDefaultState(defaultBlockState().setValue(Eigenschaften.SPALTE, Eigenschaften.Spalte.EINZELN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(Eigenschaften.SPALTE);
    }

    private BlockState verbunden(BlockState zustand, BlockGetter welt, BlockPos pos) {
        Direction blick = zustand.getValue(Eigenschaften.RICHTUNG);
        return zustand.setValue(Eigenschaften.SPALTE, Verbinden.spalte(welt, pos, blick,
                n -> n.is(this) && n.getValue(Eigenschaften.RICHTUNG) == blick));
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
