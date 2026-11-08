package net.ccbluex.liquidbounce.extras.modules.pvp

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.eventListenerScope
import net.ccbluex.liquidbounce.event.events.DisconnectEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.tags.BlockTags
import net.minecraft.world.entity.EntityTypes

/**
 * TNT minecarts we put down ourselves, by hand or by a module. The server does not say who placed a cart, so
 * a cart that appears on a rail we just clicked counts as ours.
 */
object OwnCarts : EventListener {

    // Generous for lag, short enough that nobody else gets a cart onto the same rail meanwhile
    private const val CLAIM_TICKS = 40

    private val clickedRails = HashMap<BlockPos, Job>()

    /** Entity ids of our carts. */
    val ids: Set<Int>
        field = HashSet<Int>()

    operator fun contains(id: Int) = id in ids

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        when (val packet = event.packet) {
            is ServerboundUseItemOnPacket -> mc.execute { clicked(packet.hitResult.blockPos) }
            is ClientboundAddEntityPacket -> if (packet.type == EntityTypes.TNT_MINECART) {
                mc.execute { appeared(packet) }
            }

            is ClientboundRemoveEntitiesPacket -> mc.execute { ids.removeAll(packet.entityIds) }
        }
    }

    @Suppress("unused")
    private val disconnectHandler = handler<DisconnectEvent> {
        clickedRails.values.forEach(Job::cancel)
        clickedRails.clear()
        ids.clear()
    }

    private fun clicked(pos: BlockPos) {
        if (mc.level?.getBlockState(pos)?.`is`(BlockTags.RAILS) != true) {
            return
        }
        clickedRails.put(pos, eventListenerScope.launch {
            waitTicks(CLAIM_TICKS)
            clickedRails.remove(pos)
        })?.cancel()
    }

    private fun appeared(packet: ClientboundAddEntityPacket) {
        if (BlockPos.containing(packet.x, packet.y, packet.z) in clickedRails) {
            ids += packet.id
        }
    }

}
