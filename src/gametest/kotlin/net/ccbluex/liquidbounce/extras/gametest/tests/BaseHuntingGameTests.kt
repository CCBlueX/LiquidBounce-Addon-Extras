package net.ccbluex.liquidbounce.extras.gametest.tests

import net.ccbluex.liquidbounce.extras.gametest.harness.PaperGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.chat
import net.ccbluex.liquidbounce.extras.gametest.harness.chatContains
import net.ccbluex.liquidbounce.extras.gametest.harness.disable
import net.ccbluex.liquidbounce.extras.gametest.harness.enable
import net.ccbluex.liquidbounce.extras.gametest.harness.forceLoad
import net.ccbluex.liquidbounce.extras.gametest.harness.hangItemFrame
import net.ccbluex.liquidbounce.extras.gametest.harness.screenshot
import net.ccbluex.liquidbounce.extras.gametest.harness.setBlock
import net.ccbluex.liquidbounce.extras.modules.basehunting.ModuleBaseFinder
import net.ccbluex.liquidbounce.extras.modules.basehunting.ModuleCollectibleESP
import net.ccbluex.liquidbounce.extras.modules.basehunting.ModuleStashFinder
import net.ccbluex.liquidbounce.extras.modules.basehunting.ModuleSuspiciousBlockDetector
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

class SuspiciousBlockDetectorGameTest : PaperGameTest({
    val block = origin.south(5)
    enable(ModuleSuspiciousBlockDetector)
    // Enabling rescans every chunk, so all of them are settling again
    setBlock(block, "minecraft:gold_block")
    ticks(20)
    check(client { ModuleSuspiciousBlockDetector.findings.isEmpty() }) { "reported a block of a settling chunk" }

    ticks(40)
    setBlock(block, "minecraft:iron_block")
    awaitClient("the iron block report") { it.chatContains("New Block of Iron at 0 -60 5") }

    val trapdoor = block.east(3)
    setBlock(trapdoor, "minecraft:oak_trapdoor")
    awaitClient("the trapdoor report") { it.chatContains("New Oak Trapdoor at 3 -60 5") }
    setBlock(trapdoor, "minecraft:oak_trapdoor[open=true]")
    ticks(20)
    check(chat().count { "Oak Trapdoor" in it } == 1) { "opening the trapdoor was reported as a new block" }
    screenshot("SuspiciousBlockDetector")
})

class CollectibleESPGameTest : PaperGameTest({
    enable(ModuleCollectibleESP)
    ticks(20)
    check(client { ModuleCollectibleESP.findings.isEmpty() }) { "found collectibles in an empty world" }

    val banner = origin.offset(-3, 0, 5)
    val elytra = origin.offset(1, 1, 6)
    val dirt = origin.offset(-1, 1, 6)
    setBlock(banner, "minecraft:white_banner")
    hangItemFrame(elytra, Direction.NORTH, "minecraft:elytra")
    hangItemFrame(dirt, Direction.NORTH, "minecraft:dirt")

    awaitClient("the banner and the elytra") { ModuleCollectibleESP.findings.containsAll(listOf(banner, elytra)) }
    check(dirt !in client { ModuleCollectibleESP.findings }) { "a framed dirt block counted as collectible" }
    screenshot("CollectibleESP")
    disable(ModuleCollectibleESP)
    check(client { ModuleCollectibleESP.findings.isEmpty() }) { "findings survived disabling" }
})
