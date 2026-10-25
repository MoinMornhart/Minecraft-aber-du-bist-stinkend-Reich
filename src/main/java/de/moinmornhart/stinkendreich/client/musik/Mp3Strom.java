package de.moinmornhart.stinkendreich.client.musik;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.decoder.SampleBuffer;
import net.minecraft.client.sounds.AudioStream;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;

/**
 * MP3-Datei als Tonstrom für Minecraft, dekodiert mit JLayer (reines Java). Liefert 16-Bit-PCM
 * in der Byte-Reihenfolge des Rechners, wie OpenAL es erwartet.
 */
final class Mp3Strom implements AudioStream {
    private final InputStream eingabe;
    private final Bitstream bits;
    private final Decoder decoder = new Decoder();
    private final AudioFormat format;
    private short[] puffer;
    private int pos;
    private int laenge;

    Mp3Strom(Path datei) throws IOException {
        eingabe = MusikDateien.oeffnen(datei);
        bits = new Bitstream(eingabe);
        // Das erste Frame verrät Abtastrate und Kanäle
        if (!naechstesFrame()) {
            close();
            throw new IOException("keine MP3-Daten");
        }
        format = new AudioFormat(decoder.getOutputFrequency(), 16, decoder.getOutputChannels(), true,
                ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN);
    }

    /** Länge in Sekunden: alle Frame-Köpfe durchgehen, ohne zu dekodieren. */
    static double sekunden(Path datei) throws IOException {
        try (InputStream in = MusikDateien.oeffnen(datei)) {
            Bitstream b = new Bitstream(in);
            double ms = 0;
            Header h;
            while ((h = b.readFrame()) != null) {
                ms += h.ms_per_frame();
                b.closeFrame();
            }
            return ms / 1000.0;
        } catch (BitstreamException e) {
            throw new IOException(e);
        }
    }

    /** Springt ungefähr {@code sekunden} vor, indem ganze Frames übersprungen werden. */
    void ueberspringen(double sekunden) throws IOException {
        double ms = sekunden * 1000.0;
        try {
            Header h;
            while (ms > 0 && (h = bits.readFrame()) != null) {
                ms -= h.ms_per_frame();
                bits.closeFrame();
            }
        } catch (BitstreamException e) {
            throw new IOException(e);
        }
        // Rest des zuvor dekodierten Frames verwerfen und neu ansetzen
        laenge = 0;
        naechstesFrame();
    }

    private boolean naechstesFrame() throws IOException {
        try {
            while (true) {
                Header h = bits.readFrame();
                if (h == null) {
                    return false;
                }
                try {
                    SampleBuffer ausgabe = (SampleBuffer) decoder.decodeFrame(h, bits);
                    puffer = ausgabe.getBuffer();
                    laenge = ausgabe.getBufferLength();
                    pos = 0;
                } finally {
                    bits.closeFrame();
                }
                if (laenge > 0) {
                    return true;
                }
            }
        } catch (JavaLayerException e) {
            throw new IOException(e);
        }
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    @Override
    public ByteBuffer read(int groesse) throws IOException {
        int samples = Math.max(2, groesse / 2);
        ByteBuffer aus = ByteBuffer.allocateDirect(samples * 2).order(ByteOrder.nativeOrder());
        while (aus.remaining() >= 2) {
            if (pos >= laenge && !naechstesFrame()) {
                break;
            }
            while (pos < laenge && aus.remaining() >= 2) {
                aus.putShort(puffer[pos++]);
            }
        }
        aus.flip();
        // null = Ende des Stroms (so versteht es Minecraft)
        return aus.hasRemaining() ? aus : null;
    }

    @Override
    public void close() throws IOException {
        try {
            bits.close();
        } catch (BitstreamException e) {
            throw new IOException(e);
        } finally {
            eingabe.close();
        }
    }
}
