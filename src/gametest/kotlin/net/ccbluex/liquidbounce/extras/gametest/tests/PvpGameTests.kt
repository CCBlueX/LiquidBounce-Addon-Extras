package net.ccbluex.liquidbounce.extras.gametest.tests

import com.mojang.blaze3d.platform.InputConstants
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.gametest.harness.GameTestScope
import net.ccbluex.gametest.harness.ServerGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.SURFACE
import net.ccbluex.gametest.harness.TestWorld
import net.ccbluex.gametest.harness.action.awaitAnswer
import net.ccbluex.gametest.harness.action.awaitServer
import net.ccbluex.gametest.harness.action.chatContains
import net.ccbluex.liquidbounce.gametest.harness.configure
import net.ccbluex.liquidbounce.gametest.harness.disable
import net.ccbluex.liquidbounce.gametest.harness.enable
import net.ccbluex.liquidbounce.extras.gametest.harness.screenshot
import net.ccbluex.liquidbounce.gametest.harness.setting
import net.ccbluex.gametest.harness.action.summon
import net.ccbluex.gametest.harness.action.teleport
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleCrossbowLoader
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleHitFlick
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleHitHold
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleShieldGuard
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleShieldSwap
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleSwingRedirect
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.combat.attackEntity
import net.ccbluex.liquidbounce.utils.input.InputBind
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.util.Mth
import net.minecraft.world.entity.monster.zombie.Husk
import net.minecraft.world.item.Items
import java.util.Collections
import kotlin.math.abs

class SwingRedirectGameTest : ServerGameTest(TestWorld.SURFACE, {
    // Facing north. Within reach, but a quarter turn away from the crosshair
    input.lookAt(NORTH, 0f)
    standIn(origin.west(2))
    enable(ModuleSwingRedirect)
    ticks(5)
    check(client { ModuleSwingRedirect.aim } == null) { "aimed at a target far off the crosshair" }
    input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT)
    ticks(20)
    awaitServer("entity @e[tag=$STAND_IN,nbt={Health:20.0f}]")

    // Off the crosshair by less than MaxYaw: the swing at the air lands on it. Looking a little down keeps
    // the whole stand-in in view, and the swing still meets nothing but air
    input.lookAt(NORTH, 10f)
    run("tp @e[tag=$STAND_IN] ${origin.x - 0.5} ${origin.y} ${origin.z - 1.5}")
    awaitClient("an aim at the stand-in") { ModuleSwingRedirect.aim?.target is Husk }
    // The hit shows for half a second, so the Enabled toast has to be gone before it
    ticks(100)
    input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT)
    ticks(3)
    screenshot("SwingRedirect")
    awaitServer("entity @e[tag=$STAND_IN,nbt={Health:19.0f}]")
    awaitClient("the rotation back") { abs(Mth.wrapDegrees(RotationManager.serverRotation.yaw - NORTH)) < 5f }

    // ContinuousTracking: the server faces the stand-in, about 27 degrees to the left, before and between swings
    configure(ModuleSwingRedirect, "ContinuousTracking" to true)
    ticks(3)
    repeat(20) {
        val yaw = client { Mth.wrapDegrees(RotationManager.serverRotation.yaw - NORTH) }
        check(yaw in -32f..-20f) { "not tracking the stand-in: $yaw" }
        ticks(1)
    }
    awaitServer("entity @e[tag=$STAND_IN,nbt={Health:19.0f}]")
    input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT)
    awaitServer("entity @e[tag=$STAND_IN,nbt={Health:18.0f}]")

    disable(ModuleSwingRedirect)
    check(client { ModuleSwingRedirect.aim } == null) { "the aim survived disabling" }
})

class HitHoldGameTest : ServerGameTest({
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

class HitFlickGameTest : ServerGameTest({
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

        configure(ModuleHitFlick, "Direction" to "Left")
        val left = hitStandIn(16f, sent)
        check(left in -36f..-24f) { "not turned left by 30 degrees: $left" }

        // A husk is no player, and standing still is no sprint: plain hits
        configure(ModuleHitFlick, "PlayersOnly" to true)
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

class CrossbowLoaderGameTest : ServerGameTest({
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
    // The whole inventory is too long for the answer, which cuts it short
    awaitAnswer("data get entity $playerName Inventory[{id:\"minecraft:crossbow\"}].components", "charged_projectiles")
    awaitServer("items entity $playerName container.21 minecraft:arrow[count=7]")
    awaitServer("entity @a[name=$playerName,nbt={SelectedItemSlot:0}]")
    check(client { !it.player!!.isUsingItem }) { "still using the crossbow" }

    // KeepInHand: the loaded crossbow stays in hand for the next click, then the sword comes back
    configure(ModuleCrossbowLoader, "KeepInHand" to true)
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

class ShieldSwapGameTest : ServerGameTest({
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

class ShieldGuardGameTest : ServerGameTest({
    run("item replace entity $playerName hotbar.0 with minecraft:stone_sword")
    run("item replace entity $playerName hotbar.1 with minecraft:shield")
    input.pressKey { it.keyHotbarSlots[2] }
    // Watched, but too far to swing, and standing still
    standIn(origin.south(7))
    awaitClient("the empty hand") {
        it.player!!.inventory.selectedSlot == 2 && it.player!!.inventory.getItem(1).`is`(Items.SHIELD)
    }
    enable(ModuleShieldGuard)
    ticks(20)
    check(client { ModuleShieldGuard.threat == null && !it.player!!.isUsingItem }) { "blocked against nothing" }

    // Running at us, still out of reach
    var rushed = false
    // The client smooths the steps over a few ticks
    repeat(10) { step ->
        if (step < 6) {
            run("execute as @e[tag=$STAND_IN] at @s run tp @s ~ ~ ~-0.4")
        }
        ticks(1)
        rushed = rushed || client { ModuleShieldGuard.threat != null }
    }
    check(rushed) { "a rush went unnoticed" }
    awaitClient("the rush over") { ModuleShieldGuard.threat == null && !it.player!!.isUsingItem }

    // In reach and able to swing: the hotbar shield goes up and takes the hit
    run("tp @e[tag=$STAND_IN] 0.5 -60 2.5")
    awaitClient("the shield up") { it.player!!.isBlocking }
    awaitServer("entity @a[name=$playerName,nbt={SelectedItemSlot:1}]")
    hitPlayer()
    awaitServer("items entity $playerName hotbar.1 minecraft:shield[minecraft:damage~{damage:{min:1}}]")
    awaitServer("entity @a[name=$playerName,nbt={Health:20.0f}]")

    // An axe disables the shield; the sword is in hand until it is back
    run("item replace entity @e[tag=$STAND_IN,limit=1] weapon.mainhand with minecraft:iron_axe")
    hitPlayer()
    awaitClient("the shield disabled") { it.player!!.cooldowns.isOnCooldown(it.player!!.inventory.getItem(1)) }
    awaitServer("entity @a[name=$playerName,nbt={SelectedItemSlot:0}]")
    awaitServer("entity @a[name=$playerName,nbt={SelectedItemSlot:1}]", timeout = 300)
    awaitClient("the shield up again") { it.player!!.isBlocking }

    // LowerForAxes: the shield stays down rather than get disabled again
    configure(ModuleShieldGuard, "LowerForAxes" to true)
    awaitClient("the shield down against the axe") { ModuleShieldGuard.threat != null && !it.player!!.isUsingItem }
    ticks(10)
    check(client { !it.player!!.isUsingItem }) { "blocked against an axe" }
    configure(ModuleShieldGuard, "LowerForAxes" to false)

    // WeaponsOnly: an empty hand is no threat, a sword is
    configure(ModuleShieldGuard, "WeaponsOnly" to true)
    run("item replace entity @e[tag=$STAND_IN,limit=1] weapon.mainhand with minecraft:air")
    awaitClient("the empty hand ignored") { ModuleShieldGuard.threat == null && !it.player!!.isUsingItem }
    run("item replace entity @e[tag=$STAND_IN,limit=1] weapon.mainhand with minecraft:stone_sword")
    awaitClient("the shield up against the sword") { it.player!!.isBlocking }

    // HoldAttacks: behind the raised shield no hit goes out
    check(client { !attackEntity(it.standIn()!!, SwingMode.DO_NOT_HIDE) }) { "attacked while blocking" }
    ticks(5)
    awaitServer("entity @e[tag=$STAND_IN,nbt={Health:20.0f}]")
    configure(ModuleShieldGuard, "WeaponsOnly" to false)
    run("item replace entity @e[tag=$STAND_IN,limit=1] weapon.mainhand with minecraft:air")

    // With the shield in the offhand nothing switches, so a jump onto the stand-in can end in a critical hit
    run("item replace entity $playerName hotbar.1 with minecraft:air")
    run("item replace entity $playerName weapon.offhand with minecraft:shield")
    input.lookAt(0f, 25f)
    awaitClient("the offhand shield up") { it.player!!.isBlocking && it.player!!.offhandItem.`is`(Items.SHIELD) }
    input.holdKey { it.keyJump }
    ticks(2)
    input.releaseKey { it.keyJump }
    awaitClient("the shield down while falling", timeout = 20) {
        val player = it.player!!
        !player.onGround() && player.deltaMovement.y < 0 && !player.isUsingItem
    }
    input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT)
    awaitServer("entity @e[tag=$STAND_IN,nbt={Health:18.5f}]")

    // Rising: with nothing else to block, the way up of a jump still gets the shield
    client {
        ModuleShieldGuard.setting<Boolean>("Cooldown").set(false)
        ModuleShieldGuard.setting<Boolean>("Rush").set(false)
        ModuleShieldGuard.setting<Boolean>("Rising").set(true)
    }
    awaitClient("the landing") { it.player!!.onGround() && !it.player!!.isUsingItem }
    ticks(5)
    check(client { ModuleShieldGuard.threat == null && !it.player!!.isUsingItem }) { "blocked on the ground" }
    input.holdKeyFor({ it.keyJump }, 2)
    awaitClient("the shield up on the way up", timeout = 10) {
        it.player!!.isUsingItem && it.player!!.deltaMovement.y > 0
    }
    awaitClient("the shield down on the way down", timeout = 20) {
        val player = it.player!!
        !player.onGround() && player.deltaMovement.y < 0 && !player.isUsingItem
    }

    // Gliding: an enemy in reach and able to swing, but the shield stays down
    client {
        ModuleShieldGuard.setting<Boolean>("Cooldown").set(true)
        ModuleShieldGuard.setting<Boolean>("Crits").set(false)
    }
    awaitClient("the landing") { it.player!!.onGround() }
    run("item replace entity $playerName armor.chest with minecraft:elytra")
    teleport(origin.above(4), pitch = 25f)
    awaitClient("the shield up in the air", timeout = 12) { !it.player!!.onGround() && it.player!!.isUsingItem }
    input.holdKeyFor({ it.keyJump }, 2)
    awaitClient("the shield down while gliding", timeout = 10) {
        it.player!!.isFallFlying && ModuleShieldGuard.threat != null && !it.player!!.isUsingItem
    }
    awaitClient("the landing") { it.player!!.onGround() }

    disable(ModuleShieldGuard)
    check(client { ModuleShieldGuard.threat } == null) { "the threat survived disabling" }
    awaitClient("the shield down") { !it.player!!.isUsingItem }
})

private const val STAND_IN = "standin"
private const val NORTH = 180f

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
