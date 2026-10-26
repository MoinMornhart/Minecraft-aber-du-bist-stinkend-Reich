package de.moinmornhart.stinkendreich.client.bodyguard;

import com.mojang.blaze3d.platform.NativeImage;
import de.moinmornhart.stinkendreich.StinkendReich;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Zeichnet den Bodyguard als echten Eisengolem mit Smoking und Sonnenbrille. Die Golem-Textur kommt aus Minecraft
 * selbst (auch aus Ressourcenpaketen des Spielers); beim ersten Zeichnen legt diese Klasse unseren Überzug
 * ({@code textures/entity/bodyguard_smoking.png}, nur Smoking und Brille, sonst durchsichtig) darüber und merkt sich
 * das Ergebnis als eigene Textur. Gesicht, Nase, Hände und Füße bleiben so, wie Minecraft sie zeichnet.
 */
public class BodyguardRenderer extends IronGolemRenderer {
    private static final Identifier GOLEM = Identifier.withDefaultNamespace("textures/entity/iron_golem/iron_golem.png");
    private static final Identifier SMOKING = StinkendReich.id("textures/entity/bodyguard_smoking.png");
    private static final Identifier FERTIG = StinkendReich.id("bodyguard_im_smoking");
    /** Die zusammengesetzte Textur (bis zum ersten Zeichnen leer). */
    private static Identifier textur;

    public BodyguardRenderer(EntityRendererProvider.Context kontext) {
        super(kontext);
        addLayer(new PistolenLayer(this));       // die goldene Pistole in der rechten Hand
    }

    //? if >=1.21.2 {
    @Override
    public Identifier getTextureLocation(net.minecraft.client.renderer.entity.state.IronGolemRenderState zustand) {
        return textur();
    }
    //?} else {
    /*@Override
    public Identifier getTextureLocation(net.minecraft.world.entity.animal.IronGolem golem) {
        return textur();
    }
    *///?}

    /** Golem-Textur mit Smoking-Überzug; geht dabei etwas schief, bleibt es beim normalen Golem. */
    private static Identifier textur() {
        if (textur == null) {
            textur = GOLEM;
            try {
                BufferedImage golem = lesen(GOLEM);
                BufferedImage smoking = lesen(SMOKING);
                // Der Überzug ist auf 128 × 128 gemalt; bei einem höher aufgelösten Golem wird er mitgestreckt
                java.awt.Graphics2D g = golem.createGraphics();
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.drawImage(smoking, 0, 0, golem.getWidth(), golem.getHeight(), null);
                g.dispose();
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                ImageIO.write(golem, "png", png);
                NativeImage nativ = NativeImage.read(new ByteArrayInputStream(png.toByteArray()));
                //? if >=1.21.5 {
                DynamicTexture neu = new DynamicTexture(() -> "Bodyguard im Smoking", nativ);
                //?} else
                /*DynamicTexture neu = new DynamicTexture(nativ);*/
                Minecraft.getInstance().getTextureManager().register(FERTIG, neu);
                textur = FERTIG;
            } catch (Exception e) {
                StinkendReich.LOGGER.warn("Bodyguard: Smoking nicht auf die Golem-Textur gelegt ({})", e.toString());
            }
        }
        return textur;
    }

    private static BufferedImage lesen(Identifier id) throws java.io.IOException {
        try (InputStream rein = Minecraft.getInstance().getResourceManager().getResource(id).orElseThrow().open()) {
            BufferedImage gelesen = ImageIO.read(rein);
            // In ein Bild mit Alphakanal kopieren (Paletten-PNGs lassen sich sonst nicht übermalen)
            BufferedImage bild = new BufferedImage(gelesen.getWidth(), gelesen.getHeight(), BufferedImage.TYPE_INT_ARGB);
            bild.createGraphics().drawImage(gelesen, 0, 0, null);
            return bild;
        }
    }
}
