package me.tg.amongcraft.mixin;

import me.tg.amongcraft.AmongCraftCommands;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin {

    @Inject(method = "onChatMessage", at = @At("HEAD"), cancellable = true)
    private void onChatMessage(ChatMessageC2SPacket packet, CallbackInfo ci) {
        ServerPlayNetworkHandler self = (ServerPlayNetworkHandler) (Object) this;
        ServerPlayerEntity player = self.getPlayer();
        if (player == null) return;

        if (AmongCraftCommands.dead.contains(player.getUuid())) {
            String message = packet.chatMessage();

            Text deadChatMessage = Text.literal("<" + player.getName().getString() + "> " + message)
                    .formatted(Formatting.GRAY);

            player.getServer().getPlayerManager().getPlayerList().forEach(otherPlayer -> {
                if (AmongCraftCommands.dead.contains(otherPlayer.getUuid())) {
                    otherPlayer.sendMessage(deadChatMessage, false);
                }
            });

            ci.cancel();
        }
    }
}
