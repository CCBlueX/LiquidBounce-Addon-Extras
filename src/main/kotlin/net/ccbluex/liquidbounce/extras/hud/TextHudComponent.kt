package net.ccbluex.liquidbounce.extras.hud

import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleHud
import net.ccbluex.liquidbounce.integration.theme.component.HudComponentManager
import net.ccbluex.liquidbounce.integration.theme.component.components.NativeHudComponent
import net.ccbluex.liquidbounce.render.FontManager
import net.ccbluex.liquidbounce.render.drawQuad
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.render.Alignment
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

/** One line of text on the HUD: a live value between an editable prefix and suffix. */
abstract class TextHudComponent(
    name: String,
    description: String,
    prefix: String,
    suffix: String = "",
    row: Int,
) : NativeHudComponent(
    name,
    enabled = false,
    Alignment(Alignment.ScreenAxisX.LEFT, 8, Alignment.ScreenAxisY.TOP, 180 + row * 40),
    description = description,
) {

    private val prefix by text("Prefix", prefix)
    private val suffix by text("Suffix", suffix)
    private val textColor by color("TextColor", Color4b.WHITE)
    private val shadow by boolean("Shadow", true)
    private val boxed by boolean("Boxed", false)
    private val background by color("Background", Color4b(0, 0, 0, 120))
    private val padding by int("Padding", 2, 0..8)

    protected abstract fun value(): Component

    protected open val iconWidth get() = 0f

    protected open fun drawIcon(context: GuiGraphicsExtractor, x: Int, y: Int) {}

    val text: Component get() = Component.literal(prefix).append(value()).append(suffix)

    override val guiScaledWidth get() = iconWidth + FontManager.FONT_RENDERER.getStringWidth(text) + padding * 2

    override val guiScaledHeight get() = TEXT_HEIGHT + padding * 2

    private var sizeInEditor = 0f to 0f

    // The editor drags a box of the size it was last told about
    @Suppress("unused")
    private val editorSizeHandler = handler<GameTickEvent> {
        val size = guiScaledWidth to guiScaledHeight
        if (ModuleHud.hudEditorSelected && size != sizeInEditor) {
            sizeInEditor = size
            HudComponentManager.updateComponents()
        }
    }

    @Suppress("unused")
    private val renderHandler = handler<OverlayRenderEvent> { event ->
        if (mc.gui.hud.isHidden) {
            return@handler
        }

        val bounds = getGuiScaledBounds()
        if (boxed) {
            event.context.drawQuad(bounds.xMin, bounds.yMin, bounds.xMax, bounds.yMax, background)
        }
        val x = bounds.xMin + padding
        val y = bounds.yMin + padding
        drawIcon(event.context, x.toInt(), y.toInt())
        FontManager.FONT_RENDERER.draw(event.context, text, x + iconWidth, y, textColor, shadow)
    }

    private companion object {
        const val TEXT_HEIGHT = 9f
    }

}
