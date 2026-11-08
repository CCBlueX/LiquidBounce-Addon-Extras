package net.ccbluex.liquidbounce.extras.mixins;

import net.ccbluex.liquidbounce.extras.modules.extras.ModuleDisguise;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Everything the game draws as text and every width it measures for it passes here. */
@Mixin(Font.class)
abstract class MixinFont {

    @ModifyVariable(method = "prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;",
        at = @At("HEAD"), argsOnly = true)
    private String hookDisguisePlain(String text) {
        return ModuleDisguise.INSTANCE.disguise(text);
    }

    @ModifyVariable(method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;",
        at = @At("HEAD"), argsOnly = true)
    private FormattedCharSequence hookDisguiseFormatted(FormattedCharSequence text) {
        return ModuleDisguise.INSTANCE.disguise(text);
    }

    @ModifyVariable(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), argsOnly = true)
    private String hookDisguisePlainWidth(String text) {
        return ModuleDisguise.INSTANCE.disguise(text);
    }

    @ModifyVariable(method = "width(Lnet/minecraft/util/FormattedCharSequence;)I", at = @At("HEAD"), argsOnly = true)
    private FormattedCharSequence hookDisguiseFormattedWidth(FormattedCharSequence text) {
        return ModuleDisguise.INSTANCE.disguise(text);
    }

}
