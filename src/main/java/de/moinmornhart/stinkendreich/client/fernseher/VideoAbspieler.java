package de.moinmornhart.stinkendreich.client.fernseher;

import com.mojang.blaze3d.platform.NativeImage;
import de.moinmornhart.stinkendreich.StinkendReich;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.resources.Identifier;
//? if >=1.21.5
import org.lwjgl.system.MemoryUtil;

import javax.sound.sampled.AudioFormat;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Spielt ein Video auf einem Fernseher ab. Zwei FFmpeg-Prozesse lesen ab der gewünschten Stelle: einer liefert
 * Einzelbilder (640 × 360, 30 pro Sekunde, RGBA), der andere den Ton (Mono, 48 kHz). Die Bilder landen in der
 * Bildschirmfläche einer eigenen Fernseher-Textur (8-fache Auflösung der Modell-Abwicklung, Bildschirm bei 16/16),
 * der Ton geht als Strom an Minecraft ({@link VideoTon}).
 *
 * <p>Gleichlauf: Die Uhr startet, sobald Minecraft den ersten Ton abholt. Ab da gehört zu jeder Sekunde genau ein
 * Stück Ton und das Bild mit der passenden Nummer. Kommt der Ton aus dem Netz zu spät, spielt Minecraft Stille und
 * der Abspieler überspringt später genauso viel, damit Bild und Ton zusammenbleiben. Hält das Spiel an (Esc im
 * Einzelspieler), hält auch die Uhr an.
 */
final class VideoAbspieler implements AutoCloseable {
    enum Zustand { LAEDT, LAEUFT, ANGEHALTEN, ENDE, FEHLER }

    static final int B = 640, H = 360, FPS = 30;
    private static final int TEX_B = 2048, TEX_H = 512, X0 = 16, Y0 = 16;
    private static final int RATE = 48000;
    /** So viel Ton (Bytes) muss da sein, bevor es losgeht: Minecraft holt sich am Anfang gleich 4 Sekunden. */
    private static final int VORLAUF = RATE * 2 * 4 + RATE / 2;
    private static int zaehler;
    /** Nur für Aufnahmen in Zeitlupe (Trailer): so viel schneller oder langsamer läuft die Uhr des Videos. */
    private static final double TEMPO = tempo();

    final int tvId;
    final String kennung;
    /** Stelle im Video (Sekunden), ab der dieser Abspieler liest. */
    final double start;
    private final double x, y, z;
    private volatile Zustand zustand = Zustand.LAEDT;
    private volatile String fehler = "";
    private volatile VideoQuelle quelle;
    private volatile Process bildProzess, tonProzess;
    /** yt-dlp-Prozesse, die FFmpeg die Daten liefern (werden mit beendet). */
    private final List<Process> nebenProzesse = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final ByteArrayOutputStream ffmpegFehler = new ByteArrayOutputStream();
    private final BlockingQueue<Bild> frei = new ArrayBlockingQueue<>(4);
    private final BlockingQueue<Bild> fertig = new ArrayBlockingQueue<>(4);
    private final TonPuffer ton = new TonPuffer(RATE * 2 * 10);
    private final List<Thread> faeden = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile boolean bildEnde, tonEnde, geschlossen;
    // Darstellung und Steuerung: nur im Haupt-/Render-Thread
    private NativeImage nativ;
    private DynamicTexture textur;
    private Identifier id;
    private boolean hatBild;
    private Bild offen;
    private VideoTon klang;
    // Uhr in Nanosekunden: t0 = erster Tonabruf durch Minecraft
    private volatile long t0 = -1;
    private long laeuftSeit = -1;
    /** Wann dieser Abspieler angelegt wurde und wie lange das Laden gedauert hat (Sekunden). */
    private final long angelegt = System.nanoTime();
    private volatile double ladeSekunden;
    private volatile long pauseSeit = -1;
    /** Gemeinsam schauen: fertig laden, aber noch nicht loslaufen (der Server wartet auf die anderen Zuschauer). */
    volatile boolean halten;
    private volatile long pausiert;

    private static final class Bild {
        final byte[] daten = new byte[B * H * 4];
        int nr;
    }

    VideoAbspieler(int tvId, String kennung, double start, double x, double y, double z) {
        this.tvId = tvId;
        this.kennung = kennung;
        this.start = Math.max(0, start);
        this.x = x;
        this.y = y;
        this.z = z;
        for (int i = 0; i < 4; i++) {
            frei.add(new Bild());
        }
        faden(this::vorbereiten, "Vorbereitung");
    }

    Zustand zustand() {
        return zustand;
    }

    /** Fertig zum Start (genug Bild und Ton geladen), läuft schon oder geht nicht – dann nicht länger auf ihn warten. */
    boolean startbereit() {
        if (zustand != Zustand.LAEDT) {
            return true;
        }
        return quelle != null && (ton.menge() >= VORLAUF || tonEnde) && (!fertig.isEmpty() || bildEnde);
    }

    String fehler() {
        return fehler;
    }

    boolean geschlossen() {
        return geschlossen;
    }

    private void faden(Runnable arbeit, String name) {
        Thread t = new Thread(arbeit, "Stinkend Reich Fernseher " + name);
        t.setDaemon(true);
        faeden.add(t);
        t.start();
    }

    // ------------------------------------------------------------------ Hintergrund

    private void vorbereiten() {
        try {
            VideoQuelle q = VideoQuelle.holen(kennung);
            if (geschlossen) {
                return;
            }
            quelle = q;
            if (q.dauer() > 0 && start >= q.dauer() - 1) {
                zustand = Zustand.ENDE;
                return;
            }
            double ab = q.live() ? 0 : start;
            bildProzess = starten(q, q.bild(), ab, true);
            tonProzess = starten(q, q.ton(), ab, false);
            VideoQuelle.mitlesen(bildProzess.getErrorStream(), ffmpegFehler);
            VideoQuelle.mitlesen(tonProzess.getErrorStream(), ffmpegFehler);
            if (geschlossen) {
                prozesseBeenden();
                return;
            }
            faden(this::bilderLesen, "Bild");
            faden(this::tonLesen, "Ton");
        } catch (Exception e) {
            if (!geschlossen) {
                fehler = e.getMessage() == null ? e.toString() : e.getMessage();
                zustand = Zustand.FEHLER;
                StinkendReich.LOGGER.warn("Fernseher: Video {} nicht abspielbar ({})", kennung, fehler);
            }
        }
    }

    /**
     * Startet FFmpeg für eine Spur. Bei YouTube holt yt-dlp die Daten und reicht sie über eine Röhre an FFmpeg weiter
     * (FFmpeg allein bekommt von YouTube oft „403 Forbidden“); zurück kommt der FFmpeg-Prozess.
     */
    private Process starten(VideoQuelle q, String eingabe, double ab, boolean bild) throws IOException {
        if (!q.ueberYtdlp(bild)) {
            return ffmpeg(q, eingabe, ab, bild).start();
        }
        ProcessBuilder laden = q.ytdlpSpur(bild);
        ProcessBuilder umwandeln = ffmpeg(q, "pipe:0", ab, bild);
        List<Process> kette = ProcessBuilder.startPipeline(List.of(laden, umwandeln));
        nebenProzesse.add(kette.get(0));
        // Was yt-dlp meldet (z. B. „Sign in to confirm you're not a bot“), landet mit in der Fehlermeldung
        VideoQuelle.mitlesen(kette.get(0).getErrorStream(), ffmpegFehler);
        return kette.get(1);
    }

    /** FFmpeg-Aufruf für die Bild- oder Tonspur ab einer Stelle. */
    private static ProcessBuilder ffmpeg(VideoQuelle q, String eingabe, double ab, boolean bild) throws IOException {
        Path ffmpeg = VideoWerkzeuge.finden(VideoWerkzeuge.Programm.FFMPEG);
        if (ffmpeg == null) {
            throw new IOException("FFmpeg fehlt");
        }
        List<String> b = new ArrayList<>(List.of(ffmpeg.toString(), "-nostdin", "-hide_banner", "-loglevel", "error"));
        if (eingabe.startsWith("http")) {
            b.addAll(List.of("-reconnect", "1", "-reconnect_streamed", "1", "-reconnect_delay_max", "5"));
            if (!q.agent().isEmpty()) {
                b.addAll(List.of("-user_agent", q.agent()));
            }
        }
        if (ab > 0.05) {
            b.addAll(List.of("-ss", String.format(Locale.ROOT, "%.3f", ab)));
        }
        b.addAll(List.of("-i", eingabe));
        if (bild) {
            b.addAll(List.of("-an", "-sn", "-vf",
                    "scale=" + B + ":" + H + ":force_original_aspect_ratio=decrease,pad=" + B + ":" + H
                            + ":(ow-iw)/2:(oh-ih)/2:color=black,fps=" + FPS,
                    "-pix_fmt", "rgba", "-f", "rawvideo", "pipe:1"));
        } else {
            b.addAll(List.of("-vn", "-sn", "-ac", "1", "-ar", String.valueOf(RATE), "-f", "s16le", "pipe:1"));
        }
        return new ProcessBuilder(b);
    }

    private void bilderLesen() {
        try (InputStream rein = new BufferedInputStream(bildProzess.getInputStream(), 1 << 20)) {
            int nr = 0;
            while (!geschlossen) {
                Bild b = frei.take();
                if (!fuellen(rein, b.daten)) {
                    frei.offer(b);
                    break;
                }
                b.nr = nr++;
                // Schon überfällig (das Spiel zeichnet langsamer, als das Video läuft)? Dann gleich verwerfen,
                // sonst hinge das Bild immer weiter hinterher
                if (zustand == Zustand.LAEUFT && b.nr < (int) Math.floor(uhr() * FPS) - 1) {
                    frei.offer(b);
                    continue;
                }
                fertig.put(b);
            }
        } catch (IOException | InterruptedException beendet) {
            // Prozess beendet oder Abspieler geschlossen
        } finally {
            bildEnde = true;
        }
    }

    private static boolean fuellen(InputStream rein, byte[] ziel) throws IOException {
        int pos = 0;
        while (pos < ziel.length) {
            int n = rein.read(ziel, pos, ziel.length - pos);
            if (n < 0) {
                return false;
            }
            pos += n;
        }
        return true;
    }

    private void tonLesen() {
        try (InputStream rein = tonProzess.getInputStream()) {
            byte[] p = new byte[8192];
            int n;
            while (!geschlossen && (n = rein.read(p)) > 0) {
                ton.schreiben(p, 0, n);
            }
        } catch (IOException | InterruptedException beendet) {
            // Prozess beendet oder Abspieler geschlossen
        } finally {
            tonEnde = true;
        }
    }

    // ------------------------------------------------------------------ Ton für Minecraft

    /** Tonstrom für {@link VideoTon}: liefert immer volle Stücke (fehlender Ton wird zu Stille und später übersprungen). */
    AudioStream tonStrom() {
        return new AudioStream() {
            private final AudioFormat format = new AudioFormat(RATE, 16, 1, true, false);
            private long schuld;

            @Override
            public AudioFormat getFormat() {
                return format;
            }

            @Override
            public ByteBuffer read(int groesse) {
                if (t0 < 0) {
                    t0 = System.nanoTime();
                }
                int n = Math.max(2, groesse & ~1);
                if (schuld > 0) {
                    schuld -= ton.verwerfen((int) Math.min(schuld, ton.menge() & ~1));
                }
                byte[] roh = new byte[n];
                int gelesen = ton.lesen(roh, 0, Math.min(n, ton.menge() & ~1));
                if (gelesen == 0 && (tonEnde || geschlossen)) {
                    return null;    // Ende des Videos: Minecraft beendet den Klang
                }
                if (gelesen < n && !tonEnde && !geschlossen) {
                    schuld += n - gelesen;  // Lücke mit Stille füllen (roh ist schon 0)
                    gelesen = n;
                }
                ByteBuffer aus = ByteBuffer.allocateDirect(gelesen);
                aus.put(roh, 0, gelesen);
                aus.flip();
                return aus;
            }

            @Override
            public void close() {
                // Die Prozesse beendet der Abspieler selbst
            }
        };
    }

    // ------------------------------------------------------------------ Haupt-Thread

    /** Jeden Client-Tick: Start, sobald genug Bild und Ton da sind; fällige Bilder vormerken; Pause des Spiels. */
    void tick(boolean spielPausiert) {
        long jetzt = System.nanoTime();
        if (spielPausiert && pauseSeit < 0) {
            pauseSeit = jetzt;
        } else if (!spielPausiert && pauseSeit >= 0) {
            pausiert += jetzt - pauseSeit;
            pauseSeit = -1;
        }
        if (zustand == Zustand.LAEDT && quelle != null && !spielPausiert && !geschlossen && !halten) {
            boolean tonDa = ton.menge() >= VORLAUF || tonEnde;
            boolean bildDa = !fertig.isEmpty() || bildEnde;
            if (tonDa && bildDa) {
                if (bildEnde && fertig.isEmpty() && tonEnde && ton.menge() == 0) {
                    fehler = "FFmpeg: " + VideoQuelle.kurz(ffmpegText());
                    zustand = Zustand.FEHLER;
                    StinkendReich.LOGGER.warn("Fernseher: {}", fehler);
                    return;
                }
                zustand = Zustand.LAEUFT;
                laeuftSeit = jetzt;
                ladeSekunden = (System.nanoTime() - angelegt) / 1e9;
                pausiert = 0;
                if (ton.menge() > 0) {
                    klang = new VideoTon(this, x, y, z);
                    Minecraft.getInstance().getSoundManager().play(klang);
                }
            }
        }
        if (zustand == Zustand.LAEUFT) {
            // Ohne Ton (keine Soundkarte, Kanäle voll) startet die Uhr trotzdem
            if (t0 < 0 && jetzt - laeuftSeit > 1_500_000_000L) {
                t0 = laeuftSeit;
            }
            aufholen();
            // Letztes Bild gezeigt und aller Ton abgegeben: Ende (der Klang spielt seinen Rest selbst zu Ende)
            if (bildEnde && fertig.isEmpty() && offen == null && (tonEnde || klang == null) && ton.menge() == 0) {
                zustand = Zustand.ENDE;
            }
        }
    }

    private String ffmpegText() {
        synchronized (ffmpegFehler) {
            return ffmpegFehler.toString(StandardCharsets.UTF_8);
        }
    }

    /** Sekunden seit dem Start des Tons, ohne Spielpausen. */
    private double uhr() {
        if (t0 < 0) {
            return 0;
        }
        long jetzt = System.nanoTime();
        long pause = pausiert + (pauseSeit >= 0 ? jetzt - pauseSeit : 0);
        return Math.max(0, (jetzt - t0 - pause) / 1e9 * TEMPO);
    }

    private static double tempo() {
        try {
            String t = System.getenv("STINKENDREICH_VIDEOTEMPO");
            return t == null || t.isBlank() ? 1.0 : Math.max(0.05, Math.min(4.0, Double.parseDouble(t.trim())));
        } catch (NumberFormatException e) {
            return 1.0;
        }
    }

    /** Aktuelle Stelle im Video in Sekunden. */
    double position() {
        return start + uhr();
    }

    /** Muss für die Stelle {@code soll} (Sekunden, vom Server) neu gestartet werden? */
    boolean mussNeuStarten(double soll) {
        VideoQuelle q = quelle;
        return switch (zustand) {
            // Nach dem Laden liegt der Fernseher um die Ladezeit hinter dem Server: das ist kein Grund für einen
            // Neustart (sonst lädt er neu, liegt wieder zurück, lädt neu … und zeigt nur „Video wird geladen“).
            // Neu gestartet wird nur beim Spulen oder wenn das Bild weit vorausläuft.
            case LAEUFT -> q != null && !q.live() && (soll - position() > 8 + Math.min(60, ladeSekunden) || position() - soll > 8);
            case ENDE -> q != null && q.dauer() > 0 && soll < q.dauer() - 3;
            case ANGEHALTEN -> true;
            default -> false;
        };
    }

    /** Holt das neueste fällige Bild aus der Warteschlange; ältere wandern zurück in den Vorrat. */
    private void aufholen() {
        int ziel = (int) Math.floor(uhr() * FPS);
        Bild k;
        while ((k = fertig.peek()) != null && k.nr <= ziel) {
            fertig.poll();
            if (offen != null) {
                frei.offer(offen);
            }
            offen = k;
        }
    }

    /** Textur mit dem aktuellen Bild, oder null (dann zeigt der Fernseher sein Vorschaubild). */
    Identifier textur() {
        if (geschlossen || zustand == Zustand.FEHLER || zustand == Zustand.ENDE) {
            return null;
        }
        if (zustand == Zustand.LAEUFT) {
            aufholen();
        }
        if (offen != null) {
            zeigen(offen.daten);
            frei.offer(offen);
            offen = null;
            hatBild = true;
        }
        return hatBild ? id : null;
    }

    private void anlegen() {
        nativ = new NativeImage(TEX_B, TEX_H, false);
        for (int py = 0; py < TEX_H; py++) {
            for (int px = 0; px < TEX_B; px++) {
                punkt(px, py, 0xFF0A0A0C);
            }
        }
        id = StinkendReich.id("fernseher_video/" + (++zaehler));
        //? if >=1.21.5 {
        textur = new DynamicTexture(() -> "Fernseher-Video " + kennung, nativ);
        //?} else
        /*textur = new DynamicTexture(nativ);*/
        Minecraft.getInstance().getTextureManager().register(id, textur);
    }

    /** Setzt einen Bildpunkt (Farbe als ARGB). */
    private void punkt(int px, int py, int argb) {
        //? if >=1.21.2 {
        nativ.setPixel(px, py, argb);
        //?} else
        /*nativ.setPixelRGBA(px, py, (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16));*/
    }

    /** Kopiert ein Videobild (RGBA, Zeile für Zeile) in die Bildschirmfläche der Textur und lädt sie hoch. */
    private void zeigen(byte[] d) {
        if (nativ == null) {
            anlegen();
        }
        //? if >=1.21.5 {
        // Die Textur liegt im Speicher genau so (RGBA) wie das Bild von FFmpeg: zeilenweise kopieren
        long zeiger = nativ.getPointer();
        for (int r = 0; r < H; r++) {
            MemoryUtil.memByteBuffer(zeiger + ((long) (Y0 + r) * TEX_B + X0) * 4L, B * 4).put(d, r * B * 4, B * 4);
        }
        //?} else {
        /*for (int r = 0, i = 0; r < H; r++) {
            for (int s = 0; s < B; s++, i += 4) {
                punkt(X0 + s, Y0 + r, 0xFF000000 | (d[i] & 0xFF) << 16 | (d[i + 1] & 0xFF) << 8 | (d[i + 2] & 0xFF));
            }
        }
        *///?}
        textur.upload();
    }

    // ------------------------------------------------------------------ Ende

    /** Hält an: Prozesse und Ton aus, das letzte Bild bleibt stehen. */
    void anhalten() {
        if (zustand == Zustand.ANGEHALTEN || geschlossen) {
            return;
        }
        zustand = Zustand.ANGEHALTEN;
        prozesseBeenden();
    }

    private void prozesseBeenden() {
        ton.schliessen();
        for (Process p : new Process[]{bildProzess, tonProzess}) {
            if (p != null) {
                p.destroyForcibly();
            }
        }
        nebenProzesse.forEach(Process::destroyForcibly);
        nebenProzesse.clear();
        for (Thread t : faeden) {
            if (t != Thread.currentThread()) {
                t.interrupt();
            }
        }
        if (klang != null) {
            Minecraft.getInstance().getSoundManager().stop(klang);
            klang.beenden();
            klang = null;
        }
    }

    @Override
    public void close() {
        if (geschlossen) {
            return;
        }
        geschlossen = true;
        prozesseBeenden();
        if (id != null) {
            Minecraft.getInstance().getTextureManager().release(id);
        }
        nativ = null;
        textur = null;
        id = null;
    }

    /** Ringpuffer für den Ton: der Lese-Thread schreibt (wartet, wenn voll), Minecraft liest (wartet nie). */
    private static final class TonPuffer {
        private final byte[] daten;
        private int lesePos;
        private int menge;
        private boolean zu;

        TonPuffer(int groesse) {
            daten = new byte[groesse];
        }

        synchronized void schreiben(byte[] quelle, int off, int n) throws InterruptedException {
            while (n > 0) {
                while (menge == daten.length && !zu) {
                    wait();
                }
                if (zu) {
                    return;
                }
                int schreibPos = (lesePos + menge) % daten.length;
                int stueck = Math.min(n, Math.min(daten.length - menge, daten.length - schreibPos));
                System.arraycopy(quelle, off, daten, schreibPos, stueck);
                menge += stueck;
                off += stueck;
                n -= stueck;
            }
        }

        synchronized int lesen(byte[] ziel, int off, int n) {
            int gelesen = 0;
            while (n > 0 && menge > 0) {
                int stueck = Math.min(n, Math.min(menge, daten.length - lesePos));
                System.arraycopy(daten, lesePos, ziel, off, stueck);
                lesePos = (lesePos + stueck) % daten.length;
                menge -= stueck;
                off += stueck;
                n -= stueck;
                gelesen += stueck;
            }
            if (gelesen > 0) {
                notifyAll();
            }
            return gelesen;
        }

        synchronized int verwerfen(int n) {
            int s = Math.max(0, Math.min(n, menge));
            lesePos = (lesePos + s) % daten.length;
            menge -= s;
            if (s > 0) {
                notifyAll();
            }
            return s;
        }

        synchronized int menge() {
            return menge;
        }

        synchronized void schliessen() {
            zu = true;
            notifyAll();
        }
    }
}
