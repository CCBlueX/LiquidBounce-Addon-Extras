package net.ccbluex.liquidbounce.extras.util

import net.ccbluex.liquidbounce.event.EventListener

/** World changes also fire after the client clears its level, when module listeners cannot run. */
object WorldEvents : EventListener
