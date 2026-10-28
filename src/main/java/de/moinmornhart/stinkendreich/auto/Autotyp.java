package de.moinmornhart.stinkendreich.auto;

/**
 * Die Fahrzeuge der Villa. Aussehen kommt aus tools/assets/fahrzeuge.py (Modell und acht Lackierungen),
 * hier stehen Fahrwerte und Sitzplätze.
 *
 * <p>Sitze: {seite, höhe, vorne} in Blöcken des Modells relativ zur Mitte; Platz 0 ist der Fahrer (rechts im
 * Modell, dort sitzt das Lenkrad). Jedes Fahrzeug wird mit seinem Maßstab größer gezeichnet, Sitze und
 * Trefferbox wachsen mit.</p>
 */
public enum Autotyp {
    /** Sportcabrio mit Goldfelgen und Kühlerfigur (1,5-fach gezeichnet, kleiner wirkte es neben dem Spieler wie ein Spielzeug). */
    BONZENAUTO("bonzenauto", 1.0, 1.0, 1.4, 0.0, 1.5F, new double[][]{
            {0.42, 0.55, 0.0}, {-0.42, 0.55, 0.0}, {0.42, 0.55, -1.0}, {-0.42, 0.55, -1.0}}),
    /** Kastiger Luxus-Geländewagen: langsamer, aber hoch und mit vier Plätzen. */
    GELAENDEWAGEN("gelaendewagen", 0.8, 0.85, 1.3, 0.0, 1.15F, new double[][]{
            {0.47, 0.55, 0.41}, {-0.47, 0.55, 0.41}, {0.47, 0.55, -0.84}, {-0.47, 0.55, -0.84}}),
    /**
     * Flacher Keil-Supersportwagen: am schnellsten, zwei Plätze. 1,5-fach gezeichnet; die Sitze liegen tief, damit der
     * Kopf eines sitzenden Spielers (gut 1,3 Blöcke über dem Sitzpunkt) unter das Dach passt.
     */
    SUPERSPORTWAGEN("supersportwagen", 1.4, 1.6, 1.4, 0.0, 1.5F, new double[][]{
            {0.41, 0.42, 0.16}, {-0.41, 0.42, 0.16}}),
    /** Stretch-Limousine, 8 Blöcke lang und flach: Fahrer vorn, hinten zwei Ledersofas für vier Gäste; die Sitze liegen tief, damit der Kopf unter das flache Dach passt. */
    STRETCHLIMO("stretchlimo", 0.75, 0.7, 2.9, 1.8, 1.0F, new double[][]{
            {0.41, 0.27, 1.72}, {-0.41, 0.27, 1.72}, {-0.59, 0.36, 0.5}, {0.59, 0.36, 0.5}, {-0.59, 0.36, -1.0}, {0.59, 0.36, -1.0}}),
    /** Luxus-Hubschrauber: Pilot vorn rechts, vier Plätze; fliegt (siehe {@link HubschrauberEntity}). */
    HUBSCHRAUBER("hubschrauber", 1.0, 1.0, 0.0, 1.571, 1.4F, new double[][]{
            {0.375, 0.55, 0.557}, {-0.375, 0.55, 0.557}, {0.375, 0.55, -0.221}, {-0.375, 0.55, -0.221}});

    public final String name;
    /** Faktor auf das Höchsttempo aus der Config. */
    public final double tempo;
    /** Faktor auf die Beschleunigung. */
    public final double beschleunigung;
    /** Abstand der Hinterräder von der Mitte (für Reifenqualm). */
    public final double hinterachse;
    /** Wie weit die Scheinwerfer vor der Mitte sitzen (lange Wagen). */
    public final double front;
    /** So viel größer wird das Fahrzeug gezeichnet als sein Modell aus dem Baukasten. */
    public final float massstab;
    /** Sitze in Blöcken (schon mit dem Maßstab multipliziert). */
    public final double[][] sitze;

    /** Fliegt statt zu fahren. */
    public boolean fliegt() {
        return this == HUBSCHRAUBER;
    }

    /** Maße (Hinterachse, Front, Sitze) in Blöcken des Modells; sie werden hier mit dem Maßstab multipliziert. */
    Autotyp(String name, double tempo, double beschleunigung, double hinterachse, double front, float massstab, double[][] sitze) {
        this.name = name;
        this.tempo = tempo;
        this.beschleunigung = beschleunigung;
        this.hinterachse = hinterachse * massstab;
        this.front = front * massstab;
        this.massstab = massstab;
        this.sitze = new double[sitze.length][];
        for (int i = 0; i < sitze.length; i++) {
            this.sitze[i] = new double[]{sitze[i][0] * massstab, sitze[i][1] * massstab, sitze[i][2] * massstab};
        }
    }
}
