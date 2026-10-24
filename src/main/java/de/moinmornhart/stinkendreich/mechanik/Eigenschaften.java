package de.moinmornhart.stinkendreich.mechanik;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
//? if <1.21.2
/*import net.minecraft.world.level.block.state.properties.DirectionProperty;*/
//? if >=1.21.2
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Block-Eigenschaften der Mechanismen. Der Typ der Richtungs-Eigenschaft hat sich in 1.21.2 geändert
 * (DirectionProperty → EnumProperty&lt;Direction&gt;); hier steht er an genau einer Stelle.
 */
public final class Eigenschaften {
    //? if >=1.21.2 {
    /** Waagerechte Blickrichtung der Vorderseite. */
    public static final EnumProperty<Direction> RICHTUNG = BlockStateProperties.HORIZONTAL_FACING;
    /** Blickrichtung in alle sechs Richtungen (z. B. Laser). */
    public static final EnumProperty<Direction> RICHTUNG_ALLE = BlockStateProperties.FACING;
    //?} else {
    /*public static final DirectionProperty RICHTUNG = BlockStateProperties.HORIZONTAL_FACING;
    public static final DirectionProperty RICHTUNG_ALLE = BlockStateProperties.FACING;
    *///?}
    public static final BooleanProperty AN = BlockStateProperties.POWERED;
    public static final BooleanProperty OFFEN = BlockStateProperties.OPEN;
    public static final BooleanProperty LEUCHTET = BlockStateProperties.LIT;

    private Eigenschaften() {
    }

    /** Für Code, der nur prüfen will, ob ein Wert waagerecht ist. */
    public static boolean waagerecht(Direction d) {
        return d.getAxis().isHorizontal();
    }
}
