package de.moinmornhart.stinkendreich.client.auto;

import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.auto.AutoEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/** Zeichnet das Bonzenauto in seiner Lackfarbe. */
//? if >=1.21.2 {
public class AutoRenderer extends MobRenderer<AutoEntity, AutoRenderState, AutoModell> {
//?} else
/*public class AutoRenderer extends MobRenderer<AutoEntity, AutoModell> {*/
    private static final Identifier[] TEXTUREN = new Identifier[AutoEntity.FARBEN.length];

    static {
        for (int i = 0; i < TEXTUREN.length; i++) {
            TEXTUREN[i] = StinkendReich.id("textures/entity/bonzenauto/" + AutoEntity.FARBEN[i] + ".png");
        }
    }

    public AutoRenderer(EntityRendererProvider.Context kontext) {
        super(kontext, new AutoModell(kontext.bakeLayer(AutoModell.SCHICHT)), 1.6F);
    }

    //? if >=1.21.2 {
    @Override
    public AutoRenderState createRenderState() {
        return new AutoRenderState();
    }

    @Override
    public void extractRenderState(AutoEntity auto, AutoRenderState zustand, float zwischen) {
        super.extractRenderState(auto, zustand, zwischen);
        zustand.radWinkel = net.minecraft.util.Mth.lerp(zwischen, auto.radWinkelAlt, auto.radWinkel);
        zustand.lenkWinkel = auto.lenkWinkel;
        zustand.farbe = auto.farbe();
    }

    @Override
    public Identifier getTextureLocation(AutoRenderState zustand) {
        return TEXTUREN[zustand.farbe];
    }
    //?} else {
    /*@Override
    public Identifier getTextureLocation(AutoEntity auto) {
        return TEXTUREN[auto.farbe()];
    }
    *///?}
}
