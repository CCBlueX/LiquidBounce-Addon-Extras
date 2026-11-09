package net.ccbluex.liquidbounce.extras.hud

import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.DisconnectEvent
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.render.drawQuad
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.client.ServerObserver
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.PlayerFaceExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSetTimePacket
import net.minecraft.util.Util
import net.minecraft.world.phys.Vec3
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

val extrasHudComponents: List<TextHudComponent> = [
    FpsHud, TpsHud, PingHud, PlayerHud, CpsHud, BiomeHud, CoordinatesHud, SpeedometerHud, ClockHud, CartKitHud,
    ResourcesHud,
]

internal const val UNAVAILABLE = "N/A"

private fun decimal(value: Double, digits: Int = 2) = "%.${digits}f".format(Locale.ROOT, value)

object FpsHud : MetricHudComponent(
    "FPS", "Frames rendered per second.", "FPS: ", row = 0, good = 50.0, ok = 30.0,
) {

    private const val SMOOTHING = 0.2

    private var smoothed = Double.NaN

    override val reading get() = smoothed.takeUnless { it.isNaN() } ?: mc.fps.toDouble()

    // The counter jumps once a second, this eases into each new count
    @Suppress("unused")
    private val smoothingHandler = handler<GameTickEvent> {
        smoothed = reading + (mc.fps - reading) * SMOOTHING
    }

    override fun onDisabled() {
        super.onDisabled()
        smoothed = Double.NaN
    }

    override fun format(reading: Double) = reading.roundToInt().toString()

    override fun scale(peak: Double) = maxOf(peak, 60.0)

}

object TpsHud : MetricHudComponent(
    "TPS", "Server ticks per second, estimated from time updates.", "TPS: ", row = 1, good = 18.0, ok = 15.0,
) {

    private val staleAfter = 3.seconds

    /** Whether the server stopped sending the time, which it does every second while it ticks. */
    val stale get() = ServerTimeUpdates.sinceLast?.let { it > staleAfter } == true

    override val reading get() = if (stale) 0.0 else ServerObserver.tps.takeIf { it.isFinite() }

    override fun format(reading: Double) = decimal(reading)

    override fun scale(peak: Double) = 20.0

}

/** When the server last sent the time. */
object ServerTimeUpdates : EventListener {

    @Volatile
    private var last: TimeMark? = null

    val sinceLast: Duration? get() = last?.elapsedNow()

    // Netty thread
    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        if (event.packet is ClientboundSetTimePacket && !event.isCancelled) {
            last = TimeSource.Monotonic.markNow()
        }
    }

    @Suppress("unused")
    private val disconnectHandler = handler<DisconnectEvent> { last = null }

}

object PingHud : MetricHudComponent(
    "Ping", "Your latency to the server.", "Ping: ", " ms", row = 2, good = 80.0, ok = 150.0,
) {

    override val reading get() = mc.player?.let { mc.connection?.getPlayerInfo(it.uuid)?.latency?.toDouble() }

    override fun format(reading: Double) = reading.roundToInt().toString()

    override fun scale(peak: Double) = maxOf(peak, 100.0)

}

object PlayerHud : TextHudComponent("Player", "Your username and skin.", "", row = 3) {

    private val showSkin by boolean("ShowSkin", true)

    override val iconWidth get() = if (showSkin) FACE_SIZE + 3f else 0f

    override fun drawIcon(context: GuiGraphicsExtractor, x: Int, y: Int) {
        val player = mc.player ?: return
        if (showSkin) {
            PlayerFaceExtractor.extractRenderState(context, player.skin, x, y, FACE_SIZE)
        }
    }

    override fun value(): Component = Component.literal(mc.player?.gameProfile?.name ?: mc.user.name)

    private const val FACE_SIZE = 8

}

object CpsHud : TextHudComponent("CPS", "Left and right clicks in the last second.", "CPS: ", row = 4) {
    override fun value(): Component = Component.literal("${ClickCounter.left} / ${ClickCounter.right}")
}

object BiomeHud : TextHudComponent("Biome", "The biome you are standing in.", "Biome: ", row = 5) {
    override fun value(): Component {
        val player = mc.player ?: return Component.literal(UNAVAILABLE)
        val biome = player.level().getBiome(player.blockPosition()).unwrapKey().orElse(null)
            ?: return Component.literal(UNAVAILABLE)
        return Component.translatable(Util.makeDescriptionId("biome", biome.identifier()))
    }
}

object CoordinatesHud : TextHudComponent("Coordinates", "Your position.", "XYZ: ", row = 6) {
    override fun value(): Component {
        val player = mc.player ?: return Component.literal(UNAVAILABLE)
        return Component.literal(listOf(player.x, player.y, player.z).joinToString(" / ") { decimal(it, 1) })
    }
}

object SpeedometerHud : TextHudComponent("Speedometer", "Your horizontal speed.", "Speed: ", row = 7) {

    private val unit by enumChoice("Unit", SpeedUnit.BLOCKS_PER_SECOND)
    private val showUnit by boolean("ShowUnit", true)

    override fun value(): Component {
        val player = mc.player ?: return Component.literal(UNAVAILABLE)
        val speed = Vec3(player.x - player.xo, 0.0, player.z - player.zo).horizontalDistance() * unit.perTick
        return Component.literal(decimal(speed) + if (showUnit) " ${unit.label}" else "")
    }

    private enum class SpeedUnit(override val tag: String, val perTick: Double, val label: String) : Tagged {
        BLOCKS_PER_TICK("BlocksPerTick", 1.0, "b/t"),
        BLOCKS_PER_SECOND("BlocksPerSecond", 20.0, "b/s"),
    }

}

object ClockHud : TextHudComponent("Clock", "The time on your computer.", "", row = 8) {

    private const val BAR_HEIGHT = 2f

    private val weekday by boolean("Weekday", true)
    private val seconds by boolean("Seconds", true)
    private val blinkingColon by boolean("BlinkingColon", false)
    private val minuteBar by boolean("MinuteBar", false)
    private val barColor by color("BarColor", Color4b.WHITE)

    // By weekday, then seconds
    private val formats = listOf("HH:mm", "HH:mm:ss", "EEE HH:mm", "EEE HH:mm:ss")
        .map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }
    private val pattern get() = formats[(if (weekday) 2 else 0) + if (seconds) 1 else 0]

    /** How far the current minute has run, 0 to 1. */
    val minuteProgress get() = minuteProgress(LocalTime.now())

    fun minuteProgress(time: LocalTime) = (time.second + time.nano / 1e9f) / 60f

    /** The clock at [time]; a blinking colon is off in odd seconds. */
    fun format(time: LocalDateTime): String {
        val text = time.format(pattern)
        return if (blinkingColon && time.second % 2 == 1) text.replace(':', ' ') else text
    }

    override fun value(): Component = Component.literal(format(LocalDateTime.now()))

    // Measured with the colons, so the box keeps its size while they blink
    override fun widestValue(): Component = Component.literal(LocalDateTime.now().format(pattern))

    override val footerHeight get() = if (minuteBar) BAR_HEIGHT else 0f

    override fun drawFooter(context: GuiGraphicsExtractor, x: Float, y: Float, width: Float) {
        context.drawQuad(x, y, x + width, y + BAR_HEIGHT, barColor.alpha(barColor.a / 3))
        context.drawQuad(x, y, x + width * minuteProgress, y + BAR_HEIGHT, barColor)
    }

}
