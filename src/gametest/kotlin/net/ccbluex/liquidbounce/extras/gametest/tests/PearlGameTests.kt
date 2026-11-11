package net.ccbluex.liquidbounce.extras.gametest.tests

import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.gametest.harness.GameTestScope
import net.ccbluex.gametest.harness.ServerGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.SURFACE
import net.ccbluex.gametest.harness.TestWorld
import net.ccbluex.gametest.harness.action.awaitServer
import net.ccbluex.gametest.harness.action.chatContains
import net.ccbluex.gametest.harness.action.fill
import net.ccbluex.liquidbounce.gametest.harness.configure
import net.ccbluex.liquidbounce.gametest.harness.disable
import net.ccbluex.liquidbounce.gametest.harness.enable
import net.ccbluex.gametest.harness.action.query
import net.ccbluex.liquidbounce.extras.gametest.harness.screenshot
import net.ccbluex.liquidbounce.gametest.harness.setting
import net.ccbluex.gametest.harness.action.summon
import net.ccbluex.liquidbounce.extras.modules.pvp.ModulePearlCatch
import net.ccbluex.liquidbounce.extras.modules.pvp.ModulePearlHop
import net.ccbluex.liquidbounce.extras.modules.pvp.ModulePearlLaunch
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleWindGlide
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.item.Items
import java.util.Collections
import kotlin.math.abs
import kotlin.math.hypot

private val NUMBER = Regex(""": (-?\d+(?:\.\d+)?(?:E-?\d+)?)d$""")
private val POSITION = Regex("""\[(-?[\d.E-]+)d, (-?[\d.E-]+)d, (-?[\d.E-]+)d]""")

class PearlCatchGameTest : ServerGameTest({
    run("gamerule minecraft:fall_damage false")
    val pearls = objective("pearls", "minecraft.used:minecraft.ender_pearl")
    val charges = objective("charges", "minecraft.used:minecraft.wind_charge")
    run("item replace entity $playerName hotbar.1 with minecraft:ender_pearl 16")
    awaitClient("the pearls") { it.player!!.inventory.getItem(1).`is`(Items.ENDER_PEARL) }

    // Without wind charges the pearl stays in the hand
    enable(ModulePearlCatch)
    awaitOff(ModulePearlCatch)
    awaitClient("the note about the charges") { it.chatContains("wind charges") }
    awaitServer("entity @a[name=$playerName,scores={$pearls=0}]")

    run("item replace entity $playerName hotbar.2 with minecraft:wind_charge 16")
    travel(origin, pitch = -89f)
    awaitClient("the charges") { it.player!!.inventory.getItem(2).`is`(Items.WIND_CHARGE) }

    // Held use key, as when eating or blocking: the module waits instead of throwing over it
    input.holdKey { it.keyUse }
    enable(ModulePearlCatch)
    ticks(40)
    check(client { ModulePearlCatch.enabled }) { "PearlCatch gave up while the use key was held" }
    awaitServer("entity @a[name=$playerName,scores={$pearls=0,$charges=0}]")
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
        awaitServer("entity @a[name=$playerName,scores={$pearls=$attempt,$charges=$attempt}]")
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
        ticksUntil(pearls, used)
        val gap = ticksUntil(charges, used)
        check(gap in CATCH_DELAY - 4..CATCH_DELAY + 4) { "$mode threw the charge $gap ticks after the pearl" }
        awaitOff(ModulePearlCatch)
    }

    // Fast from the offhand: both go out back to back and the totem comes back
    travel(origin)
    run("item replace entity $playerName weapon.offhand with minecraft:totem_of_undying")
    awaitClient("the totem") { it.player!!.offhandItem.`is`(Items.TOTEM_OF_UNDYING) }
    configure(ModulePearlCatch, "Mode" to "Fast")
    enable(ModulePearlCatch)
    awaitServer("entity @a[name=$playerName,scores={$pearls=5,$charges=5}]")
    awaitServer("items entity $playerName weapon.offhand minecraft:totem_of_undying")
    awaitServer("items entity $playerName hotbar.2 minecraft:wind_charge[count=11]")
    awaitOff(ModulePearlCatch)
})

class PearlLaunchGameTest : ServerGameTest({
    run("gamerule minecraft:fall_damage false")
    val charges = objective("charges", "minecraft.used:minecraft.wind_charge")
    run("item replace entity $playerName hotbar.1 with minecraft:ender_pearl 16")
    run("item replace entity $playerName hotbar.2 with minecraft:wind_charge 16")
    travel(origin, pitch = -10f)
    awaitClient("the pearls and charges") { it.player!!.inventory.getItem(2).`is`(Items.WIND_CHARGE) }

    enable(ModulePearlLaunch)
    // Where we stood the tick the charge was thrown: at the pearl, not where we threw it from
    val thrownAt = firstAnswer(
        "execute if score $playerName $charges matches 1.. run data get entity $playerName Pos[2]", NUMBER,
    ).groupValues[1].toDouble()
    check(thrownAt > origin.z + 8.0) { "the charge went out at z $thrownAt, before the pearl landed us" }
    val peak = peak(40)
    check(peak >= origin.y + 3.0) { "the charge lifted us only to y $peak" }
    awaitOff(ModulePearlLaunch)
    awaitServer("entity @a[name=$playerName,scores={$charges=1}]")
})

class WindGlideGameTest : ServerGameTest(TestWorld.SURFACE, {
    run("gamerule minecraft:fall_damage false")
    val charges = objective("charges", "minecraft.used:minecraft.wind_charge")
    run("item replace entity $playerName armor.chest with minecraft:iron_chestplate")
    run("item replace entity $playerName hotbar.1 with minecraft:elytra")
    run("item replace entity $playerName hotbar.2 with minecraft:wind_charge 64")
    awaitClient("the elytra and charges") { it.player!!.inventory.getItem(2).`is`(Items.WIND_CHARGE) }
    // Northwards from the south edge of the pad and on along a flat strip, so that no hill ends the glide
    flatStrip()
    travel(origin.south(13), yaw = 180f)

    val sent = UsePitches()
    enable(ModuleWindGlide)
    awaitServer("items entity $playerName armor.chest minecraft:elytra")
    awaitServer("entity @a[name=$playerName,nbt={FallFlying:1b}]")
    // Kept up: not one tick on the ground, while the charges keep it going. Counted in ticks, since a round trip per
    // tick would stretch the flight out
    repeat(GLIDE_TICKS) { tick ->
        ticks(1)
        check(client { it.player!!.isFallFlying && !it.player!!.onGround() }) { "the glide ended after $tick ticks" }
        if (tick == GLIDE_TICKS / 2) {
            // From behind: the elytra and the charges going off below
            input.pressKey { it.keyTogglePerspective }
            screenshot("WindGlide")
            repeat(2) { input.pressKey { it.keyTogglePerspective } }
        }
    }
    awaitServer("entity @a[name=$playerName,scores={$charges=3..}]")
    // Each charge goes down at a pitch of its own within the range
    sent.close()
    val pitches = sent.pitches.filter { it >= 45f }
    check(pitches.size >= 3 && pitches.all { it in 86.5f..90f }) { "the charges went out at $pitches" }
    check(pitches.distinct().size > 1) { "every charge went out at ${pitches.first()}" }

    run("item replace entity $playerName armor.chest with minecraft:iron_chestplate")
    awaitOff(ModuleWindGlide)
    awaitServer("entity @a[name=$playerName,nbt={OnGround:1b}]", timeout = 400)
})

class PearlHopGameTest : ServerGameTest({
    run("gamerule minecraft:fall_damage false")
    val pearls = objective("pearls", "minecraft.used:minecraft.ender_pearl")
    run("item replace entity $playerName hotbar.3 with minecraft:ender_pearl 16")
    awaitClient("the pearls") { it.player!!.inventory.getItem(3).`is`(Items.ENDER_PEARL) }

    // Legit: one hop, then slot and view are back where they were
    enable(ModulePearlHop)
    awaitServer("entity @a[name=$playerName,scores={$pearls=1}]")
    awaitOff(ModulePearlHop)
    checkHopped()
    check(client { it.player!!.inventory.selectedSlot } == 0) { "the slot stayed on the pearls" }
    awaitServer("entity @a[name=$playerName,nbt={SelectedItemSlot:0}]")
    check(abs(client { it.player!!.xRot }) < 1f) { "the view stayed down" }

    // Silent: neither slot nor view move
    travel(origin)
    configure(ModulePearlHop, "Mode" to "Silent")
    enable(ModulePearlHop)
    repeat(20) {
        check(client { it.player!!.inventory.selectedSlot == 0 && it.player!!.xRot == 0f }) { "the hop showed" }
        ticks(1)
    }
    awaitServer("entity @a[name=$playerName,scores={$pearls=2}]")
    awaitOff(ModulePearlHop)
    checkHopped()

    // Auto: nothing until a player hits us; our own pearl and a mob's hit do not count
    travel(origin)
    val auto = client { ModulePearlHop.containedValues.first { it.name == "Auto" } as ValueGroup }
    configure(auto, "Enabled" to true)
    enable(ModulePearlHop)
    ticks(40)
    awaitServer("entity @a[name=$playerName,scores={$pearls=2}]")
    run("difficulty easy")
    summon("minecraft:zombie", origin.north(3), "{NoAI:1b}")
    awaitClient("the zombie") { it.level!!.entitiesForRendering().any { entity -> entity is Zombie } }
    run("damage $playerName 1 minecraft:mob_attack by @e[type=minecraft:zombie,limit=1]")
    ticks(60)
    awaitServer("entity @a[name=$playerName,scores={$pearls=2}]")
    // A melee hit is told apart by its damage type, which only players deal
    run("damage $playerName 1 minecraft:player_attack by @e[type=minecraft:zombie,limit=1]")
    awaitServer("entity @a[name=$playerName,scores={$pearls=3}]")
    ticks(60)
    awaitServer("entity @a[name=$playerName,scores={$pearls=3}]")

    // Trigger Any: the mob's hit is enough
    configure(auto, "Trigger" to "Any")
    run("damage $playerName 1 minecraft:mob_attack by @e[type=minecraft:zombie,limit=1]")
    awaitServer("entity @a[name=$playerName,scores={$pearls=4}]")
    ticks(60)
    awaitServer("entity @a[name=$playerName,scores={$pearls=4}]")
    disable(ModulePearlHop)
    run("kill @e[type=minecraft:zombie]")
    run("difficulty peaceful")
})

private const val GLIDE_TICKS = 100

/** How far the flat strip of [flatStrip] reaches north of `origin`, enough for a whole glide at a block a tick. */
private const val STRIP_LENGTH = 100
private const val STRIP_HALF_WIDTH = 5
private const val STRIP_CLEARANCE = 24

/** PearlCatch's default Delay. */
private const val CATCH_DELAY = 66

/** Ticks until the server counts [count] on our [objective], asking every tick. */
private fun GameTestScope.ticksUntil(objective: String, count: Int): Int {
    val asked = mutableListOf<Int>()
    repeat(CATCH_DELAY * 3) { tick ->
        asked += run("execute if score $playerName $objective matches $count..")
        ticks(1)
        if (asked.any { (server.result(it) ?: 0) > 0 }) {
            return tick
        }
    }
    throw AssertionError("The server never counted $count $objective, see ${server.log}")
}

/**
 * Counts what the server sees us use, starting at zero so that "none yet" can be asked for. Returns the
 * objective's name, which is unique to the test: the scoreboard is shared by every test on the server.
 */
private fun GameTestScope.objective(name: String, criterion: String): String {
    val objective = unique(name)
    run("scoreboard objectives add $objective $criterion")
    run("scoreboard players set $playerName $objective 0")
    return objective
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

/** Levels a strip north of [TestWorld.SURFACE]'s pad like the pad itself, in slices small enough for one fill each. */
private fun GameTestScope.flatStrip() {
    val from = origin.offset(-STRIP_HALF_WIDTH, 0, -STRIP_LENGTH)
    val to = origin.offset(STRIP_HALF_WIDTH, 0, 0)
    run("forceload add ${from.x} ${from.z} ${to.x} ${to.z}")
    awaitServer("loaded ${from.x} ${from.y} ${from.z}")
    for (z in from.z..to.z step STRIP_SLICE) {
        val sliceFrom = BlockPos(from.x, from.y, z)
        val sliceTo = BlockPos(to.x, to.y, minOf(z + STRIP_SLICE - 1, to.z))
        fill(sliceFrom.below(), sliceTo.below(), "minecraft:grass_block")
        fill(sliceFrom, sliceTo.above(STRIP_CLEARANCE), "minecraft:air")
    }
    awaitServer("block ${from.x} ${from.y + STRIP_CLEARANCE} ${from.z} minecraft:air")
}

private const val STRIP_SLICE = 25
