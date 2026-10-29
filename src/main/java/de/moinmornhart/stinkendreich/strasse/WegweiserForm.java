package de.moinmornhart.stinkendreich.strasse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Form eines Wegweisers an der Straße: ein Holzpfahl mit Pfeilschildern übereinander, jedes Schild zeigt in die Richtung
 * seiner Straße und trägt Ziel und Entfernung („MINE · 340 m“) auf beiden Seiten.
 *
 * <p>Gebaut wird er aus Anzeige-Figuren von Minecraft selbst (Block- und Text-Anzeigen). Deshalb sehen ihn auch Spieler
 * ohne Mod, und Mod und Paper-Plugin teilen sich diese Klasse (ohne Minecraft-Abhängigkeiten): Sie liefert nur die
 * Teile mit Drehung, Verschiebung und Größe relativ zum Fußpunkt des Pfahls (Mitte, Bodenhöhe).</p>
 */
public final class WegweiserForm {
    /** Pfahl, Schilder und Schrift. */
    public static final String PFAHL = "minecraft:spruce_log", KAPPE = "minecraft:dark_oak_slab", BRETT = "minecraft:dark_oak_planks";
    /** Farbe der Schrift (Gold, passend zur Villa). */
    public static final String SCHRIFTFARBE = "#FFD45A";
    /** Größe der Schrift (1 = Minecraft-Standard, ein Buchstabe ist dann gut 0,15 Blöcke breit). */
    public static final float SCHRIFT = 0.8F;

    private static final float PFAHL_DICKE = 0.22F, BRETT_HOEHE = 0.36F, BRETT_DICKE = 0.08F, ABSTAND = 0.55F;
    private static final float BREITE_JE_ZEICHEN = 0.135F, RAND = 0.55F, MIN_LAENGE = 1.3F, MAX_LAENGE = 3.4F;

    /** Ein Schild: Aufschrift und Richtung, in die der Pfeil zeigt (waagerecht, x/z, muss nicht normiert sein). */
    public record Schild(String text, double dx, double dz) {
    }

    /**
     * Ein Teil des Wegweisers. {@code text == null}: Block-Anzeige mit {@code block}; sonst Text-Anzeige mit diesem Text.
     * {@code drehung} ist ein Quaternion (x, y, z, w), {@code versatz} und {@code groesse} wie in der Transformation
     * einer Anzeige-Figur (Verschiebung + Drehung · (Größe · Punkt)).
     */
    public record Teil(String block, String text, float[] drehung, float[] versatz, float[] groesse) {
        /** Transformation im Format der Anzeige-Figuren (SNBT), z. B. für {@code /summon}. */
        public String snbt() {
            return String.format(Locale.ROOT, "{left_rotation:[%sf,%sf,%sf,%sf],right_rotation:[0f,0f,0f,1f],translation:[%sf,%sf,%sf],scale:[%sf,%sf,%sf]}",
                    z(drehung[0]), z(drehung[1]), z(drehung[2]), z(drehung[3]), z(versatz[0]), z(versatz[1]), z(versatz[2]),
                    z(groesse[0]), z(groesse[1]), z(groesse[2]));
        }

        private static String z(float f) {
            return String.format(Locale.ROOT, "%.4f", f);
        }
    }

    private WegweiserForm() {
    }

    /** Aufschrift eines Schildes: Ziel in Großbuchstaben und Entfernung (unter 1000 m in Metern, sonst in km). */
    public static String aufschrift(String ziel, double meter) {
        String weite = meter < 1000 ? Math.round(meter / 10.0) * 10 + " m" : String.format(Locale.GERMAN, "%.1f km", meter / 1000.0);
        return ziel.toUpperCase(Locale.GERMAN) + "  ·  " + weite;
    }

    /** Alle Teile eines Wegweisers mit diesen Schildern (das erste Schild oben). */
    public static List<Teil> teile(List<Schild> schilder) {
        List<Teil> t = new ArrayList<>();
        float hoehe = 1.75F + schilder.size() * ABSTAND;
        float[] ohne = {0, 0, 0, 1};
        t.add(new Teil(PFAHL, null, ohne, new float[]{-PFAHL_DICKE / 2, 0, -PFAHL_DICKE / 2}, new float[]{PFAHL_DICKE, hoehe, PFAHL_DICKE}));
        t.add(new Teil(KAPPE, null, ohne, new float[]{-0.17F, hoehe, -0.17F}, new float[]{0.34F, 0.16F, 0.34F}));
        for (int i = 0; i < schilder.size(); i++) {
            Schild s = schilder.get(i);
            double laenge = Math.hypot(s.dx(), s.dz());
            if (laenge < 1e-6) {
                continue;
            }
            float dx = (float) (s.dx() / laenge), dz = (float) (s.dz() / laenge);
            float y = hoehe - 0.4F - i * ABSTAND;
            float l = Math.max(MIN_LAENGE, Math.min(MAX_LAENGE, s.text().length() * BREITE_JE_ZEICHEN * SCHRIFT + RAND));
            // Drehung um die Hochachse, die die x-Achse des Schildes in Pfeilrichtung legt: (1,0,0) → (dx, 0, dz)
            double winkel = Math.atan2(-dz, dx);
            float[] gier = {0, (float) Math.sin(winkel / 2), 0, (float) Math.cos(winkel / 2)};
            // Brett: von knapp hinter dem Pfahl bis zur Pfeilspitze
            float hinten = -0.15F;
            t.add(new Teil(BRETT, null, gier, verschoben(gier, y, hinten, -BRETT_HOEHE / 2, -BRETT_DICKE / 2),
                    new float[]{l - hinten, BRETT_HOEHE, BRETT_DICKE}));
            // Spitze: ein auf die Ecke gestelltes Quadrat (Diagonale = Brettbreite), Mitte am Ende des Bretts
            float a = (float) (BRETT_HOEHE / Math.sqrt(2));
            float[] spitze = mal(gier, new float[]{0, 0, (float) Math.sin(Math.PI / 8), (float) Math.cos(Math.PI / 8)});
            t.add(new Teil(BRETT, null, spitze, verschoben(gier, y, l, -BRETT_HOEHE / 2, -BRETT_DICKE / 2), new float[]{a, a, BRETT_DICKE}));
            // Schrift vorn und hinten (hinten um 180° gedreht, damit sie auch von dort von links nach rechts läuft)
            float mitte = (hinten + l) * 0.46F, unten = -0.1F * SCHRIFT, ab = BRETT_DICKE / 2 + 0.006F;
            float[] gross = {SCHRIFT, SCHRIFT, SCHRIFT};
            t.add(new Teil(null, s.text(), gier, verschoben(gier, y, mitte, unten, ab), gross));
            t.add(new Teil(null, s.text(), mal(gier, new float[]{0, 1, 0, 0}), verschoben(gier, y, mitte, unten, -ab), gross));
        }
        return t;
    }

    /** Punkt (x, y, z) im Schild dreht mit {@code gier} (nur um die Hochachse) und liegt auf Höhe {@code hoehe}. */
    private static float[] verschoben(float[] gier, float hoehe, float x, float y, float z) {
        float w = gier[3], s = gier[1];
        // Drehung um y mit Winkel 2·atan2(s, w): cos = w² − s², sin = 2ws
        float c = w * w - s * s, n = 2 * w * s;
        return new float[]{x * c + z * n, hoehe + y, -x * n + z * c};
    }

    /** Hamilton-Produkt a · b (erst b, dann a). */
    private static float[] mal(float[] a, float[] b) {
        return new float[]{
                a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
                a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
                a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
                a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]};
    }
}
