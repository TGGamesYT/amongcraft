package me.tg.amongcraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import me.tg.amongcraft.SpectatorHider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static javax.swing.UIManager.get;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {

    @WrapOperation(method = "onPlayerConnect", at = @At(value = "INVOKE", ordinal = 5, target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V"))
    private void hideOthersFromPlayer(ServerPlayNetworkHandler handler, Packet<?> packet, Operation<Void> original) {
        ServerPlayerEntity player = handler.getPlayer();
        if (packet instanceof PlayerListS2CPacket pkt && !SpectatorHider.canSeeSpectators(player)) {
            pkt = SpectatorHider.cloneWithMappedEntries(pkt, entry -> SpectatorHider.spoofGameMode(entry, GameMode.SURVIVAL));
            original.call(handler, pkt);
        } else {
            original.call(handler, packet);
        }
    }

    @WrapOperation(method = "onPlayerConnect", at = @At(value = "INVOKE", ordinal = 0, target = "Lnet/minecraft/server/PlayerManager;sendToAll(Lnet/minecraft/network/packet/Packet;)V"))
    private void hidePlayerFromOthers(PlayerManager manager, Packet<?> packet, Operation<Void> original) {
        if (!(packet instanceof PlayerListS2CPacket pkt)) {
            original.call(manager, packet);
            return;
        }

        PlayerListS2CPacket.Entry entry = pkt.getEntries().get(0);
        ServerPlayerEntity connecting = manager.getPlayer(entry.profileId());
        if (connecting != null && connecting.isSpectator()) {
            PlayerListS2CPacket fakePacket = SpectatorHider.cloneWithMappedEntries(pkt, e -> SpectatorHider.spoofGameMode(e, GameMode.SURVIVAL));
            for (ServerPlayerEntity other : manager.getPlayerList()) {
                other.networkHandler.sendPacket(SpectatorHider.canSeeOther(other, connecting) ? pkt : fakePacket);
            }
        } else {
            original.call(manager, packet);
        }
    }
}
