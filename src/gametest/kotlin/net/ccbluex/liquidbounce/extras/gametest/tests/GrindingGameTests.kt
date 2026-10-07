package net.ccbluex.liquidbounce.extras.gametest.tests

import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.extras.gametest.harness.GameTestScope
import net.ccbluex.liquidbounce.extras.gametest.harness.PaperGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.aim
import net.ccbluex.liquidbounce.extras.gametest.harness.awaitAnswer
import net.ccbluex.liquidbounce.extras.gametest.harness.awaitServer
import net.ccbluex.liquidbounce.extras.gametest.harness.disable
import net.ccbluex.liquidbounce.extras.gametest.harness.enable
import net.ccbluex.liquidbounce.extras.gametest.harness.give
import net.ccbluex.liquidbounce.extras.gametest.harness.query
import net.ccbluex.liquidbounce.extras.gametest.harness.screenshot
import net.ccbluex.liquidbounce.extras.gametest.harness.setBlock
import net.ccbluex.liquidbounce.extras.gametest.harness.setting
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
import net.minecraft.world.level.block.entity.SignBlockEntity
import net.minecraft.world.level.block.entity.SignTextSlot
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

class AutoSignTemplatesGameTest : PaperGameTest({
    val module = ModuleAutoSign.INSTANCE
    val nearby = client { module.containedValues.first { it.name == "Nearby" } as ValueGroup }
    client {
        module.setting<Any>("TextMode").setByString("Template")
        for (line in 1..4) {
            module.setting<String>("Front$line").set("Front $line")
            module.setting<String>("Back$line").set("Back $line")
        }
        nearby.setting<Boolean>("Enabled").set(true)
    }
    val sign = origin.south(2)

    placeSign(sign, "minecraft:oak_sign[rotation=8]")
    enable(module)
    for (line in 0..3) {
        awaitSignText(sign, "Front ${line + 1}", line = line)
    }
    disable(module)
    travel(origin.south(4), yaw = 180f)
    enable(module)
    for (line in 0..3) {
        awaitSignText(sign, "Back ${line + 1}", side = "back", line = line)
    }
    screenshot("AutoSign-templates")
    disable(module)

    travel(origin)
    placeSign(sign, "minecraft:oak_sign[rotation=8]{front_text:{messages:['Keep','','','']}}") {
        it.getText(SignTextSlot.FRONT).hasMessage(false)
    }
    enable(module)
    ticks(60)
    awaitSignText(sign, "Keep")
    client { nearby.setting<Boolean>("Overwrite").set(true) }
    awaitSignText(sign, "Front 1")
    disable(module)

    placeSign(sign, "minecraft:oak_sign[rotation=8]{is_waxed:1b}") { it.isWaxed }
    enable(module)
    ticks(60)
    awaitSignText(sign, "\"\"")
    disable(module)

    placeSign(sign, "minecraft:oak_sign[rotation=8]")
    run("gamemode adventure $playerName")
    awaitClient("adventure mode") { !it.player!!.mayBuild() }
    enable(module)
    ticks(60)
    awaitSignText(sign, "\"\"")
    disable(module)
    run("gamemode survival $playerName")
    awaitClient("survival mode") { it.player!!.mayBuild() }

    setBlock(sign.south(), "minecraft:stone")
    setBlock(sign.above(), "minecraft:stone")
    for (block in listOf("minecraft:oak_wall_sign[facing=north]", "minecraft:oak_hanging_sign[rotation=8]")) {
        placeSign(sign, block)
        enable(module)
        awaitSignText(sign, "Front 1")
        disable(module)
    }
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

private fun GameTestScope.awaitSignText(pos: BlockPos, text: String, side: String = "front", line: Int = 0) =
    awaitAnswer("data get block ${pos.x} ${pos.y} ${pos.z} ${side}_text.messages[$line]", text)

private fun GameTestScope.placeSign(pos: BlockPos, block: String, arrived: (SignBlockEntity) -> Boolean = { true }) {
    setBlock(pos, "minecraft:air")
    setBlock(pos, block)
    awaitClient("the sign") { (it.level!!.getBlockEntity(pos) as? SignBlockEntity)?.let(arrived) == true }
}

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
