package net.ccbluex.liquidbounce.extras.modules.pvp

import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.tickUntil
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.extras.util.report
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationsValueGroup
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.features.MovementCorrection
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.ccbluex.liquidbounce.utils.movement.getDegreesRelativeToView
import net.ccbluex.liquidbounce.utils.movement.getDirectionalInputForDegrees
import net.ccbluex.liquidbounce.utils.network.sendSwapItemWithOffhand
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.pow

/**
 * Throws a pearl, then a wind charge at the feet, which launches us into the pearl's path. The pearl hits its
 * own thrower once it left them, so it teleports us where we are, high above the ground.
 */
object ModulePearlCatch : ClientModule("PearlCatch", ExtrasCategories.PVP, disableOnQuit = true) {

    private const val SPAWN_TIMEOUT = 10
    private const val FLIGHT_TIMEOUT = 300
    private const val SWAP_TIMEOUT = 10

    /** Ticks from the throw until the charge launched us into the falling pearl, roughly. */
    private const val CATCH_LEAD = 14
    private const val PEARL_SPEED = 1.5
    private const val PEARL_DRAG = 0.99

    /** How far off the pearl's way down a charge still pushes us under it. */
    private const val WALK_REACH = 0.6
    private const val STANDING = 1.0E-4

    private val mode by enumChoice("Mode", Mode.VIEW)
    private val timing by enumChoice("Timing", Timing.SIMULATE)
    private val delay by int("Delay", 66, 20..100, "ticks")
    private val compensate by boolean("Compensate", true)
    private val walk by boolean("Walk", true)

    private object Fast : ValueGroup("Fast") {
        val pearlPitch by float("PearlPitch", -90f, -90f..90f)
        val chargePitch by float("ChargePitch", FEET_PITCH, -90f..90f)
        val offhand by boolean("Offhand", true)
    }

    init {
        tree(Fast)
    }

    private val rotations = tree(RotationsValueGroup(this, MovementCorrection.CHANGE_LOOK))

    /** Where Simulate walks to, under the pearl's way down. */
    private var walkingTo: Vec3? = null

    /** Our tick count when the pearl left the hand; the client learns of the pearl some ticks later. */
    private var thrownAt = 0

    /** Our tick count when the client first saw the pearl, a round trip after the throw. */
    private var pearlSeenAt = 0

    override fun onDisabled() {
        walkingTo = null
    }

    @Suppress("unused")
    private val walkHandler = handler<MovementInputEvent> { event ->
        val target = walkingTo ?: return@handler
        val degrees = getDegreesRelativeToView(target.subtract(player.position()), RotationManager.movementYaw)
        event.directionalInput = getDirectionalInputForDegrees(DirectionalInput(false, false, false, false), degrees)
    }

    @Suppress("unused")
    private val catchHandler = tickHandler {
        // A throw would cut short whatever the use key is doing: eating, blocking, drawing a bow
        if (!player.onGround() || mc.options.keyUse.isDown) {
            return@tickHandler
        }
        if (hotbarSlotOf(Items.ENDER_PEARL) == null || hotbarSlotOf(Items.WIND_CHARGE) == null) {
            report("missing")
            enabled = false
            return@tickHandler
        }

        val pearl = if (mode == Mode.FAST) throwBoth() else throwPearl(pearlRotation())?.also { launchUnder(it) }
        if (pearl != null) {
            tickUntil { pearl.isRemoved || pearl.tickCount > FLIGHT_TIMEOUT }
        }
        enabled = false
    }

    private fun pearlRotation(): Rotation {
        if (mode == Mode.VIEW) {
            return Rotation(player.yRot, player.xRot)
        }
        val speed = hypot(player.x - player.xo, player.z - player.zo)
        if (!compensate || speed < 1.0E-3) {
            return Rotation(player.yRot, -90f)
        }

        // The pearl takes our speed along but slows down, while we keep running: throw it ahead by the
        // difference, or behind when we will stand still
        val ticks = delay + CATCH_LEAD
        val needed = speed * ticks * (1.0 - PEARL_DRAG) / (1.0 - PEARL_DRAG.pow(ticks))
        val extra = ((needed - speed) / PEARL_SPEED).coerceIn(-1.0, 1.0)
        val heading = Math.toDegrees(atan2(-(player.x - player.xo), player.z - player.zo)).toFloat()
        val tilt = Math.toDegrees(asin(abs(extra))).toFloat()
        return Rotation(if (extra < 0) heading + 180f else heading, -90f + tilt)
    }

    private suspend fun throwPearl(rotation: Rotation): ThrownEnderpearl? {
        val slot = hotbarSlotOf(Items.ENDER_PEARL) ?: return null
        val known = ownPearls().toSet()
        if (!throwFrom(slot) { rotations.toRotationTarget(rotation) }) {
            return null
        }
        thrownAt = player.tickCount
        var pearl: ThrownEnderpearl? = null
        tickUntil {
            pearl = ownPearls().firstOrNull { it !in known }
            pearl != null || it >= SPAWN_TIMEOUT
        }
        pearlSeenAt = player.tickCount
        return pearl
    }

    private suspend fun launchUnder(pearl: ThrownEnderpearl) {
        if (mode == Mode.UP || timing == Timing.FIXED) {
            val down = { rotations.toRotationTarget(Rotation(player.yRot, FEET_PITCH)) }
            // Down first and held there: how long a turn takes is known only roughly, so the throw waits for Delay
            // and not the turn. throwFrom takes another tick before the use goes out, and returned a tick after ours.
            turnTo(down)
            tickUntil {
                RotationManager.setRotationTarget(down(), Priority.IMPORTANT_FOR_USAGE_2, this)
                player.tickCount - thrownAt >= delay - 2
            }
            throwCharge(Rotation(player.yRot, FEET_PITCH))
            return
        }

        // throwFrom returns a tick after the use went out
        val roundTrip = pearlSeenAt - thrownAt + 1
        // The pearl's spread can carry it further than a charge pushes us, so first walk under where it comes
        // down, then plan standing still: each tick the best plan from the pearl's live path gets faced, and
        // the turn is done once its throw is due a tick later
        var intercept: Intercept? = null
        tickUntil { ticks ->
            val spot = PearlIntercept.spot(pearl)
            walkingTo = spot.takeIf { walk && hypot(spot.x - player.x, spot.z - player.z) > WALK_REACH }
            val standing = walkingTo == null && player.deltaMovement.horizontalDistanceSqr() < STANDING
            intercept = PearlIntercept.best(pearl, roundTrip)?.takeIf { standing }?.also { plan ->
                RotationManager.setRotationTarget(
                    rotations.toRotationTarget(plan.rotation), Priority.IMPORTANT_FOR_USAGE_2, this,
                )
            }
            val due = intercept?.let { plan -> plan.delay <= 1 } == true
            due || pearl.isRemoved || ticks > FLIGHT_TIMEOUT
        }
        walkingTo = null
        val plan = intercept ?: return
        throwCharge(plan.rotation)
    }

    @IgnorableReturnValue
    private suspend fun throwCharge(rotation: Rotation): Boolean {
        val slot = hotbarSlotOf(Items.WIND_CHARGE) ?: return false
        return throwFrom(slot) { rotations.toRotationTarget(rotation) }
    }

    /** Pearl and charge back to back at fixed angles, the charge from the offhand if asked to. */
    private suspend fun throwBoth(): ThrownEnderpearl? {
        val swapped = if (Fast.offhand) preload() else null
        val pearl = throwPearl(Rotation(player.yRot, Fast.pearlPitch))
        if (pearl != null) {
            throwCharge(Rotation(player.yRot, Fast.chargePitch))
        }
        swapped?.let { swapOffhand(it) }
        return pearl
    }

    /** Moves the wind charges into the offhand like the swap key would; returns the slot to swap back. */
    private suspend fun preload(): Int? {
        if (player.offhandItem.`is`(Items.WIND_CHARGE)) {
            return null
        }
        val slot = Slots.Hotbar.findSlot(Items.WIND_CHARGE)?.hotbarIndex ?: return null
        swapOffhand(slot)
        return slot.takeIf { player.offhandItem.`is`(Items.WIND_CHARGE) }
    }

    private suspend fun swapOffhand(slot: Int) {
        val offhand = player.offhandItem.copy()
        SilentHotbar.selectSlotSilently(this, slot, SWAP_TIMEOUT)
        // The game sends the selected slot on its next tick, the swap has to follow it
        waitTicks(1)
        network.sendSwapItemWithOffhand()
        tickUntil { !ItemStack.matches(player.offhandItem, offhand) || it >= SWAP_TIMEOUT }
        SilentHotbar.resetSlot(this)
    }

    private fun ownPearls() = world.entitiesForRendering().filterIsInstance<ThrownEnderpearl>()
        .filter { it.owner === player }

    private enum class Mode(override val tag: String) : Tagged {
        UP("Up"),
        VIEW("View"),
        FAST("Fast"),
    }

    private enum class Timing(override val tag: String) : Tagged {
        FIXED("Fixed"),
        SIMULATE("Simulate"),
    }

}
