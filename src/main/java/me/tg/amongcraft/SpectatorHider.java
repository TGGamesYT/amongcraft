package me.tg.amongcraft;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import me.tg.amongcraft.mixin.PlayerListS2CPacketAccessor;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class SpectatorHider {

    public static void register() {
        // Optional: You could log this or hook server start events here
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            System.out.println("[SpectatorHider] Registered spectator hiding logic.");
        });
    }

    public static boolean canSeeSpectators(ServerPlayerEntity player) {
        return player.isSpectator() || player.hasPermissionLevel(2);
    }

    public static boolean canSeeOther(ServerPlayerEntity viewer, ServerPlayerEntity subject) {
        return viewer.equals(subject) || canSeeSpectators(viewer);
    }

    public static PlayerListS2CPacket cloneWithMappedEntries(PlayerListS2CPacket packet, Function<PlayerListS2CPacket.Entry, PlayerListS2CPacket.Entry> mapper) {
        PlayerListS2CPacket newPacket = new PlayerListS2CPacket(packet.getActions(), List.of());
        ((PlayerListS2CPacketAccessor) newPacket).setEntries(packet.getEntries().stream().map(mapper).toList());
        return newPacket;
    }

    public static PlayerListS2CPacket.Entry spoofGameMode(PlayerListS2CPacket.Entry entry, GameMode newGameMode) {
        return new PlayerListS2CPacket.Entry(entry.profileId(), entry.profile(), entry.listed(), entry.latency(), newGameMode, entry.displayName(), entry.chatSession());
    }
}
