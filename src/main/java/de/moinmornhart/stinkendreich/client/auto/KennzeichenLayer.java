package de.moinmornhart.stinkendreich.client.auto;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.moinmornhart.stinkendreich.client.fahrzeug.FahrzeugModell;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
//? if <1.21.2
/*import de.moinmornhart.stinkendreich.auto.AutoEntity;*/

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Schreibt den Namen des Besitzers auf die Nummernschilder eines Autos. Wo die Schilder sitzen, steht in den
 * Fahrzeugdaten (tools/assets/fahrzeuge.py, Eintrag „schilder“: Teil-Pfad, Ecke und Maße wie im Modell). Goldene
 * Buchstaben mit dunklem Rand (wie leuchtende Schilder); die Schrift folgt dem Modell (Maßstab, Neigen) und liegt einen
 * Hauch vor dem Schild.
 */
//? if >=1.21.2 {
public class KennzeichenLayer extends RenderLayer<AutoRenderState, FahrzeugModell> {
//?} else
/*public class KennzeichenLayer extends RenderLayer<AutoEntity, FahrzeugModell> {*/
    /** Höchstens so viele Zeichen (danach wird gekürzt). */
    private static final int MAX_ZEICHEN = 12;
    /** Goldene Buchstaben mit dunklem Rand wie leuchtende Schilder (auf jeder Lackfarbe lesbar). */
    private static final int FARBE = 0xFFF2C94C, RAND = 0xFF15110A;

    /** Ein Nummernschild: Teil-Pfad im Modell, Mitte der Schildfläche (Modell-Pixel), Breite/Höhe, hinten oder vorn. */
    private record Schild(List<String> pfad, float x, float y, float z, float breite, float hoehe, boolean hinten) {
    }

    private final List<Schild> schilder;

    //? if >=1.21.2 {
    public KennzeichenLayer(RenderLayerParent<AutoRenderState, FahrzeugModell> eltern, String typ) {
    //?} else
    /*public KennzeichenLayer(RenderLayerParent<AutoEntity, FahrzeugModell> eltern, String typ) {*/
        super(eltern);
        this.schilder = laden(typ);
    }

    private static List<Schild> laden(String typ) {
        List<Schild> liste = new ArrayList<>();
        try (InputStream ein = KennzeichenLayer.class.getResourceAsStream("/assets/stinkendreich/fahrzeuge/" + typ + ".json")) {
            JsonObject j = JsonParser.parseReader(new InputStreamReader(ein, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!j.has("schilder")) {
                return liste;
            }
            for (JsonElement e : j.getAsJsonArray("schilder")) {
                JsonObject s = e.getAsJsonObject();
                List<String> pfad = new ArrayList<>();
                s.getAsJsonArray("pfad").forEach(n -> pfad.add(n.getAsString()));
                JsonArray von = s.getAsJsonArray("von"), masse = s.getAsJsonArray("masse");
                float z0 = von.get(2).getAsFloat(), tiefe = masse.get(2).getAsFloat();
                boolean hinten = z0 > 0;                       // vorn ist -Z
                liste.add(new Schild(pfad, von.get(0).getAsFloat() + masse.get(0).getAsFloat() / 2,
                        von.get(1).getAsFloat() + masse.get(1).getAsFloat() / 2,
                        hinten ? z0 + tiefe + 0.05F : z0 - 0.05F, masse.get(0).getAsFloat(), masse.get(1).getAsFloat(), hinten));
            }
        } catch (Exception e) {
            // ohne Daten eben ohne Schrift
        }
        return liste;
    }

    /** Text aufbereiten: Großbuchstaben, gekürzt. */
    private static String text(String name) {
        String t = name.toUpperCase(Locale.ROOT);
        return t.length() > MAX_ZEICHEN ? t.substring(0, MAX_ZEICHEN) : t;
    }

    /**
     * Rückt die Pose an die Mitte des Schilds (Modellteile samt Maßstab) und skaliert so, dass der Text ins Schild passt.
     * Danach zeichnet man den Text bei (−Breite/2, −4).
     */
    private void anDasSchild(PoseStack pose, Schild s, Font schrift, String text) {
        ModelPart teil = getParentModel().wurzel();
        teil.translateAndRotate(pose);
        for (String name : s.pfad()) {
            teil = teil.getChild(name);
            teil.translateAndRotate(pose);
        }
        pose.translate(s.x() / 16.0F, s.y() / 16.0F, s.z() / 16.0F);
        if (s.hinten()) {
            // Die Schrift zeigt von sich aus nach vorn (−Z); am Heck umdrehen, damit man sie von hinten liest
            //? if >=26.3 {
            pose.rotate(Axis.YP.rotationDegrees(180.0F));
            //?} else
            /*pose.mulPose(Axis.YP.rotationDegrees(180.0F));*/
        }
        float breite = Math.max(1, schrift.width(text));
        float faktor = Math.min(s.hoehe() * 0.75F / 7.0F, s.breite() * 0.9F / breite) / 16.0F;
        pose.scale(faktor, faktor, faktor);
    }

    //? if >=1.21.9 {
    @Override
    public void submit(PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector sammler, int licht, AutoRenderState zustand,
                       float gier, float neigung) {
        if (zustand.kennzeichen.isEmpty() || zustand.isInvisible) {
            return;
        }
        Font schrift = Minecraft.getInstance().font;
        String text = text(zustand.kennzeichen);
        var zeichen = net.minecraft.network.chat.Component.literal(text).getVisualOrderText();
        for (Schild s : schilder) {
            pose.pushPose();
            anDasSchild(pose, s, schrift, text);
            sammler.submitText(pose, -schrift.width(text) / 2.0F, -4.0F, zeichen, false, Font.DisplayMode.POLYGON_OFFSET,
                    licht, FARBE, 0, RAND);
            pose.popPose();
        }
    }
    //?} elif >=1.21.2 {
    /*@Override
    public void render(PoseStack pose, net.minecraft.client.renderer.MultiBufferSource puffer, int licht, AutoRenderState zustand,
                       float gier, float neigung) {
        if (zustand.kennzeichen.isEmpty() || zustand.isInvisible) {
            return;
        }
        zeichnen(pose, puffer, licht, zustand.kennzeichen);
    }
    *///?} else {
    /*@Override
    public void render(PoseStack pose, net.minecraft.client.renderer.MultiBufferSource puffer, int licht, AutoEntity auto,
                       float a, float b, float c, float d, float e, float f) {
        if (auto.kennzeichen().isEmpty() || auto.isInvisible()) {
            return;
        }
        zeichnen(pose, puffer, licht, auto.kennzeichen());
    }
    *///?}

    //? if <1.21.9 {
    /*private void zeichnen(PoseStack pose, net.minecraft.client.renderer.MultiBufferSource puffer, int licht, String name) {
        Font schrift = Minecraft.getInstance().font;
        String text = text(name);
        for (Schild s : schilder) {
            pose.pushPose();
            anDasSchild(pose, s, schrift, text);
            schrift.drawInBatch8xOutline(net.minecraft.network.chat.Component.literal(text).getVisualOrderText(),
                    -schrift.width(text) / 2.0F, -4.0F, FARBE, RAND, pose.last().pose(), puffer, licht);
            pose.popPose();
        }
    }
    *///?}
}
