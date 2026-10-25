package de.moinmornhart.stinkendreich.klavier;

import java.util.ArrayList;
import java.util.List;

/**
 * Stücke, die der Konzertflügel selbst spielt. Alle sind gemeinfrei (Beethoven, Pachelbel). Jede Note ist
 * {Tick, MIDI-Nummer}; 20 Ticks sind eine Sekunde, 60 = C4.
 */
public final class Lieder {
    /** Namen für das Spielfenster (Schlüssel der Übersetzung: stinkendreich.klavier.lied.NAME). */
    public static final String[] NAMEN = {"fuer_elise", "ode", "kanon"};

    private Lieder() {
    }

    public static int[][] stueck(String name) {
        return switch (name) {
            case "fuer_elise" -> fuerElise();
            case "ode" -> odeAnDieFreude();
            case "kanon" -> kanonInD();
            default -> null;
        };
    }

    /** Für Elise (Beethoven), Anfang zweimal: Sechzehntel = 4 Ticks, rechte Hand oben, linke Hand Begleitung. */
    private static int[][] fuerElise() {
        int[][] teil = {
                {76}, {75}, {76}, {75}, {76}, {71}, {74}, {72},
                {69, 45}, {52}, {57}, {60}, {64}, {69},
                {71, 40}, {52}, {56}, {64}, {68}, {71},
                {72, 45}, {52}, {57}, {64}, {76}, {75},
                {76}, {75}, {76}, {71}, {74}, {72},
                {69, 45}, {52}, {57}, {60}, {64}, {69},
                {71, 40}, {52}, {56}, {64}, {72}, {71},
                {69, 45}, {52}, {57}, {}, {}, {}};
        List<int[]> noten = new ArrayList<>();
        for (int durchgang = 0; durchgang < 2; durchgang++) {
            int start = durchgang * teil.length * 4;
            for (int schritt = 0; schritt < teil.length; schritt++) {
                for (int note : teil[schritt]) {
                    noten.add(new int[]{start + schritt * 4, note});
                }
            }
        }
        return noten.toArray(new int[0][]);
    }

    /** Ode an die Freude (Beethoven, 9. Sinfonie), Viertel = 10 Ticks, mit Bass auf jeder Takt-Eins. */
    private static int[][] odeAnDieFreude() {
        // {Länge in Vierteln * 2, Note}: Melodie in C-Dur
        int[][] melodie = {
                {2, 64}, {2, 64}, {2, 65}, {2, 67}, {2, 67}, {2, 65}, {2, 64}, {2, 62},
                {2, 60}, {2, 60}, {2, 62}, {2, 64}, {3, 64}, {1, 62}, {4, 62},
                {2, 64}, {2, 64}, {2, 65}, {2, 67}, {2, 67}, {2, 65}, {2, 64}, {2, 62},
                {2, 60}, {2, 60}, {2, 62}, {2, 64}, {3, 62}, {1, 60}, {4, 60}};
        int[] bass = {48, 43, 48, 43, 48, 43, 48, 48};
        List<int[]> noten = new ArrayList<>();
        int zeit = 0;
        for (int[] m : melodie) {
            noten.add(new int[]{zeit, m[1]});
            zeit += m[0] * 5;
        }
        for (int takt = 0; takt < bass.length; takt++) {
            noten.add(new int[]{takt * 40, bass[takt]});
            noten.add(new int[]{takt * 40 + 20, bass[takt] + 7});
        }
        noten.sort((a, b) -> Integer.compare(a[0], b[0]));
        return noten.toArray(new int[0][]);
    }

    /** Kanon in D (Pachelbel): Bassfolge mit zwei Melodiezeilen, jede Note eine Sekunde, gebrochene Akkorde dazu. */
    private static int[][] kanonInD() {
        int[] bass = {50, 45, 47, 42, 43, 38, 43, 45};
        int[][] akkorde = {{62, 66, 69}, {61, 64, 69}, {59, 62, 66}, {57, 61, 66}, {55, 59, 62}, {54, 57, 62}, {55, 59, 62}, {57, 61, 64}};
        int[] zeile1 = {78, 76, 74, 73, 71, 69, 71, 73};
        int[] zeile2 = {74, 73, 71, 69, 67, 66, 67, 64};
        List<int[]> noten = new ArrayList<>();
        for (int runde = 0; runde < 2; runde++) {
            int[] melodie = runde == 0 ? zeile1 : zeile2;
            for (int i = 0; i < bass.length; i++) {
                int t = (runde * bass.length + i) * 20;
                noten.add(new int[]{t, bass[i]});
                noten.add(new int[]{t, melodie[i]});
                for (int j = 0; j < 3; j++) {
                    noten.add(new int[]{t + 5 + j * 5, akkorde[i][j]});
                }
            }
        }
        noten.add(new int[]{bass.length * 2 * 20, 50});
        noten.add(new int[]{bass.length * 2 * 20, 62});
        noten.add(new int[]{bass.length * 2 * 20, 74});
        noten.sort((a, b) -> Integer.compare(a[0], b[0]));
        return noten.toArray(new int[0][]);
    }
}
