package net.ccbluex.liquidbounce.extras

import net.ccbluex.liquidbounce.extras.commands.CommandWhere
import net.ccbluex.liquidbounce.extras.hud.ClickCounter
import net.ccbluex.liquidbounce.extras.hud.extrasHudComponents
import net.ccbluex.liquidbounce.extras.modules.basehunting.ModuleBaseFinder
import net.ccbluex.liquidbounce.extras.modules.basehunting.ModuleStashFinder
import net.ccbluex.liquidbounce.features.addon.LiquidBounceAddon
import net.ccbluex.liquidbounce.integration.theme.component.HudComponentManager

/** Entry point, named under the `liquidbounce` entrypoint in `fabric.mod.json`. */
class ExtrasAddon : LiquidBounceAddon() {

    override val categories = ExtrasCategories.all

    override fun onInitialize() {
        registerModules(
            ModuleBaseFinder,
            ModuleStashFinder,
        )
        registerCommand(CommandWhere.INSTANCE)
        registerListeners(ClickCounter)
        // The client does not persist native HUD components, so their settings live in the add-on's config
        config("extras-hud", extrasHudComponents.toMutableList())
    }

    override fun onStarted() = extrasHudComponents.forEach(HudComponentManager::register)

    override fun onStopping() = extrasHudComponents.forEach(HudComponentManager::unregister)

}
