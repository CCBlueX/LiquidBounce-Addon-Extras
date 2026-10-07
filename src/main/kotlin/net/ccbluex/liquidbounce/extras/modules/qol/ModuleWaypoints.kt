package net.ccbluex.liquidbounce.extras.modules.qol

import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.extras.util.WorldJournal
import net.ccbluex.liquidbounce.extras.util.coordinates
import net.ccbluex.liquidbounce.extras.util.drawMarkers
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.render.FontManager
import net.ccbluex.liquidbounce.render.drawQuad
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.client.regular
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.util.Mth
import kotlin.math.roundToInt

/**
 * Saved spots of this world and dimension, listed with distance and direction, and the selected one marked
 * in the world. Managed with `.waypoint`.
 */
object ModuleWaypoints : ClientModule("Waypoints", ExtrasCategories.QOL) {

    const val LIMIT = 16
    const val NAME_LENGTH = 32

    private val x by int("X", 4, 0..1000)
    private val y by int("Y", 120, 0..1000)
    private val color by color("Color", Color4b.WHITE)
    private val background by color("Background", Color4b(0, 0, 0, 160))

    val waypoints get() = WorldJournal.waypoints

    var selected: String? = null
        private set

    fun add(name: String, pos: Vec3i) = name.length <= NAME_LENGTH && (waypoints.size < LIMIT || name in waypoints) &&
        WorldJournal.putWaypoint(name, pos)

    fun remove(name: String): Boolean {
        if (selected == name) {
            selected = null
        }
        return WorldJournal.removeWaypoint(name)
    }

    @IgnorableReturnValue
    fun select(name: String?): Boolean {
        if (name != null && name !in waypoints) {
            return false
        }
        selected = name
        return true
    }

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
    private val markerHandler = handler<WorldRenderEvent> { event ->
        val pos = waypoints[selected ?: return@handler] ?: return@handler
        event.drawMarkers(listOf(BlockPos(pos.x, pos.y, pos.z)), color.alpha(80))
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> { selected = null }

    override fun onDisabled() {
        selected = null
    }

    private fun direction(pos: Vec3i): String {
        val towards = Mth.atan2(-(pos.x + 0.5 - player.x), pos.z + 0.5 - player.z) * Mth.RAD_TO_DEG
        return when (Mth.wrapDegrees(towards - player.yRot)) {
            in -45.0..45.0 -> "ahead"
            in 45.0..135.0 -> "right"
            in -135.0..-45.0 -> "left"
            else -> "behind"
        }
    }

}
