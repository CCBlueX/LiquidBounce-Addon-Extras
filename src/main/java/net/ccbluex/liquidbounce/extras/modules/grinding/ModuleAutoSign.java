package net.ccbluex.liquidbounce.extras.modules.grinding;

import net.ccbluex.liquidbounce.config.types.RangedValue;
import net.ccbluex.liquidbounce.config.types.Value;
import net.ccbluex.liquidbounce.config.types.list.ChoiceListValue;
import net.ccbluex.liquidbounce.config.types.list.Tagged;
import net.ccbluex.liquidbounce.event.events.PacketEvent;
import net.ccbluex.liquidbounce.event.events.ScreenEvent;
import net.ccbluex.liquidbounce.event.events.TransferOrigin;
import net.ccbluex.liquidbounce.extras.ExtrasCategories;
import net.ccbluex.liquidbounce.features.module.ClientModule;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.util.StringUtil;
import net.minecraft.world.level.block.entity.SignTextSlot;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Writes every sign the server opens, without showing the editor: with the text of the last one you wrote
 * yourself, or with a template for each side. {@link SignAura} opens the signs around you for it. `sign` and
 * `slot` of the editor are opened up in `liquidbounce-extras.accesswidener`.
 */
public final class ModuleAutoSign extends ClientModule {

    public static final ModuleAutoSign INSTANCE = new ModuleAutoSign();

    // Longer lines do not fit the packet, and the server drops the connection
    private static final int LINE_LENGTH = 384;

    private final RangedValue<Integer> delay = integer("Delay", 10, 0, 100, "ticks");
    private final ChoiceListValue<TextMode> textMode = enumChoice("TextMode", TextMode.REMEMBER);
    private final List<Value<String>> front = template("Front");
    private final List<Value<String>> back = template("Back");

    @SuppressWarnings("unused")
    private final SignAura nearby = tree(new SignAura(this));

    private List<String> lines = List.of();

    @SuppressWarnings("unused")
    private final AutoCloseable packetHandler = on(PacketEvent.class, this::rememberWrittenLines);

    @SuppressWarnings("unused")
    private final AutoCloseable screenHandler = on(ScreenEvent.class, this::replaceEditor);

    private ModuleAutoSign() {
        super("AutoSign", ExtrasCategories.GRINDING);
    }

    /** What a side gets written with, nothing before the first sign in `Remember` mode. */
    List<String> textFor(SignTextSlot side) {
        if (textMode.get() == TextMode.REMEMBER) {
            return lines;
        }
        return (side == SignTextSlot.FRONT ? front : back).stream()
            .map(line -> StringUtil.truncateStringIfNecessary(StringUtil.filterText(line.get()), LINE_LENGTH, false))
            .toList();
    }

    private List<Value<String>> template(String side) {
        return IntStream.rangeClosed(1, 4).mapToObj(line -> text(side + line, "")).toList();
    }

    private void rememberWrittenLines(PacketEvent event) {
        if (event.getOrigin() == TransferOrigin.OUTGOING
            && event.getPacket() instanceof ServerboundSignUpdatePacket packet) {
            lines = packet.lines();
        }
    }

    // after() hands back a handle to cancel the task; disabling the module cancels it anyway
    @SuppressWarnings("resource")
    private void replaceEditor(ScreenEvent event) {
        if (!(event.getScreen() instanceof AbstractSignEditScreen editor)) {
            return;
        }
        var text = textFor(editor.slot);
        if (text.isEmpty()) {
            return;
        }

        var update = new ServerboundSignUpdatePacket(editor.sign.getBlockPos(), text, editor.slot);
        // Some servers reject a sign update that follows its placement too closely
        after(delay.get(), () -> getNetwork().send(update));
        event.cancelEvent();
    }

    @Override
    public void onDisabled() {
        lines = List.of();
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
