package de.moinmornhart.stinkendreich.paper.bedrock;

import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Arrays;

/**
 * Was die Bedrock-Figur einer Anzeige-Figur braucht: Modell (Variante), Gesamtmaßstab und die Werte der Eigenschaften
 * {@link BedrockPaket#EIGENSCHAFTEN} (Drehung in Grad, Verschiebung in Pixeln, Rest-Maßstab je Achse).
 *
 * <p>Java zeichnet die Anzeige als Ort + Ry(−Gier)·Rx(Neigung) · Verschiebung · Drehung · Maßstab · Modell. Die Drehung
 * wird in y-x-z zerlegt (q = Ry(a)·Rx(b)·Rz(c), wie die Knochen sr_y > sr_x > sr_z) und in Bedrock-Werte übersetzt: um x
 * gleich, um y und z mit umgekehrtem Vorzeichen, Verschiebung mit umgekehrtem z (siehe {@link Modellwandler}).
 */
public record Lage(int variante, float skala, float[] werte) {

    public static Lage von(ItemDisplay d, int variante) {
        Location ort = d.getLocation();
        Transformation t = d.getTransformation();
        Quaternionf figur = new Quaternionf().rotateY((float) Math.toRadians(-ort.getYaw())).rotateX((float) Math.toRadians(ort.getPitch()));
        Quaternionf q = new Quaternionf(figur).mul(t.getLeftRotation()).mul(t.getRightRotation());
        Vector3f euler = q.getEulerAnglesYXZ(new Vector3f());
        Vector3f verschiebung = figur.transform(new Vector3f(t.getTranslation()));
        Vector3f s = t.getScale();
        float skala = Math.max(Math.abs(s.x), Math.max(Math.abs(s.y), Math.abs(s.z)));
        if (skala < 1.0E-4F) {
            skala = 1.0F;
        }
        float[] werte = {
                rund((float) Math.toDegrees(euler.x)), rund((float) -Math.toDegrees(euler.y)), rund((float) -Math.toDegrees(euler.z)),
                rund(verschiebung.x * 16), rund(verschiebung.y * 16), rund(-verschiebung.z * 16),
                rund(s.x / skala), rund(s.y / skala), rund(s.z / skala)};
        return new Lage(variante, rund(skala), werte);
    }

    /** Auf drei Nachkommastellen, damit kleine Rechenunterschiede nicht als Änderung zählen. */
    private static float rund(float w) {
        return Math.round(w * 1000.0F) / 1000.0F;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Lage l && l.variante == variante && l.skala == skala && Arrays.equals(l.werte, werte);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * variante + Float.hashCode(skala)) + Arrays.hashCode(werte);
    }
}
