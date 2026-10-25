package de.moinmornhart.stinkendreich.register;

import net.minecraft.sounds.SoundEvent;

/** Eigene Geräusche der Mod (selbst erzeugt, siehe tools/assets/sounds.py). */
public final class ModSounds {
    public static final SoundEvent PIEP_OK = ModRegister.sound("piep_ok");
    public static final SoundEvent PIEP_FEHLER = ModRegister.sound("piep_fehler");
    public static final SoundEvent SIRENE = ModRegister.sound("sirene");
    public static final SoundEvent GONG = ModRegister.sound("gong");
    public static final SoundEvent KLINGEL = ModRegister.sound("klingel");
    public static final SoundEvent TRESOR = ModRegister.sound("tresor");
    public static final SoundEvent GLOECKCHEN = ModRegister.sound("gloeckchen");
    // Bonzenauto
    public static final SoundEvent MOTOR = ModRegister.sound("motor");
    public static final SoundEvent ROTOR = ModRegister.sound("rotor");
    public static final SoundEvent ANLASSER = ModRegister.sound("anlasser");
    public static final SoundEvent HUPE = ModRegister.sound("hupe");
    public static final SoundEvent REIFEN = ModRegister.sound("reifen");
    public static final SoundEvent CRASH = ModRegister.sound("crash");
    // Aufzug
    public static final SoundEvent AUFZUG_MUSIK = ModRegister.sound("aufzug_musik");
    // Agenten-Waffen
    public static final SoundEvent SCHUSS = ModRegister.sound("schuss");
    public static final SoundEvent LASER = ModRegister.sound("laser");
    public static final SoundEvent GELDREGEN = ModRegister.sound("geldregen");
    // Bad
    public static final SoundEvent TOILETTE = ModRegister.sound("toilette");
    // Konzertflügel: Grundtöne C2, C3, C4, C5, C6 (MIDI 36, 48, 60, 72, 84)
    public static final SoundEvent[] KLAVIER = {ModRegister.sound("klavier_c2"), ModRegister.sound("klavier_c3"),
            ModRegister.sound("klavier_c4"), ModRegister.sound("klavier_c5"), ModRegister.sound("klavier_c6")};

    private ModSounds() {
    }

    public static void init() {
        // Laden der Klasse registriert die Geräusche
    }
}
