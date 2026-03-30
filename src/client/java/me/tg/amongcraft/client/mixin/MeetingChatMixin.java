package me.tg.amongcraft.client.mixin;

import me.tg.amongcraft.client.EmergencyMeetingScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.network.message.MessageType;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public class MeetingChatMixin {
    @Inject(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;ILnet/minecraft/client/gui/hud/MessageIndicator;Z)V",
            at = @At("TAIL")
    )
    private void onAddMessage(Text message, @Nullable MessageSignatureData signature, int ticks, @Nullable MessageIndicator indicator, boolean refresh, CallbackInfo ci) {
        if (EmergencyMeetingScreen.chatMessages != null) {
            EmergencyMeetingScreen.chatMessages.add(message.getString());
            if (EmergencyMeetingScreen.chatMessages.size() > 100) {
                EmergencyMeetingScreen.chatMessages.remove(0);
            }
        }
    }
}