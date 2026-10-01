package net.ccbluex.liquidbounce.extras.gametest.tests

import net.ccbluex.liquidbounce.extras.gametest.harness.PaperGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.awaitServer
import net.ccbluex.liquidbounce.extras.gametest.harness.enable
import net.ccbluex.liquidbounce.extras.gametest.harness.screenshot
import net.ccbluex.liquidbounce.extras.gametest.harness.summon
import net.ccbluex.liquidbounce.extras.modules.grinding.ModuleAutoJump
import net.ccbluex.liquidbounce.extras.modules.grinding.ModuleAutoShearer
import net.minecraft.world.entity.animal.sheep.Sheep
import net.minecraft.world.item.Items

class AutoJumpGameTest : PaperGameTest({
    enable(ModuleAutoJump.INSTANCE)
    input.holdKey { it.keyUp }
    awaitClient("a jump", timeout = 40) { !it.player!!.onGround() }
    ticks(10)
    screenshot("AutoJump")
    ticks(60)
    input.releaseKey { it.keyUp }
})

class AutoShearerGameTest : PaperGameTest({
    // Off the selected slot, so the module has to switch to them
    run("item replace entity $playerName hotbar.3 with minecraft:shears")
    summon("minecraft:sheep", origin.south(2), "{NoAI:1b}")
    awaitClient("the sheep and the shears") { minecraft ->
        val player = minecraft.player!!
        player.inventory.getItem(3).`is`(Items.SHEARS) &&
            minecraft.level!!.getEntitiesOfClass(Sheep::class.java, player.boundingBox.inflate(4.0)).isNotEmpty()
    }

    enable(ModuleAutoShearer)
    awaitServer("entity @e[type=minecraft:sheep,nbt={Sheared:1b}]")
    ticks(10)
    screenshot("AutoShearer")
})
