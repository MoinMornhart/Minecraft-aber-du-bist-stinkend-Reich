package de.moinmornhart.stinkendreich.register;

import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.auto.AutoEntity;
import de.moinmornhart.stinkendreich.auto.Autotyp;
import de.moinmornhart.stinkendreich.butler.ButlerEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Eigene Lebewesen der Mod. */
public final class ModEntities {
    /** Der Butler: so groß wie ein Spieler, feuerfest, wird bis 10 Chunks weit angezeigt. */
    public static final EntityType<ButlerEntity> BUTLER = registrieren("butler",
            EntityType.Builder.of(ButlerEntity::new, MobCategory.MISC).sized(0.6F, 1.95F).eyeHeight(1.62F)
                    .clientTrackingRange(10).fireImmune());
    /** Das Bonzenauto: 2,5 Blöcke breit, 1,2 hoch (das Modell ist länger als die Trefferbox). */
    public static final EntityType<AutoEntity> AUTO = auto(Autotyp.BONZENAUTO, 2.5F, 1.2F);
    /** Luxus-Geländewagen: breit und hoch. */
    public static final EntityType<AutoEntity> GELAENDEWAGEN = auto(Autotyp.GELAENDEWAGEN, 2.4F, 2.1F);
    /** Supersportwagen: flach. */
    public static final EntityType<AutoEntity> SUPERSPORTWAGEN = auto(Autotyp.SUPERSPORTWAGEN, 2.5F, 1.0F);
    /** Stretch-Limousine: 8 Blöcke lang, die Trefferbox deckt die Mitte ab. */
    public static final EntityType<AutoEntity> STRETCHLIMO = auto(Autotyp.STRETCHLIMO, 2.5F, 1.5F);
    /** Luxus-Hubschrauber (eigene Flugphysik, siehe HubschrauberEntity). */
    public static final EntityType<AutoEntity> HUBSCHRAUBER = registrieren(Autotyp.HUBSCHRAUBER.name,
            EntityType.Builder.<AutoEntity>of((t, w) -> new de.moinmornhart.stinkendreich.auto.HubschrauberEntity(t, w), MobCategory.MISC)
                    .sized(3.2F, 3.0F).clientTrackingRange(12).updateInterval(1).fireImmune());

    /** Die gläserne Aufzugskabine: 2 × 2 Blöcke, knapp 3 hoch; jede Bewegung sofort an die Klienten. */
    public static final EntityType<de.moinmornhart.stinkendreich.mechanik.AufzugKabine> AUFZUG = registrieren("aufzugkabine",
            EntityType.Builder.of(de.moinmornhart.stinkendreich.mechanik.AufzugKabine::new, MobCategory.MISC).sized(1.75F, 2.85F)
                    .clientTrackingRange(8).updateInterval(1).fireImmune());

    /** XXL-Fernseher an der Wand; die Trefferbox sitzt in der Mitte des Bildschirms. */
    public static final EntityType<de.moinmornhart.stinkendreich.fernseher.FernseherEntity> FERNSEHER = registrieren("fernseher",
            EntityType.Builder.of(de.moinmornhart.stinkendreich.fernseher.FernseherEntity::new, MobCategory.MISC).sized(1.2F, 2.8F)
                    .clientTrackingRange(8).fireImmune());

    /** Drehteller in der Tiefgarage: kleine Trefferbox in der Mitte, die Scheibe selbst ist 6,75 Blöcke breit. */
    public static final EntityType<de.moinmornhart.stinkendreich.auto.DrehtellerEntity> DREHTELLER = registrieren("drehteller",
            EntityType.Builder.of(de.moinmornhart.stinkendreich.auto.DrehtellerEntity::new, MobCategory.MISC).sized(1.0F, 0.2F)
                    .clientTrackingRange(8).fireImmune());

    /** Unsichtbarer Sitzplatz auf Sofas, Liegen und Bänken. */
    public static final EntityType<de.moinmornhart.stinkendreich.moebel.SitzEntity> SITZ = registrieren("sitz",
            EntityType.Builder.of(de.moinmornhart.stinkendreich.moebel.SitzEntity::new, MobCategory.MISC).sized(0.3F, 0.1F)
                    .clientTrackingRange(10).fireImmune());

    private ModEntities() {
    }

    private static EntityType<AutoEntity> auto(Autotyp art, float breite, float hoehe) {
        return registrieren(art.name, EntityType.Builder.<AutoEntity>of((t, w) -> new AutoEntity(t, w, art), MobCategory.MISC)
                .sized(breite, hoehe).clientTrackingRange(10).updateInterval(1).fireImmune());
    }

    /** Entity-Typ zu einem Fahrzeug. */
    public static EntityType<AutoEntity> auto(Autotyp art) {
        return switch (art) {
            case BONZENAUTO -> AUTO;
            case GELAENDEWAGEN -> GELAENDEWAGEN;
            case SUPERSPORTWAGEN -> SUPERSPORTWAGEN;
            case STRETCHLIMO -> STRETCHLIMO;
            case HUBSCHRAUBER -> HUBSCHRAUBER;
        };
    }

    private static <T extends Entity> EntityType<T> registrieren(String name, EntityType.Builder<T> bauer) {
        Identifier id = StinkendReich.id(name);
        // Ab 1.21.2 braucht der Bauplan den Registerschlüssel statt eines Namens
        //? if >=1.21.2 {
        EntityType<T> typ = bauer.build(ResourceKey.create(Registries.ENTITY_TYPE, id));
        //?} else
        /*EntityType<T> typ = bauer.build(id.toString());*/
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, id, typ);
    }

    public static void init() {
        FabricDefaultAttributeRegistry.register(BUTLER, ButlerEntity.attribute());
        for (Autotyp art : Autotyp.values()) {
            FabricDefaultAttributeRegistry.register(auto(art), AutoEntity.attribute(art));
        }
        FabricDefaultAttributeRegistry.register(FERNSEHER, de.moinmornhart.stinkendreich.fernseher.FernseherEntity.attribute());
        FabricDefaultAttributeRegistry.register(AUFZUG, de.moinmornhart.stinkendreich.mechanik.AufzugKabine.attribute());
        FabricDefaultAttributeRegistry.register(SITZ, de.moinmornhart.stinkendreich.moebel.SitzEntity.attribute());
        FabricDefaultAttributeRegistry.register(DREHTELLER, de.moinmornhart.stinkendreich.auto.DrehtellerEntity.attribute());
    }
}
