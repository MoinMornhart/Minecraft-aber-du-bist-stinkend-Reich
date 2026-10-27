package de.moinmornhart.stinkendreich.paper.block;

import de.moinmornhart.stinkendreich.paper.Dialoge;
import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fernseher wie in der Server-Version der Mod: Klick auf den Bildschirm öffnet ein Fenster (YouTube-Link eingeben,
 * Video im Browser abspielen, ausschalten). Ein Video im Spiel gibt es ohne Mod nicht; ist eins gewählt, zeigt der
 * Bildschirm „Video bereit“ (eigenes Bild im Ressourcenpaket).
 */
public final class Fernseher implements Listener {
    private static final Pattern YOUTUBE = Pattern.compile("(?:v=|youtu\\.be/|shorts/|embed/)([A-Za-z0-9_-]{11})");

    private final StinkendReichPaper plugin;
    private final NamespacedKey schluesselFeld, schluesselVideo;

    public Fernseher(StinkendReichPaper plugin) {
        this.plugin = plugin;
        this.schluesselFeld = new NamespacedKey(plugin, "fernseher");
        this.schluesselVideo = new NamespacedKey(plugin, "video");
    }

    /** Klickfelder vor dem Bildschirm (je Block Breite eins), gehören zur Anzeige-Figur des Fernsehers. */
    public void klickfelder(ItemDisplay tv, float breite, float hoehe) {
        Location l = tv.getLocation();
        Vector vorne = vorne(l.getYaw()), seite = new Vector(vorne.getZ(), 0, -vorne.getX());
        int n = Math.max(1, Math.round(breite));
        double unten = 1.5 * hoehe / 2.8125 - hoehe / 2.0;
        for (int i = 0; i < n; i++) {
            double versatz = (i + 0.5) - n / 2.0;
            Location ort = l.clone().add(seite.clone().multiply(versatz)).add(vorne.clone().multiply(0.2)).add(0, unten, 0);
            l.getWorld().spawn(ort, Interaction.class, f -> {
                f.setInteractionWidth(1.0F);
                f.setInteractionHeight(hoehe);
                f.setResponsive(true);
                f.setPersistent(true);
                f.getPersistentDataContainer().set(schluesselFeld, PersistentDataType.STRING, tv.getUniqueId().toString());
            });
        }
    }

    /** Richtung, in die der Bildschirm zeigt (zum Raum hin): die Blickrichtung des Gierwinkels der Marke. */
    private static Vector vorne(float gier) {
        double r = Math.toRadians(gier);
        return new Vector(-Math.sin(r), 0, Math.cos(r));
    }

    @EventHandler
    public void beimKlick(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !(e.getRightClicked() instanceof Interaction feld)) {
            return;
        }
        String id = feld.getPersistentDataContainer().get(schluesselFeld, PersistentDataType.STRING);
        if (id == null) {
            return;
        }
        e.setCancelled(true);
        if (Bukkit.getEntity(UUID.fromString(id)) instanceof ItemDisplay tv) {
            menue(e.getPlayer(), tv);
        }
    }

    /** Fenster des Fernsehers (auch für Tests). */
    public void menue(Player p, ItemDisplay tv) {
        String video = tv.getPersistentDataContainer().get(schluesselVideo, PersistentDataType.STRING);
        List<Dialoge.Knopf> k = new ArrayList<>();
        k.add(Dialoge.Knopf.von("Auf dem Fernseher anzeigen", (s, a) -> anzeigen(s, tv, a.getText("link"))));
        String text = "Ohne Mod bleibt der Bildschirm dunkel. Das Video läuft im Browser deines PCs.";
        if (video != null) {
            String link = "https://www.youtube.com/watch?v=" + video;
            text = "Läuft: " + link + "\n" + text;
            k.add(Dialoge.Knopf.link("Auf dem PC abspielen (Browser)", link));
        }
        k.add(Dialoge.Knopf.von("Ausschalten", (s, a) -> anzeigen(s, tv, null)));
        Dialoge.zeigen(p, "Fernseher", text,
                List.of(DialogInput.text("link", Component.text("YouTube-Link")).width(300).maxLength(200).build()), k, 1, 210, false);
    }

    /** Link übernehmen (null = aus): merken und das Bild „Video bereit“ auf den Bildschirm legen. */
    public void anzeigen(Player p, ItemDisplay tv, String link) {
        if (link == null || link.isBlank()) {
            tv.getPersistentDataContainer().remove(schluesselVideo);
            bild(tv, "polymer/fernseher");
            p.sendActionBar(Component.text("Fernseher aus.", NamedTextColor.GRAY));
            return;
        }
        Matcher m = YOUTUBE.matcher(link.trim());
        String video = m.find() ? m.group(1) : link.trim().matches("[A-Za-z0-9_-]{11}") ? link.trim() : null;
        if (video == null) {
            p.sendActionBar(Component.text("Das ist kein YouTube-Link.", NamedTextColor.RED));
            return;
        }
        tv.getPersistentDataContainer().set(schluesselVideo, PersistentDataType.STRING, video);
        bild(tv, "polymer/fernseher_youtube");
        plugin.getLogger().info("Fernseher: Video " + video);
        p.sendActionBar(Component.text("Video bereit – „Auf dem PC abspielen“ öffnet es im Browser.", NamedTextColor.GOLD));
    }

    /** Bild des Fernsehers (Item-Modell aus dem Ressourcenpaket). */
    private static void bild(ItemDisplay tv, String modell) {
        ItemStack s = ItemStack.of(Material.PAPER);
        s.setData(DataComponentTypes.ITEM_MODEL, Key.key("stinkendreich", modell));
        tv.setItemStack(s);
    }

    /** Nächster Fernseher zu einem Ort (für Tests). */
    public ItemDisplay naechster(Location l) {
        ItemDisplay best = null;
        double d = 64 * 64;
        for (Entity e : l.getWorld().getNearbyEntities(l, 64, 32, 64)) {
            if (e instanceof Interaction f && f.getPersistentDataContainer().has(schluesselFeld)
                    && Bukkit.getEntity(UUID.fromString(f.getPersistentDataContainer().get(schluesselFeld, PersistentDataType.STRING))) instanceof ItemDisplay tv
                    && tv.getLocation().distanceSquared(l) < d) {
                d = tv.getLocation().distanceSquared(l);
                best = tv;
            }
        }
        return best;
    }
}
