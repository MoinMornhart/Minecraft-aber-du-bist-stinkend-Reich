package de.moinmornhart.stinkendreich.client.club;

import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.club.TaenzerinEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;

/** Zeichnet die Show-Tänzerin mit eigenem Modell und einem von vier Kostümen. */
//? if >=1.21.2 {
public class TaenzerinRenderer extends HumanoidMobRenderer<TaenzerinEntity, TaenzerinZustand, TaenzerinModell> {
//?} else
/*public class TaenzerinRenderer extends HumanoidMobRenderer<TaenzerinEntity, TaenzerinModell> {*/
    private static final Identifier[] TEXTUREN = new Identifier[TaenzerinEntity.VARIANTEN];

    static {
        for (int i = 0; i < TEXTUREN.length; i++) {
            TEXTUREN[i] = StinkendReich.id("textures/entity/taenzerin_" + i + ".png");
        }
    }

    public TaenzerinRenderer(EntityRendererProvider.Context kontext) {
        super(kontext, new TaenzerinModell(kontext.bakeLayer(TaenzerinModell.SCHICHT)), 0.4F);
    }

    //? if >=1.21.2 {
    @Override
    public TaenzerinZustand createRenderState() {
        return new TaenzerinZustand();
    }

    @Override
    public void extractRenderState(TaenzerinEntity taenzerin, TaenzerinZustand zustand, float zwischen) {
        super.extractRenderState(taenzerin, zustand, zwischen);
        zustand.tanz = taenzerin.tanz();
        zustand.variante = taenzerin.variante();
    }

    @Override
    public Identifier getTextureLocation(TaenzerinZustand zustand) {
        return TEXTUREN[zustand.variante];
    }
    //?} else {
    /*@Override
    public Identifier getTextureLocation(TaenzerinEntity taenzerin) {
        return TEXTUREN[taenzerin.variante()];
    }
    *///?}
}
