package de.moinmornhart.stinkendreich.paper.villa;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Bauleitung für alle Villen des Servers. Villen entstehen jederzeit:
 * <ul>
 *   <li>die erste (Nummer 0) beim Start, auch in einer Welt, die es schon vor dem Plugin gab,</li>
 *   <li>eine eigene für jeden Spieler, der dazukommt – auch während gerade eine andere gebaut wird.</li>
 * </ul>
 * Es läuft immer nur eine {@link Baustelle}; die anderen Aufträge warten in der Merkdatei {@code baustellen.json}.
 * Ein Bau, der durch einen Neustart unterbrochen wurde, geht danach an derselben Stelle weiter. Außer in einer ganz
 * neuen Welt prüft die Baustelle jeden Platz vorher auf Spielerbauten und nimmt sonst den nächsten.
 */
public final class Bauleitung {
    /** So viele Plätze werden höchstens ausprobiert, bevor ein Auftrag aufgegeben wird. */
    private static final int MAX_VERSUCHE = 60;
    /** So oft darf ein Bau mit einem Fehler abbrechen, bevor er aufgegeben wird. */
    private static final int MAX_FEHLER = 3;
    /** Eine Welt, die jünger ist als eine Minute, gilt als neu: die erste Villa kommt ohne Prüfung an den Spawn. */
    private static final long NEUE_WELT_TICKS = 20 * 60;

    /** Ein Bauauftrag. {@code y0} steht fest, sobald das Gelände angefasst wird. */
    public static final class Auftrag {
        final UUID welt;
        final int nr;
        final UUID besitzer;
        final String name;
        /** Platz vorher auf Spielerbauten prüfen? */
        final boolean pruefen;
        int x0, z0, versuch, fehler;
        Integer y0;
        boolean figurenFertig;

        Auftrag(UUID welt, int nr, UUID besitzer, String name, boolean pruefen) {
            this.welt = welt;
            this.nr = nr;
            this.besitzer = besitzer;
            this.name = name == null ? "" : name;
            this.pruefen = pruefen;
        }

        World world() {
            return Bukkit.getWorld(welt);
        }
    }

    private final StinkendReichPaper plugin;
    /** Reihenfolge = Bau-Reihenfolge; der erste ist in Arbeit, sobald {@link #baustelle} läuft. */
    private final List<Auftrag> auftraege = new ArrayList<>();
    /** Verworfene Plätze („Welt:x:z“), damit kein Auftrag dort noch einmal sucht. */
    private final Set<String> bebaut = new HashSet<>();
    /** Spieler, die auf die erste Villa warten (in der Reihenfolge, in der sie kamen; nur bis zum Neustart). */
    private final List<UUID> warteliste = new ArrayList<>();
    private Baustelle baustelle;

    public Bauleitung(StinkendReichPaper plugin) {
        this.plugin = plugin;
        laden();
    }

    // ------------------------------------------------------------------
    // Aufträge annehmen
    // ------------------------------------------------------------------

    /** Beim Start: erste Villa beauftragen, falls sie fehlt, und angefangene Bauten fortsetzen. */
    public void starten() {
        World welt = Bukkit.getWorlds().getFirst();
        if (plugin.getConfig().getBoolean("villa.amSpawn", true)) {
            ersteVilla(welt, null);
        }
        if (!auftraege.isEmpty()) {
            plugin.getLogger().info("Bauleitung: " + auftraege.size() + " Villa/Villen warten auf den Bau");
        }
        naechster();
    }

    /**
     * Beauftragt die erste Villa der Welt (falls es sie noch nicht gibt). In einer neuen Welt steht sie am Spawn, sonst
     * am nächsten Platz ohne Spielerbauten. {@code ausloeser} wird nach dem Bau ihr Herr.
     *
     * @return false, wenn es sie schon gibt oder sie schon beauftragt ist
     */
    public boolean ersteVilla(World welt, Player ausloeser) {
        if (plugin.villen().spawnVilla(welt) != null || auftraege.stream().anyMatch(a -> a.nr == 0)) {
            return false;
        }
        boolean neu = welt.getFullTime() < NEUE_WELT_TICKS;
        Auftrag a = new Auftrag(welt.getUID(), 0, null, "", !neu);
        if (!platzWaehlen(a)) {
            return false;
        }
        auftraege.addFirst(a);
        if (ausloeser != null && !warteliste.contains(ausloeser.getUniqueId())) {
            warteliste.addFirst(ausloeser.getUniqueId());
        }
        plugin.getLogger().info("Bauleitung: erste Villa beauftragt" + (neu ? " (neue Welt, am Spawn)"
                : " (bestehende Welt: Platz ohne Spielerbauten in der Nähe des Spawns)"));
        speichern();
        naechster();
        return true;
    }

    /**
     * Beauftragt eine eigene Villa für einen Spieler im Raster um die erste herum.
     *
     * @return false, wenn es (noch) keine erste Villa gibt oder der Spieler schon eine hat oder bekommt
     */
    public boolean villaFuer(UUID besitzer, String name) {
        Villen.Villa erste = ersteVilla();
        if (erste == null || plugin.villen().vonBesitzer(besitzer) != null || wartetAufVilla(besitzer)) {
            return false;
        }
        Auftrag a = new Auftrag(erste.welt(), naechsteNummer(), besitzer, name, true);
        if (!platzWaehlen(a)) {
            plugin.getLogger().severe("Bauleitung: kein freier Platz für die Villa von " + name);
            return false;
        }
        auftraege.add(a);
        speichern();
        naechster();
        return true;
    }

    /** Beim Betreten des Servers: jedem Spieler seine Villa (oder er wartet auf die erste). */
    public void beimBetreten(Player p) {
        UUID id = p.getUniqueId();
        if (plugin.villen().vonBesitzer(id) != null) {
            return;
        }
        if (wartetAufVilla(id)) {
            p.sendMessage(Component.text("Deine Villa wird gerade gebaut – gleich bringe ich dich hin.", NamedTextColor.GOLD));
            return;
        }
        Villen.Villa erste = ersteVilla();
        if (erste == null) {
            if (!plugin.getConfig().getBoolean("villa.amSpawn", true)) {
                return;
            }
            ersteVilla(Bukkit.getWorlds().getFirst(), null);
            if (!warteliste.contains(id)) {
                warteliste.add(id);
            }
            p.sendMessage(Component.text("Die Villa wird gerade gebaut – gleich bringe ich dich hin.", NamedTextColor.GOLD));
            return;
        }
        if (erste.besitzer() == null) {
            herrDerErstenVilla(p, erste);                  // der erste Spieler ist Herr der ersten Villa
            return;
        }
        if (plugin.getConfig().getBoolean("villa.mehrereVillen", true) && villaFuer(id, p.getName())) {
            p.sendMessage(Component.text("Deine eigene Villa wird gerade gebaut – gleich bringe ich dich hin.", NamedTextColor.GOLD));
        }
    }

    private void herrDerErstenVilla(Player p, Villen.Villa erste) {
        plugin.villen().besitzerSetzen(erste, p.getUniqueId(), p.getName());
        Villen.Villa v = plugin.villen().nr(0);
        p.getInventory().addItem(plugin.zugang().karte(v));
        if (!v.inDerNaehe(plugin.bauplan(), p.getLocation(), 0)) {
            p.teleport(v.spawn(plugin.bauplan()));
        }
        p.sendMessage(Component.text("Willkommen in deiner Villa! Deine Zugangskarte liegt im Inventar.", NamedTextColor.GOLD));
    }

    /** Wird für diesen Spieler gerade eine Villa gebaut oder wartet sein Bau? */
    public boolean wartetAufVilla(UUID spieler) {
        return auftraege.stream().anyMatch(a -> spieler.equals(a.besitzer));
    }

    private Villen.Villa ersteVilla() {
        return plugin.villen().alle().stream().filter(v -> v.nr() == 0).findFirst().orElse(null);
    }

    /**
     * Freigehaltener Platz für eine künftige Villa (Grundstück x0, z0, x1, z1), in dessen Fläche diese Stelle liegt,
     * sonst null: die beiden inneren Ringe des Rasters um die erste Villa – die Plätze, die neue Spieler der Reihe nach
     * bekommen –, solange dort keine Villa steht oder entsteht und der Platz nicht wegen Spielerbauten verworfen wurde.
     * Rundum Anblendung + 8 Blöcke Rand, nach Süden zusätzlich die Zufahrt. Straßen fahren darum herum: eine Straße über
     * einem Platz machte ihn bisher unbrauchbar (die Prüfung hielt sie für Spielerbauten).
     */
    public int[] reservierterPlatz(World w, int x, int z) {
        Villen.Villa erste = ersteVilla();
        if (erste == null || !erste.welt().equals(w.getUID()) || !plugin.getConfig().getBoolean("villa.mehrereVillen", true)) {
            return null;
        }
        Bauplan plan = plugin.bauplan();
        int abstand = Math.max(240, plugin.getConfig().getInt("villa.abstand", 320));
        int rand = Math.max(4, plugin.getConfig().getInt("villa.anblendung", 12)) + 8;
        int i = Math.round((float) (x - erste.x() - plan.breiteX / 2) / abstand);
        int j = Math.round((float) (z - erste.z() - plan.breiteZ / 2) / abstand);
        int ring = Math.max(Math.abs(i), Math.abs(j));
        if (ring < 1 || ring > 2) {
            return null;
        }
        int x0 = erste.x() + i * abstand, z0 = erste.z() + j * abstand;
        if (x < x0 - rand || x >= x0 + plan.breiteX + rand || z < z0 - rand || z >= z0 + plan.breiteZ + rand + 40) {
            return null;
        }
        boolean villa = plugin.villen().alle().stream().anyMatch(v -> v.welt().equals(erste.welt())
                && Math.max(Math.abs(v.x() - x0), Math.abs(v.z() - z0)) < abstand / 2);
        boolean auftrag = auftraege.stream().anyMatch(b -> b.welt.equals(erste.welt())
                && Math.max(Math.abs(b.x0 - x0), Math.abs(b.z0 - z0)) < abstand / 2);
        if (villa || auftrag || bebaut.contains(schluessel(erste.welt(), x0, z0))) {
            return null;
        }
        return new int[]{x0, z0, x0 + plan.breiteX, z0 + plan.breiteZ};
    }

    /** Nächste freie Nummer: auch die Villen, die noch gebaut werden, zählen mit. */
    public int naechsteNummer() {
        int villen = plugin.villen().alle().stream().mapToInt(Villen.Villa::nr).max().orElse(-1);
        int warten = auftraege.stream().mapToInt(a -> a.nr).max().orElse(-1);
        return Math.max(villen, warten) + 1;
    }

    // ------------------------------------------------------------------
    // Platzwahl
    // ------------------------------------------------------------------

    /** Sucht ab {@code a.versuch} den ersten Platz, der weder belegt noch als bebaut verworfen ist. */
    private boolean platzWaehlen(Auftrag a) {
        World w = a.world();
        Bauplan plan = plugin.bauplan();
        Villen.Villa erste = ersteVilla();
        int abstand = Math.max(240, plugin.getConfig().getInt("villa.abstand", 320));
        int rand = Math.max(4, plugin.getConfig().getInt("villa.anblendung", 12));
        // Schrittweite der Suche um den Spawn: Grundstück, Zufahrt und Minengürtel dürfen sich nicht überlappen
        int schritt = Math.max(plan.breiteX, plan.breiteZ) + 2 * rand + 48;
        for (; a.versuch < MAX_VERSUCHE; a.versuch++) {
            int x, z;
            if (a.nr == 0) {
                Location spawn = w.getSpawnLocation();
                int[] p = a.versuch == 0 ? new int[]{0, 0} : Villen.platz(a.versuch);
                x = spawn.getBlockX() - plan.breiteX / 2 + p[0] * schritt;
                z = spawn.getBlockZ() - plan.breiteZ / 2 + p[1] * schritt;
            } else {
                if (erste == null) {
                    return false;
                }
                int[] p = Villen.platz(a.versuch + 1);
                x = erste.x() + p[0] * abstand;
                z = erste.z() + p[1] * abstand;
            }
            if (!bebaut.contains(schluessel(a.welt, x, z)) && !belegt(a, x, z, a.nr == 0 ? schritt : abstand)) {
                a.x0 = x;
                a.z0 = z;
                return true;
            }
        }
        return false;
    }

    /** Steht dort schon eine Villa oder wird dort schon eine gebaut? */
    private boolean belegt(Auftrag a, int x, int z, int abstand) {
        boolean villa = plugin.villen().alle().stream().anyMatch(v -> v.welt().equals(a.welt)
                && Math.max(Math.abs(v.x() - x), Math.abs(v.z() - z)) < abstand / 2);
        boolean auftrag = auftraege.stream().anyMatch(b -> b != a && b.welt.equals(a.welt)
                && Math.max(Math.abs(b.x0 - x), Math.abs(b.z0 - z)) < abstand / 2);
        return villa || auftrag;
    }

    private static String schluessel(UUID welt, int x, int z) {
        return welt + ":" + x + ":" + z;
    }

    // ------------------------------------------------------------------
    // Rückmeldungen der Baustelle
    // ------------------------------------------------------------------

    private void naechster() {
        if (baustelle != null || auftraege.isEmpty()) {
            return;
        }
        Auftrag a = auftraege.getFirst();
        if (a.world() == null) {
            plugin.getLogger().warning("Bauleitung: Welt für Villa " + a.nr + " fehlt – Auftrag verworfen");
            auftraege.removeFirst();
            speichern();
            naechster();
            return;
        }
        baustelle = new Baustelle(plugin, this, a);
        baustelle.starten();
    }

    /** Nächster Auftrag einen Tick später (die Baustelle beendet gerade ihren eigenen Tick). */
    private void spaeterWeiter() {
        baustelle = null;
        plugin.getServer().getScheduler().runTask(plugin, this::naechster);
    }

    /** Der Platz ist bebaut: merken und den nächsten suchen. */
    void platzBebaut(Auftrag a, String grund) {
        plugin.getLogger().info("Bauleitung: Platz " + a.x0 + " " + a.z0 + " für Villa " + a.nr + " verworfen (" + grund + ")");
        bebaut.add(schluessel(a.welt, a.x0, a.z0));
        a.versuch++;
        if (!platzWaehlen(a)) {
            plugin.getLogger().severe("Bauleitung: kein Platz ohne Spielerbauten für Villa " + a.nr + " gefunden");
            auftraege.remove(a);
            Player p = a.besitzer == null ? null : Bukkit.getPlayer(a.besitzer);
            if (p != null) {
                p.sendMessage(Component.text("Für deine Villa wurde kein freier Platz gefunden.", NamedTextColor.RED));
            }
        }
        speichern();
        spaeterWeiter();
    }

    /** Ab jetzt wird die Welt verändert: Bodenhöhe merken, damit es nach einem Neustart hier weitergeht. */
    void gelaendeBeginnt(Auftrag a) {
        speichern();
    }

    void figurenAufgestellt(Auftrag a) {
        a.figurenFertig = true;
        speichern();
    }

    void fehler(Auftrag a) {
        a.fehler++;
        auftraege.remove(a);
        if (a.fehler < MAX_FEHLER) {
            auftraege.add(a);                           // später noch einmal (am selben Platz)
        } else {
            plugin.getLogger().severe("Bauleitung: Villa " + a.nr + " nach " + a.fehler + " Fehlern aufgegeben");
        }
        speichern();
        spaeterWeiter();
    }

    void fertig(Auftrag a, Villen.Villa villa) {
        auftraege.remove(a);
        speichern();
        if (villa.nr() == 0) {
            ersteVillaFertig(villa);
        } else {
            plugin.strassen().zurVilla(villa);          // Straße von der ersten Villa bis vor die Tür
            Player p = a.besitzer == null ? null : Bukkit.getPlayer(a.besitzer);
            if (p != null) {
                p.teleport(villa.spawn(plugin.bauplan()));
                p.getInventory().addItem(plugin.zugang().karte(villa));
                p.sendMessage(Component.text("Willkommen in deiner eigenen Villa! Deine Zugangskarte liegt im Inventar.",
                        NamedTextColor.GOLD));
            }
        }
        spaeterWeiter();
    }

    /** Wer auf die erste Villa gewartet hat: der Erste wird ihr Herr, alle anderen bekommen ihre eigene. */
    private void ersteVillaFertig(Villen.Villa villa) {
        List<UUID> liste = new ArrayList<>(warteliste);
        warteliste.clear();
        for (UUID id : liste) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || plugin.villen().vonBesitzer(id) != null) {
                continue;
            }
            Villen.Villa erste = plugin.villen().nr(0);
            if (erste.besitzer() == null) {
                herrDerErstenVilla(p, erste);
            } else if (plugin.getConfig().getBoolean("villa.mehrereVillen", true) && villaFuer(id, p.getName())) {
                p.sendMessage(Component.text("Deine eigene Villa wird jetzt gebaut – gleich bringe ich dich hin.", NamedTextColor.GOLD));
            }
        }
    }

    // ------------------------------------------------------------------
    // Anzeige und Ende
    // ------------------------------------------------------------------

    public Baustelle baustelle() {
        return baustelle;
    }

    /** Beim Abschalten: Lade-Tickets zurückgeben; der Auftrag bleibt in der Merkdatei und geht nach dem Start weiter. */
    public void stoppen() {
        if (baustelle != null) {
            baustelle.abbrechen();
            baustelle = null;
        }
    }

    // ------------------------------------------------------------------
    // Merkdatei
    // ------------------------------------------------------------------

    private Path datei() {
        return plugin.getDataFolder().toPath().resolve("baustellen.json");
    }

    private void laden() {
        try {
            if (!Files.exists(datei())) {
                return;
            }
            JsonObject j = JsonParser.parseString(Files.readString(datei(), StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : j.getAsJsonArray("auftraege")) {
                JsonObject o = e.getAsJsonObject();
                Auftrag a = new Auftrag(UUID.fromString(o.get("welt").getAsString()), o.get("nr").getAsInt(),
                        o.has("besitzer") ? UUID.fromString(o.get("besitzer").getAsString()) : null,
                        o.has("name") ? o.get("name").getAsString() : "", o.get("pruefen").getAsBoolean());
                a.x0 = o.get("x").getAsInt();
                a.z0 = o.get("z").getAsInt();
                a.versuch = o.get("versuch").getAsInt();
                a.fehler = o.has("fehler") ? o.get("fehler").getAsInt() : 0;
                a.y0 = o.has("y0") ? o.get("y0").getAsInt() : null;
                a.figurenFertig = o.has("figurenFertig") && o.get("figurenFertig").getAsBoolean();
                // Schon fertig gebaut (Merkdatei nicht mehr geschrieben)? Dann nicht noch einmal.
                if (plugin.villen().nr(a.nr) == null) {
                    auftraege.add(a);
                }
            }
            if (j.has("bebaut")) {
                j.getAsJsonArray("bebaut").forEach(e -> bebaut.add(e.getAsString()));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("baustellen.json konnte nicht gelesen werden: " + e.getMessage());
        }
    }

    private void speichern() {
        JsonArray liste = new JsonArray();
        for (Auftrag a : auftraege) {
            JsonObject o = new JsonObject();
            o.addProperty("welt", a.welt.toString());
            o.addProperty("nr", a.nr);
            if (a.besitzer != null) {
                o.addProperty("besitzer", a.besitzer.toString());
                o.addProperty("name", a.name);
            }
            o.addProperty("pruefen", a.pruefen);
            o.addProperty("x", a.x0);
            o.addProperty("z", a.z0);
            o.addProperty("versuch", a.versuch);
            o.addProperty("fehler", a.fehler);
            if (a.y0 != null) {
                o.addProperty("y0", a.y0);
            }
            o.addProperty("figurenFertig", a.figurenFertig);
            liste.add(o);
        }
        JsonObject j = new JsonObject();
        j.add("auftraege", liste);
        JsonArray b = new JsonArray();
        bebaut.stream().sorted().forEach(b::add);
        j.add("bebaut", b);
        try {
            Files.createDirectories(datei().getParent());
            Files.writeString(datei(), new GsonBuilder().setPrettyPrinting().create().toJson(j), StandardCharsets.UTF_8);
        } catch (Exception e) {
            plugin.getLogger().warning("baustellen.json konnte nicht geschrieben werden: " + e.getMessage());
        }
    }

    /** Für Tests und Infos: kurze Übersicht der wartenden Aufträge. */
    public String uebersicht() {
        StringBuilder s = new StringBuilder();
        for (Auftrag a : auftraege) {
            s.append(s.isEmpty() ? "" : ", ").append("Villa ").append(a.nr)
                    .append(a.name.isEmpty() ? "" : " (" + a.name + ")").append(" bei ").append(a.x0).append(' ').append(a.z0);
        }
        return s.isEmpty() ? "keine" : s.toString();
    }
}
