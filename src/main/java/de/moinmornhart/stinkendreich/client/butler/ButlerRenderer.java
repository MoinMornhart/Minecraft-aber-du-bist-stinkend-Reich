package de.moinmornhart.stinkendreich.client.butler;

import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.butler.ButlerEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;
//? if >=1.21.2
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/**
 * Zeichnet den Butler mit eigenem Modell und eigener Textur. Werkzeuge, die er bei der Arbeit
 * in der Hand hält, zeichnet der Grundrenderer mit.
 */
//? if >=1.21.2 {
public class ButlerRenderer extends HumanoidMobRenderer<ButlerEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
//?} else
/*public class ButlerRenderer extends HumanoidMobRenderer<ButlerEntity, HumanoidModel<ButlerEntity>> {*/
    private final Identifier textur;

    public ButlerRenderer(EntityRendererProvider.Context kontext) {
        this(kontext, StinkendReich.id("textures/entity/butler.png"));
    }

    /** Mit eigener Textur (z. B. für die Mitarbeiter, die dasselbe Modell nutzen). */
    public ButlerRenderer(EntityRendererProvider.Context kontext, Identifier textur) {
        super(kontext, new HumanoidModel<>(kontext.bakeLayer(ButlerModell.SCHICHT)), 0.5F);
        this.textur = textur;
    }

    //? if >=1.21.2 {
    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState zustand) {
        return textur;
    }
    //?} else {
    /*@Override
    public Identifier getTextureLocation(ButlerEntity butler) {
        return textur;
    }
    *///?}
}
