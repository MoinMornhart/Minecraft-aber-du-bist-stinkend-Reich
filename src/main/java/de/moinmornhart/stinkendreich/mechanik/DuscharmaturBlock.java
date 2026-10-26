package de.moinmornhart.stinkendreich.mechanik;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Duscharmatur an der Wand: verchromte Rosette mit goldenem Drehgriff. Rechtsklick dreht den nächsten Duschkopf im
 * Umkreis von {@link Dusche#ARMATUR_REICHWEITE} Blöcken auf oder ab; der Griff steht dann schräg (Zustand {@code an}).
 */
public class DuscharmaturBlock extends MechanikBlock {
    private static final VoxelShape[] FORMEN = new VoxelShape[4];

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            FORMEN[d.get2DDataValue()] = DuschkopfBlock.gedreht(4, 4, 12, 12, 12, 16, d);
        }
    }

    public DuscharmaturBlock(Properties eigenschaften) {
        super(eigenschaften, Ausrichtung.WAAGERECHT, false, false);
    }

    @Override
    protected Property<?>[] grundEigenschaften() {
        return new Property<?>[]{Eigenschaften.RICHTUNG};
    }

    @Override
    protected void eigeneEigenschaften(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(Eigenschaften.LAEUFT);
    }

    @Override
    protected BlockState standardZustand(BlockState s) {
        return s.setValue(Eigenschaften.LAEUFT, false);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState zustand, Level welt, BlockPos pos, Player spieler, BlockHitResult treffer) {
        if (welt instanceof ServerLevel server) {
            server.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5F, 1.0F);
            BlockPos kopf = Dusche.naechsterKopf(server, pos);
            if (kopf == null) {
                de.moinmornhart.stinkendreich.Kompat.nachricht(spieler, Component.translatable("stinkendreich.dusche.kein_kopf"), true);
                return InteractionResult.SUCCESS;
            }
            Dusche.schalten(server, kopf, !server.getBlockState(kopf).getValue(Eigenschaften.LAEUFT));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected VoxelShape getShape(BlockState zustand, BlockGetter welt, BlockPos pos, CollisionContext kontext) {
        return FORMEN[zustand.getValue(Eigenschaften.RICHTUNG).get2DDataValue()];
    }
}
