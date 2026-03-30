package me.tg.amongcraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.world.GameMode;
import me.tg.amongcraft.SpectatorHider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

@Mixin(ServerPlayerInteractionManager.class)
public class ServerPlayerInteractionManagerMixin {

    @Shadow @Final protected ServerPlayerEntity player;

    @WrapOperation(method = "changeGameMode", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;sendToAll(Lnet/minecraft/network/packet/Packet;)V"))
    private void onGameModeChange(PlayerManager manager, Packet<?> packet, Operation<Void> original) {
        if (player.isSpectator()) {
            for (ServerPlayerEntity other : manager.getPlayerList()) {
                if (SpectatorHider.canSeeOther(other, player)) {
                    other.networkHandler.sendPacket(packet);
                }
            }

            List<ServerPlayerEntity> others = manager.getPlayerList().stream()
                    .filter(p -> !p.equals(player) && p.isSpectator() && SpectatorHider.canSeeOther(player, p))
                    .toList();

            if (!others.isEmpty()) {
                player.networkHandler.sendPacket(new PlayerListS2CPacket(EnumSet.of(PlayerListS2CPacket.Action.UPDATE_GAME_MODE), others));
            }

        } else {
            original.call(Objects.requireNonNull(player.getServer()).getPlayerManager(), packet);

            if (!SpectatorHider.canSeeSpectators(player)) {
                List<ServerPlayerEntity> hidden = manager.getPlayerList().stream()
                        .filter(p -> !p.equals(player) && p.isSpectator())
                        .toList();

                if (!hidden.isEmpty()) {
                    PlayerListS2CPacket spoofed = SpectatorHider.cloneWithMappedEntries(
                            new PlayerListS2CPacket(EnumSet.of(PlayerListS2CPacket.Action.UPDATE_GAME_MODE), hidden),
                            entry -> SpectatorHider.spoofGameMode(entry, GameMode.SURVIVAL)
                    );
                    player.networkHandler.sendPacket(spoofed);
                }
            }
        }
    }
}
