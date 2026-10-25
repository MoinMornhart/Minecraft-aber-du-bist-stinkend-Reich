package de.moinmornhart.stinkendreich.handy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Lieferservice über das Handy: James bringt Tiere oder Waren mit der Limo vorbei (siehe AnrufAuftrag). Tiere
 * erscheinen neben dem Wagen (Hund, Katze und Papagei sind schon gezähmt), Waren landen im Inventar.
 */
public final class Lieferservice {
    /** Was man bestellen kann (Kennung für das Handy). */
    public static final List<String> ANGEBOT = List.of("kuehe", "schweine", "schafe", "huehner", "hund", "katze", "papagei",
            "festessen", "feuerwerk", "gold");

    private Lieferservice() {
    }

    /** Liefert die Bestellung bei {@code bei} ab und gibt zurück, was es war (für James' Satz). */
    public static Component liefern(ServerLevel welt, ServerPlayer herr, String was, Vec3 bei) {
        BlockPos p = BlockPos.containing(bei.x + 2, bei.y, bei.z + 2);
        switch (was) {
            case "kuehe" -> tiere(welt, herr, typ("cow"), 2, p);
            case "schweine" -> tiere(welt, herr, typ("pig"), 2, p);
            case "schafe" -> tiere(welt, herr, typ("sheep"), 2, p);
            case "huehner" -> tiere(welt, herr, typ("chicken"), 4, p);
            case "hund" -> tiere(welt, herr, typ("wolf"), 1, p);
            case "katze" -> tiere(welt, herr, typ("cat"), 1, p);
            case "papagei" -> tiere(welt, herr, typ("parrot"), 1, p);
            case "festessen" -> waren(herr, new ItemStack(Items.COOKED_BEEF, 32), new ItemStack(Items.GOLDEN_CARROT, 32),
                    new ItemStack(Items.CAKE, 2), new ItemStack(Items.PUMPKIN_PIE, 16));
            case "feuerwerk" -> waren(herr, new ItemStack(Items.FIREWORK_ROCKET, 64));
            case "gold" -> waren(herr, new ItemStack(Items.GOLD_INGOT, 64), new ItemStack(Items.GOLD_BLOCK, 16));
            default -> {
                return Component.translatable("stinkendreich.handy.lieferung.nichts");
            }
        }
        welt.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, 30, 1.5, 0.8, 1.5, 0.1);
        return Component.translatable("stinkendreich.handy.lieferung." + was);
    }

    private static EntityType<?> typ(String name) {
        return de.moinmornhart.stinkendreich.Kompat.entityTypNachId(net.minecraft.resources.Identifier.withDefaultNamespace(name));
    }

    private static void tiere(ServerLevel welt, ServerPlayer herr, EntityType<? extends Entity> typ, int anzahl, BlockPos p) {
        for (int i = 0; i < anzahl; i++) {
            BlockPos q = p.offset(i % 2, 0, i / 2);
            //? if >=1.21.2 {
            Entity e = typ.spawn(welt, q, net.minecraft.world.entity.EntitySpawnReason.EVENT);
            //?} else
            /*Entity e = typ.spawn(welt, q, net.minecraft.world.entity.MobSpawnType.EVENT);*/
            if (e instanceof TamableAnimal zahm) {
                zahm.tame(herr);
            }
        }
    }

    private static void waren(ServerPlayer herr, ItemStack... sachen) {
        for (ItemStack s : sachen) {
            if (!herr.getInventory().add(s)) {
                net.minecraft.world.level.block.Block.popResource(herr.level(), herr.blockPosition(), s);
            }
        }
    }
}
