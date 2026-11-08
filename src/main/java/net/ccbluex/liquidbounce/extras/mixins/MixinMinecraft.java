package net.ccbluex.liquidbounce.extras.mixins;

import net.ccbluex.liquidbounce.extras.modules.pvp.AnchorKit;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Shadow
    public @Nullable HitResult hitResult;

    // A right click on an anchor while an anchor module works on it, or before its cover is up
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void extras$holdAnchorClicks(CallbackInfo ci) {
        if (AnchorKit.blocksUse(hitResult)) {
            ci.cancel();
        }
    }

}
