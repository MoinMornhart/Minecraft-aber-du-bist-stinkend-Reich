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
    private static final Identifier TEXTUR = StinkendReich.id("textures/entity/butler.png");

    public ButlerRenderer(EntityRendererProvider.Context kontext) {
        super(kontext, new HumanoidModel<>(kontext.bakeLayer(ButlerModell.SCHICHT)), 0.5F);
    }

    //? if >=1.21.2 {
    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState zustand) {
        return TEXTUR;
    }
    //?} else {
    /*@Override
    public Identifier getTextureLocation(ButlerEntity butler) {
        return TEXTUR;
    }
    *///?}
}
