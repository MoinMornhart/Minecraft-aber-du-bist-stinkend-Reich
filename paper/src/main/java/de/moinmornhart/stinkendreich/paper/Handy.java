package de.moinmornhart.stinkendreich.paper;

import de.moinmornhart.stinkendreich.paper.butler.Butler;
import de.moinmornhart.stinkendreich.paper.villa.Villen;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Das goldene Handy auf dem Paper-Server: ein Item mit dem Modell der Mod, Rechtsklick öffnet ein Dialogfenster
 * (James rufen, nach Hause schicken, Villen der anderen). Jeder Spieler bekommt es einmal beim ersten Betreten.
 */
public final class Handy implements Listener {
    private final StinkendReichPaper plugin;
    private final NamespacedKey schluesselHandy, schluesselGeschenk;

    public Handy(StinkendReichPaper plugin) {
        this.plugin = plugin;
        schluesselHandy = new NamespacedKey(plugin, "handy");
        schluesselGeschenk = new NamespacedKey(plugin, "handy_geschenkt");
    }

    public ItemStack handy() {
        ItemStack s = ItemStack.of(Material.PAPER);
        s.setData(DataComponentTypes.ITEM_MODEL, Key.key("stinkendreich", "handy"));
        s.setData(DataComponentTypes.ITEM_NAME, Component.text("Goldenes Handy", NamedTextColor.GOLD));
        s.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        s.editPersistentDataContainer(pdc -> pdc.set(schluesselHandy, PersistentDataType.BYTE, (byte) 1));
        return s;
    }

    private boolean istHandy(ItemStack s) {
        return s != null && !s.isEmpty() && s.getPersistentDataContainer().has(schluesselHandy);
    }

    @EventHandler
    public void beimBetreten(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!p.getPersistentDataContainer().has(schluesselGeschenk)) {
            p.getPersistentDataContainer().set(schluesselGeschenk, PersistentDataType.BYTE, (byte) 1);
            p.getInventory().addItem(handy());
            p.sendMessage(Component.text("Dein goldenes Handy liegt im Inventar. Rechtsklick öffnet es.", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
        }
    }

    @EventHandler
    public void beimKlick(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !istHandy(e.getItem())
                || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        // Ein Klick auf einen Mod-Block (Tür, Sofa …) oder einen Block wie Truhe, Tür, Knopf geht vor (wie in der Mod;
        // beim Schleichen öffnet sich das Handy trotzdem)
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                && (plugin.bloecke().zustand(e.getClickedBlock()) != null
                || e.getClickedBlock().getType().isInteractable() && !e.getPlayer().isSneaking())) {
            return;
        }
        e.setCancelled(true);
        oeffnen(e.getPlayer());
    }

    public void oeffnen(Player p) {
        Butler butler = plugin.butler();
        List<Dialoge.Knopf> knoepfe = new ArrayList<>();
        knoepfe.add(Dialoge.Knopf.von("James rufen", (s, a) -> butler.befehl(s, "komm")));
        knoepfe.add(Dialoge.Knopf.von("James: warten", (s, a) -> butler.befehl(s, "warte")));
        knoepfe.add(Dialoge.Knopf.von("James nach Hause", (s, a) -> butler.befehl(s, "nach hause")));
        knoepfe.add(Dialoge.Knopf.von("Smart Home", (s, a) -> plugin.smartHome().seite(s)));
        knoepfe.add(Dialoge.Knopf.von("Gästeliste", (s, a) -> gaesteSeite(s)));
        knoepfe.add(Dialoge.Knopf.von("Lieferservice", (s, a) -> lieferSeite(s)));
        knoepfe.add(Dialoge.Knopf.von("Bodyguards rufen", (s, a) -> plugin.bodyguards().rufen(s)));
        knoepfe.add(Dialoge.Knopf.von("Bodyguards heim", (s, a) -> plugin.bodyguards().entlassen(s)));
        Villen.Villa eigene = plugin.villen().vonBesitzer(p.getUniqueId());
        if (eigene != null) {
            knoepfe.add(Dialoge.Knopf.von("Zu meiner Villa", (s, a) -> s.teleport(eigene.spawn(plugin.bauplan()))));
        }
        for (Villen.Villa v : plugin.villen().alle()) {
            if (v != eigene && !v.name().equals("Villa")) {
                knoepfe.add(Dialoge.Knopf.von(v.name(), (s, a) -> s.teleport(v.spawn(plugin.bauplan()))));
            }
        }
        Dialoge.zeigen(p, "Goldenes Handy", "Was darf es sein?", List.of(), knoepfe);
    }

    // ------------------------------------------------------------------
    // Lieferservice (wie in der Mod: Tiere neben dem Spieler, Waren ins Inventar)
    // ------------------------------------------------------------------

    private static final String[][] ANGEBOT = {
            {"kuehe", "2 Kühe", "2 Kühe"}, {"schweine", "2 Schweine", "2 Schweine"}, {"schafe", "2 Schafe", "2 Schafe"},
            {"huehner", "4 Hühner", "4 Hühner"}, {"hund", "Hund (zahm)", "ein Hund (er gehört schon Ihnen)"},
            {"katze", "Katze (zahm)", "eine Katze (sie gehört schon Ihnen)"}, {"papagei", "Papagei (zahm)", "ein Papagei (er gehört schon Ihnen)"},
            {"festessen", "Festessen", "ein Festessen (in Ihrem Inventar)"}, {"feuerwerk", "64 Raketen", "64 Feuerwerksraketen (in Ihrem Inventar)"},
            {"gold", "Ein Sack Gold", "Gold (in Ihrem Inventar)"}};

    private void lieferSeite(Player p) {
        List<Dialoge.Knopf> knoepfe = new ArrayList<>();
        for (String[] a : ANGEBOT) {
            knoepfe.add(Dialoge.Knopf.von(a[1], (s, x) -> bestellen(s, a[0])));
        }
        Dialoge.zeigen(p, "Lieferservice", "James bringt es vorbei.", List.of(), knoepfe);
    }

    /** Bestellung (auch /handy liefer <was>): nach ein paar Sekunden ist sie da. */
    public void bestellen(Player p, String was) {
        String[] eintrag = null;
        for (String[] a : ANGEBOT) {
            if (a[0].equals(was)) {
                eintrag = a;
            }
        }
        if (eintrag == null) {
            return;
        }
        String satz = eintrag[2];
        p.sendActionBar(Component.text("Ihre Bestellung ist unterwegs, Sir.", NamedTextColor.GOLD));
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            org.bukkit.Location bei = p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(2));
            switch (was) {
                case "kuehe" -> tiere(p, bei, org.bukkit.entity.EntityType.COW, 2);
                case "schweine" -> tiere(p, bei, org.bukkit.entity.EntityType.PIG, 2);
                case "schafe" -> tiere(p, bei, org.bukkit.entity.EntityType.SHEEP, 2);
                case "huehner" -> tiere(p, bei, org.bukkit.entity.EntityType.CHICKEN, 4);
                case "hund" -> tiere(p, bei, org.bukkit.entity.EntityType.WOLF, 1);
                case "katze" -> tiere(p, bei, org.bukkit.entity.EntityType.CAT, 1);
                case "papagei" -> tiere(p, bei, org.bukkit.entity.EntityType.PARROT, 1);
                case "festessen" -> waren(p, new ItemStack(Material.COOKED_BEEF, 32), new ItemStack(Material.GOLDEN_CARROT, 32),
                        new ItemStack(Material.CAKE, 2), new ItemStack(Material.PUMPKIN_PIE, 16));
                case "feuerwerk" -> waren(p, new ItemStack(Material.FIREWORK_ROCKET, 64));
                default -> waren(p, new ItemStack(Material.GOLD_INGOT, 64), new ItemStack(Material.GOLD_BLOCK, 16));
            }
            bei.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, bei.clone().add(0, 1, 0), 30, 1.5, 0.8, 1.5, 0.1);
            p.sendMessage(Component.text("James: Ihre Lieferung, Sir: " + satz + ".", NamedTextColor.GOLD));
            plugin.getLogger().info("Lieferservice: " + was + " für " + p.getName());
        }, 60L);
    }

    private static void tiere(Player herr, org.bukkit.Location bei, org.bukkit.entity.EntityType typ, int anzahl) {
        for (int i = 0; i < anzahl; i++) {
            org.bukkit.entity.Entity e = bei.getWorld().spawnEntity(bei.clone().add(i % 2, 0, i / 2), typ);
            if (e instanceof org.bukkit.entity.Tameable zahm) {
                zahm.setTamed(true);
                zahm.setOwner(herr);
            }
        }
    }

    private static void waren(Player p, ItemStack... sachen) {
        for (ItemStack s : sachen) {
            p.getInventory().addItem(s).values().forEach(rest -> p.getWorld().dropItem(p.getLocation(), rest));
        }
    }

    /** Gästeliste: Namen eintragen oder streichen (Gäste kommen ohne Karte durch Tor und Türen). */
    private void gaesteSeite(Player p) {
        Villen.Villa v = plugin.villen().vonBesitzer(p.getUniqueId());
        if (v == null) {
            p.sendActionBar(Component.text("Du wohnst in keiner Villa.", NamedTextColor.RED));
            return;
        }
        List<Dialoge.Knopf> knoepfe = new ArrayList<>();
        knoepfe.add(Dialoge.Knopf.von("Eintragen", (s, a) -> {
            String name = a.getText("name");
            if (name != null && !name.isBlank()) {
                plugin.gaeste().eintragen(v.nr(), name.trim());
                s.sendActionBar(Component.text(name.trim() + " steht auf der Gästeliste.", NamedTextColor.GOLD));
            }
        }));
        for (String gast : plugin.gaeste().von(v.nr())) {
            knoepfe.add(Dialoge.Knopf.von("✕ " + gast, (s, a) -> plugin.gaeste().streichen(v.nr(), gast)));
        }
        Dialoge.zeigen(p, "Gästeliste", "Gäste kommen ohne Zugangskarte durch Tor, Türen und über die Brücke.",
                List.of(io.papermc.paper.registry.data.dialog.input.DialogInput.text("name", Component.text("Name")).build()), knoepfe);
    }
}
