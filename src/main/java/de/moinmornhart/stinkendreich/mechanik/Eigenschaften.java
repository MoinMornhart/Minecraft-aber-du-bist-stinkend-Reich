package de.moinmornhart.stinkendreich.mechanik;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
//? if <1.21.2
/*import net.minecraft.world.level.block.state.properties.DirectionProperty;*/
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

    // ---------- Türflügel: Lage im Ganzen, Ausrichtung, Öffnungsstufe ----------

    /** Spalte eines Feldes in einer Tür aus mehreren Blöcken (für Rahmen, Griffe, durchgehende Bilder). */
    public enum Spalte implements StringRepresentable {
        LINKS, MITTE, RECHTS, EINZELN;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Zeile eines Feldes in einer Tür aus mehreren Blöcken. */
    public enum Zeile implements StringRepresentable {
        UNTEN, MITTE, OBEN, EINZELN;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Bildschirm auf dem Tisch: gerade oder schräg zur Mitte (links/rechts vom Betrachter aus); WAND = in die Wand eingelassen. */
    public enum Winkel implements StringRepresentable {
        GERADE, LINKS, RECHTS, WAND;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Farbe der Smart-Home-Lampen (siehe {@link SmartHome}). */
    public enum Lichtfarbe implements StringRepresentable {
        WEISS, WARM, ROT, GRUEN, BLAU, LILA, PINK, GOLD;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        /** Farbe zu einem Namen ("rot" …), sonst null. */
        public static Lichtfarbe von(String name) {
            for (Lichtfarbe f : values()) {
                if (f.getSerializedName().equals(name)) {
                    return f;
                }
            }
            return null;
        }
    }

    public static final EnumProperty<Lichtfarbe> LICHTFARBE = EnumProperty.create("farbe", Lichtfarbe.class);
    public static final EnumProperty<Winkel> WINKEL = EnumProperty.create("winkel", Winkel.class);
    public static final EnumProperty<Spalte> SPALTE = EnumProperty.create("spalte", Spalte.class);
    public static final EnumProperty<Zeile> ZEILE = EnumProperty.create("zeile", Zeile.class);
    /** Öffnungsstufe für die Animation: 0 zu, 3 ganz offen. */
    public static final IntegerProperty STUFE = IntegerProperty.create("stufe", 0, 3);
    /** In welcher Achse die Türfläche liegt (x: Fläche läuft von West nach Ost). */
    public static final EnumProperty<Direction.Axis> ACHSE = BlockStateProperties.HORIZONTAL_AXIS;

    private Eigenschaften() {
    }

    /** Für Code, der nur prüfen will, ob ein Wert waagerecht ist. */
    public static boolean waagerecht(Direction d) {
        return d.getAxis().isHorizontal();
    }
}
