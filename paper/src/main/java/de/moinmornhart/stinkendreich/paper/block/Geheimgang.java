package de.moinmornhart.stinkendreich.paper.block;

import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import de.moinmornhart.stinkendreich.paper.villa.Villen;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Magischer Eingang wie in der Mod: Wer vor einem Geheimregal (Eingang zum Geheimclub, Bunker) kurz schleicht, öffnet es
 * mit Funkeln und Zauberklang – Bewohner, Gäste und Kartenbesitzer (bei Stufe 0 alle). Nach 10 Sekunden schließt es sich
 * von selbst, sobald niemand mehr im Durchgang steht. Der Geheimschalter (Buchregal daneben) schaltet es von Hand.
 */
public final class Geheimgang {
    private static final int TAKT = 4, SCHLEICHEN_TAKTE = 3, OFFEN_TICKS = 200, RADIUS = 4;
    private static final String REGAL = "stinkendreich:geheimregal";

    private final StinkendReichPaper plugin;
    private final VirtuelleBloecke bloecke;
    private final Tore tore;
    private final Map<UUID, Integer> schleichen = new HashMap<>();
    /** Magisch geöffnete Regale und der Takt, an dem sie aufgingen. */
    private final Map<Block, Long> offen = new HashMap<>();
    private long tick;

    public Geheimgang(StinkendReichPaper plugin, VirtuelleBloecke bloecke, Mechanik mechanik, Tore tore) {
        this.plugin = plugin;
        this.bloecke = bloecke;
        this.tore = tore;
        // Geheimschalter: Gegenteil von dem, was das Regal gerade ist
        mechanik.anmelden("geheimschalter", (p, b, z) -> {
            boolean auf = true;
            suche:
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                for (int dy = -RADIUS; dy <= RADIUS; dy++) {
                    for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                        Zustand r = bloecke.zustand(b.getRelative(dx, dy, dz));
                        if (r != null && r.block().equals(REGAL)) {
                            auf = !r.ist("open", "true");
                            break suche;
                        }
                    }
                }
            }
            tore.umschalten(b, RADIUS, REGAL, auf);
            b.getWorld().playSound(b.getLocation(), "stinkendreich:tresor", SoundCategory.BLOCKS, 0.6F, 1.4F);
            return true;
        });
        Bukkit.getScheduler().runTaskTimer(plugin, this::takt, TAKT, TAKT);
    }

    private void takt() {
        tick += TAKT;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.isSneaking() || p.getGameMode() == GameMode.SPECTATOR) {
                schleichen.remove(p.getUniqueId());
                continue;
            }
            Block regal = regalVor(p);
            if (regal == null) {
                schleichen.remove(p.getUniqueId());
                continue;
            }
            int takte = schleichen.merge(p.getUniqueId(), 1, Integer::sum);
            Location mitte = regal.getLocation().add(0.5, 0.5, 0.5);
            if (takte < SCHLEICHEN_TAKTE) {
                p.getWorld().spawnParticle(Particle.ENCHANT, mitte.clone().add(0, 0.5, 0), 25, 0.6, 0.8, 0.6, 0.6);
                continue;
            }
            schleichen.remove(p.getUniqueId());
            Villen.Villa v = plugin.villen().bei(regal.getLocation(), 16);
            if (plugin.sicherheit().stufe(v) > 0 && !plugin.zugang().darfRein(p, v)) {
                p.sendActionBar(Component.text("Die Bücher flüstern … aber das Regal bleibt zu.", NamedTextColor.GRAY));
                continue;
            }
            if (tore.umschalten(regal, RADIUS, REGAL, true)) {
                offen.put(regal, tick);
                p.getWorld().spawnParticle(Particle.ENCHANT, mitte.clone().add(0, 0.5, 0), 120, 0.8, 1.2, 0.8, 1.0);
                p.getWorld().spawnParticle(Particle.PORTAL, mitte.clone().add(0, 0.5, 0), 80, 0.6, 1.0, 0.6, 0.4);
                p.getWorld().spawnParticle(Particle.WITCH, mitte.clone().add(0, 1.0, 0), 20, 0.6, 0.8, 0.6, 0.05);
                p.getWorld().playSound(mitte, Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.BLOCKS, 1.2F, 0.9F);
                p.getWorld().playSound(mitte, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1.5F, 0.7F);
                p.getWorld().playSound(mitte, "stinkendreich:tresor", SoundCategory.BLOCKS, 0.6F, 1.4F);
                p.sendActionBar(Component.text("✨ Ein magischer Eingang öffnet sich …", NamedTextColor.LIGHT_PURPLE));
                plugin.getLogger().info("Geheimgang: " + p.getName() + " öffnet das Regal bei " + regal.getX() + " " + regal.getY() + " " + regal.getZ());
            }
        }
        zuMachen();
    }

    /** Ein geschlossenes Geheimregal direkt vor dem Spieler (bis 2 Blöcke weit, Fuß- bis Kopfhöhe) oder null. */
    private Block regalVor(Player p) {
        Block fuss = p.getLocation().getBlock();
        Block naechstes = null;
        double best = Double.MAX_VALUE;
        Location auge = p.getLocation().add(0, 1.0, 0);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    Block q = fuss.getRelative(dx, dy, dz);
                    Zustand z = bloecke.zustand(q);
                    if (z != null && z.block().equals(REGAL) && !z.ist("open", "true")) {
                        double d = q.getLocation().add(0.5, 0.5, 0.5).distanceSquared(auge);
                        if (d < best) {
                            best = d;
                            naechstes = q;
                        }
                    }
                }
            }
        }
        return naechstes;
    }

    /** Schließt magisch geöffnete Regale nach Ablauf der Zeit, wenn niemand mehr im Durchgang steht. */
    private void zuMachen() {
        for (Iterator<Map.Entry<Block, Long>> it = offen.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Block, Long> e = it.next();
            Block b = e.getKey();
            if (tick - e.getValue() < OFFEN_TICKS || !b.getWorld().isChunkLoaded(b.getX() >> 4, b.getZ() >> 4)) {
                continue;
            }
            Location mitte = b.getLocation().add(0.5, 0.5, 0.5);
            boolean jemand = false;
            for (Player p : b.getWorld().getPlayers()) {
                Location l = p.getLocation();
                if (p.getGameMode() != GameMode.SPECTATOR && Math.abs(l.getX() - mitte.getX()) < 2.0
                        && Math.abs(l.getY() - mitte.getY()) < 1.5 && Math.abs(l.getZ() - mitte.getZ()) < 2.0) {
                    jemand = true;
                }
            }
            if (jemand) {
                continue;
            }
            if (tore.umschalten(b, RADIUS, REGAL, false)) {
                b.getWorld().playSound(mitte, "stinkendreich:tresor", SoundCategory.BLOCKS, 0.6F, 1.2F);
                b.getWorld().spawnParticle(Particle.ENCHANT, mitte.clone().add(0, 0.5, 0), 40, 0.6, 1.0, 0.6, 0.6);
            }
            it.remove();
        }
    }
}
