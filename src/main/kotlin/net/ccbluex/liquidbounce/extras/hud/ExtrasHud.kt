package net.ccbluex.liquidbounce.extras.hud

import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.utils.client.ServerObserver
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.PlayerFaceExtractor
import net.minecraft.network.chat.Component
import net.minecraft.util.Util
import net.minecraft.world.phys.Vec3
import java.util.Locale

val extrasHudComponents: List<TextHudComponent> =
    [FpsHud, TpsHud, PingHud, PlayerHud, CpsHud, BiomeHud, CoordinatesHud, SpeedometerHud]

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
