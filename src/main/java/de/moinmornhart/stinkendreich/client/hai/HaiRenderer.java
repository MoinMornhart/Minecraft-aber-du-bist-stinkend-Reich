package de.moinmornhart.stinkendreich.client.hai;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import de.moinmornhart.stinkendreich.StinkendReich;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.DolphinRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Zeichnet den Hai als Delfin-Modell, 1,4-mal so groß und grau. Die Textur entsteht beim ersten Zeichnen aus der
 * Delfin-Textur von Minecraft (auch aus Ressourcenpaketen des Spielers): Jedes Pixel wird nach seiner Helligkeit
 * umgefärbt – dunkle Stellen (Rücken) zu dunklem Graublau, helle (Bauch) zu hellem Grauweiß. Augen und Maul bleiben
 * dunkel. Keine Mojang-Textur im Repo.
 */
public class HaiRenderer extends DolphinRenderer {
    private static final float MASSSTAB = 1.4F;
    /** Rücken: dunkles Graublau; Bauch: helles, leicht warmes Grau. */
    private static final int[] RUECKEN = {58, 68, 80}, BAUCH = {222, 222, 214};
    /** Delfin-Textur (erwachsen, Jungtier …) → fertige Hai-Textur. */
    private static final Map<Identifier, Identifier> FERTIG = new HashMap<>();

    public HaiRenderer(EntityRendererProvider.Context kontext) {
        super(kontext);
        shadowRadius = 0.7F * MASSSTAB;
    }

    //? if >=1.21.2 {
    @Override
    public Identifier getTextureLocation(net.minecraft.client.renderer.entity.state.DolphinRenderState zustand) {
        return haiTextur(super.getTextureLocation(zustand));
    }

    @Override
    protected void scale(net.minecraft.client.renderer.entity.state.DolphinRenderState zustand, PoseStack pose) {
        super.scale(zustand, pose);
        pose.scale(MASSSTAB, MASSSTAB, MASSSTAB);
    }
    //?} else {
    /*@Override
    public Identifier getTextureLocation(net.minecraft.world.entity.animal.Dolphin delfin) {
        return haiTextur(super.getTextureLocation(delfin));
    }

    @Override
    protected void scale(net.minecraft.world.entity.animal.Dolphin delfin, PoseStack pose, float zwischen) {
        super.scale(delfin, pose, zwischen);
        pose.scale(MASSSTAB, MASSSTAB, MASSSTAB);
    }
    *///?}

    /** Hai-Textur zu einer Delfin-Textur; geht dabei etwas schief, bleibt es beim Delfin. */
    private static Identifier haiTextur(Identifier delfin) {
        return FERTIG.computeIfAbsent(delfin, quelle -> {
            try {
                BufferedImage bild = lesen(quelle);
                umfaerben(bild);
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                ImageIO.write(bild, "png", png);
                NativeImage nativ = NativeImage.read(new ByteArrayInputStream(png.toByteArray()));
                Identifier ziel = StinkendReich.id("hai/" + quelle.getPath().replace('/', '_').replace(".png", ""));
                //? if >=1.21.5 {
                DynamicTexture neu = new DynamicTexture(() -> "Hai", nativ);
                //?} else
                /*DynamicTexture neu = new DynamicTexture(nativ);*/
                Minecraft.getInstance().getTextureManager().register(ziel, neu);
                return ziel;
            } catch (Exception e) {
                StinkendReich.LOGGER.warn("Hai: Delfin-Textur nicht umgefärbt ({})", e.toString());
                return quelle;
            }
        });
    }

    /**
     * Färbt nach Helligkeit um: Die hellsten und dunkelsten Pixel der Vorlage bestimmen den Bereich, dazwischen wird
     * zwischen Rücken- und Bauchfarbe überblendet (etwas Kontrast dazu). Durchsichtige Pixel bleiben, wie sie sind.
     */
    private static void umfaerben(BufferedImage bild) {
        double min = 1, max = 0;
        for (int y = 0; y < bild.getHeight(); y++) {
            for (int x = 0; x < bild.getWidth(); x++) {
                int argb = bild.getRGB(x, y);
                if ((argb >>> 24) == 0) {
                    continue;
                }
                double h = helligkeit(argb);
                min = Math.min(min, h);
                max = Math.max(max, h);
            }
        }
        double spanne = Math.max(1.0E-3, max - min);
        for (int y = 0; y < bild.getHeight(); y++) {
            for (int x = 0; x < bild.getWidth(); x++) {
                int argb = bild.getRGB(x, y);
                int a = argb >>> 24;
                if (a == 0) {
                    continue;
                }
                double t = (helligkeit(argb) - min) / spanne;
                // Kontrast: S-Kurve, damit Rücken und Bauch deutlich getrennt sind
                t = t * t * (3 - 2 * t);
                int r = (int) Math.round(RUECKEN[0] + (BAUCH[0] - RUECKEN[0]) * t);
                int g = (int) Math.round(RUECKEN[1] + (BAUCH[1] - RUECKEN[1]) * t);
                int b = (int) Math.round(RUECKEN[2] + (BAUCH[2] - RUECKEN[2]) * t);
                // Sehr dunkle Pixel (Augen, Maul) bleiben fast schwarz
                if (t < 0.08) {
                    r = r / 3;
                    g = g / 3;
                    b = b / 3;
                }
                bild.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
    }

    private static double helligkeit(int argb) {
        return (0.299 * ((argb >> 16) & 0xFF) + 0.587 * ((argb >> 8) & 0xFF) + 0.114 * (argb & 0xFF)) / 255.0;
    }

    private static BufferedImage lesen(Identifier id) throws java.io.IOException {
        try (InputStream rein = Minecraft.getInstance().getResourceManager().getResource(id).orElseThrow().open()) {
            BufferedImage gelesen = ImageIO.read(rein);
            // In ein Bild mit Alphakanal kopieren (Paletten-PNGs lassen sich sonst nicht umfärben)
            BufferedImage bild = new BufferedImage(gelesen.getWidth(), gelesen.getHeight(), BufferedImage.TYPE_INT_ARGB);
            bild.createGraphics().drawImage(gelesen, 0, 0, null);
            return bild;
        }
    }
}
