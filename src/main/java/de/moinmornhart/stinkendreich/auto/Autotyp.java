package de.moinmornhart.stinkendreich.auto;

/**
 * Die Fahrzeuge der Villa. Aussehen kommt aus tools/assets/fahrzeuge.py (Modell und acht Lackierungen),
 * hier stehen Fahrwerte und Sitzplätze.
 *
 * <p>Sitze: {seite, höhe, vorne} in Blöcken relativ zur Mitte; Platz 0 ist der Fahrer (rechts im Modell,
 * dort sitzt das Lenkrad).</p>
 */
public enum Autotyp {
    /** Sportcabrio mit Goldfelgen und Kühlerfigur. */
    BONZENAUTO("bonzenauto", 1.0, 1.0, 1.4, 0.0, new double[][]{
            {0.42, 0.55, 0.0}, {-0.42, 0.55, 0.0}, {0.42, 0.55, -1.0}, {-0.42, 0.55, -1.0}}),
    /** Kastiger Luxus-Geländewagen: langsamer, aber hoch und mit vier Plätzen. */
    GELAENDEWAGEN("gelaendewagen", 0.8, 0.85, 1.3, 0.0, new double[][]{
            {0.47, 0.55, 0.41}, {-0.47, 0.55, 0.41}, {0.47, 0.55, -0.84}, {-0.47, 0.55, -0.84}}),
    /** Flacher Keil-Supersportwagen: am schnellsten, zwei Plätze. */
    SUPERSPORTWAGEN("supersportwagen", 1.4, 1.6, 1.4, 0.0, new double[][]{
            {0.41, 0.49, 0.16}, {-0.41, 0.49, 0.16}}),
    /** Stretch-Limousine, 8 Blöcke lang: Fahrer vorn, hinten zwei Ledersofas für vier Gäste. */
    STRETCHLIMO("stretchlimo", 0.75, 0.7, 2.9, 1.8, new double[][]{
            {0.41, 0.55, 2.16}, {-0.41, 0.55, 2.16}, {-0.59, 0.6, 0.5}, {0.59, 0.6, 0.5}, {-0.59, 0.6, -1.0}, {0.59, 0.6, -1.0}}),
    /**
     * Luxus-Hubschrauber: Pilot vorn rechts, vier Plätze; fliegt (siehe {@link HubschrauberEntity}). Das Modell
     * wird 1,4-fach gezeichnet ({@code FahrzeugModell}), die Sitze sind schon umgerechnet.
     */
    HUBSCHRAUBER("hubschrauber", 1.0, 1.0, 0.0, 2.2, new double[][]{
            {0.525, 0.77, 0.78}, {-0.525, 0.77, 0.78}, {0.525, 0.77, -0.31}, {-0.525, 0.77, -0.31}});

    /** So viel größer wird der Hubschrauber gezeichnet als sein Modell. */
    public static final float HUBSCHRAUBER_MASSSTAB = 1.4F;

    public final String name;
    /** Faktor auf das Höchsttempo aus der Config. */
    public final double tempo;
    /** Faktor auf die Beschleunigung. */
    public final double beschleunigung;
    /** Abstand der Hinterräder von der Mitte (für Reifenqualm). */
    public final double hinterachse;
    /** Wie weit die Scheinwerfer vor der Mitte sitzen (lange Wagen). */
    public final double front;
    public final double[][] sitze;

    /** Fliegt statt zu fahren. */
    public boolean fliegt() {
        return this == HUBSCHRAUBER;
    }

    Autotyp(String name, double tempo, double beschleunigung, double hinterachse, double front, double[][] sitze) {
        this.name = name;
        this.tempo = tempo;
        this.beschleunigung = beschleunigung;
        this.hinterachse = hinterachse;
        this.front = front;
        this.sitze = sitze;
    }
}
