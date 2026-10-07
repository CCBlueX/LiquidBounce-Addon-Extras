package net.ccbluex.liquidbounce.extras.gametest.tests

import net.ccbluex.liquidbounce.extras.gametest.harness.PaperGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.chat
import net.ccbluex.liquidbounce.extras.gametest.harness.chatContains
import net.ccbluex.liquidbounce.extras.gametest.harness.disable
import net.ccbluex.liquidbounce.extras.gametest.harness.enable
import net.ccbluex.liquidbounce.extras.gametest.harness.fill
import net.ccbluex.liquidbounce.extras.gametest.harness.forceLoad
import net.ccbluex.liquidbounce.extras.gametest.harness.hangItemFrame
import net.ccbluex.liquidbounce.extras.gametest.harness.screenshot
import net.ccbluex.liquidbounce.extras.gametest.harness.setBlock
import net.ccbluex.liquidbounce.extras.gametest.harness.summon
import net.ccbluex.liquidbounce.extras.gametest.harness.setting
import net.ccbluex.liquidbounce.extras.gametest.harness.command
import net.ccbluex.liquidbounce.extras.util.WorldJournal
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.ccbluex.liquidbounce.extras.modules.basehunting.ModuleBaseFinder
import net.ccbluex.liquidbounce.extras.modules.basehunting.ModuleCaveDisturbanceDetector
import net.ccbluex.liquidbounce.extras.modules.basehunting.ModuleCollectibleESP
import net.ccbluex.liquidbounce.extras.modules.basehunting.ModulePortalFinder
import net.ccbluex.liquidbounce.extras.modules.basehunting.ModuleSoundLocator
import net.ccbluex.liquidbounce.extras.modules.basehunting.ModuleStashFinder
import net.ccbluex.liquidbounce.extras.modules.basehunting.ModuleSuspiciousBlockDetector
import net.ccbluex.liquidbounce.extras.modules.basehunting.ModuleTunnelTrailESP
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

    disable(ModuleStashFinder)
    enable(ModuleStashFinder)
    awaitClient("an already-loaded stash") { ModuleStashFinder.stashes[stash] == 5 }
    setBlock(BlockPos(204, -60, 3), "minecraft:air")
    awaitClient("the updated count") { ModuleStashFinder.stashes[stash] == 4 }
    setBlock(BlockPos(203, -61, 3), "minecraft:tuff_bricks")
    awaitClient("the support exclusion") { stash !in ModuleStashFinder.stashes }
    setBlock(BlockPos(203, -61, 3), "minecraft:stone")
    awaitClient("the changed support") { ModuleStashFinder.stashes[stash] == 4 }
    setBlock(BlockPos(200, -60, 3), "minecraft:barrel")
    setBlock(BlockPos(201, -60, 3), "minecraft:red_shulker_box")
    awaitClient("container types counted separately") {
        ModuleStashFinder.counts[stash] == mapOf(
            "minecraft:chest" to 2, "minecraft:barrel" to 1, "minecraft:red_shulker_box" to 1,
        )
    }
    client { ModuleStashFinder.setting<Set<Block>>("Containers").set(linkedSetOf(Blocks.CHEST)) }
    awaitClient("the container filter") { stash !in ModuleStashFinder.stashes }
    client { ModuleStashFinder.setting<Set<Block>>("Containers").restore() }
    awaitClient("restored container types") { ModuleStashFinder.stashes[stash] == 4 }
    disable(ModuleStashFinder)
    check(client { ModuleStashFinder.stashes.isEmpty() && ModuleStashFinder.counts.isEmpty() })
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
    val written = BlockPos(-20, -60, 20)
    val signChunk = ChunkPos.containing(written)
    setBlock(written, "minecraft:oak_sign")
    ticks(30)
    check(client { signChunk !in ModuleBaseFinder.bases }) { "a blank sign was evidence" }
    run("data merge block -20 -60 20 {back_text:{messages:['A base','','','']}}")
    awaitClient("text on the back of a sign") { ModuleBaseFinder.evidence[signChunk]?.get("signs") == 1 }
    run("data merge block -20 -60 20 {back_text:{messages:['','','','']}}")
    awaitClient("erased text") { signChunk !in ModuleBaseFinder.bases }
    val villagers = BlockPos(-20, -60, -20)
    val villagerChunk = ChunkPos.containing(villagers)
    summon("minecraft:villager", villagers, "{NoAI:1b,VillagerData:{level:1}}")
    ticks(30)
    check(client { villagerChunk !in ModuleBaseFinder.bases }) { "a novice was evidence" }
    run("data merge entity @e[type=minecraft:villager,limit=1] {VillagerData:{level:2}}")
    awaitClient("a traded villager") { ModuleBaseFinder.evidence[villagerChunk]?.get("villagers") == 1 }
    val custom = BlockPos(20, -60, 20)
    setBlock(custom, "minecraft:diamond_block")
    client { ModuleBaseFinder.setting<Set<Block>>("LandmarkBlocks").set(linkedSetOf(Blocks.DIAMOND_BLOCK)) }
    awaitClient("a custom landmark") { ChunkPos.containing(custom) in ModuleBaseFinder.bases }
    check(client { WorldJournal.findings["base:-2:1"]?.evidence?.get("signs") == 1 })
    command("findings show base:-2:1")
    command("findings waypoint base:-2:1 OldSign")
    screenshot("BaseFinder-findings")
    command("findings forget base:-2:1")
    check(client { "base:-2:1" !in WorldJournal.findings })
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

class PortalFinderGameTest : PaperGameTest({
    enable(ModulePortalFinder)
    val portal = origin.offset(4, 2, 4)
    setBlock(portal, "minecraft:end_portal")
    awaitClient("the portal report") { it.chatContains("Lit portal at 4 -58 4") }
    check(client { ModulePortalFinder.portals[ChunkPos.containing(portal)] } == portal)
    screenshot("PortalFinder")
})

class CaveDisturbanceDetectorGameTest : PaperGameTest({
    enable(ModuleCaveDisturbanceDetector)
    ticks(30)
    check(client { ModuleCaveDisturbanceDetector.findings.isEmpty() }) { "found disturbances without caves" }

    // A cave of one cave air block, and one block of plain air mined into its wall
    fill(origin.offset(6, 0, 6), origin.offset(10, 4, 10), "minecraft:stone")
    setBlock(origin.offset(8, 2, 8), "minecraft:cave_air")
    val mined = origin.offset(9, 2, 8)
    setBlock(mined, "minecraft:air")
    awaitClient("the mined block", timeout = 60) { ModuleCaveDisturbanceDetector.findings == setOf(mined) }
})

class TunnelTrailESPGameTest : PaperGameTest({
    enable(ModuleTunnelTrailESP)

    // A six long tunnel along X
    fill(BlockPos(4, -60, 8), BlockPos(11, -57, 10), "minecraft:stone")
    fill(BlockPos(5, -59, 9), BlockPos(10, -58, 9), "minecraft:air")
    // Six steps up towards south, three blocks of headroom each
    fill(BlockPos(-9, -60, 3), BlockPos(-7, -50, 11), "minecraft:stone")
    for (step in 0..5) {
        fill(BlockPos(-8, -59 + step, 4 + step), BlockPos(-8, -57 + step, 4 + step), "minecraft:air")
    }
    // A five deep shaft
    fill(BlockPos(12, -60, -6), BlockPos(14, -54, -4), "minecraft:stone")
    fill(BlockPos(13, -59, -5), BlockPos(13, -55, -5), "minecraft:air")

    val entrances = listOf(BlockPos(5, -59, 9), BlockPos(-8, -59, 4), BlockPos(13, -55, -5))
    awaitClient("the tunnel, staircase and shaft", timeout = 60) {
        ModuleTunnelTrailESP.findings.containsAll(entrances)
    }
    screenshot("TunnelTrailESP")
})

class SoundLocatorGameTest : PaperGameTest({
    enable(ModuleSoundLocator)
    run("playsound minecraft:block.chest.open block $playerName 4 -60 4")
    run("playsound minecraft:block.stone.break block $playerName -4 -60 4")
    awaitClient("the chest sound") { BlockPos(4, -60, 4) in ModuleSoundLocator.markers }
    check(BlockPos(-4, -60, 4) !in client { ModuleSoundLocator.markers }) { "marked an unselected sound" }
    disable(ModuleSoundLocator)
    check(client { ModuleSoundLocator.markers.isEmpty() }) { "markers survived disabling" }
})
