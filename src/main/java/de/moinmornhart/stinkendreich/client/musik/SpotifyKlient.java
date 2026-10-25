package de.moinmornhart.stinkendreich.client.musik;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.moinmornhart.stinkendreich.Konfig;
import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.client.KompatClient;
import de.moinmornhart.stinkendreich.musik.MusikanlageBlockEntity;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/**
 * Fernbedienung für Spotify über die Spotify Web API.
 *
 * <p>Anmeldung mit „Authorization Code + PKCE“: Es braucht nur die Client-ID der eigenen
 * Spotify-App (aus der Config), kein Secret. Nach der Anmeldung im Browser schickt Spotify den
 * Browser an {@code http://127.0.0.1:<spotifyPort>/callback}; dort wartet ein winziger
 * HTTP-Server, der nur an 127.0.0.1 lauscht und nach einer Anmeldung wieder schließt.
 *
 * <p>Die Schlüssel (Access- und Refresh-Token) liegen nur in {@code config/stinkendreich-spotify.json}
 * auf diesem Rechner und werden nie ins Protokoll geschrieben oder an den Server geschickt.
 * Abgespielt wird auf dem Spotify-Gerät des Spielers (Handy, PC, Lautsprecher), nicht im Spiel;
 * dafür verlangt Spotify ein Premium-Konto.
 */
public final class SpotifyKlient {
    public enum Status { NICHT_EINGERICHTET, GETRENNT, WARTET, VERBUNDEN }

    /** Eine eigene Playlist zum Starten. */
    public record Playlist(String name, String uri, int titel) {
    }

    /** Antwort der Web API: HTTP-Status und JSON (leer bei 204). */
    private record Antwort(int status, JsonObject json) {
        boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    private static final String KONTO = "https://accounts.spotify.com";
    private static final String API = "https://api.spotify.com/v1";
    private static final String RECHTE = "user-read-playback-state user-modify-playback-state user-read-currently-playing "
            + "playlist-read-private playlist-read-collaborative";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final SecureRandom ZUFALL = new SecureRandom();
    /** Wartezeit auf die Anmeldung im Browser. */
    private static final int ANMELDE_ZEIT_MS = 5 * 60 * 1000;

    // ---------- Anzeige (nur auf dem Client-Thread ändern) ----------
    private static boolean warten;
    private static String fehler;
    private static Object[] fehlerWerte = new Object[0];
    private static boolean spielt;
    private static boolean geraetDa;
    private static String titel = "";
    private static String interpret = "";
    private static String geraet = "";
    private static int lautstaerke = -1;
    private static List<Playlist> playlists = List.of();
    private static boolean playlistsGeladen;

    // ---------- Verknüpfung mit einer Musikanlage ----------
    private static BlockPos anlage;
    private static int verknuepftSeit;
    private static String gemeldet = "";
    private static int takt;
    private static int menueOffen;

    // ---------- Anmeldung ----------
    private static final Object SCHLOSS = new Object();
    private static Token token;
    private static boolean tokenGeladen;
    private static CompletableFuture<String> erneuerung;
    private static ServerSocket rueckruf;

    /** Inhalt der Schlüsseldatei. */
    private static final class Token {
        String access_token;
        String refresh_token;
        long ablauf;
    }

    private SpotifyKlient() {
    }

    // ---------------- Abfragen für das Menü ----------------

    public static Status status() {
        if (Konfig.get().spotifyClientId.isBlank()) {
            return Status.NICHT_EINGERICHTET;
        }
        if (warten) {
            return Status.WARTET;
        }
        return tokenLesen() != null ? Status.VERBUNDEN : Status.GETRENNT;
    }

    public static String redirectUri() {
        return "http://127.0.0.1:" + Konfig.get().spotifyPort + "/callback";
    }

    /** Fehlermeldung zum Anzeigen oder {@code null}. */
    public static Component fehler() {
        return fehler == null ? null : Component.translatable("stinkendreich.spotify.fehler." + fehler, fehlerWerte);
    }

    public static boolean spielt() {
        return spielt;
    }

    public static boolean geraetDa() {
        return geraetDa;
    }

    public static String titel() {
        return titel;
    }

    public static String interpret() {
        return interpret;
    }

    public static String geraet() {
        return geraet;
    }

    public static int lautstaerke() {
        return lautstaerke;
    }

    public static List<Playlist> playlists() {
        return playlists;
    }

    public static boolean playlistsGeladen() {
        return playlistsGeladen;
    }

    /** Kurzfassung des sichtbaren Zustands; ändert sie sich, baut das Menü sich neu auf. */
    public static String stand() {
        return status() + "|" + spielt + "|" + geraetDa + "|" + titel + "|" + interpret + "|" + geraet + "|"
                + playlists.size() + "|" + playlistsGeladen + "|" + fehler + "|" + (lautstaerke >= 0);
    }

    /** Das Menü meldet sich jeden Tick, solange es offen ist (dann wird öfter abgefragt). */
    public static void menueOffen() {
        menueOffen = 3;
    }

    // ---------------- Anmelden und Trennen ----------------

    /** Öffnet die Spotify-Anmeldung im Browser und wartet auf die Rückmeldung. */
    public static void verbinden() {
        String clientId = Konfig.get().spotifyClientId.trim();
        int port = Konfig.get().spotifyPort;
        abbrechen();
        fehler = null;
        String verifier = zufallsText(64);
        String state = zufallsText(16);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(verifier));
        String redirect = redirectUri();
        ServerSocket server;
        try {
            // Nur an 127.0.0.1, damit niemand aus dem Netz die Rückmeldung abfangen kann
            server = new ServerSocket(port, 4, InetAddress.getByName("127.0.0.1"));
            server.setSoTimeout(ANMELDE_ZEIT_MS);
        } catch (IOException e) {
            fehler("port", port);
            return;
        }
        rueckruf = server;
        warten = true;
        Thread t = new Thread(() -> rueckrufAbwarten(server, clientId, redirect, verifier, state), "Spotify-Anmeldung");
        t.setDaemon(true);
        t.start();

        Map<String, String> werte = new LinkedHashMap<>();
        werte.put("client_id", clientId);
        werte.put("response_type", "code");
        werte.put("redirect_uri", redirect);
        werte.put("code_challenge_method", "S256");
        werte.put("code_challenge", challenge);
        werte.put("scope", RECHTE);
        werte.put("state", state);
        String adresse = KONTO + "/authorize?" + formular(werte);
        try {
            KompatClient.oeffneLink(URI.create(adresse));
        } catch (RuntimeException e) {
            StinkendReich.LOGGER.warn("Browser ließ sich nicht öffnen: {}", e.getMessage());
        }
        // Falls kein Browser aufgeht: Link zum Anklicken im Chat
        Minecraft client = Minecraft.getInstance();
        KompatClient.chatNachricht(client, Component.translatable("stinkendreich.spotify.link")
                .withStyle(s -> s.withClickEvent(KompatClient.klickLink(adresse)).withUnderlined(true).withColor(ChatFormatting.GREEN)));
    }

    /** Bricht eine laufende Anmeldung ab. */
    public static void abbrechen() {
        warten = false;
        ServerSocket s = rueckruf;
        rueckruf = null;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignoriert) {
                // schon zu
            }
        }
    }

    /** Meldet ab: löscht die Schlüsseldatei. */
    public static void trennen() {
        abbrechen();
        if (anlage != null) {
            KompatClient.sende("musik_dj", anlage, "stopp");
        }
        anlage = null;
        synchronized (SCHLOSS) {
            token = null;
            tokenGeladen = true;
            try {
                Files.deleteIfExists(datei());
            } catch (IOException e) {
                StinkendReich.LOGGER.warn("Spotify-Schlüsseldatei ließ sich nicht löschen: {}", e.getMessage());
            }
        }
        zuruecksetzen();
    }

    private static void zuruecksetzen() {
        spielt = false;
        geraetDa = false;
        titel = "";
        interpret = "";
        geraet = "";
        lautstaerke = -1;
        playlists = List.of();
        playlistsGeladen = false;
    }

    /** Läuft im eigenen Thread: nimmt den Browser-Aufruf an und tauscht den Code gegen Schlüssel. */
    private static void rueckrufAbwarten(ServerSocket server, String clientId, String redirect, String verifier, String state) {
        try (server) {
            while (true) {
                try (Socket s = server.accept()) {
                    s.setSoTimeout(10_000);
                    BufferedReader ein = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
                    String zeile = ein.readLine();
                    String[] teile = zeile == null ? new String[0] : zeile.split(" ");
                    String pfad = teile.length > 1 ? teile[1] : "";
                    if (!pfad.startsWith("/callback")) {
                        // z. B. /favicon.ico
                        antworten(s, 404, seite("Nicht gefunden", ""));
                        continue;
                    }
                    Map<String, String> q = abfrage(pfad);
                    if (!state.equals(q.get("state"))) {
                        antworten(s, 400, seite("Ungültige Anfrage", "Bitte in Minecraft erneut auf „Mit Spotify verbinden“ klicken."));
                        fertig("abgebrochen");
                        return;
                    }
                    String code = q.get("code");
                    if (q.containsKey("error") || code == null) {
                        antworten(s, 200, seite("Abgebrochen", "Die Anmeldung bei Spotify wurde abgebrochen. Du kannst dieses Fenster schließen."));
                        fertig("abgebrochen");
                        return;
                    }
                    antworten(s, 200, seite("Fertig! 🎵", "Spotify ist mit der Musikanlage verbunden. Du kannst dieses Fenster schließen und zurück zu Minecraft gehen."));
                    codeEintauschen(clientId, redirect, verifier, code);
                    return;
                }
            }
        } catch (SocketTimeoutException e) {
            fertig("zeit");
        } catch (SocketException e) {
            // Server geschlossen (Abbrechen) – nichts melden
            if (rueckruf == server) {
                fertig("netz");
            }
        } catch (IOException e) {
            fertig("netz");
        }
    }

    private static void codeEintauschen(String clientId, String redirect, String verifier, String code) {
        Map<String, String> werte = new LinkedHashMap<>();
        werte.put("grant_type", "authorization_code");
        werte.put("code", code);
        werte.put("redirect_uri", redirect);
        werte.put("client_id", clientId);
        werte.put("code_verifier", verifier);
        tokenAnfrage(werte).whenComplete((t, f) -> Minecraft.getInstance().execute(() -> {
            warten = false;
            rueckruf = null;
            if (f != null) {
                fehler(f.getCause() instanceof IOException ? "netz" : "client");
                return;
            }
            fehler = null;
            KompatClient.chatNachricht(Minecraft.getInstance(),
                    Component.translatable("stinkendreich.spotify.verbunden").withStyle(ChatFormatting.GREEN));
            aktualisieren();
            playlistsLaden();
        }));
    }

    /** Meldet das Ende der Anmeldung (aus dem Anmelde-Thread) an den Client-Thread. */
    private static void fertig(String fehlerSchluessel) {
        Minecraft.getInstance().execute(() -> {
            warten = false;
            rueckruf = null;
            fehler(fehlerSchluessel);
        });
    }

    // ---------------- Schlüssel ----------------

    private static Path datei() {
        return FabricLoader.getInstance().getConfigDir().resolve(StinkendReich.MOD_ID + "-spotify.json");
    }

    private static Token tokenLesen() {
        synchronized (SCHLOSS) {
            if (!tokenGeladen) {
                tokenGeladen = true;
                try {
                    if (Files.exists(datei())) {
                        JsonObject j = JsonParser.parseString(Files.readString(datei(), StandardCharsets.UTF_8)).getAsJsonObject();
                        Token t = new Token();
                        t.access_token = text(j, "access_token");
                        t.refresh_token = text(j, "refresh_token");
                        t.ablauf = j.has("ablauf") ? j.get("ablauf").getAsLong() : 0;
                        token = t.refresh_token.isEmpty() ? null : t;
                    }
                } catch (Exception e) {
                    StinkendReich.LOGGER.warn("Spotify-Schlüsseldatei unlesbar, bitte neu verbinden");
                    token = null;
                }
            }
            return token;
        }
    }

    private static void tokenSpeichern(Token t) {
        synchronized (SCHLOSS) {
            token = t;
            tokenGeladen = true;
            JsonObject j = new JsonObject();
            j.addProperty("_hinweis", "Spotify-Anmeldung der Musikanlage. Geheim halten, nicht weitergeben. Löschen = abmelden.");
            j.addProperty("access_token", t.access_token);
            j.addProperty("refresh_token", t.refresh_token);
            j.addProperty("ablauf", t.ablauf);
            Path p = datei();
            try {
                Files.createDirectories(p.getParent());
                Files.writeString(p, j.toString(), StandardCharsets.UTF_8);
                try {
                    // Nur der eigene Benutzer darf die Datei lesen (wo das Dateisystem es kann)
                    Files.setPosixFilePermissions(p, PosixFilePermissions.fromString("rw-------"));
                } catch (UnsupportedOperationException | IOException ignoriert) {
                    // z. B. Windows
                }
            } catch (IOException e) {
                StinkendReich.LOGGER.warn("Spotify-Schlüsseldatei ließ sich nicht schreiben: {}", e.getMessage());
            }
        }
    }

    /** POST an /api/token; speichert die neuen Schlüssel. Der Inhalt der Antwort wird nie geloggt. */
    private static CompletableFuture<Token> tokenAnfrage(Map<String, String> werte) {
        HttpRequest anfrage = HttpRequest.newBuilder(URI.create(KONTO + "/api/token"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formular(werte)))
                .build();
        return HTTP.sendAsync(anfrage, HttpResponse.BodyHandlers.ofString()).thenApply(a -> {
            if (a.statusCode() == 400 || a.statusCode() == 401) {
                // Code oder Refresh-Token ungültig bzw. Client-ID falsch: neu anmelden
                throw new CompletionException(new IllegalStateException("Token-Anfrage abgelehnt (" + a.statusCode() + ")"));
            }
            if (a.statusCode() != 200) {
                // Störung bei Spotify: Schlüssel behalten, später erneut versuchen
                throw new CompletionException(new IOException("Spotify antwortet mit " + a.statusCode()));
            }
            JsonObject j = JsonParser.parseString(a.body()).getAsJsonObject();
            Token alt = tokenLesen();
            Token t = new Token();
            t.access_token = text(j, "access_token");
            // Spotify schickt beim Erneuern nicht immer einen neuen Refresh-Token
            t.refresh_token = j.has("refresh_token") ? text(j, "refresh_token") : alt == null ? "" : alt.refresh_token;
            t.ablauf = System.currentTimeMillis() + (j.has("expires_in") ? j.get("expires_in").getAsLong() : 3600) * 1000L;
            tokenSpeichern(t);
            return t;
        });
    }

    /** Gültiger Access-Token; erneuert ihn bei Bedarf (nur eine Erneuerung gleichzeitig). */
    private static CompletableFuture<String> zugang(boolean erzwingen) {
        Token t = tokenLesen();
        if (t == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("nicht verbunden"));
        }
        if (!erzwingen && t.ablauf - 60_000 > System.currentTimeMillis() && !t.access_token.isEmpty()) {
            return CompletableFuture.completedFuture(t.access_token);
        }
        synchronized (SCHLOSS) {
            if (erneuerung == null || erneuerung.isDone()) {
                Map<String, String> werte = new LinkedHashMap<>();
                werte.put("grant_type", "refresh_token");
                werte.put("refresh_token", t.refresh_token);
                werte.put("client_id", Konfig.get().spotifyClientId.trim());
                erneuerung = tokenAnfrage(werte).thenApply(n -> n.access_token);
            }
            return erneuerung;
        }
    }

    // ---------------- Web API ----------------

    private static CompletableFuture<Antwort> api(String methode, String pfad, JsonObject koerper) {
        return zugang(false).thenCompose(t -> senden(methode, pfad, koerper, t)).thenCompose(a -> a.status() == 401
                ? zugang(true).thenCompose(t -> senden(methode, pfad, koerper, t))
                : CompletableFuture.completedFuture(a));
    }

    private static CompletableFuture<Antwort> senden(String methode, String pfad, JsonObject koerper, String zugang) {
        HttpRequest.BodyPublisher inhalt = koerper == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(koerper.toString());
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(API + pfad))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + zugang)
                .method(methode, inhalt);
        if (koerper != null) {
            b.header("Content-Type", "application/json");
        }
        return HTTP.sendAsync(b.build(), HttpResponse.BodyHandlers.ofString()).thenApply(a -> {
            JsonObject j = new JsonObject();
            String text = a.body();
            if (text != null && !text.isBlank()) {
                try {
                    JsonElement e = JsonParser.parseString(text);
                    if (e.isJsonObject()) {
                        j = e.getAsJsonObject();
                    }
                } catch (RuntimeException ignoriert) {
                    // kein JSON
                }
            }
            return new Antwort(a.statusCode(), j);
        });
    }

    /** Führt einen Befehl aus und zeigt danach den neuen Stand (Spotify braucht dafür einen Moment). */
    private static void befehl(String methode, String pfad, JsonObject koerper) {
        api(methode, pfad, koerper).whenComplete((a, f) -> Minecraft.getInstance().execute(() -> {
            if (auswerten(a, f, true)) {
                CompletableFuture.delayedExecutor(600, TimeUnit.MILLISECONDS)
                        .execute(() -> Minecraft.getInstance().execute(SpotifyKlient::aktualisieren));
            } else {
                // Befehl ging schief: der Anlage den bisherigen Stand melden (Fehlermeldung bleibt stehen)
                melden(false);
            }
        }));
    }

    /**
     * Setzt bei Fehlern eine verständliche Meldung; true = alles gut. Nur ein erfolgreicher Befehl
     * ({@code loeschen}) nimmt eine alte Meldung weg, das regelmäßige Abfragen lässt sie stehen.
     */
    private static boolean auswerten(Antwort a, Throwable f, boolean loeschen) {
        if (f != null) {
            Throwable grund = f instanceof CompletionException && f.getCause() != null ? f.getCause() : f;
            if (grund instanceof IllegalStateException) {
                // Anmeldung abgelaufen oder widerrufen
                synchronized (SCHLOSS) {
                    if (token != null && grund.getMessage() != null && grund.getMessage().startsWith("Token")) {
                        token = null;
                        try {
                            Files.deleteIfExists(datei());
                        } catch (IOException ignoriert) {
                            // egal
                        }
                    }
                }
                zuruecksetzen();
                fehler("abgelaufen");
            } else {
                fehler("netz");
            }
            return false;
        }
        if (a.ok()) {
            if (loeschen) {
                fehler = null;
            }
            return true;
        }
        JsonObject e = a.json().has("error") && a.json().get("error").isJsonObject() ? a.json().getAsJsonObject("error") : new JsonObject();
        String grund = text(e, "reason");
        String nachricht = text(e, "message").toLowerCase(Locale.ROOT);
        switch (a.status()) {
            case 401 -> {
                zuruecksetzen();
                fehler("abgelaufen");
            }
            case 403 -> {
                if ("PREMIUM_REQUIRED".equals(grund) || nachricht.contains("premium")) {
                    fehler("premium");
                } else if (grund.startsWith("VOLUME") || nachricht.contains("volume")) {
                    fehler("lautstaerke");
                } else {
                    fehler("verweigert");
                }
            }
            case 404 -> fehler("geraet");
            case 429 -> fehler("bremse");
            default -> fehler("unbekannt", a.status());
        }
        return false;
    }

    private static void fehler(String schluessel, Object... werte) {
        fehler = schluessel;
        fehlerWerte = werte;
    }

    /** Fragt ab, was gerade läuft (Titel, Interpret, Gerät, Lautstärke). */
    public static void aktualisieren() {
        if (status() != Status.VERBUNDEN) {
            return;
        }
        api("GET", "/me/player", null).whenComplete((a, f) -> Minecraft.getInstance().execute(() -> {
            if (!auswerten(a, f, false)) {
                return;
            }
            JsonObject j = a.json();
            if (a.status() == 204 || !j.has("device")) {
                // Gerade ist kein Gerät aktiv
                spielt = false;
                geraetDa = false;
                titel = "";
                interpret = "";
                geraet = "";
                lautstaerke = -1;
            } else {
                geraetDa = true;
                spielt = j.has("is_playing") && j.get("is_playing").getAsBoolean();
                JsonObject g = j.getAsJsonObject("device");
                geraet = text(g, "name");
                lautstaerke = g.has("volume_percent") && !g.get("volume_percent").isJsonNull() ? g.get("volume_percent").getAsInt() : -1;
                JsonObject stueck = j.has("item") && j.get("item").isJsonObject() ? j.getAsJsonObject("item") : null;
                titel = stueck == null ? "" : text(stueck, "name");
                interpret = stueck == null ? "" : kuenstler(stueck);
            }
            melden(false);
        }));
    }

    private static String kuenstler(JsonObject stueck) {
        if (stueck.has("artists") && stueck.get("artists").isJsonArray()) {
            StringJoiner namen = new StringJoiner(", ");
            for (JsonElement k : stueck.getAsJsonArray("artists")) {
                if (k.isJsonObject()) {
                    namen.add(text(k.getAsJsonObject(), "name"));
                }
            }
            return namen.toString();
        }
        // Podcast-Folge
        return stueck.has("show") && stueck.get("show").isJsonObject() ? text(stueck.getAsJsonObject("show"), "name") : "";
    }

    /** Lädt die eigenen Playlists (bis zu 50). */
    public static void playlistsLaden() {
        if (status() != Status.VERBUNDEN) {
            return;
        }
        api("GET", "/me/playlists?limit=50", null).whenComplete((a, f) -> Minecraft.getInstance().execute(() -> {
            if (!auswerten(a, f, false)) {
                return;
            }
            List<Playlist> liste = new ArrayList<>();
            JsonArray eintraege = a.json().has("items") && a.json().get("items").isJsonArray() ? a.json().getAsJsonArray("items") : new JsonArray();
            for (JsonElement e : eintraege) {
                if (e.isJsonObject()) {
                    JsonObject p = e.getAsJsonObject();
                    liste.add(new Playlist(text(p, "name"), text(p, "uri"), anzahl(p)));
                }
            }
            playlists = liste;
            playlistsGeladen = true;
        }));
    }

    /** Anzahl Titel einer Playlist (Feld heißt je nach API-Stand „tracks“ oder „items“). */
    private static int anzahl(JsonObject p) {
        for (String feld : new String[]{"tracks", "items"}) {
            if (p.has(feld) && p.get(feld).isJsonObject() && p.getAsJsonObject(feld).has("total")) {
                return p.getAsJsonObject(feld).get("total").getAsInt();
            }
        }
        return -1;
    }

    // ---------------- Steuerung aus dem Menü ----------------

    public static void spielen(BlockPos pos) {
        verknuepfen(pos, true);
        befehl("PUT", "/me/player/play", null);
    }

    public static void pause(BlockPos pos) {
        verknuepfen(pos, false);
        befehl("PUT", "/me/player/pause", null);
    }

    public static void weiter(BlockPos pos) {
        verknuepfen(pos, true);
        befehl("POST", "/me/player/next", null);
    }

    public static void zurueck(BlockPos pos) {
        verknuepfen(pos, true);
        befehl("POST", "/me/player/previous", null);
    }

    public static void playlistStarten(BlockPos pos, Playlist p) {
        verknuepfen(pos, true);
        JsonObject k = new JsonObject();
        k.addProperty("context_uri", p.uri());
        befehl("PUT", "/me/player/play", k);
    }

    public static void setzeLautstaerke(int prozent) {
        int wert = Math.max(0, Math.min(100, prozent));
        lautstaerke = wert;
        befehl("PUT", "/me/player/volume?volume_percent=" + wert, null);
    }

    /**
     * Verbindet Spotify mit dieser Anlage: Man wird ihr DJ, Titel und Spielzustand erscheinen bei
     * allen im Menü, und die Partylichter laufen mit. Hören kann man Spotify nur selbst.
     */
    private static void verknuepfen(BlockPos pos, boolean laeuftGleich) {
        anlage = pos.immutable();
        verknuepftSeit = takt;
        gemeldet = "";
        KompatClient.sende("musik", pos, "spotify|" + (laeuftGleich ? 1 : 0) + "|" + anzeigetitel());
    }

    private static String anzeigetitel() {
        return titel.isEmpty() ? "Spotify" : interpret.isEmpty() ? titel : titel + " – " + interpret;
    }

    /** Schickt den eigenen Spotify-Stand an die verknüpfte Anlage (nur bei Änderung oder alle 10 s). */
    private static void melden(boolean immer) {
        if (anlage == null) {
            return;
        }
        String text = (spielt ? 1 : 0) + "|" + anzeigetitel();
        if (immer || !text.equals(gemeldet)) {
            gemeldet = text;
            KompatClient.sende("musik_dj", anlage, "spotify|" + text);
        }
    }

    /** Aufruf jeden Client-Tick aus {@link MusikKlient}. */
    static void tick(Minecraft client) {
        takt++;
        if (menueOffen > 0) {
            menueOffen--;
        }
        if (status() != Status.VERBUNDEN || client.level == null || client.player == null) {
            return;
        }
        if (anlage != null && takt - verknuepftSeit > 40) {
            // Hat jemand anderes übernommen oder Dateien gestartet? Dann nicht mehr melden.
            if (!(client.level.getBlockEntity(anlage) instanceof MusikanlageBlockEntity be)
                    || !MusikanlageBlockEntity.SPOTIFY.equals(be.quelle()) || !client.player.getUUID().equals(be.dj())) {
                anlage = null;
            }
        }
        if ((menueOffen > 0 || anlage != null) && takt % 100 == 0) {
            aktualisieren();
        }
        if (anlage != null && takt % 200 == 0) {
            // Lebenszeichen, sonst stoppt der Server die Anzeige nach 30 Sekunden
            melden(true);
        }
    }

    static void weltVerlassen() {
        anlage = null;
    }

    // ---------------- Hilfen ----------------

    private static String text(JsonObject j, String feld) {
        return j.has(feld) && j.get(feld).isJsonPrimitive() ? j.get(feld).getAsString() : "";
    }

    private static String zufallsText(int bytes) {
        byte[] b = new byte[bytes];
        ZUFALL.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String formular(Map<String, String> werte) {
        StringJoiner j = new StringJoiner("&");
        werte.forEach((k, v) -> j.add(URLEncoder.encode(k, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(v, StandardCharsets.UTF_8)));
        return j.toString();
    }

    private static Map<String, String> abfrage(String pfad) {
        Map<String, String> q = new HashMap<>();
        int f = pfad.indexOf('?');
        if (f < 0) {
            return q;
        }
        for (String paar : pfad.substring(f + 1).split("&")) {
            int g = paar.indexOf('=');
            if (g > 0) {
                q.put(URLDecoder.decode(paar.substring(0, g), StandardCharsets.UTF_8), URLDecoder.decode(paar.substring(g + 1), StandardCharsets.UTF_8));
            }
        }
        return q;
    }

    private static void antworten(Socket s, int status, String html) throws IOException {
        byte[] inhalt = html.getBytes(StandardCharsets.UTF_8);
        String kopf = "HTTP/1.1 " + status + (status == 200 ? " OK" : status == 404 ? " Not Found" : " Bad Request") + "\r\n"
                + "Content-Type: text/html; charset=utf-8\r\n"
                + "Content-Length: " + inhalt.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        OutputStream aus = s.getOutputStream();
        aus.write(kopf.getBytes(StandardCharsets.US_ASCII));
        aus.write(inhalt);
        aus.flush();
    }

    private static String seite(String ueberschrift, String text) {
        return "<!doctype html><html lang=\"de\"><head><meta charset=\"utf-8\"><title>Stinkend Reich – Spotify</title>"
                + "<style>body{background:#111;color:#f5d76e;font-family:sans-serif;text-align:center;padding-top:15vh}"
                + "p{color:#ddd;font-size:1.2em}</style></head><body><h1>" + ueberschrift + "</h1><p>" + text + "</p></body></html>";
    }
}
