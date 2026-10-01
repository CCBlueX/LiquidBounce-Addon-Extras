package net.ccbluex.liquidbounce.extras.util

import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.render.drawBox
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.render.withPositionRelativeToCamera
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB

/** One translucent box per position; the client batches them into a single draw per frame. */
fun WorldRenderEvent.drawMarkers(positions: Iterable<BlockPos>, color: Color4b) = renderEnvironment {
    val outline = color.alpha(255)
    withPositionRelativeToCamera {
        for (pos in positions) {
            drawBox(AABB(pos), color, outline)
        }
    }
}
