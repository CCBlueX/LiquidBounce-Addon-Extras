package net.ccbluex.liquidbounce.extras.modules.pvp

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.eventListenerScope
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.RotationUpdateEvent
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.render.drawBox
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.render.withPositionRelativeToCamera
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationTarget
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.combat.getEntitiesBoxInRange
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.entity.squaredBoxedDistanceTo
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

/**
 * Turns a swing at the air onto the closest target in reach that is close to the crosshair. The server sees one
 * tick of rotation towards a body height of the target, then the hit; with ContinuousTracking it faces that spot
 * the whole time, so the swing needs no turn at all.
 */
object ModuleSwingRedirect : ClientModule("SwingRedirect", ExtrasCategories.PVP) {

    private const val DOT_SIZE = 0.12

    private val range by float("Range", 3f, 1f..6f, "blocks")
    private val maxYaw by float("MaxYaw", 30f, 1f..180f, "°")
    private val maxPitch by float("MaxPitch", 20f, 1f..90f, "°")
    private val heights by multiEnumChoice(
        "Heights", BodyHeight.HEAD, BodyHeight.NECK, BodyHeight.CHEST, BodyHeight.WAIST, canBeNone = false,
    )
    private val blockClicks by boolean("BlockClicks", false)
    private val dot by boolean("Dot", true)
    private val dotColor by color("DotColor", Color4b(255, 64, 64, 220))
    private val continuousTracking by boolean("ContinuousTracking", false)

    /** Where a missed swing would go right now. */
    var aim: Aim? = null
        private set

    private var striking: Job? = null

    @Suppress("unused")
    private val aimHandler = handler<GameTickEvent> {
        aim = findAim()
    }

    @Suppress("unused")
    private val trackHandler = handler<RotationUpdateEvent> {
        val aim = aim?.takeIf { continuousTracking } ?: return@handler
        RotationManager.setRotationTarget(
            RotationTarget(Rotation.lookingAt(aim.point, player.eyePosition), aim.target),
            // Below KillAura and the strike itself
            Priority.IMPORTANT_FOR_USAGE_1,
            this,
        )
    }

    @Suppress("unused")
    private val clickHandler = handler<AttackClickEvent> { event ->
        val type = event.hitResult.type
        if (event.isCancelled || type == HitResult.Type.ENTITY || type == HitResult.Type.BLOCK && !blockClicks) {
            return@handler
        }
        if (striking?.isActive == true) {
            event.cancelEvent()
            return@handler
        }
        val aim = findAim() ?: return@handler
        event.cancelEvent()
        striking = eventListenerScope.launch { strike(aim.target, aim.point, meleeReach(range)) }
    }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        val aim = aim?.takeIf { dot } ?: return@handler
        val point = aim.target.getPosition(event.partialTicks).add(aim.offset)
        event.renderEnvironment {
            withPositionRelativeToCamera {
                drawBox(AABB.ofSize(point, DOT_SIZE, DOT_SIZE, DOT_SIZE), dotColor, dotColor.alpha(255))
            }
        }
    }

    override fun onDisabled() {
        aim = null
        striking = null
    }

    private fun findAim(): Aim? {
        val reach = meleeReach(range)
        val eyes = player.eyePosition
        return world.getEntitiesBoxInRange(eyes, reach) { it.shouldBeAttacked() }
            .sortedBy { it.squaredBoxedDistanceTo(eyes) }
            .firstNotNullOfOrNull { target ->
                spotOn(target, reach)?.let { Aim(target, it.subtract(target.position())) }
            }
    }

    /** The selected body height closest to the crosshair that can be hit from here. */
    private fun spotOn(target: Entity, reach: Double): Vec3? {
        val look = player.rotation
        val eyes = player.eyePosition
        val box = target.boundingBox
        return heights.asSequence()
            .map { Vec3(target.x, box.minY + box.ysize * it.fraction, target.z) }
            .map { it to Rotation.lookingAt(it, eyes) }
            .filter { (_, rotation) ->
                val delta = look.rotationDeltaTo(rotation)
                abs(delta.deltaYaw) <= maxYaw && abs(delta.deltaPitch) <= maxPitch && hits(target, rotation, reach)
            }
            .minByOrNull { (_, rotation) -> look.rotationDeltaLengthSqrTo(rotation) }
            ?.first
    }

    class Aim(val target: Entity, val offset: Vec3) {
        val point: Vec3 get() = target.position().add(offset)
    }

    private enum class BodyHeight(override val tag: String, val fraction: Double) : Tagged {
        HEAD("Head", 0.9),
        NECK("Neck", 0.8),
        CHEST("Chest", 0.65),
        WAIST("Waist", 0.5),
        THIGHS("Thighs", 0.35),
        LEGS("Legs", 0.15),
    }

}
