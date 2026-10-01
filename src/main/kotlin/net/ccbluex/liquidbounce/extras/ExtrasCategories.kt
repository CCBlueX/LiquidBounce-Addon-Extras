package net.ccbluex.liquidbounce.extras

import net.ccbluex.liquidbounce.features.module.ModuleCategory
import net.minecraft.resources.Identifier

/**
 * Categories group modules by what they are for. A module only leaves [EXTRAS] once enough modules
 * share its purpose to fill a tab of their own.
 */
object ExtrasCategories {

    @JvmField
    val BASE_HUNTING = category("Base Hunting", "base-hunting")

    @JvmField
    val GRINDING = category("Grinding", "grinding")

    @JvmField
    val QOL = category("QoL", "qol")

    @JvmField
    val EXTRAS = category("Extras", "extras")

    val all = listOf(BASE_HUNTING, GRINDING, QOL, EXTRAS)

    private fun category(name: String, icon: String) =
        ModuleCategory(name, Identifier.fromNamespaceAndPath("liquidbounce-extras", "clickgui/$icon.svg"))

}
