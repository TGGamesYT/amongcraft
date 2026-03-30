package me.tg.amongcraft;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.world.World;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.world.ServerWorldAccess;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

public class DeathListener {

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return;

            ServerWorld world = player.getServerWorld();
            Vec3d pos = player.getPos();

            // Check if the player was a noisemaker
            boolean wasNoisemaker = "Noisemaker".equalsIgnoreCase(AmongCraftCommands.getPlayerRole(player.getUuid()));

            String extraNbt = wasNoisemaker ? ",Glowing:1b" : "";

            // Build the summon command
            String command = String.format(
                    "execute in %s run summon amongcraft:dead_body %.2f %.2f %.2f {PlayerUUID:%s%s}",
                    world.getRegistryKey().getValue(),
                    pos.x, pos.y, pos.z,
                    player.getUuid(),
                    extraNbt
            );

            try {
                ServerCommandSource silentSource = new ServerCommandSource(
                        world.getServer(),
                        world.getSpawnPos().toCenterPos(),
                        Vec2f.ZERO,
                        world,
                        4,
                        "System",
                        Text.literal("System").formatted(Formatting.GRAY),
                        world.getServer(),
                        null
                ).withSilent();
                world.getServer().getCommandManager().executeWithPrefix(
                        silentSource,
                        command
                );
            } catch (Exception e) {
                Amongcraft.LOGGER.error("Failed to execute corpse summon command", e);
            }
        });
    }

    public static void clearBodies(ServerWorld world) {
        String worldId = world.getRegistryKey().getValue().toString();
        String command = String.format(
                "/execute in %s run kill @e[type=amongcraft:dead_body]",
                worldId
        );

        try {
            ServerCommandSource silentSource = new ServerCommandSource(
                    world.getServer(),
                    world.getSpawnPos().toCenterPos(),
                    Vec2f.ZERO,
                    world,
                    4,
                    "System",
                    Text.literal("System").formatted(Formatting.GRAY),
                    world.getServer(),
                    null
            ).withSilent();

            world.getServer().getCommandManager().executeWithPrefix(
                    silentSource,
                    command
            );
            Amongcraft.LOGGER.info("Cleared all amongcraft:dead_body entities in world: " + worldId);
        } catch (Exception e) {
            Amongcraft.LOGGER.error("Failed to clear dead bodies using command in world: " + worldId, e);
        }
    }

}

