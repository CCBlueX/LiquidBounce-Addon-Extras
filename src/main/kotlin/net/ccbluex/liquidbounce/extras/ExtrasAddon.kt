package net.ccbluex.liquidbounce.extras

import net.ccbluex.liquidbounce.features.addon.LiquidBounceAddon

/** Entry point, named under the `liquidbounce` entrypoint in `fabric.mod.json`. */
class ExtrasAddon : LiquidBounceAddon() {

    override val categories = ExtrasCategories.all

    override fun onInitialize() = Unit

}
