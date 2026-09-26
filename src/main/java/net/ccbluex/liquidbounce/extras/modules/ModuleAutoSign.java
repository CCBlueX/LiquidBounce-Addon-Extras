package net.ccbluex.liquidbounce.extras.modules;

import net.ccbluex.liquidbounce.extras.ExtrasCategories;
import net.ccbluex.liquidbounce.event.events.PacketEvent;
import net.ccbluex.liquidbounce.event.events.ScreenEvent;
import net.ccbluex.liquidbounce.features.module.ClientModule;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;

import java.util.List;

/**
 * Writes every new sign with the text of the last one you wrote yourself. The sign editor never
 * opens: the screen event is cancelled and the packet the editor would send goes out directly. The
 * screen's `sign` field is protected, so `liquidbounce-extras.accesswidener` opens it.
 */
public class ModuleAutoSign extends ClientModule {

    private List<String> lines;

    public ModuleAutoSign() {
        super("AutoSign", ExtrasCategories.EXTRAS);
        on(PacketEvent.class, this::onPacket);
        on(ScreenEvent.class, this::onScreen);
    }

    private void onPacket(PacketEvent event) {
        if (event.getPacket() instanceof ServerboundSignUpdatePacket packet) {
            lines = packet.lines();
        }
    }

    private void onScreen(ScreenEvent event) {
        if (lines == null || !(event.getScreen() instanceof AbstractSignEditScreen screen)) {
            return;
        }

        getNetwork().send(new ServerboundSignUpdatePacket(
            screen.sign.getBlockPos(), lines, screen.slot
        ));
        event.cancelEvent();
    }

    @Override
    public void onDisabled() {
        lines = null;
    }

}
