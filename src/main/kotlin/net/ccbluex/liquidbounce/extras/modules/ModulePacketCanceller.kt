package net.ccbluex.liquidbounce.extras.modules

import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention

/**
 * Drops the packets picked in the settings. Registry-backed settings (packets, blocks, items, sounds)
 * come with their own picker in the GUI; the handler runs first so no other module sees the packet.
 */
object ModulePacketCanceller : ClientModule("PacketCanceller", ExtrasCategories.EXTRAS) {

    private val outgoing by c2sPackets("Outgoing", sortedSetOf())
    private val incoming by s2cPackets("Incoming", sortedSetOf())

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent>(priority = EventPriorityConvention.FIRST_PRIORITY) { event ->
        val cancelled = when (event.origin) {
            TransferOrigin.OUTGOING -> outgoing
            TransferOrigin.INCOMING -> incoming
        }

        if (event.packet.type().id in cancelled) {
            event.cancelEvent()
        }
    }

}
