package net.ccbluex.liquidbounce.extras.gametest.tests

import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.extras.gametest.harness.PaperGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.screenshot
import net.ccbluex.liquidbounce.extras.gametest.harness.setting
import net.ccbluex.liquidbounce.extras.hud.ClickCounter
import net.ccbluex.liquidbounce.extras.hud.SpeedometerHud
import net.ccbluex.liquidbounce.extras.hud.extrasHudComponents
import net.ccbluex.liquidbounce.integration.theme.ThemeManager
import net.ccbluex.liquidbounce.integration.theme.component.HudComponentManager
import net.ccbluex.liquidbounce.utils.render.Alignment
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import com.mojang.blaze3d.platform.InputConstants

/**
 * Plain vanilla movement has to pass, or no other verdict means anything. Then the negative control: a
 * client claiming to be five blocks up has to be caught.
 */
class HarnessGameTest : PaperGameTest({
    input.holdKey { it.keyUp }
    ticks(40)
    input.holdKey { it.keySprint }
    input.holdKey { it.keyJump }
    ticks(60)
    input.releaseKey { it.keyJump }
    input.releaseKey { it.keySprint }
    input.releaseKey { it.keyUp }
    ticks(20)

    expectViolation("Grim to catch a move five blocks up") {
        client { minecraft ->
            val player = minecraft.player!!
            minecraft.connection!!.send(ServerboundMovePlayerPacket.Pos(player.x, player.y + 5, player.z, true, false))
        }
    }
})

class HudGameTest : PaperGameTest({
    client {
        val catalog = HudComponentManager.getComponentCatalog(ThemeManager.theme!!.metadata.id)
        for (component in extrasHudComponents) {
            check(catalog.any { it.name == component.name && it.canAdd && it.singleton }) {
                "${component.name} is not in the HUD editor's catalog"
            }
            check(HudComponentManager.addComponent(component.id.toString()) === component)
            check(HudComponentManager.addComponent(component.id.toString()) == null) {
                "${component.name} could be added twice"
            }
            check(component.text.string.isNotBlank()) { "${component.name} shows nothing" }
        }
    }

    // Settings and placement survive a save and a load of the add-on's config
    client {
        val config = ConfigSystem.configs.first { it.name == "extras-hud" }
        val defaults = ConfigSystem.serializeValueGroup(config)
        SpeedometerHud.setting<String>("Prefix").set("Moving: ")
        SpeedometerHud.setting<Boolean>("ShowUnit").set(false)
        SpeedometerHud.alignment.setFrom(Alignment(Alignment.ScreenAxisX.RIGHT, 32, Alignment.ScreenAxisY.BOTTOM, 48))
        config.saveToDisk()
        ConfigSystem.deserializeValueGroup(config, defaults)
        config.loadFromDisk()
        check(SpeedometerHud.enabled && SpeedometerHud.text.string.startsWith("Moving: "))
        check(!SpeedometerHud.text.string.endsWith("b/s"))
        check(SpeedometerHud.alignment.horizontalAlignment == Alignment.ScreenAxisX.RIGHT)
        ConfigSystem.deserializeValueGroup(config, defaults)
        config.saveToDisk()
    }

    repeat(3) { input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT) }
    repeat(2) { input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT) }
    val clicks = client { ClickCounter.left to ClickCounter.right }
    check(clicks == 3 to 2) { "counted $clicks clicks instead of (3, 2)" }
    awaitClient("the clicks to leave the one second window", timeout = 40) {
        ClickCounter.left == 0 && ClickCounter.right == 0
    }

    input.holdKey { it.keyUp }
    ticks(20)
    screenshot("Hud")
    input.releaseKey { it.keyUp }
    client { extrasHudComponents.forEach { it.enabled = false } }
})
