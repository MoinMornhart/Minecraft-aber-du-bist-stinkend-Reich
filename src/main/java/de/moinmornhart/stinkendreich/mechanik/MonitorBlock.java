package de.moinmornhart.stinkendreich.mechanik;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Überwachungsmonitor: flacher Bildschirm auf einem Standfuß, wahlweise schräg zur Mitte gedreht
 * ({@link Eigenschaften#WINKEL}). Rechtsklick schaltet den Blick auf die Kameras in der Nähe.
 */
public class MonitorBlock extends MechanikBlock {
    private static final VoxelShape FORM_NS = Shapes.or(Block.box(1, 3, 6, 15, 13, 10), Block.box(4, 0, 4, 12, 3, 12));
    private static final VoxelShape FORM_OW = Shapes.or(Block.box(6, 3, 1, 10, 13, 15), Block.box(4, 0, 4, 12, 3, 12));

    public MonitorBlock(Properties eigenschaften) {
        super(eigenschaften, Ausrichtung.WAAGERECHT, false, false);
    }

    @Override
    protected Property<?>[] grundEigenschaften() {
        return new Property<?>[]{Eigenschaften.RICHTUNG};
    }

    @Override
    protected void eigeneEigenschaften(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(Eigenschaften.WINKEL);
    }

    @Override
    protected BlockState standardZustand(BlockState s) {
        return s.setValue(Eigenschaften.WINKEL, Eigenschaften.Winkel.GERADE);
    }

    @Override
    protected VoxelShape getShape(BlockState zustand, BlockGetter welt, BlockPos pos, CollisionContext kontext) {
        if (zustand.getValue(Eigenschaften.WINKEL) == Eigenschaften.Winkel.WAND) {
            return Shapes.block();
        }
        return zustand.getValue(Eigenschaften.RICHTUNG).getAxis() == net.minecraft.core.Direction.Axis.Z ? FORM_NS : FORM_OW;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState zustand, Level welt, BlockPos pos, Player spieler, BlockHitResult treffer) {
        if (welt.isClientSide()) {
            KlientBruecke.monitor.accept(pos);
        } else {
            de.moinmornhart.stinkendreich.netz.OhneMod.zeigen(spieler, "monitor", pos);
        }
        return InteractionResult.SUCCESS;
    }
}
