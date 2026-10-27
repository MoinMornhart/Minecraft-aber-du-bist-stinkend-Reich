package de.moinmornhart.stinkendreich.paper.butler;

import com.google.gson.JsonArray;
import de.moinmornhart.stinkendreich.paper.Dialoge;
import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import de.moinmornhart.stinkendreich.paper.villa.Ausstattung;
import de.moinmornhart.stinkendreich.paper.villa.FigurenUebersetzen;
import de.moinmornhart.stinkendreich.paper.villa.Villen;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Butler James auf dem Paper-Server. Er besteht aus zwei Figuren: einer Schaufensterpuppe mit seiner Haut (das,
 * was man sieht) und einem unsichtbaren Körper, der den Weg findet (ein Händler ohne eigene Ziele). Die Puppe folgt
 * dem Körper jeden Tick. James folgt seinem Herrn, wartet oder geht nach Hause; Befehle im Chat („Butler, komm“)
 * oder per Rechtsklick über ein Menü.
 */
public final class Butler implements Listener {
    /** Was James gerade tut. */
    public enum Modus { FOLGEN, WARTEN, HEIM, ARBEIT }

    private final StinkendReichPaper plugin;
    private final NamespacedKey schluesselVilla, schluesselRolle, schluesselModus, schluesselHerr;
    /** Villa-Nummer → Puppe und Körper (nur geladene). */
    private final Map<Integer, Mannequin> puppen = new HashMap<>();
    private final Map<Integer, WanderingTrader> koerper = new HashMap<>();
    private final Map<Integer, Location> letzteStelle = new HashMap<>();
    private final Map<Integer, Integer> stillstand = new HashMap<>();
    /** Laufende Aufträge (Bergbau, Holz, Schmelzen, Liefern, Sortieren) je Villa. */
    private final Map<Integer, Auftrag> auftraege = new HashMap<>();

    public Butler(StinkendReichPaper plugin) {
        this.plugin = plugin;
        schluesselVilla = new NamespacedKey(plugin, "butler");
        schluesselRolle = new NamespacedKey(plugin, "rolle");
        schluesselModus = new NamespacedKey(plugin, "modus");
        schluesselHerr = new NamespacedKey(plugin, "herr");
        for (var w : Bukkit.getWorlds()) {
            w.getEntities().forEach(this::aufnehmen);
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        // Das nächste Dorf schon eine Minute nach dem Start suchen (kostet einmal 2–4 s), damit der erste Flug nicht hakt
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.villen().alle().stream()
                .filter(v -> v.nr() == 0 && v.world() != null)
                .forEach(v -> dorf(v.spawn(plugin.bauplan()))), 20L * 60);
    }

    /** Stellt James in der Halle einer frisch gebauten Villa auf. */
    public void erzeugen(Villen.Villa villa) {
        JsonArray m = plugin.bauplan().ersteMarke("butler");
        if (m == null) {
            return;
        }
        Location ort = Ausstattung.ort(villa, m);
        WanderingTrader k = ort.getWorld().spawn(ort, WanderingTrader.class, t -> {
            t.setPersistent(true);
            t.setRemoveWhenFarAway(false);
            t.setInvisible(true);
            t.setSilent(true);
            t.setInvulnerable(true);
            t.setDespawnDelay(0);
            t.setCollidable(false);
            t.getPersistentDataContainer().set(schluesselVilla, PersistentDataType.INTEGER, villa.nr());
            t.getPersistentDataContainer().set(schluesselRolle, PersistentDataType.STRING, "koerper");
        });
        Bukkit.getMobGoals().removeAllGoals(k);
        Mannequin p = FigurenUebersetzen.puppe(ort, "butler");
        p.setInvulnerable(true);
        p.customName(Component.text(villa.besitzerName().isEmpty() ? "James" : "James · " + villa.besitzerName(), NamedTextColor.GOLD));
        p.setCustomNameVisible(true);
        p.getPersistentDataContainer().set(schluesselVilla, PersistentDataType.INTEGER, villa.nr());
        p.getPersistentDataContainer().set(schluesselRolle, PersistentDataType.STRING, "puppe");
        p.getPersistentDataContainer().set(schluesselModus, PersistentDataType.STRING, Modus.WARTEN.name());
        if (villa.besitzer() != null) {
            p.getPersistentDataContainer().set(schluesselHerr, PersistentDataType.STRING, villa.besitzer().toString());
        }
        koerper.put(villa.nr(), k);
        puppen.put(villa.nr(), p);
    }

    private void aufnehmen(Entity e) {
        Integer nr = e.getPersistentDataContainer().get(schluesselVilla, PersistentDataType.INTEGER);
        if (nr == null) {
            return;
        }
        if (e instanceof Mannequin p) {
            puppen.put(nr, p);
        } else if (e instanceof WanderingTrader k) {
            Bukkit.getMobGoals().removeAllGoals(k);
            koerper.put(nr, k);
        }
    }

    @EventHandler
    public void beimLaden(EntitiesLoadEvent e) {
        e.getEntities().forEach(this::aufnehmen);
    }

    // ------------------------------------------------------------------
    // Herr, Modus
    // ------------------------------------------------------------------

    /** Herr des Butlers: Besitzer der Villa, bei der Spawn-Villa der erste Spieler. */
    private UUID herr(int nr) {
        Villen.Villa v = plugin.villen().nr(nr);
        return v == null ? null : v.besitzer();
    }

    private Modus modus(Mannequin p) {
        String m = p.getPersistentDataContainer().get(schluesselModus, PersistentDataType.STRING);
        try {
            return m == null ? Modus.WARTEN : Modus.valueOf(m);
        } catch (IllegalArgumentException e) {
            return Modus.WARTEN;
        }
    }

    public void modusSetzen(int nr, Modus m) {
        Mannequin p = puppen.get(nr);
        if (p != null) {
            p.getPersistentDataContainer().set(schluesselModus, PersistentDataType.STRING, m.name());
        }
    }

    /** Der Butler, der diesem Spieler gehört (Nummer der Villa) oder -1. */
    public int butlerVon(Player spieler) {
        Villen.Villa v = plugin.villen().vonBesitzer(spieler.getUniqueId());
        return v == null ? -1 : v.nr();
    }

    // ------------------------------------------------------------------
    // Bewegung
    // ------------------------------------------------------------------

    private void tick() {
        for (Map.Entry<Integer, Mannequin> e : puppen.entrySet()) {
            Mannequin p = e.getValue();
            WanderingTrader k = koerper.get(e.getKey());
            if (p == null || !p.isValid() || k == null || !k.isValid()) {
                continue;
            }
            int nr = e.getKey();
            Auftrag a = auftraege.get(nr);
            if (a != null) {
                if (a.tick() == Auftrag.Stand.FERTIG) {
                    auftraege.remove(nr);
                    modusSetzen(nr, Modus.FOLGEN);
                }
            } else if (Bukkit.getCurrentTick() % 10 == 0) {
                ziehen(nr, p, k);
            }
            if (p.isInsideVehicle()) {
                continue;                                   // sitzt am Steuer (Flug): der Körper reist mit
            }
            // Die Puppe steht immer dort, wo der Körper ist, und schaut in seine Richtung
            Location ziel = k.getLocation();
            if (p.getLocation().distanceSquared(ziel) > 0.0001 || Math.abs(p.getYaw() - ziel.getYaw()) > 1) {
                p.teleport(ziel);
                p.setBodyYaw(k.getBodyYaw());
            }
        }
    }

    /** Alle halbe Sekunde: Ziel des Körpers je nach Modus. */
    private void ziehen(int nr, Mannequin p, WanderingTrader k) {
        UUID herrId = herr(nr);
        Player herr = herrId == null ? null : Bukkit.getPlayer(herrId);
        switch (modus(p)) {
            case FOLGEN -> {
                if (herr == null || !herr.getWorld().equals(k.getWorld())) {
                    return;
                }
                double abstand = herr.getLocation().distance(k.getLocation());
                if (abstand > 32) {
                    // Zu weit weg (oder hängt fest): neben den Herrn treten
                    k.teleport(hinter(herr));
                } else if (abstand > 3.5) {
                    k.getPathfinder().moveTo(herr.getLocation(), abstand > 10 ? 1.2 : 0.9);
                    festhaengen(nr, k, herr);
                } else {
                    k.getPathfinder().stopPathfinding();
                    k.lookAt(herr.getEyeLocation());
                }
            }
            case HEIM -> {
                Villen.Villa v = plugin.villen().nr(nr);
                JsonArray m = plugin.bauplan().ersteMarke("butler");
                if (v == null || m == null) {
                    return;
                }
                Location halle = Ausstattung.ort(v, m);
                if (!halle.getWorld().equals(k.getWorld()) || halle.distance(k.getLocation()) > 64) {
                    k.teleport(halle);
                } else if (halle.distance(k.getLocation()) > 1.5) {
                    k.getPathfinder().moveTo(halle, 0.9);
                    festhaengen(nr, k, null);
                } else {
                    modusSetzen(nr, Modus.WARTEN);
                }
            }
            case WARTEN -> k.getPathfinder().stopPathfinding();
        }
    }

    /** Kommt der Körper 6 Sekunden nicht voran (z. B. vor einer geschlossenen Tür der Mod), springt er ans Ziel. */
    private void festhaengen(int nr, WanderingTrader k, Player herr) {
        Location jetzt = k.getLocation();
        Location vorher = letzteStelle.put(nr, jetzt);
        if (vorher != null && vorher.getWorld().equals(jetzt.getWorld()) && vorher.distanceSquared(jetzt) < 0.04) {
            int n = stillstand.merge(nr, 1, Integer::sum);
            if (n >= 12) {
                stillstand.put(nr, 0);
                if (herr != null) {
                    k.teleport(hinter(herr));
                } else {
                    Villen.Villa v = plugin.villen().nr(nr);
                    JsonArray m = plugin.bauplan().ersteMarke("butler");
                    if (v != null && m != null) {
                        k.teleport(Ausstattung.ort(v, m));
                    }
                }
            }
        } else {
            stillstand.put(nr, 0);
        }
    }

    private static Location hinter(Player p) {
        Location l = p.getLocation();
        return l.clone().subtract(l.getDirection().setY(0).normalize().multiply(1.5));
    }

    // ------------------------------------------------------------------
    // Ansprechen
    // ------------------------------------------------------------------

    @EventHandler
    public void beimChat(AsyncChatEvent e) {
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        String klein = text.toLowerCase(Locale.GERMAN);
        if (!(klein.startsWith("butler") || klein.startsWith("james"))) {
            return;
        }
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> befehl(p, klein));
    }

    /** Versteht einfache Sätze wie in der Mod: „komm“, „warte“, „geh nach Hause“. */
    public void befehl(Player p, String satz) {
        int nr = butlerVon(p);
        plugin.getLogger().info("Butler: „" + satz + "“ von " + p.getName() + " (Villa " + nr + ")");
        if (nr < 0) {
            sagen(p, "Verzeihung, ich stehe nicht in Ihren Diensten.");
            return;
        }
        Bergbau.Rohstoff rohstoff = Bergbau.erkennen(satz);
        boolean holen = satz.contains("hol") || satz.contains("bring") || satz.contains("besorg") || satz.contains("abbau")
                || satz.contains("grab") || satz.contains("such");
        if (rohstoff != null && holen) {
            auftragGeben(p, nr, rohstoff, menge(satz));
            return;
        }
        if ((satz.contains("holz") || satz.contains("stämme")) && (holen || satz.contains("fäll") || satz.contains("hack"))
                || satz.contains("bäume") && (satz.contains("fäll") || satz.contains("hack"))) {
            auftragGeben(p, nr, Holzfaeller.HOLZ, menge(satz));
            return;
        }
        if (satz.contains("fahr") && !satz.contains("flieg")) {
            fahren(p, nr, satz);
            return;
        }
        if (satz.contains("flieg") || satz.contains("hubschrauber") || satz.contains("heli")) {
            fliegen(p, nr, satz);
            return;
        }
        if (satz.contains("jag") || satz.contains("fleisch") && holen) {
            if (!plugin.getConfig().getBoolean("butler.jagen", true)) {
                sagen(p, "Verzeihung, die Jagd ist auf diesem Server nicht erlaubt.");
            } else {
                auftragGeben(p, nr, Jaeger.FLEISCH, menge(satz, 8));
            }
            return;
        }
        if (satz.contains("sortier") || satz.contains("räum") && satz.contains("truhe")) {
            sortieren(p, nr);
            return;
        }
        if (satz.contains("was machst") || satz.contains("status") || satz.contains("wie weit")) {
            Auftrag a = auftraege.get(nr);
            sagen(p, a == null ? "Ich stehe Ihnen zur Verfügung." : "Ich " + a.status() + ".");
            return;
        }
        Auftrag alt = auftraege.remove(nr);
        if (alt != null) {
            alt.abbrechen();                                // neuer Befehl: Auftrag abbrechen
        }
        if (satz.contains("komm") || satz.contains("folg") || satz.contains("hier") || satz.contains("ruf")) {
            modusSetzen(nr, Modus.FOLGEN);
            sagen(p, "Sehr wohl, ich komme.");
        } else if (satz.contains("wart") || satz.contains("bleib") || satz.contains("stopp") || satz.contains("halt")) {
            modusSetzen(nr, Modus.WARTEN);
            sagen(p, "Ich warte hier auf Sie.");
        } else if (satz.contains("hause") || satz.contains("heim") || satz.contains("villa")) {
            modusSetzen(nr, Modus.HEIM);
            sagen(p, "Ich gehe zurück in die Villa.");
        } else {
            sagen(p, "Verzeihung, das habe ich nicht verstanden. Ich kann Ihnen folgen, warten, nach Hause gehen oder "
                    + "Erz oder Holz holen (z. B. „hol mir 16 Eisen“), jagen, die Truhen sortieren und Sie mit dem Hubschrauber fliegen.");
        }
    }

    /** Erste Zahl im Satz (1–64), sonst 16. */
    private static int menge(String satz) {
        return menge(satz, 16);
    }

    private static int menge(String satz, int sonst) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(satz);
        return m.find() ? Math.clamp(Integer.parseInt(m.group()), 1, 64) : sonst;
    }

    /** „Hol mir 16 Eisen“ / „… 16 Holz“ / „geh jagen“: abbauen, Bäume fällen oder jagen, bei Metallen schmelzen, bringen. */
    private void auftragGeben(Player p, int nr, Bergbau.Rohstoff r, int menge) {
        Mannequin puppe = puppen.get(nr);
        WanderingTrader k = koerper.get(nr);
        Villen.Villa v = plugin.villen().nr(nr);
        if (puppe == null || k == null || v == null) {
            sagen(p, "Verzeihung, ich bin gerade nicht erreichbar.");
            return;
        }
        Auftrag alt = auftraege.remove(nr);
        if (alt != null) {
            alt.abbrechen();
        }
        k.getPathfinder().stopPathfinding();
        Location start = ButlerAuftrag.vorDerVilla(plugin, v);
        Beschaffung b = r == Holzfaeller.HOLZ ? new Holzfaeller(plugin, k, puppe, menge, start)
                : r == Jaeger.FLEISCH ? new Jaeger(plugin, k, puppe, menge, start)
                : new Bergbau(plugin, k, puppe, r, menge, start);
        auftraege.put(nr, new ButlerAuftrag(plugin, v, k, puppe, p.getUniqueId(), b));
        modusSetzen(nr, Modus.ARBEIT);
        sagen(p, r == Holzfaeller.HOLZ ? "Sehr wohl. Ich fälle " + menge + " Stämme und pflanze neue Bäume."
                : r == Jaeger.FLEISCH ? "Sehr wohl. Ich gehe auf die Jagd und bringe Ihnen " + menge + " Fleisch."
                : "Sehr wohl. Ich hole Ihnen " + menge + " " + r.anzeige() + (r.geschmolzen() != null ? " und schmelze es in der Villa." : "."));
        plugin.getLogger().info("Butler: Auftrag " + menge + " " + r.name() + " (Villa " + nr + ")");
    }

    /**
     * „Flieg mich nach Hause / ins Dorf / zur Villa von Max“: James setzt sich in den Hubschrauber der Villa, holt den
     * Herrn ab und fliegt ihn hin.
     */
    public void fliegen(Player p, int nr, String satz) {
        fliegen(p, nr, satz, true);
    }

    private void fliegen(Player p, int nr, String satz, boolean nochmal) {
        Mannequin puppe = puppen.get(nr);
        WanderingTrader k = koerper.get(nr);
        Villen.Villa eigene = plugin.villen().nr(nr);
        if (puppe == null || k == null || eigene == null) {
            sagen(p, "Verzeihung, ich bin gerade nicht erreichbar.");
            return;
        }
        // Ziel
        Location ziel = null;
        String name = null;
        for (Villen.Villa v : plugin.villen().alle()) {
            String herrName = v.besitzerName() == null ? "" : v.besitzerName().toLowerCase(Locale.GERMAN);
            if (v != eigene && !herrName.isEmpty() && satz.contains(herrName)) {
                ziel = Flug.landeplatzBei(v.spawn(plugin.bauplan()));
                name = "zur " + v.name();
            }
        }
        if (ziel == null && (satz.contains("hause") || satz.contains("heim") || satz.contains("meine villa") || satz.contains("zurück"))) {
            JsonArray pad = plugin.bauplan().ersteMarke("hubschrauber");
            ziel = pad != null ? Ausstattung.ort(eigene, pad) : eigene.spawn(plugin.bauplan());
            name = "nach Hause";
        }
        if (ziel == null && satz.contains("dorf")) {
            ziel = dorf(p.getLocation());
            name = "ins Dorf";
            if (ziel == null) {
                sagen(p, "Verzeihung, in der Nähe gibt es kein Dorf.");
                return;
            }
        }
        if (ziel == null) {
            sagen(p, "Wohin darf ich Sie fliegen? Nach Hause, ins Dorf oder zur Villa eines Nachbarn?");
            return;
        }
        // Hubschrauber: der, in dem der Herr sitzt, sonst der der Villa
        org.bukkit.entity.ArmorStand heli = p.getVehicle() != null ? plugin.fahrzeuge().hubschrauber(p.getLocation(), 6) : null;
        if (heli == null) {
            JsonArray pad = plugin.bauplan().ersteMarke("hubschrauber");
            heli = pad == null ? null : plugin.fahrzeuge().hubschrauber(Ausstattung.ort(eigene, pad), 64);
        }
        if (heli == null) {
            heli = plugin.fahrzeuge().hubschrauber(p.getLocation(), 64);
        }
        JsonArray padMarke = plugin.bauplan().ersteMarke("hubschrauber");
        if (heli == null && nochmal && padMarke != null) {
            // Der Landeplatz ist weit weg und nicht geladen: kurz laden, dann noch einmal suchen
            org.bukkit.Chunk c = Ausstattung.ort(eigene, padMarke).getChunk();
            c.addPluginChunkTicket(plugin);
            Bukkit.getScheduler().runTaskLater(plugin, () -> fliegen(p, nr, satz, false), 40L);
            Bukkit.getScheduler().runTaskLater(plugin, () -> c.removePluginChunkTicket(plugin), 20L * 15);
            return;
        }
        if (heli == null) {
            sagen(p, "Verzeihung, der Hubschrauber ist nicht in der Nähe.");
            return;
        }
        Auftrag alt = auftraege.remove(nr);
        if (alt != null) {
            alt.abbrechen();
        }
        k.getPathfinder().stopPathfinding();
        auftraege.put(nr, new Flug(plugin, k, puppe, p, heli, ziel, name));
        modusSetzen(nr, Modus.ARBEIT);
        sagen(p, plugin.fahrzeuge().sitztDrin(heli, p) ? "Sehr wohl, wir fliegen " + name + "."
                : "Sehr wohl. Ich hole Sie mit dem Hubschrauber ab und fliege Sie " + name + ".");
    }

    /**
     * „Fahr mich zur Villa von Max“ / „fahr mich nach Hause“: James fährt die Limo ans Ende der Zufahrt und den Herrn
     * über die Straßen zwischen den Villen hin (von einer Nachbarvilla zur anderen über die Spawn-Villa).
     */
    private void fahren(Player p, int nr, String satz) {
        Mannequin puppe = puppen.get(nr);
        WanderingTrader k = koerper.get(nr);
        Villen.Villa eigene = plugin.villen().nr(nr);
        var strassen = plugin.strassen();
        if (puppe == null || k == null || eigene == null) {
            sagen(p, "Verzeihung, ich bin gerade nicht erreichbar.");
            return;
        }
        // Ziel: Villa eines Nachbarn oder nach Hause
        Villen.Villa ziel = null;
        for (Villen.Villa v : plugin.villen().alle()) {
            String herrName = v.besitzerName() == null ? "" : v.besitzerName().toLowerCase(Locale.GERMAN);
            if (!herrName.isEmpty() && satz.contains(herrName) && !v.equals(eigene)) {
                ziel = v;
            }
        }
        if (ziel == null && (satz.contains("hause") || satz.contains("heim") || satz.contains("meine villa") || satz.contains("zurück"))) {
            ziel = eigene;
        }
        if (ziel == null) {
            sagen(p, "Wohin darf ich Sie fahren? Nach Hause oder zur Villa eines Nachbarn. Ins Dorf fliege ich Sie gern.");
            return;
        }
        // Wo steht der Herr? An der Spawn-Villa (Anfang aller Straßen) oder am Ende einer Straße?
        int von = -1;
        double beste = 120 * 120;
        for (Villen.Villa v : plugin.villen().alle()) {
            Location vor = strassen.villaZiel(v);
            if (vor.getWorld().equals(p.getWorld()) && vor.distanceSquared(p.getLocation()) < beste) {
                beste = vor.distanceSquared(p.getLocation());
                von = v.nr();
            }
        }
        if (von < 0) {
            sagen(p, "Bitte gehen Sie zuerst zu einer Villa – ich fahre nur auf den Straßen zwischen den Villen.");
            return;
        }
        if (von == ziel.nr()) {
            sagen(p, "Wir sind schon da.");
            return;
        }
        // Weg: zurück zur Spawn-Villa (umgekehrte Straße), dann hin zur Zielvilla
        List<int[]> weg = new java.util.ArrayList<>();
        if (von != 0) {
            var zurueck = strassen.zur(von);
            if (zurueck == null) {
                sagen(p, "Verzeihung, von hier führt noch keine Straße weg.");
                return;
            }
            List<int[]> r = new java.util.ArrayList<>(zurueck.punkte());
            java.util.Collections.reverse(r);
            weg.addAll(r);
        }
        if (ziel.nr() != 0) {
            var hin = strassen.zur(ziel.nr());
            if (hin == null) {
                sagen(p, "Verzeihung, zur " + ziel.name() + " gibt es noch keine Straße. Soll ich Sie fliegen?");
                return;
            }
            weg.addAll(hin.punkte());
        }
        if (weg.size() < 2) {
            sagen(p, "Verzeihung, dorthin finde ich keinen Weg.");
            return;
        }
        // Wagen: die Limo in der Nähe, sonst eine aus der Garage der Villa, sonst eine neue
        int[] a = weg.getFirst();
        Location start = new Location(p.getWorld(), a[0] + 0.5, a[1] + 1, a[2] + 0.5);
        org.bukkit.entity.ArmorStand limo = plugin.fahrzeuge().fahrzeug(de.moinmornhart.stinkendreich.paper.auto.Fahrzeuge.Art.STRETCHLIMO, start, 200);
        if (limo == null) {
            limo = plugin.fahrzeuge().abstellen(de.moinmornhart.stinkendreich.paper.auto.Fahrzeuge.Art.STRETCHLIMO, 0, start, 0);
        }
        Auftrag alt = auftraege.remove(nr);
        if (alt != null) {
            alt.abbrechen();
        }
        k.getPathfinder().stopPathfinding();
        String name = ziel == eigene ? "nach Hause" : "zur " + ziel.name();
        auftraege.put(nr, new Chauffeur(plugin, k, puppe, p, limo, weg, name));
        modusSetzen(nr, Modus.ARBEIT);
        sagen(p, "Sehr wohl, ich fahre Sie " + name + ".");
    }

    /** Gefundene Dörfer je Welt (die Suche kostet den Server spürbar Zeit, Dörfer wandern nicht). */
    private final Map<UUID, Location> doerfer = new HashMap<>();

    /** Landeplatz am nächsten Dorf (bis gut 700 Blöcke weit); null, wenn keins da ist. */
    @SuppressWarnings("deprecation")       // org.bukkit.StructureType.VILLAGE sucht alle Dorfarten auf einmal
    private Location dorf(Location bei) {
        Location mitte = doerfer.get(bei.getWorld().getUID());
        if (mitte == null || mitte.distanceSquared(bei) > 800 * 800) {
            mitte = bei.getWorld().locateNearestStructure(bei, org.bukkit.StructureType.VILLAGE, 44, false);
            if (mitte == null) {
                return null;
            }
            doerfer.put(bei.getWorld().getUID(), mitte);
            plugin.getLogger().info("Flug: Dorf bei " + mitte.getBlockX() + " " + mitte.getBlockZ());
        }
        mitte.getWorld().getChunkAt(mitte).load();
        return Flug.landeplatzBei(new Location(mitte.getWorld(), mitte.getX(), mitte.getWorld().getHighestBlockYAt(mitte) + 1, mitte.getZ()));
    }

    /** „Sortier die Truhen“: alle Truhen und Fässer um den Herrn herum aufräumen. */
    private void sortieren(Player p, int nr) {
        Mannequin puppe = puppen.get(nr);
        WanderingTrader k = koerper.get(nr);
        if (puppe == null || k == null) {
            sagen(p, "Verzeihung, ich bin gerade nicht erreichbar.");
            return;
        }
        Sortierer s = new Sortierer(plugin, k, puppe, p.getUniqueId(), p.getLocation());
        if (s.leer()) {
            sagen(p, "Hier stehen keine Truhen zum Sortieren.");
            return;
        }
        Auftrag alt = auftraege.remove(nr);
        if (alt != null) {
            alt.abbrechen();
        }
        k.getPathfinder().stopPathfinding();
        if (k.getLocation().distanceSquared(p.getLocation()) > 24 * 24 || !k.getWorld().equals(p.getWorld())) {
            k.teleport(hinter(p));                          // von weit her: gleich zur Stelle
        }
        auftraege.put(nr, s);
        modusSetzen(nr, Modus.ARBEIT);
        sagen(p, "Sehr wohl, ich räume die Truhen auf.");
    }

    /**
     * Testhilfe für Bilder: eine freie Stelle 3–5 Blöcke neben James, von der aus man ihn ohne Hindernis sieht (im
     * Tunnel hinter ihm, im Wald zwischen den Bäumen), Blick auf ihn. Null, wenn es keine gibt.
     */
    public Location kamera(int nr) {
        WanderingTrader k = koerper.get(nr);
        if (k == null) {
            return null;
        }
        Location auge = k.getEyeLocation();
        org.bukkit.util.Vector vorn = auge.getDirection().setY(0);
        vorn = vorn.lengthSquared() < 1.0E-6 ? new org.bukkit.util.Vector(0, 0, 1) : vorn.normalize();
        Location beste = null;
        double besteWert = -99;
        for (double r : new double[]{3.5, 4.5, 2.8}) {
            for (int i = 0; i < 16; i++) {
                for (double dy : new double[]{0.4, 1.0, -0.2}) {
                    double w = i * Math.PI / 8;
                    Location c = auge.clone().add(Math.cos(w) * r, dy, Math.sin(w) * r);
                    if (!c.getBlock().isPassable()) {
                        continue;
                    }
                    org.bukkit.util.Vector hin = auge.toVector().subtract(c.toVector());
                    if (c.getWorld().rayTraceBlocks(c, hin, hin.length() - 0.3, org.bukkit.FluidCollisionMode.NEVER, true) != null) {
                        continue;
                    }
                    // Am liebsten schräg hinter ihm (man sieht ihn und, was er bearbeitet), mittlerer Abstand
                    double schraeg = hin.clone().setY(0).normalize().dot(vorn);
                    double wert = 1 - Math.abs(schraeg - 0.5) - Math.abs(r - 3.2) * 0.3;
                    if (wert > besteWert) {
                        besteWert = wert;
                        beste = c.setDirection(hin);
                    }
                }
            }
        }
        return beste;
    }

    /** Für Fehlersuche: wo stehen die Butler und was tun sie? */
    public String uebersicht() {
        StringBuilder sb = new StringBuilder();
        puppen.forEach((nr, p) -> {
            Location l = p.getLocation();
            sb.append("James ").append(nr).append(": ").append(modus(p)).append(String.format(" bei %.1f %.1f %.1f; ", l.getX(), l.getY(), l.getZ()));
        });
        return sb.toString();
    }

    public static void sagen(Player p, String text) {
        p.sendMessage(Component.text("<James> ", NamedTextColor.GOLD).append(Component.text(text, NamedTextColor.WHITE)));
    }

    @EventHandler
    public void beimKlick(PlayerInteractEntityEvent e) {
        Integer nr = e.getRightClicked().getPersistentDataContainer().get(schluesselVilla, PersistentDataType.INTEGER);
        if (nr == null) {
            return;
        }
        e.setCancelled(true);      // kein Handelsfenster des unsichtbaren Körpers
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        menue(e.getPlayer(), nr);
    }

    public void menue(Player p, int nr) {
        if (!p.getUniqueId().equals(herr(nr))) {
            sagen(p, "Verzeihung, ich stehe nicht in Ihren Diensten.");
            return;
        }
        Dialoge.zeigen(p, "James", "Womit kann ich dienen?", List.of(), List.of(
                Dialoge.Knopf.von("Folge mir", (s, a) -> befehl(s, "komm")),
                Dialoge.Knopf.von("Warte hier", (s, a) -> befehl(s, "warte")),
                Dialoge.Knopf.von("Geh nach Hause", (s, a) -> befehl(s, "nach hause")),
                Dialoge.Knopf.von("16 Eisen holen", (s, a) -> befehl(s, "hol mir 16 eisen")),
                Dialoge.Knopf.von("16 Kohle holen", (s, a) -> befehl(s, "hol mir 16 kohle")),
                Dialoge.Knopf.von("8 Diamanten holen", (s, a) -> befehl(s, "hol mir 8 diamanten")),
                Dialoge.Knopf.von("16 Holz holen", (s, a) -> befehl(s, "hol mir 16 holz")),
                Dialoge.Knopf.von("Auf die Jagd gehen", (s, a) -> befehl(s, "jag mir 8 fleisch")),
                Dialoge.Knopf.von("Truhen hier sortieren", (s, a) -> befehl(s, "sortier die truhen")),
                Dialoge.Knopf.von("Limo: nach Hause", (s, a) -> befehl(s, "fahr mich nach hause")),
                Dialoge.Knopf.von("Hubschrauber: nach Hause", (s, a) -> befehl(s, "flieg mich nach hause")),
                Dialoge.Knopf.von("Hubschrauber: ins Dorf", (s, a) -> befehl(s, "flieg mich ins dorf")),
                Dialoge.Knopf.von("Was machst du gerade?", (s, a) -> befehl(s, "was machst du"))));
    }

    @EventHandler
    public void beimSchaden(EntityDamageEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(schluesselVilla)
                && e.getCause() != EntityDamageEvent.DamageCause.KILL) {
            e.setCancelled(true);
        }
    }
}
