package de.moinmornhart.stinkendreich.musik;

import com.google.gson.JsonObject;
import de.moinmornhart.stinkendreich.Konfig;
import de.moinmornhart.stinkendreich.mechanik.DatenBlockEntity;
import de.moinmornhart.stinkendreich.register.ModMusik;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Zustand einer Musikanlage. Der Server entscheidet, was läuft; alle Clients bekommen den Zustand
 * mitgeschickt. Wer die Datei mit demselben Namen in seinem Musikordner hat, hört mit – ab der
 * richtigen Stelle, denn der Startzeitpunkt steht als Spielzeit (Ticks) mit drin.
 */
public class MusikanlageBlockEntity extends DatenBlockEntity {
    /** Quelle der Musik: eigene Dateien (im Spiel hörbar) oder Spotify (läuft auf dem Gerät des DJs). */
    public static final String DATEI = "datei";
    public static final String SPOTIFY = "spotify";

    /** Was nach dem Ende eines Titels passiert. */
    public enum Wiederholen { AUS, ALLE, EINS }

    /** So lange darf der DJ nach dem Titelende brauchen, bis der Server von selbst stoppt (Ticks). */
    private static final long NACHLAUF = 100;
    /** Ohne Meldung vom Spotify-DJ stoppt die Anzeige nach dieser Zeit (Ticks). */
    private static final long SPOTIFY_STILLE = 20 * 30;

    // ---------- gespeichert und an die Clients geschickt ----------
    private boolean spielt;
    private String quelle = DATEI;
    private String titel = "";
    private int index = -1;
    /** Zählt jeden Titelstart hoch, damit Clients auch beim selben Titel neu anfangen. */
    private int lauf;
    /** Spielzeit (Ticks) beim Start des Titels. */
    private long start;
    /** Länge des Titels in Ticks (0 = unbekannt). */
    private long dauer;
    private UUID dj;
    private String djName = "";
    private int lautstaerke = 80;
    private boolean zufall;
    private Wiederholen wiederholen = Wiederholen.ALLE;

    // ---------- nur zur Laufzeit ----------
    private long letzteSpotifyMeldung;
    private int ticks;
    private List<BlockPos> lautsprecher = List.of();
    private long lautsprecherStand = -1000;

    public MusikanlageBlockEntity(BlockPos pos, BlockState zustand) {
        super(ModMusik.MUSIKANLAGE_TYP, pos, zustand);
    }

    // ---------------- Abfragen (Server und Client) ----------------

    public boolean spielt() {
        return spielt;
    }

    public String quelle() {
        return quelle;
    }

    public String titel() {
        return titel;
    }

    public int index() {
        return index;
    }

    public int lauf() {
        return lauf;
    }

    public long start() {
        return start;
    }

    public long dauer() {
        return dauer;
    }

    public UUID dj() {
        return dj;
    }

    public String djName() {
        return djName;
    }

    public int lautstaerke() {
        return lautstaerke;
    }

    public boolean zufall() {
        return zufall;
    }

    public Wiederholen wiederholen() {
        return wiederholen;
    }

    /**
     * Lautsprecher, die zu dieser Anlage gehören (im Umkreis {@code lautsprecherRadius} aus der
     * Config). Wird höchstens alle 5 Sekunden neu gesucht, nur in geladenen Chunks.
     */
    public List<BlockPos> lautsprecher() {
        if (level == null) {
            return List.of();
        }
        long jetzt = level.getGameTime();
        if (jetzt - lautsprecherStand >= 100 || jetzt < lautsprecherStand) {
            lautsprecherStand = jetzt;
            lautsprecher = lautsprecherSuchen(level, worldPosition);
        }
        return lautsprecher;
    }

    private static List<BlockPos> lautsprecherSuchen(Level level, BlockPos mitte) {
        int r = Math.max(0, Math.min(48, Konfig.get().lautsprecherRadius));
        List<BlockPos> gefunden = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                p.set(mitte.getX() + x, mitte.getY(), mitte.getZ() + z);
                // Ungeladene Chunks nicht anfassen (auf dem Server würde das sie laden)
                if (!level.isLoaded(p)) {
                    continue;
                }
                for (int y = -8; y <= 8; y++) {
                    p.setY(mitte.getY() + y);
                    if (level.getBlockState(p).is(ModMusik.LAUTSPRECHER)) {
                        gefunden.add(p.immutable());
                        if (gefunden.size() >= 64) {
                            return gefunden;
                        }
                    }
                }
            }
        }
        return gefunden;
    }

    // ---------------- Änderungen (nur Server) ----------------

    /** Startet einen Titel aus dem Musikordner des DJs. */
    public void dateiStarten(ServerPlayer spieler, int neuerIndex, String neuerTitel, long neueDauer) {
        quelle = DATEI;
        index = neuerIndex;
        titel = neuerTitel;
        dauer = neueDauer;
        start = level == null ? 0 : level.getGameTime();
        lauf++;
        spielt = true;
        djSetzen(spieler);
        geaendert();
    }

    /** Der DJ meldet, was gerade auf seinem Spotify läuft (für Anzeige und Partylichter). */
    public void spotifyMelden(ServerPlayer spieler, boolean laeuft, String neuerTitel) {
        letzteSpotifyMeldung = level == null ? 0 : level.getGameTime();
        boolean neu = !SPOTIFY.equals(quelle) || laeuft != spielt || !neuerTitel.equals(titel) || !spieler.getUUID().equals(dj);
        if (!neu) {
            return;
        }
        if (!SPOTIFY.equals(quelle) || !neuerTitel.equals(titel)) {
            lauf++;
            start = letzteSpotifyMeldung;
        }
        quelle = SPOTIFY;
        titel = neuerTitel;
        index = -1;
        dauer = 0;
        spielt = laeuft;
        djSetzen(spieler);
        geaendert();
    }

    public void stoppen() {
        if (spielt) {
            spielt = false;
            geaendert();
        }
    }

    public void setzeLautstaerke(int wert) {
        lautstaerke = Math.max(0, Math.min(100, wert));
        geaendert();
    }

    public void setzeZufall(boolean wert) {
        zufall = wert;
        geaendert();
    }

    public void setzeWiederholen(Wiederholen wert) {
        wiederholen = wert;
        geaendert();
    }

    private void djSetzen(ServerPlayer spieler) {
        dj = spieler.getUUID();
        djName = spieler.getScoreboardName();
    }

    /** Takt auf dem Server: stoppt, wenn der DJ weg ist und niemand den nächsten Titel startet. */
    void serverTakt() {
        if (++ticks < 20 || level == null) {
            return;
        }
        ticks = 0;
        if (!spielt) {
            return;
        }
        long jetzt = level.getGameTime();
        if (SPOTIFY.equals(quelle)) {
            boolean djDa = dj != null && level.getServer() != null && level.getServer().getPlayerList().getPlayer(dj) != null;
            if (!djDa || jetzt - letzteSpotifyMeldung > SPOTIFY_STILLE) {
                stoppen();
            }
        } else if (dauer > 0 && jetzt > start + dauer + NACHLAUF) {
            stoppen();
        }
    }

    // ---------------- Anmelden bei MusikZustand ----------------

    @Override
    public void setLevel(Level neueWelt) {
        super.setLevel(neueWelt);
        MusikZustand.anmelden(neueWelt, worldPosition);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        MusikZustand.abmelden(level, worldPosition);
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        MusikZustand.anmelden(level, worldPosition);
    }

    // ---------------- Speichern ----------------

    @Override
    protected void schreiben(JsonObject d) {
        d.addProperty("spielt", spielt);
        d.addProperty("quelle", quelle);
        d.addProperty("titel", titel);
        d.addProperty("index", index);
        d.addProperty("lauf", lauf);
        d.addProperty("start", start);
        d.addProperty("dauer", dauer);
        if (dj != null) {
            d.addProperty("dj", dj.toString());
        }
        d.addProperty("djName", djName);
        d.addProperty("lautstaerke", lautstaerke);
        d.addProperty("zufall", zufall);
        d.addProperty("wiederholen", wiederholen.name());
    }

    @Override
    protected void lesen(JsonObject d) {
        spielt = d.has("spielt") && d.get("spielt").getAsBoolean();
        quelle = d.has("quelle") ? d.get("quelle").getAsString() : DATEI;
        titel = d.has("titel") ? d.get("titel").getAsString() : "";
        index = d.has("index") ? d.get("index").getAsInt() : -1;
        lauf = d.has("lauf") ? d.get("lauf").getAsInt() : 0;
        start = d.has("start") ? d.get("start").getAsLong() : 0;
        dauer = d.has("dauer") ? d.get("dauer").getAsLong() : 0;
        try {
            dj = d.has("dj") ? UUID.fromString(d.get("dj").getAsString()) : null;
        } catch (IllegalArgumentException e) {
            dj = null;
        }
        djName = d.has("djName") ? d.get("djName").getAsString() : "";
        lautstaerke = d.has("lautstaerke") ? d.get("lautstaerke").getAsInt() : 80;
        zufall = d.has("zufall") && d.get("zufall").getAsBoolean();
        try {
            wiederholen = d.has("wiederholen") ? Wiederholen.valueOf(d.get("wiederholen").getAsString().toUpperCase(Locale.ROOT)) : Wiederholen.ALLE;
        } catch (IllegalArgumentException e) {
            wiederholen = Wiederholen.ALLE;
        }
    }
}
