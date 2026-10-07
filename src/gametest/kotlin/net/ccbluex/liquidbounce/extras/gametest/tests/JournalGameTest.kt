package net.ccbluex.liquidbounce.extras.gametest.tests

import net.ccbluex.liquidbounce.extras.gametest.harness.PaperGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.command
import net.ccbluex.liquidbounce.extras.gametest.harness.awaitAnswer
import net.ccbluex.liquidbounce.extras.gametest.harness.disable
import net.ccbluex.liquidbounce.extras.gametest.harness.enable
import net.ccbluex.liquidbounce.extras.modules.qol.ModuleWaypoints
import net.ccbluex.liquidbounce.extras.util.Journal
import net.ccbluex.liquidbounce.extras.util.Finding
import net.ccbluex.liquidbounce.extras.util.JournalStorage
import net.ccbluex.liquidbounce.extras.util.SavedPosition
import net.ccbluex.liquidbounce.extras.util.WorldJournal
import java.nio.file.Files
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level

class JournalGameTest : PaperGameTest({
    awaitClient("the world journal") { WorldJournal.ready }
    command("waypoint add Saved 10 -60 10")
    command("waypoint select Saved")
    disable(ModuleWaypoints)
    check(client { ModuleWaypoints.selected == null && "Saved" in ModuleWaypoints.waypoints })
    enable(ModuleWaypoints)
    check(client { ModuleWaypoints.waypoints["Saved"] == origin.offset(10, 0, 10) })
    reconnect()
    awaitClient("the persisted waypoint after reconnect") { WorldJournal.ready && "Saved" in WorldJournal.waypoints }
    run("execute in minecraft:the_nether run forceload add 0 0")
    awaitAnswer("execute in minecraft:the_nether if loaded 1 80 1", "Test passed")
    run("execute in minecraft:the_nether run fill 0 80 0 4 84 4 minecraft:air")
    run("execute in minecraft:the_nether run fill 0 79 0 4 79 4 minecraft:stone")
    travel(BlockPos(1, 80, 1), dimension = Level.NETHER)
    awaitClient("an isolated Nether journal") { WorldJournal.ready && WorldJournal.waypoints.isEmpty() }
    command("waypoint add Nether")
    travel(origin, dimension = Level.OVERWORLD)
    awaitClient("the Overworld journal") { WorldJournal.ready && WorldJournal.waypoints.keys == setOf("Saved") }

    val directory = Files.createTempDirectory(server.directory, "journal-test-")
    val key = "server:a:25565|minecraft:overworld"
    val expected = Journal(
        findings = mapOf(
            "stash:0:0" to Finding("stash:0:0", SavedPosition(origin), mapOf("minecraft:chest" to 4), 1, 2),
        ),
        waypoints = mapOf("Home" to SavedPosition(origin)),
    )
    JournalStorage(directory).use { store ->
        val _ = store.save(key, Journal())
        val write = store.save(key, expected)
        awaitClient("the journal write") { write.isDone }
        check(!write.isCompletedExceptionally)
    }
    JournalStorage(directory).use { store ->
        val read = store.load(key)
        awaitClient("a fresh journal read") { read.isDone }
        check(read.join() == expected)
        for (other in listOf("server:b:25565|minecraft:overworld", "server:a:25565|minecraft:the_nether")) {
            val different = store.load(other)
            awaitClient("world isolation") { different.isDone }
            check(different.join() == Journal())
        }
        val invalid = "{\"version\":999,\"findings\":{},\"waypoints\":{}}"
        Files.writeString(store.file(key), invalid)
        val failed = store.load(key)
        awaitClient("unsupported version rejection") { failed.isDone }
        check(failed.isCompletedExceptionally)
        val overwrite = store.save(key, Journal())
        awaitClient("blocked overwrite") { overwrite.isDone }
        check(overwrite.isCompletedExceptionally && Files.readString(store.file(key)) == invalid)
        val malformedKey = "malformed"
        val malformed = "{broken json"
        Files.writeString(store.file(malformedKey), malformed)
        val broken = store.load(malformedKey)
        awaitClient("malformed JSON rejection") { broken.isDone }
        check(broken.isCompletedExceptionally && Files.readString(store.file(malformedKey)) == malformed)
    }
    val blocked = directory.resolve("not-a-directory")
    Files.writeString(blocked, "preserved")
    JournalStorage(blocked).use { store ->
        val failed = store.save(key, expected)
        awaitClient("a failed disk write") { failed.isDone }
        check(failed.isCompletedExceptionally && Files.readString(blocked) == "preserved")
    }
    command("waypoint remove Saved")
})
