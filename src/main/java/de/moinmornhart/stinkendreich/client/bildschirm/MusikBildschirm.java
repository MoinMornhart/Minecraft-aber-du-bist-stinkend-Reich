package de.moinmornhart.stinkendreich.client.bildschirm;

import de.moinmornhart.stinkendreich.Konfig;
import de.moinmornhart.stinkendreich.client.KompatClient;
import de.moinmornhart.stinkendreich.client.musik.MusikDateien;
import de.moinmornhart.stinkendreich.client.musik.MusikKlient;
import de.moinmornhart.stinkendreich.client.musik.SpotifyKlient;
import de.moinmornhart.stinkendreich.musik.MusikanlageBlockEntity;
import de.moinmornhart.stinkendreich.musik.MusikanlageBlockEntity.Wiederholen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Menü der Musikanlage mit zwei Reitern:
 * <ul>
 *   <li>„Eigene Musik“: Titel aus dem Musikordner, Abspielen/Stopp/Weiter/Zurück, Lautstärke,
 *       Zufall und Wiederholen. Alle in der Nähe hören mit, wenn sie dieselbe Datei haben.</li>
 *   <li>„Spotify“: Fernbedienung für das eigene Spotify (Premium) mit Playlists.</li>
 * </ul>
 * Zeichnet wie alle Menüs der Mod nur mit Widgets, damit es in allen Versionen gleich aussieht.
 */
public class MusikBildschirm extends MenueBildschirm {
    private static final String ANLEITUNG = "https://github.com/MoinMornhart/Minecraft-aber-du-bist-stinkend-Reich#-musik";
    private static final int TITEL_JE_SEITE = 4;
    private static final int ZEILE = 22;

    // Merkt sich Reiter und Seiten bis zum nächsten Öffnen
    private static boolean spotifyReiter;
    private static int seite;
    private static int playlistSeite;

    private final BlockPos pos;
    private String stand = "";
    private boolean ersterAufbau = true;
    private int lautstaerkeWert = -1;
    private int lautstaerkeTicks;
    private int spotifyWert = -1;
    private int spotifyTicks;

    public MusikBildschirm(BlockPos pos) {
        super(Component.translatable("stinkendreich.musik.titel"));
        this.pos = pos.immutable();
    }

    private MusikanlageBlockEntity anlage() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockEntity(pos) instanceof MusikanlageBlockEntity be ? be : null;
    }

    @Override
    protected int gesamtHoehe() {
        return 280;
    }

    @Override
    protected void knoepfe() {
        MusikanlageBlockEntity be = anlage();
        if (be == null) {
            return;
        }
        int x = links();
        int y = aktuellesY();
        Button eigene = addRenderableWidget(Button.builder(Component.translatable("stinkendreich.musik.reiter.dateien"), b -> reiter(false))
                .bounds(x, y, 98, HOEHE).build());
        Button spotify = addRenderableWidget(Button.builder(Component.translatable("stinkendreich.musik.reiter.spotify"), b -> reiter(true))
                .bounds(x + BREITE - 98, y, 98, HOEHE).build());
        // Der gewählte Reiter ist ausgegraut
        eigene.active = spotifyReiter;
        spotify.active = !spotifyReiter;
        weiter(HOEHE + 6);

        if (spotifyReiter) {
            if (ersterAufbau && SpotifyKlient.status() == SpotifyKlient.Status.VERBUNDEN) {
                SpotifyKlient.aktualisieren();
                SpotifyKlient.playlistsLaden();
            }
            spotifyInhalt();
        } else {
            dateiInhalt(be);
        }
        ersterAufbau = false;
        stand = stand(be);
    }

    private void reiter(boolean spotify) {
        spotifyReiter = spotify;
        ersterAufbau = true;
        rebuildWidgets();
    }

    // ---------------- Reiter „Eigene Musik“ ----------------

    private void dateiInhalt(MusikanlageBlockEntity be) {
        List<String> liste = MusikDateien.liste();
        boolean dateien = MusikanlageBlockEntity.DATEI.equals(be.quelle());

        // Was läuft gerade?
        if (be.spielt() && dateien) {
            textzeilen(Component.translatable("stinkendreich.musik.laeuft", MusikDateien.anzeigename(be.titel()), be.djName())
                    .withStyle(ChatFormatting.GOLD), 1);
            if (!MusikKlient.hoerbar(pos) && MusikDateien.finde(be.titel()) == null) {
                textzeilen(Component.translatable("stinkendreich.musik.fehlt").withStyle(ChatFormatting.RED), 2);
            }
        } else if (be.spielt()) {
            textzeilen(Component.translatable("stinkendreich.musik.spotify_bei", be.djName(), be.titel()).withStyle(ChatFormatting.GREEN), 1);
        } else {
            textzeilen(Component.translatable("stinkendreich.musik.gestoppt").withStyle(ChatFormatting.GRAY), 1);
        }
        weiter(2);

        // Titelliste mit Seiten
        if (liste.isEmpty()) {
            textzeilen(Component.translatable(MusikDateien.MP3 ? "stinkendreich.musik.leer.mp3" : "stinkendreich.musik.leer.ogg")
                    .withStyle(ChatFormatting.YELLOW), 3);
            weiter(4);
        } else {
            int seiten = (liste.size() + TITEL_JE_SEITE - 1) / TITEL_JE_SEITE;
            seite = Math.max(0, Math.min(seite, seiten - 1));
            for (int i = seite * TITEL_JE_SEITE; i < Math.min(liste.size(), (seite + 1) * TITEL_JE_SEITE); i++) {
                int index = i;
                String titel = liste.get(i);
                boolean aktiv = be.spielt() && dateien && titel.equals(be.titel());
                String name = kuerzen((aktiv ? "▶ " : "") + MusikDateien.anzeigename(titel), BREITE - 12);
                addRenderableWidget(Button.builder(Component.literal(name).withStyle(aktiv ? ChatFormatting.GOLD : ChatFormatting.WHITE),
                        b -> MusikKlient.starten(pos, index, titel)).bounds(links(), aktuellesY(), BREITE, HOEHE).build());
                weiter(ZEILE);
            }
            if (seiten > 1) {
                blaettern(seite, seiten, s -> seite = s);
            }
        }

        // Steuerung
        boolean laeuft = be.spielt() && dateien;
        drei(Component.translatable("stinkendreich.musik.zurueck"), () -> MusikKlient.zurueck(pos, be),
                Component.translatable(laeuft ? "stinkendreich.musik.stopp" : "stinkendreich.musik.abspielen"),
                () -> {
                    if (laeuft) {
                        MusikKlient.stoppen(pos);
                    } else {
                        MusikKlient.abspielen(pos, be);
                    }
                },
                Component.translatable("stinkendreich.musik.weiter"), () -> MusikKlient.weiter(pos, be), !liste.isEmpty());

        addRenderableWidget(new Regler(links(), aktuellesY(), BREITE, "stinkendreich.musik.lautstaerke", be.lautstaerke(), wert -> {
            lautstaerkeWert = wert;
            lautstaerkeTicks = 6;
        }));
        weiter(ZEILE);

        Wiederholen w = be.wiederholen();
        Wiederholen naechstes = Wiederholen.values()[(w.ordinal() + 1) % Wiederholen.values().length];
        zwei(Component.translatable(be.zufall() ? "stinkendreich.musik.zufall.an" : "stinkendreich.musik.zufall.aus"),
                () -> KompatClient.sende("musik", pos, "zufall|" + (be.zufall() ? 0 : 1)),
                Component.translatable("stinkendreich.musik.wiederholen." + w.name().toLowerCase(java.util.Locale.ROOT)),
                () -> KompatClient.sende("musik", pos, "wiederholen|" + naechstes.name()));

        zwei(Component.translatable("stinkendreich.musik.ordner"), () -> {
                    MusikDateien.liste();
                    KompatClient.oeffneOrdner(MusikDateien.ordner());
                    MusikDateien.neuLesen();
                },
                Component.translatable("gui.done"), this::schliessen);
        textzeilen(Component.translatable(MusikDateien.MP3 ? "stinkendreich.musik.formate.mp3" : "stinkendreich.musik.formate.ogg",
                Konfig.get().musikOrdner).withStyle(ChatFormatting.GRAY), 2);
    }

    // ---------------- Reiter „Spotify“ ----------------

    private void spotifyInhalt() {
        switch (SpotifyKlient.status()) {
            case NICHT_EINGERICHTET -> {
                textzeilen(Component.translatable("stinkendreich.spotify.einrichten").withStyle(ChatFormatting.YELLOW), 2);
                textzeilen(Component.translatable("stinkendreich.spotify.schritt1"), 1);
                textzeilen(Component.translatable("stinkendreich.spotify.schritt2", SpotifyKlient.redirectUri()), 2);
                textzeilen(Component.translatable("stinkendreich.spotify.schritt3"), 1);
                weiter(2);
                textzeilen(Component.translatable("stinkendreich.spotify.premium").withStyle(ChatFormatting.GRAY), 3);
                weiter(4);
                zwei(Component.translatable("stinkendreich.spotify.anleitung"), () -> KompatClient.oeffneLink(URI.create(ANLEITUNG)),
                        Component.translatable("gui.done"), this::schliessen);
            }
            case GETRENNT -> {
                textzeilen(Component.translatable("stinkendreich.spotify.premium").withStyle(ChatFormatting.GRAY), 3);
                weiter(4);
                fehlerZeile();
                knopf(Component.translatable("stinkendreich.spotify.verbinden").withStyle(ChatFormatting.GREEN), SpotifyKlient::verbinden);
                knopf(Component.translatable("gui.done"), this::schliessen);
            }
            case WARTET -> {
                textzeilen(Component.translatable("stinkendreich.spotify.warten").withStyle(ChatFormatting.YELLOW), 2);
                weiter(4);
                knopf(Component.translatable("gui.cancel"), SpotifyKlient::abbrechen);
                knopf(Component.translatable("gui.done"), this::schliessen);
            }
            case VERBUNDEN -> verbunden();
        }
    }

    private void verbunden() {
        if (!SpotifyKlient.titel().isEmpty()) {
            textzeilen(Component.literal("♪ " + SpotifyKlient.titel()).withStyle(ChatFormatting.GOLD), 1);
            String unten = SpotifyKlient.interpret() + (SpotifyKlient.geraet().isEmpty() ? "" : " · " + SpotifyKlient.geraet());
            textzeilen(Component.literal(unten).withStyle(ChatFormatting.GRAY), 1);
        } else {
            textzeilen(Component.translatable(SpotifyKlient.geraetDa() ? "stinkendreich.spotify.nichts" : "stinkendreich.spotify.kein_geraet")
                    .withStyle(ChatFormatting.GRAY), 2);
        }
        fehlerZeile();
        weiter(2);

        boolean spielt = SpotifyKlient.spielt();
        drei(Component.translatable("stinkendreich.musik.zurueck"), () -> SpotifyKlient.zurueck(pos),
                Component.translatable(spielt ? "stinkendreich.spotify.pause" : "stinkendreich.musik.abspielen"),
                () -> {
                    if (spielt) {
                        SpotifyKlient.pause(pos);
                    } else {
                        SpotifyKlient.spielen(pos);
                    }
                },
                Component.translatable("stinkendreich.musik.weiter"), () -> SpotifyKlient.weiter(pos), true);

        if (SpotifyKlient.lautstaerke() >= 0) {
            addRenderableWidget(new Regler(links(), aktuellesY(), BREITE, "stinkendreich.spotify.lautstaerke", SpotifyKlient.lautstaerke(), wert -> {
                spotifyWert = wert;
                spotifyTicks = 8;
            }));
            weiter(ZEILE);
        }

        // Eigene Playlists
        List<SpotifyKlient.Playlist> listen = SpotifyKlient.playlists();
        if (!SpotifyKlient.playlistsGeladen()) {
            textzeilen(Component.translatable("stinkendreich.spotify.laedt").withStyle(ChatFormatting.DARK_GRAY), 1);
        } else if (listen.isEmpty()) {
            textzeilen(Component.translatable("stinkendreich.spotify.keine_playlists").withStyle(ChatFormatting.DARK_GRAY), 1);
        } else {
            textzeilen(Component.translatable("stinkendreich.spotify.playlists").withStyle(ChatFormatting.WHITE), 1);
            int seiten = (listen.size() + TITEL_JE_SEITE - 1) / TITEL_JE_SEITE;
            playlistSeite = Math.max(0, Math.min(playlistSeite, seiten - 1));
            for (int i = playlistSeite * TITEL_JE_SEITE; i < Math.min(listen.size(), (playlistSeite + 1) * TITEL_JE_SEITE); i++) {
                SpotifyKlient.Playlist p = listen.get(i);
                String name = kuerzen(p.name() + (p.titel() >= 0 ? " (" + p.titel() + ")" : ""), BREITE - 12);
                addRenderableWidget(Button.builder(Component.literal(name), b -> SpotifyKlient.playlistStarten(pos, p))
                        .bounds(links(), aktuellesY(), BREITE, HOEHE).build());
                weiter(ZEILE);
            }
            if (seiten > 1) {
                blaettern(playlistSeite, seiten, s -> playlistSeite = s);
            }
        }
        zwei(Component.translatable("stinkendreich.spotify.trennen"), SpotifyKlient::trennen,
                Component.translatable("gui.done"), this::schliessen);
    }

    private void fehlerZeile() {
        Component f = SpotifyKlient.fehler();
        if (f != null) {
            textzeilen(f.copy().withStyle(ChatFormatting.RED), 3);
        }
    }

    // ---------------- Bausteine ----------------

    private int links() {
        return width / 2 - BREITE / 2;
    }

    /** Zeile mit „◀ Seite x/y ▶“. */
    private void blaettern(int aktuell, int seiten, IntConsumer setzen) {
        int y = aktuellesY();
        Button zurueck = addRenderableWidget(Button.builder(Component.literal("◀"), b -> {
            setzen.accept(aktuell - 1);
            rebuildWidgets();
        }).bounds(links(), y, 30, HOEHE).build());
        Button vor = addRenderableWidget(Button.builder(Component.literal("▶"), b -> {
            setzen.accept(aktuell + 1);
            rebuildWidgets();
        }).bounds(links() + BREITE - 30, y, 30, HOEHE).build());
        zurueck.active = aktuell > 0;
        vor.active = aktuell < seiten - 1;
        Component text = Component.translatable("stinkendreich.musik.seite", aktuell + 1, seiten);
        StringWidget w = new StringWidget(text, font);
        w.setX(width / 2 - font.width(text) / 2);
        w.setY(y + 6);
        addRenderableWidget(w);
        weiter(ZEILE);
    }

    private void zwei(Component links, Runnable aktionLinks, Component rechts, Runnable aktionRechts) {
        int y = aktuellesY();
        addRenderableWidget(Button.builder(links, b -> aktionLinks.run()).bounds(links(), y, 98, HOEHE).build());
        addRenderableWidget(Button.builder(rechts, b -> aktionRechts.run()).bounds(links() + BREITE - 98, y, 98, HOEHE).build());
        weiter(ZEILE + 2);
    }

    private void drei(Component a, Runnable aktionA, Component b, Runnable aktionB, Component c, Runnable aktionC, boolean aktiv) {
        int y = aktuellesY();
        int breite = (BREITE - 8) / 3;
        Button[] knoepfe = {
                Button.builder(a, k -> aktionA.run()).bounds(links(), y, breite, HOEHE).build(),
                Button.builder(b, k -> aktionB.run()).bounds(links() + breite + 4, y, breite, HOEHE).build(),
                Button.builder(c, k -> aktionC.run()).bounds(links() + BREITE - breite, y, breite, HOEHE).build()};
        for (Button k : knoepfe) {
            k.active = aktiv;
            addRenderableWidget(k);
        }
        weiter(ZEILE + 2);
    }

    /** Text mit Zeilenumbruch (höchstens {@code maxZeilen}), mittig. */
    private void textzeilen(MutableComponent text, int maxZeilen) {
        int breite = Math.min(width - 20, 300);
        List<String> zeilen = umbrechen(text.getString(), breite);
        for (int i = 0; i < Math.min(maxZeilen, zeilen.size()); i++) {
            String z = i == maxZeilen - 1 && zeilen.size() > maxZeilen ? kuerzen(zeilen.get(i) + " …", breite) : zeilen.get(i);
            Component c = Component.literal(z).withStyle(text.getStyle());
            StringWidget w = new StringWidget(c, font);
            w.setX(width / 2 - font.width(c) / 2);
            w.setY(aktuellesY());
            addRenderableWidget(w);
            weiter(11);
        }
    }

    private List<String> umbrechen(String text, int breite) {
        List<String> zeilen = new ArrayList<>();
        StringBuilder zeile = new StringBuilder();
        for (String wort : text.split(" ")) {
            String versuch = zeile.isEmpty() ? wort : zeile + " " + wort;
            if (font.width(versuch) > breite && !zeile.isEmpty()) {
                zeilen.add(zeile.toString());
                zeile = new StringBuilder(wort);
            } else {
                zeile = new StringBuilder(versuch);
            }
        }
        if (!zeile.isEmpty()) {
            zeilen.add(zeile.toString());
        }
        return zeilen;
    }

    private String kuerzen(String text, int breite) {
        if (font.width(text) <= breite) {
            return text;
        }
        String t = text;
        while (!t.isEmpty() && font.width(t + "…") > breite) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "…";
    }

    /** Alles, was einen Neuaufbau verlangt (ohne Lautstärke, damit der Regler beim Ziehen bleibt). */
    private String stand(MusikanlageBlockEntity be) {
        return spotifyReiter + "|" + be.spielt() + "|" + be.quelle() + "|" + be.titel() + "|" + be.djName() + "|" + be.zufall() + "|"
                + be.wiederholen() + "|" + MusikDateien.liste().hashCode() + "|" + MusikKlient.hoerbar(pos)
                + (spotifyReiter ? "|" + SpotifyKlient.stand() : "");
    }

    @Override
    public void tick() {
        super.tick();
        MusikanlageBlockEntity be = anlage();
        if (be == null) {
            schliessen();
            return;
        }
        SpotifyKlient.menueOffen();
        // Regler erst nach dem Loslassen bzw. kurzer Pause senden
        if (lautstaerkeTicks > 0 && --lautstaerkeTicks == 0) {
            KompatClient.sende("musik", pos, "lautstaerke|" + lautstaerkeWert);
        }
        if (spotifyTicks > 0 && --spotifyTicks == 0) {
            SpotifyKlient.setzeLautstaerke(spotifyWert);
        }
        if (!stand.equals(stand(be)) && lautstaerkeTicks == 0 && spotifyTicks == 0) {
            rebuildWidgets();
        }
    }

    /** Schieberegler 0–100 %. */
    private static final class Regler extends AbstractSliderButton {
        private final String schluessel;
        private final IntConsumer aenderung;

        Regler(int x, int y, int breite, String schluessel, int prozent, IntConsumer aenderung) {
            super(x, y, breite, HOEHE, Component.empty(), prozent / 100.0);
            this.schluessel = schluessel;
            this.aenderung = aenderung;
            updateMessage();
        }

        private int prozent() {
            return (int) Math.round(value * 100);
        }

        @Override
        protected void updateMessage() {
            if (schluessel != null) {
                setMessage(Component.translatable(schluessel, prozent()));
            }
        }

        @Override
        protected void applyValue() {
            if (aenderung != null) {
                aenderung.accept(prozent());
            }
        }
    }
}
