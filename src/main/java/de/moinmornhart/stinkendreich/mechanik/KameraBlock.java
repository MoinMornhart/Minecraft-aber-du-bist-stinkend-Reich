package de.moinmornhart.stinkendreich.mechanik;

import net.minecraft.world.level.block.state.properties.Property;

/**
 * Überwachungskamera an der Wand. Der Monitor im Überwachungsraum zeigt ihren Blick.
 * Die Block-Entity dient dem Client nur dazu, alle Kameras in der Nähe schnell zu finden.
 */
public class KameraBlock extends MechanikBlock {
    public KameraBlock(Properties eigenschaften) {
        super(eigenschaften, Ausrichtung.WAAGERECHT, true, false);
    }

    @Override
    protected Property<?>[] grundEigenschaften() {
        return new Property<?>[]{Eigenschaften.RICHTUNG};
    }
}
