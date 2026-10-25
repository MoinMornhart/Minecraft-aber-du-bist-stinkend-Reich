package de.moinmornhart.stinkendreich.client.fahrzeug;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.client.auto.AutoRenderState;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
//? if >=1.21.2 {
import net.minecraft.client.model.EntityModel;
//?} else {
/*import de.moinmornhart.stinkendreich.auto.AutoEntity;
import net.minecraft.client.model.HierarchicalModel;
*///?}

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Modell eines Fahrzeugs aus dem Baukasten (tools/assets/fahrzeuge.py → assets/stinkendreich/fahrzeuge/&lt;typ&gt;.json):
 * Teile mit Drehpunkt und Drehung, darin Kästen mit ihrer Stelle auf der Textur. Räder heißen rad_vl, rad_vr,
 * rad_hl, rad_hr (vorne gelenkt), beim Hubschrauber drehen sich „rotor“ und „heckrotor“ und das ganze Modell
 * neigt sich; fehlt ein Teil, wird es einfach nicht bewegt.
 */
//? if >=1.21.2 {
public class FahrzeugModell extends EntityModel<AutoRenderState> {
//?} else
/*public class FahrzeugModell extends HierarchicalModel<AutoEntity> {*/
    private final ModelPart wurzel;
    private final ModelPart radVL, radVR, radHL, radHR;
    /** Nur Hubschrauber: Haupt- und Heckrotor. */
    private final ModelPart rotor, heckrotor;

    public FahrzeugModell(ModelPart wurzel) {
        //? if >=1.21.2
        super(wurzel);
        this.wurzel = wurzel;
        this.radVL = kind(wurzel, "rad_vl");
        this.radVR = kind(wurzel, "rad_vr");
        this.radHL = kind(wurzel, "rad_hl");
        this.radHR = kind(wurzel, "rad_hr");
        this.rotor = kind(wurzel, "rotor");
        this.heckrotor = kind(wurzel, "heckrotor");
    }

    public static ModelLayerLocation schicht(String typ) {
        return new ModelLayerLocation(StinkendReich.id(typ), "main");
    }

    private static ModelPart kind(ModelPart wurzel, String name) {
        try {
            return wurzel.getChild(name);
        } catch (Exception e) {
            return null;
        }
    }

    /** Baut die Modellschicht aus der JSON-Datei des Typs. */
    public static LayerDefinition schichtAusDatei(String typ) {
        JsonObject j;
        try (InputStream ein = FahrzeugModell.class.getResourceAsStream("/assets/stinkendreich/fahrzeuge/" + typ + ".json")) {
            j = JsonParser.parseReader(new InputStreamReader(ein, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException("Fahrzeugmodell " + typ + " fehlt", e);
        }
        MeshDefinition netz = new MeshDefinition();
        Map<String, PartDefinition> teile = new HashMap<>();
        for (JsonElement e : j.getAsJsonArray("teile")) {
            JsonObject t = e.getAsJsonObject();
            CubeListBuilder kaesten = CubeListBuilder.create();
            for (JsonElement ke : t.getAsJsonArray("kaesten")) {
                JsonObject k = ke.getAsJsonObject();
                JsonArray uv = k.getAsJsonArray("uv"), von = k.getAsJsonArray("von"), masse = k.getAsJsonArray("masse");
                kaesten.texOffs(uv.get(0).getAsInt(), uv.get(1).getAsInt())
                        .addBox(von.get(0).getAsFloat(), von.get(1).getAsFloat(), von.get(2).getAsFloat(),
                                masse.get(0).getAsFloat(), masse.get(1).getAsFloat(), masse.get(2).getAsFloat());
            }
            JsonArray p = t.getAsJsonArray("pivot"), d = t.getAsJsonArray("drehung");
            PartPose pose = PartPose.offsetAndRotation(p.get(0).getAsFloat(), p.get(1).getAsFloat(), p.get(2).getAsFloat(),
                    d.get(0).getAsFloat(), d.get(1).getAsFloat(), d.get(2).getAsFloat());
            PartDefinition eltern = t.get("eltern").isJsonNull() ? netz.getRoot() : teile.get(t.get("eltern").getAsString());
            teile.put(t.get("name").getAsString(), eltern.addOrReplaceChild(t.get("name").getAsString(), kaesten, pose));
        }
        return LayerDefinition.create(netz, j.get("breite").getAsInt(), j.get("hoehe").getAsInt());
    }

    /** Hubschrauber: Rotoren drehen, Rumpf neigen (Nase runter) und in der Kurve rollen. */
    private void fliegen(float rotorWinkel, float neigung, float rollen) {
        if (rotor == null) {
            return;
        }
        rotor.yRot = rotorWinkel;
        if (heckrotor != null) {
            heckrotor.xRot = rotorWinkel * 3.0F;
        }
        // Größer zeichnen; der Boden (Modell-y 24) bleibt dabei am Boden
        float s = de.moinmornhart.stinkendreich.auto.Autotyp.HUBSCHRAUBER_MASSSTAB;
        wurzel.xScale = s;
        wurzel.yScale = s;
        wurzel.zScale = s;
        wurzel.y = 24.0F * (1.0F - s);
        wurzel.xRot = -neigung * net.minecraft.util.Mth.DEG_TO_RAD;
        wurzel.zRot = rollen * net.minecraft.util.Mth.DEG_TO_RAD;
    }

    /** Räder drehen und Vorderräder einschlagen. */
    private void raeder(float drehung, float lenkung) {
        for (ModelPart rad : new ModelPart[]{radVL, radVR, radHL, radHR}) {
            if (rad != null) {
                rad.xRot = -drehung;
            }
        }
        if (radVL != null) {
            radVL.yRot = lenkung;
        }
        if (radVR != null) {
            radVR.yRot = lenkung;
        }
    }

    //? if >=1.21.2 {
    @Override
    public void setupAnim(AutoRenderState zustand) {
        super.setupAnim(zustand);
        raeder(zustand.radWinkel, zustand.lenkWinkel);
        fliegen(zustand.rotorWinkel, zustand.neigung, zustand.rollen);
    }
    //?} else {
    /*@Override
    public ModelPart root() {
        return wurzel;
    }

    @Override
    public void setupAnim(AutoEntity auto, float a, float b, float alter, float kopfY, float kopfX) {
        float zwischen = alter - (float) auto.tickCount;
        raeder(net.minecraft.util.Mth.lerp(zwischen, auto.radWinkelAlt, auto.radWinkel), auto.lenkWinkel);
        fliegen(net.minecraft.util.Mth.lerp(zwischen, auto.rotorWinkelAlt, auto.rotorWinkel), auto.neigung, auto.rollen);
    }
    *///?}
}
