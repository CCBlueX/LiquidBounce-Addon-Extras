package net.ccbluex.liquidbounce.extras.modules;

import net.ccbluex.liquidbounce.extras.ExtrasCategories;
import net.ccbluex.liquidbounce.config.types.Value;
import net.ccbluex.liquidbounce.event.events.ScreenEvent;
import net.ccbluex.liquidbounce.features.module.ClientModule;
import net.ccbluex.liquidbounce.utils.client.ClientChat;
import net.minecraft.client.gui.screens.DeathScreen;

/**
 * Respawns a few ticks after dying. The wait is scheduled on the module, so it is dropped if the module
 * is disabled in the meantime.
 */
public class ModuleQuickRespawn extends ClientModule {

    private final Value<Integer> delay = integer("Delay", 20, 0, 100, "ticks");
    private final Value<Boolean> announce = bool("Announce", true);

    private boolean pending;

    public ModuleQuickRespawn() {
        super("QuickRespawn", ExtrasCategories.EXTRAS);
        on(ScreenEvent.class, this::onScreen);
    }

    private void onScreen(ScreenEvent event) {
        if (event.getScreen() instanceof DeathScreen && !pending) {
            pending = true;
            after(delay.get(), this::respawn);
        }
    }

    private void respawn() {
        pending = false;
        getPlayer().respawn();
        if (announce.get()) {
            ClientChat.chat("Respawned");
        }
    }

    @Override
    public void onDisabled() {
        pending = false;
    }

}
