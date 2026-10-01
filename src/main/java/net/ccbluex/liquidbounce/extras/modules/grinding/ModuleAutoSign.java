package net.ccbluex.liquidbounce.extras.modules.grinding;

import net.ccbluex.liquidbounce.config.types.RangedValue;
import net.ccbluex.liquidbounce.event.events.PacketEvent;
import net.ccbluex.liquidbounce.event.events.ScreenEvent;
import net.ccbluex.liquidbounce.event.events.TransferOrigin;
import net.ccbluex.liquidbounce.extras.ExtrasCategories;
import net.ccbluex.liquidbounce.features.module.ClientModule;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;

import java.util.List;

/**
 * Writes every sign the server opens with the text of the last one you wrote yourself, without showing
 * the editor. `sign` and `slot` of the editor are opened up in `liquidbounce-extras.accesswidener`.
 */
public final class ModuleAutoSign extends ClientModule {

    public static final ModuleAutoSign INSTANCE = new ModuleAutoSign();

    private final RangedValue<Integer> delay = integer("Delay", 10, 0, 100, "ticks");

    private List<String> lines = List.of();

    @SuppressWarnings("unused")
    private final AutoCloseable packetHandler = on(PacketEvent.class, this::rememberWrittenLines);

    @SuppressWarnings("unused")
    private final AutoCloseable screenHandler = on(ScreenEvent.class, this::replaceEditor);

    private ModuleAutoSign() {
        super("AutoSign", ExtrasCategories.GRINDING);
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
        if (lines.isEmpty() || !(event.getScreen() instanceof AbstractSignEditScreen editor)) {
            return;
        }

        var update = new ServerboundSignUpdatePacket(editor.sign.getBlockPos(), lines, editor.slot);
        // Some servers reject a sign update that follows its placement too closely
        after(delay.get(), () -> getNetwork().send(update));
        event.cancelEvent();
    }

    @Override
    public void onDisabled() {
        lines = List.of();
    }

}
