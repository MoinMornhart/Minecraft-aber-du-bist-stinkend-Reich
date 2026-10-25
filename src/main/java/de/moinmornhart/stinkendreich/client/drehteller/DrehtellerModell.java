package de.moinmornhart.stinkendreich.client.drehteller;

import de.moinmornhart.stinkendreich.StinkendReich;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
//? if >=1.21.2 {
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
//?} else {
/*import de.moinmornhart.stinkendreich.auto.DrehtellerEntity;
import net.minecraft.client.model.HierarchicalModel;
*///?}

/**
 * Drehteller: flache runde Scheibe, 108 Pixel (6,75 Blöcke) Durchmesser, 1 Pixel hoch, mit Goldrand und Stern
 * in der Mitte. Textur 512 × 128 in doppelter Auflösung (tools/assets/drehteller.py).
 */
//? if >=1.21.2 {
public class DrehtellerModell extends EntityModel<LivingEntityRenderState> {
//?} else
/*public class DrehtellerModell extends HierarchicalModel<DrehtellerEntity> {*/
    public static final ModelLayerLocation SCHICHT = new ModelLayerLocation(StinkendReich.id("drehteller"), "main");
    private final ModelPart wurzel;

    public DrehtellerModell(ModelPart wurzel) {
        //? if >=1.21.2
        super(wurzel);
        this.wurzel = wurzel;
    }

    public static LayerDefinition schicht() {
        MeshDefinition netz = new MeshDefinition();
        // Eine quadratische Platte; die Textur macht daraus eine runde Scheibe (Ecken durchsichtig)
        netz.getRoot().addOrReplaceChild("scheibe", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-54.0F, -1.0F, -54.0F, 108.0F, 1.0F, 108.0F), PartPose.offset(0.0F, 24.0F, 0.0F));
        return LayerDefinition.create(netz, 512, 128);
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
    public void setupAnim(DrehtellerEntity teller, float a, float b, float alter, float kopfY, float kopfX) {
    }
    *///?}
}
