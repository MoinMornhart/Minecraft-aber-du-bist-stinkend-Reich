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
    public enum Modus { FOLGEN, WARTEN, HEIM }

    private final StinkendReichPaper plugin;
    private final NamespacedKey schluesselVilla, schluesselRolle, schluesselModus, schluesselHerr;
    /** Villa-Nummer → Puppe und Körper (nur geladene). */
    private final Map<Integer, Mannequin> puppen = new HashMap<>();
    private final Map<Integer, WanderingTrader> koerper = new HashMap<>();
    private final Map<Integer, Location> letzteStelle = new HashMap<>();
    private final Map<Integer, Integer> stillstand = new HashMap<>();

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
            if (Bukkit.getCurrentTick() % 10 == 0) {
                ziehen(nr, p, k);
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
        if (nr < 0) {
            sagen(p, "Verzeihung, ich stehe nicht in Ihren Diensten.");
            return;
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
            sagen(p, "Verzeihung, das habe ich nicht verstanden. Ich kann Ihnen folgen, warten oder nach Hause gehen.");
        }
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
                Dialoge.Knopf.von("Geh nach Hause", (s, a) -> befehl(s, "nach hause"))));
    }

    @EventHandler
    public void beimSchaden(EntityDamageEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(schluesselVilla)
                && e.getCause() != EntityDamageEvent.DamageCause.KILL) {
            e.setCancelled(true);
        }
    }
}
