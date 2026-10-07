package net.ccbluex.liquidbounce.extras.gametest.tests

import com.mojang.blaze3d.platform.InputConstants
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.extras.gametest.harness.GameTestScope
import net.ccbluex.liquidbounce.extras.gametest.harness.PaperGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.aim
import net.ccbluex.liquidbounce.extras.gametest.harness.awaitServer
import net.ccbluex.liquidbounce.extras.gametest.harness.disable
import net.ccbluex.liquidbounce.extras.gametest.harness.enable
import net.ccbluex.liquidbounce.extras.gametest.harness.give
import net.ccbluex.liquidbounce.extras.gametest.harness.setBlock
import net.ccbluex.liquidbounce.extras.gametest.harness.setting
import net.ccbluex.liquidbounce.extras.modules.grinding.ModuleAutoSmelter
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.AbstractFurnaceMenu
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items

class AutoSmelterSafetyGameTest : PaperGameTest({
    val module = ModuleAutoSmelter
    val pos = origin.south(2)
    setBlock(pos, "minecraft:smoker")
    give("minecraft:raw_iron", 8)
    give("minecraft:coal", 8)
    openStation()
    enable(module)
    ticks(80)
    awaitServer("items entity $playerName container.* minecraft:raw_iron[count=8]")
    awaitServer("items entity $playerName container.* minecraft:coal[count=8]")
    disable(module)
    input.pressKey(InputConstants.KEY_ESCAPE)

    setBlock(pos, "minecraft:air")
    setBlock(pos, "minecraft:furnace")
    client { module.setting<Int>("Refill").set(3) }
    openStation()
    enable(module)
    awaitServer("items block 0 -60 2 container.1 minecraft:coal[count=2]")
    awaitServer("items entity $playerName container.* minecraft:coal[count=5]")
    check(client { it.player!!.containerMenu.carried.isEmpty })
    disable(module)
    input.pressKey(InputConstants.KEY_ESCAPE)

    setBlock(pos, "minecraft:air")
    setBlock(pos, "minecraft:furnace")
    run("clear $playerName")
    give("minecraft:raw_iron", 8)
    give("minecraft:coal", 8)
    client {
        val constraints = module.containedValues.first { it.name == "Constraints" } as ValueGroup
        constraints.setting<IntRange>("ClickDelay").set(10..10)
    }
    openStation()
    enable(module)
    awaitClient("a transfer on the cursor") { !it.player!!.containerMenu.carried.isEmpty }
    input.pressKey(InputConstants.KEY_ESCAPE)
    ticks(30)
    check(client { it.player!!.inventoryMenu.carried.isEmpty })
    awaitServer("items entity $playerName container.* minecraft:raw_iron[count=8]")
    disable(module)

    // A lava bucket must leave its empty bucket in the player's inventory.
    run("clear $playerName")
    give("minecraft:raw_iron", 1)
    give("minecraft:lava_bucket")
    client { module.setting<Set<Item>>("Fuels").set(linkedSetOf(Items.LAVA_BUCKET)) }
    openStation()
    enable(module)
    awaitServer("items entity $playerName container.* minecraft:bucket")
    awaitServer("items entity $playerName container.* minecraft:iron_ingot", timeout = 300)
    disable(module)
    input.pressKey(InputConstants.KEY_ESCAPE)
})

private fun GameTestScope.openStation() {
    val pos = origin.south(2)
    awaitClient("the station") { it.level!!.getBlockState(pos).block.defaultBlockState().hasBlockEntity() }
    aim(pos)
    input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT)
    awaitClient("the station menu") { (it.gui.screen() as? AbstractContainerScreen<*>)?.menu is AbstractFurnaceMenu }
}
