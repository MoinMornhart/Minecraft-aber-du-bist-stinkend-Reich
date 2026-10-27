package de.moinmornhart.stinkendreich.paper;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.moinmornhart.stinkendreich.paper.block.Zustand;
import de.moinmornhart.stinkendreich.paper.villa.Villen;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Smart Home wie in der Mod (Handy-Seite SMART): Licht an/aus mit acht Farben, Disco, Rollläden zu, Eingangstor und
 * Garage geschlossen halten, Automatik (abends Licht an und Rollläden zu, morgens wieder auf). Gespeichert je Villa in
 * {@code smarthome.json}; die Lampen sind die Smart-Lichter des Bauplans.
 */
public final class SmartHome {
    /** Lichtfarben der Smart-Lampen (Eigenschaft „farbe“). */
    public static final String[] FARBEN = {"weiss", "warm", "rot", "gruen", "blau", "lila", "pink", "gold"};
    private static final String[] FARB_NAMEN = {"Weiß", "Warmweiß", "Rot", "Grün", "Blau", "Lila", "Pink", "Gold"};

    private final StinkendReichPaper plugin;
    private final Path datei;
    /** Villa-Nummer → Schalter („licht“, „farbe“, „disco“, „rollo_zu“, „tor_zu“, „garage_zu“, „automatik“). */
    private final Map<Integer, Map<String, String>> schalter = new HashMap<>();
    private final Random zufall = new Random();
    /** Letzte Tageshälfte je Villa für die Automatik (true = Abend). */
    private final Map<Integer, Boolean> abend = new HashMap<>();

    public SmartHome(StinkendReichPaper plugin) {
        this.plugin = plugin;
        this.datei = plugin.getDataFolder().toPath().resolve("smarthome.json");
        try {
            if (Files.exists(datei)) {
                JsonObject j = JsonParser.parseString(Files.readString(datei, StandardCharsets.UTF_8)).getAsJsonObject();
                j.entrySet().forEach(e -> {
                    Map<String, String> m = new HashMap<>();
                    e.getValue().getAsJsonObject().entrySet().forEach(w -> m.put(w.getKey(), w.getValue().getAsString()));
                    schalter.put(Integer.parseInt(e.getKey()), m);
                });
            }
        } catch (Exception e) {
            plugin.getLogger().warning("smarthome.json konnte nicht gelesen werden: " + e.getMessage());
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::takt, 20L, 10L);
    }

    public boolean an(int villa, String was) {
        return "1".equals(schalter.getOrDefault(villa, Map.of()).get(was));
    }

    private String wert(int villa, String was, String sonst) {
        return schalter.getOrDefault(villa, Map.of()).getOrDefault(was, sonst);
    }

    private void setzen(int villa, String was, String wert) {
        schalter.computeIfAbsent(villa, k -> new HashMap<>()).put(was, wert);
        JsonObject j = new JsonObject();
        schalter.forEach((nr, m) -> {
            JsonObject o = new JsonObject();
            m.forEach(o::addProperty);
            j.add(Integer.toString(nr), o);
        });
        try {
            Files.createDirectories(datei.getParent());
            Files.writeString(datei, new GsonBuilder().setPrettyPrinting().create().toJson(j), StandardCharsets.UTF_8);
        } catch (Exception ignoriert) {
            // nächster Versuch beim nächsten Schalten
        }
    }

    /** Für die Tore: Rollläden bzw. Tor/Garage per Smart Home geschlossen? */
    public boolean rolllaedenZu(Block b) {
        Villen.Villa v = plugin.villen().bei(b.getLocation(), 16);
        return v != null && an(v.nr(), "rollo_zu");
    }

    public boolean zuGehalten(Block b, boolean garage) {
        Villen.Villa v = plugin.villen().bei(b.getLocation(), 16);
        return v != null && an(v.nr(), garage ? "garage_zu" : "tor_zu");
    }

    // ------------------------------------------------------------------
    // Schalten
    // ------------------------------------------------------------------

    /** Ein Schalter der App (wie SmartHome.befehl in der Mod). */
    public void schalten(Player p, String was, String wert) {
        Villen.Villa v = plugin.villen().vonBesitzer(p.getUniqueId());
        if (v == null) {
            p.sendActionBar(Component.text("Das Smart Home steuern nur Bewohner.", NamedTextColor.RED));
            return;
        }
        int nr = v.nr();
        boolean an = wert.equals("an") || wert.equals("zu");
        switch (was) {
            case "rollo" -> setzen(nr, "rollo_zu", an ? "1" : "0");
            case "licht" -> {
                setzen(nr, "licht", an ? "1" : "0");
                if (!an) {
                    setzen(nr, "disco", "0");
                }
                lichter(v);
            }
            case "farbe" -> {
                setzen(nr, "farbe", wert);
                setzen(nr, "licht", "1");
                setzen(nr, "disco", "0");
                lichter(v);
            }
            case "disco" -> {
                setzen(nr, "disco", an ? "1" : "0");
                if (an) {
                    setzen(nr, "licht", "1");
                }
                lichter(v);
            }
            case "automatik" -> setzen(nr, "automatik", an ? "1" : "0");
            case "tor" -> setzen(nr, "tor_zu", an ? "1" : "0");
            case "garage" -> setzen(nr, "garage_zu", an ? "1" : "0");
            default -> {
                return;
            }
        }
        p.sendActionBar(Component.text("Smart Home: erledigt", NamedTextColor.GOLD));
        plugin.getLogger().info("Smart Home: " + was + " " + wert + " (Villa " + nr + ")");
    }

    /** Alle Smart-Lampen der Villa nach den Schaltern setzen (Licht, Farbe). */
    private void lichter(Villen.Villa v) {
        boolean licht = !"0".equals(wert(v.nr(), "licht", "1"));
        String farbe = wert(v.nr(), "farbe", "warm");
        for (Block b : lampen(v)) {
            Zustand z = plugin.bloecke().zustand(b);
            if (z != null) {
                Zustand neu = z.mit("lit", Boolean.toString(licht)).mit("farbe", farbe);
                if (!neu.equals(z)) {
                    plugin.bloecke().setzen(b, neu);
                }
            }
        }
    }

    private List<Block> lampen(Villen.Villa v) {
        List<Block> liste = new ArrayList<>();
        for (Block b : plugin.bloecke().alle("smartlicht")) {
            if (v.inDerNaehe(plugin.bauplan(), b.getLocation(), 0)) {
                liste.add(b);
            }
        }
        return liste;
    }

    /** Alle halbe Sekunde: Disco (zufällige Farben) und Automatik (Abend/Morgen). */
    private void takt() {
        for (Villen.Villa v : plugin.villen().alle()) {
            int nr = v.nr();
            if (an(nr, "disco") && v.world() != null) {
                for (Block b : lampen(v)) {
                    Zustand z = plugin.bloecke().zustand(b);
                    if (z != null) {
                        plugin.bloecke().setzen(b, z.mit("lit", "true").mit("farbe", FARBEN[zufall.nextInt(FARBEN.length)]));
                    }
                }
            }
            if (an(nr, "automatik") && v.world() != null) {
                long zeit = v.world().getTime();
                boolean jetztAbend = zeit >= 12500 && zeit < 23500;
                Boolean vorher = abend.put(nr, jetztAbend);
                if (vorher != null && vorher != jetztAbend) {
                    setzen(nr, "licht", jetztAbend ? "1" : "0");
                    setzen(nr, "rollo_zu", jetztAbend ? "1" : "0");
                    lichter(v);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Handy-Seite
    // ------------------------------------------------------------------

    public void seite(Player p) {
        Villen.Villa v = plugin.villen().vonBesitzer(p.getUniqueId());
        int nr = v == null ? -1 : v.nr();
        List<Dialoge.Knopf> k = new ArrayList<>();
        boolean licht = !"0".equals(wert(nr, "licht", "1"));
        k.add(Dialoge.Knopf.von(licht ? "Licht aus" : "Licht an", (s, a) -> schalten(s, "licht", licht ? "aus" : "an")));
        k.add(Dialoge.Knopf.von(an(nr, "disco") ? "Disco aus" : "Disco an", (s, a) -> schalten(s, "disco", an(nr, "disco") ? "aus" : "an")));
        for (int i = 0; i < FARBEN.length; i++) {
            String f = FARBEN[i];
            k.add(Dialoge.Knopf.von("Licht " + FARB_NAMEN[i], (s, a) -> schalten(s, "farbe", f)));
        }
        k.add(Dialoge.Knopf.von(an(nr, "rollo_zu") ? "Rollläden öffnen" : "Rollläden schließen",
                (s, a) -> schalten(s, "rollo", an(nr, "rollo_zu") ? "auf" : "zu")));
        k.add(Dialoge.Knopf.von(an(nr, "tor_zu") ? "Eingangstor frei" : "Eingangstor bleibt zu",
                (s, a) -> schalten(s, "tor", an(nr, "tor_zu") ? "auf" : "zu")));
        k.add(Dialoge.Knopf.von(an(nr, "garage_zu") ? "Garagentor frei" : "Garagentor bleibt zu",
                (s, a) -> schalten(s, "garage", an(nr, "garage_zu") ? "auf" : "zu")));
        k.add(Dialoge.Knopf.von(an(nr, "automatik") ? "Automatik aus" : "Automatik an (abends Licht, Rollläden zu)",
                (s, a) -> schalten(s, "automatik", an(nr, "automatik") ? "aus" : "an")));
        Dialoge.zeigen(p, "Smart Home", v == null ? "Du wohnst in keiner Villa." : v.name(), List.of(), k);
    }
}
