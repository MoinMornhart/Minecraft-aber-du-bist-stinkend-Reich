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
    private String sha1 = "";
    private HttpServer webserver;
    private String adresse;

    public Ressourcenpaket(StinkendReichPaper plugin) {
        this.plugin = plugin;
    }

    /** Name des Item-Modells, das im Paket auf ein Blockmodell zeigt (für die Anzeige-Figuren). */
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
                if (e.isDirectory() || !(name.startsWith("mod/assets/") || name.startsWith("paket/assets/"))) {
                    continue;
                }
                try (InputStream ein = jar.getInputStream(e)) {
                    byte[] inhalt = ein.readAllBytes();
                    String pfad = name.substring(name.indexOf("assets/"));
                    dateien.put(pfad, inhalt);
                    // Lack der Fahrzeuge: dieselbe Textur auch dort, wo die Fahrzeug-Modelle sie suchen
                    String lack = "assets/stinkendreich/textures/entity/";
                    if (pfad.startsWith(lack) && FAHRZEUGE.contains(pfad.substring(lack.length()).split("/")[0])
                            && pfad.substring(lack.length()).contains("/")) {
                        dateien.put("assets/stinkendreich/textures/item/polymer/fahrzeug/" + pfad.substring(lack.length()), inhalt);
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
        String host = plugin.getConfig().getString("ressourcenpaket.host", "");
        if (host == null || host.isBlank()) {
            host = plugin.getServer().getIp();
        }
        if (host == null || host.isBlank()) {
            host = "localhost";
        }
        adresse = "http://" + host + ":" + port + "/stinkendreich-" + sha1.substring(0, 8) + ".zip";
        plugin.getLogger().info("Ressourcenpaket unter " + adresse);
    }

    public void stoppen() {
        if (webserver != null) {
            webserver.stop(0);
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
        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(PAKET_ID, URI.create(adresse), sha1);
        spieler.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .required(plugin.getConfig().getBoolean("ressourcenpaket.pflicht", true))
                .prompt(Component.text("Ohne dieses Paket sieht die Villa nicht wie eine Villa aus."))
                .replace(false)
                .build());
    }
}
