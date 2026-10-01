package net.ccbluex.liquidbounce.extras.probe;

import ac.grim.grimac.api.GrimAPIProvider;
import ac.grim.grimac.api.event.events.FlagEvent;
import ac.grim.grimac.api.event.events.GrimPlayerSetbackEvent;
import io.papermc.paper.event.player.PlayerFailMoveEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Grim prints next to nothing by default: alerts only past punishment thresholds, and setbacks never.
 * Its API sees every flag, so this plugin reports each one as a console line the game tests parse.
 */
public final class ExtrasProbe extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        var grim = GrimAPIProvider.get();
        var plugin = grim.getGrimPlugin(this);
        var events = grim.getEventBus();

        events.get(FlagEvent.class).onFlagSupplier(plugin, (user, check, verbose, cancelled) -> {
            report("FLAG", user.getName(), check.getCheckName(), check.getViolations(), verbose.get());
            return cancelled;
        });
        events.get(GrimPlayerSetbackEvent.class).onPlayerSetback(plugin, (user, _, x, y, z, _) ->
            report("SETBACK", user.getName(), x, y, z));

        getServer().getPluginManager().registerEvents(this, this);
        report("READY", grim.getGrimVersion());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFailMove(PlayerFailMoveEvent event) {
        if (!event.isAllowed()) {
            report("FAILMOVE", event.getPlayer().getName(), event.getFailReason());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        var reason = PlainTextComponentSerializer.plainText().serialize(event.reason());
        report("KICK", event.getPlayer().getName(), event.getCause(), reason);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        report("QUIT", event.getPlayer().getName(), event.getReason());
    }

    private void report(Object... fields) {
        getLogger().info(Arrays.stream(fields).map(String::valueOf).collect(Collectors.joining(" ")));
    }

}
