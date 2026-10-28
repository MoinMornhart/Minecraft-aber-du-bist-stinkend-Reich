package de.moinmornhart.stinkendreich.fernseher;

import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.mechanik.DekoBlock;
import de.moinmornhart.stinkendreich.mechanik.Eigenschaften;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * Abgelegte Fernbedienung (z. B. auf dem Nachttisch): Rechtsklick nimmt sie in die Hand – auch im Sitzen, ohne
 * aufzustehen (Schleichen hieße aussteigen). In der Hand steuert sie dann den Fernseher ({@link FernbedienungItem}).
 * Sie legt sich auf die Oberkante des Möbels darunter („tiefe“ = so viele Pixel tiefer), statt darüber zu schweben.
 */
public class FernbedienungBlock extends DekoBlock {
    public static final IntegerProperty TIEFE = IntegerProperty.create("tiefe", 0, 8);

    public FernbedienungBlock(Properties eigenschaften, List<double[]> kaesten, boolean fest, double sitz) {
        super(eigenschaften, kaesten, fest, sitz);
        registerDefaultState(defaultBlockState().setValue(TIEFE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(TIEFE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext kontext) {
        BlockState zustand = super.getStateForPlacement(kontext);
        BlockPos unten = kontext.getClickedPos().below();
        VoxelShape form = kontext.getLevel().getBlockState(unten).getShape(kontext.getLevel(), unten);
        double oben = form.isEmpty() ? 1.0 : form.max(Direction.Axis.Y);
        int tiefe = (int) Math.round((1.0 - Math.min(1.0, oben)) * 16);
        return zustand == null ? null : zustand.setValue(TIEFE, Math.max(0, Math.min(8, tiefe)));
    }

    @Override
    protected VoxelShape getShape(BlockState zustand, BlockGetter welt, BlockPos pos, CollisionContext kontext) {
        boolean laengs = zustand.getValue(Eigenschaften.RICHTUNG).getAxis() == Direction.Axis.Z;
        double t = zustand.getValue(TIEFE);
        return laengs ? Block.box(6, -t, 3, 10, 1 - t, 13) : Block.box(3, -t, 6, 13, 1 - t, 10);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState zustand, Level welt, BlockPos pos, Player spieler, BlockHitResult treffer) {
        if (!welt.isClientSide()) {
            welt.removeBlock(pos, false);
            ItemStack stapel = new ItemStack(asItem());
            // Bevorzugt in die leere Hand, sonst ins Inventar, notfalls vor die Füße
            if (spieler.getMainHandItem().isEmpty()) {
                spieler.setItemInHand(InteractionHand.MAIN_HAND, stapel);
            } else if (!spieler.getInventory().add(stapel)) {
                welt.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(welt, spieler.getX(), spieler.getY(), spieler.getZ(), stapel));
            }
            welt.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.4F);
            Kompat.nachricht(spieler, Component.translatable("stinkendreich.fernbedienung.genommen"), true);
        }
        return InteractionResult.SUCCESS;
    }
}
