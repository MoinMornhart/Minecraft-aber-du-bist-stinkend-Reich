package de.moinmornhart.stinkendreich.client.aufzug;

import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.mechanik.AufzugKabine;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
//? if >=1.21.2
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Zeichnet die Aufzugskabine (ohne Schatten). */
//? if >=1.21.2 {
public class AufzugRenderer extends MobRenderer<AufzugKabine, LivingEntityRenderState, AufzugModell> {
//?} else
/*public class AufzugRenderer extends MobRenderer<AufzugKabine, AufzugModell> {*/
    private static final Identifier TEXTUR = StinkendReich.id("textures/entity/aufzugkabine.png");

    public AufzugRenderer(EntityRendererProvider.Context kontext) {
        super(kontext, new AufzugModell(kontext.bakeLayer(AufzugModell.SCHICHT)), 0.0F);
    }

    //? if >=1.21.2 {
    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }

    @Override
    public Identifier getTextureLocation(LivingEntityRenderState zustand) {
        return TEXTUR;
    }
    //?} else {
    /*@Override
    public Identifier getTextureLocation(AufzugKabine kabine) {
        return TEXTUR;
    }
    *///?}
}
