package de.moinmornhart.stinkendreich.paper.block;

import de.moinmornhart.stinkendreich.paper.Ressourcenpaket;
import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Die Blöcke der Mod auf einem Paper-Server. Jeder „virtuelle Block“ besteht aus
 * <ul>
 *   <li>einem Träger aus dem Spiel an seiner Stelle: Barriere, wenn man anstößt, Licht, wenn er leuchtet, sonst
 *       Strukturleere (bei Deckenblöcken direkt das Vorbild, dann ohne Figur),</li>
 *   <li>einer Anzeige-Figur (ItemDisplay) mit dem echten Modell aus dem Ressourcenpaket; sie trägt auch den Zustand
 *       des Blocks (z. B. „Tür offen“), so bleibt er mit der Welt gespeichert,</li>
 *   <li>bei Blöcken ohne Kollision einem Klickfeld (Interaction), damit man sie trotzdem benutzen kann.</li>
 * </ul>
 */
public final class VirtuelleBloecke implements Listener {
    private final StinkendReichPaper plugin;
    private final Blockdaten daten;
    final NamespacedKey schluesselZustand;
    final NamespacedKey schluesselKlick;
    final NamespacedKey schluesselWerte;
    /** Welt → Blockposition → Anzeige-Figur (nur geladene). */
    private final Map<UUID, Map<Long, ItemDisplay>> figuren = new HashMap<>();
    private final Map<UUID, Map<Long, Interaction>> klickfelder = new HashMap<>();
    /** Welt → Blockposition → Kurzname des Blocks (für Steuerungen, die alle Blöcke einer Art brauchen). */
    private final Map<UUID, Map<Long, String>> namen = new HashMap<>();
    /** Gesperrte Bereiche (Villa fährt gerade, siehe {@link Inkognito}): dort gibt es keine Zustände und nichts wird gesetzt. */
    private final java.util.List<Map.Entry<UUID, org.bukkit.util.BoundingBox>> gesperrt = new java.util.ArrayList<>();

    public VirtuelleBloecke(StinkendReichPaper plugin, Blockdaten daten) {
        this.plugin = plugin;
        this.daten = daten;
        this.schluesselZustand = new NamespacedKey(plugin, "zustand");
        this.schluesselKlick = new NamespacedKey(plugin, "klick");
        this.schluesselWerte = new NamespacedKey(plugin, "werte");
        // Schon geladene Figuren aufnehmen (Plugin neu geladen)
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                aufnehmen(e);
            }
        }
    }

    public Blockdaten daten() {
        return daten;
    }

    public static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | ((long) y & 0xFFF);
    }

    private static long pack(Block b) {
        return pack(b.getX(), b.getY(), b.getZ());
    }

    // ------------------------------------------------------------------
    // Lesen
    // ------------------------------------------------------------------

    /** Zustand des virtuellen Blocks an dieser Stelle oder null. */
    public Zustand zustand(Block b) {
        if (gesperrt(b)) {
            return null;
        }
        ItemDisplay figur = figur(b);
        if (figur == null) {
            return null;
        }
        String text = figur.getPersistentDataContainer().get(schluesselZustand, PersistentDataType.STRING);
        return text == null ? null : Zustand.lesen(text);
    }

    public ItemDisplay figur(Block b) {
        Map<Long, ItemDisplay> m = figuren.get(b.getWorld().getUID());
        ItemDisplay f = m == null ? null : m.get(pack(b));
        return f != null && f.isValid() ? f : null;
    }

    /** Position des Blocks, zu dem ein Klickfeld gehört, oder null. */
    public Block blockVonKlickfeld(Entity e) {
        if (!(e instanceof Interaction)) {
            return null;
        }
        long[] pos = e.getPersistentDataContainer().get(schluesselKlick, PersistentDataType.LONG_ARRAY);
        return pos == null || pos.length < 3 ? null : e.getWorld().getBlockAt((int) pos[0], (int) pos[1], (int) pos[2]);
    }

    // ------------------------------------------------------------------
    // Setzen
    // ------------------------------------------------------------------

    /** Setzt einen Block der Mod (Träger, Figur, Klickfeld). */
    public void setzen(Block b, Zustand zustand) {
        if (gesperrt(b)) {
            return;
        }
        Blockdaten.Darstellung d = daten.darstellung(zustand);
        if (d.vorbild() != null) {
            entfernenFiguren(b);
            b.setBlockData(Bukkit.createBlockData(d.vorbild()), false);
            return;
        }
        b.setBlockData(traeger(d), false);
        lichtDarueber(b, d);
        ItemDisplay figur = figur(b);
        if (figur == null) {
            Location mitte = b.getLocation().add(0.5, 0.5, 0.5);
            figur = b.getWorld().spawn(mitte, ItemDisplay.class, f -> {
                f.setPersistent(true);
                f.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                f.setViewRange(1.0F);
            });
            figuren.computeIfAbsent(b.getWorld().getUID(), w -> new HashMap<>()).put(pack(b), figur);
        }
        figur.getPersistentDataContainer().set(schluesselZustand, PersistentDataType.STRING, zustand.toString());
        namen.computeIfAbsent(b.getWorld().getUID(), w -> new HashMap<>()).put(pack(b), zustand.name());
        anzeigen(figur, d);
        klickfeld(b, d);
    }

    /**
     * Träger aus dem Spiel: Leuchtende Blöcke ohne großen Körper (Lampen, Kronleuchter) werden ein Lichtblock (man
     * läuft hindurch, dafür leuchten sie wirklich), feste Blöcke eine Barriere, alles andere Strukturleere.
     */
    private static BlockData traeger(Blockdaten.Darstellung d) {
        if (d.licht() > 0 && !grosserKoerper(d)) {
            return lichtblock(d.licht());
        }
        if (d.fest()) {
            return Material.BARRIER.createBlockData();
        }
        return Material.STRUCTURE_VOID.createBlockData();
    }

    private static BlockData lichtblock(int staerke) {
        Light licht = (Light) Material.LIGHT.createBlockData();
        licht.setLevel(Math.clamp(staerke, 1, 15));
        return licht;
    }

    /** Hat der Block einen Körper, auf dem man steht (z. B. leuchtende Tanzfläche)? Dann bleibt er eine Barriere. */
    private static boolean grosserKoerper(Blockdaten.Darstellung d) {
        for (Blockdaten.Kasten k : d.kollision()) {
            if (k.y1() - k.y0() >= 8 && k.x1() - k.x0() >= 12 && k.z1() - k.z0() >= 12) {
                return true;
            }
        }
        return false;
    }

    /** Feste leuchtende Blöcke geben ihr Licht über den Block darüber ab (falls dort Luft ist). */
    private void lichtDarueber(Block b, Blockdaten.Darstellung d) {
        Block oben = b.getRelative(org.bukkit.block.BlockFace.UP);
        boolean soll = d.licht() > 0 && d.fest() && grosserKoerper(d);
        if (soll && oben.getType().isAir()) {
            oben.setBlockData(lichtblock(d.licht()), false);
        } else if (!soll && oben.getType() == Material.LIGHT && figur(oben) == null) {
            oben.setType(Material.AIR, false);
        }
    }

    private void anzeigen(ItemDisplay figur, Blockdaten.Darstellung d) {
        if (d.modell() == null) {
            figur.setItemStack(null);
            return;
        }
        ItemStack bild = ItemStack.of(Material.PAPER);
        bild.setData(DataComponentTypes.ITEM_MODEL, Key.key(Ressourcenpaket.anzeigeModell(d.modell())));
        figur.setItemStack(bild);
        // Drehung wie im Blockzustand: erst um x, dann um y (beide im Uhrzeigersinn von oben gesehen)
        Quaternionf drehung = new Quaternionf().rotateY((float) Math.toRadians(-d.y())).rotateX((float) Math.toRadians(-d.x()));
        figur.setTransformation(new Transformation(new Vector3f(), drehung, new Vector3f(1, 1, 1), new Quaternionf()));
    }

    /** Klickfeld für Blöcke ohne Kollision (Barrieren kann man selbst anklicken). */
    private void klickfeld(Block b, Blockdaten.Darstellung d) {
        Map<Long, Interaction> m = klickfelder.computeIfAbsent(b.getWorld().getUID(), w -> new HashMap<>());
        Interaction feld = m.get(pack(b));
        if (d.fest() || d.umriss().isEmpty()) {
            if (feld != null) {
                feld.remove();
                m.remove(pack(b));
            }
            return;
        }
        double x0 = 16, y0 = 16, z0 = 16, x1 = 0, y1 = 0, z1 = 0;
        for (Blockdaten.Kasten k : d.umriss()) {
            x0 = Math.min(x0, k.x0()); y0 = Math.min(y0, k.y0()); z0 = Math.min(z0, k.z0());
            x1 = Math.max(x1, k.x1()); y1 = Math.max(y1, k.y1()); z1 = Math.max(z1, k.z1());
        }
        Location fuss = b.getLocation().add((x0 + x1) / 32.0, y0 / 16.0, (z0 + z1) / 32.0);
        float breite = (float) (Math.max(x1 - x0, z1 - z0) / 16.0), hoehe = (float) ((y1 - y0) / 16.0);
        if (feld == null || !feld.isValid()) {
            feld = b.getWorld().spawn(fuss, Interaction.class, f -> {
                f.setPersistent(true);
                f.getPersistentDataContainer().set(schluesselKlick, PersistentDataType.LONG_ARRAY,
                        new long[]{b.getX(), b.getY(), b.getZ()});
            });
            m.put(pack(b), feld);
        } else {
            feld.teleport(fuss);
        }
        feld.setInteractionWidth(Math.max(0.1F, breite));
        feld.setInteractionHeight(Math.max(0.1F, hoehe));
        feld.setResponsive(true);
    }

    /** Entfernt einen Block der Mod ganz (Figur, Klickfeld) und setzt Luft. */
    public void entfernen(Block b) {
        if (gesperrt(b)) {
            return;
        }
        entfernenFiguren(b);
        b.setType(Material.AIR, false);
    }

    private void entfernenFiguren(Block b) {
        ItemDisplay f = figur(b);
        if (f != null) {
            f.remove();
        }
        Map<Long, ItemDisplay> m = figuren.get(b.getWorld().getUID());
        if (m != null) {
            m.remove(pack(b));
        }
        Map<Long, String> n = namen.get(b.getWorld().getUID());
        if (n != null) {
            n.remove(pack(b));
        }
        Map<Long, Interaction> k = klickfelder.get(b.getWorld().getUID());
        if (k != null) {
            Interaction feld = k.remove(pack(b));
            if (feld != null) {
                feld.remove();
            }
        }
    }

    // ------------------------------------------------------------------
    // Werte der Mechanik (wie die Block-Entity „stinkendreich:mechanik“ in der Mod: Ziel, Radius, Name …)
    // ------------------------------------------------------------------

    /** Übernimmt die Block-Daten aus dem Bauplan ({@code {"werte": {...}}} unter „stinkendreich“). */
    public void datenSetzen(Block b, Map<String, Object> daten) {
        ItemDisplay figur = figur(b);
        Object json = daten.get("stinkendreich");
        if (figur != null && json instanceof String text) {
            try {
                com.google.gson.JsonObject j = com.google.gson.JsonParser.parseString(text).getAsJsonObject();
                if (j.has("werte")) {
                    figur.getPersistentDataContainer().set(schluesselWerte, PersistentDataType.STRING, j.get("werte").toString());
                }
            } catch (Exception ignoriert) {
                // kaputte Daten: Block ohne Werte
            }
        }
    }

    /** Werte eines Blocks (leer, wenn er keine hat). */
    public Map<String, String> werte(Block b) {
        Map<String, String> m = new HashMap<>();
        ItemDisplay figur = figur(b);
        String text = figur == null ? null : figur.getPersistentDataContainer().get(schluesselWerte, PersistentDataType.STRING);
        if (text != null) {
            com.google.gson.JsonParser.parseString(text).getAsJsonObject().entrySet()
                    .forEach(e -> m.put(e.getKey(), e.getValue().getAsString()));
        }
        return m;
    }

    public void wertSetzen(Block b, String name, String wert) {
        ItemDisplay figur = figur(b);
        if (figur == null) {
            return;
        }
        Map<String, String> m = werte(b);
        m.put(name, wert);
        com.google.gson.JsonObject j = new com.google.gson.JsonObject();
        m.forEach(j::addProperty);
        figur.getPersistentDataContainer().set(schluesselWerte, PersistentDataType.STRING, j.toString());
    }

    // ------------------------------------------------------------------
    // Verzeichnis der geladenen Figuren
    // ------------------------------------------------------------------

    private void aufnehmen(Entity e) {
        if (e instanceof ItemDisplay d && d.getPersistentDataContainer().has(schluesselZustand)) {
            Location l = d.getLocation();
            long p = pack(l.getBlockX(), l.getBlockY(), l.getBlockZ());
            figuren.computeIfAbsent(d.getWorld().getUID(), w -> new HashMap<>()).put(p, d);
            String text = d.getPersistentDataContainer().get(schluesselZustand, PersistentDataType.STRING);
            namen.computeIfAbsent(d.getWorld().getUID(), w -> new HashMap<>()).put(p, Zustand.lesen(text).name());
        } else if (e instanceof Interaction i) {
            long[] pos = i.getPersistentDataContainer().get(schluesselKlick, PersistentDataType.LONG_ARRAY);
            if (pos != null && pos.length == 3) {
                klickfelder.computeIfAbsent(i.getWorld().getUID(), w -> new HashMap<>())
                        .put(pack((int) pos[0], (int) pos[1], (int) pos[2]), i);
            }
        }
    }

    @EventHandler
    public void beimLaden(EntitiesLoadEvent e) {
        e.getEntities().forEach(this::aufnehmen);
    }

    @EventHandler
    public void beimEntladen(EntitiesUnloadEvent e) {
        UUID welt = e.getWorld().getUID();
        for (Entity en : e.getEntities()) {
            Location l = en.getLocation();
            if (en instanceof ItemDisplay && figuren.containsKey(welt)) {
                long p = pack(l.getBlockX(), l.getBlockY(), l.getBlockZ());
                if (figuren.get(welt).remove(p, en) && namen.containsKey(welt)) {
                    namen.get(welt).remove(p);
                }
            } else if (en instanceof Interaction && klickfelder.containsKey(welt)) {
                klickfelder.get(welt).values().remove(en);
            }
        }
    }

    /** Wer im Kreativmodus eine Barriere eines Mod-Blocks abbaut, entfernt auch Figur und Klickfeld. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beimAbbauen(BlockBreakEvent e) {
        if (figur(e.getBlock()) != null) {
            entfernenFiguren(e.getBlock());
        }
    }

    /** Alle geladenen Blöcke der Mod mit diesem Kurznamen (z. B. „torsteuerung“). */
    public java.util.List<Block> alle(String name) {
        java.util.List<Block> liste = new java.util.ArrayList<>();
        for (var welt : namen.entrySet()) {
            World w = Bukkit.getWorld(welt.getKey());
            if (w == null) {
                continue;
            }
            for (var e : welt.getValue().entrySet()) {
                if (e.getValue().equals(name)) {
                    long p = e.getKey();
                    int x = (int) (p >> 38), z = (int) (p << 26 >> 38), y = (int) (p << 52 >> 52);
                    liste.add(w.getBlockAt(x, y, z));
                }
            }
        }
        return liste;
    }

    // ------------------------------------------------------------------
    // Sperren und Verschieben (Inkognito)
    // ------------------------------------------------------------------

    private boolean gesperrt(Block b) {
        if (gesperrt.isEmpty()) {
            return false;
        }
        for (Map.Entry<UUID, org.bukkit.util.BoundingBox> e : gesperrt) {
            if (e.getKey().equals(b.getWorld().getUID()) && e.getValue().contains(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5)) {
                return true;
            }
        }
        return false;
    }

    /** Sperrt einen Bereich (die Mechanik dort ruht, bis {@link #entsperren} kommt). */
    public void sperren(World w, org.bukkit.util.BoundingBox bereich) {
        gesperrt.add(Map.entry(w.getUID(), bereich.clone()));
    }

    public void entsperren(World w, org.bukkit.util.BoundingBox bereich) {
        gesperrt.removeIf(e -> e.getKey().equals(w.getUID()) && e.getValue().equals(bereich));
    }

    /**
     * Nach einer Fahrt um {@code dy}: Klickfelder kennen ihren neuen Block, das Verzeichnis wird für den Bereich (alte
     * und neue Lage) neu aufgebaut.
     */
    public void verschoben(World w, org.bukkit.util.BoundingBox alt, int dy) {
        org.bukkit.util.BoundingBox neu = alt.clone().shift(0, dy, 0);
        org.bukkit.util.BoundingBox beide = alt.clone().union(neu);
        UUID id = w.getUID();
        for (Map<UUID, ? extends Map<Long, ?>> m : java.util.List.of(figuren, klickfelder, namen)) {
            Map<Long, ?> karte = m.get(id);
            if (karte != null) {
                karte.keySet().removeIf(p -> {
                    int x = (int) (p >> 38), z = (int) (p << 26 >> 38), y = (int) (p << 52 >> 52);
                    return beide.contains(x + 0.5, y + 0.5, z + 0.5);
                });
            }
        }
        for (Entity e : w.getNearbyEntities(neu)) {
            if (e instanceof Interaction i) {
                long[] pos = i.getPersistentDataContainer().get(schluesselKlick, PersistentDataType.LONG_ARRAY);
                if (pos != null && pos.length == 3) {
                    i.getPersistentDataContainer().set(schluesselKlick, PersistentDataType.LONG_ARRAY, new long[]{pos[0], pos[1] + dy, pos[2]});
                }
            }
            aufnehmen(e);
        }
    }

    /** Für Fehlersuche: Anzahl der bekannten Figuren. */
    public int anzahl() {
        return figuren.values().stream().mapToInt(Map::size).sum();
    }
}
