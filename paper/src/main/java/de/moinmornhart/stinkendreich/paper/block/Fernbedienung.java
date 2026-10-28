package de.moinmornhart.stinkendreich.paper.block;

import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

/**
 * Fernbedienung wie in der Mod: Liegt sie auf einem Möbel, nimmt ein Rechtsklick sie in die Hand – auch im Sitzen, ohne
 * aufzustehen. In der Hand öffnet ein Rechtsklick das Fenster des Fernsehers, den man anschaut (sonst des nächsten).
 * Schleichen + Rechtsklick auf die Oberseite eines Blocks legt sie wieder ab.
 */
public final class Fernbedienung implements Listener {
    private final StinkendReichPaper plugin;
    private final VirtuelleBloecke bloecke;
    private final NamespacedKey schluessel;

    public Fernbedienung(StinkendReichPaper plugin, VirtuelleBloecke bloecke, Mechanik mechanik) {
        this.plugin = plugin;
        this.bloecke = bloecke;
        this.schluessel = new NamespacedKey(plugin, "fernbedienung");
        mechanik.anmelden("fernbedienung", (p, b, z) -> {
            nehmen(p, b);
            return true;
        });
    }

    /** Die Fernbedienung als Gegenstand (Papier mit dem Bild der Mod). */
    public ItemStack gegenstand() {
        ItemStack s = ItemStack.of(Material.PAPER);
        s.setData(DataComponentTypes.ITEM_MODEL, Key.key("stinkendreich", "fernbedienung"));
        s.setData(DataComponentTypes.ITEM_NAME, Component.text("Fernbedienung"));
        s.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        s.editPersistentDataContainer(pdc -> pdc.set(schluessel, PersistentDataType.BYTE, (byte) 1));
        return s;
    }

    private boolean istFernbedienung(ItemStack s) {
        return s != null && !s.isEmpty() && s.getPersistentDataContainer().has(schluessel);
    }

    /** Vom Möbel in die Hand (bevorzugt in die leere Hand, sonst ins Inventar, notfalls vor die Füße). */
    private void nehmen(Player p, Block b) {
        bloecke.entfernen(b);
        ItemStack s = gegenstand();
        if (p.getInventory().getItemInMainHand().isEmpty()) {
            p.getInventory().setItemInMainHand(s);
        } else {
            p.getInventory().addItem(s).values().forEach(rest -> p.getWorld().dropItem(p.getLocation(), rest));
        }
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.4F, 1.4F);
        p.sendActionBar(Component.text("Fernbedienung in der Hand · Rechtsklick: Fernseher · Schleichen + Rechtsklick auf einen Tisch: ablegen",
                NamedTextColor.GOLD));
    }

    @EventHandler
    public void beimKlick(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !istFernbedienung(e.getItem())
                || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player p = e.getPlayer();
        Block geklickt = e.getClickedBlock();
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && geklickt != null) {
            if (p.isSneaking() && e.getBlockFace() == BlockFace.UP) {
                e.setCancelled(true);
                ablegen(p, geklickt);
                return;
            }
            // Ein Klick auf einen Mod-Block (Tür, Sofa, andere Fernbedienung …) oder eine Truhe geht vor
            if (bloecke.zustand(geklickt) != null || geklickt.getType().isInteractable()) {
                return;
            }
        }
        e.setCancelled(true);
        ItemDisplay tv = plugin.fernseher().ziel(p);
        if (tv == null) {
            p.sendActionBar(Component.text("Kein Fernseher in der Nähe", NamedTextColor.GRAY));
        } else {
            plugin.fernseher().menue(p, tv);
        }
    }

    /** Auf den Block legen: sie liegt auf seiner Oberkante (bei niedrigen Möbeln entsprechend tiefer). */
    private void ablegen(Player p, Block unten) {
        Block ziel = unten.getRelative(BlockFace.UP);
        if (!ziel.getType().isAir() || bloecke.zustand(ziel) != null) {
            return;
        }
        double oben = 1.0;
        Zustand z = bloecke.zustand(unten);
        if (z != null) {
            oben = 0;
            for (Blockdaten.Kasten k : bloecke.daten().darstellung(z).umriss()) {
                oben = Math.max(oben, k.y1() / 16.0);
            }
            oben = oben == 0 ? 1.0 : oben;
        } else if (!unten.isPassable()) {
            oben = unten.getBoundingBox().getMaxY() - unten.getY();
        }
        int tiefe = (int) Math.max(0, Math.min(8, Math.round((1.0 - Math.min(1.0, oben)) * 16)));
        String richtung = p.getFacing().getOppositeFace().name().toLowerCase(Locale.ROOT);
        bloecke.setzen(ziel, Zustand.lesen("stinkendreich:fernbedienung[facing=" + richtung + ",tiefe=" + tiefe + ",waterlogged=false]"));
        p.getInventory().setItemInMainHand(null);
        p.playSound(p.getLocation(), Sound.BLOCK_WOOD_PLACE, SoundCategory.PLAYERS, 0.5F, 1.4F);
    }
}
