package de.moinmornhart.stinkendreich.paper.auto;

import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Autos und der Hubschrauber auf dem Paper-Server (wie {@code AutoEntity} in der Mod, gefahren wie in der
 * Server-Version für Spieler ohne Mod: der Server liest W/A/S/D und bewegt den Wagen).
 *
 * <p>Ein Fahrzeug besteht aus einem unsichtbaren Körper (Rüstungsständer mit Schwerkraft und Stufenhöhe, er stößt an
 * Wände wie ein echter Wagen), der Anzeige-Figur mit dem Modell und der Lackfarbe aus dem Ressourcenpaket, einem
 * Klickfeld zum Einsteigen und je Insasse einem unsichtbaren Sitz genau an seinem Platz im Wagen. Fahrdaten (Typ,
 * Farbe, Tempo) stehen im Körper.</p>
 */
public final class Fahrzeuge implements Listener {
    /** Ein Fahrzeugtyp der Mod: Name, Tempo-, Beschleunigungsfaktor, Maßstab und Sitze (seitlich, Höhe, vorn). */
    public enum Art {
        BONZENAUTO("bonzenauto", 1.0, 1.0, 1.5F, new double[][]{{0.42, 0.55, 0.0}, {-0.42, 0.55, 0.0}, {0.42, 0.55, -1.0}, {-0.42, 0.55, -1.0}}),
        GELAENDEWAGEN("gelaendewagen", 0.8, 0.85, 1.15F, new double[][]{{0.47, 0.55, 0.41}, {-0.47, 0.55, 0.41}, {0.47, 0.55, -0.84}, {-0.47, 0.55, -0.84}}),
        SUPERSPORTWAGEN("supersportwagen", 1.4, 1.6, 1.5F, new double[][]{{0.41, 0.42, 0.16}, {-0.41, 0.42, 0.16}}),
        STRETCHLIMO("stretchlimo", 0.75, 0.7, 1.0F, new double[][]{{0.41, 0.45, 2.16}, {-0.41, 0.45, 2.16}, {-0.59, 0.5, 0.5}, {0.59, 0.5, 0.5}, {-0.59, 0.5, -1.0}, {0.59, 0.5, -1.0}}),
        HUBSCHRAUBER("hubschrauber", 1.0, 1.0, 1.4F, new double[][]{{0.375, 0.55, 0.557}, {-0.375, 0.55, 0.557}, {0.375, 0.55, -0.221}, {-0.375, 0.55, -0.221}});

        public final String name;
        final double tempo, beschleunigung;
        final float massstab;
        /** Sitze in Blöcken (mit Maßstab). */
        final double[][] sitze;

        Art(String name, double tempo, double beschleunigung, float massstab, double[][] sitze) {
            this.name = name;
            this.tempo = tempo;
            this.beschleunigung = beschleunigung;
            this.massstab = massstab;
            this.sitze = new double[sitze.length][];
            for (int i = 0; i < sitze.length; i++) {
                this.sitze[i] = new double[]{sitze[i][0] * massstab, sitze[i][1] * massstab, sitze[i][2] * massstab};
            }
        }

        boolean fliegt() {
            return this == HUBSCHRAUBER;
        }

        static Art von(String name) {
            for (Art a : values()) {
                if (a.name.equals(name)) {
                    return a;
                }
            }
            return BONZENAUTO;
        }
    }

    /** Lackfarben wie in der Mod (AutoEntity.FARBEN). */
    public static final String[] FARBEN = {"schwarz", "weiss", "rot", "gelb", "gold", "blau", "gruen", "pink"};
    /** Höchsttempo des Bonzenautos in km/h (Mod-Config autoHoechstTempo). */
    private static final double HOECHST_KMH = 110.0;

    /** Laufende Daten eines geladenen Fahrzeugs. */
    private static final class Wagen {
        final ArmorStand koerper;
        final Art art;
        ItemDisplay modell;
        Interaction klick;
        /** Sitzplatz → Sitz-Figur (nur belegte). */
        final Map<Integer, ItemDisplay> sitze = new HashMap<>();
        double tempo, lenkung;
        Vector schub = new Vector();
        int crashSperre;

        Wagen(ArmorStand koerper, Art art) {
            this.koerper = koerper;
            this.art = art;
        }
    }

    private final StinkendReichPaper plugin;
    private final NamespacedKey schluesselArt, schluesselFarbe, schluesselRolle, schluesselWagen, schluesselPlatz;
    /** Körper-UUID → Wagen. */
    private final Map<UUID, Wagen> wagen = new HashMap<>();

    public Fahrzeuge(StinkendReichPaper plugin) {
        this.plugin = plugin;
        schluesselArt = new NamespacedKey(plugin, "fahrzeug");
        schluesselFarbe = new NamespacedKey(plugin, "farbe");
        schluesselRolle = new NamespacedKey(plugin, "fahrzeug_teil");
        schluesselWagen = new NamespacedKey(plugin, "fahrzeug_von");
        schluesselPlatz = new NamespacedKey(plugin, "platz");
        for (var w : Bukkit.getWorlds()) {
            w.getEntities().forEach(this::aufnehmen);
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    // ------------------------------------------------------------------
    // Aufstellen
    // ------------------------------------------------------------------

    /** Stellt ein Fahrzeug ab (Fuhrpark, Landeplatz). */
    public void abstellen(Art art, int farbe, Location ort, float blick) {
        Location l = ort.clone();
        l.setYaw(blick);
        l.setPitch(0);
        ArmorStand k = l.getWorld().spawn(l, ArmorStand.class, a -> {
            a.setPersistent(true);
            a.setInvisible(true);
            a.setSilent(true);
            a.setInvulnerable(true);
            a.setBasePlate(false);
            a.setGravity(!art.fliegt());
            a.setDisabledSlots(EquipmentSlot.values());
            a.getPersistentDataContainer().set(schluesselArt, PersistentDataType.STRING, art.name);
            a.getPersistentDataContainer().set(schluesselFarbe, PersistentDataType.INTEGER, Math.floorMod(farbe, FARBEN.length));
            if (a.getAttribute(Attribute.STEP_HEIGHT) != null) {
                a.getAttribute(Attribute.STEP_HEIGHT).setBaseValue(1.05);
            }
        });
        aufnehmen(k);
    }

    private void aufnehmen(Entity e) {
        if (e instanceof ArmorStand a && a.getPersistentDataContainer().has(schluesselArt)) {
            Art art = Art.von(a.getPersistentDataContainer().get(schluesselArt, PersistentDataType.STRING));
            Wagen w = wagen.computeIfAbsent(a.getUniqueId(), u -> new Wagen(a, art));
            teileFinden(w);
        } else if (e.getPersistentDataContainer().has(schluesselRolle)) {
            // Teile ohne Körper (z. B. nach einem Absturz) werden beim nächsten Tick neu angelegt; alte weg
            String von = e.getPersistentDataContainer().get(schluesselWagen, PersistentDataType.STRING);
            Wagen w = von == null ? null : wagen.get(UUID.fromString(von));
            if (w == null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (e.isValid() && !wagen.containsKey(UUID.fromString(von == null ? new UUID(0, 0).toString() : von))) {
                        e.remove();
                    }
                });
            } else {
                teileFinden(w);
            }
        }
    }

    /** Sucht Modell und Klickfeld eines Wagens in der Nähe (nach dem Laden eines Chunks) oder legt sie an. */
    private void teileFinden(Wagen w) {
        String id = w.koerper.getUniqueId().toString();
        for (Entity e : w.koerper.getNearbyEntities(6, 4, 6)) {
            String von = e.getPersistentDataContainer().get(schluesselWagen, PersistentDataType.STRING);
            if (!id.equals(von)) {
                continue;
            }
            String rolle = e.getPersistentDataContainer().get(schluesselRolle, PersistentDataType.STRING);
            if ("modell".equals(rolle) && e instanceof ItemDisplay d) {
                w.modell = d;
            } else if ("klick".equals(rolle) && e instanceof Interaction i) {
                w.klick = i;
            } else if ("sitz".equals(rolle) && e.getPassengers().isEmpty()) {
                e.remove();     // leerer Sitz von früher
            }
        }
    }

    private <T extends Entity> T teil(Wagen w, Class<T> typ, String rolle, java.util.function.Consumer<T> einrichten) {
        return w.koerper.getWorld().spawn(w.koerper.getLocation(), typ, e -> {
            e.setPersistent(!rolle.equals("sitz"));
            e.getPersistentDataContainer().set(schluesselRolle, PersistentDataType.STRING, rolle);
            e.getPersistentDataContainer().set(schluesselWagen, PersistentDataType.STRING, w.koerper.getUniqueId().toString());
            einrichten.accept(e);
        });
    }

    private ItemDisplay modellAnlegen(Wagen w) {
        int farbe = w.koerper.getPersistentDataContainer().getOrDefault(schluesselFarbe, PersistentDataType.INTEGER, 0);
        return teil(w, ItemDisplay.class, "modell", d -> {
            ItemStack bild = ItemStack.of(Material.PAPER);
            bild.setData(DataComponentTypes.ITEM_MODEL,
                    Key.key("stinkendreich", "polymer/fahrzeug/" + w.art.name + "_" + FARBEN[Math.floorMod(farbe, FARBEN.length)]));
            d.setItemStack(bild);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            // Wie FahrzeugAnzeige der Server-Version: Modellraum (y nach unten) auf den Kopf, Maßstab 4 × massstab
            float m = w.art.massstab * 4.0F;
            d.setTransformation(new Transformation(new Vector3f(0, 1.5F * w.art.massstab, 0),
                    new Quaternionf().rotateZ((float) Math.PI), new Vector3f(m, m, m), new Quaternionf()));
            d.setTeleportDuration(2);
            d.setViewRange(2.0F);
        });
    }

    // ------------------------------------------------------------------
    // Jeden Tick
    // ------------------------------------------------------------------

    private void tick() {
        List<UUID> weg = new ArrayList<>();
        for (Wagen w : wagen.values()) {
            if (!w.koerper.isValid()) {
                if (w.koerper.isDead()) {
                    weg.add(w.koerper.getUniqueId());
                    teileEntfernen(w);
                }
                continue;
            }
            if (w.modell == null || !w.modell.isValid()) {
                w.modell = modellAnlegen(w);
            }
            if (w.klick == null || !w.klick.isValid()) {
                float breite = 1.6F * w.art.massstab + (w.art == Art.STRETCHLIMO ? 1.0F : 0.0F);
                w.klick = teil(w, Interaction.class, "klick", i -> {
                    i.setInteractionWidth(breite);
                    i.setInteractionHeight(1.4F * w.art.massstab);
                    i.setResponsive(true);
                });
            }
            Player fahrer = fahrer(w);
            if (fahrer != null) {
                fahren(w, fahrer);
            } else if (!w.art.fliegt() && Math.abs(w.tempo) > 0.001) {
                // Ausrollen ohne Fahrer
                w.tempo *= 0.9;
                Vector r = richtung(w.koerper.getYaw()).multiply(w.tempo);
                w.koerper.setVelocity(new Vector(r.getX(), w.koerper.getVelocity().getY(), r.getZ()));
            }
            nachziehen(w);
        }
        weg.forEach(wagen::remove);
    }

    /** Modell, Klickfeld und Sitze an den Körper hängen. */
    private void nachziehen(Wagen w) {
        Location k = w.koerper.getLocation();
        float gier = k.getYaw();
        Location m = k.clone();
        m.setPitch(0);
        if (w.modell.getLocation().distanceSquared(m) > 1.0E-4 || Math.abs(w.modell.getYaw() - gier) > 0.01) {
            w.modell.teleport(m);
        }
        Location kl = k.clone();
        if (w.klick.getLocation().distanceSquared(kl) > 1.0E-4) {
            w.klick.teleport(kl);
        }
        for (Map.Entry<Integer, ItemDisplay> e : w.sitze.entrySet()) {
            ItemDisplay sitz = e.getValue();
            if (!sitz.isValid()) {
                continue;
            }
            Location ziel = sitzOrt(w, e.getKey());
            sitz.teleport(ziel, io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
        }
        w.sitze.values().removeIf(s -> !s.isValid() || s.getPassengers().isEmpty());
    }

    /** Weltposition eines Sitzes (wie getPassengerAttachmentPoint in der Mod). */
    private static Location sitzOrt(Wagen w, int platz) {
        Location k = w.koerper.getLocation();
        double[] s = w.art.sitze[Math.min(platz, w.art.sitze.length - 1)];
        double winkel = Math.toRadians(-k.getYaw());
        // Vec3.yRot(winkel): x' = x cos + z sin, z' = z cos − x sin
        double x = s[0] * Math.cos(winkel) + s[2] * Math.sin(winkel);
        double z = s[2] * Math.cos(winkel) - s[0] * Math.sin(winkel);
        Location l = k.clone().add(x, s[1], z);
        l.setPitch(0);
        return l;
    }

    private static Vector richtung(float gier) {
        double r = Math.toRadians(gier);
        return new Vector(-Math.sin(r), 0, Math.cos(r));
    }

    private Player fahrer(Wagen w) {
        ItemDisplay sitz = w.sitze.get(0);
        return sitz != null && sitz.isValid() && sitz.getPassengers().stream().findFirst().orElse(null) instanceof Player p ? p : null;
    }

    /** Fahrphysik wie AutoEntity.travel in der Mod (Gas, Bremse, Rückwärtsgang, Lenkung, Handbremse). */
    private void fahren(Wagen w, Player p) {
        Input tasten = p.getCurrentInput();
        double gas = (tasten.isForward() ? 1 : 0) - (tasten.isBackward() ? 1 : 0);
        double lenk = (tasten.isLeft() ? 1 : 0) - (tasten.isRight() ? 1 : 0);
        double max = Math.max(0.3, HOECHST_KMH / 72.0 * w.art.tempo);
        if (w.art.fliegt()) {
            fliegen(w, tasten, gas, lenk, max);
            return;
        }
        boolean handbremse = tasten.isJump();
        double kraft = w.art.beschleunigung;
        if (gas > 0) {
            if (w.tempo < 0) {
                w.tempo = Math.min(0, w.tempo + 0.06);
            } else {
                double kurve = 1.0 - (w.tempo / max) * (w.tempo / max);
                w.tempo = Math.min(max, w.tempo + (0.004 + 0.016 * kurve) * kraft);
            }
        } else if (gas < 0) {
            w.tempo = w.tempo > 0 ? Math.max(0, w.tempo - 0.06) : Math.max(-0.35, w.tempo - 0.012);
        } else {
            w.tempo *= 0.985;
            w.tempo -= Math.signum(w.tempo) * Math.min(Math.abs(w.tempo), 0.002);
        }
        if (handbremse) {
            w.tempo *= 0.975;
        }
        if (w.koerper.isInWater()) {
            w.tempo *= 0.85;
        }
        w.lenkung += (lenk - w.lenkung) * 0.3;
        double betrag = Math.abs(w.tempo);
        double wirkung = Math.min(1.0, betrag / 0.25) * (1.0 - 0.5 * Math.min(1.0, betrag / max));
        float drehung = (float) (w.lenkung * 4.2 * wirkung * Math.signum(w.tempo) * (handbremse ? 1.6 : 1.0));
        float gier = w.koerper.getYaw() - drehung;
        w.koerper.setRotation(gier, 0);
        Vector soll = richtung(gier).multiply(w.tempo);
        boolean driftet = handbremse && betrag > 0.35 && Math.abs(w.lenkung) > 0.2;
        double haftung = driftet ? 0.07 : (w.koerper.isOnGround() ? 0.55 : 0.05);
        w.schub.add(soll.clone().subtract(w.schub).multiply(haftung));
        // Aufprall: kommt der Körper kaum voran, obwohl er soll, bremst er stark
        Vector bewegt = w.koerper.getVelocity();
        double gewollt = Math.hypot(w.schub.getX(), w.schub.getZ());
        if (gewollt > 0.2 && Math.hypot(bewegt.getX(), bewegt.getZ()) < 0.25 * gewollt && w.koerper.getTicksLived() > 5) {
            if (betrag > 0.5 && w.crashSperre <= 0) {
                w.koerper.getWorld().playSound(w.koerper.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.4F, 1.6F);
                w.crashSperre = 20;
            }
            w.tempo *= 0.35;
            w.schub.multiply(0.35);
        }
        if (w.crashSperre > 0) {
            w.crashSperre--;
        }
        w.koerper.setVelocity(new Vector(w.schub.getX(), bewegt.getY(), w.schub.getZ()));
        if (driftet && w.koerper.getTicksLived() % 2 == 0) {
            w.koerper.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, w.koerper.getLocation().add(0, 0.2, 0), 2, 0.6, 0.05, 0.6, 0.01);
        }
    }

    /** Hubschrauber: W/S vor und zurück, A/D drehen, Leertaste steigen, Sprinttaste sinken. */
    private void fliegen(Wagen w, Input tasten, double gas, double lenk, double max) {
        w.tempo += gas * 0.02;
        w.tempo = Math.clamp(w.tempo * 0.98, -0.4, max);
        float gier = w.koerper.getYaw() - (float) (lenk * 3.5);
        w.koerper.setRotation(gier, 0);
        double hoch = (tasten.isJump() ? 0.35 : 0) - (tasten.isSprint() ? 0.35 : 0);
        Vector v = richtung(gier).multiply(w.tempo);
        w.koerper.setVelocity(new Vector(v.getX(), hoch, v.getZ()));
        if (w.koerper.getTicksLived() % 10 == 0 && (w.tempo != 0 || hoch != 0)) {
            w.koerper.getWorld().playSound(w.koerper.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 0.4F, 0.6F);
        }
    }

    // ------------------------------------------------------------------
    // Autopilot (James als Pilot, siehe butler/Flug)
    // ------------------------------------------------------------------

    /** Der nächste Hubschrauber im Umkreis, an dessen Steuer kein Spieler sitzt, oder null. */
    public ArmorStand hubschrauber(Location nahe, double radius) {
        ArmorStand beste = null;
        for (Wagen w : wagen.values()) {
            Location l = w.koerper.getLocation();
            if (w.art.fliegt() && w.koerper.isValid() && fahrer(w) == null && l.getWorld().equals(nahe.getWorld())
                    && l.distanceSquared(nahe) < radius * radius
                    && (beste == null || l.distanceSquared(nahe) < beste.getLocation().distanceSquared(nahe))) {
                beste = w.koerper;
            }
        }
        return beste;
    }

    /** Setzt jemanden (James) auf den Pilotensitz; false, wenn der besetzt ist. */
    public boolean pilotSetzen(ArmorStand heli, Entity pilot) {
        Wagen w = wagen.get(heli.getUniqueId());
        if (w == null) {
            return false;
        }
        ItemDisplay alt = w.sitze.get(0);
        if (alt != null && alt.isValid() && !alt.getPassengers().isEmpty()) {
            return alt.getPassengers().contains(pilot);
        }
        ItemDisplay sitz = teil(w, ItemDisplay.class, "sitz", d -> {
            d.getPersistentDataContainer().set(schluesselPlatz, PersistentDataType.INTEGER, 0);
            d.setTeleportDuration(2);
        });
        sitz.teleport(sitzOrt(w, 0));
        pilot.teleport(sitz.getLocation());
        sitz.addPassenger(pilot);
        w.sitze.put(0, sitz);
        return true;
    }

    /** Sitzt jemand in diesem Fahrzeug (egal auf welchem Platz)? */
    public boolean sitztDrin(ArmorStand fahrzeug, Entity wer) {
        Wagen w = wagen.get(fahrzeug.getUniqueId());
        return w != null && w.sitze.values().stream().anyMatch(s -> s.isValid() && s.getPassengers().contains(wer));
    }

    /**
     * Ein Tick Autopilot (nur ohne Spieler am Steuer): der Hubschrauber rückt um {@code schritt} weiter und schaut in
     * Richtung {@code gier}. Er wird versetzt statt angeschoben, weil Figuren außerhalb der Simulationsweite der
     * Spieler stillstehen (der Landeplatz auf dem Dach ist oft weiter weg); Modell und Sitze gleiten hinterher.
     */
    public void steuern(ArmorStand heli, Vector schritt, float gier) {
        Wagen w = wagen.get(heli.getUniqueId());
        if (w == null || fahrer(w) != null) {
            return;
        }
        Location l = heli.getLocation().add(schritt);
        l.setYaw(gier);
        l.setPitch(0);
        heli.setVelocity(new Vector());
        heli.teleport(l, io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
        if (Bukkit.getCurrentTick() % 10 == 0 && schritt.lengthSquared() > 1.0E-4) {
            heli.getWorld().playSound(l, Sound.ENTITY_BREEZE_WIND_BURST, 0.5F, 0.6F);
        }
    }

    // ------------------------------------------------------------------
    // Ein- und Aussteigen
    // ------------------------------------------------------------------

    @EventHandler
    public void beimKlick(PlayerInteractEntityEvent e) {
        Entity ziel = e.getRightClicked();
        if (!ziel.getPersistentDataContainer().has(schluesselRolle) || e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        e.setCancelled(true);
        String von = ziel.getPersistentDataContainer().get(schluesselWagen, PersistentDataType.STRING);
        Wagen w = von == null ? null : wagen.get(UUID.fromString(von));
        if (w != null && !e.getPlayer().isInsideVehicle()) {
            einsteigen(w, e.getPlayer());
        }
    }

    /** Testhilfe: in das nächste Fahrzeug (bis 10 Blöcke) einsteigen, als wäre es angeklickt. */
    public boolean einsteigen(Player p) {
        Wagen beste = null;
        for (Wagen w : wagen.values()) {
            if (w.koerper.isValid() && w.koerper.getWorld().equals(p.getWorld())
                    && w.koerper.getLocation().distanceSquared(p.getLocation()) < 100
                    && (beste == null || w.koerper.getLocation().distanceSquared(p.getLocation())
                    < beste.koerper.getLocation().distanceSquared(p.getLocation()))) {
                beste = w;
            }
        }
        if (beste == null || p.isInsideVehicle()) {
            return false;
        }
        einsteigen(beste, p);
        return true;
    }

    /** Setzt den Spieler auf den ersten freien Platz (Platz 0 = Fahrer). */
    private void einsteigen(Wagen w, Player spieler) {
        for (int platz = 0; platz < w.art.sitze.length; platz++) {
            ItemDisplay sitz = w.sitze.get(platz);
            if (sitz == null || !sitz.isValid() || sitz.getPassengers().isEmpty()) {
                int p = platz;
                ItemDisplay neu = teil(w, ItemDisplay.class, "sitz", d -> {
                    d.getPersistentDataContainer().set(schluesselPlatz, PersistentDataType.INTEGER, p);
                    d.setTeleportDuration(2);
                });
                neu.teleport(sitzOrt(w, platz));
                neu.addPassenger(spieler);
                w.sitze.put(platz, neu);
                if (platz == 0) {
                    spieler.sendActionBar(net.kyori.adventure.text.Component.text(w.art.fliegt()
                            ? "W/S: vor/zurück · A/D: drehen · Leertaste: steigen · Sprinten: sinken · Schleichen: aussteigen"
                            : "W/S: Gas/Bremse · A/D: lenken · Leertaste: Handbremse · Schleichen: aussteigen"));
                }
                return;
            }
        }
    }

    /** Klick auf den unsichtbaren Körper (Rüstungsständer) zählt wie ein Klick aufs Klickfeld. */
    @EventHandler
    public void beimKlickAufKoerper(org.bukkit.event.player.PlayerInteractAtEntityEvent e) {
        if (!e.getRightClicked().getPersistentDataContainer().has(schluesselArt)) {
            return;
        }
        e.setCancelled(true);
        Wagen w = wagen.get(e.getRightClicked().getUniqueId());
        if (w != null && w.klick != null && e.getHand() == EquipmentSlot.HAND) {
            beimKlick(new PlayerInteractEntityEvent(e.getPlayer(), w.klick, EquipmentSlot.HAND));
        }
    }

    @EventHandler
    public void beimAnfassen(org.bukkit.event.player.PlayerArmorStandManipulateEvent e) {
        if (e.getRightClicked().getPersistentDataContainer().has(schluesselArt)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void beimAussteigen(EntityDismountEvent e) {
        Entity sitz = e.getDismounted();
        if ("sitz".equals(sitz.getPersistentDataContainer().get(schluesselRolle, PersistentDataType.STRING))) {
            Entity wer = e.getEntity();
            Bukkit.getScheduler().runTask(plugin, () -> {
                sitz.remove();
                // Neben dem Wagen absetzen, nicht darin
                Location l = wer.getLocation();
                wer.teleport(l.add(richtung(l.getYaw() + 90).multiply(1.5)).add(0, 0.3, 0));
            });
        }
    }

    @EventHandler
    public void beimVerlassen(PlayerQuitEvent e) {
        Entity f = e.getPlayer().getVehicle();
        if (f != null && f.getPersistentDataContainer().has(schluesselRolle)) {
            f.eject();
            f.remove();
        }
    }

    @EventHandler
    public void beimLaden(EntitiesLoadEvent e) {
        e.getEntities().forEach(this::aufnehmen);
    }

    @EventHandler
    public void beimSchaden(EntityDamageEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(schluesselArt) && e.getCause() != EntityDamageEvent.DamageCause.KILL) {
            e.setCancelled(true);
        }
    }

    private void teileEntfernen(Wagen w) {
        if (w.modell != null) {
            w.modell.remove();
        }
        if (w.klick != null) {
            w.klick.remove();
        }
        w.sitze.values().forEach(Entity::remove);
    }
}
