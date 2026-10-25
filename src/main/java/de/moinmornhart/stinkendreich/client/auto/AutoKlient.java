package de.moinmornhart.stinkendreich.client.auto;

import com.mojang.blaze3d.platform.InputConstants;
import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.auto.AutoEntity;
import de.moinmornhart.stinkendreich.client.KompatClient;
import de.moinmornhart.stinkendreich.mechanik.KlientBruecke;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;

/**
 * Alles, was der Fahrer auf seinem Rechner braucht: Tasten (H Hupe, L Licht, V Kamera,
 * Leertaste Handbremse), Tacho über der Schnellleiste, Kamera beim Einsteigen und Motorgeräusche.
 */
public final class AutoKlient {
    private static KeyMapping hupe, licht, kamera;
    /** Kameraabstand der Verfolgerkamera (Blöcke); 0 = Fahrersicht. */
    public static float kameraAbstand = 7.0F;
    private static final float[] ABSTAENDE = {7.0F, 0.0F, 11.0F};
    private static int kameraModus;
    private static boolean sassImAuto;
    private static CameraType vorherigeKamera;
    private static int hinweisTicks;
    private static final Map<Integer, MotorGeraeusch> MOTOREN = new HashMap<>();

    private AutoKlient() {
    }

    public static void registrieren() {
        hupe = anmelden(taste("hupe", InputConstants.KEY_H));
        licht = anmelden(taste("licht", InputConstants.KEY_G)); // L öffnet in Minecraft die Fortschritte
        kamera = anmelden(taste("kamera", InputConstants.KEY_V));
        KlientBruecke.handbremse = () -> Minecraft.getInstance().options.keyJump.isDown();
        KlientBruecke.sinken = () -> Minecraft.getInstance().options.keySprint.isDown();
        ClientTickEvents.END_CLIENT_TICK.register(AutoKlient::tick);
    }

    /** Meldet eine Taste bei Fabric an (die Hilfsklasse heißt ab 26.1 anders). */
    private static KeyMapping anmelden(KeyMapping taste) {
        //? if >=26.1 {
        return net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(taste);
        //?} else
        /*return net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(taste);*/
    }

    private static KeyMapping taste(String name, int code) {
        // Ab 1.21.9 sind Tastengruppen eigene Objekte statt Texte
        //? if >=1.21.9 {
        KeyMapping.Category gruppe = GRUPPE;
        //?} else
        /*String gruppe = "key.categories.stinkendreich";*/
        // Tastaturtasten heißen ab 26.3 KEYBOARD statt KEYSYM
        //? if >=26.3 {
        InputConstants.Type art = InputConstants.Type.KEYBOARD;
        //?} else
        /*InputConstants.Type art = InputConstants.Type.KEYSYM;*/
        return new KeyMapping("key.stinkendreich." + name, art, code, gruppe);
    }

    //? if >=1.21.9
    private static final KeyMapping.Category GRUPPE = KeyMapping.Category.register(de.moinmornhart.stinkendreich.StinkendReich.id("bonzenauto"));

    private static void tick(Minecraft client) {
        if (client.player == null || client.level == null) {
            MOTOREN.clear();
            return;
        }
        motorenPflegen(client);
        boolean imAuto = client.player.getVehicle() instanceof AutoEntity;
        if (imAuto && !sassImAuto) {
            // Beim Einsteigen: nach vorn schauen und Verfolgerkamera
            AutoEntity auto = (AutoEntity) client.player.getVehicle();
            client.player.setYRot(auto.getYRot());
            client.player.yRotO = auto.getYRot();
            client.player.setYHeadRot(auto.getYRot());
            client.player.setXRot(10.0F);
            vorherigeKamera = client.options.getCameraType();
            kameraModus = 0;
            kameraSetzen(client);
            hinweisTicks = 20 * 6;
        } else if (!imAuto && sassImAuto && vorherigeKamera != null) {
            client.options.setCameraType(vorherigeKamera);
        }
        sassImAuto = imAuto;
        if (!imAuto) {
            while (hupe.consumeClick() || licht.consumeClick() || kamera.consumeClick()) {
                // Tasten außerhalb des Autos ignorieren
            }
            return;
        }
        AutoEntity auto = (AutoEntity) client.player.getVehicle();
        boolean fahrer = auto.getControllingPassenger() == client.player;
        while (hupe.consumeClick()) {
            if (fahrer) {
                KompatClient.sende("auto", auto.blockPosition(), "hupe");
            }
        }
        while (licht.consumeClick()) {
            if (fahrer) {
                KompatClient.sende("auto", auto.blockPosition(), "licht");
            }
        }
        while (kamera.consumeClick()) {
            kameraModus = (kameraModus + 1) % ABSTAENDE.length;
            kameraSetzen(client);
        }
        // Tacho über der Schnellleiste
        if (client.player.tickCount % 4 == 0) {
            Component text = Component.literal(auto.kmh() + " km/h").withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD);
            if (auto.art.fliegt()) {
                // Höhe über dem Boden
                int boden = client.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                        auto.getBlockX(), auto.getBlockZ());
                text = text.copy().append(Component.literal("  ↑ " + Math.max(0, auto.getBlockY() - boden) + " m").withStyle(ChatFormatting.AQUA));
            }
            if (auto.licht()) {
                text = text.copy().append(Component.literal("  ☀").withStyle(ChatFormatting.YELLOW));
            }
            if (hinweisTicks > 0) {
                text = text.copy().append(Component.literal("   ").append(Component.translatable(
                                auto.art.fliegt() ? "stinkendreich.hubschrauber.hinweis" : "stinkendreich.auto.hinweis"))
                        .withStyle(ChatFormatting.GRAY));
            }
            Kompat.nachricht(client.player, text, true);
        }
        if (hinweisTicks > 0) {
            hinweisTicks--;
        }
    }

    private static void kameraSetzen(Minecraft client) {
        kameraAbstand = ABSTAENDE[kameraModus];
        // Beim Hubschrauber weiter weg (der Rotor ist groß)
        if (kameraAbstand > 0 && client.player.getVehicle() instanceof AutoEntity a && a.art.fliegt()) {
            kameraAbstand *= 1.6F;
        }
        client.options.setCameraType(kameraAbstand == 0.0F ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_BACK);
        // Ab 1.21.6 liest die Kamera den Abstand aus dem Fahrzeug
        //? if >=1.21.6 {
        if (client.player.getVehicle() instanceof AutoEntity auto) {
            var attr = auto.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.CAMERA_DISTANCE);
            if (attr != null) {
                attr.setBaseValue(Math.max(4.0F, kameraAbstand));
            }
        }
        //?}
    }

    /** Startet für jedes Auto mit Fahrer in der Nähe ein Motorgeräusch. */
    private static void motorenPflegen(Minecraft client) {
        MOTOREN.values().removeIf(m -> m.isStopped());
        if (client.player.tickCount % 10 != 0) {
            return;
        }
        AABB nah = client.player.getBoundingBox().inflate(48);
        for (AutoEntity auto : client.level.getEntitiesOfClass(AutoEntity.class, nah, a -> a.getControllingPassenger() != null)) {
            if (!MOTOREN.containsKey(auto.getId())) {
                MotorGeraeusch m = new MotorGeraeusch(auto);
                MOTOREN.put(auto.getId(), m);
                client.getSoundManager().play(m);
            }
        }
    }
}
