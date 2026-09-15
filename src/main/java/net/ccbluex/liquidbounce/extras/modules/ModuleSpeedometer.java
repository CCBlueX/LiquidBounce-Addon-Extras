package net.ccbluex.liquidbounce.extras.modules;

import net.ccbluex.liquidbounce.extras.ExtrasCategories;
import net.ccbluex.liquidbounce.config.types.Value;
import net.ccbluex.liquidbounce.config.types.group.Mode;
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup;
import net.ccbluex.liquidbounce.config.types.list.ChoiceListValue;
import net.ccbluex.liquidbounce.config.types.list.Tagged;
import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent;
import net.ccbluex.liquidbounce.features.module.ClientModule;
import net.ccbluex.liquidbounce.render.FontManager;
import net.ccbluex.liquidbounce.render.Render2DKt;
import net.ccbluex.liquidbounce.render.engine.type.Color4b;
import net.ccbluex.liquidbounce.utils.client.ClientChat;
import net.minecraft.network.chat.MutableComponent;

/**
 * Shows the player's speed on the screen. The same module in Java: settings are fields holding a
 * [Value], event handlers are methods passed to [on], modes are inner classes.
 */
public class ModuleSpeedometer extends ClientModule {

    enum Unit implements Tagged {
        BLOCKS_PER_TICK("BlocksPerTick", 1.0),
        BLOCKS_PER_SECOND("BlocksPerSecond", 20.0);

        final String tag;
        final double factor;

        Unit(String tag, double factor) {
            this.tag = tag;
            this.factor = factor;
        }

        @Override
        public String getTag() {
            return tag;
        }
    }

    private final Value<Integer> x = integer("X", 4, 0, 400);
    private final Value<Integer> y = integer("Y", 100, 0, 400);
    private final ChoiceListValue<Unit> unit = enumChoice("Unit", Unit.BLOCKS_PER_SECOND);
    private final Value<Color4b> color = color("Color", Color4b.WHITE);
    private final ModeValueGroup<Style> style = choices("Style", new Plain(), new Boxed());

    public ModuleSpeedometer() {
        super("Speedometer", ExtrasCategories.EXTRAS);
        on(OverlayRenderEvent.class, this::onRender);
    }

    private void onRender(OverlayRenderEvent event) {
        if (!getInGame()) {
            return;
        }

        double speed = getPlayer().getDeltaMovement().horizontalDistance() * unit.get().factor;
        MutableComponent line = ClientChat.regular(String.format("%.2f ", speed))
            .append(ClientChat.variable(unit.get().getTag()));
        style.getActiveMode().draw(event, line);
    }

    abstract class Style extends Mode {
        Style(String name) {
            super(name);
        }

        @Override
        public ModeValueGroup<?> getParent() {
            return style;
        }

        abstract void draw(OverlayRenderEvent event, MutableComponent line);
    }

    class Plain extends Style {
        Plain() {
            super("Plain");
        }

        @Override
        void draw(OverlayRenderEvent event, MutableComponent line) {
            FontManager.getFONT_RENDERER().draw(event.getContext(), line, x.get(), y.get(), color.get());
        }
    }

    class Boxed extends Style {
        private final Value<Color4b> background = color("Background", new Color4b(0, 0, 0, 120));
        private final Value<Integer> padding = integer("Padding", 2, 0, 8);

        Boxed() {
            super("Boxed");
        }

        @Override
        void draw(OverlayRenderEvent event, MutableComponent line) {
            float width = FontManager.getFONT_RENDERER().getStringWidth(line);
            float left = x.get() - padding.get();
            float top = y.get() - padding.get();
            Render2DKt.drawQuad(event.getContext(), left, top, x.get() + width + padding.get(), y.get() + 9 + padding.get(), background.get());
            FontManager.getFONT_RENDERER().draw(event.getContext(), line, x.get(), y.get(), color.get());
        }
    }

}
