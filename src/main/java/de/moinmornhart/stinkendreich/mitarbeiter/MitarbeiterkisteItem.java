package de.moinmornhart.stinkendreich.mitarbeiter;

import de.moinmornhart.stinkendreich.register.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;

/**
 * Mitarbeiterkiste (kommt per Handy): stellt eine Truhe mit dem Namen „Mitarbeiterkiste“ auf, und drei Mitarbeiter
 * steigen aus – einer sucht Diamanten, einer Eisen, einer Kohle. Ihre Beute landet in dieser Truhe.
 */
public class MitarbeiterkisteItem extends BlockItem {
    private static final String[] START = {"diamant", "rohes_eisen", "kohle"};

    public MitarbeiterkisteItem(Properties eigenschaften) {
        super(Blocks.CHEST, eigenschaften);
    }

    @Override
    public InteractionResult place(BlockPlaceContext kontext) {
        InteractionResult r = super.place(kontext);
        if (r.consumesAction() && kontext.getLevel() instanceof ServerLevel welt && kontext.getPlayer() instanceof ServerPlayer chef) {
            BlockPos kiste = kontext.getClickedPos();
            for (int i = 0; i < START.length; i++) {
                MitarbeiterEntity m = ModEntities.MITARBEITER.create(welt
                        //? if >=1.21.2
                        , net.minecraft.world.entity.EntitySpawnReason.EVENT
                );
                if (m == null) {
                    continue;
                }
                Direction seite = Direction.from2DDataValue(i);
                BlockPos platz = kiste.relative(seite, 2);
                m.teleportTo(platz.getX() + 0.5, platz.getY(), platz.getZ() + 0.5);
                m.einstellen(chef, kiste, START[i], welt.getRandom().nextInt(64) + i);
                welt.addFreshEntity(m);
                welt.sendParticles(ParticleTypes.HAPPY_VILLAGER, m.getX(), m.getY() + 1, m.getZ(), 12, 0.4, 0.6, 0.4, 0.05);
            }
            de.moinmornhart.stinkendreich.Kompat.nachricht(chef, Component.translatable("stinkendreich.mitarbeiter.da").withStyle(ChatFormatting.GOLD), false);
        }
        return r;
    }
}
