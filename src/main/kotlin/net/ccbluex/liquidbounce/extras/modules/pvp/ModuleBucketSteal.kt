package net.ccbluex.liquidbounce.extras.modules.pvp

import net.ccbluex.liquidbounce.event.tickConditional
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.aiming.NoRotationMode
import net.ccbluex.liquidbounce.utils.aiming.NormalRotationMode
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.utils.raytraceBlockSide
import net.ccbluex.liquidbounce.utils.aiming.utils.raytraceBox
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.block.doPlacement
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.combat.findEnemy
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.entity.useItem
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.raytracing.traceFromPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.FluidTags
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.Items
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import kotlin.math.min

/**
 * Takes water and lava away from your enemy: scoops source blocks next to them with an empty bucket, or
 * plugs them with a cobweb when there is no bucket. With rotations off, only what your crosshair is on, with
 * OnWeb only sources next to a cobweb.
 */
object ModuleBucketSteal : ClientModule("BucketSteal", ExtrasCategories.PVP) {

    private const val AIM_TIMEOUT = 10

    private val water by boolean("Water", true)
    private val lava by boolean("Lava", true)
    private val radius by float("Radius", 2f, 0f..4f, "blocks")
    private val enemyRange by float("EnemyRange", 8f, 1f..16f, "blocks")
    private val range by float("Range", 4.5f, 1f..6f, "blocks")
    private val cobwebs by boolean("Cobwebs", true)
    private val onWeb by boolean("OnWeb", false)
    private val delay by intRange("Delay", 4..4, 0..20, "ticks")
    private val slotResetDelay by int("SlotResetDelay", 2, 0..20, "ticks")
    private val swingMode by enumChoice("Swing", SwingMode.DO_NOT_HIDE)
    private val rotationMode = modes(this, "RotationMode") {
        arrayOf(NormalRotationMode(it, this), NoRotationMode(it, this))
    }

    private val reach get() = min(range.toDouble(), player.blockInteractionRange())

    @Suppress("unused")
    private val stealHandler = tickHandler {
        if (mc.gui.screen() != null || player.isUsingItem) {
            return@tickHandler
        }
        val bucket = Slots.OffhandWithHotbar.findSlot(Items.BUCKET)
        val web = if (cobwebs) Slots.OffhandWithHotbar.findSlot(Items.COBWEB) else null
        if (bucket == null && web == null) {
            return@tickHandler
        }
        val enemy = world.findEnemy(0f..enemyRange) ?: return@tickHandler
        val source = pickSource(enemy) ?: return@tickHandler

        val done = when {
            bucket != null -> scoop(source, bucket)
            web != null -> plug(source, web)
            else -> false
        }
        if (done) {
            waitTicks(delay.random())
        }
    }

    private fun pickSource(enemy: Entity): BlockPos? {
        if (rotationMode.activeMode is NoRotationMode) {
            return crosshairSource()?.takeIf { it.distSqr(enemy.blockPosition()) <= radius * radius }
        }
        val eyes = player.eyePosition
        val near = enemy.blockPosition()
        val span = radius.toInt()
        return BlockPos.betweenClosed(near.offset(-span, -span, -span), near.offset(span, span, span))
            .asSequence()
            .filter {
                isWanted(it) && it.distSqr(near) <= radius * radius && Vec3.atCenterOf(it).distanceTo(eyes) <= reach + 1
            }
            .map(BlockPos::immutable)
            .minByOrNull { it.distSqr(near) }
    }

    private fun isWanted(pos: BlockPos): Boolean {
        val fluid = world.getFluidState(pos)
        return fluid.isSource && (water && fluid.`is`(FluidTags.WATER) || lava && fluid.`is`(FluidTags.LAVA)) &&
            (!onWeb || Direction.entries.any { world.getBlockState(pos.relative(it)).`is`(Blocks.COBWEB) })
    }

    private fun crosshairSource() =
        traceFromPlayer(player.rotation, reach, fluid = ClipContext.Fluid.SOURCE_ONLY)
            .takeIf { it.type == HitResult.Type.BLOCK && isWanted(it.blockPos) }?.blockPos

    // The server traces the bucket from the rotation in the packet, for source blocks only
    private fun scoopable(source: BlockPos) =
        traceFromPlayer(RotationManager.serverRotation, reach, fluid = ClipContext.Fluid.SOURCE_ONLY)
            .let { it.type == HitResult.Type.BLOCK && it.blockPos == source }

    private suspend fun scoop(source: BlockPos, bucket: HotbarItemSlot) = aimThen(
        rotation = {
            val box = AABB(source).setMaxY(source.y + world.getFluidState(source).getHeight(world, source).toDouble())
            raytraceBox(player.eyePosition, box, reach, 0.0)?.rotation
        },
        aimed = { isWanted(source) && scoopable(source) },
    ) {
        select(bucket)
        val rotation = RotationManager.serverRotation
        useItem(bucket.useHand, rotation.yRot, rotation.xRot, swingMode)
    }

    private suspend fun plug(source: BlockPos, web: HotbarItemSlot) = aimThen(
        rotation = { supportRotation(source) },
        aimed = { pluggable(source) != null },
    ) {
        val hit = pluggable(source) ?: return@aimThen
        select(web)
        doPlacement(hit, RotationManager.serverRotation, web.useHand, swingMode = swingMode)
    }

    // Down first: the floor under a pool is the face least likely to be covered
    private fun supportRotation(source: BlockPos): Rotation? {
        val eyes = player.eyePosition
        val context = CollisionContext.of(player)
        return Direction.entries.sortedBy { it != Direction.DOWN }.firstNotNullOfOrNull { side ->
            val support = source.relative(side)
            if (world.getBlockState(support).canBeReplaced()) {
                null
            } else {
                raytraceBlockSide(side.opposite, support, eyes, reach * reach, 0.0, context)?.rotation
            }
        }
    }

    // Fluids are replaceable, so a click on the block behind the source puts the web into it
    private fun pluggable(source: BlockPos): BlockHitResult? =
        traceFromPlayer(RotationManager.serverRotation, reach).takeIf {
            it.type == HitResult.Type.BLOCK && it.blockPos.relative(it.direction) == source && isWanted(source)
        }

    private fun select(slot: HotbarItemSlot) {
        if (!slot.isOffHand) {
            SilentHotbar.selectSlotSilently(this, slot, slotResetDelay + 1)
        }
    }

    /** Aims with [rotation] and runs [action] once the server has a rotation that passes [aimed]. */
    private suspend fun aimThen(rotation: () -> Rotation?, aimed: () -> Boolean, action: () -> Unit): Boolean {
        var done = false
        tickConditional(AIM_TIMEOUT) {
            val target = if (rotationMode.activeMode is NoRotationMode) {
                player.rotation
            } else {
                rotation() ?: return@tickConditional true
            }
            rotationMode.activeMode.rotate(target, isFinished = { aimed() }) {
                if (!done && aimed()) {
                    action()
                    done = true
                }
            }
            done
        }
        return done
    }

    override fun onDisabled() = SilentHotbar.resetSlot(this)

}
