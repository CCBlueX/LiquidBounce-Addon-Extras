package net.ccbluex.liquidbounce.extras.modules.pvp

import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.client.world
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.jvm.optionals.getOrNull
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** A wind charge throw [delay] ticks from now that puts us into the pearl's path [height] blocks up. */
internal class Intercept(val delay: Int, val rotation: Rotation, val miss: Double, val height: Double)

/**
 * Plays the next ticks forward: the pearl as it flies, a wind charge thrown at some tick and angle, its burst,
 * and our launch from it. Explosions push from their center towards the eyes, with less force the further
 * the feet are; a pearl hits its owner too once it left them.
 */
internal object PearlIntercept {

    private const val MAX_DELAY = 40
    private const val MAX_PEARL_TICKS = 200
    private const val MAX_CHARGE_TICKS = 8
    private val PITCHES = floatArrayOf(90f, 85f, 80f, 75f, 70f, 65f, 60f)

    private const val PEARL_GRAVITY = 0.03
    private const val PEARL_DRAG = 0.99
    private const val CHARGE_SPEED = 1.5
    private const val BURST_RADIUS = 2.4
    private const val BURST_KNOCKBACK = 1.22
    private const val BURST_OFFSET = 0.25
    private const val GRAVITY = 0.08
    private const val VERTICAL_DRAG = 0.98
    private const val AIR_DRAG = 0.91

    /** Projectiles hit a box grown by this once they flew a few ticks. */
    private const val HIT_MARGIN = 0.3

    /** The player's half width plus [HIT_MARGIN]. */
    private const val HIT_REACH = 0.6
    private const val SAFE_MISS = 0.4

    /** Ticks either side of the estimated lead that a plan has to hold for, for jitter and a client behind. */
    private const val LEAD_SLACK = 3

    private const val MIN_HEIGHT = 1.5
    private const val AIM_HEIGHT = 6.0

    /** [roundTrip] is how many of our ticks passed between sending our pearl and seeing it. */
    fun best(pearl: ThrownEnderpearl, roundTrip: Int): Intercept? {
        val path = pearlPath(pearl)
        val drift = Vec3(player.x - player.xo, 0.0, player.z - player.zo)
        val leads = pearlLeads(roundTrip)
        var best: Intercept? = null
        val yaw = pushYaw(path, drift)
        for (delay in 0..MAX_DELAY) {
            for (pitch in PITCHES) {
                val intercept = simulate(delay, Rotation(yaw, pitch), path, drift, leads) ?: continue
                if (intercept.miss <= SAFE_MISS && intercept.height >= MIN_HEIGHT &&
                    (best == null || intercept.score < best.score)) {
                    best = intercept
                }
            }
        }
        return best
    }

    private val Intercept.score get() = miss - height * 0.05

    /**
     * How many ticks the server's pearl is ahead of ours once our movement reaches it: we see the pearl half a
     * round trip late, the burst that launches us reaches us a round trip after our throw, and our movement takes
     * another half to arrive. The server's player list reports no latency in the first seconds after joining, so
     * the round trip of our own pearl is the measure.
     */
    private fun pearlLeads(roundTrip: Int) = 2 * roundTrip - LEAD_SLACK..2 * roundTrip + LEAD_SLACK

    private fun pearlPath(pearl: ThrownEnderpearl): List<Vec3> {
        val path = mutableListOf(pearl.position())
        var velocity = pearl.deltaMovement
        repeat(MAX_PEARL_TICKS) {
            velocity = velocity.subtract(0.0, PEARL_GRAVITY, 0.0).scale(PEARL_DRAG)
            val from = path.last()
            val to = from.add(velocity)
            val hit = world.clip(ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, pearl))
            if (hit.type != HitResult.Type.MISS) {
                path += hit.location
                return path
            }
            path += to
        }
        return path
    }

    /** Where the pearl passes the height we get launched to on its way down. */
    fun spot(pearl: ThrownEnderpearl): Vec3 {
        val path = pearlPath(pearl)
        return path[catchTick(path)]
    }

    private fun catchTick(path: List<Vec3>): Int {
        val apex = path.indices.maxBy { path[it].y }
        val ground = player.y
        return (apex until path.size).firstOrNull { path[it].y <= ground + AIM_HEIGHT } ?: (path.size - 1)
    }

    /** Throwing away from where the pearl comes down pushes us under it. */
    private fun pushYaw(path: List<Vec3>, drift: Vec3): Float {
        val tick = catchTick(path)
        val feet = player.position().add(drift.scale(tick.toDouble()))
        val offsetX = path[tick].x - feet.x
        val offsetZ = path[tick].z - feet.z
        return Math.toDegrees(atan2(offsetX, -offsetZ)).toFloat()
    }

    private fun simulate(delay: Int, rotation: Rotation, path: List<Vec3>, drift: Vec3, leads: IntRange): Intercept? {
        val feet = launch(delay, rotation, path.size, drift) ?: return null
        var miss = 0.0
        var height = Double.MAX_VALUE
        for (lead in leads) {
            val (tick, entry) = entry(feet, path, lead) ?: return null
            miss = max(miss, hypot(entry.x - feet[tick].x, entry.z - feet[tick].z))
            height = min(height, feet[tick].y - player.y)
        }
        return Intercept(delay, rotation, miss, height)
    }

    /** Our feet each tick from now, when a charge thrown in [delay] ticks launches us, until we land again. */
    private fun launch(delay: Int, rotation: Rotation, ticks: Int, drift: Vec3): List<Vec3>? {
        val thrownFrom = player.position().add(drift.scale(delay.toDouble()))
        val (center, burst) = burst(thrownFrom, rotation, drift) ?: return null
        val tick = delay + burst
        val feet = MutableList(min(tick + 1, ticks)) { player.position().add(drift.scale(it.toDouble())) }
        if (feet.size < ticks) {
            var position = feet.last()
            val distance = position.distanceTo(center) / BURST_RADIUS
            if (distance > 1.0) {
                return null
            }
            val knockback = position.add(0.0, player.eyeHeight.toDouble(), 0.0).subtract(center).normalize()
                .scale((1.0 - distance) * BURST_KNOCKBACK)
            // The push adds to what we already move by, and the tick it arrives we still stand, so the ground's
            // friction slows us sideways once before the air does
            var velocity = player.deltaMovement.add(knockback)
            var friction = world.getBlockState(player.onPos).block.friction * AIR_DRAG
            while (feet.size < ticks) {
                position = position.add(velocity)
                velocity = Vec3(velocity.x * friction, (velocity.y - GRAVITY) * VERTICAL_DRAG, velocity.z * friction)
                friction = AIR_DRAG
                if (position.y < player.y) {
                    break
                }
                feet += position
            }
        }
        return feet
    }

    /**
     * The tick and point the pearl, [lead] ticks ahead of ours, enters our hitbox. A projectile only hits a box
     * its step enters from outside: rising into the pearl swallows it, and it flies on out of our feet.
     */
    private fun entry(feet: List<Vec3>, path: List<Vec3>, lead: Int): Pair<Int, Vec3>? {
        for ((tick, at) in feet.withIndex()) {
            if (tick + lead < 0) {
                continue
            }
            val from = path.getOrNull(tick + lead) ?: return null
            val to = path.getOrNull(tick + lead + 1) ?: return null
            val box = AABB(
                at.x - HIT_REACH, at.y - HIT_MARGIN, at.z - HIT_REACH,
                at.x + HIT_REACH, at.y + player.bbHeight + HIT_MARGIN, at.z + HIT_REACH,
            )
            if (box.contains(from)) {
                return null
            }
            box.clip(from, to).getOrNull()?.let { return tick to it }
        }
        return null
    }

    /** Where a charge thrown from [feet] bursts, and how many ticks after the throw. */
    private fun burst(feet: Vec3, rotation: Rotation, drift: Vec3): Pair<Vec3, Int>? {
        val yaw = Math.toRadians(rotation.yaw.toDouble())
        val pitch = Math.toRadians(rotation.pitch.toDouble())
        val velocity = Vec3(-sin(yaw) * cos(pitch), -sin(pitch), cos(yaw) * cos(pitch))
            .scale(CHARGE_SPEED).add(drift)
        var position = Vec3(feet.x, feet.y + player.eyeHeight, feet.z)
        for (tick in 1..MAX_CHARGE_TICKS) {
            val next = position.add(velocity)
            val hit = world.clip(clip(position, next))
            if (hit.type != HitResult.Type.MISS) {
                val normal = Vec3.atLowerCornerOf(hit.direction.unitVec3i)
                return hit.location.add(normal.scale(BURST_OFFSET)) to tick
            }
            position = next
        }
        return null
    }

    private fun clip(from: Vec3, to: Vec3) =
        ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)

}
