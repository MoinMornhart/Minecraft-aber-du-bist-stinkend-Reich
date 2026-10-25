package de.moinmornhart.stinkendreich.client.butler;

import de.moinmornhart.stinkendreich.StinkendReich;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Eigenes Modell des Butlers: Menschengestalt mit Frack (zwei Frackschöße hinten),
 * abstehender Fliege am Kragen und glatt gekämmtem Haar. Textur: 64×64,
 * Aufteilung oben wie bei einem Spieler-Skin, unten die Teile des Fracks.
 */
public final class ButlerModell {
    public static final ModelLayerLocation SCHICHT = new ModelLayerLocation(StinkendReich.id("butler"), "main");

    private ButlerModell() {
    }

    public static LayerDefinition schicht() {
        MeshDefinition netz = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition koerper = netz.getRoot().getChild("body");
        // Frackschöße: hängen hinten vom Gürtel bis zu den Kniekehlen, leicht abstehend
        koerper.addOrReplaceChild("frack_links", CubeListBuilder.create().texOffs(0, 32)
                .addBox(0.1F, 0.0F, 0.0F, 3.8F, 8.0F, 0.6F), PartPose.offsetAndRotation(0.0F, 10.5F, 1.9F, 0.18F, 0.0F, -0.04F));
        koerper.addOrReplaceChild("frack_rechts", CubeListBuilder.create().texOffs(12, 32)
                .addBox(-3.9F, 0.0F, 0.0F, 3.8F, 8.0F, 0.6F), PartPose.offsetAndRotation(0.0F, 10.5F, 1.9F, 0.18F, 0.0F, 0.04F));
        // Fliege: zwei Schleifen und ein Knoten vor dem Hemdkragen
        koerper.addOrReplaceChild("fliege", CubeListBuilder.create().texOffs(24, 32)
                .addBox(-2.0F, 0.4F, -2.7F, 4.0F, 1.6F, 0.7F), PartPose.ZERO);
        // Haar: etwas Volumen über dem Kopf (glatt nach hinten gekämmt)
        PartDefinition kopf = netz.getRoot().getChild("head");
        kopf.addOrReplaceChild("haar", CubeListBuilder.create().texOffs(0, 42)
                .addBox(-4.0F, -8.6F, -4.0F, 8.0F, 1.0F, 8.6F), PartPose.ZERO);
        return LayerDefinition.create(netz, 64, 64);
    }
}
