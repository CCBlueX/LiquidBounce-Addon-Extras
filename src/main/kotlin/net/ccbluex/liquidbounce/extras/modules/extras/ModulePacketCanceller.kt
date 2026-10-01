package net.ccbluex.liquidbounce.extras.modules.extras

import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention

/** Drops the selected packets before any other module sees them. */
object ModulePacketCanceller : ClientModule("PacketCanceller", ExtrasCategories.EXTRAS) {

    private val outgoing by c2sPackets("Outgoing", sortedSetOf())
    private val incoming by s2cPackets("Incoming", sortedSetOf())

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent>(EventPriorityConvention.FIRST_PRIORITY) { event ->
        val dropped = when (event.origin) {
            TransferOrigin.OUTGOING -> outgoing
            TransferOrigin.INCOMING -> incoming
        }
        if (event.packet.type().id in dropped) {
            event.cancelEvent()
        }
    }

}
