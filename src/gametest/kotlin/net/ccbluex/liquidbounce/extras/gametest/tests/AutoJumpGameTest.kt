package net.ccbluex.liquidbounce.extras.gametest.tests

import net.ccbluex.liquidbounce.extras.gametest.harness.PaperGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.enable
import net.ccbluex.liquidbounce.extras.gametest.harness.screenshot
import net.ccbluex.liquidbounce.extras.modules.grinding.ModuleAutoJump

class AutoJumpGameTest : PaperGameTest({
    enable(ModuleAutoJump.INSTANCE)
    input.holdKey { it.keyUp }
    awaitClient("a jump", timeout = 40) { !it.player!!.onGround() }
    ticks(10)
    screenshot("AutoJump")
    ticks(60)
    input.releaseKey { it.keyUp }
})
