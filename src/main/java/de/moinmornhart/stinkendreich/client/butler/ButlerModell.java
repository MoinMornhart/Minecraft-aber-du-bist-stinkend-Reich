package de.moinmornhart.stinkendreich.client.butler;

import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.client.MenschenNetz;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Eigenes Modell des Butlers: Menschengestalt im Layout einer Spieler-Haut ({@link MenschenNetz}, zweite Schicht für
 * Haar, Frack, Ärmel und Schuhe), dazu zwei Frackschöße hinten und eine abstehende Fliege am Kragen. Dieselbe Haut
 * tragen James und der Mitarbeiter auch als Schaufensterpuppe auf Servern ohne Mod (tools/assets/butler.py).
 */
public final class ButlerModell {
    public static final ModelLayerLocation SCHICHT = new ModelLayerLocation(StinkendReich.id("butler"), "main");

    private ButlerModell() {
    }

    public static LayerDefinition schicht() {
        MeshDefinition netz = MenschenNetz.erstellen(false);
        PartDefinition koerper = netz.getRoot().getChild("body");
        // Frackschöße: hängen hinten vom Gürtel bis zu den Kniekehlen, leicht abstehend (Fläche 56,16 der Haut)
        koerper.addOrReplaceChild("frack_links", CubeListBuilder.create().texOffs(56, 16)
                .addBox(0.2F, 0.0F, 0.0F, 3.5F, 8.0F, 0.5F), PartPose.offsetAndRotation(0.0F, 10.5F, 2.0F, 0.18F, 0.0F, -0.04F));
        koerper.addOrReplaceChild("frack_rechts", CubeListBuilder.create().texOffs(56, 16).mirror()
                .addBox(-3.7F, 0.0F, 0.0F, 3.5F, 8.0F, 0.5F), PartPose.offsetAndRotation(0.0F, 10.5F, 2.0F, 0.18F, 0.0F, 0.04F));
        // Fliege: steht vor dem Hemdkragen (Fläche 24,0)
        koerper.addOrReplaceChild("fliege", CubeListBuilder.create().texOffs(24, 0)
                .addBox(-1.5F, 0.3F, -2.8F, 3.0F, 1.5F, 0.5F), PartPose.ZERO);
        return LayerDefinition.create(netz, 64, 64);
    }
}
