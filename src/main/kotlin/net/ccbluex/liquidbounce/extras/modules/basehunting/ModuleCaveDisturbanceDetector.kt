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
import net.minecraft.core.SectionPos
import net.minecraft.world.level.block.Blocks

/**
 * Caves generate as cave air, so a lone block of plain air in a cave wall is where someone mined. The idea
 * comes from Trouser-Streak's CaveDisturbanceDetector, see README Credits.
 */
object ModuleCaveDisturbanceDetector : ClientModule("CaveDisturbanceDetector", ExtrasCategories.BASE_HUNTING) {

    private val range by int("Range", 16, 4..48, "blocks")
    private val color by color("Color", Color4b(200, 50, 255, 85))

    val findings: Set<BlockPos>
        field = HashSet<BlockPos>()

    @Suppress("unused")
    private val scanHandler = tickHandler {
        findings.clear()
        scanNearbySections()
        waitTicks(20)
    }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event -> event.drawMarkers(findings, color) }

    override fun onDisabled() = findings.clear()

    private fun scanNearbySections() {
        val origin = player.blockPosition()
        val from = SectionPos.of(origin.offset(-range, -range, -range))
        val to = SectionPos.of(origin.offset(range, range, range))
        for (section in SectionPos.betweenClosedStream(from.x(), from.y(), from.z(), to.x(), to.y(), to.z())) {
            val chunk = world.chunkSource.getChunkNow(section.x(), section.z()) ?: continue
            val index = chunk.getSectionIndexFromSectionY(section.y())
            if (index !in chunk.sections.indices || !chunk.sections[index].maybeHas { it.`is`(Blocks.CAVE_AIR) }) {
                continue
            }

            for (caveAir in BlockPos.betweenClosed(section.origin(), section.origin().offset(15, 15, 15))) {
                if (!chunk.getBlockState(caveAir).`is`(Blocks.CAVE_AIR)) {
                    continue
                }
                for (direction in Direction.entries) {
                    val neighbour = caveAir.relative(direction)
                    if (isLonePlainAir(neighbour)) {
                        findings += neighbour
                    }
                }
            }
        }
    }

    private fun isLonePlainAir(pos: BlockPos) = world.getBlockState(pos).`is`(Blocks.AIR) &&
        BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))
            .none { it != pos && world.getBlockState(it).`is`(Blocks.AIR) }

}
