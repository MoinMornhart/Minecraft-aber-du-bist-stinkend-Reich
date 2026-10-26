package de.moinmornhart.stinkendreich.client.auto;

import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.auto.AutoEntity;
import de.moinmornhart.stinkendreich.auto.Autotyp;
import de.moinmornhart.stinkendreich.client.fahrzeug.FahrzeugModell;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/** Zeichnet ein Auto (jeder Typ mit eigenem Modell) in seiner Lackfarbe. */
//? if >=1.21.2 {
public class AutoRenderer extends MobRenderer<AutoEntity, AutoRenderState, FahrzeugModell> {
//?} else
/*public class AutoRenderer extends MobRenderer<AutoEntity, FahrzeugModell> {*/
    private final Identifier[] texturen = new Identifier[AutoEntity.FARBEN.length];

    public AutoRenderer(EntityRendererProvider.Context kontext, Autotyp typ) {
        super(kontext, new FahrzeugModell(kontext.bakeLayer(FahrzeugModell.schicht(typ.name)), typ.massstab),
                1.6F * typ.massstab);
        for (int i = 0; i < texturen.length; i++) {
            texturen[i] = StinkendReich.id("textures/entity/" + typ.name + "/" + AutoEntity.FARBEN[i] + ".png");
        }
        addLayer(new KennzeichenLayer(this, typ.name));
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
        zustand.rotorWinkel = net.minecraft.util.Mth.lerp(zwischen, auto.rotorWinkelAlt, auto.rotorWinkel);
        zustand.neigung = auto.neigung;
        zustand.rollen = auto.rollen;
        zustand.farbe = auto.farbe();
        zustand.kennzeichen = auto.kennzeichen();
    }

    @Override
    public Identifier getTextureLocation(AutoRenderState zustand) {
        return texturen[zustand.farbe];
    }
    //?} else {
    /*@Override
    public Identifier getTextureLocation(AutoEntity auto) {
        return texturen[auto.farbe()];
    }
    *///?}
}
