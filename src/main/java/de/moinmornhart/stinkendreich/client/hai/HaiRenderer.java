package de.moinmornhart.stinkendreich.client.hai;

import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.hai.HaiEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
//? if >=1.21.2
import net.minecraft.client.renderer.entity.state.DolphinRenderState;

/**
 * Zeichnet den Hai mit dem eigenen {@link HaiModell} und der selbst gemalten Textur (graublauer Rücken, weißer Bauch,
 * Kiemenschlitze, schwarze Augen, Zähne; {@code tools/assets/hai.py}).
 */
//? if >=1.21.2 {
public class HaiRenderer extends MobRenderer<HaiEntity, DolphinRenderState, HaiModell> {
//?} else
/*public class HaiRenderer extends MobRenderer<HaiEntity, HaiModell> {*/
    private static final Identifier TEXTUR = StinkendReich.id("textures/entity/hai.png");

    public HaiRenderer(EntityRendererProvider.Context kontext) {
        super(kontext, new HaiModell(kontext.bakeLayer(HaiModell.SCHICHT)), 0.9F);
    }

    //? if >=1.21.2 {
    @Override
    public DolphinRenderState createRenderState() {
        return new DolphinRenderState();
    }

    @Override
    public void extractRenderState(HaiEntity hai, DolphinRenderState zustand, float zwischen) {
        super.extractRenderState(hai, zustand, zwischen);
        zustand.isMoving = hai.getDeltaMovement().horizontalDistanceSqr() > 1.0E-7;
    }

    @Override
    public Identifier getTextureLocation(DolphinRenderState zustand) {
        return TEXTUR;
    }
    //?} else {
    /*@Override
    public Identifier getTextureLocation(HaiEntity hai) {
        return TEXTUR;
    }
    *///?}
}
