package net.ccbluex.liquidbounce.extras.hud

import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.render.drawQuad
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.client.ServerObserver
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.PlayerFaceExtractor
import net.minecraft.network.chat.Component
import net.minecraft.util.Util
import net.minecraft.world.phys.Vec3
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

val extrasHudComponents: List<TextHudComponent> =
    [FpsHud, TpsHud, PingHud, PlayerHud, CpsHud, BiomeHud, CoordinatesHud, SpeedometerHud, ClockHud, CartKitHud]

private const val UNAVAILABLE = "N/A"

private fun decimal(value: Double, digits: Int = 2) = "%.${digits}f".format(Locale.ROOT, value)

object FpsHud : TextHudComponent("FPS", "Frames rendered per second.", "FPS: ", row = 0) {
    override fun value(): Component = Component.literal(mc.fps.toString())
}

object TpsHud : TextHudComponent(
    "TPS", "Server ticks per second, estimated from time updates.", "TPS: ", row = 1,
) {
    override fun value(): Component =
        Component.literal(ServerObserver.tps.takeIf { it.isFinite() }?.let(::decimal) ?: UNAVAILABLE)
}

object PingHud : TextHudComponent("Ping", "Your latency to the server.", "Ping: ", " ms", row = 2) {
    override fun value(): Component {
        val latency = mc.player?.let { mc.connection?.getPlayerInfo(it.uuid)?.latency }
        return Component.literal(latency?.toString() ?: UNAVAILABLE)
    }
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
