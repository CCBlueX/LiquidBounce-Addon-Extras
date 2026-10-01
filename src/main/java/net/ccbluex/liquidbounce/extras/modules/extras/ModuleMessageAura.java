package net.ccbluex.liquidbounce.extras.modules.extras;

import net.ccbluex.liquidbounce.config.types.RangedValue;
import net.ccbluex.liquidbounce.config.types.Value;
import net.ccbluex.liquidbounce.event.events.WorldEntityRemoveEvent;
import net.ccbluex.liquidbounce.extras.ExtrasCategories;
import net.ccbluex.liquidbounce.features.misc.FriendManager;
import net.ccbluex.liquidbounce.features.module.ClientModule;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

/**
 * Whispers a message to every player who comes into view, one per delay. The client has no event for an
 * entity entering, so arrivals are the difference between two ticks' player lists. Whoever is around when
 * you join or enable the module is left alone.
 */
public final class ModuleMessageAura extends ClientModule {

    public static final ModuleMessageAura INSTANCE = new ModuleMessageAura();

    private final Value<String> message = text("Message", "Hello from LiquidBounce");
    private final RangedValue<Integer> delay = integer("Delay", 40, 1, 400, "ticks");
    private final Value<Boolean> ignoreFriends = bool("IgnoreFriends", true);

    private final Set<UUID> inView = new HashSet<>();
    private final Queue<String> recipients = new ArrayDeque<>();
    private @Nullable ClientLevel level;
    private boolean whispering;

    @SuppressWarnings("unused")
    private final AutoCloseable removeHandler = on(WorldEntityRemoveEvent.class,
        event -> inView.remove(event.getEntity().getUUID()));

    @SuppressWarnings("unused")
    private final AutoCloseable lookoutHandler = onTick(this::lookOut);

    private ModuleMessageAura() {
        super("MessageAura", ExtrasCategories.EXTRAS);
    }

    private void lookOut() {
        var world = getWorld();
        if (world != level) {
            level = world;
            whispering = false;
            recipients.clear();
            inView.clear();
            world.players().forEach(player -> inView.add(player.getUUID()));
            return;
        }

        for (Player other : world.players()) {
            if (other != getPlayer() && inView.add(other.getUUID()) && !isExempt(other)) {
                recipients.add(other.getGameProfile().name());
            }
        }

        if (!whispering) {
            whisperNext();
        }
    }

    // after() hands back a handle to cancel the task; disabling the module cancels it anyway
    @SuppressWarnings("resource")
    private void whisperNext() {
        var recipient = recipients.poll();
        whispering = recipient != null;
        if (whispering) {
            getNetwork().sendCommand("msg " + recipient + " " + message.get());
            after(delay.get(), this::whisperNext);
        }
    }

    private boolean isExempt(Player player) {
        return ignoreFriends.get() && FriendManager.INSTANCE.isFriend(player);
    }

    @Override
    public void onDisabled() {
        level = null;
        whispering = false;
        inView.clear();
        recipients.clear();
    }

}
