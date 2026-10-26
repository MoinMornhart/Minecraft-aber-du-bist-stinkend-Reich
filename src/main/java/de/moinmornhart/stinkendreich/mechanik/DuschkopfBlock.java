package de.moinmornhart.stinkendreich.mechanik;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
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
 * Regen-Duschkopf: große, flache, quadratische Brause aus Chrom mit Goldrand. Hängt am Deckenrohr ({@code wand=false})
 * oder am Wandrohr ({@code wand=true}, Rohr kommt aus der Wand hinter der Rückseite). Rechtsklick dreht das Wasser
 * auf oder ab (Zustand {@code an}); was dann passiert, steht in {@link Dusche}.
 */
public class DuschkopfBlock extends MechanikBlock {
    /** Umrisse je Richtung (Index = 2D-Wert), getrennt für Decken- und Wandrohr. */
    private static final VoxelShape[] DECKE = new VoxelShape[4];
    private static final VoxelShape[] WAND = new VoxelShape[4];

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            VoxelShape platte = Block.box(1, 9.5, 1, 15, 11.5, 15);
            DECKE[d.get2DDataValue()] = Shapes.or(platte, Block.box(6.5, 11.5, 6.5, 9.5, 16, 9.5));
            WAND[d.get2DDataValue()] = Shapes.or(platte, gedreht(6.5, 11.5, 6.5, 9.5, 15, 16, d));
        }
    }

    public DuschkopfBlock(Properties eigenschaften) {
        super(eigenschaften, Ausrichtung.WAAGERECHT, false, false);
    }

    @Override
    protected Property<?>[] grundEigenschaften() {
        return new Property<?>[]{Eigenschaften.RICHTUNG};
    }

    @Override
    protected void eigeneEigenschaften(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(Eigenschaften.LAEUFT, Eigenschaften.WANDROHR);
    }

    @Override
    protected BlockState standardZustand(BlockState s) {
        return s.setValue(Eigenschaften.LAEUFT, false).setValue(Eigenschaften.WANDROHR, false);
    }

    /** An eine Wand gesetzt: Wandrohr, Vorderseite von der Wand weg. Sonst Deckenrohr. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext kontext) {
        BlockState s = super.getStateForPlacement(kontext);
        return s.setValue(Eigenschaften.WANDROHR, kontext.getClickedFace().getAxis().isHorizontal());
    }

    @Override
    public int takt() {
        return Dusche.TAKT;
    }

    @Override
    public void takt(ServerLevel welt, BlockPos pos, BlockState zustand, MechanikBlockEntity daten) {
        Dusche.takt(welt, pos, zustand, daten);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState zustand, Level welt, BlockPos pos, Player spieler, BlockHitResult treffer) {
        if (welt instanceof ServerLevel server) {
            server.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4F, 1.2F);
            Dusche.schalten(server, pos, !zustand.getValue(Eigenschaften.LAEUFT));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected VoxelShape getShape(BlockState zustand, BlockGetter welt, BlockPos pos, CollisionContext kontext) {
        int i = zustand.getValue(Eigenschaften.RICHTUNG).get2DDataValue();
        return zustand.getValue(Eigenschaften.WANDROHR) ? WAND[i] : DECKE[i];
    }

    /**
     * Kasten in Pixeln mit Vorderseite nach Norden, gedreht in die Richtung {@code d} (wie die Blockzustände:
     * Osten = 90° im Uhrzeigersinn von oben).
     */
    static VoxelShape gedreht(double x0, double y0, double z0, double x1, double y1, double z1, Direction d) {
        return switch (d) {
            case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            default -> Block.box(x0, y0, z0, x1, y1, z1);
        };
    }
}
