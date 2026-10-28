package de.moinmornhart.stinkendreich.paper.bedrock;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Mannequin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.geysermc.geyser.api.skin.Skin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bedrock-Spieler über Geyser (Paper-Plugin): baut beim Start das Bedrock-Paket, meldet es bei Geyser an, verwaltet
 * die Ersatzfiguren ({@link Zwillinge}) und die Häute der Schaufensterpuppen. Wird nur angelegt, wenn das Plugin
 * „Geyser-Spigot“ da ist und {@code bedrock.an} in der Config gilt.
 */
public final class Bedrock implements Listener {
    private final Plugin plugin;
    private final Map<String, byte[]> java;
    private final GeyserAnbindung geyser;
    private final Zwillinge zwillinge;
    /** Schaufensterpuppe → Haut („stinkendreich:entity/butler“) und schmale Arme. */
    private final Map<UUID, String[]> puppen = new ConcurrentHashMap<>();
    private final Map<String, Skin> haeute = new ConcurrentHashMap<>();

    private Bedrock(Plugin plugin, Map<String, byte[]> java, Path paketDatei, BedrockPaket paket) {
        this.plugin = plugin;
        this.java = java;
        Zwillinge[] z = new Zwillinge[1];
        this.geyser = new GeyserAnbindung(plugin, paketDatei, id -> z[0] == null ? null : z[0].lage(id), this::haut, this::schmal);
        this.zwillinge = new Zwillinge(plugin, paket.varianten(), geyser::istBedrock, geyser::aendern);
        z[0] = zwillinge;
    }

    /**
     * Bedrock-Anbindung starten (im {@code onEnable}, Geyser ist dann schon geladen, startet aber erst nach allen
     * Plugins – rechtzeitig für die Anmeldung).
     *
     * @param java Dateien des Java-Ressourcenpakets („assets/…“ → Inhalt)
     */
    public static Bedrock starten(Plugin plugin, Map<String, byte[]> java) throws Exception {
        Path datei = plugin.getDataFolder().toPath().resolve("bedrock").resolve("stinkendreich.mcpack");
        BedrockPaket paket = new BedrockPaket(plugin.getLogger(), java);
        paket.bauen(datei);
        Bedrock b = new Bedrock(plugin, java, datei, paket);
        b.geyser.anmelden();
        b.zwillinge.starten();
        Bukkit.getPluginManager().registerEvents(b, plugin);
        for (World w : Bukkit.getWorlds()) {
            w.getEntitiesByClass(Mannequin.class).forEach(b::puppeMerken);
        }
        return b;
    }

    public void beenden() {
        zwillinge.beenden();
    }

    public int ersatzfiguren() {
        return zwillinge.anzahl();
    }

    // ------------------------------------------------------------------
    // Häute der Schaufensterpuppen
    // ------------------------------------------------------------------

    @EventHandler
    public void beimHinzufuegen(EntityAddToWorldEvent e) {
        if (e.getEntity() instanceof Mannequin m) {
            puppeMerken(m);
        }
    }

    private void puppeMerken(Mannequin m) {
        ResolvableProfile profil = m.getProfile();
        if (profil == null || profil.skinPatch() == null) {
            return;
        }
        Key koerper = profil.skinPatch().body();
        if (koerper != null && koerper.namespace().equals("stinkendreich")) {
            boolean schmal = profil.skinPatch().model() == org.bukkit.profile.PlayerTextures.SkinModel.SLIM;
            puppen.put(m.getUniqueId(), new String[]{koerper.asString(), schmal ? "schmal" : ""});
        }
    }

    /** Haut einer Schaufensterpuppe für Geyser (RGBA-Bytes), null für alles andere. */
    private Skin haut(UUID figur) {
        String[] p = puppen.get(figur);
        if (p == null) {
            return null;
        }
        return haeute.computeIfAbsent(p[0], k -> {
            String[] t = k.split(":", 2);
            byte[] png = java.get("assets/" + t[0] + "/textures/" + t[1] + ".png");
            if (png == null) {
                return null;
            }
            try {
                BufferedImage bild = ImageIO.read(new ByteArrayInputStream(png));
                ByteArrayOutputStream aus = new ByteArrayOutputStream(bild.getWidth() * bild.getHeight() * 4);
                for (int y = 0; y < bild.getHeight(); y++) {
                    for (int x = 0; x < bild.getWidth(); x++) {
                        int argb = bild.getRGB(x, y);
                        aus.write((argb >> 16) & 0xFF);
                        aus.write((argb >> 8) & 0xFF);
                        aus.write(argb & 0xFF);
                        aus.write((argb >>> 24) & 0xFF);
                    }
                }
                return new Skin(k, aus.toByteArray());
            } catch (Exception e) {
                plugin.getLogger().warning("Bedrock: Haut " + k + " nicht lesbar: " + e);
                return null;
            }
        });
    }

    /** Schmale Arme (Tänzerinnen)? */
    boolean schmal(UUID figur) {
        String[] p = puppen.get(figur);
        return p != null && !p[1].isEmpty();
    }
}
