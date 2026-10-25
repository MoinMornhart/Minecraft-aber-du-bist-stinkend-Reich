package de.moinmornhart.stinkendreich.klavier;

import de.moinmornhart.stinkendreich.mechanik.DekoBlock;
import de.moinmornhart.stinkendreich.mechanik.KlientBruecke;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * Konzertflügel (3 × 3 Blöcke): der mittlere Block „konzertfluegel“ trägt das große Modell, die acht unsichtbaren
 * Teile „fluegel_teil“ drumherum geben ihm Festigkeit. Rechtsklick auf irgendeinen Teil öffnet das Spielfenster.
 */
public class KlavierBlock extends DekoBlock {
    public KlavierBlock(Properties eigenschaften, List<double[]> kaesten, boolean fest, double sitz) {
        super(eigenschaften, kaesten, fest, sitz);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState zustand, Level welt, BlockPos pos, Player spieler, BlockHitResult treffer) {
        if (welt.isClientSide()) {
            KlientBruecke.klavier.accept(Klavier.mitte(welt, pos));
        }
        return InteractionResult.SUCCESS;
    }
}
