package net.ccbluex.liquidbounce.extras.modules.basehunting

import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.extras.util.drawMarkers
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Vec3i

/** Passages players dig: two-high tunnels, staircases and one-wide shafts. */
object ModuleTunnelTrailESP : ClientModule("TunnelTrailESP", ExtrasCategories.BASE_HUNTING) {

    private val range by int("Range", 16, 4..32, "blocks")
    private val minimumLength by int("MinimumLength", 5, 3..16, "blocks")
    private val color by color("Color", Color4b(255, 170, 40, 85))

    val findings: Set<BlockPos>
        field = HashSet<BlockPos>()

    @Suppress("unused")
    private val scanHandler = tickHandler {
        findings.clear()
        scan()
        waitTicks(20)
    }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event -> event.drawMarkers(findings, color) }

    override fun onDisabled() = findings.clear()

    private fun scan() {
        val origin = player.blockPosition()
        val halfHeight = range / 2
        for (pos in BlockPos.betweenClosed(
            origin.offset(-range, -halfHeight, -range),
            origin.offset(range, halfHeight, range),
        )) {
            for (direction in Direction.Plane.HORIZONTAL) {
                collectRun(pos, direction.unitVec3i) { isTunnel(it, direction.axis) }
                collectRun(pos, direction.unitVec3i.above()) { isStairStep(it, direction.axis) }
            }
            collectRun(pos, Direction.DOWN.unitVec3i, ::isShaft)
        }
    }

    private fun collectRun(start: BlockPos, step: Vec3i, isCell: (BlockPos) -> Boolean) {
        if (!isCell(start) || isCell(start.subtract(step))) {
            return
        }
        val run = generateSequence(start.immutable()) { it.offset(step) }.takeWhile(isCell).take(range * 4).toList()
        if (run.size >= minimumLength) {
            findings += run
        }
    }

    private fun isTunnel(pos: BlockPos, axis: Direction.Axis) = canWalkIn(pos) && isEnclosedAcross(pos, axis)

    private fun isStairStep(pos: BlockPos, axis: Direction.Axis) = canWalkOn(pos.below()) &&
        canWalkThrough(pos) && canWalkThrough(pos.above()) && canWalkThrough(pos.above(2)) &&
        isEnclosedAcross(pos, axis)

    private fun isShaft(pos: BlockPos) =
        canWalkThrough(pos) && Direction.Plane.HORIZONTAL.none { canWalkThrough(pos.relative(it)) }

    private fun isEnclosedAcross(pos: BlockPos, axis: Direction.Axis) = Direction.Plane.HORIZONTAL
        .filter { it.axis != axis }
        .none { canWalkThrough(pos.relative(it)) || canWalkThrough(pos.above().relative(it)) }

    private fun canWalkIn(pos: BlockPos) = canWalkOn(pos.below()) &&
        canWalkThrough(pos) && canWalkThrough(pos.above()) && !canWalkThrough(pos.above(2))

    private fun canWalkOn(pos: BlockPos): Boolean {
        val state = world.getBlockState(pos)
        return !state.isAir && state.fluidState.isEmpty && !state.getCollisionShape(world, pos).isEmpty
    }

    private fun canWalkThrough(pos: BlockPos): Boolean {
        val state = world.getBlockState(pos)
        return state.isAir || state.fluidState.isEmpty && state.getCollisionShape(world, pos).isEmpty
    }

}
