package de.moinmornhart.stinkendreich.client.fernseher;

import de.moinmornhart.stinkendreich.fernseher.FernseherEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/** Zeichnet den Fernseher mit dem Bild des gewählten Videos; der Bildschirm leuchtet selbst. */
//? if >=1.21.2 {
public class FernseherRenderer extends MobRenderer<FernseherEntity, FernseherZustand, FernseherModell> {
//?} else
/*public class FernseherRenderer extends MobRenderer<FernseherEntity, FernseherModell> {*/

    public FernseherRenderer(EntityRendererProvider.Context kontext) {
        super(kontext, new FernseherModell(kontext.bakeLayer(FernseherModell.SCHICHT)), 0.0F);
    }

    @Override
    protected int getBlockLightLevel(FernseherEntity tv, BlockPos pos) {
        return 15;
    }

    //? if >=1.21.2 {
    @Override
    public FernseherZustand createRenderState() {
        return new FernseherZustand();
    }

    @Override
    public void extractRenderState(FernseherEntity tv, FernseherZustand zustand, float zwischen) {
        super.extractRenderState(tv, zustand, zwischen);
        zustand.video = tv.video();
    }

    @Override
    public Identifier getTextureLocation(FernseherZustand zustand) {
        return FernseherBild.textur(zustand.video);
    }
    //?} else {
    /*@Override
    public Identifier getTextureLocation(FernseherEntity tv) {
        return FernseherBild.textur(tv.video());
    }
    *///?}
}
