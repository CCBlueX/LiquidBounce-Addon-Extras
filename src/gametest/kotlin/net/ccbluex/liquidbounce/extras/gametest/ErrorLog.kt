package net.ccbluex.liquidbounce.extras.gametest

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Every ERROR logged while the test runs, apart from what the test environment itself causes.
 */
object ErrorLog : AbstractAppender("ExampleAddonGameTest", null, null, true, Property.EMPTY_ARRAY) {

    // No account, no Realms, no narrator library and a virtual display.
    private val environment = listOf(
        "CoroutineScope of ModuleClickGUI", "Failed to fetch", "profile key", "realms", "Realms", "LiquidChat",
        "[com.mojang.blaze3d.platform.Window]", "Anisotropic", "Unable to receive update information", "narrator",
    )

    val lines = CopyOnWriteArrayList<String>()

    fun install() {
        start()
        val context = LogManager.getContext(false) as LoggerContext
        context.configuration.rootLogger.addAppender(this, Level.ERROR, null)
        context.updateLoggers()
    }

    override fun append(event: LogEvent) {
        val line = "[${event.loggerName}] ${event.message.formattedMessage}" + (event.thrown?.let { ": $it" } ?: "")
        if (environment.none { it in line }) {
            lines += line
        }
    }

}
