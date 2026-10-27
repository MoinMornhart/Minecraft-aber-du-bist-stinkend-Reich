package de.moinmornhart.stinkendreich.paper.block;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Ein Blockzustand der Mod, z. B. {@code stinkendreich:prunktuer[facing=north,half=lower,open=false]}. Auf dem
 * Paper-Server gibt es diese Blöcke nicht wirklich; das Plugin merkt sich den Zustand an der Anzeige-Figur des Blocks.
 */
public record Zustand(String block, Map<String, String> eigenschaften) {
    public Zustand {
        eigenschaften = Collections.unmodifiableMap(new TreeMap<>(eigenschaften));
    }

    /** Liest „namespace:name[a=b,c=d]“. */
    public static Zustand lesen(String text) {
        int klammer = text.indexOf('[');
        if (klammer < 0) {
            return new Zustand(text, Map.of());
        }
        Map<String, String> e = new TreeMap<>();
        String innen = text.substring(klammer + 1, text.length() - 1);
        if (!innen.isEmpty()) {
            for (String teil : innen.split(",")) {
                String[] kv = teil.split("=", 2);
                e.put(kv[0], kv.length > 1 ? kv[1] : "");
            }
        }
        return new Zustand(text.substring(0, klammer), e);
    }

    /** Kurzname ohne Namensraum, z. B. „prunktuer“. */
    public String name() {
        int i = block.indexOf(':');
        return i < 0 ? block : block.substring(i + 1);
    }

    public String wert(String eigenschaft) {
        return eigenschaften.get(eigenschaft);
    }

    public boolean ist(String eigenschaft, String wert) {
        return wert.equals(eigenschaften.get(eigenschaft));
    }

    /** Derselbe Zustand mit einer zusätzlichen Eigenschaft (auch wenn sie bisher fehlte). */
    public Zustand ergaenzt(String eigenschaft, String wert) {
        Map<String, String> e = new TreeMap<>(eigenschaften);
        e.put(eigenschaft, wert);
        return new Zustand(block, e);
    }

    /** Derselbe Zustand mit einer geänderten Eigenschaft (nur wenn es sie gibt). */
    public Zustand mit(String eigenschaft, String wert) {
        if (!eigenschaften.containsKey(eigenschaft)) {
            return this;
        }
        Map<String, String> e = new TreeMap<>(eigenschaften);
        e.put(eigenschaft, wert);
        return new Zustand(block, e);
    }

    /** Schlüssel der Eigenschaften wie in bloecke.json („a=b,c=d“, sortiert). */
    public String schluessel() {
        StringBuilder sb = new StringBuilder();
        eigenschaften.forEach((k, v) -> {
            if (!sb.isEmpty()) {
                sb.append(',');
            }
            sb.append(k).append('=').append(v);
        });
        return sb.toString();
    }

    @Override
    public String toString() {
        return eigenschaften.isEmpty() ? block : block + "[" + schluessel() + "]";
    }
}
