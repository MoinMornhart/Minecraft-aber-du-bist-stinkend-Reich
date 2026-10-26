package de.moinmornhart.stinkendreich.client.handy;

import de.moinmornhart.stinkendreich.client.KompatClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else
/*import net.minecraft.client.gui.GuiGraphics;*/

import java.util.List;

/**
 * Das Handy: ein goldenes Smartphone mit Apps. Startseite: James anrufen (Limo oder Hubschrauber), Navigation
 * (James holt dich ab und fährt dich hin; unter „Meine Orte“ eigene Ziele speichern, die James dann auch im Chat
 * kennt), Lieferservice (Tiere und Waren), Aufträge (wie im Chat), Mitarbeiter bestellen, Bodyguards rufen oder
 * heimschicken. Jede Taste schickt eine Nachricht „handy“ an den Server.
 */
public class HandyBildschirm extends Screen {
    private static final int BREITE = 160, HOEHE = 270;
    private static final int KNOPF_B = 136, KNOPF_H = 18, ABSTAND = 3;

    private enum Seite { START, NAVI, ORTE, LIEFERUNG, AUFTRAG, SMART }

    /** Höchstens so viele eigene Orte passen auf eine Seite. */
    private static final int ORTE_MAX = 7;
    /** Die Orte, die James kennt (schickt der Server, siehe {@link #orteEmpfangen}). */
    private static List<String> orte = List.of();

    private Seite seite = Seite.START;
    private EditBox eingabe;

    public HandyBildschirm() {
        super(Component.translatable("item.stinkendreich.handy"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int links() {
        return width / 2 - BREITE / 2;
    }

    private int oben() {
        return height / 2 - HOEHE / 2;
    }

    @Override
    protected void init() {
        int x = links() + (BREITE - KNOPF_B) / 2;
        int y = oben() + 40;
        switch (seite) {
            case START -> {
                y = knopf(x, y, "anruf_limo", () -> senden("anruf|limo"));
                y = knopf(x, y, "anruf_heli", () -> senden("anruf|heli"));
                y = knopf(x, y, "navi", () -> wechseln(Seite.NAVI));
                y = knopf(x, y, "lieferung", () -> wechseln(Seite.LIEFERUNG));
                y = knopf(x, y, "auftraege", () -> wechseln(Seite.AUFTRAG));
                y = knopf(x, y, "mitarbeiter", () -> senden("mitarbeiter"));
                y = knopf(x, y, "bodyguards", () -> senden("bodyguards|rufen"));
                y = knopf(x, y, "bodyguards_heim", () -> senden("bodyguards|heim"));
                knopf(x, y, "smarthome", () -> wechseln(Seite.SMART));
            }
            case NAVI -> {
                for (String ziel : List.of("villa", "dorf", "endportal", "netherportal", "mine")) {
                    String befehl = ziel.equals("villa") ? "#villa" : ziel;
                    y = knopf(x, y, "navi." + ziel, () -> senden("fahr|" + befehl));
                }
                y += 4;
                knopf(x, y, "orte", () -> wechseln(Seite.ORTE));
                knopf(x, oben() + HOEHE - 30, "zurueck", () -> wechseln(Seite.START));
            }
            case ORTE -> {
                // Gespeicherte Orte: Name = hinfahren, ✕ = löschen
                List<String> liste = orte.size() > ORTE_MAX ? orte.subList(0, ORTE_MAX) : orte;
                for (String ort : liste) {
                    addRenderableWidget(Button.builder(Component.literal("➜ " + ort.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + ort.substring(1)),
                            b -> senden("fahr|" + ort))
                            .bounds(x, y, KNOPF_B - 22, KNOPF_H).build());
                    addRenderableWidget(Button.builder(Component.literal("✕"), b -> anfragen("ort|vergessen|" + ort))
                            .bounds(x + KNOPF_B - 20, y, 20, KNOPF_H).build());
                    y += KNOPF_H + ABSTAND;
                }
                if (orte.isEmpty()) {
                    y += 14;    // Platz für den Hinweis „noch keine Orte“
                }
                eingabe = new EditBox(font, x, Math.max(y + 4, oben() + 192), KNOPF_B, KNOPF_H, Component.translatable("stinkendreich.handy.orte.name"));
                eingabe.setHint(Component.translatable("stinkendreich.handy.orte.name"));
                eingabe.setMaxLength(32);
                addRenderableWidget(eingabe);
                knopf(x, Math.max(y + 4, oben() + 192) + KNOPF_H + 4, "orte.speichern", () -> {
                    if (!eingabe.getValue().isBlank()) {
                        anfragen("ort|merken|" + eingabe.getValue().trim());
                        eingabe.setValue("");
                    }
                });
                knopf(x, oben() + HOEHE - 30, "zurueck", () -> wechseln(Seite.NAVI));
            }
            case LIEFERUNG -> {
                for (String was : de.moinmornhart.stinkendreich.handy.Lieferservice.ANGEBOT) {
                    y = knopf(x, y, "liefer." + was, () -> senden("liefer|" + was), KNOPF_H - 3) - 1;
                }
                knopf(x, oben() + HOEHE - 30, "zurueck", () -> wechseln(Seite.START));
            }
            case SMART -> {
                // Smart Home: je Zeile zwei Knöpfe (an/aus), dazu acht Lichtfarben; das Handy bleibt dabei offen
                int halb = (KNOPF_B - 4) / 2;
                for (String[] zeile : new String[][]{{"rollo", "zu", "auf"}, {"bruecke", "zu", "auf"}, {"licht", "an", "aus"},
                        {"disco", "an", "aus"}, {"automatik", "an", "aus"}}) {
                    addRenderableWidget(Button.builder(Component.translatable("stinkendreich.handy.smart." + zeile[0] + "." + zeile[1]),
                            b -> anfragen("smart|" + zeile[0] + "|" + zeile[1])).bounds(x, y, halb, KNOPF_H).build());
                    addRenderableWidget(Button.builder(Component.translatable("stinkendreich.handy.smart." + zeile[0] + "." + zeile[2]),
                            b -> anfragen("smart|" + zeile[0] + "|" + zeile[2])).bounds(x + halb + 4, y, halb, KNOPF_H).build());
                    y += KNOPF_H + ABSTAND;
                }
                int fx = x, fb = (KNOPF_B - 7 * 2) / 8;
                for (var f : de.moinmornhart.stinkendreich.mechanik.Eigenschaften.Lichtfarbe.values()) {
                    String name = f.getSerializedName();
                    Button knopf = Button.builder(Component.literal("■").withStyle(s -> s.withColor(FARBEN.get(name))),
                            b -> anfragen("smart|farbe|" + name)).bounds(fx, y, fb, KNOPF_H).build();
                    knopf.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.translatable("stinkendreich.smarthome.farbe." + name)));
                    addRenderableWidget(knopf);
                    fx += fb + 2;
                }
                knopf(x, oben() + HOEHE - 30, "zurueck", () -> wechseln(Seite.START));
            }
            case AUFTRAG -> {
                for (String a : List.of("eisen", "diamanten", "holz", "essen", "sortieren", "heim")) {
                    String text = Component.translatable("stinkendreich.handy.auftrag." + a + ".text").getString();
                    y = knopf(x, y, "auftrag." + a, () -> senden("auftrag|" + text));
                }
                eingabe = new EditBox(font, x, y + 4, KNOPF_B, KNOPF_H, Component.translatable("stinkendreich.handy.auftrag.frei"));
                eingabe.setHint(Component.translatable("stinkendreich.handy.auftrag.frei"));
                eingabe.setMaxLength(120);
                addRenderableWidget(eingabe);
                y += KNOPF_H + 8;
                knopf(x, y, "auftrag.senden", () -> {
                    if (!eingabe.getValue().isBlank()) {
                        senden("auftrag|" + eingabe.getValue().trim());
                    }
                });
                knopf(x, oben() + HOEHE - 30, "zurueck", () -> wechseln(Seite.START));
            }
        }
    }

    /** Farben der Farbknöpfe in der Smart-Home-App (wie die Glasschirme der Lampen). */
    private static final java.util.Map<String, Integer> FARBEN = java.util.Map.of("weiss", 0xF4F4F4, "warm", 0xFFD58A,
            "rot", 0xFF3B30, "gruen", 0x3BE36A, "blau", 0x3B82FF, "lila", 0xA45BFF, "pink", 0xFF5BC8, "gold", 0xF2C230);

    private int knopf(int x, int y, String name, Runnable aktion) {
        return knopf(x, y, name, aktion, KNOPF_H);
    }

    private int knopf(int x, int y, String name, Runnable aktion, int hoehe) {
        addRenderableWidget(Button.builder(Component.translatable("stinkendreich.handy." + name), b -> aktion.run())
                .bounds(x, y, KNOPF_B, hoehe).build());
        return y + hoehe + ABSTAND;
    }

    private void wechseln(Seite neu) {
        seite = neu;
        eingabe = null;
        if (neu == Seite.ORTE) {
            anfragen("orte");
        }
        rebuildWidgets();
    }

    /** Schickt eine Aktion an den Server, ohne das Handy zu schließen (die Antwort kommt als neue Ortsliste). */
    private void anfragen(String wert) {
        Minecraft mc = Minecraft.getInstance();
        KompatClient.sende("handy", mc.player != null ? mc.player.blockPosition() : BlockPos.ZERO, wert);
    }

    /** Neue Ortsliste vom Server (Namen zeilenweise); ein offenes Handy zeigt sie sofort. */
    public static void orteEmpfangen(String zeilen) {
        orte = zeilen.isBlank() ? List.of() : List.of(zeilen.split("\n"));
        if (KompatClient.offenerBildschirm() instanceof HandyBildschirm h && h.seite == Seite.ORTE) {
            String text = h.eingabe != null ? h.eingabe.getValue() : "";
            h.rebuildWidgets();
            if (h.eingabe != null) {
                h.eingabe.setValue(text);
            }
        }
    }

    /** Schickt die Aktion an den Server und schließt das Handy (wie Auflegen). */
    private void senden(String wert) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos pos = mc.player != null ? mc.player.blockPosition() : BlockPos.ZERO;
        KompatClient.sende("handy", pos, wert);
        onClose();
    }

    // ------------------------------------------------------------------
    // Zeichnen: goldener Rahmen, dunkler Bildschirm, Statusleiste mit Uhrzeit
    // ------------------------------------------------------------------

    // Das Gehäuse gehört zum Hintergrund (sonst läge die Abdunklung darüber), die Schrift kommt nach den Knöpfen
    //? if >=26.1 {
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractBackground(g, mx, my, delta);
        gehaeuse(g);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        beschriftung(g);
    }

    private void gehaeuse(GuiGraphicsExtractor g) {
    //?} else {
    /*@Override
    public void renderBackground(GuiGraphics g, int mx, int my, float delta) {
        super.renderBackground(g, mx, my, delta);
        gehaeuse(g);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        super.render(g, mx, my, delta);
        beschriftung(g);
    }

    private void gehaeuse(GuiGraphics g) {
    *///?}
        int x = links(), y = oben();
        g.fill(x - 3, y - 3, x + BREITE + 3, y + HOEHE + 3, 0xFFB8891E);          // Goldrahmen
        g.fill(x - 2, y - 2, x + BREITE + 2, y + HOEHE + 2, 0xFFE8BA34);
        g.fill(x, y, x + BREITE, y + HOEHE, 0xFF0E0F14);                          // Gehäuse
        g.fill(x + 5, y + 12, x + BREITE - 5, y + HOEHE - 12, 0xFF1B2033);        // Bildschirm
        g.fill(x + 5, y + 12, x + BREITE - 5, y + 24, 0xFF11131D);                // Statusleiste
        g.fill(x + BREITE / 2 - 16, y + 4, x + BREITE / 2 + 16, y + 7, 0xFF2A2C35);    // Lautsprecher
        g.fill(x + BREITE / 2 - 12, y + HOEHE - 8, x + BREITE / 2 + 12, y + HOEHE - 6, 0xFF3A3C45); // Leiste unten
    }

    //? if >=26.1 {
    private void beschriftung(GuiGraphicsExtractor g) {
    //?} else
    /*private void beschriftung(GuiGraphics g) {*/
        int x = links(), y = oben();
        schrift(g, uhrzeit(), x + 10, y + 14, 0xFFFFFFFF, false);
        schrift(g, "▮▮▮ 100%", x + BREITE - 10 - font.width("▮▮▮ 100%"), y + 14, 0xFF8BE38B, false);
        String titel = Component.translatable("stinkendreich.handy.titel." + seite.name().toLowerCase(java.util.Locale.ROOT)).getString();
        schrift(g, titel, x + BREITE / 2, y + 28, 0xFFE8BA34, true);
        if (seite == Seite.ORTE && orte.isEmpty()) {
            schrift(g, Component.translatable("stinkendreich.handy.orte.leer").getString(), x + BREITE / 2, y + 46, 0xFFB8BCC8, true);
        }
    }

    /** Uhrzeit der Spielwelt (6000 Ticks = 12:00 Uhr). */
    private static String uhrzeit() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return "";
        }
        //? if >=26.1 {
        long t = (mc.level.getDefaultClockTime() + 6000) % 24000;
        //?} else
        /*long t = (mc.level.getDayTime() + 6000) % 24000;*/
        return String.format("%02d:%02d", t / 1000, (t % 1000) * 60 / 1000);
    }

    //? if >=26.1 {
    private void schrift(GuiGraphicsExtractor g, String text, int x, int y, int farbe, boolean mittig) {
        g.text(font, text, mittig ? x - font.width(text) / 2 : x, y, farbe, false);
    }
    //?} else {
    /*private void schrift(GuiGraphics g, String text, int x, int y, int farbe, boolean mittig) {
        g.drawString(font, text, mittig ? x - font.width(text) / 2 : x, y, farbe, false);
    }
    *///?}
}
