package net.ccbluex.liquidbounce.extras.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.ccbluex.liquidbounce.features.command.CommandRegistrar;
import net.ccbluex.liquidbounce.features.command.brigadier.ClientCommandSource;
import net.ccbluex.liquidbounce.utils.client.ClientChat;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * `.where` prints the player's position, `.where share` tells the server chat. Brigadier as in any
 * Fabric mod, with [ClientCommandSource] as the source type.
 */
public class CommandWhere implements CommandRegistrar {

    @Override
    public void register(CommandDispatcher<ClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<ClientCommandSource>literal("where")
            .executes(context -> {
                BlockPos pos = Minecraft.getInstance().player.blockPosition();
                ClientChat.chat(ClientChat.regular("You are at ").append(ClientChat.variable(format(pos))));
                return 1;
            })
            .then(LiteralArgumentBuilder.<ClientCommandSource>literal("share")
                .executes(context -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.getConnection().sendChat("I am at " + format(mc.player.blockPosition()));
                    return 1;
                })));
    }

    private static String format(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

}
