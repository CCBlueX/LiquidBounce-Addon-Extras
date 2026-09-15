package net.ccbluex.liquidbounce.extras

import net.ccbluex.liquidbounce.extras.modules.ModuleAutoJump
import net.ccbluex.liquidbounce.extras.modules.ModuleAutoShearer
import net.ccbluex.liquidbounce.extras.modules.ModuleBlockFinder
import net.ccbluex.liquidbounce.extras.modules.ModuleLightOverlay
import net.ccbluex.liquidbounce.extras.modules.ModulePacketCanceller
import net.ccbluex.liquidbounce.extras.modules.ModuleQuickRespawn
import net.ccbluex.liquidbounce.extras.modules.ModuleSpeedometer
import net.ccbluex.liquidbounce.extras.modules.ModuleStashFinder
import net.ccbluex.liquidbounce.features.addon.LiquidBounceAddon

/**
 * Entry point, named under the `liquidbounce` entrypoint in `fabric.mod.json`. Half of the features
 * are Kotlin, half Java; the API makes no difference between the two.
 */
class ExtrasAddon : LiquidBounceAddon() {

    override val categories = listOf(ExtrasCategories.EXTRAS)

    override fun onInitialize() {
        registerModules(ModuleSpeedometer(), ModuleQuickRespawn(), ModuleBlockFinder, ModuleLightOverlay, ModulePacketCanceller, ModuleStashFinder, ModuleAutoShearer, ModuleAutoJump())
    }

}
