package de.moinmornhart.stinkendreich.server;

//? if >=1.21.6 {
import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.netz.ModErkennung;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.core.api.other.PolymerSoundEvent;
import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
//? if >=26.1 {
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
//?} else
/*import xyz.nucleoid.packettweaker.PacketContext;*/
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
//? if >=1.21.9
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * Server-Version für Spieler ohne Mod (nur aktiv, wenn auf dem Server die Bibliothek Polymer liegt). Polymer
 * übersetzt beim Senden alles, was es in Minecraft ohne Mod nicht gibt: Blöcke, Items, Figuren und Töne der Mod
 * werden zu normalen Minecraft-Dingen, die ihr Aussehen aus einem Ressourcenpaket bekommen. Das Paket baut Polymer
 * aus unseren eigenen Dateien und schickt es beim Betreten mit. Spieler mit Mod bekommen alles unverändert.
 */
public final class ServerPaket {
    /** Figuren, die für Spieler ohne Mod als Anzeige-Figur mit ihrem Modell erscheinen (siehe FahrzeugAnzeige). */
    private static final java.util.Set<String> MODELLE = java.util.Set.of("aufzugkabine", "drehteller", "fernseher");

    private ServerPaket() {
    }

    /**
     * Bekommt der Empfänger dieses Pakets die echten Mod-Dinge? Ohne Empfänger (Polymer fragt auch beim Laden von
     * Chunks, dann ist der Kontext leer) gilt: nein, damit Polymer unsere Blöcke als übersetzt erkennt. Ein Fehler an
     * dieser Stelle würde das Laden des Chunks abbrechen, und der Server erzeugte ihn neu.
     */
    static boolean mitMod(PacketContext kontext) {
        if (kontext == null) {
            return false;
        }
        net.minecraft.network.Connection verbindung = PolymerKompat.verbindung(kontext);
        return verbindung != null && ModErkennung.hatMod(verbindung);
    }

    public static void registrieren() {
        StinkendReich.LOGGER.info("Polymer gefunden: Server-Version für Spieler ohne Mod ist aktiv");
        PolymerResourcePackUtils.addModAssets(StinkendReich.MOD_ID);
        PolymerResourcePackUtils.markAsRequired();

        int bloecke = 0, items = 0, figuren = 0, toene = 0;
        for (Block block : eigene(BuiltInRegistries.BLOCK)) {
            BlockUebersetzung u = new BlockUebersetzung(block);
            PolymerBlock.registerOverlay(block, u);
            BlockWithElementHolder.registerOverlay(block, u);
            bloecke++;
        }
        for (Item item : eigene(BuiltInRegistries.ITEM)) {
            PolymerItem.registerOverlay(item, new ItemUebersetzung());
            items++;
        }
        PolymerBlockUtils.registerBlockEntity(eigene(BuiltInRegistries.BLOCK_ENTITY_TYPE).toArray(new net.minecraft.world.level.block.entity.BlockEntityType<?>[0]));
        for (EntityType<?> typ : eigene(BuiltInRegistries.ENTITY_TYPE)) {
            figurUebersetzen(typ);
            figuren++;
        }
        for (var ton : eigene(BuiltInRegistries.SOUND_EVENT)) {
            PolymerSoundEvent.registerOverlay(ton);
            toene++;
        }
        FahrzeugAnzeige.registrieren();
        Bedienfelder.registrieren();
        KameraBlick.registrieren();
        // Item-Modelle für die Anzeige-Figuren der Blöcke und die Fahrzeuge ins Ressourcenpaket
        PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT.register(paket -> {
            FahrzeugAnzeige.ressourcen(paket);
            for (Identifier modell : BlockUebersetzung.ANZEIGE_MODELLE) {
                Identifier item = BlockUebersetzung.itemModellId(modell);
                paket.addStringData("assets/" + item.getNamespace() + "/items/" + item.getPath() + ".json",
                        "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"" + modell + "\"}}");
            }
        });
        StinkendReich.LOGGER.info("Server-Version: {} Blöcke, {} Items, {} Figurenarten, {} Töne übersetzt", bloecke, items, figuren, toene);
        andereEintraegeMelden();
    }

    /** Alle Einträge der Mod in einer Registry. */
    private static <T> List<T> eigene(Registry<T> registry) {
        return registry.keySet().stream().filter(id -> id.getNamespace().equals(StinkendReich.MOD_ID))
                .map(id -> registry.getValue(id)).toList();
    }

    /** Für die Fehlersuche: In welchen weiteren Registries hat die Mod Einträge? (Die müssen auch übersetzt werden.) */
    private static void andereEintraegeMelden() {
        BuiltInRegistries.REGISTRY.forEach(r -> {
            if (r == BuiltInRegistries.BLOCK || r == BuiltInRegistries.ITEM || r == BuiltInRegistries.ENTITY_TYPE
                    || r == BuiltInRegistries.SOUND_EVENT || r == BuiltInRegistries.BLOCK_ENTITY_TYPE) {
                return;
            }
            long n = r.keySet().stream().filter(id -> id.getNamespace().equals(StinkendReich.MOD_ID)).count();
            if (n > 0) {
                StinkendReich.LOGGER.warn("Server-Version: {} Einträge in {} sind noch nicht übersetzt", n, r.key());
            }
        });
    }

    /** Items: Papier mit unserem Modell (das Ressourcenpaket kennt die Item-Modelle der Mod). */
    private static final class ItemUebersetzung implements PolymerItem {
        @Override
        public Item getPolymerItem(ItemStack stack, PacketContext kontext) {
            return mitMod(kontext) ? stack.getItem() : Items.PAPER;
        }

        @Override
        public boolean canSyncRawToClient(PacketContext kontext) {
            return mitMod(kontext);
        }
    }

    /**
     * Figuren: James, Mitarbeiter, Tänzerinnen und Bodyguards als Schaufensterpuppe (Menschenfigur) mit eigener Haut
     * aus dem Ressourcenpaket, Fahrzeuge und Aufzugskabine als Anzeige-Figur mit Modell,
     * alles andere vorerst als Rüstungsständer.
     */
    private static <T extends Entity> void figurUebersetzen(EntityType<T> typ) {
        String name = BuiltInRegistries.ENTITY_TYPE.getKey(typ).getPath();
        // Bodyguards: ohne Mod ein großer Türsteher im Smoking (Menschenfigur, 1,45-fach), denn die Haut eines
        // Eisengolems lässt sich nicht für einzelne Golems austauschen
        boolean bodyguard = name.equals("bodyguard");
        boolean mensch = bodyguard || name.equals("butler") || name.equals("mitarbeiter") || name.equals("taenzerin");
        // Sitze sind unsichtbar (leere Anzeige-Figur), Fahrzeuge zeigen ihr Modell (FahrzeugAnzeige)
        // Menschen ab 1.21.9 als Schaufensterpuppe mit eigener Haut, davor als Standbild (FahrzeugAnzeige.figur)
        //? if >=1.21.9 {
        boolean puppe = mensch;
        //?} else
        /*boolean puppe = false;*/
        String ersatzName = puppe ? "mannequin"
                : mensch || name.equals("sitz") || MODELLE.contains(name) || typIstFahrzeug(typ) ? "item_display" : "armor_stand";
        EntityType<?> ersatz = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace(ersatzName));
        java.util.function.Function<T, PolymerEntity> uebersetzer = e -> e instanceof de.moinmornhart.stinkendreich.auto.AutoEntity auto
                ? FahrzeugAnzeige.uebersetzung(auto, typ)
                : MODELLE.contains(name) ? FahrzeugAnzeige.uebersetzung(e, name, typ)
                : mensch && !puppe ? FahrzeugAnzeige.figur(e, typ, bodyguard ? 1.45F : 1.0F)
                : new PolymerEntity() {
            @Override
            public EntityType<?> getPolymerEntityType(PacketContext kontext) {
                return mitMod(kontext) ? typ : ersatz;
            }

            @Override
            public void modifyRawTrackedData(List<SynchedEntityData.DataValue<?>> daten, ServerPlayer spieler, boolean erstes) {
                if (ModErkennung.hatMod(spieler)) {
                    return;
                }
                // Puppen behalten die Daten aller Lebewesen (Gesundheit, Haltung …), Anzeige-Figuren nur die jeder Figur
                fremdeDatenEntfernen(daten, puppe ? 15 : 8);
                //? if >=1.21.9 {
                if (puppe && erstes) {
                    daten.add(SynchedEntityData.DataValue.create(Puppe.PROFIL, haut(e)));
                    if (Puppe.BESCHREIBUNG != null) {
                        daten.add(SynchedEntityData.DataValue.create(Puppe.BESCHREIBUNG, Optional.empty()));
                    }
                }
                //?}
            }

            @Override
            public void modifyRawEntityAttributeData(List<net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket.AttributeSnapshot> daten,
                                                     ServerPlayer spieler, boolean erstes) {
                if (bodyguard && puppe && !ModErkennung.hatMod(spieler)) {
                    daten.removeIf(a -> a.attribute().equals(net.minecraft.world.entity.ai.attributes.Attributes.SCALE));
                    daten.add(new net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket.AttributeSnapshot(
                            net.minecraft.world.entity.ai.attributes.Attributes.SCALE, 1.45, List.of()));
                }
            }
        };
        //? if >=1.21.9 {
        PolymerEntityUtils.registerOverlay(typ, new PolymerSyncedObject<EntityType<?>>() {
            @Override
            public EntityType<?> getPolymerReplacement(EntityType<?> t, PacketContext kontext) {
                // Spieler mit Mod bekommen immer die echte Figur (sonst passen Typ und Daten nicht zusammen)
                return mitMod(kontext) ? t : ersatz;
            }

            @Override
            public boolean canSyncRawToClient(PacketContext kontext) {
                return mitMod(kontext);
            }
        }, uebersetzer);
        //?} else
        /*PolymerEntityUtils.registerOverlay(typ, (it.unimi.dsi.fastutil.Function<T, PolymerEntity>) o -> uebersetzer.apply((T) o));*/
    }

    /**
     * Entfernt die Datenfelder der echten Figur ab Nummer {@code ab}. Polymer reicht ein Feld an den Ersatz weiter,
     * wenn dieser an derselben Stelle ein Feld desselben Typs hat; so würde aus „ohne KI“ (Mob) bei einer
     * Anzeige-Figur „dreht sich immer zum Spieler“ und aus einer Zahl des Autos die Helligkeit.
     */
    static void fremdeDatenEntfernen(List<SynchedEntityData.DataValue<?>> daten, int ab) {
        daten.removeIf(wert -> wert.id() >= ab);
    }

    private static boolean typIstFahrzeug(EntityType<?> typ) {
        for (var art : de.moinmornhart.stinkendreich.auto.Autotyp.values()) {
            if (de.moinmornhart.stinkendreich.register.ModEntities.auto(art) == typ) {
                return true;
            }
        }
        return false;
    }

    //? if >=1.21.9 {
    /**
     * Die Haut einer Figur (Textur aus dem Ressourcenpaket, Tänzerinnen je nach Kostüm). Gebaut über das Datenformat
     * von Minecraft ({@code {"texture": …, "model": "wide"}}), das gibt es ab 1.21.9 überall gleich.
     */
    private static ResolvableProfile haut(Entity e) {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("texture", StinkendReich.id("entity/" + FahrzeugAnzeige.haut(e)).toString());
        json.addProperty("model", "wide");
        return ResolvableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
    }

    /** Zugriff auf die Datenfelder der Schaufensterpuppe (Haut und der Schriftzug „NPC“ darunter). */
    private static final class Puppe extends Mannequin {
        static final EntityDataAccessor<ResolvableProfile> PROFIL = DATA_PROFILE;
        static final EntityDataAccessor<Optional<Component>> BESCHREIBUNG = beschreibung();

        private Puppe(Level welt) {
            super(welt);
        }

        /**
         * Das Feld für den Schriftzug (privat). Gesucht über seinen Datentyp, nicht über den Namen: In der fertigen
         * .jar für 1.21.x tragen die Felder von Minecraft andere Namen als beim Entwickeln.
         */
        @SuppressWarnings("unchecked")
        private static EntityDataAccessor<Optional<Component>> beschreibung() {
            for (java.lang.reflect.Field f : Mannequin.class.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers()) || !EntityDataAccessor.class.isAssignableFrom(f.getType())) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    EntityDataAccessor<?> a = (EntityDataAccessor<?>) f.get(null);
                    if (a.serializer() == net.minecraft.network.syncher.EntityDataSerializers.OPTIONAL_COMPONENT) {
                        return (EntityDataAccessor<Optional<Component>>) a;
                    }
                } catch (ReflectiveOperationException ex) {
                    // nächstes Feld
                }
            }
            return null;
        }
    }
    //?}
}
//?}
