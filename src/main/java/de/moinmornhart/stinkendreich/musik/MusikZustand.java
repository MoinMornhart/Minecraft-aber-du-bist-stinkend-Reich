package de.moinmornhart.stinkendreich.musik;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Öffentliche Schnittstelle des Musiksystems für andere Blöcke (Tanzfläche, Discokugel …).
 *
 * <p>Jede Musikanlage meldet sich hier an, sobald sie in einer geladenen Welt steht. Die Abfragen
 * funktionieren auf dem Server und auf dem Client gleich, denn der Zustand der Anlage (spielt sie,
 * welcher Titel) wird an alle Clients geschickt. Aufrufen bitte aus dem Takt der jeweiligen Welt
 * (Server-Tick bzw. Client-Tick/Darstellung), nicht aus fremden Threads.
 *
 * <pre>{@code
 * if (MusikZustand.laeuftBei(level, pos, 12)) {
 *     // Tanzfläche bunt leuchten lassen
 * }
 * }</pre>
 */
public final class MusikZustand {
    /** Positionen aller geladenen Anlagen je Welt (Server- und Client-Welt getrennt). */
    private static final Map<Level, Set<BlockPos>> ANLAGEN = Collections.synchronizedMap(new WeakHashMap<>());

    private MusikZustand() {
    }

    /**
     * Läuft im Umkreis {@code radius} um {@code pos} gerade Musik? Zählt die Anlage selbst und
     * jeden Lautsprecher, der zu einer spielenden Anlage gehört. Auch Spotify zählt, sobald der
     * DJ dort etwas abspielt.
     */
    public static boolean laeuftBei(Level level, BlockPos pos, double radius) {
        return anlageBei(level, pos, radius) != null;
    }

    /**
     * Die nächstgelegene spielende Anlage, deren Pult oder Lautsprecher höchstens {@code radius}
     * Blöcke von {@code pos} entfernt ist, sonst {@code null}. Darüber kommt man an Titel, Quelle
     * und Lautstärke ({@link MusikanlageBlockEntity#titel()} usw.).
     */
    public static MusikanlageBlockEntity anlageBei(Level level, BlockPos pos, double radius) {
        if (level == null || pos == null) {
            return null;
        }
        double grenze = radius * radius;
        MusikanlageBlockEntity beste = null;
        double besteEntfernung = Double.MAX_VALUE;
        for (BlockPos anlage : anlagen(level)) {
            if (!(level.getBlockEntity(anlage) instanceof MusikanlageBlockEntity be) || !be.spielt()) {
                continue;
            }
            double d = anlage.distSqr(pos);
            for (BlockPos box : be.lautsprecher()) {
                d = Math.min(d, box.distSqr(pos));
            }
            if (d <= grenze && d < besteEntfernung) {
                beste = be;
                besteEntfernung = d;
            }
        }
        return beste;
    }

    /** Positionen aller geladenen Musikanlagen dieser Welt (Kopie). */
    public static List<BlockPos> anlagen(Level level) {
        Set<BlockPos> menge = ANLAGEN.get(level);
        return menge == null ? List.of() : new ArrayList<>(menge);
    }

    static void anmelden(Level level, BlockPos pos) {
        if (level != null) {
            ANLAGEN.computeIfAbsent(level, l -> ConcurrentHashMap.newKeySet()).add(pos.immutable());
        }
    }

    static void abmelden(Level level, BlockPos pos) {
        if (level != null) {
            Set<BlockPos> menge = ANLAGEN.get(level);
            if (menge != null) {
                menge.remove(pos);
            }
        }
    }
}
