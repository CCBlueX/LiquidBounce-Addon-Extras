package net.ccbluex.liquidbounce.extras.gametest.harness

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import java.util.concurrent.CopyOnWriteArrayList

/** Every ERROR the client logs, apart from what a test machine without an account or sound causes anyway. */
object ClientErrors : AbstractAppender("ExtrasClientErrors", null, null, true, Property.EMPTY_ARRAY) {

    private val environment = listOf(
        "CoroutineScope of ModuleClickGUI", "Failed to fetch", "profile key", "realms", "Realms", "LiquidChat",
        "[com.mojang.blaze3d.platform.Window]", "Anisotropic", "Unable to receive update information", "narrator",
    )

    private val lines = CopyOnWriteArrayList<String>()

    init {
        start()
        val context = LogManager.getContext(false) as LoggerContext
        context.configuration.rootLogger.addAppender(this, Level.ERROR, null)
        context.updateLoggers()
    }

    fun reset() = lines.clear()

    fun assertNone() = check(lines.isEmpty()) {
        "the client logged ${lines.size} error(s), first: ${lines.first()}"
    }

    override fun append(event: LogEvent) {
        val line = "[${event.loggerName}] ${event.message.formattedMessage}" + (event.thrown?.let { ": $it" } ?: "")
        if (environment.none { it in line }) {
            lines += line
        }
    }

}
