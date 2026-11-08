package net.ccbluex.liquidbounce.extras.mixins;

import net.ccbluex.liquidbounce.extras.modules.extras.ModuleDisguise;
import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Name tags and other centred text are measured as components, which never pass `MixinFont`. */
@Mixin(StringSplitter.class)
abstract class MixinStringSplitter {

    @Shadow
    public abstract float stringWidth(FormattedCharSequence text);

    @Inject(method = "stringWidth(Lnet/minecraft/network/chat/FormattedText;)F", at = @At("HEAD"), cancellable = true)
    private void hookDisguiseWidth(FormattedText text, CallbackInfoReturnable<Float> cir) {
        var disguised = ModuleDisguise.INSTANCE.disguise(text);
        if (disguised != null) {
            cir.setReturnValue(stringWidth(disguised));
        }
    }

}
