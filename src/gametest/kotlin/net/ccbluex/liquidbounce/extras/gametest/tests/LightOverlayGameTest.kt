package net.ccbluex.liquidbounce.extras.gametest.tests

import net.ccbluex.liquidbounce.extras.gametest.harness.PaperGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.enable
import net.ccbluex.liquidbounce.extras.gametest.harness.fill
import net.ccbluex.liquidbounce.extras.gametest.harness.screenshot
import net.ccbluex.liquidbounce.extras.modules.qol.ModuleLightOverlay
import net.ccbluex.liquidbounce.extras.modules.qol.ModuleLightOverlay.Spawn

class LightOverlayGameTest : PaperGameTest({
    enable(ModuleLightOverlay)
    awaitClient("open ground to count as dark at night") {
        ModuleLightOverlay.spots[origin.south(3)] == Spawn.POTENTIAL
    }

    fill(origin.offset(-6, 0, -6), origin.offset(-2, 4, -2), "minecraft:stone hollow")
    awaitClient("the roofed box to count as dark now") {
        ModuleLightOverlay.spots[origin.offset(-4, 1, -4)] == Spawn.ALWAYS
    }
    screenshot("LightOverlay")
})
