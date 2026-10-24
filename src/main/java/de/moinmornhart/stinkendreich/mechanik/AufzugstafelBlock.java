package de.moinmornhart.stinkendreich.mechanik;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Bedientafel im Aufzug. Alle Tafeln, die genau übereinander im selben Schacht hängen, sind die
 * Haltestellen. Rechtsklick öffnet die Etagenwahl; die Fahrt übernimmt {@link Aufzug}.
 * Funktioniert für jeden Spieler.
 */
public class AufzugstafelBlock extends MechanikBlock {
    public static final int SCHACHT_HOEHE = 96;

    public AufzugstafelBlock(Properties eigenschaften) {
        super(eigenschaften, Ausrichtung.WAAGERECHT, true, false);
    }

    @Override
    protected Property<?>[] grundEigenschaften() {
        return new Property<?>[]{Eigenschaften.RICHTUNG};
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState zustand, Level welt, BlockPos pos, Player spieler, BlockHitResult treffer) {
        if (welt.isClientSide()) {
            KlientBruecke.aufzug.accept(pos);
        }
        return InteractionResult.SUCCESS;
    }

    /** Alle Haltestellen im selben Schacht, von unten nach oben. */
    public static List<BlockPos> haltestellen(BlockGetter welt, BlockPos tafel) {
        List<BlockPos> liste = new ArrayList<>();
        for (int dy = -SCHACHT_HOEHE; dy <= SCHACHT_HOEHE; dy++) {
            BlockPos p = tafel.offset(0, dy, 0);
            if (welt.getBlockState(p).getBlock() instanceof AufzugstafelBlock) {
                liste.add(p);
            }
        }
        return liste;
    }

    /** Anzeigename der Haltestelle (vom Bauplan gesetzt, sonst Höhe). */
    public static String name(BlockGetter welt, BlockPos tafel) {
        MechanikBlockEntity d = daten(welt, tafel);
        String name = d == null ? "" : d.text("name", "");
        return name.isEmpty() ? "Y " + tafel.getY() : name;
    }
}
