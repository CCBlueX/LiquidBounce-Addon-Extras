package net.ccbluex.liquidbounce.extras

import net.ccbluex.liquidbounce.extras.commands.CommandFindings
import net.ccbluex.liquidbounce.extras.commands.CommandWaypoint
import net.ccbluex.liquidbounce.extras.commands.CommandWhere
import net.ccbluex.liquidbounce.extras.hud.ClickCounter
import net.ccbluex.liquidbounce.extras.hud.extrasHudComponents
import net.ccbluex.liquidbounce.extras.modules.basehunting.ModuleBaseFinder
import net.ccbluex.liquidbounce.extras.modules.basehunting.ModuleCaveDisturbanceDetector
import net.ccbluex.liquidbounce.extras.modules.basehunting.ModuleCollectibleESP
import net.ccbluex.liquidbounce.extras.modules.basehunting.ModulePortalFinder
import net.ccbluex.liquidbounce.extras.modules.basehunting.ModuleSoundLocator
import net.ccbluex.liquidbounce.extras.modules.basehunting.ModuleStashFinder
import net.ccbluex.liquidbounce.extras.modules.basehunting.ModuleSuspiciousBlockDetector
import net.ccbluex.liquidbounce.extras.modules.basehunting.ModuleTunnelTrailESP
import net.ccbluex.liquidbounce.extras.modules.extras.ModuleAntiTrap
import net.ccbluex.liquidbounce.extras.modules.extras.ModuleDisguise
import net.ccbluex.liquidbounce.extras.modules.extras.ModuleIntruderAlert
import net.ccbluex.liquidbounce.extras.modules.extras.ModuleMessageAura
import net.ccbluex.liquidbounce.extras.modules.extras.ModulePacketCanceller
import net.ccbluex.liquidbounce.extras.modules.grinding.ModuleAutoAnvilRepair
import net.ccbluex.liquidbounce.extras.modules.grinding.ModuleAutoBreed
import net.ccbluex.liquidbounce.extras.modules.grinding.ModuleAutoJump
import net.ccbluex.liquidbounce.extras.modules.grinding.ModuleAutoShearer
import net.ccbluex.liquidbounce.extras.modules.grinding.ModuleAutoSign
import net.ccbluex.liquidbounce.extras.modules.grinding.ModuleAutoSmelter
import net.ccbluex.liquidbounce.extras.modules.grinding.ModuleOreSimulation
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleAnchorAura
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleAnchorMacro
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleBlastCover
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleBucketSteal
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleCartBomb
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleCrossbowLoader
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleCrystalMacro
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleHitFlick
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleHitHold
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleMisplayGuard
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleShieldGuard
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleShieldSwap
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleSwingRedirect
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleTotemSwitch
import net.ccbluex.liquidbounce.extras.modules.qol.ModuleLightOverlay
import net.ccbluex.liquidbounce.extras.modules.qol.ModuleNoItemBounce
import net.ccbluex.liquidbounce.extras.modules.qol.ModuleWaypoints
import net.ccbluex.liquidbounce.extras.util.WorldJournal
import net.ccbluex.liquidbounce.features.addon.LiquidBounceAddon
import net.ccbluex.liquidbounce.integration.theme.component.HudComponentManager

/** Entry point, named under the `liquidbounce` entrypoint in `fabric.mod.json`. */
class ExtrasAddon : LiquidBounceAddon() {

    override val categories = ExtrasCategories.all

    override fun onInitialize() {
        registerModules(
            ModuleBaseFinder,
            ModuleStashFinder,
            ModuleSuspiciousBlockDetector,
            ModuleCollectibleESP,
            ModulePortalFinder,
            ModuleCaveDisturbanceDetector,
            ModuleTunnelTrailESP,
            ModuleSoundLocator,
            ModuleAutoJump.INSTANCE,
            ModuleAutoShearer,
            ModuleAutoSign.INSTANCE,
            ModuleAutoAnvilRepair,
            ModuleAutoSmelter,
            ModuleAutoBreed,
            ModuleLightOverlay,
            ModuleWaypoints,
            ModuleCrystalMacro,
            ModuleBucketSteal,
            ModuleMisplayGuard,
            ModuleTotemSwitch,
            ModuleMessageAura.INSTANCE,
            ModulePacketCanceller,
            ModuleIntruderAlert,
            ModuleBlastCover,
            ModuleAnchorAura,
            ModuleAnchorMacro,
            ModuleSwingRedirect,
            ModuleHitHold,
            ModuleHitFlick,
            ModuleCrossbowLoader,
            ModuleShieldSwap,
            ModuleShieldGuard,
            ModuleAntiTrap,
            ModuleDisguise,
            ModuleNoItemBounce,
            ModuleOreSimulation,
            ModuleCartBomb,
        )
        registerCommand(CommandWhere.INSTANCE)
        registerCommand(CommandWaypoint)
        registerCommand(CommandFindings)
        registerListeners(ClickCounter, WorldJournal)
        // The client does not persist native HUD components, so their settings live in the add-on's config
        config("extras-hud", extrasHudComponents.toMutableList())
    }

    override fun onStarted() = extrasHudComponents.forEach(HudComponentManager::register)

    override fun onStopping() = extrasHudComponents.forEach(HudComponentManager::unregister)

}
