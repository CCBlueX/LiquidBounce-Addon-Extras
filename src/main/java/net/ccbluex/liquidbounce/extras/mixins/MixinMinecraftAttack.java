package net.ccbluex.liquidbounce.extras.mixins;

import net.ccbluex.liquidbounce.event.EventManager;
import net.ccbluex.liquidbounce.extras.modules.pvp.AttackClickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MixinMinecraftAttack {

    @Shadow
    public @Nullable HitResult hitResult;

    // Past every check that makes vanilla ignore the click, right where it decides between entity, block and air
    @Inject(method = "startAttack", cancellable = true, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/phys/HitResult;getType()Lnet/minecraft/world/phys/HitResult$Type;"))
    private void hookAttackClick(CallbackInfoReturnable<Boolean> cir) {
        var hit = hitResult;
        if (hit != null && EventManager.INSTANCE.callEvent(new AttackClickEvent(hit)).isCancelled()) {
            cir.setReturnValue(false);
        }
    }

}
