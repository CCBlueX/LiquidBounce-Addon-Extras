package net.ccbluex.liquidbounce.extras.gametest.tests

import net.ccbluex.gametest.harness.SingleplayerGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.SURFACE
import net.ccbluex.gametest.harness.TestWorld
import net.ccbluex.gametest.harness.action.awaitAnswer
import net.ccbluex.gametest.harness.action.chatContains
import net.ccbluex.liquidbounce.gametest.harness.command
import net.ccbluex.liquidbounce.gametest.harness.disable
import net.ccbluex.liquidbounce.gametest.harness.enable
import net.ccbluex.gametest.harness.action.fill
import net.ccbluex.gametest.harness.action.give
import net.ccbluex.liquidbounce.extras.gametest.harness.screenshot
import net.ccbluex.liquidbounce.extras.modules.qol.ModuleLightOverlay
import net.ccbluex.liquidbounce.extras.modules.qol.ModuleLightOverlay.Spawn
import net.ccbluex.liquidbounce.extras.modules.qol.ModuleNoItemBounce
import net.ccbluex.liquidbounce.extras.modules.qol.ModuleWaypoints
import net.ccbluex.liquidbounce.extras.util.WorldJournal
import net.ccbluex.liquidbounce.extras.util.coordinates
import net.minecraft.core.BlockPos
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level

class LightOverlayGameTest : SingleplayerGameTest(TestWorld.SURFACE, {
    enable(ModuleLightOverlay)
    awaitClient("open ground to count as dark at night") {
        ModuleLightOverlay.spots[origin.south(3)] == Spawn.POTENTIAL
    }

    // Tinted glass keeps the light out as well, so the shed gets a window to look in through
    fill(origin.offset(-2, 0, -8), origin.offset(2, 3, -4), "minecraft:stone hollow")
    fill(origin.offset(-1, 1, -4), origin.offset(1, 2, -4), "minecraft:tinted_glass")
    awaitClient("the shed to count as dark now") {
        ModuleLightOverlay.spots[origin.offset(0, 1, -6)] == Spawn.ALWAYS
    }
    // Looking north, at faces the sun lights
    travel(origin.south(3), yaw = 180f, pitch = 10f)
    screenshot("LightOverlay")
})

class NoItemBounceGameTest : SingleplayerGameTest({
    enable(ModuleNoItemBounce)
    give("minecraft:cobblestone", 16)
    awaitClient("the stack to arrive") { it.player!!.inventory.getItem(0).`is`(Items.COBBLESTONE) }
    awaitClient("the bounce to be left out") { ModuleNoItemBounce.flattenedFrames > 0 }

    disable(ModuleNoItemBounce)
    check(client { ModuleNoItemBounce.flattenedFrames } == 0) { "disabling kept the count" }
    give("minecraft:cobblestone", 16)
    awaitClient("the stack to grow") { it.player!!.inventory.getItem(0).count == 32 }
    ticks(10)
    check(client { ModuleNoItemBounce.flattenedFrames } == 0) { "a bounce was left out while disabled" }
})

class WaypointsGameTest : SingleplayerGameTest(TestWorld.SURFACE, {
    command("waypoint add Home")
    check(client { ModuleWaypoints.enabled && ModuleWaypoints.waypoints["Home"] == origin }) {
        "adding a waypoint did not store it and show the list"
    }
    command("waypoint add Far 120 70 -340")

    travel(origin.east(10), yaw = 90f, pitch = 15f)
    command("waypoint select Home")
    command("waypoint list")
    awaitClient("the listed waypoint") { it.chatContains("Home at ${origin.coordinates}") }
    screenshot("Waypoints")
    command("waypoint deselect")
    check(client { ModuleWaypoints.selected == null }) { "deselecting the waypoint did not" }

    reconnect()
    check(client { ModuleWaypoints.waypoints.keys == setOf("Home", "Far") }) { "the waypoints were not saved" }

    run("execute in minecraft:the_nether run forceload add 0 0")
    awaitAnswer("execute in minecraft:the_nether if loaded 0 80 0", "Test passed")
    run("execute in minecraft:the_nether run fill -2 80 -2 2 83 2 minecraft:air")
    run("execute in minecraft:the_nether run fill -2 79 -2 2 79 2 minecraft:stone")
    travel(BlockPos(0, 80, 0), dimension = Level.NETHER)
    check(client { ModuleWaypoints.waypoints.isEmpty() }) { "Overworld waypoints showed in the Nether" }
    travel(origin, dimension = Level.OVERWORLD)

    command("waypoint remove Home")
    check(client { ModuleWaypoints.waypoints.keys == setOf("Far") }) { "removing the waypoint did not" }

    leave()
    val journal = WorldJournal.folder.listFiles()!!.single()
    journal.writeText("{")
    join()
    check(runCatching { command("waypoint list") }.isFailure) { "an unreadable journal was used" }
    check(journal.readText() == "{") { "an unreadable journal was overwritten" }
})
