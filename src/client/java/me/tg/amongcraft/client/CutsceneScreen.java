package me.tg.amongcraft.client;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import me.tg.amongcraft.Amongcraft;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.texture.PlayerSkinProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;


import java.util.*;

public class CutsceneScreen extends Screen {
    private final Identifier background;
    private final Identifier sound;
    private final boolean fading;
    private final boolean redColor;
    private final Set<UUID> playerUUIDs;

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final List<PlayerEntity> displayPlayers = new ArrayList<>();
    private SoundInstance playingSound;
    private long startTime;
    private int fadeOutTicks = -1;
    private float fadeOpacity = 0.0f;

    public CutsceneScreen(Identifier background, Identifier sound, boolean fading, Set<UUID> displayPlayers, boolean redColor) {
        super(Text.literal("Cutscene"));
        this.background = background;
        this.sound = sound;
        this.fading = fading;
        this.redColor = redColor;
        this.playerUUIDs = (displayPlayers == null) ? null : new HashSet<>(displayPlayers);

        Amongcraft.LOGGER.info("opening CutsceneScreen(Identifier background: " + background.toString() + ", Identifier sound " + sound.toString() + ", boolena fading " + fading + ", Set<UUID> displayPlayers " + displayPlayers + ", boolean redcolor " + redColor + ")");
        if (client.getResourceManager().getResource(new Identifier("amongcraft", "textures/victory")).isPresent()) {
            System.err.println("❌ Texture NOT found in resource manager!");
        } else {
            System.out.println("✅ Texture found.");
        }
    }

    @Override
    protected void init() {
        startTime = System.currentTimeMillis();

        if (playerUUIDs != null) {
            UUID selfUUID = client.player.getUuid();
            playerUUIDs.remove(selfUUID);

            displayPlayers.add(client.player);

            for (UUID uuid : playerUUIDs) {
                GameProfile profile = new GameProfile(uuid, null);

                // Fill the GameProfile with name + properties (from Mojang session server)
                client.getSessionService().fillProfileProperties(profile, false); // false = not require secure

                String playerName = profile.getName(); // This should now be non-null

                client.getSkinProvider().loadSkin(profile,
                        (type, textureLocation, texture) -> {
                            if (type == MinecraftProfileTexture.Type.SKIN) {
                                CustomSkinnedPlayer custom = new CustomSkinnedPlayer(
                                        client,
                                        client.world,
                                        profile,
                                        textureLocation
                                );
                                displayPlayers.add(custom);
                            }
                        },
                        false
                );
            }
        }

        playingSound = PositionedSoundInstance.master(SoundEvent.of(sound), 1.0F);
        client.getSoundManager().play(playingSound);
    }

    @Override
    public void tick() {
        if (fadeOutTicks == -1 && !client.getSoundManager().isPlaying(playingSound)) {
            fadeOutTicks = 20; // 1 second fade out
        }

        if (fadeOutTicks > 0) {
            fadeOpacity = 1.0f - (fadeOutTicks / 20f);
            fadeOutTicks--;
        } else if (fadeOutTicks == 0) {
            client.setScreen(null);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MatrixStack matrices = context.getMatrices();

        // Background
        context.drawTexture(background, 0, 0, 0, 0, width, height, width, height);

        // Fade in
        if (fading && fadeOutTicks == -1) {
            float time = (System.currentTimeMillis() - startTime) / 1000F;
            float fadeInOpacity = 1.0f - MathHelper.clamp(time / 1.0F, 0.0F, 1.0F);
            int fadeColor = (int) (fadeInOpacity * 255) << 24;
            context.fill(0, 0, width, height, fadeColor);
        }

        // Players (if any)
        if (playerUUIDs != null) {
            int centerX = width / 2;
            int baseY = height / 2 + 60;
            int centerIndex = 0;

            for (int i = 0; i < displayPlayers.size(); i++) {
                PlayerEntity player = displayPlayers.get(i);
                int offset = i - centerIndex;
                int spacing = 90;
                int x = centerX + (offset * spacing);
                int y = baseY;
                int scale = Math.max(20, 40 - Math.abs(offset) * 8);

                drawEntity(context, x, y, scale, player);

                String name = player.getName().getString();
                int textWidth = client.textRenderer.getWidth(name);
                int textX = x - textWidth / 2;
                int textY = y + 50;
                int color = redColor ? 0xFF5555 : 0xFFFFFF;
                context.drawText(client.textRenderer, name, textX, textY, color, true);
            }
        }

        // Fade out
        if (fadeOutTicks >= 0) {
            int alpha = (int) (fadeOpacity * 255);
            context.fill(0, 0, width, height, (alpha << 24));
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawEntity(DrawContext context, int x, int y, int scale, LivingEntity entity) {
        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
        MatrixStack matrices = context.getMatrices();

        // Fallback name
        if (entity.getName() == null || entity.getName().getString() == null) {
            entity.setCustomName(Text.literal("Player"));
        }

        matrices.push();
        matrices.translate(x, y, 1050.0);
        matrices.scale(scale, -scale, scale); // Flip Y (fix upside down)

        // Rotate so the entity faces the camera, ignoring its in-world rotation
        // This flips the entity to face front on (0 degrees yaw)
        matrices.multiply(new Quaternionf().rotateY((float) Math.toRadians(180F)));

        dispatcher.setRenderShadows(false);

        // Render with zero yaw and pitch so it faces front
        float yaw = 0f;
        float pitch = 0f;

        dispatcher.render(entity, 0, 0, 0, yaw, 1.0F, matrices, context.getVertexConsumers(), 15728880);
        dispatcher.setRenderShadows(true);

        matrices.pop();
    }


    @Override
    public boolean shouldPause() {
        return false;
    }
    public static class CustomSkinnedPlayer extends OtherClientPlayerEntity {
        private final Identifier customSkin;

        public CustomSkinnedPlayer(MinecraftClient client, ClientWorld world, GameProfile profile, Identifier skin) {
            super(world, profile);
            this.customSkin = skin;
        }

        @Override
        public Identifier getSkinTexture() {
            return customSkin;
        }
    }
}
