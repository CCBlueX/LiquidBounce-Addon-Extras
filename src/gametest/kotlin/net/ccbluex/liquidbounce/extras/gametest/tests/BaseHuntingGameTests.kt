package net.ccbluex.liquidbounce.extras.gametest.tests

import net.ccbluex.gametest.harness.SingleplayerGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.SURFACE
import net.ccbluex.gametest.harness.TestWorld
import net.ccbluex.gametest.harness.action.awaitAnswer
import net.ccbluex.gametest.harness.action.chat
import net.ccbluex.gametest.harness.action.chatContains
import net.ccbluex.liquidbounce.gametest.harness.configure
import net.ccbluex.liquidbounce.gametest.harness.command
import net.ccbluex.liquidbounce.gametest.harness.disable
import net.ccbluex.liquidbounce.gametest.harness.enable
import net.ccbluex.gametest.harness.action.fill
import net.ccbluex.gametest.harness.action.hangItemFrame
import net.ccbluex.liquidbounce.extras.gametest.harness.screenshot
import net.ccbluex.gametest.harness.action.setBlock
import net.ccbluex.liquidbounce.gametest.harness.setting
import net.ccbluex.gametest.harness.action.summon
import net.ccbluex.liquidbounce.extras.modules.basehunting.ModuleBaseFinder
import net.ccbluex.liquidbounce.extras.modules.basehunting.ModuleCaveDisturbanceDetector
import net.ccbluex.liquidbounce.extras.modules.basehunting.ModuleCollectibleESP
import net.ccbluex.liquidbounce.extras.modules.basehunting.ModulePortalFinder
import net.ccbluex.liquidbounce.extras.modules.basehunting.ModuleSoundLocator
import net.ccbluex.liquidbounce.extras.modules.basehunting.ModuleStashFinder
import net.ccbluex.liquidbounce.extras.modules.basehunting.ModuleSuspiciousBlockDetector
import net.ccbluex.liquidbounce.extras.modules.basehunting.ModuleTunnelTrailESP
import net.ccbluex.liquidbounce.extras.modules.qol.ModuleWaypoints
import net.ccbluex.liquidbounce.extras.util.WorldJournal
import net.ccbluex.liquidbounce.extras.util.coordinates
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks

class StashFinderGameTest : SingleplayerGameTest(TestWorld.SURFACE, {
    // Filled while the client is in the End, so the chests arrive with the chunk
    enable(ModuleStashFinder)
    // Far from the dragon, on a platform like in BaseFinderEnd
    val end = BlockPos(1000, 80, 1000)
    run("execute in minecraft:the_end run forceload add 1000 1000")
    awaitAnswer("execute in minecraft:the_end if loaded 1000 80 1000", "Test passed")
    run("execute in minecraft:the_end run fill 999 79 999 1001 79 1001 minecraft:stone")
    travel(end, dimension = Level.END)
    val row = (-2..2).map { origin.offset(it, 0, -4) }
    fill(row.first(), row.last(), "minecraft:chest[facing=south]")
    travel(origin, dimension = Level.OVERWORLD)

    val stash = ChunkPos.containing(row.first())
    awaitClient("the stash report") {
        it.chatContains("5 containers in the chunk at ${stash.minBlockX} ${stash.minBlockZ}")
    }
    check(client { stash.containers } == 5)
    // Looking north, at faces the sun lights
    travel(origin.offset(3, 0, 1), yaw = 155f, pitch = 8f)
    screenshot("StashFinder")

    disable(ModuleStashFinder)
    enable(ModuleStashFinder)
    awaitClient("the stash after enabling again") { stash.containers == 5 }
    setBlock(row[4], "minecraft:air")
    awaitClient("one container less") { stash.containers == 4 }
    setBlock(row[3].below(), "minecraft:tuff_bricks")
    awaitClient("a container on a trial chamber block left out") { stash !in ModuleStashFinder.stashes }
    setBlock(row[3].below(), "minecraft:grass_block")
    awaitClient("the container back") { stash.containers == 4 }

    setBlock(row[0], "minecraft:barrel")
    setBlock(row[1], "minecraft:red_shulker_box")
    awaitClient("containers counted by block") {
        ModuleStashFinder.stashes[stash] ==
            mapOf("minecraft:chest" to 2, "minecraft:barrel" to 1, "minecraft:red_shulker_box" to 1)
    }
    configure(ModuleStashFinder, "Containers" to linkedSetOf(Blocks.CHEST))
    awaitClient("only chests counted") { stash !in ModuleStashFinder.stashes }
    client { ModuleStashFinder.setting<Set<Block>>("Containers").restore() }
    awaitClient("every container counted again") { stash.containers == 4 }

    disable(ModuleStashFinder)
    check(client { ModuleStashFinder.stashes.isEmpty() }) { "stashes survived disabling" }
})

private val ChunkPos.containers get() = ModuleStashFinder.stashes[this]?.values?.sum()

class BaseFinderGameTest : SingleplayerGameTest({
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
    awaitClient("the three bases") { ModuleBaseFinder.bases.keys.containsAll(expected) }

    val sign = BlockPos(-20, -60, 20)
    val signChunk = ChunkPos.containing(sign)
    setBlock(sign, "minecraft:oak_sign")
    ticks(30)
    check(client { signChunk !in ModuleBaseFinder.bases }) { "a blank sign was evidence" }
    run("data merge block -20 -60 20 {back_text:{messages:['A base','','','']}}")
    awaitClient("text on the back of a sign") { ModuleBaseFinder.bases[signChunk]?.get("signs") == 1 }
    run("data merge block -20 -60 20 {back_text:{messages:['','','','']}}")
    awaitClient("the text erased") { signChunk !in ModuleBaseFinder.bases }

    val villager = BlockPos(-20, -60, -20)
    val villagerChunk = ChunkPos.containing(villager)
    summon("minecraft:villager", villager, "{NoAI:1b,VillagerData:{level:1}}")
    ticks(30)
    check(client { villagerChunk !in ModuleBaseFinder.bases }) { "a novice was evidence" }
    run("data merge entity @e[type=minecraft:villager,limit=1] {VillagerData:{level:2}}")
    awaitClient("a traded villager") { ModuleBaseFinder.bases[villagerChunk]?.get("villagers") == 1 }

    val landmark = BlockPos(20, -60, 20)
    setBlock(landmark, "minecraft:diamond_block")
    configure(ModuleBaseFinder, "LandmarkBlocks" to linkedSetOf(Blocks.DIAMOND_BLOCK))
    awaitClient("a landmark of your own") { ChunkPos.containing(landmark) in ModuleBaseFinder.bases }
    disable(ModuleBaseFinder)
    check(client { ModuleBaseFinder.bases.isEmpty() }) { "reports survived disabling" }
})

class BaseFinderEndGameTest : SingleplayerGameTest({
    val pos = BlockPos(1000, 80, 1000)
    run("execute in minecraft:the_end run forceload add 992 992")
    awaitAnswer("execute in minecraft:the_end if loaded 1000 80 1000", "Test passed")
    run("execute in minecraft:the_end run fill 998 80 998 1005 84 1005 minecraft:air")
    run("execute in minecraft:the_end run fill 998 79 998 1005 79 1005 minecraft:stone")
    travel(pos, dimension = Level.END)
    // Like on an End ship
    for (x in listOf(1000, 1002)) {
        run("execute in minecraft:the_end run setblock $x 80 1003 minecraft:stone")
        run(
            "execute in minecraft:the_end run summon minecraft:item_frame $x.5 80 1002.5 " +
                "{Pos:[$x.5d,80d,1002.5d],block_pos:[I;$x,80,1002],Facing:2b,Item:{id:'minecraft:elytra',count:1}}",
        )
    }
    enable(ModuleBaseFinder)
    ticks(60)
    val chunk = ChunkPos.containing(pos)
    check(client { chunk !in ModuleBaseFinder.bases }) { "elytra frames counted as placed entities" }
    run(
        "execute in minecraft:the_end as @e[type=minecraft:item_frame] run data merge entity @s " +
            "{Item:{id:'minecraft:diamond',count:1}}",
    )
    awaitClient("diamond frames in the End") { ModuleBaseFinder.bases[chunk]?.get("entities") == 2 }
})

class FindingsGameTest : SingleplayerGameTest({
    for (x in 0..3) {
        setBlock(BlockPos(x, -60, 20), "minecraft:chest")
    }
    enable(ModuleStashFinder)
    awaitClient("the stash report") { it.chatContains("4 containers in the chunk at 0 16") }
    val stash = "stash:0:1"
    check(client { WorldJournal.findings[stash]?.evidence } == mapOf("minecraft:chest" to 4)) {
        "the stash was not saved"
    }

    disable(ModuleStashFinder)
    enable(ModuleStashFinder)
    awaitClient("the stash found again") { ChunkPos(0, 1) in ModuleStashFinder.stashes }
    check(chat().count { "containers in the chunk" in it } == 1) { "a saved stash was reported again" }
    setBlock(BlockPos(4, -60, 20), "minecraft:barrel")
    awaitClient("the changed stash reported") { it.chatContains("5 containers in the chunk at 0 16") }

    command("findings list")
    awaitClient("the listed stash") { it.chatContains("$stash at ") }
    command("findings waypoint $stash Stash")
    check(client { ModuleWaypoints.selected == "Stash" }) { "the finding did not become the selected waypoint" }
    check(client { ModuleWaypoints.waypoints["Stash"] == WorldJournal.findings[stash]?.position })
    command("findings forget $stash")
    check(client { stash !in WorldJournal.findings }) { "forgetting the stash did not" }
})

class SuspiciousBlockDetectorGameTest : SingleplayerGameTest(TestWorld.SURFACE, {
    // Looking north, as in StashFinder
    travel(origin, yaw = 180f, pitch = 15f)
    val block = origin.north(4)
    enable(ModuleSuspiciousBlockDetector)
    // Enabling rescans every chunk, so all of them are settling again
    setBlock(block, "minecraft:gold_block")
    ticks(20)
    check(client { ModuleSuspiciousBlockDetector.findings.isEmpty() }) { "reported a block of a settling chunk" }

    // The rescan can come late on a slow machine, and each one settles the chunk anew
    awaitClient("the chunk to settle") { ModuleSuspiciousBlockDetector.isSettled(block) }
    setBlock(block, "minecraft:iron_block")
    awaitClient("the iron block report") { it.chatContains("New Block of Iron at ${block.coordinates}") }

    val trapdoor = block.west(3)
    setBlock(trapdoor, "minecraft:oak_trapdoor")
    awaitClient("the trapdoor report") { it.chatContains("New Oak Trapdoor at ${trapdoor.coordinates}") }
    setBlock(trapdoor, "minecraft:oak_trapdoor[open=true]")
    ticks(20)
    check(chat().count { "Oak Trapdoor" in it } == 1) { "opening the trapdoor was reported as a new block" }

    val chest = block.east(3)
    setBlock(chest, "minecraft:chest[facing=south]")
    awaitClient("the chest report") { it.chatContains("New Chest at ${chest.coordinates}") }
    ticks(10)
    screenshot("SuspiciousBlockDetector")
})

class CollectibleESPGameTest : SingleplayerGameTest(TestWorld.SURFACE, {
    travel(origin.north(2), yaw = 180f, pitch = 10f)
    enable(ModuleCollectibleESP)
    ticks(20)
    check(client { ModuleCollectibleESP.findings.isEmpty() }) { "found collectibles in an empty world" }

    val banner = origin.offset(3, 0, -7)
    val elytra = origin.offset(0, 1, -6)
    val totem = origin.offset(-2, 1, -6)
    val dirt = origin.offset(2, 1, -6)
    setBlock(banner, "minecraft:red_banner")
    hangItemFrame(elytra, Direction.SOUTH, "minecraft:elytra")
    hangItemFrame(totem, Direction.SOUTH, "minecraft:totem_of_undying")
    hangItemFrame(dirt, Direction.SOUTH, "minecraft:dirt")

    awaitClient("the banner, the elytra and the totem") {
        ModuleCollectibleESP.findings.containsAll(listOf(banner, elytra, totem))
    }
    check(dirt !in client { ModuleCollectibleESP.findings }) { "a framed dirt block counted as collectible" }
    screenshot("CollectibleESP")
    disable(ModuleCollectibleESP)
    check(client { ModuleCollectibleESP.findings.isEmpty() }) { "findings survived disabling" }
})

class PortalFinderGameTest : SingleplayerGameTest(TestWorld.SURFACE, {
    travel(origin.south(2), yaw = 180f)
    enable(ModulePortalFinder)
    // A nether portal frame, lit like a player would
    val frame = origin.offset(-2, 0, -6)
    fill(frame, frame.offset(3, 4, 0), "minecraft:obsidian")
    fill(frame.offset(1, 1, 0), frame.offset(2, 3, 0), "minecraft:air")
    setBlock(frame.offset(1, 1, 0), "minecraft:fire")

    val chunk = ChunkPos.containing(frame)
    awaitClient("the lit portal") { chunk in ModulePortalFinder.portals }
    val portal = client { ModulePortalFinder.portals.getValue(chunk) }
    awaitClient("the portal report") { it.chatContains("Lit portal at ${portal.coordinates}") }
    screenshot("PortalFinder")
})

class CaveDisturbanceDetectorGameTest : SingleplayerGameTest({
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

class TunnelTrailESPGameTest : SingleplayerGameTest(TestWorld.SURFACE, {
    travel(origin.south(3), yaw = 180f, pitch = 10f)
    enable(ModuleTunnelTrailESP)

    // An eight long tunnel straight ahead
    val tunnel = origin.north(5)
    fill(tunnel.offset(-1, 0, -7), tunnel.offset(1, 2, 0), "minecraft:stone")
    fill(tunnel.offset(0, 0, -7), tunnel.above(), "minecraft:air")
    // Six steps up towards north, three blocks of headroom each
    val staircase = origin.offset(-5, 1, -4)
    fill(staircase.offset(-1, -1, -7), staircase.offset(1, 9, 0), "minecraft:stone")
    for (step in 0..5) {
        fill(staircase.offset(0, step, -step), staircase.offset(0, step + 2, -step), "minecraft:air")
    }
    // A five deep shaft
    val shaft = origin.offset(5, 5, -5)
    fill(shaft.offset(-1, -5, -1), shaft.offset(1, 0, 1), "minecraft:stone")
    fill(shaft.below(4), shaft, "minecraft:air")

    awaitClient("the tunnel, staircase and shaft", timeout = 60) {
        ModuleTunnelTrailESP.findings.containsAll(listOf(tunnel, staircase, shaft))
    }
    screenshot("TunnelTrailESP")
})

class SoundLocatorGameTest : SingleplayerGameTest(TestWorld.SURFACE, {
    travel(origin, yaw = 180f, pitch = 10f)
    configure(ModuleSoundLocator, "Chat" to true)
    enable(ModuleSoundLocator)
    // A chest opened inside a hut, out of sight
    val hut = origin.offset(-2, -1, -10)
    fill(hut, hut.offset(4, 3, 4), "minecraft:cobblestone hollow")
    val chest = hut.offset(2, 1, 2)
    setBlock(chest, "minecraft:chest")
    val stone = origin.offset(5, 0, -4)
    run("playsound minecraft:block.chest.open block $playerName ${chest.coordinates}")
    run("playsound minecraft:block.stone.break block $playerName ${stone.coordinates}")
    awaitClient("the chest sound") { chest in ModuleSoundLocator.markers }
    check(stone !in client { ModuleSoundLocator.markers }) { "marked an unselected sound" }
    screenshot("SoundLocator")
    disable(ModuleSoundLocator)
    check(client { ModuleSoundLocator.markers.isEmpty() }) { "markers survived disabling" }
})
