package de.moinmornhart.stinkendreich.paper.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Rechtsklicks auf Blöcke der Mod: Türen auf- und zuklappen, auf Sofas, Stühlen und Barhockern sitzen. Weitere
 * Mechanismen (Kartenleser, Aufzug, Tastenfeld …) melden sich über {@link #anmelden}.
 */
public final class Mechanik implements Listener {
    /** Was ein Block der Mod bei Rechtsklick tut. */
    public interface Handlung {
        /** @return true, wenn der Klick erledigt ist */
        boolean benutzen(Player spieler, Block block, Zustand zustand);
    }

    /** Türen, die man von Hand öffnet (wie Holztüren). */
    private static final Set<String> HANDTUEREN = Set.of("prunktuer", "glastuer", "stahltuer", "clubtuer");

    private final StinkendReichPaper plugin;
    private final VirtuelleBloecke bloecke;
    /** Sitzhöhe in Pixeln je Möbel (aus deko_bloecke.json der Mod). */
    private final Map<String, Integer> sitze = new HashMap<>();
    private final Map<String, Handlung> handlungen = new HashMap<>();
    private final org.bukkit.NamespacedKey schluesselSitz;

    public Mechanik(StinkendReichPaper plugin, VirtuelleBloecke bloecke) {
        this.plugin = plugin;
        this.bloecke = bloecke;
        this.schluesselSitz = new org.bukkit.NamespacedKey(plugin, "sitz");
        JsonElement deko = Blockdaten.jsonElement(plugin, "mod/deko_bloecke.json");
        if (deko instanceof JsonArray liste) {
            for (JsonElement e : liste) {
                JsonObject o = e.getAsJsonObject();
                if (o.has("sitz")) {
                    sitze.put(o.get("name").getAsString(), o.get("sitz").getAsInt());
                }
            }
        }
        for (String tuer : HANDTUEREN) {
            handlungen.put(tuer, (p, b, z) -> tuerSchalten(b, z));
        }
    }

    /** Meldet eine Handlung für einen Block der Mod an (Kurzname wie „kartenleser“). */
    public void anmelden(String block, Handlung h) {
        handlungen.put(block, h);
    }

    @EventHandler(ignoreCancelled = false)
    public void beimKlick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) {
            return;
        }
        Block b = e.getClickedBlock();
        Zustand z = bloecke.zustand(b);
        if (z == null) {
            return;
        }
        if (benutzen(e.getPlayer(), b, z)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void beimKlickAufFeld(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block b = bloecke.blockVonKlickfeld(e.getRightClicked());
        Zustand z = b == null ? null : bloecke.zustand(b);
        if (z != null && benutzen(e.getPlayer(), b, z)) {
            e.setCancelled(true);
        }
    }

    private boolean benutzen(Player p, Block b, Zustand z) {
        Handlung h = handlungen.get(z.name());
        if (h != null && h.benutzen(p, b, z)) {
            return true;
        }
        Integer sitz = sitze.get(z.name());
        if (sitz != null && !p.isSneaking()) {
            hinsetzen(p, b, z, sitz);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Türen
    // ------------------------------------------------------------------

    /** Klappt eine zweiteilige Tür auf oder zu (beide Hälften). */
    public boolean tuerSchalten(Block b, Zustand z) {
        if (z.wert("open") == null) {
            return false;
        }
        boolean auf = !z.ist("open", "true");
        tuerSetzen(b, z, auf);
        return true;
    }

    public void tuerSetzen(Block b, Zustand z, boolean auf) {
        String wert = Boolean.toString(auf);
        bloecke.setzen(b, z.mit("open", wert));
        Block andere = b.getRelative(z.ist("half", "lower") ? BlockFace.UP : BlockFace.DOWN);
        Zustand za = bloecke.zustand(andere);
        if (za != null && za.block().equals(z.block())) {
            bloecke.setzen(andere, za.mit("open", wert));
        }
        b.getWorld().playSound(b.getLocation().add(0.5, 0.5, 0.5),
                auf ? Sound.BLOCK_WOODEN_DOOR_OPEN : Sound.BLOCK_WOODEN_DOOR_CLOSE, 1.0F, 1.0F);
    }

    // ------------------------------------------------------------------
    // Sitzen
    // ------------------------------------------------------------------

    /** Setzt den Spieler auf ein Möbel (unsichtbare Anzeige-Figur als Sitz, wie SitzEntity in der Mod). */
    private void hinsetzen(Player p, Block b, Zustand z, int hoehe) {
        if (p.isInsideVehicle()) {
            return;
        }
        BlockFace blick = blickrichtung(z);
        Location ort = b.getLocation().add(0.5 - blick.getModX() * 0.1, hoehe / 16.0, 0.5 - blick.getModZ() * 0.1);
        ort.setYaw(gier(blick));
        ItemDisplay sitz = b.getWorld().spawn(ort, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.getPersistentDataContainer().set(schluesselSitz, PersistentDataType.BYTE, (byte) 1);
        });
        sitz.addPassenger(p);
    }

    /** Möbel schauen in die Richtung „facing“ (Sofas: dorthin, wohin man sitzend blickt). */
    private static BlockFace blickrichtung(Zustand z) {
        String f = z.wert("facing");
        if (f == null) {
            return BlockFace.SOUTH;
        }
        try {
            return BlockFace.valueOf(f.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BlockFace.SOUTH;
        }
    }

    private static float gier(BlockFace f) {
        return switch (f) {
            case NORTH -> 180.0F;
            case EAST -> -90.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };
    }

    @EventHandler
    public void beimAufstehen(EntityDismountEvent e) {
        Entity sitz = e.getDismounted();
        if (sitz.getPersistentDataContainer().has(schluesselSitz)) {
            sitz.remove();
            // Nicht im Möbel stecken bleiben: einen halben Block höher absetzen
            Entity wer = e.getEntity();
            plugin.getServer().getScheduler().runTask(plugin, () -> wer.teleport(wer.getLocation().add(0, 0.5, 0)));
        }
    }

    @EventHandler
    public void beimVerlassen(PlayerQuitEvent e) {
        Entity fahrzeug = e.getPlayer().getVehicle();
        if (fahrzeug != null && fahrzeug.getPersistentDataContainer().has(schluesselSitz)) {
            fahrzeug.remove();
        }
    }
}
