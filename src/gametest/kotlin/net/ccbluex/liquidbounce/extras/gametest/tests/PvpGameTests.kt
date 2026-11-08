package net.ccbluex.liquidbounce.extras.gametest.tests

import com.mojang.blaze3d.platform.InputConstants
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.extras.gametest.harness.GameTestScope
import net.ccbluex.liquidbounce.extras.gametest.harness.PaperGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.awaitAnswer
import net.ccbluex.liquidbounce.extras.gametest.harness.awaitServer
import net.ccbluex.liquidbounce.extras.gametest.harness.chatContains
import net.ccbluex.liquidbounce.extras.gametest.harness.disable
import net.ccbluex.liquidbounce.extras.gametest.harness.enable
import net.ccbluex.liquidbounce.extras.gametest.harness.screenshot
import net.ccbluex.liquidbounce.extras.gametest.harness.setting
import net.ccbluex.liquidbounce.extras.gametest.harness.summon
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleCrossbowLoader
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleHitFlick
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleHitHold
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleShieldSwap
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleSwingRedirect
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.input.InputBind
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.util.Mth
import net.minecraft.world.entity.monster.zombie.Husk
import net.minecraft.world.item.Items
import java.util.Collections
import kotlin.math.abs

class SwingRedirectGameTest : PaperGameTest({
    // Within reach, but a quarter turn away from the crosshair
    standIn(origin.east(2))
    enable(ModuleSwingRedirect)
    ticks(5)
    check(client { ModuleSwingRedirect.aim } == null) { "aimed at a target far off the crosshair" }
    input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT)
    ticks(20)
    awaitServer("entity @e[tag=$STAND_IN,nbt={Health:20.0f}]")

    // Off the crosshair by less than MaxYaw: the swing at the air lands on it
    run("tp @e[tag=$STAND_IN] 1.5 -60 2.5")
    awaitClient("an aim at the stand-in") { ModuleSwingRedirect.aim?.target is Husk }
    screenshot("SwingRedirect")
    input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT)
    awaitServer("entity @e[tag=$STAND_IN,nbt={Health:19.0f}]")
    awaitClient("the rotation back") { abs(Mth.wrapDegrees(RotationManager.serverRotation.yaw)) < 5f }

    // ContinuousTracking: the server faces the stand-in, about 27 degrees to the left, before and between swings
    client { ModuleSwingRedirect.setting<Boolean>("ContinuousTracking").set(true) }
    ticks(3)
    repeat(20) {
        val yaw = client { Mth.wrapDegrees(RotationManager.serverRotation.yaw) }
        check(yaw in -32f..-20f) { "not tracking the stand-in: $yaw" }
        ticks(1)
    }
    awaitServer("entity @e[tag=$STAND_IN,nbt={Health:19.0f}]")
    input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT)
    awaitServer("entity @e[tag=$STAND_IN,nbt={Health:18.0f}]")

    disable(ModuleSwingRedirect)
    check(client { ModuleSwingRedirect.aim } == null) { "the aim survived disabling" }
})

class HitHoldGameTest : PaperGameTest({
    standIn(origin.south(2))
    enable(ModuleHitHold)

    // The second click comes while the stand-in is still hurt, where it would do nothing
    input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT)
    awaitClient("the stand-in hurt") { it.standIn()!!.hurtTime > 0 }
    input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT)
    awaitClient("the swing held") { ModuleHitHold.held != null }
    awaitServer("entity @e[tag=$STAND_IN,nbt={Health:18.0f}]")
    awaitClient("the swing landed") { ModuleHitHold.held == null }

    // In the air for good: held for a second, then given up. A mob without AI never falls, and one summoned in
    // the air never touched the ground, on either side
    run("kill @e[tag=$STAND_IN]")
    awaitClient("the stand-in gone") { it.standIn() == null }
    summon("minecraft:husk", origin.south(2).above(), """{NoAI:1b,Silent:1b,Tags:["$STAND_IN"]}""")
    awaitClient("the stand-in in the air") { it.standIn()?.onGround() == false }
    input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT)
    awaitClient("the swing held") { ModuleHitHold.held != null }
    awaitClient("the swing given up", timeout = 40) { ModuleHitHold.held == null }
    ticks(10)
    awaitServer("entity @e[tag=$STAND_IN,nbt={Health:20.0f}]")

    input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT)
    awaitClient("the swing held") { ModuleHitHold.held != null }
    disable(ModuleHitHold)
    check(client { ModuleHitHold.held } == null) { "the held swing survived disabling" }
    ticks(30)
    awaitServer("entity @e[tag=$STAND_IN,nbt={Health:20.0f}]")
})

class HitFlickGameTest : PaperGameTest({
    standIn(origin.south(2))
    val sent = SentYaws()
    try {
        client {
            ModuleHitFlick.setting<ClosedFloatingPointRange<Float>>("Angle").set(30f..30f)
            ModuleHitFlick.setting<Float>("Jitter").set(5f)
            ModuleHitFlick.setting<Any>("Direction").setByString("Right")
        }
        enable(ModuleHitFlick)
        // Looking south is a yaw of 0; to the right it grows. Jitter spreads flicks of the same angle
        val right = (19 downTo 17).map { hitStandIn(it.toFloat(), sent) }
        check(right.all { it in 24f..36f }) { "not turned right by 30 degrees: $right" }
        check(right.max() - right.min() > 0.3f) { "no jitter: $right" }

        client { ModuleHitFlick.setting<Any>("Direction").setByString("Left") }
        val left = hitStandIn(16f, sent)
        check(left in -36f..-24f) { "not turned left by 30 degrees: $left" }

        // A husk is no player, and standing still is no sprint: plain hits
        client { ModuleHitFlick.setting<Boolean>("PlayersOnly").set(true) }
        val notPlayer = hitStandIn(15f, sent)
        check(abs(notPlayer) < 5f) { "flicked a hit on a husk: $notPlayer" }
        client {
            ModuleHitFlick.setting<Boolean>("PlayersOnly").set(false)
            ModuleHitFlick.setting<Boolean>("RequireSprint").set(true)
        }
        val walking = hitStandIn(14f, sent)
        check(abs(walking) < 5f) { "flicked a hit without sprinting: $walking" }

        disable(ModuleHitFlick)
        check(client { ModuleHitFlick.flicking } == null) { "the flick survived disabling" }
    } finally {
        sent.close()
    }
})

/** Hits the stand-in down to [health] and returns the yaw furthest from south sent meanwhile. */
private fun GameTestScope.hitStandIn(health: Float, sent: SentYaws): Float {
    // Past the hurt time of the last hit
    ticks(15)
    sent.yaws.clear()
    input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT)
    awaitServer("entity @e[tag=$STAND_IN,nbt={Health:${health}f}]")
    awaitClient("the flick over") { ModuleHitFlick.flicking == null }
    return synchronized(sent.yaws) { sent.yaws.map { Mth.wrapDegrees(it) } }.maxByOrNull { abs(it) } ?: 0f
}

class CrossbowLoaderGameTest : PaperGameTest({
    run("item replace entity $playerName hotbar.0 with minecraft:stone_sword")
    run("item replace entity $playerName container.20 with minecraft:crossbow")
    awaitClient("the crossbow") { it.player!!.inventory.getItem(20).`is`(Items.CROSSBOW) }

    // Nothing to load it with
    enable(ModuleCrossbowLoader)
    awaitClient("the complaint") { it.chatContains("nothing to load") && !ModuleCrossbowLoader.enabled }
    awaitServer("items entity $playerName container.20 minecraft:crossbow")

    run("item replace entity $playerName container.21 with minecraft:arrow 8")
    awaitClient("the arrows") { it.player!!.inventory.getItem(21).`is`(Items.ARROW) }
    enable(ModuleCrossbowLoader)
    awaitClient("the loader done", timeout = 200) { !ModuleCrossbowLoader.enabled }
    awaitAnswer("data get entity $playerName Inventory", "charged_projectiles")
    awaitServer("items entity $playerName container.21 minecraft:arrow[count=7]")
    awaitServer("entity @a[name=$playerName,nbt={SelectedItemSlot:0}]")
    check(client { !it.player!!.isUsingItem }) { "still using the crossbow" }

    // KeepInHand: the loaded crossbow stays in hand for the next click, then the sword comes back
    client { ModuleCrossbowLoader.setting<Boolean>("KeepInHand").set(true) }
    run("item replace entity $playerName hotbar.2 with minecraft:crossbow")
    awaitClient("the second crossbow") { it.player!!.inventory.getItem(2).`is`(Items.CROSSBOW) }
    enable(ModuleCrossbowLoader)
    awaitServer("items entity $playerName container.21 minecraft:arrow[count=6]", timeout = 200)
    awaitServer("entity @a[name=$playerName,nbt={SelectedItemSlot:2}]")
    awaitClient("the loaded crossbow in hand") {
        it.player!!.inventory.selectedSlot == 2 && !it.player!!.isUsingItem && ModuleCrossbowLoader.enabled
    }
    input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT)
    awaitServer("entity @e[type=minecraft:arrow]")
    awaitServer("entity @a[name=$playerName,nbt={SelectedItemSlot:0}]")
    awaitClient("the loader done") { !ModuleCrossbowLoader.enabled }

    // Put away by hand: the other slot stays
    enable(ModuleCrossbowLoader)
    awaitServer("items entity $playerName container.21 minecraft:arrow[count=5]", timeout = 200)
    awaitClient("the loaded crossbow in hand") {
        it.player!!.inventory.selectedSlot == 2 && !it.player!!.isUsingItem && ModuleCrossbowLoader.enabled
    }
    input.pressKey { it.keyHotbarSlots[3] }
    awaitClient("the loader done") { !ModuleCrossbowLoader.enabled }
    awaitServer("entity @a[name=$playerName,nbt={SelectedItemSlot:3}]")
    check(client { it.player!!.inventory.selectedSlot == 3 }) { "the slot changed back after putting it away" }
})

class ShieldSwapGameTest : PaperGameTest({
    standIn(origin.south(2))
    run("item replace entity $playerName hotbar.0 with minecraft:stone_sword")
    run("item replace entity $playerName container.20 with minecraft:shield")
    awaitClient("the shield") { it.player!!.inventory.getItem(20).`is`(Items.SHIELD) }
    val key = InputConstants.KEY_V
    client { ModuleShieldSwap.bindValue.set(InputBind(InputConstants.Type.KEYBOARD, key, InputBind.BindAction.TOGGLE)) }

    input.pressKey(key)
    awaitClient("the shield up") { it.player!!.isBlocking }
    awaitServer("entity @a[name=$playerName,nbt={SelectedItemSlot:1}]")
    hitPlayer()
    awaitServer("items entity $playerName hotbar.1 minecraft:shield[minecraft:damage~{damage:{min:1}}]")
    awaitServer("entity @a[name=$playerName,nbt={Health:20.0f}]")

    input.pressKey(key)
    awaitClient("the shield down") { !it.player!!.isUsingItem && !ModuleShieldSwap.enabled }
    check(client { ModuleShieldSwap.shield } == null) { "the shield slot survived disabling" }
    awaitServer("entity @a[name=$playerName,nbt={SelectedItemSlot:0}]")
    hitPlayer()
    awaitServer("entity @a[name=$playerName,nbt=!{Health:20.0f}]")

    client { ModuleShieldSwap.bindValue.set(InputBind(InputConstants.Type.KEYBOARD, key, InputBind.BindAction.HOLD)) }
    input.holdKey(key)
    awaitClient("the shield up while held") { it.player!!.isBlocking }
    input.releaseKey(key)
    awaitClient("the shield down on release") { !it.player!!.isUsingItem && !ModuleShieldSwap.enabled }
    awaitServer("entity @a[name=$playerName,nbt={SelectedItemSlot:0}]")
})

private const val STAND_IN = "standin"

/**
 * A still, unarmored husk as the enemy, since the server cannot provide a second player. Hostile mobs need a
 * difficulty above peaceful; a husk does not burn in daylight. A fist takes exactly one point off it.
 */
private fun GameTestScope.standIn(pos: BlockPos) {
    run("difficulty easy")
    summon("minecraft:husk", pos, """{NoAI:1b,PersistenceRequired:1b,Silent:1b,OnGround:1b,Tags:["$STAND_IN"]}""")
    run("attribute @e[tag=$STAND_IN,limit=1] minecraft:armor base set 0")
    awaitClient("the stand-in") { it.standIn() != null }
    // A mob without AI never moves, so the client learns that it stands on the ground from the periodic sync
    awaitClient("the stand-in on the ground", timeout = 100) { it.standIn()!!.onGround() }
}

private fun Minecraft.standIn() = level!!.entitiesForRendering().firstOrNull { it is Husk } as Husk?

/** A blockable hit from the stand-in, which the player faces; easy difficulty makes 4 of it. */
private fun GameTestScope.hitPlayer() =
    run("damage $playerName 6 minecraft:mob_attack by @e[tag=$STAND_IN,limit=1]")

/** Yaws of the movement packets sent while it is open. */
private class SentYaws : EventListener, AutoCloseable {

    val yaws: MutableList<Float> = Collections.synchronizedList(mutableListOf())

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        val yaw = (event.packet as? ServerboundMovePlayerPacket)?.getYRot(Float.NaN) ?: return@handler
        if (!yaw.isNaN()) {
            yaws += yaw
        }
    }

    override fun close() = EventManager.unregisterEventHandler(this)

}
