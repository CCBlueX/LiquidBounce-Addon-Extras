package net.ccbluex.liquidbounce.extras.modules.qol

import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.render.drawLines
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.engine.type.Vec3f
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.render.withPositionRelativeToCamera
import net.minecraft.core.BlockPos
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.ScaffoldingBlock
import net.minecraft.world.level.block.SlabBlock
import net.minecraft.world.level.block.SnowLayerBlock
import net.minecraft.world.level.block.StairBlock
import net.minecraft.world.level.block.TransparentBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.Half
import net.minecraft.world.level.block.state.properties.SlabType

/** Crosses where hostile mobs can spawn: red right now, yellow once the sky goes dark. */
object ModuleLightOverlay : ClientModule("LightOverlay", ExtrasCategories.QOL) {

    private val horizontalRange by int("HorizontalRange", 8, 1..32, "blocks")
    private val verticalRange by int("VerticalRange", 4, 1..16, "blocks")
    private val lightLevel by int("LightLevel", 0, 0..15)
    private val color by color("Color", Color4b(225, 25, 25))
    private val potentialColor by color("PotentialColor", Color4b(225, 225, 25))

    enum class Spawn { POTENTIAL, ALWAYS }

    val spots: Map<BlockPos, Spawn>
        field = HashMap<BlockPos, Spawn>()

    private var crosses = emptyMap<Spawn, Array<Vec3f>>()

    @Suppress("unused")
    private val scanHandler = tickHandler {
        scan()
        waitTicks(5)
    }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        event.renderEnvironment {
            withPositionRelativeToCamera {
                crosses[Spawn.ALWAYS]?.let { drawLines(color.argb, *it) }
                crosses[Spawn.POTENTIAL]?.let { drawLines(potentialColor.argb, *it) }
            }
        }
    }

    override fun onDisabled() {
        spots.clear()
        crosses = emptyMap()
    }

    private fun scan() {
        val origin = player.blockPosition()
        val reach = BlockPos(horizontalRange, verticalRange, horizontalRange)
        spots.clear()
        for (pos in BlockPos.betweenClosed(origin.subtract(reach), origin.offset(reach))) {
            spawnAt(pos)?.let { spots[pos.immutable()] = it }
        }
        crosses = spots.entries.groupBy({ it.value }, { it.key })
            .mapValues { (_, positions) -> positions.flatMap(::cross).toTypedArray() }
    }

    private fun cross(pos: BlockPos): List<Vec3f> {
        val x = pos.x.toFloat()
        val y = pos.y + 0.01f
        val z = pos.z.toFloat()
        return listOf(Vec3f(x, y, z), Vec3f(x + 1, y, z + 1), Vec3f(x + 1, y, z), Vec3f(x, y, z + 1))
    }

    // Derived from Meteor Client's BlockUtils.isValidMobSpawn and isValidSpawnBlock, see README Credits
    private fun spawnAt(pos: BlockPos): Spawn? {
        val state = world.getBlockState(pos)
        val isThinSnow = state.block is SnowLayerBlock && state.getValue(SnowLayerBlock.LAYERS) == 1
        if (!state.isAir && !isThinSnow || !isSpawnFloor(world.getBlockState(pos.below()))) {
            return null
        }

        return when {
            world.getBrightness(LightLayer.BLOCK, pos) > lightLevel -> null
            world.getBrightness(LightLayer.SKY, pos) > lightLevel -> Spawn.POTENTIAL
            else -> Spawn.ALWAYS
        }
    }

    private fun isSpawnFloor(state: BlockState) = when (state.block) {
        Blocks.BEDROCK, Blocks.BARRIER, is TransparentBlock, is ScaffoldingBlock -> false
        Blocks.SOUL_SAND, Blocks.MUD -> true
        is SlabBlock if state.getValue(SlabBlock.TYPE) == SlabType.TOP -> true
        is StairBlock if state.getValue(StairBlock.HALF) == Half.TOP -> true
        else -> state.isSolidRender
    }

}
