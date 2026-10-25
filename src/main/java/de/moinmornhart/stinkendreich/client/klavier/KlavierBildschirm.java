package de.moinmornhart.stinkendreich.client.klavier;

import de.moinmornhart.stinkendreich.client.KompatClient;
import de.moinmornhart.stinkendreich.klavier.Klavier;
import de.moinmornhart.stinkendreich.klavier.Lieder;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else
/*import net.minecraft.client.gui.GuiGraphics;*/
//? if >=1.21.9 {
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?}

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Spielfenster des Konzertflügels: drei Oktaven (C3 … C6) zum Anklicken oder mit der Computertastatur.
 * Untere Oktave: Y X C V B N M (weiß), S D G H J (schwarz); mittlere: Q W E R T Z U und 2 3 5 6 7;
 * obere: I O P Ü + und 9 0 ´. Oben Knöpfe für die Stücke, die der Flügel selbst spielt. Das Spiel läuft weiter,
 * damit die Töne vom Server kommen und alle in der Nähe sie hören.
 */
public class KlavierBildschirm extends Screen {
    private static final int WEISS_B = 16, WEISS_H = 72, SCHWARZ_B = 10, SCHWARZ_H = 44;
    private static final boolean[] IST_SCHWARZ = {false, true, false, true, false, false, true, false, true, false, true, false};

    /** Taste der Computertastatur (Tastencode, Lage wie auf US-Tastaturen) → MIDI-Nummer und Aufschrift (deutsch). */
    private static final Map<Integer, Integer> TASTEN = new HashMap<>();
    private static final Map<Integer, String> AUFSCHRIFT = new HashMap<>();

    static {
        belegen(48, new int[]{InputConstants.KEY_Z, InputConstants.KEY_S, InputConstants.KEY_X, InputConstants.KEY_D, InputConstants.KEY_C, InputConstants.KEY_V,
                InputConstants.KEY_G, InputConstants.KEY_B, InputConstants.KEY_H, InputConstants.KEY_N, InputConstants.KEY_J, InputConstants.KEY_M},
                new String[]{"Y", "S", "X", "D", "C", "V", "G", "B", "H", "N", "J", "M"});
        belegen(60, new int[]{InputConstants.KEY_Q, InputConstants.KEY_2, InputConstants.KEY_W, InputConstants.KEY_3, InputConstants.KEY_E, InputConstants.KEY_R,
                InputConstants.KEY_5, InputConstants.KEY_T, InputConstants.KEY_6, InputConstants.KEY_Y, InputConstants.KEY_7, InputConstants.KEY_U},
                new String[]{"Q", "2", "W", "3", "E", "R", "5", "T", "6", "Z", "7", "U"});
        belegen(72, new int[]{InputConstants.KEY_I, InputConstants.KEY_9, InputConstants.KEY_O, InputConstants.KEY_0, InputConstants.KEY_P,
                InputConstants.KEY_LBRACKET, InputConstants.KEY_EQUALS, InputConstants.KEY_RBRACKET},
                new String[]{"I", "9", "O", "0", "P", "Ü", "´", "+"});
    }

    private static void belegen(int ab, int[] codes, String[] schrift) {
        for (int i = 0; i < codes.length; i++) {
            TASTEN.put(codes[i], ab + i);
            AUFSCHRIFT.put(ab + i, schrift[i]);
        }
    }

    private final BlockPos fluegel;
    /** Wann eine Taste zuletzt angeschlagen wurde (zum Aufleuchten), MIDI → Millisekunden. */
    private final Map<Integer, Long> angeschlagen = new HashMap<>();
    /** Gerade gehaltene Computertasten (damit gehaltene Tasten nicht wiederholt anschlagen). */
    private final Set<Integer> gehalten = new HashSet<>();

    public KlavierBildschirm(BlockPos fluegel) {
        super(Component.translatable("block.stinkendreich.konzertfluegel"));
        this.fluegel = fluegel;
    }

    @Override
    protected void init() {
        int knopfB = 100, abstand = 4;
        int gesamt = (Lieder.NAMEN.length + 1) * knopfB + Lieder.NAMEN.length * abstand;
        int x = width / 2 - gesamt / 2, y = tastenY() - 34;
        for (String lied : Lieder.NAMEN) {
            addRenderableWidget(Button.builder(Component.translatable("stinkendreich.klavier.lied." + lied),
                    b -> KompatClient.sende("klavier", fluegel, "l|" + lied)).bounds(x, y, knopfB, 20).build());
            x += knopfB + abstand;
        }
        addRenderableWidget(Button.builder(Component.translatable("stinkendreich.klavier.stopp"),
                b -> KompatClient.sende("klavier", fluegel, "s")).bounds(x, y, knopfB, 20).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------
    // Klaviatur
    // ------------------------------------------------------------------

    private static int weisseTasten() {
        int n = 0;
        for (int note = Klavier.TIEFSTE; note <= Klavier.HOECHSTE; note++) {
            n += IST_SCHWARZ[note % 12] ? 0 : 1;
        }
        return n;
    }

    private int tastenX() {
        return width / 2 - weisseTasten() * WEISS_B / 2;
    }

    private int tastenY() {
        return height / 2 - 10;
    }

    /** Linke Kante einer Taste; bei schwarzen Tasten mittig über der Fuge zur vorigen weißen. */
    private int linkeKante(int note) {
        int weisse = 0;
        for (int n = Klavier.TIEFSTE; n < note; n++) {
            weisse += IST_SCHWARZ[n % 12] ? 0 : 1;
        }
        return IST_SCHWARZ[note % 12] ? tastenX() + weisse * WEISS_B - SCHWARZ_B / 2 : tastenX() + weisse * WEISS_B;
    }

    /** Welche Taste liegt unter dem Mauszeiger (schwarze zuerst, sie liegen oben)? -1 = keine. */
    private int tasteBei(double mx, double my) {
        int y0 = tastenY();
        for (int note = Klavier.TIEFSTE; note <= Klavier.HOECHSTE; note++) {
            int x = linkeKante(note);
            if (IST_SCHWARZ[note % 12] && mx >= x && mx < x + SCHWARZ_B && my >= y0 && my < y0 + SCHWARZ_H) {
                return note;
            }
        }
        for (int note = Klavier.TIEFSTE; note <= Klavier.HOECHSTE; note++) {
            int x = linkeKante(note);
            if (!IST_SCHWARZ[note % 12] && mx >= x && mx < x + WEISS_B && my >= y0 && my < y0 + WEISS_H) {
                return note;
            }
        }
        return -1;
    }

    private void anschlagen(int note) {
        angeschlagen.put(note, System.currentTimeMillis());
        KompatClient.sende("klavier", fluegel, "t|" + note);
    }

    private boolean leuchtet(int note) {
        Long zeit = angeschlagen.get(note);
        return zeit != null && System.currentTimeMillis() - zeit < 200;
    }

    private boolean klick(double mx, double my, int knopf) {
        int note = knopf == 0 ? tasteBei(mx, my) : -1;
        if (note >= 0) {
            anschlagen(note);
            return true;
        }
        return false;
    }

    private boolean taste(int code) {
        Integer note = TASTEN.get(code);
        if (note == null) {
            return false;
        }
        if (gehalten.add(code)) {
            anschlagen(note);
        }
        return true;
    }

    //? if >=1.21.9 {
    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doppelt) {
        return klick(e.x(), e.y(), e.button()) || super.mouseClicked(e, doppelt);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        return taste(e.key()) || super.keyPressed(e);
    }

    @Override
    public boolean keyReleased(KeyEvent e) {
        gehalten.remove(e.key());
        return super.keyReleased(e);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mx, double my, int knopf) {
        return klick(mx, my, knopf) || super.mouseClicked(mx, my, knopf);
    }

    @Override
    public boolean keyPressed(int code, int scan, int mod) {
        return taste(code) || super.keyPressed(code, scan, mod);
    }

    @Override
    public boolean keyReleased(int code, int scan, int mod) {
        gehalten.remove(code);
        return super.keyReleased(code, scan, mod);
    }
    *///?}

    // ------------------------------------------------------------------
    // Zeichnen (ab 26.1 heißt die Zeichenfläche GuiGraphicsExtractor)
    // ------------------------------------------------------------------

    //? if >=26.1 {
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        zeichne(g, mx, my);
    }

    private void zeichne(GuiGraphicsExtractor g, int mx, int my) {
    //?} else {
    /*@Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        super.render(g, mx, my, delta);
        zeichne(g, mx, my);
    }

    private void zeichne(GuiGraphics g, int mx, int my) {
    *///?}
        int x0 = tastenX(), y0 = tastenY(), breite = weisseTasten() * WEISS_B;
        int unter = tasteBei(mx, my);
        // Rahmen in schwarzem Lack mit Goldkante
        g.fill(x0 - 8, y0 - 8, x0 + breite + 8, y0 + WEISS_H + 8, 0xFF111114);
        g.fill(x0 - 6, y0 - 6, x0 + breite + 6, y0 - 5, 0xFFE8BA34);
        for (int note = Klavier.TIEFSTE; note <= Klavier.HOECHSTE; note++) {
            if (IST_SCHWARZ[note % 12]) {
                continue;
            }
            int x = linkeKante(note);
            int farbe = leuchtet(note) ? 0xFFF5D76E : note == unter ? 0xFFE6E6E0 : 0xFFF8F8F4;
            g.fill(x, y0, x + WEISS_B - 1, y0 + WEISS_H, farbe);
            g.fill(x + WEISS_B - 1, y0, x + WEISS_B, y0 + WEISS_H, 0xFF9A9A96);
            schrift(g, AUFSCHRIFT.getOrDefault(note, ""), x + WEISS_B / 2, y0 + WEISS_H - 11, 0xFF55555A);
            if (note % 12 == 0) {
                schrift(g, "C" + (note / 12 - 1), x + WEISS_B / 2, y0 + WEISS_H + 10, 0xFFE8BA34);
            }
        }
        for (int note = Klavier.TIEFSTE; note <= Klavier.HOECHSTE; note++) {
            if (!IST_SCHWARZ[note % 12]) {
                continue;
            }
            int x = linkeKante(note);
            int farbe = leuchtet(note) ? 0xFFC9A227 : note == unter ? 0xFF3A3A40 : 0xFF141417;
            g.fill(x, y0, x + SCHWARZ_B, y0 + SCHWARZ_H, farbe);
            schrift(g, AUFSCHRIFT.getOrDefault(note, ""), x + SCHWARZ_B / 2, y0 + SCHWARZ_H - 11, 0xFFBBBBC0);
        }
        schrift(g, title.getString(), width / 2, y0 - 50, 0xFFFFFFFF);
        schrift(g, Component.translatable("stinkendreich.klavier.hinweis").getString(), width / 2, y0 + WEISS_H + 24, 0xFFCCCCCC);
    }

    /** Text mittig an x. */
    //? if >=26.1 {
    private void schrift(GuiGraphicsExtractor g, String text, int x, int y, int farbe) {
        g.text(font, text, x - font.width(text) / 2, y, farbe, false);
    }
    //?} else {
    /*private void schrift(GuiGraphics g, String text, int x, int y, int farbe) {
        g.drawString(font, text, x - font.width(text) / 2, y, farbe, false);
    }
    *///?}
}
