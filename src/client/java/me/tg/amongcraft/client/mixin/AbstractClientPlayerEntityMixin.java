package me.tg.amongcraft.client.mixin;

import me.tg.amongcraft.client.DisguiseSkinCache;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {

    @Inject(method = "getSkinTexture", at = @At("HEAD"), cancellable = true)
    public void injectCustomSkin(CallbackInfoReturnable<Identifier> cir) {
        AbstractClientPlayerEntity player = (AbstractClientPlayerEntity) (Object) this;

        Identifier disguised = DisguiseSkinCache.getCustomSkinFor(player.getUuid());
        if (disguised != null) {
            cir.setReturnValue(disguised);
        }
    }
}