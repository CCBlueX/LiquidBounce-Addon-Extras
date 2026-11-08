package net.ccbluex.liquidbounce.extras.mixins;

import net.ccbluex.liquidbounce.extras.modules.extras.ModuleDisguise;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The faces in the player list. */
@Mixin(PlayerInfo.class)
abstract class MixinPlayerInfo {

    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void hookDisguiseSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        var skin = ModuleDisguise.INSTANCE.skinOf(((PlayerInfo) (Object) this).getProfile().id());
        if (skin != null) {
            cir.setReturnValue(skin);
        }
    }

}
