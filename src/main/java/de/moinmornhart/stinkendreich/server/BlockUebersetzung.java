package de.moinmornhart.stinkendreich.server;

//? if >=1.21.6 {
import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.netz.ModErkennung;
import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.BlockAwareAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import eu.pb4.polymer.virtualentity.api.elements.InteractionElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
//? if >=26.1 {
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
//?} else
/*import xyz.nucleoid.packettweaker.PacketContext;*/
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Wie ein Block der Mod für Spieler ohne Mod aussieht. Volle Würfel werden zu einem Notenblock-Zustand, den das
 * Ressourcenpaket mit unserem Modell zeigt. Alle anderen (Möbel, Geräte, Türen …) sind für den Client eine
 * unsichtbare Barriere (gleiche Kollision, anklickbar), darüber zeigt eine Anzeige-Figur das echte Modell. Blöcke
 * ohne Kollision (z. B. die unsichtbaren Teile des Flügels) bekommen ein Klickfeld in ihrer Größe.
 * <p>
 * Wichtig ist {@link PolymerTexturedBlock}: Ohne diese Kennzeichnung macht Polymer aus den Notenblock-Zuständen mit
 * unseren Modellen wieder einen gewöhnlichen Notenblock (das ist für echte Notenblöcke gedacht).
 */
final class BlockUebersetzung implements PolymerTexturedBlock, BlockWithElementHolder {
    /** Alle Modelle, die als Anzeige-Item im Ressourcenpaket gebraucht werden. */
    static final Set<Identifier> ANZEIGE_MODELLE = new TreeSet<>();

    private final Map<BlockState, BlockState> ersatz = new HashMap<>();
    private final Map<BlockState, Blockmodelle.Modell> anzeige = new HashMap<>();
    /** Umriss der Blöcke ohne Kollision (für das Klickfeld). */
    private final Map<BlockState, AABB> klickfeld = new HashMap<>();

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
                VoxelShape umriss = zustand.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                if (!fest && !umriss.isEmpty()) {
                    klickfeld.put(zustand, umriss.bounds());
                }
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
        if (ServerPaket.mitMod(kontext)) {
            return zustand;     // Spieler mit Mod: der echte Block
        }
        return ersatz.getOrDefault(zustand, Blocks.BARRIER.defaultBlockState());
    }

    @Override
    public boolean canSyncRawToClient(PacketContext kontext) {
        return ServerPaket.mitMod(kontext);
    }

    // ------------------------------------------------------------------
    // Anzeige-Figur mit dem echten Modell
    // ------------------------------------------------------------------

    @Override
    public ElementHolder createElementHolder(ServerLevel welt, BlockPos pos, BlockState zustand) {
        return anzeige.containsKey(zustand) || klickfeld.containsKey(zustand) ? new Halter(zustand) : null;
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
    private final class Halter extends FahrzeugAnzeige.NurOhneMod {
        private final ItemDisplayElement figur = new ItemDisplayElement();
        /** Klickfeld: leitet Rechtsklicks an den echten Block weiter, als hätte der Spieler ihn angeklickt. */
        private final InteractionElement klick = new InteractionElement(new VirtualElement.InteractionHandler() {
            @Override
            //? if >=26.1 {
            public void interact(ServerPlayer spieler, InteractionHand hand, Vec3 ort, boolean zweite) {
            //?} else
            /*public void interact(ServerPlayer spieler, InteractionHand hand) {*/
                if (getAttachment() instanceof BlockAwareAttachment a) {
                    BlockPos pos = a.getBlockPos();
                    if (de.moinmornhart.stinkendreich.dev.Autotest.aktiv()) {
                        StinkendReich.LOGGER.info("Autotest: Klickfeld bei {} von {}", pos, spieler.getScoreboardName());
                    }
                    spieler.gameMode.useItemOn(spieler, spieler.level(), spieler.getItemInHand(hand), hand,
                            new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
                }
            }
        });

        Halter(BlockState zustand) {
            figur.setItemDisplayContext(ItemDisplayContext.NONE);
            figur.setInvisible(true);
            zeigen(zustand);
            addElement(figur);
            if (!klickfeld.isEmpty()) {
                addElement(klick);
            }
        }

        private void zeigen(BlockState zustand) {
            AABB feld = klickfeld.get(zustand);
            if (feld != null) {
                // Das Klickfeld steht mit seiner Unterseite mittig im Umriss (Halter-Ursprung = Blockmitte)
                klick.setSize((float) Math.max(feld.getXsize(), feld.getZsize()), (float) feld.getYsize());
                klick.setOffset(new Vec3(feld.getCenter().x - 0.5, feld.minY - 0.5, feld.getCenter().z - 0.5));
            } else {
                klick.setSize(0.0F, 0.0F);
            }
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
