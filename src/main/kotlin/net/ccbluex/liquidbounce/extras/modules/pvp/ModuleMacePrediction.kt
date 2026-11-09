package net.ccbluex.liquidbounce.extras.modules.pvp

import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.render.drawBox
import net.ccbluex.liquidbounce.render.drawCircleOutline
import net.ccbluex.liquidbounce.render.drawLineStrip
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.engine.type.Vec3f
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.render.withPositionRelativeToCamera
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/**
 * Shows where the nearest enemy in the air comes down: its fall arc, or its track over the ground, a marker
 * where it lands and a ring of your reach around that spot. An enemy walking on the ground gets the path it
 * keeps to at its current pace, up to the next edge.
 */
object ModuleMacePrediction : ClientModule("MacePrediction", ExtrasCategories.PVP) {

    // Vanilla air physics, see LivingEntity.travelInAir
    private const val GRAVITY = 0.08
    private const val VERTICAL_DRAG = 0.98
    private const val HORIZONTAL_DRAG = 0.91

    private const val MAX_TICKS = 200
    private const val MARKER_SIZE = 0.25
    private const val MARKER_FILL = 80
    private const val RING_LIFT = 0.02
    private const val GROUND_CHECK = 0.05

    // Blocks per tick below which a grounded enemy counts as standing
    private const val MIN_WALK_SPEED = 0.01

    private val range by float("Range", 32f, 4f..128f, "blocks")
    private val path by enumChoice("Path", PathMode.ARC)
    private val walking by boolean("Walking", true)
    private val walkTicks by int("WalkTicks", 20, 1..100, "ticks")
    private val marker by enumChoice("Marker", MarkerStyle.BOTH)
    private val pathColor by color("PathColor", Color4b(255, 170, 40))
    private val ringColor by color("RingColor", Color4b(255, 60, 60, 160))

    enum class PathMode(override val tag: String) : Tagged {
        ARC("Arc"),
        GROUND("Ground"),
    }

    enum class MarkerStyle(override val tag: String) : Tagged {
        BOX("Box"),
        OUTLINE("Outline"),
        BOTH("Both"),
    }

    /** The enemy whose fall or walk is predicted. */
    var target: LivingEntity? = null
        private set

    /** Whether [prediction] is a walk over the ground rather than a fall. */
    var walks = false
        private set

    /** Where [target] passes tick by tick, the landing point or the end of the walk last. */
    val prediction: List<Vec3>
        field = ArrayList<Vec3>()

    @Suppress("unused")
    private val predictHandler = handler<GameTickEvent> {
        prediction.clear()
        walks = false
        target = world.getEntitiesOfClass(LivingEntity::class.java, player.boundingBox.inflate(range.toDouble())) {
            it !== player && it.isAlive && !it.isFallFlying && !it.isPassenger &&
                (isAirborne(it) || walking && isWalking(it)) && it.shouldBeAttacked()
        }.minByOrNull { it.distanceToSqr(player) }
        val enemy = target ?: return@handler
        if (isAirborne(enemy)) {
            predictFall(enemy)
        } else {
            predictWalk(enemy)
        }
    }

    // A mob standing still sends no movement, so its on-ground flag never reaches the client
    private fun isAirborne(entity: LivingEntity) =
        world.noCollision(entity, entity.boundingBox.move(0.0, -GROUND_CHECK, 0.0))

    private fun isWalking(entity: LivingEntity) = horizontalStep(entity).length() > MIN_WALK_SPEED

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        val end = prediction.lastOrNull() ?: return@handler
        val points = if (path == PathMode.GROUND && !walks) prediction.map { Vec3(it.x, end.y, it.z) } else prediction
        val markerBox = AABB(end, end).inflate(MARKER_SIZE)
        event.renderEnvironment {
            withPositionRelativeToCamera {
                drawLineStrip(pathColor.argb, *points.map(::toVec3f).toTypedArray())
                drawBox(
                    markerBox,
                    pathColor.alpha(MARKER_FILL).takeIf { marker != MarkerStyle.OUTLINE },
                    pathColor.takeIf { marker != MarkerStyle.BOX },
                )
            }
            withPositionRelativeToCamera(end.add(0.0, RING_LIFT, 0.0)) {
                drawCircleOutline(player.entityInteractionRange().toFloat(), ringColor)
            }
        }
    }

    override fun onDisabled() {
        prediction.clear()
        target = null
        walks = false
    }

    private fun predictFall(entity: LivingEntity) {
        // What moved it last tick; remote entities have no motion of their own on the client
        var velocity = Vec3(entity.x - entity.xo, entity.y - entity.yo, entity.z - entity.zo)
        var position = entity.position()
        prediction += position
        repeat(MAX_TICKS) {
            val next = position.add(velocity)
            val hit = world.clip(ClipContext(position, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, entity))
            if (hit.type != HitResult.Type.MISS) {
                prediction += hit.location
                return
            }
            position = next
            prediction += position
            velocity = Vec3(
                velocity.x * HORIZONTAL_DRAG,
                (velocity.y - GRAVITY) * VERTICAL_DRAG,
                velocity.z * HORIZONTAL_DRAG,
            )
        }
    }

    private fun predictWalk(entity: LivingEntity) {
        walks = true
        val step = horizontalStep(entity)
        var box = entity.boundingBox
        prediction += entity.position()
        repeat(walkTicks) {
            val next = box.move(step)
            // A wall stops the walk, and so does an edge: nobody keeps walking into a drop
            if (!world.noCollision(entity, next) || world.noCollision(entity, next.move(0.0, -GROUND_CHECK, 0.0))) {
                return
            }
            box = next
            prediction += prediction.last().add(step)
        }
    }

    private fun horizontalStep(entity: LivingEntity) = Vec3(entity.x - entity.xo, 0.0, entity.z - entity.zo)

    private fun toVec3f(pos: Vec3) = Vec3f(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat())

}
