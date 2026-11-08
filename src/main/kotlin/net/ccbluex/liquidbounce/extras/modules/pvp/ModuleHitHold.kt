package net.ccbluex.liquidbounce.extras.modules.pvp

import kotlinx.coroutines.launch
import net.ccbluex.liquidbounce.event.eventListenerScope
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickUntil
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3

/**
 * Holds a swing at a target that would waste it, because it is still hurt from the last hit or in the air, and lands
 * it through a one-tick rotation the moment that is over.
 */
object ModuleHitHold : ClientModule("HitHold", ExtrasCategories.PVP) {

    private const val GIVE_UP = 20

    // Before the other modules that act on a click at an entity, so a held swing stays one swing
    private const val PRIORITY: Short = 10

    private val hurt by boolean("Hurt", true)
    private val airborne by boolean("Airborne", true)

    /** The target of the swing being held. */
    var held: LivingEntity? = null
        private set

    @Suppress("unused")
    private val clickHandler = handler<AttackClickEvent>(priority = PRIORITY) { event ->
        if (event.isCancelled) {
            return@handler
        }
        if (held != null) {
            event.cancelEvent()
            return@handler
        }
        val hit = event.hitResult as? EntityHitResult ?: return@handler
        val target = hit.entity as? LivingEntity ?: return@handler
        if (!target.shouldBeAttacked() || ready(target)) {
            return@handler
        }
        event.cancelEvent()
        hold(target, hit.location.subtract(target.position()))
    }

    override fun onDisabled() {
        held = null
    }

    private fun hold(target: LivingEntity, offset: Vec3) {
        held = target
        eventListenerScope.launch {
            try {
                tickUntil { !target.isAlive || ready(target) || it >= GIVE_UP }
                if (target.isAlive && ready(target)) {
                    val reach = meleeReach(Float.MAX_VALUE)
                    val point = target.position().add(offset)
                        .takeIf { hits(target, Rotation.lookingAt(it, player.eyePosition), reach) }
                        ?: closestSpot(target, reach)
                    if (point != null) {
                        strike(target, point, reach)
                    }
                }
            } finally {
                held = null
            }
        }
    }

    private fun ready(target: LivingEntity) = (!hurt || target.hurtTime == 0) && (!airborne || target.onGround())

}
