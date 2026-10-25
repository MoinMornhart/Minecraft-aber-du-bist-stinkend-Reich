package de.moinmornhart.stinkendreich.client.fernseher;

import de.moinmornhart.stinkendreich.StinkendReich;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
//? if >=1.21.2 {
import net.minecraft.client.model.EntityModel;
//?} else {
/*import de.moinmornhart.stinkendreich.fernseher.FernseherEntity;
import net.minecraft.client.model.HierarchicalModel;
*///?}

/**
 * Flacher Fernseher 80 × 45 Pixel (5 × 2,8 Blöcke), 2 Pixel tief. Die Vorderseite (-Z) ist der Bildschirm und liegt
 * auf der Textur bei (2, 2) mit 80 × 45; die Textur darf ein Vielfaches davon groß sein (feiner aufgelöst).
 */
//? if >=1.21.2 {
public class FernseherModell extends EntityModel<FernseherZustand> {
//?} else
/*public class FernseherModell extends HierarchicalModel<FernseherEntity> {*/
    public static final ModelLayerLocation SCHICHT = new ModelLayerLocation(StinkendReich.id("fernseher"), "main");
    private final ModelPart wurzel;

    public FernseherModell(ModelPart wurzel) {
        //? if >=1.21.2
        super(wurzel);
        this.wurzel = wurzel;
    }

    public static LayerDefinition schicht() {
        MeshDefinition netz = new MeshDefinition();
        netz.getRoot().addOrReplaceChild("bildschirm", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-40.0F, -45.0F, -1.0F, 80.0F, 45.0F, 2.0F), PartPose.offset(0.0F, 24.0F, 0.0F));
        return LayerDefinition.create(netz, 256, 64);
    }

    //? if >=1.21.2 {
    @Override
    public void setupAnim(FernseherZustand zustand) {
        super.setupAnim(zustand);
    }
    //?} else {
    /*@Override
    public ModelPart root() {
        return wurzel;
    }

    @Override
    public void setupAnim(FernseherEntity tv, float a, float b, float alter, float kopfY, float kopfX) {
    }
    *///?}
}
