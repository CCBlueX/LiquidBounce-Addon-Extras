package net.ccbluex.liquidbounce.extras.gametest.tests

import net.ccbluex.liquidbounce.extras.gametest.harness.GameTestScope
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.extras.gametest.harness.PaperGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.awaitServer
import net.ccbluex.liquidbounce.extras.gametest.harness.enable
import net.ccbluex.liquidbounce.extras.gametest.harness.screenshot
import net.ccbluex.liquidbounce.extras.modules.pvp.ModulePearlLaunch
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleWindGlide
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.minecraft.world.item.Items

private val NUMBER = Regex(""": (-?\d+(?:\.\d+)?(?:E-?\d+)?)d$""")

class PearlLaunchGameTest : PaperGameTest({
    run("gamerule minecraft:fall_damage false")
    objective("charges", "minecraft.used:minecraft.wind_charge")
    run("item replace entity $playerName hotbar.1 with minecraft:ender_pearl 16")
    run("item replace entity $playerName hotbar.2 with minecraft:wind_charge 16")
    travel(origin, pitch = -10f)
    awaitClient("the pearls and charges") { it.player!!.inventory.getItem(2).`is`(Items.WIND_CHARGE) }

    enable(ModulePearlLaunch)
    // Where we stood the tick the charge was thrown: at the pearl, not where we threw it from
    val thrownAt = firstAnswer(
        "execute if score $playerName charges matches 1.. run data get entity $playerName Pos[2]", NUMBER,
    ).groupValues[1].toDouble()
    check(thrownAt > origin.z + 8.0) { "the charge went out at z $thrownAt, before the pearl landed us" }
    val peak = peak(40)
    check(peak >= origin.y + 3.0) { "the charge lifted us only to y $peak" }
    awaitOff(ModulePearlLaunch)
    awaitServer("entity @a[name=$playerName,scores={charges=1}]")
})

class WindGlideGameTest : PaperGameTest({
    run("gamerule minecraft:fall_damage false")
    objective("charges", "minecraft.used:minecraft.wind_charge")
    run("item replace entity $playerName armor.chest with minecraft:iron_chestplate")
    run("item replace entity $playerName hotbar.1 with minecraft:elytra")
    run("item replace entity $playerName hotbar.2 with minecraft:wind_charge 64")
    awaitClient("the elytra and charges") { it.player!!.inventory.getItem(2).`is`(Items.WIND_CHARGE) }

    val sent = UsePitches()
    enable(ModuleWindGlide)
    awaitServer("items entity $playerName armor.chest minecraft:elytra")
    awaitServer("entity @a[name=$playerName,nbt={FallFlying:1b}]")
    // Kept up: not one tick on the ground, while the charges keep it going
    val since = server.console.size
    repeat(GLIDE_TICKS) { tick ->
        run("execute unless entity @a[name=$playerName,nbt={FallFlying:1b}] run data get entity $playerName Pos[1]")
        ticks(1)
        if (tick == GLIDE_TICKS / 2) {
            screenshot("WindGlide")
        }
    }
    ticks(5)
    val landed = server.console.drop(since).firstOrNull { NUMBER.containsMatchIn(it.message) }
    check(landed == null) { "the glide ended: $landed" }
    awaitServer("entity @a[name=$playerName,scores={charges=3..}]")
    // Each charge goes down at a pitch of its own within the range
    sent.close()
    val pitches = sent.pitches.filter { it >= 45f }
    check(pitches.size >= 3 && pitches.all { it in 86.5f..90f }) { "the charges went out at $pitches" }
    check(pitches.distinct().size > 1) { "every charge went out at ${pitches.first()}" }

    run("item replace entity $playerName armor.chest with minecraft:iron_chestplate")
    awaitOff(ModuleWindGlide)
    awaitServer("entity @a[name=$playerName,nbt={OnGround:1b}]", timeout = 400)
})

private const val GLIDE_TICKS = 120

/** Counts what the server sees us use, starting at zero so that "none yet" can be asked for. */
private fun GameTestScope.objective(name: String, criterion: String) {
    run("scoreboard objectives add $name $criterion")
    run("scoreboard players set $playerName $name 0")
}

private fun GameTestScope.awaitOff(module: ClientModule) =
    awaitClient("${module.name} to turn itself off", timeout = 400) { !module.enabled }

/** Runs [command] every tick until the server answers; answers come in order, so the first is the earliest tick. */
private fun GameTestScope.firstAnswer(command: String, answer: Regex, timeout: Int = 300): MatchResult {
    val since = server.console.size
    repeat(timeout) {
        run(command)
        ticks(1)
        server.console.drop(since).firstNotNullOfOrNull { answer.find(it.message) }?.let { return it }
    }
    throw AssertionError("The server never answered '$command' with '$answer', see ${server.log}")
}

/** The highest the server saw us over the next [ticks]. */
private fun GameTestScope.peak(ticks: Int): Double {
    val since = server.console.size
    repeat(ticks) {
        run("data get entity $playerName Pos[1]")
        ticks(1)
    }
    return server.console.drop(since).mapNotNull { NUMBER.find(it.message)?.groupValues[1]?.toDouble() }.max()
}


/** Pitches of the item uses sent while it is open. */
private class UsePitches : EventListener, AutoCloseable {

    val pitches: MutableList<Float> = Collections.synchronizedList(mutableListOf())

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        (event.packet as? ServerboundUseItemPacket)?.let { pitches += it.xRot }
    }

    override fun close() = EventManager.unregisterEventHandler(this)

}
