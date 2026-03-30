package me.tg.amongcraft;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.Map;

import java.util.UUID;

import static me.tg.amongcraft.AmongCraftCommands.*;

public class AmongCraftListeners {

    public static final Identifier SAVE_SETTINGS = new Identifier("amongcraft", "save_settings");
    private static final Map<UUID, BlockPos> deathLocations = new HashMap<>();

    public static void register() {
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!GameState.isRunning()) return;
            if (!oldPlayer.isDead()) return;
            UUID uuid = newPlayer.getUuid();
            if (dead.contains(uuid)) return;

            AmongCraftCommands.setPlayerDead(newPlayer);
            BlockPos pos = deathLocations.remove(uuid);
            if (pos != null) {
                newPlayer.teleport(newPlayer.getServerWorld(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, newPlayer.getYaw(), newPlayer.getPitch());
            }
        });

        // In case respawn event misses it, also hook player death directly
        ServerPlayerEvents.ALLOW_DEATH.register((player, damageSource, damageAmount) -> {
            if (!GameState.isRunning()) return true;
            UUID uuid = player.getUuid();

            deathLocations.put(uuid, player.getBlockPos());

            if (!dead.contains(uuid)) {
                AmongCraftCommands.setPlayerDead(player);
            }
            return true; // allow death to proceed
        });

        ServerPlayNetworking.registerGlobalReceiver(SAVE_SETTINGS, (server, player, handler, buf, responseSender) -> {
            String json = buf.readString();

            server.execute(() -> handleSaveSettings(json, player));
        });

    }

    private static void handleSaveSettings(String json, ServerPlayerEntity player) {
        try {
            JsonObject settingsJson = JsonParser.parseString(json).getAsJsonObject();
            SettingsManager.save(settingsJson);
            player.sendMessage(Text.literal("§aSettings saved."), false);
        } catch (Exception e) {
            player.sendMessage(Text.literal("§cInvalid settings JSON!"), false);
            e.printStackTrace();
        }
    }
}
