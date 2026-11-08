package net.ccbluex.liquidbounce.extras.modules.pvp

import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.tickUntil
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationTarget
import net.ccbluex.liquidbounce.utils.aiming.RotationsValueGroup
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.utils.RotationUtil
import net.ccbluex.liquidbounce.utils.aiming.utils.setRotation
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.inventory.useHotbarSlotOrOffhand
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.kotlin.random
import net.minecraft.world.item.Item

/** Ticks a turn may take before the throw is given up. */
internal const val AIM_TIMEOUT = 20

/** Straight down at the feet, where a wind charge launches its thrower the most. */
internal const val FEET_PITCH = 90f

internal fun hotbarSlotOf(item: Item) = Slots.OffhandWithHotbar.findSlot(item)

internal fun isReady(slot: HotbarItemSlot) = !player.cooldowns.isOnCooldown(slot.itemStack)

/** The rotation group's target, let go of the tick after it was last asked for. */
internal fun RotationsValueGroup.briefTarget(rotation: Rotation): RotationTarget {
    val target = toRotationTarget(rotation)
    return RotationTarget(
        target.rotation, null, target.processors, 1, target.resetThreshold, false, target.movementCorrection,
    )
}

/** Keeps turning towards [target] until the rotation the server has is settled on it, at most [AIM_TIMEOUT] ticks. */
@IgnorableReturnValue
context(listener: EventListener)
internal suspend fun turnTo(target: () -> RotationTarget): Boolean {
    // LiquidBounce up to 0.42.0 wraps pitch deltas like yaw: straight up to straight down sticks, so cross the horizon
    if (target().rotation.pitch - sentRotation.pitch > PITCH_WRAP) {
        settle {
            val flip = target()
            RotationTarget(
                Rotation(flip.rotation.yaw, 0f), null, flip.processors, flip.ticksUntilReset, flip.resetThreshold,
                flip.considerInventory, flip.movementCorrection,
            )
        }
    }
    return settle(target)
}

@IgnorableReturnValue
context(listener: EventListener)
private suspend fun settle(target: () -> RotationTarget): Boolean {
    tickUntil {
        RotationManager.setRotationTarget(target(), Priority.IMPORTANT_FOR_USAGE_2, listener)
        settledOn(target().rotation) || it >= AIM_TIMEOUT
    }
    return settledOn(target().rotation)
}

/**
 * Turns to [target] and throws what [slot] holds, carrying the rotation of the last movement packet. The use
 * packet goes out before this tick's movement packet, and Grim wants both to carry the same rotation, so the
 * throw waits until the next turn step would not move anymore and returns only once that packet went out,
 * still facing the same way: a module that turns itself off right away would otherwise drop the rotation
 * before it. False when the turn timed out or the item is cooling down.
 */
@IgnorableReturnValue
context(listener: EventListener)
internal suspend fun throwFrom(slot: HotbarItemSlot, target: () -> RotationTarget): Boolean {
    if (!turnTo(target) || !isReady(slot)) {
        return false
    }
    RotationManager.setRotationTarget(target(), Priority.IMPORTANT_FOR_USAGE_2, listener)
    val rotation = sentRotation
    useHotbarSlotOrOffhand(slot, 1, rotation.yaw, rotation.pitch)
    waitTicks(1)
    return true
}

/**
 * Turns our own view towards [rotation] by at most [speed] degrees a tick, as a mouse would, then waits
 * until the server has it. No rotation of the client's own is involved, the view is what gets sent.
 */
@IgnorableReturnValue
internal suspend fun lookAt(rotation: Rotation, speed: ClosedFloatingPointRange<Float>): Boolean {
    tickUntil {
        val delta = player.rotation.rotationDeltaTo(rotation)
        val step = speed.random()
        val yaw = player.yRot + delta.deltaYaw.coerceIn(-step, step)
        val pitch = player.xRot + delta.deltaPitch.coerceIn(-step, step)
        player.setRotation(Rotation(yaw, pitch))
        settledOn(rotation) || it >= AIM_TIMEOUT
    }
    return settledOn(rotation)
}

private val sentRotation get() = RotationManager.currentRotation ?: player.rotation

// Turn steps snap to the mouse's smallest step, anything below half of it stays where it is
private fun settledOn(rotation: Rotation) =
    RotationManager.serverRotation.isRotationDeltaCloseTo(sentRotation, SAME_ROTATION) &&
        sentRotation.isRotationDeltaCloseTo(rotation, RotationUtil.gcd.toFloat() / 2f)

private const val SAME_ROTATION = 1.0E-3f

/** A pitch turn this large wraps around. */
private const val PITCH_WRAP = 179f
