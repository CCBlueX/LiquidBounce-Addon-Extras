package net.ccbluex.liquidbounce.extras

import net.ccbluex.liquidbounce.features.addon.LiquidBounceAddon

/**
 * Entry point, named under the `liquidbounce` entrypoint in `fabric.mod.json`. Half of the features
 * are Kotlin, half Java; the API makes no difference between the two.
 */
class ExtrasAddon : LiquidBounceAddon() {

    override val categories = listOf(ExtrasCategories.EXTRAS)

    override fun onInitialize() {
    }

}
