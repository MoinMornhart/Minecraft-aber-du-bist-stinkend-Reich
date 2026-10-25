package de.moinmornhart.stinkendreich.mechanik;

import de.moinmornhart.stinkendreich.moebel.SitzEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * Deko-Block der Villa (Möbel, Partyzubehör, Küchengeräte, Goldkram). Form, Licht und Klang kommen aus
 * deko_bloecke.json (erzeugt von tools/assets/deko.py, siehe {@link de.moinmornhart.stinkendreich.register.ModDeko}).
 * Jeder Deko-Block zeigt mit der Vorderseite zum Spieler, der ihn setzt, und kann im Wasser stehen
 * (z. B. der Flamingo im Pool). Sitzmöbel (Sofa, Liege, Bank) haben eine Sitzhöhe: Rechtsklick setzt sich drauf.
 */
public class DekoBlock extends Block implements SimpleWaterloggedBlock {
    public static final BooleanProperty NASS = BlockStateProperties.WATERLOGGED;

    /** Formen je Richtung (Index = 2D-Wert: Süden, Westen, Norden, Osten). */
    private final VoxelShape[] formen = new VoxelShape[4];
    private final boolean fest;
    /** Sitzhöhe in Pixeln, negativ = kein Sitzmöbel. */
    private final double sitz;

    /**
     * @param kaesten Kästen in Pixeln (x0, y0, z0, x1, y1, z1), Vorderseite nach Norden
     * @param fest    ob man dagegen läuft (Kronleuchter und Discokugel nicht)
     * @param sitz    Sitzhöhe in Pixeln oder -1
     */
    public DekoBlock(Properties eigenschaften, List<double[]> kaesten, boolean fest, double sitz) {
        super(eigenschaften);
        this.fest = fest;
        this.sitz = sitz;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            VoxelShape form = Shapes.empty();
            for (double[] k : kaesten) {
                form = Shapes.or(form, gedreht(k, d));
            }
            formen[d.get2DDataValue()] = form.isEmpty() ? Shapes.block() : form;
        }
        registerDefaultState(stateDefinition.any().setValue(Eigenschaften.RICHTUNG, Direction.NORTH).setValue(NASS, false));
    }

    /** Dreht einen Kasten von „Vorderseite Norden“ in die Richtung {@code d} (im Uhrzeigersinn von oben). */
    private static VoxelShape gedreht(double[] k, Direction d) {
        double x0 = k[0], z0 = k[2], x1 = k[3], z1 = k[5];
        for (int i = 0; i < (d.get2DDataValue() + 2) % 4; i++) {
            double nx0 = 16 - z1, nx1 = 16 - z0, nz0 = x0, nz1 = x1;
            x0 = nx0;
            x1 = nx1;
            z0 = nz0;
            z1 = nz1;
        }
        return Block.box(Math.max(0, x0), Math.max(0, k[1]), Math.max(0, z0), Math.min(16, x1), Math.min(16, k[4]), Math.min(16, z1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(Eigenschaften.RICHTUNG, NASS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext kontext) {
        boolean wasser = kontext.getLevel().getFluidState(kontext.getClickedPos()).getType() == Fluids.WATER;
        return defaultBlockState().setValue(Eigenschaften.RICHTUNG, kontext.getHorizontalDirection().getOpposite()).setValue(NASS, wasser);
    }

    /** Sitzmöbel: hinsetzen (mit Schleichen kann man stattdessen etwas daraufstellen). */
    @Override
    protected InteractionResult useWithoutItem(BlockState zustand, Level welt, BlockPos pos, Player spieler, BlockHitResult treffer) {
        if (sitz < 0 || spieler.isShiftKeyDown() || spieler.isPassenger()) {
            return InteractionResult.PASS;
        }
        if (welt instanceof ServerLevel server) {
            SitzEntity.hinsetzen(server, pos, sitz, zustand.getValue(Eigenschaften.RICHTUNG), spieler);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected FluidState getFluidState(BlockState zustand) {
        return zustand.getValue(NASS) ? Fluids.WATER.getSource(false) : super.getFluidState(zustand);
    }

    @Override
    protected BlockState rotate(BlockState zustand, Rotation drehung) {
        return zustand.setValue(Eigenschaften.RICHTUNG, drehung.rotate(zustand.getValue(Eigenschaften.RICHTUNG)));
    }

    @Override
    protected VoxelShape getShape(BlockState zustand, BlockGetter welt, BlockPos pos, CollisionContext kontext) {
        return formen[zustand.getValue(Eigenschaften.RICHTUNG).get2DDataValue()];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState zustand, BlockGetter welt, BlockPos pos, CollisionContext kontext) {
        return fest ? getShape(zustand, welt, pos, kontext) : Shapes.empty();
    }
}
