package de.moinmornhart.stinkendreich.mechanik;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ein Feld eines beweglichen Abschlusses: Tor, Garagentor, Rollladen, Schiebetür, Panzertür, Tresortür,
 * Geheimregal, Poolabdeckung oder Gitterfalle. Gesteuert wird es von {@link TorsteuerungBlock},
 * {@link GeheimschalterBlock} oder {@link FallenplatteBlock}, die nur {@link Eigenschaften#OFFEN} setzen.
 * <p>
 * Jedes Feld weiß, wo es im Ganzen sitzt ({@link Eigenschaften#SPALTE}, {@link Eigenschaften#ZEILE}); danach
 * wählt das Modell Rahmen, Griffe und Muster, damit eine Tür aus vielen Blöcken wie ein Stück aussieht.
 * Ändert sich OFFEN, fährt das Feld in Stufen auf oder zu ({@link Eigenschaften#STUFE}: 0 zu, 3 offen).
 * Die Modelle der Zwischenstufen zeigen es verschoben, je nach Typ zur Seite, nach oben oder nach unten
 * (siehe tools/assets/tueren.py). Ab Stufe 1 bleibt es fest, bis es ganz offen ist.
 */
public class TorfluegelBlock extends Block {
    /** Grundform eines Feldes. */
    public enum Form {
        /** Türblatt, 4 Pixel dick, mittig im Block. */
        DUENN,
        /** Platte am Boden, 2 Pixel hoch (Poolabdeckung). */
        FLACH,
        /** Ganzer Block (Geheimregal). */
        VOLL
    }

    /** Ticks pro Öffnungsstufe: drei Stufen ergeben gut eine Drittelsekunde Bewegung. */
    private static final int SCHRITT_TICKS = 2;
    private static final int GANZ_OFFEN = 3;
    private static final VoxelShape DUENN_X = Block.box(0, 0, 6, 16, 16, 10);
    private static final VoxelShape DUENN_Z = Block.box(6, 0, 0, 10, 16, 16);
    private static final VoxelShape FLACH = Block.box(0, 0, 0, 16, 2, 16);

    private final Form form;

    /**
     * @param offenAmAnfang ob der Abschluss beim Setzen von Hand offen ist
     * @param form          Grundform der Felder
     */
    public TorfluegelBlock(Properties eigenschaften, boolean offenAmAnfang, Form form) {
        super(eigenschaften);
        this.form = form;
        registerDefaultState(stateDefinition.any()
                .setValue(Eigenschaften.OFFEN, offenAmAnfang)
                .setValue(Eigenschaften.STUFE, offenAmAnfang ? GANZ_OFFEN : 0)
                .setValue(Eigenschaften.ACHSE, Direction.Axis.X)
                .setValue(Eigenschaften.SPALTE, Eigenschaften.Spalte.EINZELN)
                .setValue(Eigenschaften.ZEILE, Eigenschaften.Zeile.EINZELN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(Eigenschaften.OFFEN, Eigenschaften.STUFE, Eigenschaften.ACHSE, Eigenschaften.SPALTE, Eigenschaften.ZEILE);
    }

    /** Von Hand gesetzt: Die Fläche steht quer zur Blickrichtung des Spielers. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext kontext) {
        return defaultBlockState().setValue(Eigenschaften.ACHSE, kontext.getHorizontalDirection().getClockWise().getAxis());
    }

    /** Ganz offen (nicht nur „soll offen sein“), dann ist das Feld leer. */
    public static boolean ganzOffen(BlockState zustand) {
        return zustand.getValue(Eigenschaften.STUFE) == GANZ_OFFEN;
    }

    private VoxelShape form(BlockState zustand) {
        if (ganzOffen(zustand)) {
            return Shapes.empty();
        }
        return switch (form) {
            case FLACH -> FLACH;
            case VOLL -> Shapes.block();
            case DUENN -> zustand.getValue(Eigenschaften.ACHSE) == Direction.Axis.X ? DUENN_X : DUENN_Z;
        };
    }

    @Override
    protected VoxelShape getShape(BlockState zustand, BlockGetter welt, BlockPos pos, CollisionContext kontext) {
        return form(zustand);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState zustand, BlockGetter welt, BlockPos pos, CollisionContext kontext) {
        return form(zustand);
    }

    /** Wege (Butler, Tiere) führen nur durch ganz offene Felder; dünne Türblätter gelten sonst als Hindernis. */
    @Override
    protected boolean isPathfindable(BlockState zustand, PathComputationType art) {
        return ganzOffen(zustand);
    }

    /** Jede Änderung von OFFEN (auch per /setblock) startet die Bewegung. */
    @Override
    protected void onPlace(BlockState zustand, Level welt, BlockPos pos, BlockState alt, boolean verschoben) {
        if (!welt.isClientSide() && zustand.getValue(Eigenschaften.STUFE) != ziel(zustand)) {
            welt.scheduleTick(pos, this, SCHRITT_TICKS);
        }
    }

    @Override
    protected void tick(BlockState zustand, ServerLevel welt, BlockPos pos, RandomSource zufall) {
        int stufe = zustand.getValue(Eigenschaften.STUFE);
        int ziel = ziel(zustand);
        if (stufe == ziel) {
            return;
        }
        stufe += stufe < ziel ? 1 : -1;
        // Nur an die Klienten senden: Nachbarn müssen von der Bewegung nichts wissen
        welt.setBlock(pos, zustand.setValue(Eigenschaften.STUFE, stufe), Block.UPDATE_CLIENTS);
        if (stufe != ziel) {
            welt.scheduleTick(pos, this, SCHRITT_TICKS);
        }
    }

    private static int ziel(BlockState zustand) {
        return zustand.getValue(Eigenschaften.OFFEN) ? GANZ_OFFEN : 0;
    }

    /** Setzt den gewünschten Zustand eines Feldes; die Bewegung läuft dann von selbst. */
    public static boolean setzeOffen(Level welt, BlockPos pos, BlockState zustand, boolean offen) {
        if (zustand.getValue(Eigenschaften.OFFEN) == offen) {
            return false;
        }
        welt.setBlock(pos, zustand.setValue(Eigenschaften.OFFEN, offen), Block.UPDATE_ALL);
        return true;
    }
}
