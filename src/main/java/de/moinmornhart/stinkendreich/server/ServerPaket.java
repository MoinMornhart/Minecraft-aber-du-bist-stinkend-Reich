package de.moinmornhart.stinkendreich.server;

//? if >=26.3 {
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
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import eu.pb4.polymer.core.api.utils.PolymerUtils;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
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
    private ServerPaket() {
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
            return Items.PAPER;
        }

        @Override
        public boolean canSyncRawToClient(PacketContext kontext) {
            return ModErkennung.hatMod(kontext.get(PacketContext.CONNECTION));
        }
    }

    /**
     * Figuren: James, Mitarbeiter und Tänzerinnen als Schaufensterpuppe (Menschenfigur) mit ihrer eigenen Haut aus
     * dem Ressourcenpaket, Bodyguards als Eisengolem, Fahrzeuge und Aufzugskabine als Anzeige-Figur mit Modell,
     * alles andere vorerst als Rüstungsständer.
     */
    private static <T extends Entity> void figurUebersetzen(EntityType<T> typ) {
        String name = BuiltInRegistries.ENTITY_TYPE.getKey(typ).getPath();
        boolean mensch = name.equals("butler") || name.equals("mitarbeiter") || name.equals("taenzerin");
        // Sitze sind unsichtbar (leere Anzeige-Figur), Fahrzeuge zeigen ihr Modell (FahrzeugAnzeige)
        String ersatzName = mensch ? "mannequin" : name.equals("bodyguard") ? "iron_golem"
                : name.equals("sitz") || name.equals("aufzugkabine") || typIstFahrzeug(typ) ? "item_display" : "armor_stand";
        EntityType<?> ersatz = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace(ersatzName));
        PolymerEntityUtils.registerOverlay(typ, new PolymerSyncedObject<EntityType<?>>() {
            @Override
            public EntityType<?> getPolymerReplacement(EntityType<?> t, PacketContext kontext) {
                return ersatz;
            }

            @Override
            public boolean canSyncRawToClient(PacketContext kontext) {
                return ModErkennung.hatMod(kontext.get(PacketContext.CONNECTION));
            }
        }, (java.util.function.Function<T, PolymerEntity>) e -> e instanceof de.moinmornhart.stinkendreich.auto.AutoEntity auto
                ? FahrzeugAnzeige.uebersetzung(auto, typ)
                : e instanceof de.moinmornhart.stinkendreich.mechanik.AufzugKabine kabine ? FahrzeugAnzeige.uebersetzung(kabine, typ)
                : new PolymerEntity() {
            @Override
            public EntityType<?> getPolymerEntityType(PacketContext kontext) {
                return ModErkennung.hatMod(kontext.get(PacketContext.CONNECTION)) ? typ : ersatz;
            }

            @Override
            public void modifyRawTrackedData(List<SynchedEntityData.DataValue<?>> daten, ServerPlayer spieler, boolean erstes) {
                if (mensch && erstes && !ModErkennung.hatMod(spieler)) {
                    daten.add(SynchedEntityData.DataValue.create(Puppe.PROFIL, haut(e, name)));
                    if (Puppe.BESCHREIBUNG != null) {
                        daten.add(SynchedEntityData.DataValue.create(Puppe.BESCHREIBUNG, Optional.empty()));
                    }
                }
            }
        });
    }

    private static boolean typIstFahrzeug(EntityType<?> typ) {
        for (var art : de.moinmornhart.stinkendreich.auto.Autotyp.values()) {
            if (de.moinmornhart.stinkendreich.register.ModEntities.auto(art) == typ) {
                return true;
            }
        }
        return false;
    }

    /** Die Haut einer Figur (Textur aus dem Ressourcenpaket, Tänzerinnen je nach Kostüm). */
    private static ResolvableProfile haut(Entity e, String name) {
        String textur = e instanceof de.moinmornhart.stinkendreich.club.TaenzerinEntity t ? "entity/taenzerin_" + t.variante() : "entity/" + name;
        return PolymerUtils.createProfileComponent(new PlayerSkin.Patch(Optional.of(new ClientAsset.ResourceTexture(StinkendReich.id(textur))),
                Optional.empty(), Optional.empty(), Optional.of(PlayerModelType.WIDE)));
    }

    /** Zugriff auf die Datenfelder der Schaufensterpuppe (Haut und der Schriftzug „NPC“ darunter). */
    private static final class Puppe extends Mannequin {
        static final EntityDataAccessor<ResolvableProfile> PROFIL = DATA_PROFILE;
        static final EntityDataAccessor<Optional<Component>> BESCHREIBUNG = beschreibung();

        private Puppe(Level welt) {
            super(welt);
        }

        @SuppressWarnings("unchecked")
        private static EntityDataAccessor<Optional<Component>> beschreibung() {
            try {
                java.lang.reflect.Field f = Mannequin.class.getDeclaredField("DATA_DESCRIPTION");
                f.setAccessible(true);
                return (EntityDataAccessor<Optional<Component>>) f.get(null);
            } catch (ReflectiveOperationException ex) {
                return null;
            }
        }
    }
}
//?}
