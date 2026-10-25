package de.moinmornhart.stinkendreich.register;

import de.moinmornhart.stinkendreich.StinkendReich;
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
    public static final EntityType<de.moinmornhart.stinkendreich.auto.AutoEntity> AUTO = registrieren("bonzenauto",
            EntityType.Builder.of(de.moinmornhart.stinkendreich.auto.AutoEntity::new, MobCategory.MISC).sized(2.5F, 1.2F)
                    .clientTrackingRange(10).fireImmune());

    /** Die gläserne Aufzugskabine: 2 × 2 Blöcke, knapp 3 hoch; jede Bewegung sofort an die Klienten. */
    public static final EntityType<de.moinmornhart.stinkendreich.mechanik.AufzugKabine> AUFZUG = registrieren("aufzugkabine",
            EntityType.Builder.of(de.moinmornhart.stinkendreich.mechanik.AufzugKabine::new, MobCategory.MISC).sized(1.75F, 2.85F)
                    .clientTrackingRange(8).updateInterval(1).fireImmune());

    private ModEntities() {
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
        FabricDefaultAttributeRegistry.register(AUTO, de.moinmornhart.stinkendreich.auto.AutoEntity.attribute());
        FabricDefaultAttributeRegistry.register(AUFZUG, de.moinmornhart.stinkendreich.mechanik.AufzugKabine.attribute());
    }
}
