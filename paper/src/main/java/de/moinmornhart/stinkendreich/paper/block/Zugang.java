package de.moinmornhart.stinkendreich.paper.block;

import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import de.moinmornhart.stinkendreich.paper.villa.Villen;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Zugang wie in der Mod: Zugangskarte, Kartenleser, Sicherheits- und Banktüren (gehen mit Karte auf und bleiben
 * offen, Rechtsklick schließt), Bunkertüren (Kartenleser öffnet sie für 3 Sekunden) und Privattüren (nur Bewohner).
 */
public final class Zugang {
    /** Türen, die nur der Kartenleser öffnet und die offen stehen bleiben. */
    private static final Set<String> SICHERHEITSTUEREN = Set.of("sicherheitstuer", "banktuer");
    /** Eisentüren: Kartenleser öffnet sie für 3 Sekunden. */
    private static final Set<String> EISENTUEREN = Set.of("bunkertuer");

    private final StinkendReichPaper plugin;
    private final VirtuelleBloecke bloecke;
    private final Mechanik mechanik;
    private final NamespacedKey schluesselKarte;

    public Zugang(StinkendReichPaper plugin, VirtuelleBloecke bloecke, Mechanik mechanik) {
        this.plugin = plugin;
        this.bloecke = bloecke;
        this.mechanik = mechanik;
        this.schluesselKarte = new NamespacedKey(plugin, "karte");
        mechanik.anmelden("kartenleser", this::kartenleser);
        mechanik.anmelden("privattuer", (p, b, z) -> {
            if (bewohner(p, b)) {
                mechanik.tuerSchalten(b, z);
            } else {
                hinweis(p, "Privat – nur für Bewohner.", NamedTextColor.RED);
            }
            return true;
        });
        for (String tuer : SICHERHEITSTUEREN) {
            mechanik.anmelden(tuer, (p, b, z) -> {
                if (z.ist("open", "true")) {
                    doppeltuerSetzen(b, z, false);
                } else {
                    hinweis(p, "Zugangskarte an den Kartenleser halten.", NamedTextColor.YELLOW);
                }
                return true;
            });
        }
        for (String tuer : EISENTUEREN) {
            mechanik.anmelden(tuer, (p, b, z) -> {
                hinweis(p, "Diese Tür öffnet nur der Kartenleser oder das Tastenfeld.", NamedTextColor.YELLOW);
                return true;
            });
        }
    }

    /** Eine Zugangskarte für die Villa (Modell der Mod, Name der Villa). */
    public ItemStack karte(Villen.Villa v) {
        ItemStack s = ItemStack.of(Material.PAPER);
        s.setData(DataComponentTypes.ITEM_MODEL, Key.key("stinkendreich", "zugangskarte"));
        s.setData(DataComponentTypes.ITEM_NAME, Component.text("Zugangskarte · " + v.name(), NamedTextColor.GOLD));
        s.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        s.editPersistentDataContainer(pdc -> pdc.set(schluesselKarte, PersistentDataType.INTEGER, v.nr()));
        return s;
    }

    /** Hat der Spieler eine Zugangskarte dieser Villa dabei? */
    public boolean hatKarte(Player p, int villa) {
        for (ItemStack s : p.getInventory().getContents()) {
            Integer nr = s == null || s.isEmpty() ? null : s.getPersistentDataContainer().get(schluesselKarte, PersistentDataType.INTEGER);
            if (nr != null && nr == villa) {
                return true;
            }
        }
        return false;
    }

    /** Bewohner: Besitzer der Villa (bei einer Villa ohne Besitzer jeder) oder Gast. */
    public boolean bewohner(Player p, Block b) {
        Villen.Villa v = plugin.villen().bei(b.getLocation(), 16);
        return v == null || v.besitzer() == null || v.besitzer().equals(p.getUniqueId()) || plugin.gaeste().darfRein(v, p);
    }

    /** Bewohner wie in der Mod: Besitzer (bei einer Villa ohne Besitzer jeder) oder wer eine Zugangskarte hat. */
    public boolean istBewohner(Player p, Villen.Villa v) {
        return v == null || v.besitzer() == null || v.besitzer().equals(p.getUniqueId()) || hatKarte(p, v.nr());
    }

    /** Eigentümer: nur er stellt die Alarmstufe ein. */
    public boolean istEigentuemer(Player p, Villen.Villa v) {
        return v == null || v.besitzer() == null || v.besitzer().equals(p.getUniqueId());
    }

    /** Darf herein (Türen, Tore, Kartenleser): Bewohner, Kartenbesitzer und Gäste. */
    public boolean darfRein(Player p, Villen.Villa v) {
        return istBewohner(p, v) || plugin.gaeste().darfRein(v, p);
    }

    private boolean kartenleser(Player p, Block leser, Zustand z) {
        Villen.Villa v = plugin.villen().bei(leser.getLocation(), 16);
        // Stufe 0 (System aus): jeder kommt durch
        boolean darf = plugin.sicherheit().stufe(v) == 0 || darfRein(p, v);
        if (!darf) {
            leser.getWorld().playSound(leser.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0F, 0.6F);
            hinweis(p, "Keine Zugangskarte für diese Villa.", NamedTextColor.RED);
            if (plugin.sicherheit().stufe(v) >= 1) {
                plugin.sicherheit().alarm(v, leser, p.getName() + " hat ohne Karte versucht, eine Tür zu öffnen");
            }
            return true;
        }
        leser.getWorld().playSound(leser.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 1.6F);
        hinweis(p, "Zugang gewährt.", NamedTextColor.GREEN);
        for (Block tuer : tuerenBei(leser)) {
            Zustand t = bloecke.zustand(tuer);
            if (t == null) {
                continue;
            }
            mechanik.tuerSetzen(tuer, t, true);
            if (EISENTUEREN.contains(t.name())) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Zustand jetzt = bloecke.zustand(tuer);
                    if (jetzt != null && jetzt.ist("open", "true")) {
                        mechanik.tuerSetzen(tuer, jetzt, false);
                    }
                }, 60L);
            }
        }
        return true;
    }

    /** Untere Hälften der Türen um den Kartenleser (1 Block) samt zweitem Flügel einer Doppeltür. */
    private Set<Block> tuerenBei(Block leser) {
        Set<Block> tueren = new LinkedHashSet<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block b = leser.getRelative(dx, dy, dz);
                    if (untereTuer(b)) {
                        tueren.add(b);
                    }
                }
            }
        }
        for (Block b : Set.copyOf(tueren)) {
            for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                if (untereTuer(b.getRelative(f))) {
                    tueren.add(b.getRelative(f));
                }
            }
        }
        return tueren;
    }

    private boolean untereTuer(Block b) {
        Zustand z = bloecke.zustand(b);
        return z != null && z.ist("half", "lower") && (SICHERHEITSTUEREN.contains(z.name()) || EISENTUEREN.contains(z.name()));
    }

    /** Schließt bzw. öffnet eine Sicherheitstür samt zweitem Flügel. */
    private void doppeltuerSetzen(Block b, Zustand z, boolean auf) {
        Block unten = z.ist("half", "lower") ? b : b.getRelative(BlockFace.DOWN);
        Zustand zu = bloecke.zustand(unten);
        if (zu != null) {
            mechanik.tuerSetzen(unten, zu, auf);
        }
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block n = unten.getRelative(f);
            Zustand zn = bloecke.zustand(n);
            if (zn != null && zn.ist("half", "lower") && SICHERHEITSTUEREN.contains(zn.name())) {
                mechanik.tuerSetzen(n, zn, auf);
            }
        }
    }

    private static void hinweis(Player p, String text, NamedTextColor farbe) {
        p.sendActionBar(Component.text(text, farbe));
    }
}
