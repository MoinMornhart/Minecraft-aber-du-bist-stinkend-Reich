package de.moinmornhart.stinkendreich.paper.block;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.moinmornhart.stinkendreich.paper.Dialoge;
import de.moinmornhart.stinkendreich.paper.StinkendReichPaper;
import de.moinmornhart.stinkendreich.paper.villa.Villen;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sicherheitssystem wie in der Mod (VillaZustand, Sicherheitszentrale, Laser, Bewegungsmelder, Sirene, Warnleuchte,
 * Tastenfeld, Tresorfallen): je Villa eine Stufe, gespeichert in {@code sicherheit.json}.
 * <ul>
 *   <li>Stufe 0: aus (Tor offen, Kartenleser lassen jeden durch)</li>
 *   <li>Stufe 1: Alarm bei Eindringlingen (Bewegungsmelder, Tastenfeld, Zentrale, Tresorfallen)</li>
 *   <li>Stufe 2: zusätzlich Lockdown (Rollläden zu, Schiebetüren nur für Berechtigte)</li>
 *   <li>Stufe 3: zusätzlich Laser</li>
 *   <li>Stufe 4: Festung (Minen, Abwehrgeschütze, Himmelslaser, Zugbrücke bleibt für Fremde drin); nur an der Zentrale
 *   in der Kommandozentrale im Bunker</li>
 * </ul>
 * Eindringlinge sind Monster und Spieler, die weder Bewohner noch Gast sind und keine Zugangskarte haben.
 */
public final class Sicherheit {
    public static final int MAX_STUFE = 4;
    /** Ab dieser Stufe ist die Villa eine Festung (siehe {@link Festung}). */
    public static final int FESTUNG = 4;
    private static final String[] STUFEN = {"Stufe 0 – Aus", "Stufe 1 – Alarm", "Stufe 2 – Lockdown", "Stufe 3 – Laser", "Festung"};
    private static final int LASER_REICHWEITE = 24, LASER_MAX = 64;
    private static final String STANDARD_CODE = "1234";

    private final StinkendReichPaper plugin;
    private final VirtuelleBloecke bloecke;
    private final Zugang zugang;
    private final Tore tore;
    private final Path datei;
    /** Stufe je Villa-Nummer (Standard 1). */
    private final Map<Integer, Integer> stufen = new HashMap<>();
    /** Alarm bis zu diesem Server-Tick, je Villa. */
    private final Map<Integer, Long> alarmBis = new HashMap<>();
    private final Map<Integer, Long> entschaerftBis = new HashMap<>();
    /** Takt, zu dem die Festung eingeschaltet wurde (für die Welle; fehlt = schon lange an). */
    private final Map<Integer, Long> festungSeit = new HashMap<>();
    /** Entschärfknöpfe: wann welcher zuletzt gedrückt wurde (je Villa, Schlüssel = Position). */
    private final Map<Integer, Map<String, Long>> druecke = new HashMap<>();
    /** Falsche Codes je Spieler (ab drei Alarm). */
    private final Map<UUID, Integer> fehlversuche = new HashMap<>();
    /** Letzte Auslösung je Fallenplatte (Position). */
    private final Map<String, Long> fallenZuletzt = new HashMap<>();
    private long tick;

    public Sicherheit(StinkendReichPaper plugin, VirtuelleBloecke bloecke, Mechanik mechanik, Zugang zugang, Tore tore) {
        this.plugin = plugin;
        this.bloecke = bloecke;
        this.zugang = zugang;
        this.tore = tore;
        this.datei = plugin.getDataFolder().toPath().resolve("sicherheit.json");
        try {
            if (Files.exists(datei)) {
                JsonObject j = JsonParser.parseString(Files.readString(datei, StandardCharsets.UTF_8)).getAsJsonObject();
                j.entrySet().forEach(e -> stufen.put(Integer.parseInt(e.getKey()), e.getValue().getAsJsonObject().get("stufe").getAsInt()));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("sicherheit.json konnte nicht gelesen werden: " + e.getMessage());
        }
        mechanik.anmelden("sicherheitszentrale", (p, b, z) -> {
            zentrale(p, b);
            return true;
        });
        mechanik.anmelden("tastenfeld", (p, b, z) -> {
            tastenfeld(p, b, "");
            return true;
        });
        mechanik.anmelden("entschaerfknopf", (p, b, z) -> {
            entschaerfen(p, b);
            return true;
        });
        Bukkit.getScheduler().runTaskTimer(plugin, this::takt, 5L, 5L);
    }

    // ------------------------------------------------------------------
    // Zustand
    // ------------------------------------------------------------------

    private Villen.Villa villa(Block b) {
        return plugin.villen().bei(b.getLocation(), 16);
    }

    private static int nr(Villen.Villa v) {
        return v == null ? -1 : v.nr();
    }

    public int stufe(Villen.Villa v) {
        return stufen.getOrDefault(nr(v), 1);
    }

    public int stufe(Block b) {
        return stufe(villa(b));
    }

    public boolean alarmAktiv(Villen.Villa v) {
        return alarmBis.getOrDefault(nr(v), 0L) > tick;
    }

    /** Vor dem Versenken (Inkognito): System aus, keine Laser, Minen oder Lava im Versteck. */
    public void stufeFuerInkognito(Villen.Villa v) {
        if (stufe(v) != 0) {
            stufeSetzen(v, 0);
        }
    }

    /** Ticks seit dem Einschalten der Festung (groß, wenn sie schon vor dem Start an war). */
    public long festungTicks(Villen.Villa v) {
        Long seit = festungSeit.get(nr(v));
        return seit == null ? Long.MAX_VALUE / 2 : tick - seit;
    }

    private void stufeSetzen(Villen.Villa v, int neu) {
        boolean warFestung = stufe(v) >= FESTUNG;
        stufen.put(nr(v), Math.max(0, Math.min(MAX_STUFE, neu)));
        JsonObject j = new JsonObject();
        stufen.forEach((n, s) -> {
            JsonObject o = new JsonObject();
            o.addProperty("stufe", s);
            j.add(Integer.toString(n), o);
        });
        try {
            Files.createDirectories(datei.getParent());
            Files.writeString(datei, new GsonBuilder().setPrettyPrinting().create().toJson(j), StandardCharsets.UTF_8);
        } catch (Exception e) {
            plugin.getLogger().warning("sicherheit.json konnte nicht gespeichert werden: " + e.getMessage());
        }
        plugin.getLogger().info("Sicherheit: Villa " + nr(v) + " auf " + STUFEN[stufe(v)]);
        if (stufe(v) >= FESTUNG && !warFestung) {
            // Festung: Sirenen und Warnleuchten, Geschütze, Laser und Minen fahren nacheinander hoch; nichts im Chat
            festungSeit.put(nr(v), tick);
            alarmBis.put(nr(v), tick + 20L * 12);
            anBewohner(v, Component.text("FESTUNG: Die Villa ist uneinnehmbar.", NamedTextColor.GOLD).decorate(TextDecoration.BOLD), false);
            return;
        }
        anBewohner(v, Component.text("Sicherheitssystem: " + STUFEN[stufe(v)], NamedTextColor.AQUA), false);
    }

    /** Monster immer; Spieler, wenn sie nicht hineindürfen (kein Bewohner, kein Gast, keine Karte). */
    public boolean istEindringling(Entity e, Villen.Villa v) {
        if (e instanceof Player p) {
            return p.getGameMode() != GameMode.SPECTATOR && p.getGameMode() != GameMode.CREATIVE && !zugang.darfRein(p, v);
        }
        return e instanceof Enemy;
    }

    /** Löst Alarm aus (30 Sekunden, verlängert sich bei weiteren Auslösungen). */
    public void alarm(Villen.Villa v, Block ort, String grund) {
        boolean neu = !alarmAktiv(v);
        alarmBis.put(nr(v), tick + 20L * 30);
        if (neu) {
            plugin.getLogger().info("Alarm bei " + ort.getX() + " " + ort.getY() + " " + ort.getZ() + ": " + grund);
            anBewohner(v, Component.text("ALARM! " + grund + " (bei " + ort.getX() + " " + ort.getY() + " " + ort.getZ() + ")",
                    NamedTextColor.RED).decorate(TextDecoration.BOLD), stufe(v) < FESTUNG);
        }
    }

    private void anBewohner(Villen.Villa v, Component text, boolean auchImChat) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (zugang.istBewohner(p, v)) {
                p.sendActionBar(text);
                if (auchImChat) {
                    p.sendMessage(text);
                }
            }
        }
    }

    private static void ton(Block b, String name, float lautstaerke, float hoehe) {
        b.getWorld().playSound(b.getLocation().add(0.5, 0.5, 0.5), "stinkendreich:" + name, SoundCategory.BLOCKS, lautstaerke, hoehe);
    }

    // ------------------------------------------------------------------
    // Takt (alle 5 Ticks): Laser; alle 10: Bewegungsmelder, Warnleuchten, Fallen; alle 30: Sirenen
    // ------------------------------------------------------------------

    private void takt() {
        tick += 5;
        for (Block b : bloecke.alle("laser_sender")) {
            laser(b);
        }
        if (tick % 10 == 0) {
            for (Block b : bloecke.alle("bewegungsmelder")) {
                bewegungsmelder(b);
            }
            for (Block b : bloecke.alle("warnleuchte")) {
                warnleuchte(b);
            }
            fallen();
        }
        if (tick % 30 == 0) {
            for (Block b : bloecke.alle("alarmsirene")) {
                if (alarmAktiv(villa(b))) {
                    ton(b, "sirene", 3.0F, 1.0F);
                }
            }
        }
        // Alarm vorbei: einmal melden
        alarmBis.entrySet().removeIf(e -> {
            if (e.getValue() > tick) {
                return false;
            }
            Villen.Villa v = plugin.villen().nr(e.getKey());
            anBewohner(v, Component.text("Alarm beendet.", NamedTextColor.GREEN), false);
            return true;
        });
    }

    /** Laser-Sender: ab Stufe 3 wächst je Takt der Strahl um zwei Blöcke bis zur Wand, darunter schrumpft er wieder. */
    private void laser(Block sender) {
        Zustand z = bloecke.zustand(sender);
        if (z == null) {
            return;
        }
        BlockFace richtung = BlockFace.valueOf(z.wert("facing") == null ? "NORTH" : z.wert("facing").toUpperCase(java.util.Locale.ROOT));
        Villen.Villa v = villa(sender);
        boolean an = stufe(v) >= 3 && (richtung != BlockFace.UP || stufe(v) < FESTUNG || plugin.festung().bereit(v, sender, true));
        int reichweite = Math.max(1, Math.min(LASER_MAX, zahl(bloecke.werte(sender).get("reichweite"), LASER_REICHWEITE)));
        String achse = switch (richtung) {
            case UP, DOWN -> "y";
            case EAST, WEST -> "x";
            default -> "z";
        };
        int laenge = 0;
        for (int i = 1; i <= reichweite; i++) {
            Zustand s = bloecke.zustand(sender.getRelative(richtung, i));
            if (s == null || !s.name().equals("laserstrahl")) {
                break;
            }
            laenge = i;
        }
        if (!an) {
            for (int i = laenge; i > Math.max(0, laenge - 2); i--) {
                bloecke.entfernen(sender.getRelative(richtung, i));
            }
            return;
        }
        for (int neu = 0; neu < 2 && laenge < reichweite; neu++) {
            Block q = sender.getRelative(richtung, laenge + 1);
            if (!q.getType().isAir() || bloecke.zustand(q) != null) {
                break;
            }
            bloecke.setzen(q, Zustand.lesen("stinkendreich:laserstrahl[axis=" + achse + "]"));
            laenge++;
        }
        if (laenge == 0) {
            return;
        }
        // Wer im Strahl steht: Alarm, Schaden, leuchtet 30 Sekunden
        Location a = sender.getRelative(richtung).getLocation().add(0.5, 0.5, 0.5);
        Location e = sender.getRelative(richtung, laenge).getLocation().add(0.5, 0.5, 0.5);
        BoundingBox kasten = BoundingBox.of(a, e).expand(0.3);
        for (Entity ent : sender.getWorld().getNearbyEntities(kasten)) {
            if (ent instanceof LivingEntity le && !(ent instanceof ArmorStand) && istEindringling(ent, v)) {
                if (ent instanceof Player p) {
                    alarm(v, ent.getLocation().getBlock(), p.getName() + " hat einen Laser durchbrochen");
                }
                le.damage(3.0);
                le.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 30, 0));
            }
        }
    }

    /** Bewegungsmelder: „powered“ solange sich vor ihm etwas bewegt (Licht in der Garage), ab Stufe 1 Alarm. */
    private void bewegungsmelder(Block b) {
        Zustand z = bloecke.zustand(b);
        if (z == null) {
            return;
        }
        BlockFace vorne = BlockFace.valueOf(z.wert("facing") == null ? "NORTH" : z.wert("facing").toUpperCase(java.util.Locale.ROOT));
        BoundingBox kasten = BoundingBox.of(b).expandDirectional(vorne.getModX() * 7.0, 0, vorne.getModZ() * 7.0).expand(3, 2, 3);
        List<LivingEntity> gesehen = new ArrayList<>();
        for (Entity e : b.getWorld().getNearbyEntities(kasten)) {
            if (e instanceof LivingEntity le && !(e instanceof ArmorStand) && !(e instanceof Player p && p.getGameMode() == GameMode.SPECTATOR)) {
                gesehen.add(le);
            }
        }
        boolean bewegung = !gesehen.isEmpty();
        if (!z.ist("powered", Boolean.toString(bewegung))) {
            bloecke.setzen(b, z.mit("powered", Boolean.toString(bewegung)));
        }
        // Wie das Redstone-Signal in der Mod: Lampen am Block dahinter leuchten mit
        Block hinten = b.getRelative(vorne.getOppositeFace());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block l = hinten.getRelative(dx, dy, dz);
                    if (l.getType() == Material.REDSTONE_LAMP && l.getBlockData() instanceof Lightable lampe && lampe.isLit() != bewegung) {
                        lampe.setLit(bewegung);
                        l.setBlockData(lampe, false);
                    }
                }
            }
        }
        Villen.Villa v = villa(b);
        if (stufe(v) >= 1) {
            for (LivingEntity e : gesehen) {
                if (istEindringling(e, v)) {
                    alarm(v, b, "Bewegung erkannt: " + e.getName());
                    break;
                }
            }
        }
    }

    /** Warnleuchte: blinkt während eines Alarms. */
    private void warnleuchte(Block b) {
        Zustand z = bloecke.zustand(b);
        if (z == null) {
            return;
        }
        boolean an = (alarmAktiv(villa(b)) || plugin.zugbruecke().warnen(b)) && (tick / 10) % 2 == 0;
        if (!z.ist("lit", Boolean.toString(an))) {
            bloecke.setzen(b, z.mit("lit", Boolean.toString(an)));
        }
    }

    /** Tresorfallen: Eindringling auf einer Fallenplatte → Alarm, Gift, Blindheit, Gitter 15 Sekunden zu. */
    private void fallen() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR || !p.isOnGround()) {
                continue;
            }
            Block unter = p.getLocation().subtract(0, 0.1, 0).getBlock();
            Zustand z = bloecke.zustand(unter);
            if (z == null || !z.name().equals("fallenplatte")) {
                continue;
            }
            Villen.Villa v = villa(unter);
            String k = unter.getX() + " " + unter.getY() + " " + unter.getZ();
            if (stufe(v) < 1 || !istEindringling(p, v) || entschaerftBis.getOrDefault(nr(v), 0L) > tick
                    || tick - fallenZuletzt.getOrDefault(k, -1000L) < 100) {
                continue;
            }
            fallenZuletzt.put(k, tick);
            alarm(v, unter, p.getName() + " hat die Tresorfalle ausgelöst");
            p.sendActionBar(Component.text("FALLE! Der Tresor ist verriegelt.", NamedTextColor.RED).decorate(TextDecoration.BOLD));
            p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 20 * 6, 0));
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 4, 0));
            ton(unter, "tresor", 1.5F, 0.7F);
            tore.umschalten(unter, 10, "stinkendreich:gitterfalle", false);
            Bukkit.getScheduler().runTaskLater(plugin, () -> tore.umschalten(unter, 10, "stinkendreich:gitterfalle", true), 20L * 15);
        }
    }

    // ------------------------------------------------------------------
    // Bedienung
    // ------------------------------------------------------------------

    /** Sicherheitszentrale: Stufe wählen (nur Eigentümer), Alarm abschalten (Bewohner). */
    public void zentrale(Player p, Block b) {
        Villen.Villa v = villa(b);
        String bewohner = v == null || v.besitzerName() == null ? "–" : v.besitzerName();
        String text = "Sicherheitssystem: " + STUFEN[stufe(v)] + " · Zustand: " + (alarmAktiv(v) ? "Alarm" : "ruhig")
                + " · Bewohner: " + bewohner;
        List<Dialoge.Knopf> k = new ArrayList<>();
        boolean kommando = "ja".equals(bloecke.werte(b).get("kommando"));
        for (int i = 0; i <= (kommando ? MAX_STUFE : MAX_STUFE - 1); i++) {
            int stufe = i;
            k.add(Dialoge.Knopf.von((stufe(v) == i ? "▶ " : "") + STUFEN[i], (s, a) -> zentraleKnopf(s, b, "stufe:" + stufe)));
        }
        k.add(Dialoge.Knopf.von("Alarm abschalten", (s, a) -> zentraleKnopf(s, b, "alarm_aus")));
        Dialoge.zeigen(p, "Sicherheitszentrale", text, List.of(), k);
    }

    /** Ein Knopf der Zentrale (auch für Tests: {@code /villa zentrale <wert>}). */
    public void zentraleKnopf(Player p, Block b, String wert) {
        Villen.Villa v = villa(b);
        if (!zugang.istBewohner(p, v)) {
            p.sendActionBar(Component.text("Zugriff verweigert.", NamedTextColor.RED));
            ton(b, "piep_fehler", 0.8F, 1.0F);
            if (stufe(v) >= 1) {
                alarm(v, b, p.getName() + " hat die Zentrale ohne Berechtigung benutzt");
            }
            return;
        }
        if (wert.equals("alarm_aus")) {
            alarmBis.put(nr(v), tick);
            ton(b, "piep_ok", 0.8F, 1.0F);
        } else if (wert.startsWith("stufe:")) {
            if (!zugang.istEigentuemer(p, v)) {
                p.sendActionBar(Component.text("Die Alarmstufe kann nur der Eigentümer der Villa einstellen.", NamedTextColor.RED));
                ton(b, "piep_fehler", 0.8F, 1.0F);
                return;
            }
            int neu = zahl(wert.substring(6), stufe(v));
            if (neu >= FESTUNG && !"ja".equals(bloecke.werte(b).get("kommando"))) {
                p.sendActionBar(Component.text("Die Festung lässt sich nur in der Kommandozentrale im Bunker einschalten.", NamedTextColor.RED));
                ton(b, "piep_fehler", 0.8F, 1.0F);
                return;
            }
            stufeSetzen(v, neu);
            ton(b, "piep_ok", 0.8F, 1.0F);
        }
    }

    /** Tastenfeld: Code eingeben (Standard 1234), richtig = Tür 5 Sekunden offen; Bewohner können den Code ändern. */
    public void tastenfeld(Player p, Block b, String hinweis) {
        List<Dialoge.Knopf> k = new ArrayList<>();
        k.add(Dialoge.Knopf.von("Öffnen", (s, a) -> codeEingeben(s, b, a.getText("code"))));
        k.add(Dialoge.Knopf.von("Code ändern", (s, a) -> codeAendern(s, b, a.getText("code"), a.getText("neu"))));
        Dialoge.zeigen(p, "Tastenfeld", hinweis.isEmpty() ? "Code eingeben." : hinweis, List.of(
                DialogInput.text("code", Component.text("Code")).maxLength(8).build(),
                DialogInput.text("neu", Component.text("Neuer Code (nur zum Ändern)")).maxLength(8).build()), k);
    }

    /** Code prüfen (auch für Tests). */
    public void codeEingeben(Player p, Block b, String code) {
        Zustand z = bloecke.zustand(b);
        if (z == null) {
            return;
        }
        if (code != null && code.trim().equals(bloecke.werte(b).getOrDefault("code", STANDARD_CODE))) {
            fehlversuche.remove(p.getUniqueId());
            ton(b, "piep_ok", 1.0F, 1.2F);
            p.sendActionBar(Component.text("Code richtig – Tür offen.", NamedTextColor.GREEN));
            bloecke.setzen(b, z.mit("powered", "true"));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Zustand jetzt = bloecke.zustand(b);
                if (jetzt != null) {
                    bloecke.setzen(b, jetzt.mit("powered", "false"));
                }
            }, 100L);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        Block s = b.getRelative(dx, dy, dz);
                        Zustand zs = bloecke.zustand(s);
                        if (zs != null && zs.name().equals("torsteuerung")) {
                            tore.impuls(s, 100);
                        }
                    }
                }
            }
            return;
        }
        ton(b, "piep_fehler", 1.0F, 1.0F);
        p.sendActionBar(Component.text("Falscher Code!", NamedTextColor.RED));
        int n = fehlversuche.merge(p.getUniqueId(), 1, Integer::sum);
        Villen.Villa v = villa(b);
        if (n >= 3) {
            fehlversuche.remove(p.getUniqueId());
            if (stufe(v) >= 1) {
                alarm(v, b, p.getName() + " hat dreimal einen falschen Code eingegeben");
            }
        }
    }

    private void codeAendern(Player p, Block b, String alt, String neu) {
        boolean gueltig = neu != null && neu.trim().matches("\\d{4,8}") && alt != null
                && alt.trim().equals(bloecke.werte(b).getOrDefault("code", STANDARD_CODE)) && zugang.istBewohner(p, villa(b));
        if (!gueltig) {
            p.sendActionBar(Component.text("Code nicht geändert (alter Code falsch, kein Bewohner oder neuer Code nicht 4–8 Ziffern).",
                    NamedTextColor.RED));
            return;
        }
        bloecke.wertSetzen(b, "code", neu.trim());
        p.sendActionBar(Component.text("Neuer Code gespeichert.", NamedTextColor.GREEN));
    }

    /** Entschärfknopf: genug verschiedene Knöpfe in 15 Sekunden → Tresorfallen 60 Sekunden aus. */
    private void entschaerfen(Player p, Block b) {
        Villen.Villa v = villa(b);
        Map<String, Long> m = druecke.computeIfAbsent(nr(v), n -> new HashMap<>());
        m.put(b.getX() + " " + b.getY() + " " + b.getZ(), tick);
        m.values().removeIf(t -> tick - t > 20 * 15);
        int fehlt = Math.max(0, plugin.getConfig().getInt("tresor.knoepfe", 2) - m.size());
        ton(b, "piep_ok", 0.3F, 0.6F);
        if (fehlt == 0) {
            entschaerftBis.put(nr(v), tick + 20L * 60);
            m.clear();
            p.sendActionBar(Component.text("Tresorfallen für 60 Sekunden entschärft.", NamedTextColor.GREEN));
        } else {
            p.sendActionBar(Component.text("Klick … noch " + fehlt + " Knopf", NamedTextColor.GRAY));
        }
    }

    private static int zahl(String text, int sonst) {
        try {
            return text == null ? sonst : Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return sonst;
        }
    }
}
