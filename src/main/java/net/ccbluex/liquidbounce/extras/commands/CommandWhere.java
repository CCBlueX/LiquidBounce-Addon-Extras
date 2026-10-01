package net.ccbluex.liquidbounce.extras.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.ccbluex.liquidbounce.features.command.CommandRegistrar;
import net.ccbluex.liquidbounce.features.command.brigadier.ClientCommandSource;
import net.ccbluex.liquidbounce.lang.LanguageKt;
import net.ccbluex.liquidbounce.utils.client.ClientChat;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * `.where` prints your position, `.where share` says it in the server chat. Plain Brigadier, the way
 * Java builds commands; Kotlin gets a DSL on top.
 */
public final class CommandWhere implements CommandRegistrar {

    public static final CommandWhere INSTANCE = new CommandWhere();

    private CommandWhere() {
    }

    @Override
    public void register(CommandDispatcher<ClientCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<ClientCommandSource>literal("where")
            .executes(_ -> print())
            .then(LiteralArgumentBuilder.<ClientCommandSource>literal("share").executes(_ -> share())));
    }

    private static int print() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return 0;
        }

        var position = ClientChat.variable(format(player.blockPosition()));
        ClientChat.chat(ClientChat.regular(LanguageKt.translation("liquidbounce.command.where.position", position)));
        return Command.SINGLE_SUCCESS;
    }

    private static int share() {
        var minecraft = Minecraft.getInstance();
        var connection = minecraft.getConnection();
        if (connection == null || minecraft.player == null) {
            return 0;
        }

        connection.sendChat("I am at " + format(minecraft.player.blockPosition()));
        return Command.SINGLE_SUCCESS;
    }

    private static String format(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

}
