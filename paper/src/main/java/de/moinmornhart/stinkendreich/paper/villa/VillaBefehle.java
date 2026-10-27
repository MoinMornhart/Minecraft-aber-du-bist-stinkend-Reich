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
                // Erste Villa, falls sie fehlt (wer den Befehl gibt, wird nach dem Bau ihr Herr)
                if (p == null) {
                    return false;
                }
                antwort(wer, plugin.bauleitung().ersteVilla(p.getWorld(), p)
                        ? "Die erste Villa wird gebaut – gleich bringe ich dich hin."
                        : "Die erste Villa gibt es schon oder sie wird gerade gebaut (/villa auftraege).");
            }
            case "nachbar" -> {
                String besitzer = args.length > 1 ? args[1] : "Nachbar" + plugin.bauleitung().naechsteNummer();
                boolean ok = plugin.bauleitung().villaFuer(UUID.nameUUIDFromBytes(("Nachbar:" + besitzer)
                        .getBytes(StandardCharsets.UTF_8)), besitzer);
                antwort(wer, ok ? "Villa für " + besitzer + " wird gebaut." : "Geht nicht: Es gibt noch keine erste Villa.");
            }
            case "auftraege" -> antwort(wer, "Villen im Bau: " + plugin.bauleitung().uebersicht());
            case "zaehlen" -> {
                // Testhilfe: Figuren je Art auf dem Grundstück einer Villa (doppelt angelegte Anzeigen fallen auf)
                Villen.Villa v = plugin.villen().nr(args.length > 1 ? Integer.parseInt(args[1]) : 0);
                if (v == null) {
                    antwort(wer, "Keine solche Villa.");
                    return true;
                }
                Bauplan plan = plugin.bauplan();
                java.util.Map<String, Integer> anzahl = new java.util.TreeMap<>();
                for (org.bukkit.entity.Entity e : v.world().getNearbyEntities(new org.bukkit.util.BoundingBox(v.x(), v.y(), v.z(),
                        v.x() + plan.breiteX, v.y() + plan.hoehe, v.z() + plan.breiteZ))) {
                    anzahl.merge(e.getType().getKey().getKey(), 1, Integer::sum);
                }
                antwort(wer, "Zählung Villa " + v.nr() + ": " + anzahl);
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
            case "agent" -> {
                // Testhilfe: ganze Agenten-Ausrüstung ins Inventar
                if (p != null && args.length > 1 && args[1].equals("an")) {
                    plugin.agent().anziehen(p);                 // Anzug an, Waffen auf die ersten drei Plätze
                    antwort(wer, "Agenten-Anzug angezogen.");
                } else if (p != null) {
                    plugin.agent().alles().forEach(p.getInventory()::addItem);
                    antwort(wer, "Agenten-Ausrüstung im Inventar.");
                }
            }
            case "strassen" -> antwort(wer, "Straßen: " + plugin.strassen().uebersicht());
            case "strasse" -> {
                // Testhilfe: Straße zu einer Villa (neu) bauen
                Villen.Villa v = args.length > 1 ? plugin.villen().nr(Integer.parseInt(args[1])) : null;
                if (v == null) {
                    antwort(wer, "Welche Villa? /villa strasse <nummer>");
                } else {
                    plugin.strassen().neu(v);
                    antwort(wer, "Straße zur " + v.name() + " vorgemerkt.");
                }
            }
            case "strassenblick" -> {
                // Testhilfe für Bilder: über der ersten Straße an Stelle <anteil> (0–1), Blick die Straße entlang
                var liste = plugin.strassen().alle();
                if (p == null || liste.isEmpty()) {
                    antwort(wer, "Noch keine Straße.");
                } else {
                    var punkte = liste.getFirst().punkte();
                    double anteil = args.length > 1 ? Double.parseDouble(args[1]) : 0.5;
                    int i = (int) Math.clamp(anteil * (punkte.size() - 1), 0, punkte.size() - 1);
                    int[] a = punkte.get(Math.max(0, i - 14)), b = punkte.get(Math.min(punkte.size() - 1, i + 6));
                    Location l = new Location(p.getWorld(), a[0] + 0.5, a[1] + (args.length > 2 ? Double.parseDouble(args[2]) : 14), a[2] + 0.5);
                    l.setDirection(new org.bukkit.util.Vector(b[0] - a[0], b[1] + 1 - l.getY(), b[2] - a[2]));
                    p.teleport(l);
                }
            }
            case "einsteigen" -> {
                // Testhilfe: ins nächste Fahrzeug einsteigen (wie Rechtsklick)
                if (p != null && !plugin.fahrzeuge().einsteigen(p)) {
                    antwort(wer, "Kein Fahrzeug in der Nähe.");
                }
            }
            case "butlerblick" -> {
                // Testhilfe für Bilder: freie Stelle mit Blick auf den eigenen Butler
                Location c = p == null ? null : plugin.butler().kamera(Math.max(0, plugin.butler().butlerVon(p)));
                if (c != null) {
                    p.teleport(c);
                } else {
                    antwort(wer, "Keine freie Stelle mit Blick auf James.");
                }
            }
            case "hai" -> {
                // Testhilfe: Hai an der eigenen Stelle aussetzen (im Wasser)
                if (p != null) {
                    plugin.haie().aussetzen(p.getLocation());
                    antwort(wer, "Hai ausgesetzt (" + plugin.haie().anzahl() + " Haie).");
                }
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
        return args.length == 1 ? List.of("bauen", "nachbar", "hin", "ort", "block", "agent", "hai", "fernseher", "inkognito", "bruecke", "zentrale", "code", "klick", "kamera", "lied", "klaviatur", "platte", "info") : List.of();
    }
}
