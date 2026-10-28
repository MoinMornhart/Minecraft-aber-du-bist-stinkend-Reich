package de.moinmornhart.stinkendreich.paper.bedrock;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Ersatzfiguren für Bedrock-Spieler: Geyser zeigt keine Anzeige-Figuren (Möbel, Türen, Autos …). Für jede Anzeige-Figur
 * mit einem unserer Modelle steht deshalb eine leere Text-Figur am selben Ort – nur für Bedrock-Spieler sichtbar. Geyser
 * meldet ihr Erscheinen ({@link GeyserAnbindung}), und Bedrock zeichnet dort statt eines Namensschilds die Figur
 * {@link BedrockPaket#FIGUR} mit dem passenden Modell, gedreht, verschoben und skaliert wie das Original.
 *
 * <p>Die Ersatzfiguren gibt es nur, solange ein Bedrock-Spieler online ist; sie werden nicht gespeichert. Bewegt sich ein
 * Original (Autos, Aufzug, Zugbrücke, Inkognito), zieht die Ersatzfigur mit: Figuren, die sich gerade bewegt haben,
 * werden jeden Tick geprüft, ruhende alle zehn Ticks, das Modell alle 20 Ticks.
 */
public final class Zwillinge implements Listener {
    /** Was zu einer Ersatzfigur gehört. */
    private static final class Zwilling {
        final ItemDisplay quelle;
        final TextDisplay figur;
        Lage lage;
        Location ort;
        Transformation trafo;
        String modell;
        int ruhe;

        Zwilling(ItemDisplay quelle, TextDisplay figur) {
            this.quelle = quelle;
            this.figur = figur;
        }
    }

    private final Plugin plugin;
    private final Map<String, Integer> varianten;
    private final Predicate<UUID> istBedrock;
    private final Melder melder;
    /** Original → Ersatzfigur (nur im Haupt-Thread). */
    private final Map<UUID, Zwilling> zwillinge = new HashMap<>();
    /** Ersatzfigur → Lage (Geyser fragt aus seinem eigenen Thread). */
    private final Map<UUID, Lage> lagen = new ConcurrentHashMap<>();
    private boolean aktiv;
    private int takt;

    /** Meldet Änderungen an Geyser (Variante, Maßstab, Eigenschaften einer schon sichtbaren Ersatzfigur). */
    public interface Melder {
        void aendern(UUID figur, Lage lage);
    }

    public Zwillinge(Plugin plugin, Map<String, Integer> varianten, Predicate<UUID> istBedrock, Melder melder) {
        this.plugin = plugin;
        this.varianten = varianten;
        this.istBedrock = istBedrock;
        this.melder = melder;
    }

    public void starten() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        for (Player p : Bukkit.getOnlinePlayers()) {
            beitreten(p);
        }
    }

    /** Lage einer Ersatzfigur (für Geysers Spawn-Ereignis), null wenn die Figur keine ist. */
    public Lage lage(UUID figur) {
        return lagen.get(figur);
    }

    public int anzahl() {
        return zwillinge.size();
    }

    // ------------------------------------------------------------------
    // Spieler
    // ------------------------------------------------------------------

    @EventHandler
    public void beimBetreten(PlayerJoinEvent e) {
        beitreten(e.getPlayer());
    }

    @EventHandler
    public void beimVerlassen(PlayerQuitEvent e) {
        UUID weg = e.getPlayer().getUniqueId();
        // Erst im nächsten Tick: der Spieler zählt beim Verlassen noch als online
        Bukkit.getScheduler().runTask(plugin, () -> {
            boolean nochBedrock = Bukkit.getOnlinePlayers().stream().anyMatch(p -> !p.getUniqueId().equals(weg) && istBedrock.test(p.getUniqueId()));
            if (aktiv && !nochBedrock) {
                abschalten();
            }
        });
    }

    private void beitreten(Player p) {
        if (!istBedrock.test(p.getUniqueId())) {
            return;
        }
        if (!aktiv) {
            aktiv = true;
            for (World w : Bukkit.getWorlds()) {
                for (ItemDisplay d : w.getEntitiesByClass(ItemDisplay.class)) {
                    anlegen(d);
                }
            }
            plugin.getLogger().info("Bedrock: " + zwillinge.size() + " Ersatzfiguren für Bedrock-Spieler angelegt");
        }
        for (Zwilling z : zwillinge.values()) {
            p.showEntity(plugin, z.figur);
        }
    }

    private void abschalten() {
        aktiv = false;
        for (Zwilling z : zwillinge.values()) {
            z.figur.remove();
        }
        zwillinge.clear();
        lagen.clear();
        plugin.getLogger().info("Bedrock: kein Bedrock-Spieler mehr online, Ersatzfiguren entfernt");
    }

    // ------------------------------------------------------------------
    // Figuren kommen und gehen
    // ------------------------------------------------------------------

    @EventHandler
    public void beimHinzufuegen(EntityAddToWorldEvent e) {
        if (aktiv && e.getEntity() instanceof ItemDisplay d) {
            // Erst im nächsten Tick: beim Erzeugen wird das Modell manchmal erst nach dem Hinzufügen gesetzt
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (aktiv && d.isValid() && !zwillinge.containsKey(d.getUniqueId())) {
                    anlegen(d);
                }
            });
        }
    }

    @EventHandler
    public void beimEntfernen(EntityRemoveFromWorldEvent e) {
        if (e.getEntity() instanceof ItemDisplay) {
            // Nicht sofort: beim Entladen eines Gebiets darf Paper währenddessen keine Figuren entfernen
            UUID quelle = e.getEntity().getUniqueId();
            Bukkit.getScheduler().runTask(plugin, () -> entfernen(quelle));
        }
    }

    private void anlegen(ItemDisplay d) {
        String modell = modell(d);
        Integer variante = modell == null ? null : varianten.get(modell);
        if (variante == null) {
            return;
        }
        Location ort = d.getLocation();
        Lage lage = Lage.von(d, variante);
        Location stelle = ort.clone();
        stelle.setYaw(0);
        stelle.setPitch(0);
        TextDisplay figur = d.getWorld().spawn(stelle, TextDisplay.class, t -> {
            t.setPersistent(false);
            t.setVisibleByDefault(false);
            t.text(Component.empty());
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            t.setShadowed(false);
            t.addScoreboardTag("stinkendreich_bedrock");
            // Vor dem ersten Senden eintragen: Geyser fragt beim Erscheinen nach der Lage
            lagen.put(t.getUniqueId(), lage);
        });
        Zwilling z = new Zwilling(d, figur);
        z.lage = lage;
        z.ort = ort;
        z.trafo = d.getTransformation();
        z.modell = modell;
        zwillinge.put(d.getUniqueId(), z);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (istBedrock.test(p.getUniqueId())) {
                p.showEntity(plugin, figur);
            }
        }
    }

    private void entfernen(UUID quelle) {
        Zwilling z = zwillinge.remove(quelle);
        if (z != null) {
            lagen.remove(z.figur.getUniqueId());
            if (z.figur.isValid()) {
                z.figur.remove();
            }
        }
    }

    /** Item-Modell der Anzeige-Figur („stinkendreich:…“), null ohne eigenes Modell. */
    private static String modell(ItemDisplay d) {
        ItemStack item = d.getItemStack();
        if (item == null || item.isEmpty()) {
            return null;
        }
        Key k = item.getData(DataComponentTypes.ITEM_MODEL);
        return k == null || !k.namespace().equals("stinkendreich") ? null : k.asString();
    }

    // ------------------------------------------------------------------
    // Jeden Tick: mitziehen
    // ------------------------------------------------------------------

    private void tick() {
        if (!aktiv) {
            return;
        }
        takt++;
        List<UUID> weg = new ArrayList<>();
        for (Iterator<Map.Entry<UUID, Zwilling>> it = zwillinge.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Zwilling> e = it.next();
            Zwilling z = e.getValue();
            if (!z.quelle.isValid() || !z.figur.isValid()) {
                if (!z.quelle.isValid()) {
                    weg.add(e.getKey());
                }
                continue;
            }
            // Ruhende Figuren seltener prüfen (verteilt über die Ticks)
            if (z.ruhe > 40 && (takt + e.getKey().hashCode()) % 10 != 0) {
                continue;
            }
            boolean modellPruefen = (takt + e.getKey().hashCode()) % 20 == 0;
            String modell = modellPruefen ? modell(z.quelle) : z.modell;
            Location ort = z.quelle.getLocation();
            Transformation trafo = z.quelle.getTransformation();
            boolean bewegt = !gleich(ort, z.ort);
            boolean anders = bewegt || !trafo.equals(z.trafo) || !Objects.equals(modell, z.modell);
            if (!anders) {
                z.ruhe++;
                continue;
            }
            z.ruhe = 0;
            z.ort = ort;
            z.trafo = trafo;
            z.modell = modell;
            Integer variante = modell == null ? null : varianten.get(modell);
            if (variante == null) {
                weg.add(e.getKey());
                continue;
            }
            if (bewegt) {
                Location stelle = ort.clone();
                stelle.setYaw(0);
                stelle.setPitch(0);
                z.figur.teleport(stelle);
            }
            Lage neu = Lage.von(z.quelle, variante);
            if (!neu.equals(z.lage)) {
                z.lage = neu;
                lagen.put(z.figur.getUniqueId(), neu);
                melder.aendern(z.figur.getUniqueId(), neu);
            }
        }
        weg.forEach(this::entfernen);
    }

    private static boolean gleich(Location a, Location b) {
        return a.getWorld() == b.getWorld() && a.getX() == b.getX() && a.getY() == b.getY() && a.getZ() == b.getZ()
                && a.getYaw() == b.getYaw() && a.getPitch() == b.getPitch();
    }

    /** Beim Abschalten des Plugins: alle Ersatzfiguren weg (sie werden ohnehin nicht gespeichert). */
    public void beenden() {
        for (Zwilling z : zwillinge.values()) {
            z.figur.remove();
        }
        zwillinge.clear();
        lagen.clear();
    }
}
