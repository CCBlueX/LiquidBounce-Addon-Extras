package net.ccbluex.liquidbounce.extras.gametest.harness

import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.extras.gametest.mixin.ChatComponentAccessor
import net.ccbluex.liquidbounce.features.command.CommandManager
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos

/**
 * Turns towards [pos] and lets the new rotation reach the server before anything is done with it. A human
 * cannot turn and click within one tick, and Grim knows that.
 */
fun GameTestScope.aim(pos: BlockPos) {
    input.lookAt(pos)
    ticks(2)
}

fun GameTestScope.enable(module: ClientModule) = client { module.enabled = true }

fun GameTestScope.disable(module: ClientModule) = client { module.enabled = false }

@Suppress("UNCHECKED_CAST")
fun <T : Any> ValueGroup.setting(name: String) = containedValues.first { it.name == name } as Value<T>

/** Runs a `.command` of the client; it never reaches the server. */
fun GameTestScope.command(line: String) = client { CommandManager.execute(line) }

/** Chat lines without formatting, newest first. Only readable on the client thread. */
val Minecraft.chat: List<String>
    get() = (gui.hud.chat as ChatComponentAccessor).allMessages.map {
        ChatFormatting.stripFormatting(it.content().string).orEmpty()
    }

fun Minecraft.chatContains(text: String) = chat.any { text in it }

fun GameTestScope.chat() = client { it.chat }

fun GameTestScope.screenshot(name: String) {
    // Vanilla's notes on unsigned chat and social interactions would cover the corner
    client { it.gui.toastManager().clear() }
    context.takeScreenshot(name)
}
