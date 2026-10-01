package net.ccbluex.liquidbounce.extras.modules.qol

import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.extras.util.coordinates
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.render.FontManager
import net.ccbluex.liquidbounce.render.drawQuad
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.client.regular
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import kotlin.math.roundToInt

/** Named spots for this session, listed with distance and direction. Managed with `.waypoint`. */
object ModuleWaypoints : ClientModule("Waypoints", ExtrasCategories.QOL) {

    const val LIMIT = 16
    const val NAME_LENGTH = 32

    private val x by int("X", 4, 0..1000)
    private val y by int("Y", 120, 0..1000)
    private val color by color("Color", Color4b.WHITE)
    private val background by color("Background", Color4b(0, 0, 0, 160))

    val waypoints: Map<String, BlockPos>
        field = LinkedHashMap<String, BlockPos>()

    fun add(name: String, pos: BlockPos): Boolean {
        if (name.length > NAME_LENGTH || waypoints.size >= LIMIT && name !in waypoints) {
            return false
        }
        waypoints[name] = pos.immutable()
        return true
    }

    fun remove(name: String) = waypoints.remove(name) != null

    @Suppress("unused")
    private val renderHandler = handler<OverlayRenderEvent> { event ->
        val font = FontManager.FONT_RENDERER
        waypoints.entries.forEachIndexed { index, (name, pos) ->
            val distance = Mth.sqrt(pos.distToCenterSqr(player.position()).toFloat()).roundToInt()
            val line = regular(message("line", name, distance, message(direction(pos)), pos.coordinates))
            val top = y + index * 14f
            event.context.drawQuad(x - 2f, top - 2f, x + font.getStringWidth(line) + 2f, top + 11f, background)
            font.draw(event.context, line, x.toFloat(), top, color)
        }
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> { waypoints.clear() }

    override fun onDisabled() = waypoints.clear()

    private fun direction(pos: BlockPos): String {
        val towards = Mth.atan2(-(pos.x + 0.5 - player.x), pos.z + 0.5 - player.z) * Mth.RAD_TO_DEG
        return when (Mth.wrapDegrees(towards - player.yRot)) {
            in -45.0..45.0 -> "ahead"
            in 45.0..135.0 -> "right"
            in -135.0..-45.0 -> "left"
            else -> "behind"
        }
    }

}
