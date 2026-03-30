package me.tg.amongcraft.client.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

import me.tg.amongcraft.client.DisguiseSkinCache;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    public void onGetDisplayName(CallbackInfoReturnable<Text> cir) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        UUID uuid = player.getUuid();

        if (DisguiseSkinCache.hasDisguiseFor(uuid)) {
            // Replace with your disguised name, e.g.
            String disguisedName = DisguiseSkinCache.getDisguisedName(uuid);
            if (disguisedName != null) {
                cir.setReturnValue(Text.literal(disguisedName));
            }
        }
    }
}