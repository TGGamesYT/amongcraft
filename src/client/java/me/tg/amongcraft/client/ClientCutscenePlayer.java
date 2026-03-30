package me.tg.amongcraft.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class ClientCutscenePlayer {
    private static final int FRAME_RATE = 20;
    public static final Identifier CUTSCENE_PACKET = new Identifier("amongcraft", "cutscene_packet");

    public static void playCutscene(String cutsceneId, List<UUID> actorUuids) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.setScreen(new CutsceneScreen(cutsceneId, actorUuids));
    }

    public static class CutsceneScreen extends Screen {
        private final String cutsceneId; // Changed to String
        private final List<UUID> actorUuids;
        private int frame = 0;
        private long lastFrameTime = 0;
        private NativeImageBackedTexture currentTexture;
        private final Identifier currentTextureId = new Identifier("amongcraft", "cutscene_current");

        protected CutsceneScreen(String cutsceneId, List<UUID> actorUuids) {
            super(Text.of("Cutscene"));
            this.cutsceneId = cutsceneId;
            this.actorUuids = actorUuids;
        }

        @Override
        public void render(net.minecraft.client.gui.DrawContext ctx, int mouseX, int mouseY, float delta) {
            super.render(ctx, mouseX, mouseY, delta);
            if (System.currentTimeMillis() - lastFrameTime >= 1000 / FRAME_RATE) {
                lastFrameTime = System.currentTimeMillis();
                loadNextFrame();
                frame++;
            }
            if (currentTexture != null) {
                MinecraftClient.getInstance().getTextureManager().bindTexture(currentTextureId);
                ctx.drawTexture(currentTextureId, 0, 0, 0, 0, width, height, width, height);
            }
        }

        private void loadNextFrame() {
            try {
                MinecraftClient client = MinecraftClient.getInstance();

                Identifier frameId = new Identifier("amongcraft", "cutscene/" + cutsceneId + "/frame/" + frame + ".png");
                Identifier maskId = new Identifier("amongcraft", "cutscene/" + cutsceneId + "/players/" + frame + ".png");

                InputStream frameStream = client.getResourceManager().getResource(frameId).orElseThrow().getInputStream();
                InputStream maskStream = client.getResourceManager().getResource(maskId).orElseThrow().getInputStream();

                NativeImage frameImage = NativeImage.read(frameStream);
                NativeImage maskImage = NativeImage.read(maskStream);

                NativeImage projected = projectSkinColors(maskImage, actorUuids);
                overlayImage(frameImage, projected);

                if (currentTexture != null) currentTexture.close();
                currentTexture = new NativeImageBackedTexture(frameImage);
                client.getTextureManager().registerTexture(currentTextureId, currentTexture);

            } catch (Exception e) {
                // End cutscene gracefully on error or at end of frames
                MinecraftClient.getInstance().setScreen(null);
            }
        }

        private NativeImage projectSkinColors(NativeImage mask, List<UUID> actorUuids) throws IOException {
            MinecraftClient client = MinecraftClient.getInstance();
            NativeImage output = new NativeImage(mask.getWidth(), mask.getHeight(), true);

            Map<Integer, NativeImage> colorToSkin = new HashMap<>();

            for (int i = 0; i < actorUuids.size(); i++) {
                UUID uuid = actorUuids.get(i);
                PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(uuid);
                Identifier skinId = entry != null ? entry.getSkinTexture() : DefaultSkinHelper.getTexture(uuid);

                InputStream in = client.getResourceManager().getResource(skinId).orElseThrow().getInputStream();
                NativeImage skin = NativeImage.read(in);

                // Assuming one color per actor is stored on mask row 0 at x = i
                int marker = mask.getColor(i, 0);
                colorToSkin.put(marker, skin);
            }

            for (int x = 0; x < mask.getWidth(); x++) {
                for (int y = 0; y < mask.getHeight(); y++) {
                    int color = mask.getColor(x, y);
                    if (colorToSkin.containsKey(color)) {
                        NativeImage skin = colorToSkin.get(color);
                        int sx = 8 + (x % 8);
                        int sy = 8 + (y % 8);
                        output.setColor(x, y, skin.getColor(sx, sy));
                    } else {
                        output.setColor(x, y, 0x00000000);
                    }
                }
            }

            return output;
        }

        private void overlayImage(NativeImage base, NativeImage overlay) {
            for (int x = 0; x < base.getWidth(); x++) {
                for (int y = 0; y < base.getHeight(); y++) {
                    int over = overlay.getColor(x, y);
                    if ((over >> 24 & 0xFF) > 10) {
                        base.setColor(x, y, over);
                    }
                }
            }
        }
    }
}
