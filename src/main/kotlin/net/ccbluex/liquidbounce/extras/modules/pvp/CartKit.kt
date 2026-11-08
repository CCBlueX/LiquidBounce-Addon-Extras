package net.ccbluex.liquidbounce.extras.modules.pvp

import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.client.world
import net.ccbluex.liquidbounce.utils.entity.useItem
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.item.getEnchantment
import net.ccbluex.liquidbounce.utils.raytracing.raytraceBlock
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.BlockTags
import net.minecraft.tags.ItemTags
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.CrossbowItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

internal val ItemStack.isRail get() = `is`(ItemTags.RAILS)

internal val ItemStack.isTntCart get() = `is`(Items.TNT_MINECART)

/** A flaming arrow sets a TNT minecart off the moment it hits; plain arrows only break it. */
internal val ItemStack.isFlameBow get() = item is BowItem && getEnchantment(Enchantments.FLAME) > 0

internal val ItemStack.isLoadedCrossbow get() = item is CrossbowItem && CrossbowItem.isCharged(this)

internal fun ItemStack.canShoot() =
    isLoadedCrossbow || item is BowItem && !player.getProjectile(this).isEmpty

/** Uses the launcher in [slot] facing where the server last saw us face. */
internal fun useLauncher(slot: HotbarItemSlot) {
    val rotation = RotationManager.serverRotation
    useItem(slot.useHand, rotation.yRot, rotation.xRot)
}

/** What sets the cart off: a launcher, and flint and steel when its arrows do not burn by themselves. */
internal class Igniter(val launcher: HotbarItemSlot, val flint: HotbarItemSlot?) {

    val burns get() = launcher.itemStack.isFlameBow

    companion object {
        /** A Flame bow first, then a loaded crossbow or a bow shooting through fire. */
        fun find(): Igniter? {
            Slots.OffhandWithHotbar.findSlot { it.isFlameBow && it.canShoot() }?.let { return Igniter(it, null) }
            val flint = Slots.OffhandWithHotbar.findSlot(Items.FLINT_AND_STEEL) ?: return null
            val launcher = Slots.OffhandWithHotbar.findSlot { it.isLoadedCrossbow }
                ?: Slots.OffhandWithHotbar.findSlot { it.item is BowItem && it.canShoot() }
                ?: return null
            return Igniter(launcher, flint)
        }
    }

}

/** Where things go: rails, carts, fire on an arrow's path and the arrow itself. */
internal object CartAim {

    // Arrow speed per tick at full draw and from a crossbow, and its gravity
    private const val BOW_SPEED = 3.0
    private const val CROSSBOW_SPEED = 3.15
    private const val GRAVITY = 0.05
    private const val ARROW_DROP = 0.1
    private const val AIM_RAISE = 0.2
    private const val AIM_TOLERANCE = 1.5f
    // A rail is two pixels high, a cart sits on it
    private const val RAIL_TOP = 0.1
    private const val CART_CENTER = 0.4

    /** Arrows leave a little below the eyes. */
    val arrowOrigin: Vec3 get() = player.eyePosition.subtract(0.0, ARROW_DROP, 0.0)

    /** Where arrows go: the upper half of the cart, so that they clear a block at our feet. */
    fun aimPoint(cart: Entity): Vec3 = cart.boundingBox.center.add(0.0, cart.bbHeight * AIM_RAISE, 0.0)

    fun railPoint(rail: BlockPos) = Vec3(rail.x + 0.5, rail.y + RAIL_TOP, rail.z + 0.5)

    /** Where a TNT minecart sits on the [rail]. */
    fun cartCenter(rail: BlockPos) = Vec3(rail.x + 0.5, rail.y + CART_CENTER, rail.z + 0.5)

    /** Carts on the [rail], and those its neighbours pushed off it. */
    fun carts(rail: BlockPos): List<MinecartTNT> =
        world.getEntitiesOfClass(MinecartTNT::class.java, AABB(rail).inflate(1.0, 0.0, 1.0)) { it.isAlive }

    /** Whether nothing solid stands between where arrows leave and [point]. */
    fun clearShot(point: Vec3) = world.clip(
        ClipContext(arrowOrigin, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player),
    ).type == HitResult.Type.MISS

    /**
     * The air next to the [rail] that an arrow to [point] on the cart flies through last, with a floor to
     * light a fire on.
     */
    fun fireCell(rail: BlockPos, point: Vec3): BlockPos? {
        val origin = arrowOrigin
        return BlockPos.betweenClosed(rail.offset(-1, 0, -1), rail.offset(1, 0, 1))
            .map(BlockPos::immutable)
            .filter { cell ->
                cell != rail && world.getBlockState(cell).let { it.isAir || it.`is`(BlockTags.FIRE) } &&
                    world.getBlockState(cell.below()).isFaceSturdy(world, cell.below(), Direction.UP) &&
                    !AABB(cell).intersects(player.boundingBox) &&
                    AABB(cell).clip(origin, point).isPresent
            }
            .maxByOrNull { it.distToCenterSqr(origin) }
    }

    /** Ticks an arrow from the [launcher] drawn for [charge] ticks needs to reach [point]. */
    fun flightTicks(point: Vec3, launcher: ItemStack, charge: Int): Double {
        val speed = if (launcher.item is CrossbowItem) {
            CROSSBOW_SPEED
        } else {
            BowItem.getPowerForTime(charge) * BOW_SPEED
        }
        return arrowOrigin.distanceTo(point) / speed
    }

    fun shotAt(target: Entity, launcher: ItemStack, charge: Int): Rotation? {
        val point = aimPoint(target)
        if (!clearShot(point)) {
            return null
        }
        // Within reach an arrow is in the air for a tick or two, so its drag does not matter yet
        val ticks = flightTicks(point, launcher, charge)
        return Rotation.lookingAt(point.add(0.0, GRAVITY / 2 * ticks * ticks, 0.0), arrowOrigin)
    }

    fun aimed(rotation: Rotation) = RotationManager.serverRotation.directionAngleTo(rotation) <= AIM_TOLERANCE

    /** Where the server rotation meets the [rail], unless something else is in the way. */
    fun railHit(rail: BlockPos, reach: Double): BlockHitResult? {
        val hit = raytraceBlock(reach, RotationManager.serverRotation, rail, world.getBlockState(rail)) ?: return null
        // The ray above only knows the rail's own shape
        val seen = world.clip(
            ClipContext(player.eyePosition, hit.location, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player),
        )
        return hit.takeIf { seen.type == HitResult.Type.MISS || seen.blockPos == rail }
    }

}
