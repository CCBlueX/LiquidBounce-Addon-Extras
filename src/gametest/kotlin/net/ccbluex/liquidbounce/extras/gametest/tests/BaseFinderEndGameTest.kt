package net.ccbluex.liquidbounce.extras.gametest.tests

import net.ccbluex.liquidbounce.extras.gametest.harness.PaperGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.awaitAnswer
import net.ccbluex.liquidbounce.extras.gametest.harness.disable
import net.ccbluex.liquidbounce.extras.gametest.harness.enable
import net.ccbluex.liquidbounce.extras.modules.basehunting.ModuleBaseFinder
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level

class BaseFinderEndGameTest : PaperGameTest({
    val pos = BlockPos(1000, 80, 1000)
    run("execute in minecraft:the_end run forceload add 992 992")
    awaitAnswer("execute in minecraft:the_end if loaded 1000 80 1000", "Test passed")
    run("execute in minecraft:the_end run fill 998 80 998 1005 84 1005 minecraft:air")
    run("execute in minecraft:the_end run fill 998 79 998 1005 79 1005 minecraft:stone")
    travel(pos, dimension = Level.END)
    // Server console commands default to the Overworld; execute at the player for this fixture.
    for (x in listOf(1000, 1002)) {
        run("execute at $playerName run setblock $x 80 1003 minecraft:stone")
        run("execute at $playerName run summon minecraft:item_frame $x.5 80 1002.5 " +
            "{Pos:[$x.5d,80d,1002.5d],block_pos:[I;$x,80,1002],Facing:2b,Item:{id:'minecraft:elytra',count:1}}")
    }
    enable(ModuleBaseFinder)
    ticks(60)
    val chunk = ChunkPos.containing(pos)
    check(client { chunk !in ModuleBaseFinder.bases }) { "natural elytra frames counted as placed entities" }
    run("execute at $playerName as @e[type=minecraft:item_frame] run data merge entity @s " +
        "{Item:{id:'minecraft:diamond',count:1}}")
    awaitClient("placed frames in the End") { ModuleBaseFinder.evidence[chunk]?.get("entities") == 2 }
    disable(ModuleBaseFinder)
    check(client { ModuleBaseFinder.bases.isEmpty() })
})
