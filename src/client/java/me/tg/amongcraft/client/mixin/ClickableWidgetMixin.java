package me.tg.amongcraft.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClickableWidget.class)
public abstract class ClickableWidgetMixin {
    @Shadow protected int x, y, width, height;

    private boolean amongcraft$wasHovered = false;

    @Inject(method = "render", at = @At("TAIL"))
    private void amongcraft$onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;

        if (hovered && !amongcraft$wasHovered) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                client.getSoundManager().play(PositionedSoundInstance.master(
                        SoundEvent.of(new Identifier("amongcraft", "ui.hover")),
                        1.0f
                ));
            }
        }

        amongcraft$wasHovered = hovered;
    }

    @Inject(method = "playDownSound", at = @At("HEAD"), cancellable = true)
    private void amongcraft$customClickSound(SoundManager soundManager, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.getSoundManager().play(PositionedSoundInstance.master(
                    SoundEvent.of(new Identifier("amongcraft", "ui.click")),
                    1.0f
            ));
        }
        ci.cancel(); // Cancel default sound
    }

}