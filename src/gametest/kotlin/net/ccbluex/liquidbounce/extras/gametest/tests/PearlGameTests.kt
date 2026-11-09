package net.ccbluex.liquidbounce.extras.gametest.tests

import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.extras.gametest.harness.GameTestScope
import net.ccbluex.liquidbounce.extras.gametest.harness.PaperGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.awaitServer
import net.ccbluex.liquidbounce.extras.gametest.harness.chatContains
import net.ccbluex.liquidbounce.extras.gametest.harness.disable
import net.ccbluex.liquidbounce.extras.gametest.harness.enable
import net.ccbluex.liquidbounce.extras.gametest.harness.query
import net.ccbluex.liquidbounce.extras.gametest.harness.screenshot
import net.ccbluex.liquidbounce.extras.gametest.harness.setting
import net.ccbluex.liquidbounce.extras.gametest.harness.summon
import net.ccbluex.liquidbounce.extras.modules.pvp.ModulePearlCatch
import net.ccbluex.liquidbounce.extras.modules.pvp.ModulePearlHop
import net.ccbluex.liquidbounce.extras.modules.pvp.ModulePearlLaunch
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleWindGlide
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.item.Items
import java.util.Collections
import kotlin.math.abs
import kotlin.math.hypot

private val NUMBER = Regex(""": (-?\d+(?:\.\d+)?(?:E-?\d+)?)d$""")
private val POSITION = Regex("""\[(-?[\d.E-]+)d, (-?[\d.E-]+)d, (-?[\d.E-]+)d]""")

class PearlCatchGameTest : PaperGameTest({
    run("gamerule minecraft:fall_damage false")
    objective("pearls", "minecraft.used:minecraft.ender_pearl")
    objective("charges", "minecraft.used:minecraft.wind_charge")
    run("item replace entity $playerName hotbar.1 with minecraft:ender_pearl 16")
    awaitClient("the pearls") { it.player!!.inventory.getItem(1).`is`(Items.ENDER_PEARL) }

    // Without wind charges the pearl stays in the hand
    enable(ModulePearlCatch)
    awaitOff(ModulePearlCatch)
    awaitClient("the note about the charges") { it.chatContains("wind charges") }
    awaitServer("entity @a[name=$playerName,scores={pearls=0}]")

    run("item replace entity $playerName hotbar.2 with minecraft:wind_charge 16")
    travel(origin, pitch = -89f)
    awaitClient("the charges") { it.player!!.inventory.getItem(2).`is`(Items.WIND_CHARGE) }

    // Held use key, as when eating or blocking: the module waits instead of throwing over it
    input.holdKey { it.keyUse }
    enable(ModulePearlCatch)
    ticks(40)
    check(client { ModulePearlCatch.enabled }) { "PearlCatch gave up while the use key was held" }
    awaitServer("entity @a[name=$playerName,scores={pearls=0,charges=0}]")
    disable(ModulePearlCatch)
    input.releaseKey { it.keyUse }

    // Simulated: the charge goes out once the launch it gives puts us into the falling pearl, wherever the
    // pearl's spread takes it
    for (attempt in 1..2) {
        travel(origin, pitch = -89f)
        enable(ModulePearlCatch)
        awaitServer("entity @e[type=minecraft:ender_pearl]")
        val caught = firstAnswer(
            "execute unless entity @e[type=minecraft:ender_pearl] run data get entity $playerName Pos[1]", NUMBER,
        ).groupValues[1].toDouble()
        // A pearl that hits us standing still takes us to where it was, above our head, so a catch has to be higher
        check(caught >= origin.y + 4.0) { "catch $attempt took us at y $caught, not mid-air" }
        awaitServer("entity @a[name=$playerName,scores={pearls=$attempt,charges=$attempt}]")
        awaitOff(ModulePearlCatch)
    }

    // Fixed timing, along the view and straight up: the charge follows the pearl after Delay
    for ((mode, used) in listOf("View" to 3, "Up" to 4)) {
        travel(origin, pitch = -60f)
        client {
            ModulePearlCatch.setting<Any>("Mode").setByString(mode)
            ModulePearlCatch.setting<Any>("Timing").setByString("Fixed")
        }
        enable(ModulePearlCatch)
        ticksUntil("pearls", used)
        val gap = ticksUntil("charges", used)
        check(gap in CATCH_DELAY - 4..CATCH_DELAY + 4) { "$mode threw the charge $gap ticks after the pearl" }
        awaitOff(ModulePearlCatch)
    }

    // Fast from the offhand: both go out back to back and the totem comes back
    travel(origin)
    run("item replace entity $playerName weapon.offhand with minecraft:totem_of_undying")
    awaitClient("the totem") { it.player!!.offhandItem.`is`(Items.TOTEM_OF_UNDYING) }
    client { ModulePearlCatch.setting<Any>("Mode").setByString("Fast") }
    enable(ModulePearlCatch)
    awaitServer("entity @a[name=$playerName,scores={pearls=5,charges=5}]")
    awaitServer("items entity $playerName weapon.offhand minecraft:totem_of_undying")
    awaitServer("items entity $playerName hotbar.2 minecraft:wind_charge[count=11]")
    awaitOff(ModulePearlCatch)
})

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

class PearlHopGameTest : PaperGameTest({
    run("gamerule minecraft:fall_damage false")
    objective("pearls", "minecraft.used:minecraft.ender_pearl")
    run("item replace entity $playerName hotbar.3 with minecraft:ender_pearl 16")
    awaitClient("the pearls") { it.player!!.inventory.getItem(3).`is`(Items.ENDER_PEARL) }

    // Legit: one hop, then slot and view are back where they were
    enable(ModulePearlHop)
    awaitServer("entity @a[name=$playerName,scores={pearls=1}]")
    awaitOff(ModulePearlHop)
    checkHopped()
    check(client { it.player!!.inventory.selectedSlot } == 0) { "the slot stayed on the pearls" }
    awaitServer("entity @a[name=$playerName,nbt={SelectedItemSlot:0}]")
    check(abs(client { it.player!!.xRot }) < 1f) { "the view stayed down" }

    // Silent: neither slot nor view move
    travel(origin)
    client { ModulePearlHop.setting<Any>("Mode").setByString("Silent") }
    enable(ModulePearlHop)
    repeat(20) {
        check(client { it.player!!.inventory.selectedSlot == 0 && it.player!!.xRot == 0f }) { "the hop showed" }
        ticks(1)
    }
    awaitServer("entity @a[name=$playerName,scores={pearls=2}]")
    awaitOff(ModulePearlHop)
    checkHopped()

    // Auto: nothing until a player hits us; our own pearl and a mob's hit do not count
    travel(origin)
    val auto = client { ModulePearlHop.containedValues.first { it.name == "Auto" } as ValueGroup }
    client { auto.setting<Boolean>("Enabled").set(true) }
    enable(ModulePearlHop)
    ticks(40)
    awaitServer("entity @a[name=$playerName,scores={pearls=2}]")
    run("difficulty easy")
    summon("minecraft:zombie", origin.north(3), "{NoAI:1b}")
    awaitClient("the zombie") { it.level!!.entitiesForRendering().any { entity -> entity is Zombie } }
    run("damage $playerName 1 minecraft:mob_attack by @e[type=minecraft:zombie,limit=1]")
    ticks(60)
    awaitServer("entity @a[name=$playerName,scores={pearls=2}]")
    // A melee hit is told apart by its damage type, which only players deal
    run("damage $playerName 1 minecraft:player_attack by @e[type=minecraft:zombie,limit=1]")
    awaitServer("entity @a[name=$playerName,scores={pearls=3}]")
    ticks(60)
    awaitServer("entity @a[name=$playerName,scores={pearls=3}]")

    // Trigger Any: the mob's hit is enough
    client { auto.setting<Any>("Trigger").setByString("Any") }
    run("damage $playerName 1 minecraft:mob_attack by @e[type=minecraft:zombie,limit=1]")
    awaitServer("entity @a[name=$playerName,scores={pearls=4}]")
    ticks(60)
    awaitServer("entity @a[name=$playerName,scores={pearls=4}]")
    disable(ModulePearlHop)
    run("kill @e[type=minecraft:zombie]")
    run("difficulty peaceful")
})

private const val GLIDE_TICKS = 120

/** PearlCatch's default Delay. */
private const val CATCH_DELAY = 66

/** Ticks until the server counts [count] on our [objective], asking every tick. */
private fun GameTestScope.ticksUntil(objective: String, count: Int): Int {
    val since = server.console.size
    repeat(CATCH_DELAY * 3) { tick ->
        run("execute if score $playerName $objective matches $count.. run say $objective $count")
        ticks(1)
        if (server.console.drop(since).any { "[Server] $objective $count" in it.message }) {
            return tick
        }
    }
    throw AssertionError("The server never counted $count $objective, see ${server.log}")
}

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

/** A short hop: the server moved us off the block's center, but not far. */
private fun GameTestScope.checkHopped() {
    val (x, _, z) = query("data get entity $playerName Pos", POSITION).destructured
    val hop = hypot(x.toDouble() - origin.x - 0.5, z.toDouble() - origin.z - 0.5)
    check(hop in 0.1..2.0) { "hopped $hop blocks" }
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
