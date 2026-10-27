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
                if (id.equals("minecraft:painting") && !Nbt.text(f.daten(), "variant", "minecraft:").startsWith("minecraft:")) {
                    return false;   // eigene Bilder der Mod: kommen über das Ressourcenpaket (noch nicht auf Paper)
                }
                return spawnen(new HashMap<>(f.daten()), ort) != null;
            }
        } catch (Exception e) {
            plugin.getLogger().fine("Figur " + id + " übersprungen: " + e.getMessage());
        }
        return false;
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
