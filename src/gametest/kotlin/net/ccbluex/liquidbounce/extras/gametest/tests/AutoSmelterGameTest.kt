package net.ccbluex.liquidbounce.extras.gametest.tests

import com.mojang.blaze3d.platform.InputConstants
import net.ccbluex.liquidbounce.extras.gametest.harness.PaperGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.aim
import net.ccbluex.liquidbounce.extras.gametest.harness.awaitServer
import net.ccbluex.liquidbounce.extras.gametest.harness.disable
import net.ccbluex.liquidbounce.extras.gametest.harness.enable
import net.ccbluex.liquidbounce.extras.gametest.harness.give
import net.ccbluex.liquidbounce.extras.gametest.harness.screenshot
import net.ccbluex.liquidbounce.extras.gametest.harness.setBlock
import net.ccbluex.liquidbounce.extras.gametest.harness.setting
import net.ccbluex.liquidbounce.extras.modules.grinding.ModuleAutoSmelter
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.AbstractFurnaceMenu
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items

class AutoSmelterGameTest : PaperGameTest({
    val pos = origin.south(2)
    for ((block, ingredient, output) in listOf(
        Triple("furnace", "raw_iron", "iron_ingot"),
        Triple("blast_furnace", "raw_gold", "gold_ingot"),
        Triple("smoker", "beef", "cooked_beef"),
    )) {
        disable(ModuleAutoSmelter)
        run("clear $playerName")
        setBlock(pos, "minecraft:air")
        setBlock(pos, "minecraft:$block")
        give("minecraft:$ingredient", 2)
        give("minecraft:coal", 8)
        client {
            ModuleAutoSmelter.setting<Set<Item>>("Inputs").set(linkedSetOf(Items.RAW_IRON, Items.RAW_GOLD, Items.BEEF))
        }
        awaitClient("the station") { !it.level!!.getBlockState(pos).isAir }
        aim(pos)
        input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT)
        awaitClient("the furnace menu") {
            (it.gui.screen() as? AbstractContainerScreen<*>)?.menu is AbstractFurnaceMenu
        }
        enable(ModuleAutoSmelter)
        awaitServer("items entity $playerName container.* minecraft:$output[count=2]", timeout = 600)
        awaitServer("items entity $playerName container.* minecraft:coal[count=7]")
        check(client { it.player!!.containerMenu.carried.isEmpty }) { "fuel transfer left the cursor occupied" }
        screenshot("AutoSmelter-$block")
        input.pressKey(InputConstants.KEY_ESCAPE)
    }
    disable(ModuleAutoSmelter)
    run("clear $playerName")
    setBlock(pos, "minecraft:air")
    setBlock(pos, "minecraft:furnace{Items:[{Slot:2b,id:'minecraft:iron_ingot',count:64}]}")
    repeat(36) { run("item replace entity $playerName container.$it with minecraft:cobblestone 64") }
    awaitClient("a full inventory") { it.player!!.inventory.nonEquipmentItems.all { stack -> stack.count == 64 } }
    aim(pos)
    input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT)
    awaitClient("the full furnace") { (it.gui.screen() as? AbstractContainerScreen<*>)?.menu is AbstractFurnaceMenu }
    enable(ModuleAutoSmelter)
    ticks(60)
    awaitServer("items block ${pos.x} ${pos.y} ${pos.z} container.2 minecraft:iron_ingot[count=64]")
    check(client { it.player!!.containerMenu.carried.isEmpty })
    disable(ModuleAutoSmelter)
    input.pressKey(InputConstants.KEY_ESCAPE)
})
