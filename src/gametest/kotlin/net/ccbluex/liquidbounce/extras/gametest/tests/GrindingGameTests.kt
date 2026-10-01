package net.ccbluex.liquidbounce.extras.gametest.tests

import net.ccbluex.liquidbounce.extras.gametest.harness.GameTestScope
import net.ccbluex.liquidbounce.extras.gametest.harness.PaperGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.aim
import net.ccbluex.liquidbounce.extras.gametest.harness.awaitAnswer
import net.ccbluex.liquidbounce.extras.gametest.harness.awaitServer
import net.ccbluex.liquidbounce.extras.gametest.harness.enable
import net.ccbluex.liquidbounce.extras.gametest.harness.screenshot
import net.ccbluex.liquidbounce.extras.gametest.harness.summon
import net.ccbluex.liquidbounce.extras.modules.grinding.ModuleAutoJump
import net.ccbluex.liquidbounce.extras.modules.grinding.ModuleAutoShearer
import net.ccbluex.liquidbounce.extras.modules.grinding.ModuleAutoSign
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.animal.sheep.Sheep
import net.minecraft.world.item.Items
import com.mojang.blaze3d.platform.InputConstants

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

class AutoSignGameTest : PaperGameTest({
    run("item replace entity $playerName hotbar.0 with minecraft:oak_sign 2")
    awaitClient("the signs") { it.player!!.mainHandItem.`is`(Items.OAK_SIGN) }
    enable(ModuleAutoSign.INSTANCE)

    // Nothing written yet, so the first sign is written by hand
    val first = origin.south(2)
    aim(first.below())
    input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT)
    context.waitForScreen(AbstractSignEditScreen::class.java)
    input.typeChars("Extras")
    context.clickScreenButton("gui.done")
    awaitSignText(first, "Extras")

    val second = origin.south(2).east()
    aim(second.below())
    input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT)
    repeat(20) {
        check(client { it.gui.screen() !is AbstractSignEditScreen }) { "the sign editor opened" }
        ticks(1)
    }
    awaitSignText(second, "Extras")
    screenshot("AutoSign")
})

private fun GameTestScope.awaitSignText(pos: BlockPos, text: String) =
    awaitAnswer("data get block ${pos.x} ${pos.y} ${pos.z} front_text.messages[0]", text)
