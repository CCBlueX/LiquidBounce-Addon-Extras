package net.ccbluex.liquidbounce.extras.gametest.tests

import net.ccbluex.liquidbounce.extras.gametest.harness.PaperGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.awaitAnswer
import net.ccbluex.liquidbounce.extras.gametest.harness.chatContains
import net.ccbluex.liquidbounce.extras.gametest.harness.command
import net.ccbluex.liquidbounce.extras.gametest.harness.enable
import net.ccbluex.liquidbounce.extras.gametest.harness.fill
import net.ccbluex.liquidbounce.extras.gametest.harness.screenshot
import net.ccbluex.liquidbounce.extras.modules.qol.ModuleLightOverlay
import net.ccbluex.liquidbounce.extras.modules.qol.ModuleLightOverlay.Spawn
import net.ccbluex.liquidbounce.extras.modules.qol.ModuleWaypoints
import net.ccbluex.liquidbounce.extras.util.WorldJournal
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level

class LightOverlayGameTest : PaperGameTest({
    enable(ModuleLightOverlay)
    awaitClient("open ground to count as dark at night") {
        ModuleLightOverlay.spots[origin.south(3)] == Spawn.POTENTIAL
    }

    fill(origin.offset(-6, 0, -6), origin.offset(-2, 4, -2), "minecraft:stone hollow")
    awaitClient("the roofed box to count as dark now") {
        ModuleLightOverlay.spots[origin.offset(-4, 1, -4)] == Spawn.ALWAYS
    }
    screenshot("LightOverlay")
})

class WaypointsGameTest : PaperGameTest({
    command("waypoint add Home")
    check(client { ModuleWaypoints.enabled && ModuleWaypoints.waypoints["Home"] == origin }) {
        "adding a waypoint did not store it and show the list"
    }

    travel(origin.east(10), yaw = 90f, pitch = 15f)
    command("waypoint select Home")
    command("waypoint list")
    awaitClient("the listed waypoint") { it.chatContains("Home at 0 -60 0") }
    screenshot("Waypoints")
    command("waypoint deselect")
    check(client { ModuleWaypoints.selected == null }) { "deselecting the waypoint did not" }

    command("waypoint add Far 100 -60 100")
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

    close()
    val journal = WorldJournal.folder.listFiles()!!.single()
    journal.writeText("{")
    join()
    check(runCatching { command("waypoint list") }.isFailure) { "an unreadable journal was used" }
    check(journal.readText() == "{") { "an unreadable journal was overwritten" }
})
