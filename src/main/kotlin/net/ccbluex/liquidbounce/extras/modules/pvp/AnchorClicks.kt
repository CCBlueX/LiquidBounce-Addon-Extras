package net.ccbluex.liquidbounce.extras.modules.pvp

import net.ccbluex.liquidbounce.additions.realSelectedSlot
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.tickConditional
import net.ccbluex.liquidbounce.utils.aiming.PostRotationExecutor
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationsValueGroup
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.block.doPlacement
import net.ccbluex.liquidbounce.utils.block.isInteractable
import net.ccbluex.liquidbounce.utils.client.RestrictedSingleUseAction
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.client.world
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.raytracing.traceFromPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

private const val CLICK_TIMEOUT = 20

// How far from a face's centre the other points to click lie, as a share of the face, so a partly hidden face
// still offers a click
private const val FACE_INSET = 0.3

/** A face of [block] to click at [point]. */
class Click(val block: BlockPos, val face: Direction, val point: Vec3, private val range: Double) {

    val rotation: Rotation get() = Rotation.lookingAt(point, player.eyePosition)

    /** The hit [rotation] gives from where the player stands, if it is this click. */
    fun hit(rotation: Rotation = RotationManager.serverRotation): BlockHitResult? =
        traceFromPlayer(rotation, range).takeIf {
            it.type == HitResult.Type.BLOCK && it.blockPos == block && it.direction == face
        }

}

/**
 * A click that puts a block at [pos]: on what stands there when the game replaces it, like the fire a blast
 * leaves behind, or against a neighbour that does nothing when used.
 */
fun placeClick(pos: BlockPos, range: Double): Click? = Direction.entries.asSequence()
    .mapNotNull { side ->
        val neighbour = pos.relative(side)
        val state = world.getBlockState(neighbour)
        if (state.canBeReplaced() || state.isInteractable) null else clickOn(neighbour, listOf(side.opposite), range)
    }
    .plus(listOfNotNull(inPlaceClick(pos, range)))
    .minByOrNull { it.point.distanceToSqr(player.eyePosition) }

private fun inPlaceClick(pos: BlockPos, range: Double) =
    if (world.getBlockState(pos).canBeReplaced()) clickOn(pos, range = range) else null

/** Where a block placed with [hit] goes: into what it hit when the game replaces that, else in front of the face. */
fun placedAt(hit: BlockHitResult): BlockPos =
    if (world.getBlockState(hit.blockPos).canBeReplaced()) hit.blockPos else hit.blockPos.relative(hit.direction)

/** A click on one of [faces] of [block] that nothing in between blocks. */
fun clickOn(block: BlockPos, faces: Collection<Direction> = Direction.entries, range: Double): Click? {
    val eyes = player.eyePosition
    val center = Vec3.atCenterOf(block)
    return faces.sortedBy { center.relative(it, 0.5).distanceToSqr(eyes) }
        .firstNotNullOfOrNull { face ->
            facePoints(block, face).filter { it.distanceTo(eyes) <= range }
                .map { Click(block, face, it, range) }
                .firstOrNull { it.hit(it.rotation) != null }
        }
}

/** Points on [face] of what the crosshair can hit of [block], none for a block it passes through. */
private fun facePoints(block: BlockPos, face: Direction): Sequence<Vec3> {
    val shape = world.getBlockState(block).getShape(world, block)
    if (shape.isEmpty) {
        return emptySequence()
    }
    val box = shape.bounds().move(block)
    val side = if (face.axisDirection == Direction.AxisDirection.POSITIVE) box.max(face.axis) else box.min(face.axis)
    val center = box.center.with(face.axis, side)
    val (first, second) = Direction.entries
        .filter { it.axis != face.axis && it.axisDirection == Direction.AxisDirection.POSITIVE }
    return sequenceOf(0.0 to 0.0, -1.0 to -1.0, 1.0 to -1.0, -1.0 to 1.0, 1.0 to 1.0).map { (a, b) ->
        center.relative(first, a * FACE_INSET * box.size(first.axis))
            .relative(second, b * FACE_INSET * box.size(second.axis))
    }
}

private fun AABB.size(axis: Direction.Axis) = max(axis) - min(axis)

/**
 * Turns towards [find]'s click through the rotation manager and runs [action] once the server has a rotation
 * that hits it. Gives up when there is no click left or after [timeout] ticks.
 */
suspend fun EventListener.clickAimed(
    rotations: RotationsValueGroup,
    timeout: Int = CLICK_TIMEOUT,
    find: () -> Click?,
    action: (BlockHitResult) -> Unit,
): Boolean {
    var done = false
    tickConditional(timeout) {
        if (done) {
            return@tickConditional true
        }
        val click = find() ?: return@tickConditional true
        RotationManager.setRotationTarget(
            click.rotation,
            valueGroup = rotations,
            priority = Priority.IMPORTANT_FOR_USAGE_2,
            provider = this,
            whenReached = RestrictedSingleUseAction({ click.hit() != null }) {
                PostRotationExecutor.addTask(this, postMove = false, priority = true) {
                    click.hit()?.let {
                        action(it)
                        done = true
                    }
                }
            },
        )
        false
    }
    return done
}

/** Uses [slot] for [action]: picked like a number key, or only for the server, and back right after. */
inline fun EventListener.withSlot(slot: HotbarItemSlot, visible: Boolean, action: () -> Unit) {
    val index = slot.hotbarIndex ?: return action()
    val inventory = player.inventory
    val previous = inventory.realSelectedSlot
    if (visible) {
        inventory.selectedSlot = index
    } else {
        SilentHotbar.selectSlotSilently(this, slot, 1)
    }
    try {
        action()
    } finally {
        if (visible) {
            inventory.selectedSlot = previous
        } else {
            SilentHotbar.resetSlot(this)
        }
    }
}

/** Right-clicks [hit] with [slot] as vanilla would, with the rotation the server has. */
fun EventListener.use(hit: BlockHitResult, slot: HotbarItemSlot, visible: Boolean = false) =
    withSlot(slot, visible) {
        doPlacement(hit, RotationManager.serverRotation, slot.useHand)
    }
