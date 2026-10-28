package de.moinmornhart.stinkendreich.paper;

import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Baut beim Start das Ressourcenpaket (Bilder, Modelle, Töne und Texte der Mod plus Item-Definitionen für die
 * Anzeige-Figuren) und schickt es jedem Spieler beim Betreten. Das Paket liefert ein kleiner eingebauter Webserver
 * (Port in der Config, „ressourcenpaket.port“); wer es lieber selbst hostet, trägt „ressourcenpaket.adresse“ ein.
 */
public final class Ressourcenpaket implements Listener {
    /** Formatnummer der Ressourcenpakete von Minecraft 26.3. */
    private static final int FORMAT = 97;
    /** Fahrzeuge der Mod (Ordner ihrer Lacktexturen). */
    private static final Set<String> FAHRZEUGE = Set.of("bonzenauto", "gelaendewagen", "hubschrauber", "stretchlimo", "supersportwagen");
    private static final UUID PAKET_ID = UUID.nameUUIDFromBytes("stinkendreich-paket".getBytes(StandardCharsets.UTF_8));

    private final StinkendReichPaper plugin;
    private byte[] daten = new byte[0];
    /** Alle Dateien des Pakets („assets/…“ → Inhalt), z. B. für das Bedrock-Paket. */
    private Map<String, byte[]> dateien = Map.of();
    private String sha1 = "";
    private HttpServer webserver;
    private String adresse;

    public Ressourcenpaket(StinkendReichPaper plugin) {
        this.plugin = plugin;
    }

    /** Name des Item-Modells, das im Paket auf ein Blockmodell zeigt (für die Anzeige-Figuren). */
    /** Dateien des gebauten Pakets („assets/…“ → Inhalt). */
    public Map<String, byte[]> dateien() {
        return dateien;
    }

    public static String anzeigeModell(String modell) {
        String[] teile = modell.split(":", 2);
        return "stinkendreich:anzeige/" + teile[0] + "/" + teile[1];
    }

    public void bauen(Set<String> blockModelle) throws IOException {
        TreeMap<String, byte[]> dateien = new TreeMap<>();
        // 1. Alles aus der Mod (liegt im Plugin unter mod/assets/…)
        try (JarFile jar = new JarFile(plugin.jarDatei())) {
            for (JarEntry e : java.util.Collections.list(jar.entries())) {
                String name = e.getName();
                if (e.isDirectory() || !(name.startsWith("mod/assets/") || name.startsWith("paket/assets/") || name.startsWith("zusatz/assets/"))) {
                    continue;
                }
                try (InputStream ein = jar.getInputStream(e)) {
                    byte[] inhalt = ein.readAllBytes();
                    String pfad = name.substring(name.indexOf("assets/"));
                    dateien.put(pfad, inhalt);
                    // Lack der Fahrzeuge: dieselbe Textur auch dort, wo die Fahrzeug-Modelle sie suchen
                    // Gemälde der Mod: als flaches Item-Modell (Bild vorn, Holz hinten), Textur im Block-Atlas (der Rahmen ist Fichtenholz, ein Modell darf nur einen Atlas nutzen)
                    String bilder = "assets/stinkendreich/textures/painting/";
                    if (pfad.startsWith(bilder) && pfad.endsWith(".png")) {
                        String bild = pfad.substring(bilder.length(), pfad.length() - 4);
                        dateien.put("assets/stinkendreich/textures/block/polymer/bild/" + bild + ".png", inhalt);
                        dateien.put("assets/stinkendreich/models/item/polymer/bild/" + bild + ".json", gemaeldeModell(bild));
                        dateien.put("assets/stinkendreich/items/polymer/bild/" + bild + ".json", ("{\"model\":{\"type\":\"minecraft:model\","
                                + "\"model\":\"stinkendreich:item/polymer/bild/" + bild + "\"}}").getBytes(StandardCharsets.UTF_8));
                    }
                    String lack = "assets/stinkendreich/textures/entity/";
                    if (pfad.startsWith(lack) && FAHRZEUGE.contains(pfad.substring(lack.length()).split("/")[0])
                            && pfad.substring(lack.length()).contains("/")) {
                        dateien.put("assets/stinkendreich/textures/item/polymer/fahrzeug/" + pfad.substring(lack.length()), inhalt);
                    }
                    // Hai: Textur der Mod für die Hai-Modelle (zusatz/…/polymer/hai*.json aus tools/paper/hai_modell.py)
                    if (pfad.equals(lack + "hai.png")) {
                        dateien.put("assets/stinkendreich/textures/item/polymer/hai.png", inhalt);
                    }
                }
            }
        }
        // 2. Item-Definitionen: je Blockmodell eine, damit Anzeige-Figuren es zeigen können
        for (String modell : blockModelle) {
            String item = anzeigeModell(modell);
            String[] teile = item.split(":", 2);
            dateien.put("assets/" + teile[0] + "/items/" + teile[1] + ".json",
                    ("{\"model\":{\"type\":\"minecraft:model\",\"model\":\"" + modell + "\"}}").getBytes(StandardCharsets.UTF_8));
        }
        dateien.put("pack.mcmeta", ("{\"pack\":{\"description\":\"Minecraft, aber du bist stinkend reich\",\"min_format\":"
                + FORMAT + ",\"max_format\":" + FORMAT + "}}").getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream puffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(puffer)) {
            for (var e : dateien.entrySet()) {
                ZipEntry eintrag = new ZipEntry(e.getKey());
                eintrag.setTime(0);       // immer gleiche Prüfsumme bei gleichem Inhalt
                zip.putNextEntry(eintrag);
                zip.write(e.getValue());
                zip.closeEntry();
            }
        }
        daten = puffer.toByteArray();
        this.dateien = java.util.Collections.unmodifiableMap(dateien);
        try {
            sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(daten));
        } catch (Exception e) {
            throw new IOException(e);
        }
        Path ziel = plugin.getDataFolder().toPath().resolve("ressourcenpaket.zip");
        Files.createDirectories(ziel.getParent());
        Files.write(ziel, daten);
        plugin.getLogger().info("Ressourcenpaket: " + dateien.size() + " Dateien, " + (daten.length / 1024) + " KB (" + ziel + ")");
    }

    /**
     * Ein Gemälde als Item-Modell: eine 1 × 1 Block große, einen Pixel dicke Tafel (die Anzeige-Figur streckt sie auf
     * Breite × Höhe), Bild auf der Südseite, Rückseite und Kanten aus Fichtenholz wie bei Bildern im Spiel.
     */
    private static byte[] gemaeldeModell(String name) {
        String holz = "{\"uv\":[0,0,16,16],\"texture\":\"#holz\"}";
        return ("{\"textures\":{\"bild\":\"stinkendreich:block/polymer/bild/" + name + "\",\"holz\":\"minecraft:block/spruce_planks\","
                + "\"particle\":\"minecraft:block/spruce_planks\"},\"elements\":[{\"from\":[0,0,7.5],\"to\":[16,16,8.5],\"faces\":{"
                + "\"south\":{\"uv\":[0,0,16,16],\"texture\":\"#bild\"},\"north\":" + holz + ",\"east\":" + holz
                + ",\"west\":" + holz + ",\"up\":" + holz + ",\"down\":" + holz + "}}]}").getBytes(StandardCharsets.UTF_8);
    }

    /** Startet den eingebauten Webserver (oder nimmt die Adresse aus der Config). */
    public void bereitstellen() throws IOException {
        String eigene = plugin.getConfig().getString("ressourcenpaket.adresse", "");
        if (eigene != null && !eigene.isBlank()) {
            adresse = eigene;
            return;
        }
        int port = plugin.getConfig().getInt("ressourcenpaket.port", 25580);
        webserver = HttpServer.create(new InetSocketAddress(port), 0);
        webserver.createContext("/", austausch -> {
            byte[] antwort = daten;
            austausch.getResponseHeaders().add("Content-Type", "application/zip");
            austausch.sendResponseHeaders(200, antwort.length);
            try (var aus = austausch.getResponseBody()) {
                aus.write(antwort);
            }
        });
        webserver.start();
        this.port = port;
        String host = plugin.getConfig().getString("ressourcenpaket.host", "");
        if (host == null || host.isBlank()) {
            host = plugin.getServer().getIp();
        }
        // Ohne feste Adresse (server-ip ist meist leer) bekommt jeder Spieler die Adresse, mit der er sich verbunden
        // hat (siehe adresseFuer) – vorher stand dort „localhost“, und Minecraft suchte das Paket auf dem eigenen PC
        festerHost = host == null || host.isBlank() ? null : host;
        adresse = "http://" + (festerHost != null ? festerHost : "<Adresse des Spielers>") + ":" + port + pfad();
        plugin.getLogger().info("Ressourcenpaket unter " + adresse + " (Port " + port + " muss von außen erreichbar sein)");
    }

    public void stoppen() {
        if (webserver != null) {
            webserver.stop(0);
        }
    }

    private int port;
    /** Host aus der Config bzw. server.properties, sonst null (dann die Adresse, mit der sich der Spieler verbunden hat). */
    private String festerHost;

    private String pfad() {
        return "/stinkendreich-" + sha1.substring(0, 8) + ".zip";
    }

    /** Download-Adresse für diesen Spieler. */
    private String adresseFuer(Player spieler) {
        if (webserver == null) {
            return adresse;                          // selbst gehostet (ressourcenpaket.adresse)
        }
        String host = festerHost;
        if (host == null) {
            InetSocketAddress v = spieler.getVirtualHost();
            host = v == null || v.getHostString() == null || v.getHostString().isBlank() ? "localhost" : v.getHostString();
            // Manche Clients hängen an den Namen noch Zusatzdaten (z. B. „\0FML…“) oder einen Punkt an
            int null0 = host.indexOf('\0');
            if (null0 >= 0) {
                host = host.substring(0, null0);
            }
            if (host.endsWith(".")) {
                host = host.substring(0, host.length() - 1);
            }
        }
        if (host.contains(":") && !host.startsWith("[")) {
            host = "[" + host + "]";                 // IPv6
        }
        return "http://" + host + ":" + port + pfad();
    }

    /** Konnte ein Spieler das Paket nicht laden: Hinweis für den Betreiber im Protokoll. */
    @EventHandler
    public void beimStatus(org.bukkit.event.player.PlayerResourcePackStatusEvent e) {
        if (e.getStatus() == org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD
                || e.getStatus() == org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.INVALID_URL) {
            plugin.getLogger().warning(e.getPlayer().getName() + " konnte das Ressourcenpaket nicht laden ("
                    + adresseFuer(e.getPlayer()) + "). Ist Port " + port + " (TCP) von außen erreichbar? Sonst in der Config "
                    + "„ressourcenpaket.host“ (öffentliche Adresse) oder „ressourcenpaket.adresse“ (eigener Download) eintragen.");
        }
    }

    @EventHandler
    public void beimBetreten(PlayerJoinEvent e) {
        senden(e.getPlayer());
    }

    public void senden(Player spieler) {
        if (adresse == null) {
            return;
        }
        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(PAKET_ID, URI.create(adresseFuer(spieler)), sha1);
        spieler.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .required(plugin.getConfig().getBoolean("ressourcenpaket.pflicht", true))
                .prompt(Component.text("Ohne dieses Paket sieht die Villa nicht wie eine Villa aus."))
                .replace(false)
                .build());
    }
}
