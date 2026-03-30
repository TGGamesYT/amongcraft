package me.tg.amongcraft;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static me.tg.amongcraft.AmongCraftCommands.*;

public class AmongCraftWinListener {

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(AmongCraftWinListener::onTick);
    }

    private static void onTick(MinecraftServer server) {
        int impostorCount = 0;
        int crewmateCount = 0;

        Set<UUID> alive = new HashSet<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID uuid = player.getUuid();
            if (dead.contains(uuid)) continue;

            alive.add(uuid);
            if (impostors.contains(uuid)) {
                impostorCount++;
            } else if (crewmates.contains(uuid)) {
                crewmateCount++;
            }
        }

        if (!alive.isEmpty()) {
            if (GameState.isRunning()) {
                if (impostorCount >= crewmateCount && impostorCount > 0) {
                    // Impostors win
                    server.getPlayerManager().broadcast(Text.literal("§cImpostors win!"), false);
                    killAll(server);
                    TaskProgressTracker.removeTaskMap(server);
                    GameState.setRunning(false);
                } else if (impostorCount == 0) {
                    // Crewmates win
                    server.getPlayerManager().broadcast(Text.literal("§aCrewmates win!"), false);
                    killAll(server);
                    GameState.setRunning(false);
                }
            }
        }
    }

    public static void killAll(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            AmongCraftCommands.setPlayerDead(player);
        }
    }
}
