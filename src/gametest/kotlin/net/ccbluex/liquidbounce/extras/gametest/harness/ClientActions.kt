package net.ccbluex.liquidbounce.extras.gametest.harness

import net.ccbluex.gametest.harness.GameTestScope
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.event.handler
import net.minecraft.core.UUIDUtil
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import net.ccbluex.gametest.harness.action.screenshot as takeScreenshot

/** The harness's screenshot, once the theme no longer shows an "Enabled" or "Disabled" toast. */
fun GameTestScope.screenshot(name: String) {
    awaitToggleToasts()
    takeScreenshot(name)
}

/** Waits until the theme no longer shows an "Enabled" or "Disabled" toast, before a scene a screenshot needs. */
fun GameTestScope.awaitToggleToasts() {
    while (ToggleToasts.showing) {
        ticks(1)
    }
}

/** Whether the theme still shows an "Enabled" or "Disabled" toast, which no screenshot is about. */
internal object ToggleToasts : EventListener {

    // The theme keeps a toast for three seconds and slides it out in a fifth, and on llvmpipe the browser's
    // picture reaches the screen a few slow frames later
    private val lifetime = 4500.milliseconds
    @Volatile
    private var shown: TimeSource.Monotonic.ValueTimeMark? = null

    val showing get() = shown?.let { it.elapsedNow() < lifetime } == true

    fun reset() {
        shown = null
    }

    @Suppress("unused")
    private val notificationHandler = handler<NotificationEvent> {
        if (it.severity == NotificationEvent.Severity.ENABLED || it.severity == NotificationEvent.Severity.DISABLED) {
            shown = TimeSource.Monotonic.markNow()
        }
    }

}

/** An entity UUID as NBT that no other session uses; the server finds some, like a pearl's owner, in any world. */
fun GameTestScope.uniqueUuid(name: String): String =
    UUIDUtil.uuidToIntArray(UUID.nameUUIDFromBytes(unique(name).toByteArray())).joinToString(",", "[I;", "]")
