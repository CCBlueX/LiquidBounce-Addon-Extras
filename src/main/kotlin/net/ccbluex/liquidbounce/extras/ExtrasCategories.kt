package net.ccbluex.liquidbounce.extras

import net.ccbluex.liquidbounce.features.module.ModuleCategory
import net.minecraft.resources.Identifier

/**
 * Registered through [ExtrasAddon.categories]. The icon lives in `resources/liquidbounce-extras/`, the
 * add-on's own folder, never in `assets/`.
 */
object ExtrasCategories {

    @JvmField
    val EXTRAS = ModuleCategory("Extras", Identifier.fromNamespaceAndPath("liquidbounce-extras", "clickgui/extras.svg"))

}
