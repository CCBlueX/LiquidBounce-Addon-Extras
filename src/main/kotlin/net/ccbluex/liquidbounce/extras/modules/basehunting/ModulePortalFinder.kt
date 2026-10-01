package net.ccbluex.liquidbounce.extras.modules.basehunting

import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.extras.util.BlockTracker
import net.ccbluex.liquidbounce.extras.util.coordinates
import net.ccbluex.liquidbounce.extras.util.drawMarkers
import net.ccbluex.liquidbounce.extras.util.report
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.block.ChunkScanner
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks

/** Lit portals only exist where a player built one. */
object ModulePortalFinder : ClientModule("PortalFinder", ExtrasCategories.BASE_HUNTING) {

    private val color by color("Color", Color4b(160, 80, 255, 90))
    private val notify by boolean("Notify", false)

    val portals: Map<ChunkPos, BlockPos>
        field = HashMap<ChunkPos, BlockPos>()

    private val tracker = BlockTracker { state ->
        state.block.takeIf { it === Blocks.NETHER_PORTAL || it === Blocks.END_PORTAL }
    }

    @Suppress("unused")
    private val reportHandler = tickHandler {
        for (pos in tracker.allPositions()) {
            val chunk = ChunkPos.containing(pos)
            if (chunk !in portals) {
                portals[chunk] = pos.immutable()
                report("found", pos.coordinates, notify = notify)
            }
        }
        waitTicks(10)
    }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        event.drawMarkers(tracker.allPositions().asIterable(), color)
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> { portals.clear() }

    override fun onEnabled() = ChunkScanner.subscribe(tracker)

    override fun onDisabled() {
        ChunkScanner.unsubscribe(tracker)
        portals.clear()
    }

}
