package de.moinmornhart.stinkendreich.server;

//? if >=26.1 {
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.auto.AutoEntity;
import de.moinmornhart.stinkendreich.auto.Autotyp;
import de.moinmornhart.stinkendreich.mechanik.AufzugKabine;
import de.moinmornhart.stinkendreich.netz.ModErkennung;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.data.DisplayEntityData;
import eu.pb4.polymer.virtualentity.api.elements.InteractionElement;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Autos, Hubschrauber und die Aufzugskabine für Spieler ohne Mod. Aus den Fahrzeugdaten der Mod
 * (assets/stinkendreich/fahrzeuge) baut diese Klasse beim Erstellen des Ressourcenpakets ein Item-Modell je Fahrzeug
 * und Lackfarbe (seit 26.x dürfen Modellteile beliebig gedreht sein, so werden auch die runden Räder genau), dazu das
 * Modell der Kabine. Für den Client ist das Fahrzeug dann eine Anzeige-Figur mit diesem Modell; ein unsichtbares
 * Klickfeld leitet Rechtsklicks an den echten Wagen weiter.
 */
final class FahrzeugAnzeige {
    /** Modellkoordinaten werden so verkleinert, dass sie in den erlaubten Bereich passen; die Anzeige vergrößert wieder. */
    private static final float VERKLEINERT = 0.25F;

    /** Anzeige-Figur für Items (über die ID, die Typ-Konstanten liegen je nach Version woanders). */
    private static final EntityType<?> ITEM_DISPLAY_TYP = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("item_display"));
    private static final EntityDataAccessor<ItemStack> ITEM = feld("DATA_ITEM_STACK_ID");
    private static final EntityDataAccessor<Byte> ITEM_ART = feld("DATA_ITEM_DISPLAY_ID");

    private FahrzeugAnzeige() {
    }

    @SuppressWarnings("unchecked")
    private static <T> EntityDataAccessor<T> feld(String name) {
        try {
            java.lang.reflect.Field f = Display.ItemDisplay.class.getDeclaredField(name);
            f.setAccessible(true);
            return (EntityDataAccessor<T>) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Anzeige-Feld " + name + " fehlt", e);
        }
    }

    /** Klickfelder an jedes geladene Fahrzeug hängen (nur für Spieler ohne Mod sichtbar). */
    static void registrieren() {
        ServerEntityEvents.ENTITY_LOAD.register((e, welt) -> {
            if (e instanceof AutoEntity auto) {
                EntityAttachment.ofTicking(new Halter(auto, true), auto);
            } else if (e instanceof AufzugKabine kabine) {
                // Die Kabine ist durchklickbar (Klicks gehen an die Tafeln dahinter), braucht also nur Sitze
                EntityAttachment.ofTicking(new Halter(kabine, false), kabine);
            }
        });
    }

    /** Wie das Fahrzeug für Spieler ohne Mod gesendet wird: als Anzeige-Figur mit seinem Modell. */
    static PolymerEntity uebersetzung(AutoEntity auto, EntityType<?> echt) {
        // Polymer fragt schon im Konstruktor nach (da ist auto.art noch leer): alles erst beim Senden lesen
        return uebersetzung(echt, () -> auto.art.massstab, () -> {
            ItemStack modell = new ItemStack(Items.PAPER);
            modell.set(DataComponents.ITEM_MODEL, itemModell(auto.art, AutoEntity.FARBEN[auto.farbe()]));
            return modell;
        });
    }

    /** Die Aufzugskabine als Anzeige-Figur mit ihrem Modell. */
    static PolymerEntity uebersetzung(AufzugKabine kabine, EntityType<?> echt) {
        ItemStack modell = new ItemStack(Items.PAPER);
        modell.set(DataComponents.ITEM_MODEL, StinkendReich.id("polymer/aufzugkabine"));
        return uebersetzung(echt, () -> 1.0F, () -> modell);
    }

    /** Anzeige-Figur mit einem Modell im Entity-Modellraum (y nach unten, Boden bei y = 24 Pixel). */
    private static PolymerEntity uebersetzung(EntityType<?> echt, java.util.function.Supplier<Float> massstab,
                                              java.util.function.Supplier<ItemStack> modell) {
        return new PolymerEntity() {
            @Override
            public EntityType<?> getPolymerEntityType(PacketContext kontext) {
                return ServerPaket.mitMod(kontext) ? echt : ITEM_DISPLAY_TYP;
            }

            @Override
            public void modifyRawTrackedData(List<SynchedEntityData.DataValue<?>> daten, ServerPlayer spieler, boolean erstes) {
                if (ModErkennung.hatMod(spieler)) {
                    return;
                }
                float m = massstab.get() * (1 / VERKLEINERT);
                daten.add(SynchedEntityData.DataValue.create(ITEM, modell.get()));
                if (erstes) {
                    daten.add(SynchedEntityData.DataValue.create(ITEM_ART, (byte) ItemDisplayContext.NONE.ordinal()));
                    daten.add(SynchedEntityData.DataValue.create(DisplayEntityData.SCALE, new Vector3f(m, m, m)));
                    // Modellraum (y nach unten, vorn = -Z) um die x-Achse auf den Kopf drehen, Boden auf Höhe 0
                    daten.add(SynchedEntityData.DataValue.create(DisplayEntityData.LEFT_ROTATION, new Quaternionf().rotateX((float) Math.PI)));
                    daten.add(SynchedEntityData.DataValue.create(DisplayEntityData.TRANSLATION,
                            new Vector3f(0, 1.5F * massstab.get(), 0)));
                    daten.add(SynchedEntityData.DataValue.create(DisplayEntityData.TELEPORTATION_DURATION, 3));
                    daten.add(SynchedEntityData.DataValue.create(DisplayEntityData.VIEW_RANGE, 2.0F));
                }
            }
        };
    }

    static Identifier itemModell(Autotyp art, String farbe) {
        return StinkendReich.id("polymer/fahrzeug/" + art.name + "_" + farbe);
    }

    /**
     * Klickfeld und Sitze eines Fahrzeugs für Spieler ohne Mod. Ihr Minecraft kennt den Wagen nur als Anzeige-Figur
     * (Insassen säßen dort auf dem Boden); deshalb sitzt jeder Insasse für sie auf einem eigenen unsichtbaren Sitz
     * genau an seinem Platz im Wagen. Das wird jeden Tick neu gemeldet, weil der Server den Insassen sonst wieder an
     * den Wagen selbst hängt.
     */
    private static final class Halter extends NurOhneMod {
        private final net.minecraft.world.entity.Entity auto;
        private final Map<Integer, InteractionElement> sitze = new HashMap<>();

        Halter(net.minecraft.world.entity.Entity auto, boolean klickbar) {
            this.auto = auto;
            if (klickbar) {
                InteractionElement klick = InteractionElement.redirect(auto);
                klick.setSize(auto.getBbWidth() + 0.4F, auto.getBbHeight() + 0.2F);
                addElement(klick);
            }
        }

        @Override
        protected void onTick() {
            super.onTick();
            java.util.Set<Integer> jetzt = new java.util.HashSet<>();
            for (net.minecraft.world.entity.Entity insasse : auto.getPassengers()) {
                jetzt.add(insasse.getId());
                InteractionElement sitz = sitze.computeIfAbsent(insasse.getId(), id -> {
                    InteractionElement neu = InteractionElement.redirect(auto);
                    neu.setSize(0.0F, 0.0F);
                    addElement(neu);
                    return neu;
                });
                sitz.setOffset(auto.getPassengerRidingPosition(insasse).subtract(auto.position()));
                sendPacket(eu.pb4.polymer.virtualentity.api.VirtualEntityUtils.createClientboundSetPassengersPacket(
                        sitz.getEntityId(), new int[]{insasse.getId()}));
            }
            sitze.entrySet().removeIf(e -> {
                if (jetzt.contains(e.getKey())) {
                    return false;
                }
                sendPacket(eu.pb4.polymer.virtualentity.api.VirtualEntityUtils.createClientboundSetPassengersPacket(
                        e.getValue().getEntityId(), new int[0]));
                removeElement(e.getValue());
                return true;
            });
        }
    }

    /** Hält Elemente, die nur Spieler ohne Mod brauchen (mit Mod gibt es den echten Wagen). */
    static class NurOhneMod extends ElementHolder {
        @Override
        public boolean startWatching(ServerGamePacketListenerImpl spieler) {
            return !ModErkennung.hatMod(spieler.player) && super.startWatching(spieler);
        }
    }

    // ------------------------------------------------------------------
    // Item-Modelle aus den Fahrzeugdaten
    // ------------------------------------------------------------------

    /** Schreibt Modelle, Item-Definitionen und Texturen aller Fahrzeuge ins Ressourcenpaket. */
    static void ressourcen(ResourcePackBuilder paket) {
        kabine(paket);
        for (Autotyp art : Autotyp.values()) {
            JsonObject daten = lesen("assets/stinkendreich/fahrzeuge/" + art.name + ".json");
            if (daten == null) {
                continue;
            }
            String basis = "item/polymer/fahrzeug/" + art.name;
            paket.addStringData("assets/stinkendreich/models/" + basis + ".json", modell(daten).toString());
            for (String farbe : AutoEntity.FARBEN) {
                byte[] textur = bytes("assets/stinkendreich/textures/entity/" + art.name + "/" + farbe + ".png");
                if (textur == null) {
                    continue;
                }
                String texturId = "stinkendreich:item/polymer/fahrzeug/" + art.name + "/" + farbe;
                paket.addData("assets/stinkendreich/textures/item/polymer/fahrzeug/" + art.name + "/" + farbe + ".png", textur);
                JsonObject kind = new JsonObject();
                kind.addProperty("parent", "stinkendreich:" + basis);
                JsonObject texturen = new JsonObject();
                texturen.addProperty("lack", texturId);
                texturen.addProperty("particle", texturId);
                kind.add("textures", texturen);
                Identifier item = itemModell(art, farbe);
                paket.addStringData("assets/stinkendreich/models/item/" + item.getPath() + ".json", kind.toString());
                paket.addStringData("assets/stinkendreich/items/" + item.getPath() + ".json",
                        "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"stinkendreich:item/" + item.getPath() + "\"}}");
            }
        }
    }

    /**
     * Die Aufzugskabine: dieselben Kästen wie {@code client/aufzug/AufzugModell} (Textur 256 × 256 aus
     * tools/assets/aufzug.py), im Format der Fahrzeugdaten, damit dasselbe Umrechnen greift.
     */
    private static void kabine(ResourcePackBuilder paket) {
        byte[] textur = bytes("assets/stinkendreich/textures/entity/aufzugkabine.png");
        if (textur == null) {
            return;
        }
        // u, v, x, y, z, Breite, Höhe, Tiefe
        float[][] kaesten = {
                {0, 0, -14, 23, -14, 28, 1, 28}, {0, 30, -14, -22, -14, 28, 1, 28}, {120, 0, -7, -21, -7, 14, 1, 14},
                {0, 60, -14, -21, -12, 1, 44, 26}, {0, 60, 13, -21, -12, 1, 44, 26}, {0, 132, -12, -21, 13, 24, 44, 1},
                {120, 20, -14, -21, -14, 2, 44, 2}, {120, 20, 12, -21, -14, 2, 44, 2}, {120, 20, -14, -21, 12, 2, 44, 2},
                {120, 20, 12, -21, 12, 2, 44, 2}, {140, 20, -12, -21, -14, 24, 3, 1}, {140, 30, -13, 9, -11, 1, 1, 20},
                {140, 30, 12, 9, -11, 1, 1, 20}, {60, 132, -11, 17, 6, 22, 6, 6}, {120, 132, -12, 15, 5, 24, 2, 8},
                {120, 150, -12, 4, 11, 24, 11, 2}};
        JsonArray liste = new JsonArray();
        for (float[] k : kaesten) {
            JsonObject kasten = new JsonObject();
            kasten.add("uv", arr(k[0], k[1]));
            kasten.add("von", arr(k[2], k[3], k[4]));
            kasten.add("masse", arr(k[5], k[6], k[7]));
            liste.add(kasten);
        }
        JsonObject teil = new JsonObject();
        teil.addProperty("name", "kabine");
        teil.add("pivot", arr(0, 0, 0));
        teil.add("drehung", arr(0, 0, 0));
        teil.add("kaesten", liste);
        JsonObject daten = new JsonObject();
        daten.addProperty("breite", 256);
        daten.addProperty("hoehe", 256);
        JsonArray teile = new JsonArray();
        teile.add(teil);
        daten.add("teile", teile);
        JsonObject m = modell(daten);
        JsonObject texturen = new JsonObject();
        texturen.addProperty("lack", "stinkendreich:item/polymer/aufzugkabine");
        texturen.addProperty("particle", "stinkendreich:item/polymer/aufzugkabine");
        m.add("textures", texturen);
        paket.addData("assets/stinkendreich/textures/item/polymer/aufzugkabine.png", textur);
        paket.addStringData("assets/stinkendreich/models/item/polymer/aufzugkabine.json", m.toString());
        paket.addStringData("assets/stinkendreich/items/polymer/aufzugkabine.json",
                "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"stinkendreich:item/polymer/aufzugkabine\"}}");
    }

    /**
     * Baut aus den Teilen und Kästen ein Modell. Jeder Kasten wird ein Element in Modellkoordinaten (verkleinert, um
     * 8 verschoben), gedreht wie sein Teil (Drehpunkt = Teil-Drehpunkt, Eltern-Drehungen mitgerechnet nur über den
     * Drehpunkt). Die Flächen bekommen ihre Stelle auf der Textur wie bei Minecraft-Modellen.
     */
    private static JsonObject modell(JsonObject daten) {
        int breite = daten.get("breite").getAsInt(), hoehe = daten.get("hoehe").getAsInt();
        Map<String, JsonObject> teile = new HashMap<>();
        for (JsonElement t : daten.getAsJsonArray("teile")) {
            teile.put(t.getAsJsonObject().get("name").getAsString(), t.getAsJsonObject());
        }
        JsonArray elemente = new JsonArray();
        for (JsonObject teil : teile.values()) {
            // Drehpunkt und Drehung über alle Eltern zusammenzählen (die Eltern sind hier nie selbst schräg und
            // gedreht zugleich – die Kinder drehen nur um den Drehpunkt der Eltern)
            float[] pivot = vec(teil.getAsJsonArray("pivot"));
            float[] dreh = vec(teil.getAsJsonArray("drehung"));
            String eltern = teil.has("eltern") && !teil.get("eltern").isJsonNull() ? teil.get("eltern").getAsString() : null;
            while (eltern != null && teile.containsKey(eltern)) {
                JsonObject e = teile.get(eltern);
                float[] ep = vec(e.getAsJsonArray("pivot")), ed = vec(e.getAsJsonArray("drehung"));
                pivot = new float[]{pivot[0] + ep[0], pivot[1] + ep[1], pivot[2] + ep[2]};
                dreh = new float[]{dreh[0] + ed[0], dreh[1] + ed[1], dreh[2] + ed[2]};
                eltern = e.has("eltern") && !e.get("eltern").isJsonNull() ? e.get("eltern").getAsString() : null;
            }
            for (JsonElement k : teil.getAsJsonArray("kaesten")) {
                JsonObject kasten = k.getAsJsonObject();
                float[] von = vec(kasten.getAsJsonArray("von")), masse = vec(kasten.getAsJsonArray("masse"));
                float[] uv = vec(kasten.getAsJsonArray("uv"));
                JsonObject el = new JsonObject();
                el.add("from", arr(p(pivot[0] + von[0]), p(pivot[1] + von[1]), p(pivot[2] + von[2])));
                el.add("to", arr(p(pivot[0] + von[0] + masse[0]), p(pivot[1] + von[1] + masse[1]), p(pivot[2] + von[2] + masse[2])));
                if (dreh[0] != 0 || dreh[1] != 0 || dreh[2] != 0) {
                    JsonObject r = new JsonObject();
                    r.add("origin", arr(p(pivot[0]), p(pivot[1]), p(pivot[2])));
                    r.addProperty("x", Math.toDegrees(dreh[0]));
                    r.addProperty("y", Math.toDegrees(dreh[1]));
                    r.addProperty("z", Math.toDegrees(dreh[2]));
                    el.add("rotation", r);
                }
                el.add("faces", flaechen(uv[0], uv[1], masse[0], masse[1], masse[2], breite, hoehe));
                elemente.add(el);
            }
        }
        JsonObject m = new JsonObject();
        m.add("elements", elemente);
        return m;
    }

    /** Modellpixel → verkleinerte Elementkoordinate. */
    private static float p(float pixel) {
        return pixel * VERKLEINERT + 8;
    }

    /** Die sechs Flächen eines Kastens mit Textur-Ausschnitt (Aufteilung wie bei Minecraft-Modellen). */
    private static JsonObject flaechen(float u, float v, float w, float h, float d, int breite, int hoehe) {
        JsonObject f = new JsonObject();
        // Modellraum: y zeigt nach unten (min y = oben), vorn ist -Z (= „north“ im Modell)
        f.add("down", flaeche(u + d + w, v, u + d + w + w, v + d, breite, hoehe));          // max y: unten
        f.add("up", flaeche(u + d, v + d, u + d + w, v, breite, hoehe));                    // min y: oben
        f.add("north", flaeche(u + d, v + d, u + d + w, v + d + h, breite, hoehe));          // vorn
        f.add("south", flaeche(u + d + w + d, v + d, u + d + w + d + w, v + d + h, breite, hoehe));
        f.add("west", flaeche(u, v + d, u + d, v + d + h, breite, hoehe));
        f.add("east", flaeche(u + d + w, v + d, u + d + w + d, v + d + h, breite, hoehe));
        return f;
    }

    private static JsonObject flaeche(float u1, float v1, float u2, float v2, int breite, int hoehe) {
        JsonObject o = new JsonObject();
        o.add("uv", arr(u1 * 16 / breite, v1 * 16 / hoehe, u2 * 16 / breite, v2 * 16 / hoehe));
        o.addProperty("texture", "#lack");
        return o;
    }

    private static float[] vec(JsonArray a) {
        float[] r = new float[a.size()];
        for (int i = 0; i < r.length; i++) {
            r[i] = a.get(i).getAsFloat();
        }
        return r;
    }

    private static JsonArray arr(float... werte) {
        JsonArray a = new JsonArray();
        for (float w : werte) {
            a.add(w);
        }
        return a;
    }

    private static Optional<Path> pfad(String pfad) {
        return FabricLoader.getInstance().getModContainer(StinkendReich.MOD_ID).flatMap(m -> m.findPath(pfad));
    }

    private static JsonObject lesen(String pfad) {
        return pfad(pfad).map(p -> {
            try {
                return JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception e) {
                return null;
            }
        }).orElse(null);
    }

    private static byte[] bytes(String pfad) {
        return pfad(pfad).map(p -> {
            try {
                return Files.readAllBytes(p);
            } catch (Exception e) {
                return null;
            }
        }).orElse(null);
    }
}
//?}
