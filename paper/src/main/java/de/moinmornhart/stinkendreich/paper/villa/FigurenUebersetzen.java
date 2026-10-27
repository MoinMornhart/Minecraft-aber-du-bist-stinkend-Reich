package de.moinmornhart.stinkendreich.paper.villa;

import de.moinmornhart.stinkendreich.paper.Nbt;
import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Pose;
import org.bukkit.profile.PlayerTextures;

import java.util.HashMap;
import java.util.Map;

/**
 * Stellt die Figuren aus dem Bauplan auf: Figuren aus dem Spiel (Bilder, Rahmen, Fische, Schildkröten, Delfine) über
 * das Datenformat von Minecraft, Figuren der Mod als Ersatz (Tänzerinnen als Schaufensterpuppe mit ihrer Haut aus dem
 * Ressourcenpaket, Haie als Delfin mit Namen).
 */
public final class FigurenUebersetzen {
    private final StinkendReichPaper plugin;

    public FigurenUebersetzen(StinkendReichPaper plugin) {
        this.plugin = plugin;
    }

    /** Eine Schaufensterpuppe mit einer Haut aus dem Ressourcenpaket (z. B. „butler“, „taenzerin_2“). */
    public static Mannequin puppe(Location ort, String haut) {
        return ort.getWorld().spawn(ort, Mannequin.class, p -> {
            p.setPersistent(true);
            p.setImmovable(true);
            p.setProfile(ResolvableProfile.resolvableProfile()
                    .skinPatch(s -> s.body(Key.key("stinkendreich", "entity/" + haut)).model(PlayerTextures.SkinModel.CLASSIC))
                    .build());
            p.setDescription(null);
        });
    }

    public boolean aufstellen(Bauplan.Figur f, Location ort) {
        String id = Nbt.text(f.daten(), "id", "");
        try {
            if (id.equals("stinkendreich:taenzerin")) {
                String[] teile = Nbt.text(f.daten(), "stinkendreich", "laufen|0").split("\\|");
                Mannequin p = puppe(ort, "taenzerin_" + (teile.length > 1 ? teile[1] : "0"));
                p.setPose(Pose.STANDING);
                return true;
            }
            if (id.equals("stinkendreich:hai")) {
                Map<String, Object> delfin = new HashMap<>(f.daten());
                delfin.put("id", "minecraft:dolphin");
                return spawnen(delfin, ort) != null;
            }
            if (id.startsWith("minecraft:")) {
                String variante = Nbt.text(f.daten(), "variant", "minecraft:");
                if (id.equals("minecraft:painting") && variante.startsWith("stinkendreich:")) {
                    return gemaelde(variante.substring("stinkendreich:".length()), Nbt.zahl(f.daten(), "facing", 0), ort);
                }
                return spawnen(new HashMap<>(f.daten()), ort) != null;
            }
        } catch (Exception e) {
            plugin.getLogger().fine("Figur " + id + " übersprungen: " + e.getMessage());
        }
        return false;
    }

    /**
     * Ein Gemälde der Mod (eigene Bildvarianten gibt es auf Paper nicht): Anzeige-Figur mit dem flachen Bildmodell,
     * gestreckt auf Breite × Höhe und an die Wand gesetzt wie ein Bild im Spiel (Mitte des Blocks, fast an der Wand,
     * bei gerader Breite/Höhe einen halben Block versetzt).
     */
    private boolean gemaelde(String name, int richtung, Location ort) {
        com.google.gson.JsonObject variante = de.moinmornhart.stinkendreich.paper.block.Blockdaten.json(plugin,
                "mod/data/stinkendreich/painting_variant/" + name + ".json");
        if (variante == null) {
            return false;
        }
        int breite = variante.get("width").getAsInt(), hoehe = variante.get("height").getAsInt();
        // facing: 0 Süden, 1 Westen, 2 Norden, 3 Osten (wie bei Bildern im Spiel)
        org.bukkit.block.BlockFace blick = switch (richtung) {
            case 1 -> org.bukkit.block.BlockFace.WEST;
            case 2 -> org.bukkit.block.BlockFace.NORTH;
            case 3 -> org.bukkit.block.BlockFace.EAST;
            default -> org.bukkit.block.BlockFace.SOUTH;
        };
        org.bukkit.block.BlockFace links = switch (blick) {           // gegen den Uhrzeigersinn
            case NORTH -> org.bukkit.block.BlockFace.WEST;
            case WEST -> org.bukkit.block.BlockFace.SOUTH;
            case SOUTH -> org.bukkit.block.BlockFace.EAST;
            default -> org.bukkit.block.BlockFace.NORTH;
        };
        double versatzB = breite % 2 == 0 ? 0.5 : 0.0, versatzH = hoehe % 2 == 0 ? 0.5 : 0.0;
        Location mitte = ort.getBlock().getLocation().add(0.5, 0.5, 0.5)
                .add(-blick.getModX() * 0.46875, 0, -blick.getModZ() * 0.46875)
                .add(links.getModX() * versatzB, versatzH, links.getModZ() * versatzB);
        // Item-Anzeigen zeichnen ihr Modell um 180° gedreht: die Bildseite (Süden im Modell) zeigt bei Gierwinkel 0
        // nach Norden, deshalb hier jeweils die Gegenrichtung
        mitte.setYaw(switch (blick) {
            case WEST -> 270.0F;
            case NORTH -> 0.0F;
            case EAST -> 90.0F;
            default -> 180.0F;
        });
        mitte.getWorld().spawn(mitte, org.bukkit.entity.ItemDisplay.class, d -> {
            org.bukkit.inventory.ItemStack bild = org.bukkit.inventory.ItemStack.of(org.bukkit.Material.PAPER);
            bild.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, Key.key("stinkendreich", "polymer/bild/" + name));
            d.setItemStack(bild);
            d.setPersistent(true);
            d.setItemDisplayTransform(org.bukkit.entity.ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.Quaternionf(),
                    new org.joml.Vector3f(breite, hoehe, 1), new org.joml.Quaternionf()));
        });
        return true;
    }

    private Entity spawnen(Map<String, Object> daten, Location ort) {
        Nbt.Liste pos = new Nbt.Liste(6);
        pos.add(ort.getX());
        pos.add(ort.getY());
        pos.add(ort.getZ());
        daten.put("Pos", pos);
        daten.remove("UUID");
        // Bilder und Rahmen hängen an einem Block
        daten.put("block_pos", new int[]{ort.getBlockX(), ort.getBlockY(), ort.getBlockZ()});
        daten.put("TileX", ort.getBlockX());
        daten.put("TileY", ort.getBlockY());
        daten.put("TileZ", ort.getBlockZ());
        daten.put("DataVersion", plugin.bauplan().datenVersion);
        Entity e = Bukkit.getUnsafe().deserializeEntity(Nbt.schreibenGzip(daten), ort.getWorld(), false, false);
        if (e == null) {
            return null;
        }
        e.setPersistent(true);
        return e.spawnAt(ort) ? e : null;
    }
}
