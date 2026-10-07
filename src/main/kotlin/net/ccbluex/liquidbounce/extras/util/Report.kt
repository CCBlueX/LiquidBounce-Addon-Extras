package net.ccbluex.liquidbounce.extras.util

import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.notification
import net.ccbluex.liquidbounce.utils.client.regular
import net.ccbluex.liquidbounce.utils.client.variable
import net.minecraft.core.Vec3i
import net.minecraft.world.level.ChunkPos

/**
 * Chat line from the module's `messages.<key>` translation with highlighted arguments, optionally
 * mirrored as a notification.
 */
context(module: ClientModule)
fun report(key: String, vararg args: Any, notify: Boolean = false) {
    val message = regular(module.message(key, *args.map { variable(it.toString()) }.toTypedArray()))
    chat(message)
    if (notify) {
        notification(module.name, message.string, NotificationEvent.Severity.INFO)
    }
}

val Vec3i.coordinates get() = "$x $y $z"

val ChunkPos.coordinates get() = "$minBlockX $minBlockZ"
