package net.ccbluex.liquidbounce.extras.gametest.tests

import net.ccbluex.liquidbounce.extras.gametest.harness.GameTestScope
import net.ccbluex.liquidbounce.extras.gametest.harness.PaperGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.awaitServer
import net.ccbluex.liquidbounce.extras.gametest.harness.enable
import net.ccbluex.liquidbounce.extras.modules.pvp.ModulePearlLaunch
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
