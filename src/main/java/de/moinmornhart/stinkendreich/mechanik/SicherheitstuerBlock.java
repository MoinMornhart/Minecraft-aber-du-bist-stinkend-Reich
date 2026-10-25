package de.moinmornhart.stinkendreich.mechanik;

import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.register.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Sicherheits- und Banktür: geht nur mit Zugangskarte am Kartenleser daneben auf (bei Stufe 0 auch ohne) und
 * bleibt dann offen stehen, bis jemand sie per Rechtsklick wieder schließt – etwa von innen, wenn man im Büro
 * oder im Kontrollraum ist. Von Hand öffnen lässt sie sich nicht. Der Kartenleser öffnet sie direkt (ohne
 * Redstone-Impuls, der sie beim Abklingen wieder zufallen ließe), siehe {@link #oeffnenBei}.
 */
public class SicherheitstuerBlock extends ZiertuerBlock {
    public SicherheitstuerBlock(BlockSetType art, Properties eigenschaften) {
        super(art, eigenschaften);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState zustand, Level welt, BlockPos pos, Player spieler, BlockHitResult treffer) {
        if (welt.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (isOpen(zustand)) {
            // Schließen, bei einer Doppeltür beide Flügel
            BlockPos unten = zustand.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
            setOpen(spieler, welt, welt.getBlockState(unten), unten, false);
            for (net.minecraft.core.Direction r : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockPos n = unten.relative(r);
                if (untereHaelfte(welt, n)) {
                    BlockState s = welt.getBlockState(n);
                    ((SicherheitstuerBlock) s.getBlock()).setOpen(spieler, welt, s, n, false);
                }
            }
        } else {
            welt.playSound(null, pos, ModSounds.PIEP_FEHLER, SoundSource.BLOCKS, 0.8F, 1.0F);
            Kompat.nachricht(spieler, Component.translatable("stinkendreich.sicherheitstuer.karte").withStyle(ChatFormatting.YELLOW), true);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Öffnet die Sicherheits- oder Banktür direkt neben dem Kartenleser (ein Block Abstand in jede Richtung, auch
     * schräg), bei einer Doppeltür auch den zweiten Flügel. Gibt zurück, ob dort eine solche Tür war.
     */
    public static boolean oeffnenBei(Level welt, BlockPos leser, Entity wer) {
        java.util.Set<BlockPos> tueren = new java.util.HashSet<>();
        for (BlockPos p : BlockPos.betweenClosed(leser.offset(-1, -1, -1), leser.offset(1, 1, 1))) {
            if (untereHaelfte(welt, p)) {
                tueren.add(p.immutable());
            }
        }
        // Zweiter Flügel einer Doppeltür gleich daneben
        for (BlockPos p : java.util.List.copyOf(tueren)) {
            for (net.minecraft.core.Direction r : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                if (untereHaelfte(welt, p.relative(r))) {
                    tueren.add(p.relative(r));
                }
            }
        }
        for (BlockPos p : tueren) {
            BlockState s = welt.getBlockState(p);
            ((SicherheitstuerBlock) s.getBlock()).setOpen(wer, welt, s, p, true);
        }
        return !tueren.isEmpty();
    }

    private static boolean untereHaelfte(Level welt, BlockPos p) {
        BlockState s = welt.getBlockState(p);
        return s.getBlock() instanceof SicherheitstuerBlock && s.getValue(HALF) == DoubleBlockHalf.LOWER;
    }
}
