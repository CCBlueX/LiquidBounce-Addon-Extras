package net.ccbluex.liquidbounce.extras.modules

import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.RefreshArrayListEvent
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.render.drawBox
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.render.withPositionRelativeToCamera
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.block.outlineBox
import net.ccbluex.liquidbounce.utils.block.state
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks

/**
 * Highlights every block of one type around the player. Scans on a tick handler, draws on the
 * render event, and puts the count into the module's tag in the HUD.
 */
object ModuleBlockFinder : ClientModule("BlockFinder", ExtrasCategories.EXTRAS) {

    private val block by block("Block", Blocks.DIAMOND_ORE)
    private val range by int("Range", 8, 1..16, "blocks")
    private val color by color("Color", Color4b(0, 200, 255, 90))
    private val outline by boolean("Outline", true)

    private var found: List<BlockPos> = emptyList()

    override val tag: String
        get() = found.size.toString()

    override fun onDisabled() {
        found = emptyList()
        EventManager.callEvent(RefreshArrayListEvent)
    }

    @Suppress("unused")
    private val scanHandler = tickHandler {
        val origin = player.blockPosition()
        // betweenClosed hands out one moving cursor, so copy each hit before moving on.
        val positions = BlockPos.betweenClosed(origin.offset(-range, -range, -range), origin.offset(range, range, range))
            .mapNotNull { pos -> if (pos.state?.block === block) pos.immutable() else null }

        if (positions.size != found.size) {
            EventManager.callEvent(RefreshArrayListEvent)
        }
        found = positions
        waitTicks(10)
    }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        event.renderEnvironment {
            for (pos in found) {
                withPositionRelativeToCamera(pos) {
                    drawBox(pos.outlineBox, color, if (outline) color.alpha(255) else null)
                }
            }
        }
    }

}
