package de.moinmornhart.stinkendreich.client.drehteller;

import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.auto.DrehtellerEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
//? if >=1.21.2
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Zeichnet den Drehteller (dreht sich über die Körperdrehung der Entity mit). */
//? if >=1.21.2 {
public class DrehtellerRenderer extends MobRenderer<DrehtellerEntity, LivingEntityRenderState, DrehtellerModell> {
//?} else
/*public class DrehtellerRenderer extends MobRenderer<DrehtellerEntity, DrehtellerModell> {*/
    private static final Identifier TEXTUR = StinkendReich.id("textures/entity/drehteller.png");

    public DrehtellerRenderer(EntityRendererProvider.Context kontext) {
        super(kontext, new DrehtellerModell(kontext.bakeLayer(DrehtellerModell.SCHICHT)), 0.0F);
    }

    /** Die Trefferbox ist klein, gezeichnet wird aber immer die ganze Scheibe. */
    //? if >=1.21.2 {
    @Override
    protected boolean affectedByCulling(DrehtellerEntity teller) {
        return false;
    }
    //?}

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
    public Identifier getTextureLocation(DrehtellerEntity teller) {
        return TEXTUR;
    }
    *///?}
}
