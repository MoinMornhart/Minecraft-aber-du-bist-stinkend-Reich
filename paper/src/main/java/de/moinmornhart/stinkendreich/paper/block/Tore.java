package de.moinmornhart.stinkendreich.paper.block;

import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import de.moinmornhart.stinkendreich.paper.villa.Villen;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.WanderingTrader;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tore, Haustür, Garagentor, Schiebetüren und Rollläden (wie TorsteuerungBlock und TorfluegelBlock der Mod): Jede
 * Torsteuerung im Bauplan schaltet alle Flügel eines Typs in ihrem Umkreis. Ein Flügel fährt in drei Stufen auf
 * (Eigenschaft „stufe“ 0–3, die Modelle zeigen ihn verschoben).
 * <ul>
 *   <li>{@code tor}: offen, wenn ein Spieler davorsteht (zu Fuß 6 Blöcke, im Fahrzeug 14) oder James unterwegs ist</li>
 *   <li>{@code schiebetuer}: offen, wenn jemand im Umkreis ist</li>
 *   <li>{@code rollladen}: offen (ohne Lockdown)</li>
 *   <li>{@code schalter}: wechselt bei Rechtsklick oder Redstone-Flanke</li>
 *   <li>{@code redstone}/{@code redstone_invers}: folgt dem Redstone-Signal</li>
 * </ul>
 */
public final class Tore {
    private static final int SCHRITT_TICKS = 2, GANZ_OFFEN = 3;

    private final StinkendReichPaper plugin;
    private final VirtuelleBloecke bloecke;
    /** Zuletzt geschalteter Zustand je Steuerung (Welt + Position). */
    private final Map<String, Boolean> zuletzt = new HashMap<>();
    private final Map<String, Integer> pruefung = new HashMap<>();
    private final Map<String, Boolean> schalter = new HashMap<>();
    private final Map<String, Boolean> signal = new HashMap<>();

    public Tore(StinkendReichPaper plugin, VirtuelleBloecke bloecke, Mechanik mechanik) {
        this.plugin = plugin;
        this.bloecke = bloecke;
        mechanik.anmelden("torsteuerung", (p, b, z) -> {
            if (!"schalter".equals(bloecke.werte(b).getOrDefault("modus", "schalter"))) {
                return false;
            }
            String k = schluessel(b);
            schalter.put(k, !schalter.getOrDefault(k, false));
            return true;
        });
        Bukkit.getScheduler().runTaskTimer(plugin, this::takt, 5L, 5L);
    }

    private static String schluessel(Block b) {
        return b.getWorld().getUID() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }

    private void takt() {
        for (Block b : bloecke.alle("torsteuerung")) {
            Map<String, String> w = bloecke.werte(b);
            String modus = w.getOrDefault("modus", "schalter");
            int radius = zahl(w.get("radius"), 6);
            String k = schluessel(b);
            boolean offen = switch (modus) {
                case "tor" -> amTor(b);
                case "rollladen" -> true;
                case "redstone" -> b.isBlockIndirectlyPowered();
                case "redstone_invers" -> !b.isBlockIndirectlyPowered();
                case "schiebetuer" -> jemandNah(b.getLocation().add(0.5, 0.5, 0.5), radius);
                default -> {
                    boolean an = b.isBlockIndirectlyPowered();
                    if (an && !signal.getOrDefault(k, false)) {
                        schalter.put(k, !schalter.getOrDefault(k, false));
                    }
                    signal.put(k, an);
                    yield schalter.getOrDefault(k, false);
                }
            };
            int n = pruefung.merge(k, 1, Integer::sum);
            Boolean vorher = zuletzt.get(k);
            if (vorher != null && vorher == offen && n < 40) {
                continue;
            }
            pruefung.put(k, 0);
            zuletzt.put(k, offen);
            String ziel = w.getOrDefault("ziel", "stinkendreich:tor");
            if (umschalten(b, radius, ziel, offen)) {
                b.getWorld().playSound(b.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 0.6F, offen ? 1.3F : 1.0F);
            }
        }
    }

    private static int zahl(String text, int sonst) {
        try {
            return text == null ? sonst : Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return sonst;
        }
    }

    /** Steht ein Spieler am Tor (zu Fuß 6 Blöcke um die Tormitte, höchstens 4 darüber/darunter; im Fahrzeug 14)? */
    private boolean amTor(Block b) {
        Location mitte = b.getLocation().add(0.5, 3.5, 0.5);
        World w = b.getWorld();
        for (Player p : w.getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            Location l = p.getLocation();
            double dx = Math.abs(l.getX() - mitte.getX()), dy = Math.abs(l.getY() - mitte.getY()), dz = Math.abs(l.getZ() - mitte.getZ());
            if (p.isInsideVehicle() ? Math.max(dx, dz) <= 14 && dy <= 5 : Math.max(dx, dz) <= 6 && dy <= 4) {
                if (darfRein(p, b)) {
                    return true;
                }
            }
        }
        // James auf dem Weg
        for (WanderingTrader t : w.getEntitiesByClass(WanderingTrader.class)) {
            if (t.getPersistentDataContainer().has(new org.bukkit.NamespacedKey(plugin, "butler"))
                    && t.getLocation().distanceSquared(mitte) < 36 && t.getPathfinder().hasPath()) {
                return true;
            }
        }
        return false;
    }

    /** Bewohner (Besitzer der Villa) – bei einer Villa ohne Besitzer jeder. */
    private boolean darfRein(Player p, Block b) {
        Villen.Villa v = plugin.villen().bei(b.getLocation(), 16);
        UUID herr = v == null ? null : v.besitzer();
        return herr == null || herr.equals(p.getUniqueId()) || plugin.gaeste().darfRein(v, p);
    }

    private static boolean jemandNah(Location mitte, int radius) {
        for (Player p : mitte.getWorld().getPlayers()) {
            if (p.getGameMode() != GameMode.SPECTATOR && p.getLocation().distanceSquared(mitte) <= (radius + 0.5) * (radius + 0.5)) {
                return true;
            }
        }
        return false;
    }

    /** Setzt alle Flügel des Typs im Umkreis (und darüber/darunter weiter); true, wenn sich etwas geändert hat. */
    public boolean umschalten(Block mitte, int radius, String typ, boolean offen) {
        boolean geaendert = false;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block b = mitte.getRelative(dx, dy, dz);
                    Zustand z = bloecke.zustand(b);
                    if (z == null || !z.block().equals(typ)) {
                        continue;
                    }
                    geaendert |= setzeOffen(b, z, offen);
                    if (dy == radius || dy == -radius) {
                        for (int i = 1; i <= 16; i++) {
                            Block weiter = b.getRelative(0, dy > 0 ? i : -i, 0);
                            Zustand zw = bloecke.zustand(weiter);
                            if (zw == null || !zw.block().equals(typ)) {
                                break;
                            }
                            geaendert |= setzeOffen(weiter, zw, offen);
                        }
                    }
                }
            }
        }
        return geaendert;
    }

    /** Ein Flügel: gewünschten Zustand setzen und in Stufen hinfahren. */
    public boolean setzeOffen(Block b, Zustand z, boolean offen) {
        if (z.ist("open", Boolean.toString(offen))) {
            return false;
        }
        bloecke.setzen(b, z.mit("open", Boolean.toString(offen)));
        bewegen(b);
        return true;
    }

    private void bewegen(Block b) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Zustand z = bloecke.zustand(b);
            if (z == null || z.wert("stufe") == null) {
                return;
            }
            int stufe = Integer.parseInt(z.wert("stufe"));
            int ziel = z.ist("open", "true") ? GANZ_OFFEN : 0;
            if (stufe == ziel) {
                return;
            }
            stufe += stufe < ziel ? 1 : -1;
            bloecke.setzen(b, z.mit("stufe", Integer.toString(stufe)));
            if (stufe != ziel) {
                bewegen(b);
            }
        }, SCHRITT_TICKS);
    }
}
