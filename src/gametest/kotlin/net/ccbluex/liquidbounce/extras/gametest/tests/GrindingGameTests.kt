package net.ccbluex.liquidbounce.extras.gametest.tests

import net.ccbluex.liquidbounce.extras.gametest.harness.GameTestScope
import net.ccbluex.liquidbounce.extras.gametest.harness.PaperGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.aim
import net.ccbluex.liquidbounce.extras.gametest.harness.awaitAnswer
import net.ccbluex.liquidbounce.extras.gametest.harness.awaitServer
import net.ccbluex.liquidbounce.extras.gametest.harness.enable
import net.ccbluex.liquidbounce.extras.gametest.harness.give
import net.ccbluex.liquidbounce.extras.gametest.harness.query
import net.ccbluex.liquidbounce.extras.gametest.harness.screenshot
import net.ccbluex.liquidbounce.extras.gametest.harness.setBlock
import net.ccbluex.liquidbounce.extras.gametest.harness.summon
import net.ccbluex.liquidbounce.extras.modules.grinding.ModuleAutoAnvilRepair
import net.ccbluex.liquidbounce.extras.modules.grinding.ModuleAutoJump
import net.ccbluex.liquidbounce.extras.modules.grinding.ModuleAutoShearer
import net.ccbluex.liquidbounce.extras.modules.grinding.ModuleAutoSign
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
import net.minecraft.client.gui.screens.inventory.AnvilScreen
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.animal.sheep.Sheep
import net.minecraft.world.inventory.AnvilMenu
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.AnvilBlock
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

class AutoAnvilRepairGameTest : PaperGameTest({
    val anvil = origin.south(2)
    setBlock(anvil, "minecraft:anvil")
    give("minecraft:diamond_pickaxe[damage=1000]", 2)
    run("experience set $playerName 30 levels")

    enable(ModuleAutoAnvilRepair)
    openAnvil(anvil)
    awaitServer("entity @a[name=$playerName,level=28]")
    check(pickaxes() == 1) { "the pair was not combined into one pickaxe" }
    screenshot("AutoAnvilRepair")
    client { it.player!!.closeContainer() }

    // Priced above MaxLevelCost: goes back untouched
    run("clear $playerName")
    give("minecraft:diamond_pickaxe[damage=1000,repair_cost=10]", 2)
    ticks(20)
    openAnvil(anvil)
    ticks(60)
    val inputs = client { minecraft ->
        val menu = (minecraft.gui.screen() as AnvilScreen).menu
        listOf(AnvilMenu.INPUT_SLOT, AnvilMenu.ADDITIONAL_SLOT).count { menu.getSlot(it).hasItem() }
    }
    check(inputs == 0) { "the expensive pair stayed in the anvil" }
    check(pickaxes() == 2) { "the expensive pair was combined" }
    awaitServer("entity @a[name=$playerName,level=28]")
    client { it.player!!.closeContainer() }
})

private fun GameTestScope.awaitSignText(pos: BlockPos, text: String) =
    awaitAnswer("data get block ${pos.x} ${pos.y} ${pos.z} front_text.messages[0]", text)

private fun GameTestScope.openAnvil(pos: BlockPos) {
    awaitClient("the anvil to arrive") { it.level!!.getBlockState(pos).block is AnvilBlock }
    aim(pos)
    input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT)
    awaitClient("the anvil to open") { it.gui.screen() is AnvilScreen }
}

private fun GameTestScope.pickaxes(): Int {
    val answer = query(
        "execute if items entity $playerName container.* minecraft:diamond_pickaxe",
        Regex("""Test (?:passed\. Count: (\d+)|failed)"""),
    )
    return answer.groupValues[1].toIntOrNull() ?: 0
}
