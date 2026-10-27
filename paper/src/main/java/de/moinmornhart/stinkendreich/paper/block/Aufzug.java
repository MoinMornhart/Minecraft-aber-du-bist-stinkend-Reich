package de.moinmornhart.stinkendreich.paper.block;

import de.moinmornhart.stinkendreich.paper.Dialoge;
import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Aufzug der Villa auf dem Paper-Server: Rechtsklick auf eine Bedientafel zeigt alle Haltestellen im selben Schacht
 * (Tafeln genau übereinander, wie in der Mod). Die Fahrt ist ein kurzes Zischen und Ankommen in der Kabine der
 * gewählten Etage; die Untergrundbasis fährt er nur für Bewohner an.
 */
public final class Aufzug {
    private static final int SCHACHT_HOEHE = 96;

    private final StinkendReichPaper plugin;
    private final VirtuelleBloecke bloecke;
    private final Zugang zugang;

    public Aufzug(StinkendReichPaper plugin, VirtuelleBloecke bloecke, Mechanik mechanik, Zugang zugang) {
        this.plugin = plugin;
        this.bloecke = bloecke;
        this.zugang = zugang;
        mechanik.anmelden("aufzugstafel", (p, b, z) -> {
            menue(p, b);
            return true;
        });
    }

    private List<Block> haltestellen(Block tafel) {
        List<Block> liste = new ArrayList<>();
        for (int dy = -SCHACHT_HOEHE; dy <= SCHACHT_HOEHE; dy++) {
            Block b = tafel.getRelative(0, dy, 0);
            Zustand z = bloecke.zustand(b);
            if (z != null && z.name().equals("aufzugstafel")) {
                liste.add(b);
            }
        }
        return liste;
    }

    private void menue(Player p, Block tafel) {
        List<Dialoge.Knopf> knoepfe = new ArrayList<>();
        List<Block> stellen = haltestellen(tafel);
        for (int i = stellen.size() - 1; i >= 0; i--) {
            Block ziel = stellen.get(i);
            Map<String, String> w = bloecke.werte(ziel);
            String name = w.getOrDefault("name", "Etage " + (i + 1));
            boolean hier = ziel.equals(tafel);
            knoepfe.add(new Dialoge.Knopf(Component.text(hier ? "● " + name : name, hier ? NamedTextColor.GOLD : NamedTextColor.WHITE),
                    (s, a) -> fahren(s, tafel, ziel)));
        }
        Dialoge.zeigen(p, "Aufzug", "Wohin darf es gehen?", List.of(), knoepfe);
    }

    private void fahren(Player p, Block von, Block ziel) {
        Map<String, String> w = bloecke.werte(ziel);
        boolean nurKarte = "ja".equals(w.get("karte")) || "Untergrundbasis".equals(w.get("name"));
        if (nurKarte && !zugang.bewohner(p, ziel)) {
            p.sendActionBar(Component.text("Diese Etage nur mit Zugangskarte.", NamedTextColor.RED));
            return;
        }
        // Standplatz in der Kabine (wie in der Mod): Mitte der Kabine relativ zur Mitte der Tafel, Fußhöhe relativ zur Tafel
        double dx = zahl(w.get("kabine_dx"), 0), dy = zahl(w.get("kabine_dy"), 0), dz = zahl(w.get("kabine_dz"), 0);
        Location ankunft = ziel.getLocation().add(0.5 + dx, dy, 0.5 + dz);
        // Blick wie vorher, damit man sich nicht verdreht
        ankunft.setYaw(p.getLocation().getYaw());
        ankunft.setPitch(p.getLocation().getPitch());
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6F, 1.8F);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            p.teleport(ankunft);
            p.getWorld().playSound(ankunft, Sound.BLOCK_NOTE_BLOCK_BELL, 1.0F, 1.2F);
            p.getWorld().spawnParticle(Particle.END_ROD, ankunft.clone().add(0, 1, 0), 12, 0.4, 0.8, 0.4, 0.01);
        }, 20L);
    }

    private static double zahl(String text, double sonst) {
        try {
            return text == null ? sonst : Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return sonst;
        }
    }
}
