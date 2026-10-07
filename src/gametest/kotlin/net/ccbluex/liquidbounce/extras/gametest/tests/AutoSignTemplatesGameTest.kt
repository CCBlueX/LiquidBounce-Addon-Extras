package net.ccbluex.liquidbounce.extras.gametest.tests

import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.extras.gametest.harness.PaperGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.awaitAnswer
import net.ccbluex.liquidbounce.extras.gametest.harness.disable
import net.ccbluex.liquidbounce.extras.gametest.harness.enable
import net.ccbluex.liquidbounce.extras.gametest.harness.screenshot
import net.ccbluex.liquidbounce.extras.gametest.harness.setBlock
import net.ccbluex.liquidbounce.extras.gametest.harness.setting
import net.ccbluex.liquidbounce.extras.modules.grinding.ModuleAutoSign
import net.minecraft.world.level.block.entity.SignBlockEntity
import net.minecraft.world.level.block.entity.SignTextSlot

class AutoSignTemplatesGameTest : PaperGameTest({
    val module = ModuleAutoSign.INSTANCE
    val nearby = client { module.containedValues.first { it.name == "Nearby" } as ValueGroup }
    client {
        module.setting<Any>("TextMode").setByString("Template")
        for (i in 1..4) {
            module.setting<String>("Front$i").set("Front $i")
            module.setting<String>("Back$i").set("Back $i")
        }
        nearby.setting<Boolean>("Enabled").set(true)
    }
    val pos = origin.south(2)
    setBlock(pos, "minecraft:oak_sign[rotation=8]")
    awaitClient("a standing sign") { it.level!!.getBlockEntity(pos) is SignBlockEntity }
    enable(module)
    for (i in 0..3) awaitAnswer("data get block 0 -60 2 front_text.messages[$i]", "Front ${i + 1}")
    disable(module)
    travel(origin.south(4), yaw = 180f)
    enable(module)
    for (i in 0..3) awaitAnswer("data get block 0 -60 2 back_text.messages[$i]", "Back ${i + 1}")
    screenshot("AutoSign-templates")
    disable(module)

    travel(origin)
    setBlock(pos, "minecraft:air")
    setBlock(pos, "minecraft:oak_sign[rotation=8]{front_text:{messages:['Keep','','','']}}")
    enable(module)
    ticks(60)
    awaitAnswer("data get block 0 -60 2 front_text.messages[0]", "Keep")
    client { nearby.setting<Boolean>("Overwrite").set(true) }
    awaitAnswer("data get block 0 -60 2 front_text.messages[0]", "Front 1")
    disable(module)

    setBlock(pos, "minecraft:air")
    setBlock(pos, "minecraft:oak_sign[rotation=8]{is_waxed:1b}")
    awaitClient("a waxed sign") { (it.level!!.getBlockEntity(pos) as? SignBlockEntity)?.isWaxed == true }
    enable(module)
    ticks(60)
    check(client {
        (it.level!!.getBlockEntity(pos) as SignBlockEntity).getText(SignTextSlot.FRONT)
            .getMessages(false).all { line -> line.string.isEmpty() }
    })
    disable(module)

    setBlock(pos, "minecraft:air")
    setBlock(pos, "minecraft:oak_sign[rotation=8]")
    run("gamemode adventure $playerName")
    awaitClient("adventure mode") { it.gameMode!!.playerMode.name == "ADVENTURE" }
    enable(module)
    ticks(60)
    awaitAnswer("data get block 0 -60 2 front_text.messages[0]", "\"\"")
    disable(module)
    run("gamemode survival $playerName")
    awaitClient("survival mode") { it.gameMode!!.playerMode.name == "SURVIVAL" }

    for (block in listOf("oak_wall_sign[facing=north]", "oak_hanging_sign[rotation=8]")) {
        setBlock(pos, "minecraft:air")
        setBlock(pos.south(), "minecraft:stone")
        setBlock(pos.above(), "minecraft:stone")
        setBlock(pos, "minecraft:$block")
        awaitClient("a $block") { it.level!!.getBlockEntity(pos) is SignBlockEntity }
        enable(module)
        awaitAnswer("data get block 0 -60 2 front_text.messages[0]", "Front 1")
        disable(module)
    }

    // A delayed write must die with its module, even though the server already opened the editor.
    client { module.setting<Int>("Delay").set(60) }
    setBlock(pos, "minecraft:air")
    setBlock(pos, "minecraft:oak_sign[rotation=8]")
    awaitClient("a blank sign") {
        (it.level!!.getBlockEntity(pos) as? SignBlockEntity)?.getText(SignTextSlot.FRONT)
            ?.getMessages(false)?.all { line -> line.string.isEmpty() } == true
    }
    enable(module)
    awaitClient("the delayed write") { module.isWriting }
    disable(module)
    ticks(80)
    awaitAnswer("data get block 0 -60 2 front_text.messages[0]", "\"\"")
})
