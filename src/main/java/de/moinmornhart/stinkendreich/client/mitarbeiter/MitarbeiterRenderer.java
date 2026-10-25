package de.moinmornhart.stinkendreich.client.mitarbeiter;

import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.client.butler.ButlerRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** Mitarbeiter: Modell wie James (die Frackteile sind in der Textur durchsichtig), Latzhose und gelber Bauhelm. */
public class MitarbeiterRenderer extends ButlerRenderer {
    public MitarbeiterRenderer(EntityRendererProvider.Context kontext) {
        super(kontext, StinkendReich.id("textures/entity/mitarbeiter.png"));
    }
}
