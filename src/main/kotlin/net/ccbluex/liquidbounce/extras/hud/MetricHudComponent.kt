package net.ccbluex.liquidbounce.extras.hud

import net.ccbluex.liquidbounce.event.events.DisconnectEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.render.drawQuad
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

/**
 * A number on the HUD, coloured by how good it is, over a graph of its recent readings. Readings at [good] or
 * better are good, down to [ok] they are ok; a [good] below [ok] means lower is better.
 */
abstract class MetricHudComponent(
    name: String,
    description: String,
    prefix: String,
    suffix: String = "",
    row: Int,
    private val good: Double,
    private val ok: Double,
) : TextHudComponent(name, description, prefix, suffix, row) {

    private val graph by boolean("Graph", true)
    private val grading by boolean("Grading", true)
    private val goodColor by color("GoodColor", Color4b(85, 255, 85))
    private val okColor by color("OkColor", Color4b(255, 255, 85))
    private val badColor by color("BadColor", Color4b(255, 85, 85))

    /** The current reading, null while there is none. */
    abstract val reading: Double?

    protected abstract fun format(reading: Double): String

    /** What a full bar of the graph stands for, given the highest reading on it. */
    protected abstract fun scale(peak: Double): Double

    /** The readings of the last [SAMPLES] intervals, oldest first. */
    val history: List<Double>
        field = ArrayDeque<Double>(SAMPLES)

    @Suppress("unused")
    private val sampleHandler = tickHandler {
        reading?.let {
            if (history.size == SAMPLES) {
                history.removeFirst()
            }
            history.addLast(it)
        }
        waitTicks(SAMPLE_TICKS)
    }

    @Suppress("unused")
    private val disconnectHandler = handler<DisconnectEvent> { history.clear() }

    override fun onDisabled() = history.clear()

    override fun value(): Component {
        val reading = reading ?: return Component.literal(UNAVAILABLE)
        val text = Component.literal(format(reading))
        return if (grading) text.withColor(gradeColor(reading).argb and RGB) else text
    }

    override val footerHeight get() = if (graph) GRAPH_HEIGHT else 0f

    override val footerWidth get() = if (graph) SAMPLES * BAR_WIDTH else 0f

    override fun drawFooter(context: GuiGraphicsExtractor, x: Float, y: Float, width: Float) {
        val bar = width / SAMPLES
        val scale = scale(history.maxOrNull() ?: 0.0)
        // The newest reading stays at the right edge
        val first = SAMPLES - history.size
        history.forEachIndexed { index, sample ->
            val height = (sample / scale * GRAPH_HEIGHT).toFloat().coerceIn(1f, GRAPH_HEIGHT)
            val color = if (grading) gradeColor(sample) else Color4b.WHITE
            val left = x + (first + index) * bar
            context.drawQuad(
                left, y + GRAPH_HEIGHT - height, left + bar - BAR_GAP, y + GRAPH_HEIGHT,
                if (index == history.lastIndex) color else color.alpha(color.a / 2),
            )
        }
    }

    private fun gradeColor(reading: Double): Color4b {
        val better = if (good < ok) { limit: Double -> reading <= limit } else { limit: Double -> reading >= limit }
        return when {
            better(good) -> goodColor
            better(ok) -> okColor
            else -> badColor
        }
    }

    companion object {
        const val SAMPLES = 24
        const val SAMPLE_TICKS = 10
        private const val GRAPH_HEIGHT = 6f
        private const val BAR_WIDTH = 2f
        private const val BAR_GAP = 0.5f
        private const val RGB = 0xFFFFFF
    }

}
