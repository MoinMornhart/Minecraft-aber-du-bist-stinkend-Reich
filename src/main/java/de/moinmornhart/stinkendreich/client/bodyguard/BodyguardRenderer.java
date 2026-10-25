package de.moinmornhart.stinkendreich.client.bodyguard;

import de.moinmornhart.stinkendreich.StinkendReich;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.resources.Identifier;

/** Zeichnet den Bodyguard mit dem Modell des Eisengolems und eigener Textur (Smoking, Sonnenbrille, goldene Pistole). */
public class BodyguardRenderer extends IronGolemRenderer {
    private static final Identifier TEXTUR = StinkendReich.id("textures/entity/bodyguard.png");

    public BodyguardRenderer(EntityRendererProvider.Context kontext) {
        super(kontext);
    }

    //? if >=1.21.2 {
    @Override
    public Identifier getTextureLocation(net.minecraft.client.renderer.entity.state.IronGolemRenderState zustand) {
        return TEXTUR;
    }
    //?} else {
    /*@Override
    public Identifier getTextureLocation(net.minecraft.world.entity.animal.IronGolem golem) {
        return TEXTUR;
    }
    *///?}
}
