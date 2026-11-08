package net.ccbluex.liquidbounce.extras.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.ccbluex.liquidbounce.extras.modules.qol.ModuleNoItemBounce;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Hud.class)
abstract class MixinHud {

    @ModifyExpressionValue(
        method = "extractSlot",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getPopTime()I")
    )
    private int hookItemBounce(int popTime) {
        return ModuleNoItemBounce.INSTANCE.popTime(popTime);
    }

}
