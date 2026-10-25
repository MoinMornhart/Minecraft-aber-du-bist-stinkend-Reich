package de.moinmornhart.stinkendreich.client.aufzug;

import de.moinmornhart.stinkendreich.StinkendReich;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
//? if >=1.21.2 {
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
//?} else {
/*import de.moinmornhart.stinkendreich.mechanik.AufzugKabine;
import net.minecraft.client.model.HierarchicalModel;
*///?}

/**
 * Modell der Aufzugskabine: Marmorboden mit Goldkante, drei Glaswände zwischen goldenen Eckpfosten,
 * Handläufe, rote Samtbank an der Rückwand, Lichtdecke und goldener Türsturz. Die Tür ist im Modell -Z.
 * Textur 256 × 256 (tools/assets/aufzug.py).
 */
//? if >=1.21.2 {
public class AufzugModell extends EntityModel<LivingEntityRenderState> {
//?} else
/*public class AufzugModell extends HierarchicalModel<AufzugKabine> {*/
    public static final ModelLayerLocation SCHICHT = new ModelLayerLocation(StinkendReich.id("aufzugkabine"), "main");

    private final ModelPart wurzel;

    public AufzugModell(ModelPart wurzel) {
        //? if >=1.21.2
        super(wurzel);
        this.wurzel = wurzel;
    }

    public static LayerDefinition schicht() {
        MeshDefinition netz = new MeshDefinition();
        PartDefinition w = netz.getRoot();
        // Ursprung auf dem Boden (y = 24), negative y-Werte gehen nach oben; Innenhöhe 44 Pixel
        w.addOrReplaceChild("boden", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-14.0F, 23.0F, -14.0F, 28.0F, 1.0F, 28.0F), PartPose.ZERO);
        w.addOrReplaceChild("decke", CubeListBuilder.create().texOffs(0, 30)
                .addBox(-14.0F, -22.0F, -14.0F, 28.0F, 1.0F, 28.0F), PartPose.ZERO);
        w.addOrReplaceChild("licht", CubeListBuilder.create().texOffs(120, 0)
                .addBox(-7.0F, -21.0F, -7.0F, 14.0F, 1.0F, 14.0F), PartPose.ZERO);
        // Glaswände links, rechts und hinten
        w.addOrReplaceChild("glas", CubeListBuilder.create()
                .texOffs(0, 60).addBox(-14.0F, -21.0F, -12.0F, 1.0F, 44.0F, 26.0F)
                .texOffs(0, 60).addBox(13.0F, -21.0F, -12.0F, 1.0F, 44.0F, 26.0F)
                .texOffs(0, 132).addBox(-12.0F, -21.0F, 13.0F, 24.0F, 44.0F, 1.0F), PartPose.ZERO);
        // Goldene Eckpfosten, Türsturz und Handläufe
        w.addOrReplaceChild("gold", CubeListBuilder.create()
                .texOffs(120, 20).addBox(-14.0F, -21.0F, -14.0F, 2.0F, 44.0F, 2.0F)
                .texOffs(120, 20).addBox(12.0F, -21.0F, -14.0F, 2.0F, 44.0F, 2.0F)
                .texOffs(120, 20).addBox(-14.0F, -21.0F, 12.0F, 2.0F, 44.0F, 2.0F)
                .texOffs(120, 20).addBox(12.0F, -21.0F, 12.0F, 2.0F, 44.0F, 2.0F)
                .texOffs(140, 20).addBox(-12.0F, -21.0F, -14.0F, 24.0F, 3.0F, 1.0F)
                .texOffs(140, 30).addBox(-13.0F, 9.0F, -11.0F, 1.0F, 1.0F, 20.0F)
                .texOffs(140, 30).addBox(12.0F, 9.0F, -11.0F, 1.0F, 1.0F, 20.0F), PartPose.ZERO);
        // Samtbank an der Rückwand: Sockel, Sitz, Lehne
        w.addOrReplaceChild("bank", CubeListBuilder.create()
                .texOffs(60, 132).addBox(-11.0F, 17.0F, 6.0F, 22.0F, 6.0F, 6.0F)
                .texOffs(120, 132).addBox(-12.0F, 15.0F, 5.0F, 24.0F, 2.0F, 8.0F)
                .texOffs(120, 150).addBox(-12.0F, 4.0F, 11.0F, 24.0F, 11.0F, 2.0F), PartPose.ZERO);
        return LayerDefinition.create(netz, 256, 256);
    }

    //? if >=1.21.2 {
    @Override
    public void setupAnim(LivingEntityRenderState zustand) {
        super.setupAnim(zustand);
    }
    //?} else {
    /*@Override
    public ModelPart root() {
        return wurzel;
    }

    @Override
    public void setupAnim(AufzugKabine kabine, float a, float b, float alter, float kopfY, float kopfX) {
    }
    *///?}
}
