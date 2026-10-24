package de.moinmornhart.stinkendreich;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Alle Einstellungen der Mod. Liegt als {@code config/stinkendreich.json} im Minecraft-Ordner
 * und wird beim ersten Start mit Standardwerten und Erklärungen angelegt.
 */
public final class Konfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static Konfig aktuell = new Konfig();

    // ---------- Villa ----------
    /** Villa in jeder neuen Welt am Spawn bauen. */
    public boolean villaBauen = true;
    /** Breite des Übergangs vom ebenen Grundstück ins natürliche Gelände (Blöcke). */
    public int villaAnblendung = 24;
    /** Spieler beim allerersten Betreten einer Welt vor die Villa stellen. */
    public boolean villaSpawnSetzen = true;

    // ---------- Butler ----------
    /** Anthropic-API-Key für den Chat mit dem Butler. Leer = Butler nur über Befehle und Menü. */
    public String anthropicApiKey = "";
    /** Claude-Modell für den Butler-Chat. */
    public String butlerModell = "claude-sonnet-5-5";

    // ---------- Update ----------
    /** Beim Start auf GitHub nach einer neuen Version suchen. */
    public boolean updatePruefen = true;
    /** Optionaler GitHub-Token, nur nötig, falls das Repo privat ist. */
    public String githubToken = "";

    private Konfig() {
    }

    public static Konfig get() {
        return aktuell;
    }

    /** Lädt die Datei; fehlende Werte werden ergänzt und die Datei neu geschrieben. */
    public static void laden() {
        Path pfad = FabricLoader.getInstance().getConfigDir().resolve(StinkendReich.MOD_ID + ".json");
        try {
            if (Files.exists(pfad)) {
                JsonObject json = GSON.fromJson(Files.readString(pfad, StandardCharsets.UTF_8), JsonObject.class);
                json.remove("_erklaerung");
                Konfig geladen = GSON.fromJson(json, Konfig.class);
                if (geladen != null) {
                    aktuell = geladen;
                }
            }
            speichern(pfad);
        } catch (Exception e) {
            StinkendReich.LOGGER.error("Konfiguration {} konnte nicht gelesen werden, nutze Standardwerte", pfad, e);
        }
    }

    private static void speichern(Path pfad) throws IOException {
        JsonObject json = GSON.toJsonTree(aktuell).getAsJsonObject();
        Map<String, String> erklaerung = new LinkedHashMap<>();
        erklaerung.put("villaBauen", "Villa in jeder neuen Welt am Spawn bauen (true/false)");
        erklaerung.put("villaAnblendung", "Breite des Übergangs vom ebenen Grundstück ins Gelände in Blöcken");
        erklaerung.put("villaSpawnSetzen", "Spieler beim ersten Betreten vor die Villa stellen");
        erklaerung.put("anthropicApiKey", "API-Key von console.anthropic.com für den Butler-Chat. Leer lassen = ohne Claude");
        erklaerung.put("butlerModell", "Claude-Modell für den Butler");
        erklaerung.put("updatePruefen", "Beim Start nach neuen Versionen auf GitHub suchen");
        erklaerung.put("githubToken", "Nur nötig, wenn das GitHub-Repo privat ist");
        JsonObject mitErklaerung = new JsonObject();
        mitErklaerung.add("_erklaerung", GSON.toJsonTree(erklaerung));
        json.entrySet().forEach(e -> mitErklaerung.add(e.getKey(), e.getValue()));
        Files.createDirectories(pfad.getParent());
        Files.writeString(pfad, GSON.toJson(mitErklaerung), StandardCharsets.UTF_8);
    }
}
