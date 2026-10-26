package de.moinmornhart.stinkendreich.client.fernseher;

import com.mojang.blaze3d.vertex.PoseStack;
import de.moinmornhart.stinkendreich.fernseher.FernseherEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

/** Zeichnet den Fernseher mit dem laufenden Video oder dem Vorschaubild; der Bildschirm leuchtet selbst. */
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
        zustand.tvId = tv.getId();
        zustand.breite = tv.breite();
        zustand.hoehe = tv.hoehe();
    }

    /** Streckt das Modell auf die eingestellte Bildgröße (Standard 5 × 2,8 Blöcke). */
    @Override
    protected void scale(FernseherZustand zustand, PoseStack stapel) {
        stapel.scale(zustand.breite / FernseherEntity.STANDARD_BREITE, zustand.hoehe / FernseherEntity.STANDARD_HOEHE, 1.0F);
    }

    @Override
    public Identifier getTextureLocation(FernseherZustand zustand) {
        Identifier video = FernseherVideos.textur(zustand.tvId);
        return video != null ? video : FernseherBild.textur(zustand.video);
    }
    //?} else {
    /*@Override
    protected void scale(FernseherEntity tv, PoseStack stapel, float zwischen) {
        stapel.scale(tv.breite() / FernseherEntity.STANDARD_BREITE, tv.hoehe() / FernseherEntity.STANDARD_HOEHE, 1.0F);
    }

    @Override
    public Identifier getTextureLocation(FernseherEntity tv) {
        Identifier video = FernseherVideos.textur(tv.getId());
        return video != null ? video : FernseherBild.textur(tv.video());
    }
    *///?}

    // Großer Fernseher: zum Zeichnen zählt das ganze Bild, nicht nur das Klickfeld in der Mitte
    //? if >=26.3 {
    @Override
    protected AABB getBoundingBoxForCulling(FernseherEntity tv, float zwischen) {
        return tv.bildKasten();
    }
    //?} else if >=1.21.2 {
    /*@Override
    protected AABB getBoundingBoxForCulling(FernseherEntity tv) {
        return tv.bildKasten();
    }
    *///?}
}
