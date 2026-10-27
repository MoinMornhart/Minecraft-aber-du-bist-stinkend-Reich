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
                // Höhe „boden+N“: N Blöcke über dem natürlichen Boden an dieser Stelle (für Bilder im Gelände)
                double wx = v.x() + Double.parseDouble(args[1]), wz = v.z() + Double.parseDouble(args[3]);
                double wy = args[2].startsWith("boden")
                        ? Baustelle.bodenHoehe(v.world(), (int) Math.floor(wx), (int) Math.floor(wz))
                        + (args[2].length() > 5 ? Double.parseDouble(args[2].substring(5)) : 0.0)
                        : v.y() + Double.parseDouble(args[2]);
                Location l = new Location(v.world(), wx, wy, wz);
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
            case "fernseher" -> {
                // Testhilfe: Fenster des nächsten Fernsehers bzw. /villa fernseher <link>
                org.bukkit.entity.ItemDisplay tv = p == null ? null : plugin.fernseher().naechster(p.getLocation());
                if (tv == null) {
                    antwort(wer, "Kein Fernseher in der Nähe.");
                } else if (args.length > 1) {
                    plugin.fernseher().anzeigen(p, tv, args[1]);
                } else {
                    plugin.fernseher().menue(p, tv);
                }
            }
            case "inkognito" -> {
                Villen.Villa v = p == null ? null : plugin.villen().bei(p.getLocation(), 64);
                if (v == null) {
                    v = plugin.villen().nr(0);
                }
                antwort(wer, plugin.inkognito().schalten(v, args.length < 2 || args[1].equals("an")));
            }
            case "bruecke" -> {
                Villen.Villa v = p == null ? null : plugin.villen().bei(p.getLocation(), 64);
                if (v == null) {
                    v = plugin.villen().nr(0);
                }
                boolean an = args.length < 2 || args[1].equals("ein");
                antwort(wer, plugin.zugbruecke().sichern(v, an) ? (an ? "Zugbrücke gesichert." : "Zugbrücke offen.") : "Keine Zugbrücke.");
            }
            case "klick", "kamera" -> {
                // Testhilfe: Rechtsklick auf den nächsten Mod-Block dieser Art (/villa klick tuerklingel) bzw. Blick
                // durch Kamera n am nächsten Monitor (/villa kamera 2)
                Block b = p == null ? null : naechster(p, was.equals("kamera") ? "monitor" : args.length > 1 ? args[1] : "");
                if (b == null) {
                    antwort(wer, "Nichts in der Nähe.");
                    return true;
                }
                if (was.equals("kamera")) {
                    plugin.kameras().startenAmMonitor(p, b, args.length > 1 ? Integer.parseInt(args[1]) - 1 : 0);
                } else {
                    plugin.mechanik().benutzen(p, b, plugin.bloecke().zustand(b));
                }
            }
            case "lied", "klaviatur", "platte" -> {
                // Testhilfe: Stück am nächsten Flügel, Klaviatur zeigen, Schallplatte an der nächsten Musikanlage
                Block b = p == null ? null : naechster(p, was.equals("platte") ? "musikanlage" : "konzertfluegel");
                if (b == null) {
                    antwort(wer, "Nichts in der Nähe.");
                    return true;
                }
                switch (was) {
                    case "lied" -> plugin.unterhaltung().liedSpielen(b, args.length > 1 ? args[1] : "fuer_elise");
                    case "klaviatur" -> plugin.unterhaltung().klaviatur(p, b);
                    default -> plugin.unterhaltung().platte(b, args.length > 1 ? args[1] : "pigstep");
                }
            }
            case "zentrale", "code" -> {
                // Testhilfe: Knopf der nächsten Zentrale bzw. Code am nächsten Tastenfeld (wie im Dialog)
                Block b = p == null ? null : naechster(p, was.equals("code") ? "tastenfeld" : "sicherheitszentrale");
                if (b == null) {
                    antwort(wer, "Nichts in der Nähe.");
                    return true;
                }
                if (args.length < 2) {
                    if (was.equals("code")) {
                        plugin.sicherheit().tastenfeld(p, b, "");
                    } else {
                        plugin.sicherheit().zentrale(p, b);
                    }
                } else if (was.equals("code")) {
                    plugin.sicherheit().codeEingeben(p, b, args[1]);
                } else {
                    plugin.sicherheit().zentraleKnopf(p, b, args[1]);
                }
            }
            default -> {
                // Mod-Blöcke auf dem Grundstück der Spawn-Villa (über die ganze Tiefe, auch versteckt)
                Villen.Villa v0 = plugin.villen().nr(0);
                int innen = 0;
                if (v0 != null && v0.world() != null) {
                    org.bukkit.util.BoundingBox kasten = new org.bukkit.util.BoundingBox(v0.x(), v0.y() - 64, v0.z(),
                            v0.x() + plugin.bauplan().breiteX, v0.y() + plugin.bauplan().hoehe + 8, v0.z() + plugin.bauplan().breiteZ);
                    for (org.bukkit.entity.Entity e : v0.world().getNearbyEntities(kasten)) {
                        if (e instanceof org.bukkit.entity.ItemDisplay d && plugin.bloecke().zustand(d.getLocation().getBlock()) != null) {
                            innen++;
                        }
                    }
                }
                String text = plugin.villen().alle().size() + " Villen, " + plugin.bloecke().anzahl() + " Mod-Blöcke geladen, "
                        + innen + " auf dem Grundstück. "
                        + plugin.butler().uebersicht();
                antwort(wer, text);
                plugin.getLogger().info("Info: " + text);
            }
        }
        return true;
    }

    /** Nächster Mod-Block dieser Art in derselben Welt (höchstens 200 Blöcke weit). */
    private Block naechster(Player p, String name) {
        Block best = null;
        double d = 200 * 200;
        for (Block b : plugin.bloecke().alle(name)) {
            if (b.getWorld().equals(p.getWorld()) && b.getLocation().distanceSquared(p.getLocation()) < d) {
                d = b.getLocation().distanceSquared(p.getLocation());
                best = b;
            }
        }
        return best;
    }

    private static void antwort(CommandSender wer, String text) {
        wer.sendMessage(Component.text(text, NamedTextColor.GOLD));
    }

    @Override
    public List<String> onTabComplete(CommandSender wer, Command befehl, String name, String[] args) {
        return args.length == 1 ? List.of("bauen", "nachbar", "hin", "ort", "block", "fernseher", "inkognito", "bruecke", "zentrale", "code", "klick", "kamera", "lied", "klaviatur", "platte", "info") : List.of();
    }
}
