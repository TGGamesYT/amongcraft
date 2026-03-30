package me.tg.amongcraft.client;


import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.util.Identifier;
import me.tg.amongcraft.DeadBodyEntity;

import java.util.Optional;
import java.util.UUID;

public class DeadBodyEntityRenderer extends EntityRenderer<DeadBodyEntity> {
    public static SkeletonEntity fakeSkeleton = null;

    public static SkeletonEntity getFakeSkeleton() {
        if (fakeSkeleton == null) {
            fakeSkeleton = new SkeletonEntity(EntityType.SKELETON, MinecraftClient.getInstance().world);
            fakeSkeleton.setPose(EntityPose.SLEEPING);
            fakeSkeleton.prevHeadYaw = 25;
            fakeSkeleton.setHeadYaw(25);
        }
        return fakeSkeleton;
    }

    public DeadBodyEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(DeadBodyEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        MinecraftClient client = MinecraftClient.getInstance();
        matrices.push();
        matrices.translate(1.0, 0.0, 0.0);

        Optional<UUID> playerUuid = entity.getPlayerUuid();
        if (playerUuid.isPresent() && entity.age < 6000) {
            UUID uuid = playerUuid.get();

            GameProfile profile = client.getSessionService().fillProfileProperties(new GameProfile(uuid, null), false);
            if (profile == null || profile.getName() == null) {
                profile = new GameProfile(uuid, "Unknown");
            }

            ClientPlayerEntity fakePlayer = new ClientPlayerEntity(
                    client, client.world,
                    new ClientPlayNetworkHandler(null, null, new ClientConnection(NetworkSide.CLIENTBOUND), null, profile, null),
                    null, null, false, false
            ) {
                @Override
                public boolean shouldRenderName() {
                    return false;
                }
            };

            fakePlayer.setPose(EntityPose.SLEEPING);
            fakePlayer.prevHeadYaw = 25;
            fakePlayer.setHeadYaw(25);
            fakePlayer.setUuid(uuid);

            client.getEntityRenderDispatcher().getRenderer(fakePlayer).render(fakePlayer, 0f, tickDelta, matrices, vertexConsumers, light);
        } else {
            SkeletonEntity skeleton = getFakeSkeleton();
            client.getEntityRenderDispatcher().getRenderer(skeleton).render(skeleton, 0f, tickDelta, matrices, vertexConsumers, light);
        }
        matrices.pop();
    }

    @Override
    public Identifier getTexture(DeadBodyEntity entity) {
        return null;
    }
}
