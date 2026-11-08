package net.ccbluex.liquidbounce.extras.mixins;

import net.ccbluex.liquidbounce.extras.modules.extras.ModuleDisguise;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The label above an entity's head, and only that. */
@Mixin(EntityRenderer.class)
abstract class MixinEntityRenderer {

    @Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
    private void hookDisguiseNameTag(Entity entity, CallbackInfoReturnable<@Nullable Component> cir) {
        cir.setReturnValue(ModuleDisguise.INSTANCE.nameTag(entity, cir.getReturnValue()));
    }

}
