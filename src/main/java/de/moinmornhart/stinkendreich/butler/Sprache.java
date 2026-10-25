package de.moinmornhart.stinkendreich.butler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Versteht einfache Aufträge auf Deutsch, z. B. „Hol mir 32 Eisen“, „Fäll 20 Holz und leg es in
 * die Truhe“, „Sortier die Truhen“, „Folge mir“. Ohne Claude ist das die Sprachsteuerung des
 * Butlers; mit Claude (API-Key in der Config) versteht er auch freie Sätze.
 */
public final class Sprache {
    public enum Art { HOLEN, KOCHEN, SORTIEREN, FOLGEN, BLEIBEN, HEIM, KOMMEN, STOPP, INVENTAR, STATUS, HILFE,
        FAHREN, ORT_MERKEN, ORTE, UNBEKANNT }

    /** Ziele beim Fahren (in {@link Befehl#quelle()}): Villa, Blickziel, nur vorfahren; sonst Name eines Ortes. */
    public static final String ZIEL_VILLA = "#villa", ZIEL_BLICK = "#blick", ZIEL_ABHOLEN = "#abholen";

    /**
     * Ein verstandener Auftrag. {@code menge} ist -1, wenn keine Zahl genannt wurde. Beim Fahren und Ortemerken
     * steht in {@code quelle} das Ziel bzw. der Name des Ortes.
     */
    public record Befehl(Art art, String quelle, int menge, boolean inTruhe) {
        public static Befehl von(Art art) {
            return new Befehl(art, null, -1, false);
        }
    }

    /** Wörter (normalisiert, ohne Umlaute) → Quelle. Längere Wendungen haben Vorrang. */
    private static final Map<String, String> WOERTER = new LinkedHashMap<>();
    private static final Map<String, Integer> ZAHLWOERTER = Map.ofEntries(
            Map.entry("ein", 1), Map.entry("eine", 1), Map.entry("einen", 1), Map.entry("einem", 1), Map.entry("eins", 1),
            Map.entry("zwei", 2), Map.entry("drei", 3), Map.entry("vier", 4), Map.entry("fuenf", 5), Map.entry("sechs", 6),
            Map.entry("sieben", 7), Map.entry("acht", 8), Map.entry("neun", 9), Map.entry("zehn", 10), Map.entry("elf", 11),
            Map.entry("zwoelf", 12), Map.entry("zwanzig", 20), Map.entry("dreissig", 30), Map.entry("vierzig", 40),
            Map.entry("fuenfzig", 50), Map.entry("sechzig", 60), Map.entry("hundert", 100), Map.entry("paar", 8));
    private static final Pattern ZAHL = Pattern.compile("\\b(\\d{1,4})\\b");

    static {
        wort("rohes_eisen", "rohes eisen", "roheisen", "eisenerz", "eisen erz", "ungeschmolzenes eisen");
        wort("rohes_gold", "rohes gold", "rohgold", "golderz", "ungeschmolzenes gold");
        wort("rohes_kupfer", "rohes kupfer", "rohkupfer", "kupfererz");
        wort("eisenbarren", "eisenbarren", "eisen", "barren");
        wort("goldbarren", "goldbarren", "gold");
        wort("kupferbarren", "kupferbarren", "kupfer");
        wort("holzkohle", "holzkohle");
        wort("kohle", "kohle", "kohlen", "steinkohle");
        wort("diamant", "diamanten", "diamant", "dias");
        wort("smaragd", "smaragde", "smaragd");
        wort("redstone", "redstone", "rotstein");
        wort("lapis", "lapislazuli", "lapis");
        wort("quarz", "quarz");
        wort("bruchstein", "bruchstein", "pflasterstein", "cobblestone", "cobble");
        wort("stein", "glatter stein", "steine", "stein");
        wort("tiefenschiefer", "tiefenschiefer", "deepslate");
        wort("erde", "erde", "dreck");
        wort("glas", "glas");
        wort("sand", "sand");
        wort("kies", "kies");
        wort("ziegel", "ziegelsteine", "ziegel");
        wort("ton", "tonklumpen", "ton", "lehm");
        wort("bretter", "holzbretter", "bretter", "planken");
        wort("stoecke", "stoecke", "stock", "staebe");
        wort("fackeln", "fackeln", "fackel");
        wort("werkbank", "werkbaenke", "werkbank");
        wort("truhe", "truhen", "truhe", "kisten", "kiste");
        wort("ofen", "oefen", "ofen");
        wort("leiter", "leitern", "leiter");
        wort("eimer", "eimer");
        wort("eisenblock", "eisenbloecke", "eisenblock");
        wort("goldblock", "goldbloecke", "goldblock");
        wort("diamantblock", "diamantbloecke", "diamantblock");
        wort("smaragdblock", "smaragdblock");
        wort("eisenspitzhacke", "eisenspitzhacke", "eisenhacke", "spitzhacke");
        wort("diamantspitzhacke", "diamantspitzhacke", "diamanthacke");
        wort("eisenschwert", "eisenschwert", "schwert");
        wort("diamantschwert", "diamantschwert");
        wort("eisenaxt", "eisenaxt", "axt");
        wort("schienen", "schienen");
        wort("brot", "brot");
        wort("rohes_fleisch", "rohes fleisch", "rohfleisch");
        wort("essen", "essen", "nahrung", "mahlzeit", "fleisch", "futter", "proviant", "was zu essen");
        wort("holz", "holzstaemme", "baumstaemme", "staemme", "stamm", "holz", "baeume");
    }

    private Sprache() {
    }

    private static void wort(String quelle, String... woerter) {
        for (String w : woerter) {
            WOERTER.put(w, quelle);
        }
    }

    /** Kleinbuchstaben, Umlaute ausgeschrieben, Satzzeichen weg. */
    public static String normalisieren(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
                .replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    public static Befehl verstehen(String eingabe) {
        String t = " " + normalisieren(eingabe) + " ";
        boolean inTruhe = false;
        for (String ziel : List.of(" in die truhe", " in die kiste", " in der truhe", " in truhe", " in die lieferkiste",
                " ins lager", " einlagern", " in den vorrat", " in die truhen")) {
            if (t.contains(ziel)) {
                inTruhe = true;
                t = t.replace(ziel, " ");
            }
        }
        if (enthaelt(t, " stopp ", " stop ", " halt ", "abbrechen", "hoer auf", "lass das", "lass es", "vergiss es", "aufhoeren")) {
            return Befehl.von(Art.STOPP);
        }
        if (enthaelt(t, "hilfe", "was kannst du", "befehle")) {
            return Befehl.von(Art.HILFE);
        }
        if (enthaelt(t, "inventar", "taschen", "was hast du")) {
            return Befehl.von(Art.INVENTAR);
        }
        if (enthaelt(t, "status", "was machst du", "wie weit", "wie lange")) {
            return Befehl.von(Art.STATUS);
        }
        if (enthaelt(t, "sortier", "aufraeum", "raeum", "ordne", "ordnung")) {
            return Befehl.von(Art.SORTIEREN);
        }
        // Chauffeur: Orte merken und auflisten, fahren
        if (enthaelt(t, "merk dir", "merke dir", "ort merken", "ort speichern", "speicher den ort", "speichere den ort",
                "speicher diesen ort", "speichere diesen ort")) {
            return new Befehl(Art.ORT_MERKEN, ortsname(t), -1, false);
        }
        if (enthaelt(t, " welche orte", " orte ", "gespeicherte orte", "deine orte")) {
            return Befehl.von(Art.ORTE);
        }
        if (enthaelt(t, "hol den wagen", "hol das auto", "hol die limo", "hol mein auto", "hol meinen wagen", "vorfahren",
                "wagen holen", "auto holen", "limo holen")) {
            return new Befehl(Art.FAHREN, ZIEL_ABHOLEN, -1, false);
        }
        if (enthaelt(t, " fahr", "chauffier", "kutschier", "bring mich")) {
            String ziel;
            if (enthaelt(t, "nach hause", " heim", "zur villa", "zu hause", "zurueck")) {
                ziel = ZIEL_VILLA;
            } else if (enthaelt(t, " dahin", " dorthin", " da hin", " dort hin", " hierhin", " wo ich hin", " dahinten", " da vorne")) {
                ziel = ZIEL_BLICK;
            } else {
                String name = ortsname(t);
                ziel = name.isEmpty() ? ZIEL_BLICK : name;
            }
            return new Befehl(Art.FAHREN, ziel, -1, false);
        }
        int menge = menge(t);
        if (enthaelt(t, "koch", " brat", "grill", " jag")) {
            return new Befehl(Art.KOCHEN, "essen", menge, inTruhe);
        }
        String quelle = quelle(t);
        if (quelle != null) {
            // „geschmolzen“/„roh“ ändern die Bedeutung von Metallen
            boolean roh = enthaelt(t, " roh", "ungeschmolzen", " erz");
            if (roh && quelle.endsWith("barren")) {
                quelle = "rohes_" + quelle.replace("barren", "");
            }
            // „… nach Hause bringen“ heißt: in die Lieferkiste
            return new Befehl(Art.HOLEN, quelle, menge, inTruhe || enthaelt(t, "nach hause", "zu hause"));
        }
        if (enthaelt(t, "nach hause", " heim", "geh zurueck", "zuhause", "zu hause")) {
            return Befehl.von(Art.HEIM);
        }
        if (enthaelt(t, "folg", "komm mit", "begleit", "mitkommen")) {
            return Befehl.von(Art.FOLGEN);
        }
        if (enthaelt(t, "komm", "hierher", "zu mir")) {
            return Befehl.von(Art.KOMMEN);
        }
        if (enthaelt(t, "bleib", " warte", " steh ", " stehen")) {
            return Befehl.von(Art.BLEIBEN);
        }
        return Befehl.von(Art.UNBEKANNT);
    }

    /** Name eines Ortes: was nach „als“, „zu“, „zum“, „nach“ … steht (ohne Artikel und Füllwörter). */
    private static String ortsname(String text) {
        String t = " " + text.trim() + " ";
        int start = -1;
        for (String w : List.of(" als ", " zum ", " zur ", " zu ", " nach ", " ins ", " in die ", " in den ", " in das ", " namens ", " ort ")) {
            int i = t.lastIndexOf(w);
            if (i >= 0 && i + w.length() > start) {
                start = i + w.length();
            }
        }
        if (start < 0) {
            return "";
        }
        String rest = t.substring(start).trim();
        for (String f : List.of("bitte", "mal", "jetzt", "sofort", "dem ", "der ", "die ", "das ", "den ", "meinem ", "meiner ", "meinen ")) {
            rest = rest.replaceAll("(^| )" + f.trim() + "( |$)", " ").trim();
        }
        return rest;
    }

    private static boolean enthaelt(String text, String... woerter) {
        for (String w : woerter) {
            if (text.contains(w)) {
                return true;
            }
        }
        return false;
    }

    /** Längstes bekanntes Wort im Text (ganze Wörter). */
    private static String quelle(String text) {
        List<String> kandidaten = new ArrayList<>(WOERTER.keySet());
        kandidaten.sort(Comparator.comparingInt(String::length).reversed());
        for (String w : kandidaten) {
            if (text.contains(" " + w + " ")) {
                return WOERTER.get(w);
            }
        }
        return null;
    }

    /** Erste Zahl im Text (Ziffern oder Zahlwort), „Stapel“ zählt 64. -1 = keine Angabe. */
    private static int menge(String text) {
        int n = -1;
        Matcher m = ZAHL.matcher(text);
        if (m.find()) {
            n = Integer.parseInt(m.group(1));
        } else {
            for (String w : text.trim().split(" ")) {
                Integer z = ZAHLWOERTER.get(w);
                if (z != null) {
                    n = z;
                    break;
                }
            }
        }
        if (text.contains(" stapel") || text.contains(" stack")) {
            n = (n <= 0 ? 1 : n) * 64;
        }
        return n;
    }

    /** Übliche Menge, wenn keine Zahl genannt wurde. */
    public static int standardMenge(String quelle) {
        Quellen.Quelle q = Quellen.get(quelle);
        if (q == null) {
            return 16;
        }
        if (q.weg() == Quellen.Weg.HERSTELLEN && q.anzeige().getDefaultMaxStackSize() < 64) {
            return 1;
        }
        if (q.anzeige().getDefaultMaxStackSize() == 64 && q.weg() == Quellen.Weg.HERSTELLEN && q.menge() == 1) {
            return 1; // Werkbank, Truhe, Ofen, Blöcke
        }
        if (quelle.equals("essen")) {
            return 8;
        }
        return 16;
    }
}
