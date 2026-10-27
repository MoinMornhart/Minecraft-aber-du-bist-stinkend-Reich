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
        // Ein Klick auf einen Mod-Block (Tür, Sofa …) geht vor
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                && plugin.bloecke().zustand(e.getClickedBlock()) != null) {
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
