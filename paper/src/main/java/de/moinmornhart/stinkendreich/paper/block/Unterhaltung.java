package de.moinmornhart.stinkendreich.paper.block;

import de.moinmornhart.stinkendreich.klavier.Lieder;
import de.moinmornhart.stinkendreich.paper.Dialoge;
import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.SoundCategory;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Konzertflügel und Musikanlage wie in der Server-Version der Mod: Der Flügel spielt Stücke (Für Elise, Ode an die
 * Freude, Kanon in D) oder man spielt selbst auf einer Klaviatur im Dialogfenster (C3 … C6). Die Musikanlage spielt
 * Schallplatten von Minecraft (eigene Musik geht nur mit der Mod).
 */
public final class Unterhaltung {
    private static final int TIEFSTE = 48, HOECHSTE = 84;
    private static final float LAUTSTAERKE = 2.0F;
    private static final String[] NOTENNAMEN = {"C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "H"};
    private static final String[] LIED_NAMEN = {"Für Elise", "Ode an die Freude", "Kanon in D"};
    private static final String[] PLATTEN = {"pigstep", "otherside", "creator", "relic", "precipice", "lava_chicken",
            "cat", "chirp", "blocks", "mall", "strad", "wait"};

    /** Laufende Stücke je Flügel: Noten {Tick, MIDI} und Stand {Zeit, nächste Note}. */
    private record Vortrag(int[][] noten, int[] stand) {
    }

    private final StinkendReichPaper plugin;
    private final VirtuelleBloecke bloecke;
    private final Map<Block, Vortrag> vortraege = new HashMap<>();

    public Unterhaltung(StinkendReichPaper plugin, VirtuelleBloecke bloecke, Mechanik mechanik) {
        this.plugin = plugin;
        this.bloecke = bloecke;
        for (String teil : new String[]{"konzertfluegel", "fluegel_teil"}) {
            mechanik.anmelden(teil, (p, b, z) -> {
                fluegel(p, mitte(b));
                return true;
            });
        }
        mechanik.anmelden("musikanlage", (p, b, z) -> {
            musik(p, b);
            return true;
        });
        Bukkit.getScheduler().runTaskTimer(plugin, this::takt, 1L, 1L);
    }

    // ------------------------------------------------------------------
    // Flügel
    // ------------------------------------------------------------------

    /** Der mittlere Block des Flügels zu einem angeklickten Teil. */
    private Block mitte(Block b) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Zustand z = bloecke.zustand(b.getRelative(dx, 0, dz));
                if (z != null && z.name().equals("konzertfluegel")) {
                    return b.getRelative(dx, 0, dz);
                }
            }
        }
        return b;
    }

    private void fluegel(Player p, Block fluegel) {
        List<Dialoge.Knopf> k = new ArrayList<>();
        for (int i = 0; i < Lieder.NAMEN.length; i++) {
            String lied = Lieder.NAMEN[i];
            k.add(Dialoge.Knopf.von(i < LIED_NAMEN.length ? LIED_NAMEN[i] : lied, (s, a) -> liedSpielen(fluegel, lied)));
        }
        k.add(Dialoge.Knopf.von("Stopp", (s, a) -> vortraege.remove(fluegel)));
        k.add(Dialoge.Knopf.von("Selbst spielen (Klaviatur)", (s, a) -> klaviatur(s, fluegel)));
        Dialoge.zeigen(p, "Konzertflügel", "", List.of(), k, 1, 200, false);
    }

    /** Spielfenster mit allen Tasten von C3 bis C6; das Fenster bleibt offen. */
    public void klaviatur(Player p, Block fluegel) {
        List<Dialoge.Knopf> tasten = new ArrayList<>();
        for (int note = TIEFSTE; note <= HOECHSTE; note++) {
            int n = note;
            tasten.add(Dialoge.Knopf.von(NOTENNAMEN[note % 12] + (note / 12 - 1), (s, a) -> ton(fluegel, n)));
        }
        Dialoge.zeigen(p, "Konzertflügel", "Klick auf eine Taste spielt den Ton. Esc schließt.", List.of(), tasten, 12, 34, true);
    }

    public void liedSpielen(Block fluegel, String lied) {
        int[][] stueck = Lieder.stueck(lied);
        if (stueck != null) {
            vortraege.put(fluegel, new Vortrag(stueck, new int[]{0, 0}));
            plugin.getLogger().info("Flügel spielt " + lied);
        }
    }

    /** Ein Ton am Flügel (MIDI-Nummer, 60 = C4) mit einer Musiknote über dem Deckel. */
    private static void ton(Block fluegel, int note) {
        int n = Math.max(24, Math.min(96, note));
        int grund = Math.max(0, Math.min(4, Math.round((n - 36) / 12.0F)));
        float hoehe = (float) Math.pow(2.0, (n - (36 + grund * 12)) / 12.0);
        fluegel.getWorld().playSound(fluegel.getLocation().add(0.5, 0.5, 0.5), "stinkendreich:klavier_c" + (grund + 2),
                SoundCategory.RECORDS, LAUTSTAERKE, Math.max(0.5F, Math.min(2.0F, hoehe)));
        fluegel.getWorld().spawnParticle(Particle.NOTE, fluegel.getX() + 0.5, fluegel.getY() + 1.6, fluegel.getZ() + 0.5, 0,
                ((n - TIEFSTE) % 24) / 24.0, 0.0, 0.0, 1.0);
    }

    private void takt() {
        Iterator<Map.Entry<Block, Vortrag>> it = vortraege.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Block, Vortrag> e = it.next();
            Vortrag v = e.getValue();
            Block b = e.getKey();
            if (!b.getWorld().isChunkLoaded(b.getX() >> 4, b.getZ() >> 4) || bloecke.zustand(b) == null) {
                it.remove();
                continue;
            }
            int zeit = v.stand()[0]++;
            while (v.stand()[1] < v.noten().length && v.noten()[v.stand()[1]][0] <= zeit) {
                ton(b, v.noten()[v.stand()[1]][1]);
                v.stand()[1]++;
            }
            if (v.stand()[1] >= v.noten().length && zeit > v.noten()[v.noten().length - 1][0] + 40) {
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------------
    // Musikanlage
    // ------------------------------------------------------------------

    private void musik(Player p, Block anlage) {
        List<Dialoge.Knopf> k = new ArrayList<>();
        for (String platte : PLATTEN) {
            NamespacedKey ton = NamespacedKey.minecraft("music_disc." + platte);
            if (Registry.SOUNDS.get(ton) != null) {
                k.add(Dialoge.Knopf.von(name(platte), (s, a) -> platte(anlage, platte)));
            }
        }
        k.add(Dialoge.Knopf.von("■ Stopp", (s, a) -> platte(anlage, "stopp")));
        Dialoge.zeigen(p, "Musikanlage", "Eigene Musik geht nur mit der Mod – hier laufen die Schallplatten von Minecraft.", List.of(), k);
    }

    /** Spielt eine Schallplatte an der Musikanlage (alle in der Nähe hören sie) oder hält sie an. */
    public void platte(Block anlage, String platte) {
        for (Player q : anlage.getWorld().getPlayers()) {
            if (q.getLocation().distanceSquared(anlage.getLocation()) < 96 * 96) {
                q.stopSound(SoundCategory.RECORDS);
            }
        }
        if (!platte.equals("stopp")) {
            anlage.getWorld().playSound(anlage.getLocation().add(0.5, 0.5, 0.5), "minecraft:music_disc." + platte, SoundCategory.RECORDS, 4.0F, 1.0F);
            plugin.getLogger().info("Musikanlage spielt " + platte);
        }
    }

    private static String name(String platte) {
        return switch (platte) {
            case "pigstep" -> "Lena Raine – Pigstep";
            case "otherside" -> "Lena Raine – otherside";
            case "creator" -> "Lena Raine – Creator";
            case "relic" -> "Aaron Cherof – Relic";
            case "precipice" -> "Aaron Cherof – Precipice";
            case "lava_chicken" -> "Hyper Potions – Lava Chicken";
            default -> "C418 – " + platte;
        };
    }
}
