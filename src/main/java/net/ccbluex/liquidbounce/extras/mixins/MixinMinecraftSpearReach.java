package net.ccbluex.liquidbounce.extras.mixins;

import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleSpearAssist;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The attack key of a weapon that misses: SpearAssist may stab with a spear instead of the empty swing. */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftSpearReach {

    @Shadow
    private int missTime;

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void hookSpearReach(CallbackInfoReturnable<Boolean> cir) {
        if (missTime <= 0 && ModuleSpearAssist.stabBeyondReach()) {
            cir.setReturnValue(false);
        }
    }

}
