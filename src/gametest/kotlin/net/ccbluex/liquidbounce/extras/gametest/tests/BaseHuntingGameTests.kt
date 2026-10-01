package net.ccbluex.liquidbounce.extras.gametest.tests

import net.ccbluex.liquidbounce.extras.gametest.harness.PaperGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.chatContains
import net.ccbluex.liquidbounce.extras.gametest.harness.disable
import net.ccbluex.liquidbounce.extras.gametest.harness.enable
import net.ccbluex.liquidbounce.extras.gametest.harness.forceLoad
import net.ccbluex.liquidbounce.extras.gametest.harness.hangItemFrame
import net.ccbluex.liquidbounce.extras.gametest.harness.screenshot
import net.ccbluex.liquidbounce.extras.gametest.harness.setBlock
import net.ccbluex.liquidbounce.extras.modules.basehunting.ModuleBaseFinder
import net.ccbluex.liquidbounce.extras.modules.basehunting.ModuleStashFinder
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.ChunkPos

class StashFinderGameTest : PaperGameTest({
    // Filled while the client cannot see it, so the chests arrive with the chunk
    val stash = ChunkPos(12, 0)
    forceLoad(stash)
    for (x in 200..204) {
        setBlock(BlockPos(x, -60, 3), "minecraft:chest")
    }
    forceLoad(stash, load = false)

    enable(ModuleStashFinder)
    travel(BlockPos(196, -60, 0), yaw = -60f, pitch = 20f)
    awaitClient("the stash report") { it.chatContains("5 containers in the chunk at 192 0") }
    check(client { ModuleStashFinder.stashes[stash] } == 5)
    screenshot("StashFinder")
})

class BaseFinderGameTest : PaperGameTest({
    enable(ModuleBaseFinder)
    ticks(40)
    check(client { ModuleBaseFinder.bases.isEmpty() }) { "reported a base in an empty world" }

    setBlock(BlockPos(40, -60, 40), "minecraft:beacon")
    for (x in -40..-37) {
        setBlock(BlockPos(x, -60, 40), "minecraft:crafting_table")
    }
    for (x in listOf(38, 40)) {
        hangItemFrame(BlockPos(x, -60, -40), Direction.SOUTH, "minecraft:diamond")
    }

    val expected = setOf(ChunkPos(2, 2), ChunkPos(-3, 2), ChunkPos(2, -3))
    awaitClient("the three bases") { ModuleBaseFinder.bases.containsAll(expected) }
    disable(ModuleBaseFinder)
    check(client { ModuleBaseFinder.bases.isEmpty() }) { "reports survived disabling" }
})
