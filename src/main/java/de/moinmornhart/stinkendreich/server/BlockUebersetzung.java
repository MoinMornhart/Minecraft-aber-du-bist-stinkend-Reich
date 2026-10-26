package de.moinmornhart.stinkendreich.server;

//? if >=26.3 {
import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.netz.ModErkennung;
import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.BlockAwareAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Wie ein Block der Mod für Spieler ohne Mod aussieht. Volle Würfel werden zu einem Notenblock-Zustand, den das
 * Ressourcenpaket mit unserem Modell zeigt. Alle anderen (Möbel, Geräte, Türen …) sind für den Client eine
 * unsichtbare Barriere (gleiche Kollision, anklickbar), darüber zeigt eine Anzeige-Figur das echte Modell.
 */
final class BlockUebersetzung implements PolymerBlock, BlockWithElementHolder {
    /** Alle Modelle, die als Anzeige-Item im Ressourcenpaket gebraucht werden. */
    static final Set<Identifier> ANZEIGE_MODELLE = new TreeSet<>();

    private final Map<BlockState, BlockState> ersatz = new HashMap<>();
    private final Map<BlockState, Blockmodelle.Modell> anzeige = new HashMap<>();

    BlockUebersetzung(Block block) {
        for (BlockState zustand : block.getStateDefinition().getPossibleStates()) {
            Optional<Blockmodelle.Modell> m = Blockmodelle.modell(zustand);
            BlockState e = null;
            if (m.isPresent() && m.get().vollerWuerfel()) {
                e = PolymerBlockResourceUtils.requestBlock(BlockModelType.FULL_BLOCK,
                        PolymerBlockModel.of(m.get().id(), m.get().x(), m.get().y()));
            }
            if (e == null) {
                // Kein freier Texturblock mehr oder kein Würfel: Barriere (bzw. nichts) plus Anzeige-Figur
                boolean fest = !zustand.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
                e = fest ? Blocks.BARRIER.defaultBlockState() : Blocks.STRUCTURE_VOID.defaultBlockState();
                m.ifPresent(modell -> {
                    anzeige.put(zustand, modell);
                    ANZEIGE_MODELLE.add(modell.id());
                });
            }
            ersatz.put(zustand, e);
        }
    }

    @Override
    public BlockState getPolymerBlockState(BlockState zustand, PacketContext kontext) {
        return ersatz.getOrDefault(zustand, Blocks.BARRIER.defaultBlockState());
    }

    @Override
    public boolean canSyncRawToClient(PacketContext kontext) {
        return ModErkennung.hatMod(kontext.get(PacketContext.CONNECTION));
    }

    // ------------------------------------------------------------------
    // Anzeige-Figur mit dem echten Modell
    // ------------------------------------------------------------------

    @Override
    public ElementHolder createElementHolder(ServerLevel welt, BlockPos pos, BlockState zustand) {
        return anzeige.containsKey(zustand) ? new Halter(zustand) : null;
    }

    @Override
    public Vec3 getElementHolderOffset(ServerLevel welt, BlockPos pos, BlockState zustand) {
        return Vec3.ZERO;
    }

    /** Das Anzeige-Item für ein Modell (Papier mit unserem Modell aus dem Ressourcenpaket). */
    static ItemStack anzeigeItem(Identifier modell) {
        ItemStack s = new ItemStack(Items.PAPER);
        s.set(DataComponents.ITEM_MODEL, itemModellId(modell));
        return s;
    }

    /** Name des Item-Modells, das im Ressourcenpaket auf das Blockmodell zeigt. */
    static Identifier itemModellId(Identifier modell) {
        return StinkendReich.id("polymer/" + modell.getNamespace() + "/" + modell.getPath());
    }

    /** Zeigt das Modell des aktuellen Zustands; ändert sich der Zustand (Tür auf), wechselt das Modell mit. */
    private final class Halter extends ElementHolder {
        private final ItemDisplayElement figur = new ItemDisplayElement();

        Halter(BlockState zustand) {
            figur.setItemDisplayContext(ItemDisplayContext.NONE);
            figur.setInvisible(true);
            zeigen(zustand);
            addElement(figur);
        }

        private void zeigen(BlockState zustand) {
            Blockmodelle.Modell m = anzeige.get(zustand);
            if (m == null) {
                figur.setItem(ItemStack.EMPTY);
                return;
            }
            figur.setItem(anzeigeItem(m.id()));
            figur.setLeftRotation(new Quaternionf().rotateY((float) Math.toRadians(-m.y())).rotateX((float) Math.toRadians(-m.x())));
        }

        @Override
        public void notifyUpdate(HolderAttachment.UpdateType art) {
            super.notifyUpdate(art);
            if (art == BlockAwareAttachment.BLOCK_STATE_UPDATE && getAttachment() instanceof BlockAwareAttachment a) {
                zeigen(a.getBlockState());
            }
        }
    }
}
//?}
