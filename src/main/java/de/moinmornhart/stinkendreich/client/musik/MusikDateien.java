package de.moinmornhart.stinkendreich.client.musik;

import de.moinmornhart.stinkendreich.Konfig;
import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.musik.MusikanlageBlockEntity.Wiederholen;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

/**
 * Der Musikordner des Spielers ({@code <Minecraft-Ordner>/stinkendreich/musik/}): Liste der Titel,
 * Länge eines Titels und die Wahl des nächsten Titels (Zufall, Wiederholen).
 */
public final class MusikDateien {
    /** MP3 geht nur, wenn die eingebettete JLayer-Bibliothek da ist (sonst nur OGG). */
    public static final boolean MP3 = klasseDa("javazoom.jl.decoder.Decoder");

    private static List<String> liste = List.of();
    private static long stand;

    private MusikDateien() {
    }

    public static Path ordner() {
        return FabricLoader.getInstance().getGameDir().resolve(Konfig.get().musikOrdner).normalize();
    }

    /** Dateinamen im Musikordner, alphabetisch; höchstens alle zwei Sekunden neu gelesen. */
    public static synchronized List<String> liste() {
        long jetzt = System.currentTimeMillis();
        if (jetzt - stand < 2000) {
            return liste;
        }
        stand = jetzt;
        Path ordner = ordner();
        try {
            Files.createDirectories(ordner);
            try (Stream<Path> dateien = Files.list(ordner)) {
                liste = dateien.filter(Files::isRegularFile)
                        .map(p -> p.getFileName().toString())
                        .filter(MusikDateien::abspielbar)
                        .sorted(Comparator.comparing(n -> n.toLowerCase(Locale.ROOT)))
                        .toList();
            }
        } catch (IOException e) {
            StinkendReich.LOGGER.warn("Musikordner {} nicht lesbar: {}", ordner, e.getMessage());
            liste = List.of();
        }
        return liste;
    }

    /** Liste sofort neu lesen (z. B. nach „Ordner öffnen“). */
    public static synchronized void neuLesen() {
        stand = 0;
    }

    private static boolean abspielbar(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".ogg") || (MP3 && n.endsWith(".mp3"));
    }

    /**
     * Sucht einen Titel aus dem Netz im eigenen Ordner. Nur Namen aus der eigenen Liste zählen,
     * so kann ein fremder Titel nie auf eine andere Datei zeigen (z. B. „../geheim.txt“).
     */
    public static Path finde(String titel) {
        return titel != null && liste().contains(titel) ? ordner().resolve(titel) : null;
    }

    /** Titel ohne Dateiendung für die Anzeige. */
    public static String anzeigename(String titel) {
        int punkt = titel.lastIndexOf('.');
        return punkt > 0 ? titel.substring(0, punkt) : titel;
    }

    static boolean istMp3(Path datei) {
        return datei.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mp3");
    }

    /** Länge eines Titels in Ticks (aufgerundet); 0, wenn sie sich nicht bestimmen lässt. */
    public static long dauerTicks(Path datei) {
        try {
            double sekunden = istMp3(datei) ? Mp3Strom.sekunden(datei) : oggSekunden(datei);
            return sekunden > 0 ? (long) Math.ceil(sekunden * 20.0) : 0;
        } catch (Exception e) {
            StinkendReich.LOGGER.warn("Länge von {} unbekannt: {}", datei.getFileName(), e.getMessage());
            return 0;
        }
    }

    /**
     * Länge einer Ogg-Vorbis-Datei ohne Dekodieren: Abtastrate aus dem Vorbis-Kopf, Anzahl der
     * Samples aus der „granule position“ der letzten Ogg-Seite.
     */
    static double oggSekunden(Path datei) throws IOException {
        try (SeekableByteChannel kanal = Files.newByteChannel(datei)) {
            ByteBuffer kopf = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN);
            kanal.read(kopf);
            int rate = 0;
            byte[] k = kopf.array();
            for (int i = 0; i + 16 < kopf.position(); i++) {
                if (k[i] == 1 && new String(k, i + 1, 6, StandardCharsets.US_ASCII).equals("vorbis")) {
                    rate = kopf.getInt(i + 12);
                    break;
                }
            }
            if (rate <= 0) {
                return 0;
            }
            long groesse = kanal.size();
            int n = (int) Math.min(groesse, 65536);
            ByteBuffer ende = ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN);
            kanal.position(groesse - n);
            while (ende.hasRemaining() && kanal.read(ende) > 0) {
                // weiterlesen
            }
            byte[] e = ende.array();
            for (int i = ende.position() - 14; i >= 0; i--) {
                if (e[i] == 'O' && e[i + 1] == 'g' && e[i + 2] == 'g' && e[i + 3] == 'S' && e[i + 4] == 0) {
                    long samples = ende.getLong(i + 6);
                    return samples > 0 ? samples / (double) rate : 0;
                }
            }
            return 0;
        }
    }

    static InputStream oeffnen(Path datei) throws IOException {
        return new BufferedInputStream(Files.newInputStream(datei), 65536);
    }

    /**
     * Nächster Titel in der Liste.
     *
     * @param richtung     +1 = weiter, -1 = zurück, 0 = derselbe
     * @param automatisch  true, wenn der vorige Titel zu Ende ist (dann gilt „Wiederholen“)
     * @return Index des neuen Titels oder -1 = aufhören
     */
    public static int naechster(int anzahl, int aktuell, int richtung, boolean zufall, Wiederholen wiederholen, boolean automatisch) {
        if (anzahl <= 0) {
            return -1;
        }
        if (richtung == 0 || (automatisch && wiederholen == Wiederholen.EINS && aktuell >= 0 && aktuell < anzahl)) {
            return Math.max(0, Math.min(anzahl - 1, aktuell));
        }
        if (zufall && anzahl > 1) {
            int i = ThreadLocalRandom.current().nextInt(anzahl - 1);
            return aktuell >= 0 && i >= aktuell ? i + 1 : i;
        }
        int i = aktuell + richtung;
        if (i >= anzahl) {
            return automatisch && wiederholen == Wiederholen.AUS ? -1 : 0;
        }
        return i < 0 ? anzahl - 1 : i;
    }

    private static boolean klasseDa(String name) {
        try {
            Class.forName(name, false, MusikDateien.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
