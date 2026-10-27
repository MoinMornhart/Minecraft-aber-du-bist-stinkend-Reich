package de.moinmornhart.stinkendreich.paper.villa;

import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import de.moinmornhart.stinkendreich.paper.block.Zustand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * {@code /villa …} für Betreiber: bauen, Nachbarvilla (Test), hinbringen, Ort relativ zur Villa (Fotos), Infos.
 */
public final class VillaBefehle implements TabExecutor {
    private final StinkendReichPaper plugin;

    private VillaBefehle(StinkendReichPaper plugin) {
        this.plugin = plugin;
    }

    public static void registrieren(StinkendReichPaper plugin) {
        VillaBefehle b = new VillaBefehle(plugin);
        plugin.getCommand("villa").setExecutor(b);
        plugin.getCommand("villa").setTabCompleter(b);
    }

    @Override
    public boolean onCommand(CommandSender wer, Command befehl, String name, String[] args) {
        String was = args.length == 0 ? "info" : args[0];
        Player p = wer instanceof Player pl ? pl : null;
        switch (was) {
            case "bauen" -> {
                if (p == null) {
                    return false;
                }
                plugin.spawnVillaBauen(p.getWorld());
                antwort(wer, "Villa wird am Spawn gebaut.");
            }
            case "nachbar" -> {
                String besitzer = args.length > 1 ? args[1] : "Nachbar" + plugin.villen().naechsteNummer();
                plugin.nachbarBauen(UUID.nameUUIDFromBytes(("Nachbar:" + besitzer).getBytes(StandardCharsets.UTF_8)), besitzer);
                antwort(wer, "Villa für " + besitzer + " wird gebaut.");
            }
            case "hin" -> {
                Villen.Villa v = plugin.villen().nr(args.length > 1 ? Integer.parseInt(args[1]) : 0);
                if (p == null || v == null) {
                    antwort(wer, "Keine solche Villa.");
                    return true;
                }
                p.teleport(v.spawn(plugin.bauplan()));
            }
            case "ort" -> {
                // /villa ort x y z [gier neigung]: Stelle im Bauplan der Villa, in der man steht (sonst Spawn-Villa)
                Villen.Villa v = p == null ? null : plugin.villen().bei(p.getLocation(), 64);
                if (v == null) {
                    v = plugin.villen().nr(0);
                }
                if (p == null || v == null || args.length < 4) {
                    return false;
                }
                Location l = new Location(v.world(), v.x() + Double.parseDouble(args[1]), v.y() + Double.parseDouble(args[2]),
                        v.z() + Double.parseDouble(args[3]));
                if (args.length >= 6) {
                    l.setYaw(Float.parseFloat(args[4]));
                    l.setPitch(Float.parseFloat(args[5]));
                }
                p.teleport(l);
            }
            case "block" -> {
                if (p == null) {
                    return false;
                }
                Block b = p.getTargetBlockExact(8);
                Zustand z = b == null ? null : plugin.bloecke().zustand(b);
                antwort(wer, b == null ? "Kein Block." : b.getType() + " " + (z == null ? "" : z + " " + plugin.bloecke().werte(b)));
            }
            default -> {
                String text = plugin.villen().alle().size() + " Villen, " + plugin.bloecke().anzahl() + " Mod-Blöcke geladen. "
                        + plugin.butler().uebersicht();
                antwort(wer, text);
                plugin.getLogger().info("Info: " + text);
            }
        }
        return true;
    }

    private static void antwort(CommandSender wer, String text) {
        wer.sendMessage(Component.text(text, NamedTextColor.GOLD));
    }

    @Override
    public List<String> onTabComplete(CommandSender wer, Command befehl, String name, String[] args) {
        return args.length == 1 ? List.of("bauen", "nachbar", "hin", "ort", "block", "info") : List.of();
    }
}
