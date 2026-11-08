package net.ccbluex.liquidbounce.extras.modules.pvp

import net.ccbluex.liquidbounce.event.tickUntil
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationTarget
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.utils.raytraceBox
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.combat.attackEntity
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.raytracing.isLookingAtEntity
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3

// The movement packet of the tick after the request carries the rotation
private const val STRIKE_TIMEOUT = 3

/** Reach for melee, never more than the server allows. */
internal fun meleeReach(range: Float) = minOf(range.toDouble(), player.entityInteractionRange())

/** Whether [rotation] from the eyes hits [target] within [reach], with nothing in between. */
internal fun hits(target: Entity, rotation: Rotation, reach: Double) =
    isLookingAtEntity(toEntity = target, rotation = rotation, range = reach, throughWallsRange = 0.0) != null

/** The spot of [target] in [reach] closest to where the player looks, or `null` when none is visible. */
internal fun closestSpot(target: Entity, reach: Double): Vec3? =
    raytraceBox(player.eyePosition, target.boundingBox, reach, 0.0)?.vec

/**
 * Hits [target] at [point] through a rotation the server has for exactly one tick, then lets it snap back. The hit
 * waits for a movement packet that faced the target, which is what Grim checks it against.
 */
internal suspend fun ClientModule.strike(target: Entity, point: Vec3, reach: Double) {
    val rotation = Rotation.lookingAt(point, player.eyePosition)
    RotationManager.setRotationTarget(RotationTarget(rotation, target), Priority.IMPORTANT_FOR_USAGE_2, this)
    tickUntil { facing(target, reach) || it >= STRIKE_TIMEOUT }
    if (target.isAlive && facing(target, reach)) {
        attackEntity(target, SwingMode.DO_NOT_HIDE)
    }
}

private fun facing(target: Entity, reach: Double) = hits(target, RotationManager.serverRotation, reach)
