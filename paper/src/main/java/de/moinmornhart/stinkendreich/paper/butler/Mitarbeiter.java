package de.moinmornhart.stinkendreich.paper.butler;

import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import de.moinmornhart.stinkendreich.paper.villa.FigurenUebersetzen;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mitarbeiter auf dem Paper-Server (wie {@code MitarbeiterEntity} in der Mod): Die Mitarbeiterkiste aus dem Handy wird
 * aufgestellt, drei Mitarbeiter ziehen ein (Diamanten, Eisen, Kohle). Jeder baut in Ladungen zu 16 ab
 * ({@link Bergbau}) und bringt sie in die Kiste; ist sie voll, macht er Pause. Rechtsklick des Chefs wechselt die
 * Aufgabe. Wie James: Schaufensterpuppe (Haut „mitarbeiter“) über einem unsichtbaren Körper, unverwundbar.
 */
public final class Mitarbeiter implements Listener {
    private static final String[] NAMEN = {"Ede", "Gustav", "Karl", "Heinz", "Otto", "Fritz", "Willi", "Paul"};
    private static final List<String> AUFGABEN = List.of("diamant", "eisen", "kohle", "gold", "smaragd", "redstone", "lapis", "kupfer");
    private static final int LADUNG = 16;

    private final StinkendReichPaper plugin;
    private final NamespacedKey schluesselKiste, schluesselArbeiter, schluesselPuppe;
    private final Map<WanderingTrader, Arbeiter> arbeiter = new HashMap<>();
    private int nummer;

    /** Laufzeit eines Mitarbeiters (Kiste, Chef und Aufgabe stehen im PDC des Körpers). */
    private static final class Arbeiter {
        Mannequin puppe;
        Bergbau bergbau;
        int pause;
    }

    public Mitarbeiter(StinkendReichPaper plugin) {
        this.plugin = plugin;
        this.schluesselKiste = new NamespacedKey(plugin, "mitarbeiterkiste");
        this.schluesselArbeiter = new NamespacedKey(plugin, "mitarbeiter");
        this.schluesselPuppe = new NamespacedKey(plugin, "mitarbeiter_puppe");
        Bukkit.getScheduler().runTaskTimer(plugin, this::takt, 1L, 1L);
    }

    /** Die Mitarbeiterkiste (eine Truhe mit dem Bild der Mod). */
    public ItemStack kiste() {
        ItemStack s = ItemStack.of(Material.CHEST);
        s.setData(DataComponentTypes.ITEM_MODEL, Key.key("stinkendreich", "mitarbeiterkiste"));
        s.setData(DataComponentTypes.ITEM_NAME, Component.translatable("item.stinkendreich.mitarbeiterkiste", "Mitarbeiterkiste")
                .color(NamedTextColor.GOLD));
        s.editPersistentDataContainer(pdc -> pdc.set(schluesselKiste, PersistentDataType.BYTE, (byte) 1));
        return s;
    }

    /** Handy: Kiste ins Inventar. */
    public void bestellen(Player p) {
        for (ItemStack rest : p.getInventory().addItem(kiste()).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
        p.sendMessage(Component.text("Ihre Mitarbeiterkiste ist im Inventar. Stellen Sie sie dort auf, wo gearbeitet werden soll.",
                NamedTextColor.GOLD));
    }

    @EventHandler(ignoreCancelled = true)
    public void beimAufstellen(BlockPlaceEvent e) {
        if (!e.getItemInHand().getPersistentDataContainer().has(schluesselKiste)) {
            return;
        }
        Block kiste = e.getBlockPlaced();
        Player chef = e.getPlayer();
        for (int i = 0; i < 3; i++) {
            Location ort = kiste.getLocation().add(0.5 + (i - 1) * 1.2, 1.0, 0.5);
            if (!ort.getBlock().isPassable()) {
                ort = kiste.getLocation().add(0.5, 1.0, 0.5);
            }
            einstellen(ort, kiste, chef, AUFGABEN.get(i));
        }
        chef.sendMessage(Component.text("Drei Mitarbeiter sind eingetroffen: Diamanten, Eisen und Kohle. Rechtsklick auf einen wechselt "
                + "seine Aufgabe.", NamedTextColor.GOLD));
        plugin.getLogger().info("Mitarbeiter: drei eingestellt bei " + kiste.getX() + " " + kiste.getY() + " " + kiste.getZ());
    }

    private void einstellen(Location ort, Block kiste, Player chef, String aufgabe) {
        String name = NAMEN[Math.floorMod(nummer++, NAMEN.length)];
        WanderingTrader k = ort.getWorld().spawn(ort, WanderingTrader.class, t -> {
            t.setPersistent(true);
            t.setRemoveWhenFarAway(false);
            t.setInvisible(true);
            t.setSilent(true);
            t.setInvulnerable(true);
            t.setDespawnDelay(0);
            t.setCollidable(false);
            PersistentDataContainer d = t.getPersistentDataContainer();
            d.set(schluesselArbeiter, PersistentDataType.STRING, aufgabe + "|" + chef.getUniqueId() + "|" + kiste.getX() + "|"
                    + kiste.getY() + "|" + kiste.getZ() + "|" + name);
        });
        Bukkit.getMobGoals().removeAllGoals(k);
        Mannequin p = FigurenUebersetzen.puppe(ort, "mitarbeiter");
        p.customName(Component.text(name, NamedTextColor.YELLOW));
        p.setCustomNameVisible(true);
        p.getPersistentDataContainer().set(schluesselPuppe, PersistentDataType.STRING, k.getUniqueId().toString());
        Arbeiter a = new Arbeiter();
        a.puppe = p;
        a.pause = 40;
        arbeiter.put(k, a);
    }

    @EventHandler
    public void beimLaden(EntitiesLoadEvent e) {
        Map<String, Mannequin> puppen = new HashMap<>();
        List<WanderingTrader> koerper = new ArrayList<>();
        for (Entity n : e.getEntities()) {
            if (n instanceof Mannequin m && m.getPersistentDataContainer().has(schluesselPuppe)) {
                puppen.put(m.getPersistentDataContainer().get(schluesselPuppe, PersistentDataType.STRING), m);
            } else if (n instanceof WanderingTrader t && t.getPersistentDataContainer().has(schluesselArbeiter)) {
                koerper.add(t);
            }
        }
        for (WanderingTrader t : koerper) {
            Mannequin m = puppen.get(t.getUniqueId().toString());
            if (m != null && !arbeiter.containsKey(t)) {
                Bukkit.getMobGoals().removeAllGoals(t);
                Arbeiter a = new Arbeiter();
                a.puppe = m;
                a.pause = 40;
                arbeiter.put(t, a);
            }
        }
    }

    // ------------------------------------------------------------------

    private String[] daten(WanderingTrader k) {
        return k.getPersistentDataContainer().getOrDefault(schluesselArbeiter, PersistentDataType.STRING, "").split("\\|");
    }

    private Block kiste(WanderingTrader k) {
        String[] d = daten(k);
        return d.length < 5 ? null : k.getWorld().getBlockAt(Integer.parseInt(d[2]), Integer.parseInt(d[3]), Integer.parseInt(d[4]));
    }

    private void takt() {
        arbeiter.entrySet().removeIf(e -> !e.getKey().isValid() || !e.getValue().puppe.isValid());
        for (Map.Entry<WanderingTrader, Arbeiter> e : arbeiter.entrySet()) {
            WanderingTrader k = e.getKey();
            Arbeiter a = e.getValue();
            // Die Puppe steht dort, wo der Körper ist
            Location l = k.getLocation();
            if (a.puppe.getLocation().distanceSquared(l) > 0.0001 || Math.abs(a.puppe.getYaw() - l.getYaw()) > 1) {
                a.puppe.teleport(l);
                a.puppe.setBodyYaw(k.getBodyYaw());
            }
            Block kiste = kiste(k);
            if (kiste == null || !(kiste.getState() instanceof Container)) {
                continue;
            }
            if (a.bergbau == null) {
                if (a.pause > 0) {
                    a.pause--;
                    continue;
                }
                if (voll(kiste)) {
                    a.pause = 20 * 30;
                    continue;
                }
                Bergbau.Rohstoff r = Bergbau.ROHSTOFFE.get(daten(k)[0]);
                a.bergbau = new Bergbau(plugin, k, a.puppe, r, LADUNG, kiste.getLocation().add(0.5, 1, 0.5));
                continue;
            }
            Bergbau.Stand s = a.bergbau.tick();
            if (s == Bergbau.Stand.LAEUFT) {
                continue;
            }
            a.bergbau.aufraeumen();
            if (s == Bergbau.Stand.NICHTS) {
                sagen(k, "Hier finde ich keine " + a.bergbau.rohstoff().anzeige() + " mehr.");
                a.pause = 20 * 60;
            } else {
                abliefern(k, a, kiste);
                a.pause = 20;
            }
            a.bergbau = null;
        }
    }

    private static boolean voll(Block kiste) {
        return ((Container) kiste.getState()).getInventory().firstEmpty() < 0;
    }

    private void abliefern(WanderingTrader k, Arbeiter a, Block kiste) {
        int n = 0;
        for (ItemStack s : a.bergbau.beute()) {
            n += s.getAmount();
            for (ItemStack rest : ((Container) kiste.getState()).getInventory().addItem(s).values()) {
                kiste.getWorld().dropItemNaturally(kiste.getLocation().add(0.5, 1, 0.5), rest);
            }
        }
        a.puppe.swingMainHand();
        kiste.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, kiste.getLocation().add(0.5, 1, 0.5), 8, 0.3, 0.3, 0.3, 0);
        plugin.getLogger().info("Mitarbeiter: " + n + " " + a.bergbau.rohstoff().anzeige() + " in die Kiste");
        sagen(k, n + " " + a.bergbau.rohstoff().anzeige() + " sind in der Kiste.");
    }

    /** Mitarbeiter reden nicht im Chat, nur kurz über der Schnellleiste, wenn der Chef in der Nähe ist. */
    private void sagen(WanderingTrader k, String text) {
        String[] d = daten(k);
        Player chef = d.length > 1 ? Bukkit.getPlayer(UUID.fromString(d[1])) : null;
        if (chef != null && chef.getWorld().equals(k.getWorld()) && chef.getLocation().distanceSquared(k.getLocation()) < 32 * 32) {
            chef.sendActionBar(Component.text("[" + (d.length > 5 ? d[5] : "Mitarbeiter") + "] ", NamedTextColor.YELLOW)
                    .append(Component.text(text, NamedTextColor.WHITE)));
        }
    }

    /** Rechtsklick des Chefs: nächste Aufgabe (was er gerade tut, bricht er ab). */
    @EventHandler
    public void beimKlick(PlayerInteractEntityEvent e) {
        WanderingTrader k = null;
        if (e.getRightClicked() instanceof WanderingTrader t && arbeiter.containsKey(t)) {
            k = t;
        } else if (e.getRightClicked() instanceof Mannequin m && m.getPersistentDataContainer().has(schluesselPuppe)) {
            for (Map.Entry<WanderingTrader, Arbeiter> a : arbeiter.entrySet()) {
                if (a.getValue().puppe.equals(m)) {
                    k = a.getKey();
                }
            }
        }
        if (k == null) {
            return;
        }
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        String[] d = daten(k);
        if (!e.getPlayer().getUniqueId().toString().equals(d[1])) {
            e.getPlayer().sendActionBar(Component.text((d.length > 5 ? d[5] : "Er") + " arbeitet nicht für dich.", NamedTextColor.GRAY));
            return;
        }
        String neu = AUFGABEN.get((AUFGABEN.indexOf(d[0]) + 1) % AUFGABEN.size());
        d[0] = neu;
        k.getPersistentDataContainer().set(schluesselArbeiter, PersistentDataType.STRING, String.join("|", d));
        Arbeiter a = arbeiter.get(k);
        if (a.bergbau != null) {
            a.bergbau.abbrechen();
            a.bergbau = null;
        }
        a.pause = 20;
        e.getPlayer().sendActionBar(Component.text((d.length > 5 ? d[5] : "Er") + " sucht jetzt: " + Bergbau.ROHSTOFFE.get(neu).anzeige(),
                NamedTextColor.GOLD));
    }

    /** Wird die Kiste abgebaut, gehen ihre Mitarbeiter nach Hause. */
    @EventHandler(ignoreCancelled = true)
    public void beimAbbauen(BlockBreakEvent e) {
        arbeiter.entrySet().removeIf(en -> {
            if (e.getBlock().equals(kiste(en.getKey()))) {
                if (en.getValue().bergbau != null) {
                    en.getValue().bergbau.abbrechen();
                }
                en.getKey().getWorld().spawnParticle(Particle.CLOUD, en.getKey().getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.02);
                en.getValue().puppe.remove();
                en.getKey().remove();
                return true;
            }
            return false;
        });
    }

    @EventHandler
    public void beimSchaden(EntityDamageEvent e) {
        if ((e.getEntity().getPersistentDataContainer().has(schluesselArbeiter) || e.getEntity().getPersistentDataContainer().has(schluesselPuppe))
                && e.getCause() != EntityDamageEvent.DamageCause.KILL) {
            e.setCancelled(true);
        }
    }

    /** Für Tests: wie viele arbeiten gerade und was tun sie? */
    public String uebersicht() {
        StringBuilder sb = new StringBuilder();
        arbeiter.forEach((k, a) -> sb.append(daten(k).length > 5 ? daten(k)[5] : "?").append(": ")
                .append(a.bergbau == null ? "Pause" : a.bergbau.status()).append("; "));
        return sb.toString();
    }
}
