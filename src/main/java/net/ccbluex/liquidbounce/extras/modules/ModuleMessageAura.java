package net.ccbluex.liquidbounce.extras.modules;

import net.ccbluex.liquidbounce.extras.ExtrasCategories;
import net.ccbluex.liquidbounce.config.types.Value;
import net.ccbluex.liquidbounce.event.events.WorldEntityRemoveEvent;
import net.ccbluex.liquidbounce.features.misc.FriendManager;
import net.ccbluex.liquidbounce.features.module.ClientModule;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Whispers a message to every player who comes into view. The client only has an event for entities
 * leaving the world, so arrivals are found by comparing the player list each tick. Players already
 * there when you join a world are not greeted, and messages go out one at a time.
 */
public class ModuleMessageAura extends ClientModule {

    private final Value<String> message = text("Message", "Hello from LiquidBounce");
    private final Value<Integer> delay = integer("Delay", 40, 1, 400, "ticks");
    private final Value<Boolean> ignoreFriends = bool("IgnoreFriends", true);

    private final Set<UUID> inView = new HashSet<>();
    private final Deque<String> pending = new ArrayDeque<>();
    private ClientLevel level;
    private int ticksSinceMessage;

    public ModuleMessageAura() {
        super("MessageAura", ExtrasCategories.EXTRAS);
        onTick(this::tick);
        on(WorldEntityRemoveEvent.class, event -> inView.remove(event.getEntity().getUUID()));
    }

    private void tick() {
        if (!getInGame()) {
            return;
        }

        if (getWorld() != level) {
            level = getWorld();
            inView.clear();
            pending.clear();
            for (Player other : getWorld().players()) {
                inView.add(other.getUUID());
            }
            return;
        }

        for (Player other : getWorld().players()) {
            if (other == getPlayer() || !inView.add(other.getUUID())) {
                continue;
            }
            if (ignoreFriends.get() && FriendManager.INSTANCE.isFriend(other)) {
                continue;
            }
            pending.add(other.getGameProfile().name());
        }

        if (++ticksSinceMessage >= delay.get() && !pending.isEmpty()) {
            getNetwork().sendCommand("msg " + pending.poll() + " " + message.get());
            ticksSinceMessage = 0;
        }
    }

    @Override
    public void onDisabled() {
        inView.clear();
        pending.clear();
        level = null;
    }

}
