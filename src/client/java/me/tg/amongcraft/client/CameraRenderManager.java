package me.tg.amongcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import me.tg.amongcraft.CameraSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.DynamicTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.world.World;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class CameraRenderManager {
    private static final CameraRenderManager INSTANCE = new CameraRenderManager();

    // Unique key: identifier + num (e.g., "main:1")
    private final Map<String, CameraEntry> cameras = new HashMap<>();
    private final MinecraftClient client = MinecraftClient.getInstance();

    public static CameraRenderManager getInstance() {
        return INSTANCE;
    }

    public void updateMonitors(List<CameraSystem.MonitorData> monitors) {
        Set<String> seen = new HashSet<>();

        for (CameraSystem.MonitorData monitor : monitors) {
            for (CameraSystem.CameraData cam : monitor.cameras) {
                String key = cam.identifier + ":" + cam.num;
                seen.add(key);

                // Register new cameras if missing
                registerCamera(cam.identifier, cam.num, cam.pos);
            }
        }

        // Optionally unregister any stale cameras
        cameras.keySet().removeIf(key -> {
            if (!seen.contains(key)) {
                unregisterCamera(key.split(":")[0], Integer.parseInt(key.split(":")[1]));
                return true;
            }
            return false;
        });
    }


    public void registerCamera(String identifier, int num, BlockPos pos) {
        String key = identifier + ":" + num;
        if (!cameras.containsKey(key)) {
            cameras.put(key, new CameraEntry(pos));
        }
    }

    public void unregisterCamera(String identifier, int num) {
        String key = identifier + ":" + num;
        CameraEntry entry = cameras.remove(key);
        if (entry != null) {
            entry.destroy();
        }
    }

    public void tick() {
        if (client.world == null || client.player == null) return;

        for (Map.Entry<String, CameraEntry> entry : cameras.entrySet()) {
            entry.getValue().update(client.world);
        }
    }

    public Identifier getCameraTexture(String identifier, int num) {
        String key = identifier + ":" + num;
        CameraEntry entry = cameras.get(key);
        return entry != null ? entry.getTexture() : SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
    }

    public void clear() {
        for (CameraEntry entry : cameras.values()) {
            entry.destroy();
        }
        cameras.clear();
    }

    public class CameraEntry {
        private static final int WIDTH = 256;
        private static final int HEIGHT = 144;

        private float yaw = 0;
        private final SimpleFramebuffer framebuffer;
        private final BlockPos pos;
        private final NativeImageBackedTexture dynamicTexture;
        private static Identifier textureId = null;

        public CameraEntry(BlockPos pos) {
            this.pos = pos;
            this.framebuffer = new SimpleFramebuffer(WIDTH, HEIGHT, true, MinecraftClient.IS_SYSTEM_MAC);

            // Create an empty NativeImage and wrap in a dynamic texture
            NativeImage nativeImage = new NativeImage(WIDTH, HEIGHT, true);
            this.dynamicTexture = new NativeImageBackedTexture(nativeImage);

            this.textureId = MinecraftClient.getInstance().getTextureManager()
                    .registerDynamicTexture("camera_" + pos.toShortString(), dynamicTexture);
        }

        public static Identifier getTexture() {
            return textureId;
        }

        public void update(World world) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.world == null) return;

            yaw = (yaw + 1.5f) % 360;

            framebuffer.beginWrite(true);
            RenderSystem.clear(0, true);

            // Backup player state
            Vec3d oldPos = client.player.getPos();
            float oldYaw = client.player.getYaw(), oldPitch = client.player.getPitch();

            // Temporarily move player camera
            Vec3d eye = Vec3d.ofCenter(pos).add(0, 1.5, 0);
            client.player.setPosition(eye);
            client.player.setYaw(yaw);
            client.player.setPitch(-30f);
            client.gameRenderer.getCamera().update(world, client.player, false, false, client.getTickDelta());

            client.gameRenderer.renderWorld(client.getTickDelta(), System.nanoTime(), new MatrixStack());

            // Restore player state
            client.player.setPosition(oldPos);
            client.player.setYaw(oldYaw);
            client.player.setPitch(oldPitch);
            client.gameRenderer.getCamera().update(world, client.player, false, false, client.getTickDelta());

            framebuffer.endWrite();

            // Restore default framebuffer
            client.getFramebuffer().beginWrite(true);
        }


        public void destroy() {
            framebuffer.delete();
        }
    }

}
