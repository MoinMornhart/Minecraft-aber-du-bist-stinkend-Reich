package de.moinmornhart.stinkendreich.client.bildschirm;

import de.moinmornhart.stinkendreich.client.KompatClient;
import de.moinmornhart.stinkendreich.mechanik.Eigenschaften;
import de.moinmornhart.stinkendreich.mechanik.KameraBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.entity.BlockEntity;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else
/*import net.minecraft.client.gui.GuiGraphics;*/

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Blick durch die Überwachungskameras. Der Bildschirm ist durchsichtig; die Spielansicht
 * wird auf eine unsichtbare Hilfsfigur vor der jeweiligen Kamera umgeschaltet.
 * Solange er offen ist, bewegt sich der Spieler nicht.
 */
public class KameraBildschirm extends Screen {
    private static final int SUCHRADIUS_CHUNKS = 6;
    private final List<BlockPos> kameras = new ArrayList<>();
    private int nummer;
    private ArmorStand blick;
    private StringWidget info;

    public KameraBildschirm(BlockPos monitor) {
        super(Component.translatable("block.stinkendreich.monitor"));
        ClientLevel welt = Minecraft.getInstance().level;
        int cx = monitor.getX() >> 4;
        int cz = monitor.getZ() >> 4;
        for (int x = cx - SUCHRADIUS_CHUNKS; x <= cx + SUCHRADIUS_CHUNKS; x++) {
            for (int z = cz - SUCHRADIUS_CHUNKS; z <= cz + SUCHRADIUS_CHUNKS; z++) {
                if (!welt.hasChunk(x, z)) {
                    continue;
                }
                for (BlockEntity be : welt.getChunk(x, z).getBlockEntities().values()) {
                    if (be.getBlockState().getBlock() instanceof KameraBlock) {
                        kameras.add(be.getBlockPos());
                    }
                }
            }
        }
        kameras.sort(Comparator.<BlockPos>comparingInt(p -> p.getY()).thenComparingInt(p -> p.getX()).thenComparingInt(p -> p.getZ()));
    }

    @Override
    protected void init() {
        int y = height - 28;
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> wechseln(-1)).bounds(width / 2 - 110, y, 40, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("stinkendreich.kamera.beenden"), b -> onClose()).bounds(width / 2 - 65, y, 130, 20).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> wechseln(1)).bounds(width / 2 + 70, y, 40, 20).build());
        info = new StringWidget(Component.literal(""), font);
        info.setY(8);
        addRenderableWidget(info);
        zeigen();
    }

    private void wechseln(int schritt) {
        if (!kameras.isEmpty()) {
            nummer = Math.floorMod(nummer + schritt, kameras.size());
            zeigen();
        }
    }

    private void zeigen() {
        Minecraft client = Minecraft.getInstance();
        Component text;
        if (kameras.isEmpty()) {
            text = Component.translatable("stinkendreich.kamera.keine");
        } else {
            BlockPos k = kameras.get(nummer);
            Direction vorne = client.level.getBlockState(k).getValue(Eigenschaften.RICHTUNG);
            if (blick == null) {
                blick = new ArmorStand(client.level, 0, 0, 0);
                blick.setInvisible(true);
                // Eigene Kennnummer: Die Hilfsfigur ist nie in der Welt angemeldet, andere Mods (z. B. NoRisk Client)
                // fragen die Nummer der Kamera-Figur aber ab; ohne Nummer stürzt das Spiel ab
                blick.setId(-424242);
            }
            // Ein halber Block vor der Kamera, leicht nach unten geneigt
            double x = k.getX() + 0.5 + vorne.getStepX() * 0.6;
            double z = k.getZ() + 0.5 + vorne.getStepZ() * 0.6;
            blick.setPos(x, k.getY() - 1.2, z);
            blick.setYRot(vorne.toYRot());
            blick.setYHeadRot(vorne.toYRot());
            blick.setXRot(25.0F);
            blick.xRotO = 25.0F;
            blick.yRotO = vorne.toYRot();
            blick.xo = x;
            blick.yo = k.getY() - 1.2;
            blick.zo = z;
            client.setCameraEntity(blick);
            text = Component.translatable("stinkendreich.kamera.nummer", nummer + 1, kameras.size());
        }
        info.setMessage(text);
        info.setWidth(font.width(text));
        info.setX(width / 2 - font.width(text) / 2);
    }

    @Override
    public void onClose() {
        Minecraft client = Minecraft.getInstance();
        client.setCameraEntity(client.player);
        KompatClient.zeige(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Kein abgedunkelter oder verschwommener Hintergrund: man soll das Kamerabild sehen
    //? if >=26.1 {
    @Override
    public void extractBackground(GuiGraphicsExtractor grafik, int mx, int my, float delta) {
    }
    //?} else {
    /*@Override
    public void renderBackground(GuiGraphics grafik, int mx, int my, float delta) {
    }
    *///?}
}
