package de.moinmornhart.stinkendreich.client.fernseher;

import de.moinmornhart.stinkendreich.StinkendReich;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Hilfsprogramme für Videos im Fernseher:
 * <ul>
 *   <li><b>yt-dlp</b> findet zu einem YouTube-Link die Adressen von Bild und Ton,</li>
 *   <li><b>Deno</b> rechnet für yt-dlp die Schutzaufgaben von YouTube aus (ohne geht es oft nicht),</li>
 *   <li><b>FFmpeg</b> macht daraus Einzelbilder und Ton, die Minecraft anzeigen und abspielen kann.</li>
 * </ul>
 * Liegen sie schon auf dem Rechner (im PATH, auf dem Mac auch Homebrew), nimmt die Mod diese. Sonst lädt sie sie
 * nach Zustimmung des Spielers einmalig von den offiziellen GitHub-Seiten nach {@code .minecraft/stinkendreich/werkzeuge}.
 * Das selbst geladene yt-dlp bringt sich einmal pro Spielstart selbst auf den neuesten Stand.
 */
public final class VideoWerkzeuge {
    enum Programm {
        YTDLP("yt-dlp", "yt-dlp"), DENO("deno", "Deno"), FFMPEG("ffmpeg", "FFmpeg");

        final String datei;
        final String anzeige;

        Programm(String datei, String anzeige) {
            this.datei = datei;
            this.anzeige = anzeige;
        }
    }

    private static final HttpClient NETZ = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private static volatile boolean laedt;
    private static volatile String stand = "";
    private static volatile String fehler = "";
    private static boolean aktualisiert;

    private VideoWerkzeuge() {
    }

    static Path ordner() {
        return FabricLoader.getInstance().getGameDir().resolve("stinkendreich").resolve("werkzeuge");
    }

    private static String system() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    }

    static boolean windows() {
        return system().contains("win");
    }

    static boolean mac() {
        return system().contains("mac");
    }

    private static boolean arm() {
        String a = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return a.contains("aarch64") || a.contains("arm64");
    }

    private static String exe(String name) {
        return windows() ? name + ".exe" : name;
    }

    /** Testvideo statt YouTube (nur für den automatischen Test): dann reicht FFmpeg. */
    static String testvideo() {
        String t = System.getenv("STINKENDREICH_TESTVIDEO");
        return t == null ? "" : t.trim();
    }

    /** Pfad des Programms oder null, wenn es fehlt: erst der eigene Ordner, dann der PATH. */
    static Path finden(Programm p) {
        Path eigen = ordner().resolve(exe(p.datei));
        if (Files.isRegularFile(eigen)) {
            return eigen;
        }
        List<String> orte = new ArrayList<>(Arrays.asList(System.getenv().getOrDefault("PATH", "").split(File.pathSeparator)));
        if (mac()) {
            orte.add("/opt/homebrew/bin");
            orte.add("/usr/local/bin");
        }
        for (String ort : orte) {
            try {
                if (ort.isBlank()) {
                    continue;
                }
                Path kandidat = Path.of(ort.replace("\"", "").trim()).resolve(exe(p.datei));
                if (Files.isRegularFile(kandidat) && Files.isExecutable(kandidat)) {
                    return kandidat;
                }
            } catch (RuntimeException ungueltig) {
                // seltsamer Eintrag im PATH
            }
        }
        return null;
    }

    private static volatile boolean bereitGemerkt;
    private static volatile long bereitGeprueft;

    /** Sind alle nötigen Programme da? (Wird jeden Tick gefragt, deshalb nur alle 3 Sekunden wirklich geprüft.) */
    public static boolean bereit() {
        long jetzt = System.nanoTime();
        if (bereitGeprueft != 0 && jetzt - bereitGeprueft < 3_000_000_000L) {
            return bereitGemerkt;
        }
        boolean da = !testvideo().isEmpty() ? finden(Programm.FFMPEG) != null
                : Arrays.stream(Programm.values()).allMatch(p -> finden(p) != null);
        bereitGemerkt = da;
        bereitGeprueft = jetzt;
        return da;
    }

    public static boolean laedt() {
        return laedt;
    }

    /** Fortschritt beim Laden, z. B. „FFmpeg 45 %“. */
    public static String stand() {
        return stand;
    }

    /** Letzter Fehler beim Laden (leer = keiner). */
    public static String fehler() {
        return fehler;
    }

    /** Lädt alle fehlenden Programme im Hintergrund (nur nach Zustimmung des Spielers aufrufen). */
    public static synchronized void laden() {
        if (laedt || bereit()) {
            return;
        }
        laedt = true;
        fehler = "";
        Thread t = new Thread(() -> {
            try {
                for (Programm p : Programm.values()) {
                    if (finden(p) == null) {
                        herunterladen(p);
                    }
                }
                StinkendReich.LOGGER.info("Fernseher: Hilfsprogramme bereit in {}", ordner());
            } catch (Exception e) {
                fehler = e.getMessage() == null ? e.toString() : e.getMessage();
                StinkendReich.LOGGER.warn("Fernseher: Hilfsprogramme nicht geladen ({})", fehler);
            } finally {
                stand = "";
                bereitGeprueft = 0;
                laedt = false;
            }
        }, "Stinkend Reich Werkzeuge");
        t.setDaemon(true);
        t.start();
    }

    /** Download-Adresse für dieses Betriebssystem (null = gibt es nicht zum Herunterladen). */
    private static String adresse(Programm p) {
        return switch (p) {
            case YTDLP -> "https://github.com/yt-dlp/yt-dlp/releases/latest/download/"
                    + (windows() ? "yt-dlp.exe" : mac() ? "yt-dlp_macos" : arm() ? "yt-dlp_linux_aarch64" : "yt-dlp_linux");
            case DENO -> "https://github.com/denoland/deno/releases/latest/download/deno-"
                    + (windows() ? "x86_64-pc-windows-msvc" : (arm() ? "aarch64-" : "x86_64-") + (mac() ? "apple-darwin" : "unknown-linux-gnu"))
                    + ".zip";
            case FFMPEG -> windows() ? "https://github.com/yt-dlp/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip"
                    : mac() ? null
                    : "https://github.com/yt-dlp/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linux" + (arm() ? "arm64" : "64") + "-gpl.tar.xz";
        };
    }

    private static void herunterladen(Programm p) throws IOException, InterruptedException {
        String url = adresse(p);
        if (url == null) {
            throw new IOException(p.anzeige + " fehlt. Bitte selbst installieren, z. B. mit „brew install ffmpeg“.");
        }
        Path ordner = ordner();
        Files.createDirectories(ordner);
        Path roh = ordner.resolve(p.datei + ".download");
        StinkendReich.LOGGER.info("Fernseher: lade {} von {}", p.anzeige, url);
        HttpResponse<InputStream> antwort = NETZ.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(20))
                .header("User-Agent", "StinkendReich-Mod").build(), HttpResponse.BodyHandlers.ofInputStream());
        if (antwort.statusCode() != 200) {
            antwort.body().close();
            throw new IOException(p.anzeige + ": Download fehlgeschlagen (HTTP " + antwort.statusCode() + ")");
        }
        long gesamt = antwort.headers().firstValueAsLong("Content-Length").orElse(-1);
        try (InputStream rein = antwort.body(); OutputStream raus = Files.newOutputStream(roh)) {
            byte[] puffer = new byte[1 << 16];
            long geschafft = 0;
            int n;
            while ((n = rein.read(puffer)) > 0) {
                raus.write(puffer, 0, n);
                geschafft += n;
                stand = gesamt > 0 ? p.anzeige + " " + (geschafft * 100 / gesamt) + " %" : p.anzeige + " " + (geschafft >> 20) + " MB";
            }
        }
        stand = p.anzeige + " wird ausgepackt";
        Path ziel = ordner.resolve(exe(p.datei));
        if (url.endsWith(".zip")) {
            auspackenZip(roh, exe(p.datei), ziel);
        } else if (url.endsWith(".tar.xz")) {
            auspackenTar(roh, exe(p.datei), ziel);
        } else {
            Files.move(roh, ziel, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.deleteIfExists(roh);
        if (!windows() && !ziel.toFile().setExecutable(true)) {
            throw new IOException(p.anzeige + " lässt sich nicht ausführbar machen");
        }
    }

    /** Holt aus dem ZIP die Datei mit diesem Namen (egal in welchem Unterordner). */
    static void auspackenZip(Path zip, String name, Path ziel) throws IOException {
        try (ZipInputStream rein = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            while ((e = rein.getNextEntry()) != null) {
                String eintrag = e.getName().replace('\\', '/');
                if (!e.isDirectory() && (eintrag.equals(name) || eintrag.endsWith("/" + name))) {
                    Path tmp = ziel.resolveSibling(name + ".neu");
                    Files.copy(rein, tmp, StandardCopyOption.REPLACE_EXISTING);
                    Files.move(tmp, ziel, StandardCopyOption.REPLACE_EXISTING);
                    return;
                }
            }
        }
        throw new IOException(name + " nicht im Archiv gefunden");
    }

    /** tar.xz (FFmpeg für Linux): mit dem tar des Systems auspacken und die Datei herausnehmen. */
    private static void auspackenTar(Path archiv, String name, Path ziel) throws IOException, InterruptedException {
        Path tmp = archiv.resolveSibling(name + "-auspacken");
        loeschen(tmp);
        Files.createDirectories(tmp);
        Process tar = new ProcessBuilder("tar", "-xJf", archiv.toString(), "-C", tmp.toString())
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        if (!tar.waitFor(10, TimeUnit.MINUTES) || tar.exitValue() != 0) {
            tar.destroyForcibly();
            throw new IOException("tar konnte " + archiv.getFileName() + " nicht auspacken");
        }
        try (Stream<Path> alle = Files.walk(tmp)) {
            Path gefunden = alle.filter(d -> d.getFileName().toString().equals(name) && d.getParent().getFileName().toString().equals("bin"))
                    .findFirst().orElseThrow(() -> new IOException(name + " nicht im Archiv gefunden"));
            Files.move(gefunden, ziel, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            loeschen(tmp);
        }
    }

    private static void loeschen(Path ordner) throws IOException {
        if (!Files.exists(ordner)) {
            return;
        }
        try (Stream<Path> alle = Files.walk(ordner)) {
            for (Path d : alle.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(d);
            }
        }
    }

    /**
     * Bringt das selbst geladene yt-dlp einmal pro Spielstart auf den neuesten Stand (YouTube ändert öfter etwas).
     * Läuft im Thread des Aufrufers; ein Fehler hier stört das Abspielen nicht.
     */
    static void ytdlpAktualisieren() {
        synchronized (VideoWerkzeuge.class) {
            if (aktualisiert) {
                return;
            }
            aktualisiert = true;
        }
        Path ytdlp = finden(Programm.YTDLP);
        if (ytdlp == null || !ytdlp.startsWith(ordner())) {
            return;
        }
        try {
            Process p = new ProcessBuilder(ytdlp.toString(), "-U").redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!p.waitFor(90, TimeUnit.SECONDS)) {
                p.destroyForcibly();
            }
        } catch (Exception e) {
            StinkendReich.LOGGER.info("Fernseher: yt-dlp-Aktualisierung übersprungen ({})", e.getMessage());
        }
    }
}
