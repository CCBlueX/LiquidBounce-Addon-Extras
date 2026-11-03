package net.ccbluex.liquidbounce.extras.modules.pvp

import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.aiming.NoRotationMode
import net.ccbluex.liquidbounce.utils.aiming.NormalRotationMode
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.entity.getDamageFromExplosion
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.render.trajectory.TrajectoryInfo
import net.ccbluex.liquidbounce.utils.render.trajectory.TrajectoryInfoRenderer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.BlockTags
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil

/**
 * Meets an enemy's ender pearl with a TNT minecart: lays it next to where the pearl comes down and shoots it
 * just as the thrower arrives.
 */
object ModulePearlCart : ClientModule("PearlCart", ExtrasCategories.PVP) {

    private const val MAX_FLIGHT = 200

    // MinecartTNT.explode for a cart standing still
    private const val POWER = 4f

    private val range by float("Range", 6f, 2f..10f, "blocks")
    private val carts by int("Carts", 1, 1..4)
    private val charge by int("Charge", 10, 3..20, "ticks")
    private val delay by int("Delay", 2, 0..10, "ticks")
    private val minEnemyDamage by float("MinEnemyDamage", 0f, 0f..40f, "HP")
    private val maxSelfDamage by float("MaxSelfDamage", 40f, 0f..40f, "HP")
    private val rotationMode = modes(this, "RotationMode") {
        arrayOf(NormalRotationMode(it, this), NoRotationMode(it, this))
    }

    private val rig = CartRig(this, rotationMode)

    /** Pearls already met, or given up on. */
    val pearls: Set<Int>
        field = HashSet<Int>()

    @Suppress("unused")
    private val punishHandler = tickHandler {
        pearls.retainAll { world.getEntity(it) != null }
        val pearl = world.entitiesForRendering().firstOrNull {
            // The first tick of a pearl still has the spawn packet's motion, not its own
            it is ThrownEnderpearl && it.tickCount >= 2 && it.id !in pearls &&
                it.owner.let { owner -> owner != null && owner !== player && owner.shouldBeAttacked() }
        } as ThrownEnderpearl? ?: return@tickHandler
        pearls += pearl.id

        val landing = landing(pearl)?.first ?: return@tickHandler
        if (landing.distToCenterSqr(player.position()) > range * range) {
            return@tickHandler
        }
        val igniter = Igniter.find() ?: return@tickHandler
        val rail = railSpot(landing, pearl.owner as? LivingEntity) ?: return@tickHandler
        val cart = rig.setUp(rail, carts) ?: return@tickHandler
        rig.ignite(cart, igniter, charge) { arrived(pearl, cart, igniter) }
    }

    override fun onDisabled() {
        pearls.clear()
        rig.reset()
    }

    /** Whether an arrow let go now reaches the [cart] [delay] ticks after the pearl is down. */
    private fun arrived(pearl: ThrownEnderpearl, cart: MinecartTNT, igniter: Igniter): Boolean {
        val remaining = if (pearl.isAlive) landing(pearl)?.second ?: 0 else 0
        val launcher = igniter.launcher.itemStack
        val draw = maxOf(charge, player.ticksUsingItem)
        // The release goes out with the next tick
        val flight = ceil(CartAim.flightTicks(CartAim.aimPoint(cart), launcher, draw)).toInt() + 1
        return remaining + delay <= flight
    }

    /** Where the pearl lands, as the block it lands in, and in how many ticks. */
    private fun landing(pearl: ThrownEnderpearl): Pair<BlockPos, Int>? {
        val simulation = TrajectoryInfoRenderer(
            pearl.owner ?: player, pearl.owner, ItemStack(Items.ENDER_PEARL), pearl.deltaMovement, pearl.position(),
            TrajectoryInfo.GENERIC, PEARL_TRAJECTORY, TrajectoryInfoRenderer.Type.REAL, Vec3.ZERO,
        ).runSimulation(MAX_FLIGHT)
        val hit = simulation.hitResult ?: return null
        if (hit.type == HitResult.Type.MISS) {
            return null
        }
        val pos = if (hit is BlockHitResult) hit.blockPos.relative(hit.direction) else BlockPos.containing(hit.location)
        return pos to simulation.positions.size
    }

    private fun worthIt(rail: BlockPos, landing: BlockPos, thrower: LivingEntity?): Boolean {
        val blast = CartAim.cartCenter(rail)
        if (player.getDamageFromExplosion(blast, POWER) > maxSelfDamage) {
            return false
        }
        if (minEnemyDamage <= 0f) {
            return true
        }
        thrower ?: return false
        // The helper measures from where the thrower stands now, so the blast moves along with them
        val arrival = Vec3.atBottomCenterOf(landing)
        return thrower.getDamageFromExplosion(blast.add(thrower.position().subtract(arrival)), POWER) >= minEnemyDamage
    }

    /** Next to the [landing], on the side the arrow comes from, so that the thrower is not in its way. */
    private fun railSpot(landing: BlockPos, thrower: LivingEntity?): BlockPos? {
        val rail = Slots.OffhandWithHotbar.findSlot { it.isRail }
        return BlockPos.betweenClosed(landing.offset(-1, 0, -1), landing.offset(1, 0, 1))
            .map(BlockPos::immutable)
            .filter { cell ->
                cell != landing &&
                    world.getBlockState(cell.below()).isFaceSturdy(world, cell.below(), Direction.UP) &&
                    CartAim.clearShot(CartAim.cartCenter(cell)) &&
                    (world.getBlockState(cell).`is`(BlockTags.RAILS) || rail != null && rig.canPlace(cell, rail)) &&
                    worthIt(cell, landing, thrower)
            }
            .minByOrNull { it.distToCenterSqr(player.eyePosition) }
    }

}
