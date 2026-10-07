package net.ccbluex.liquidbounce.extras.modules.grinding;

import net.ccbluex.liquidbounce.config.types.RangedValue;
import net.ccbluex.liquidbounce.config.types.Value;
import net.ccbluex.liquidbounce.config.types.list.ChoiceListValue;
import net.ccbluex.liquidbounce.config.types.list.Tagged;
import net.ccbluex.liquidbounce.event.events.PacketEvent;
import net.ccbluex.liquidbounce.event.events.ScreenEvent;
import net.ccbluex.liquidbounce.event.events.TransferOrigin;
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent;
import net.ccbluex.liquidbounce.extras.ExtrasCategories;
import net.ccbluex.liquidbounce.extras.util.WorldEvents;
import net.ccbluex.liquidbounce.features.module.ClientModule;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.world.level.block.entity.SignTextSlot;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class ModuleAutoSign extends ClientModule {

    public static final ModuleAutoSign INSTANCE = new ModuleAutoSign();

    private final RangedValue<Integer> delay = integer("Delay", 10, 0, 100, "ticks");
    private final ChoiceListValue<TextMode> textMode = enumChoice("TextMode", TextMode.REMEMBER);
    private final List<Value<String>> front = List.of(
        text("Front1", ""), text("Front2", ""), text("Front3", ""), text("Front4", "")
    );
    private final List<Value<String>> back = List.of(
        text("Back1", ""), text("Back2", ""), text("Back3", ""), text("Back4", "")
    );
    private final SignAura nearby = tree(new SignAura(this));

    private List<String> lines = List.of();
    private @Nullable ServerboundSignUpdatePacket pending;

    @SuppressWarnings("unused")
    private final AutoCloseable packetHandler = on(PacketEvent.class, this::rememberWrittenLines);

    @SuppressWarnings("unused")
    private final AutoCloseable screenHandler = on(ScreenEvent.class, this::replaceEditor);

    @SuppressWarnings("unused")
    private final AutoCloseable worldHandler = WorldEvents.INSTANCE.on(WorldChangeEvent.class, _ -> reset());

    private ModuleAutoSign() {
        super("AutoSign", ExtrasCategories.GRINDING);
    }

    private void rememberWrittenLines(PacketEvent event) {
        if (!event.isCancelled() && event.getOrigin() == TransferOrigin.OUTGOING
            && event.getPacket() instanceof ServerboundSignUpdatePacket packet) {
            lines = List.copyOf(packet.lines());
        }
    }

    public List<String> textFor(SignTextSlot slot) {
        if (textMode.get() == TextMode.REMEMBER) {
            return lines;
        }
        return (slot == SignTextSlot.FRONT ? front : back).stream().map(value -> sanitize(value.get())).toList();
    }

    private static String sanitize(String text) {
        var cleaned = text.codePoints().filter(c -> c >= 32 && c != 127 && c != 167).limit(384)
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
        var end = Math.min(cleaned.length(), 384);
        if (end > 0 && Character.isHighSurrogate(cleaned.charAt(end - 1))) {
            end--;
        }
        return cleaned.substring(0, end);
    }

    public boolean isWriting() {
        return pending != null;
    }

    // after() hands back a handle to cancel the task; disabling the module cancels it anyway
    @SuppressWarnings("resource")
    private void replaceEditor(ScreenEvent event) {
        pending = null;
        if (event.isCancelled() || !(event.getScreen() instanceof AbstractSignEditScreen editor)) {
            return;
        }
        var text = textFor(editor.slot);
        if (text.isEmpty()) {
            return;
        }
        var level = getWorld();
        var update = new ServerboundSignUpdatePacket(editor.sign.getBlockPos(), text, editor.slot);
        pending = update;
        // Some servers reject a sign update that follows its placement too closely
        after(delay.get(), () -> {
            if (pending == update) {
                pending = null;
                if (getMc().level == level && getPlayer().blockPosition().distSqr(update.pos()) < 64
                    && !editor.sign.isRemoved() && !editor.sign.isWaxed()) {
                    getNetwork().send(update);
                }
            }
        });
        event.cancelEvent();
    }

    @Override
    public void onDisabled() {
        reset();
    }

    private void reset() {
        lines = List.of();
        pending = null;
    }

    private enum TextMode implements Tagged {
        REMEMBER("Remember"), TEMPLATE("Template");

        private final String tag;

        TextMode(String tag) {
            this.tag = tag;
        }

        @Override
        public String getTag() {
            return tag;
        }
    }

}
