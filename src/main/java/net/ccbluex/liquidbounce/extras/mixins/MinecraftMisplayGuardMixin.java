package net.ccbluex.liquidbounce.extras.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleMisplayGuard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The player's own clicks, before anything reaches the server. */
@Mixin(Minecraft.class)
public abstract class MinecraftMisplayGuardMixin {

    @Shadow
    public @Nullable HitResult hitResult;

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void extras$guardAttack(CallbackInfoReturnable<Boolean> cir) {
        if (hitResult instanceof EntityHitResult hit && ModuleMisplayGuard.INSTANCE.blocksAttack(hit.getEntity())) {
            cir.setReturnValue(false);
        }
    }

    // A failed use ends the click, so the other hand is not tried either
    @WrapOperation(
        method = "startUseItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;useItemOn(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"
        )
    )
    private InteractionResult extras$guardUseOn(
        MultiPlayerGameMode gameMode,
        LocalPlayer player,
        InteractionHand hand,
        BlockHitResult hit,
        Operation<InteractionResult> original
    ) {
        return ModuleMisplayGuard.INSTANCE.blocksUseOn(hand, hit)
            ? InteractionResult.FAIL
            : original.call(gameMode, player, hand, hit);
    }

}
