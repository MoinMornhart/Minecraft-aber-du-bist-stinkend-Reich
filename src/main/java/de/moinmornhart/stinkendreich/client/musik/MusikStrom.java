package de.moinmornhart.stinkendreich.client.musik;

import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;

/** Öffnet eine Musikdatei als Tonstrom und springt auf Wunsch an eine Stelle im Titel. */
final class MusikStrom {
    private MusikStrom() {
    }

    /**
     * @param ab Stelle im Titel in Sekunden (für Spieler, die später dazukommen)
     */
    static AudioStream oeffnen(Path datei, double ab) throws IOException {
        if (MusikDateien.istMp3(datei)) {
            if (!MusikDateien.MP3) {
                throw new IOException("MP3 wird nicht unterstützt");
            }
            Mp3Strom mp3 = new Mp3Strom(datei);
            if (ab > 0.5) {
                mp3.ueberspringen(ab);
            }
            return mp3;
        }
        InputStream eingabe = MusikDateien.oeffnen(datei);
        AudioStream ogg;
        try {
            // Minecrafts eigener Ogg-Vorbis-Dekoder (derselbe wie für die Spielmusik)
            ogg = new JOrbisAudioStream(eingabe);
        } catch (IOException | RuntimeException e) {
            eingabe.close();
            throw e;
        }
        if (ab > 0.5) {
            ueberspringen(ogg, ab);
        }
        return ogg;
    }

    /** Dekodiert und verwirft Daten bis zur gewünschten Stelle (Ogg-Vorbis dekodiert sehr schnell). */
    private static void ueberspringen(AudioStream strom, double sekunden) throws IOException {
        AudioFormat f = strom.getFormat();
        long rest = (long) (sekunden * f.getSampleRate()) * f.getFrameSize();
        while (rest > 0) {
            ByteBuffer b = strom.read((int) Math.min(rest, 1 << 16));
            if (b == null || !b.hasRemaining()) {
                return;
            }
            rest -= b.remaining();
        }
    }
}
