package net.ccbluex.liquidbounce.extras.modules.pvp

import kotlinx.coroutines.launch
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.eventListenerScope
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationTarget
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3
import kotlin.random.Random

/**
 * Turns the server-side rotation sideways by a random angle for one tick before each hit, then back onto the
 * target, and hits.
 */
object ModuleHitFlick : ClientModule("HitFlick", ExtrasCategories.PVP) {

    // The flick goes out with the next movement packet, the tick after that is ours again
    private const val FLICK_TICKS = 2

    private val angle by floatRange("Angle", 30f..60f, 5f..150f, "°")
    private val direction by enumChoice("Direction", Direction.RANDOM)
    private val jitter by float("Jitter", 1.5f, 0f..5f, "°")
    private val requireSprint by boolean("RequireSprint", false)
    private val playersOnly by boolean("PlayersOnly", false)

    /** The target of the hit being flicked. */
    var flicking: Entity? = null
        private set

    @Suppress("unused")
    private val clickHandler = handler<AttackClickEvent> { event ->
        if (event.isCancelled) {
            return@handler
        }
        if (flicking != null) {
            event.cancelEvent()
            return@handler
        }
        val hit = event.hitResult as? EntityHitResult ?: return@handler
        if (requireSprint && !player.isSprinting) {
            return@handler
        }
        val target = hit.entity.takeIf { it.shouldBeAttacked() && (!playersOnly || it is Player) } ?: return@handler
        event.cancelEvent()
        flick(target, hit.location.subtract(target.position()))
    }

    override fun onDisabled() {
        flicking = null
    }

    private fun flick(target: Entity, offset: Vec3) {
        flicking = target
        val turn = angle.start + Random.nextFloat() * (angle.endInclusive - angle.start)
        val away = Rotation(player.yRot + direction.side() * turn + jitter(), pitch(player.xRot + jitter()))
        RotationManager.setRotationTarget(RotationTarget(away), Priority.IMPORTANT_FOR_USAGE_2, this)
        eventListenerScope.launch {
            try {
                waitTicks(FLICK_TICKS)
                val reach = meleeReach(Float.MAX_VALUE)
                val point = target.position().add(offset)
                    .takeIf { hits(target, Rotation.lookingAt(it, player.eyePosition), reach) }
                    ?: closestSpot(target, reach)
                if (target.isAlive && point != null) {
                    strike(target, jittered(target, point, reach) ?: point, reach)
                }
            } finally {
                flicking = null
            }
        }
    }

    /** Where a turn back off from [point] by up to [jitter] lands on [target], if it still hits it. */
    private fun jittered(target: Entity, point: Vec3, reach: Double): Vec3? {
        if (jitter <= 0f) {
            return null
        }
        val eyes = player.eyePosition
        val straight = Rotation.lookingAt(point, eyes)
        val turned = Rotation(straight.yaw + jitter(), pitch(straight.pitch + jitter()))
        return target.boundingBox.clip(eyes, eyes.add(turned.directionVector.scale(reach))).orElse(null)
            ?.takeIf { hits(target, Rotation.lookingAt(it, eyes), reach) }
    }

    private fun jitter() = if (jitter > 0f) Random.nextDouble(-jitter.toDouble(), jitter.toDouble()).toFloat() else 0f

    private fun pitch(pitch: Float) = pitch.coerceIn(-90f, 90f)

    private enum class Direction(override val tag: String) : Tagged {
        LEFT("Left"),
        RIGHT("Right"),
        RANDOM("Random");

        // Yaw grows clockwise seen from above, so to the right
        fun side() = when (this) {
            LEFT -> -1
            RIGHT -> 1
            RANDOM -> if (Random.nextBoolean()) 1 else -1
        }
    }

}
