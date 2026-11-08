package net.ccbluex.liquidbounce.extras.modules.pvp

import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.tickUntil
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.extras.util.report
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.aiming.RotationsValueGroup
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket
import net.minecraft.world.item.Items

/**
 * Throws a pearl where we look and, the moment the server teleports us to it, a wind charge at our feet.
 */
object ModulePearlLaunch : ClientModule("PearlLaunch", ExtrasCategories.PVP, disableOnQuit = true) {

    private const val FLIGHT_TIMEOUT = 300

    private val jump by boolean("Jump", true)
    private val rotations = tree(RotationsValueGroup(this))

    @Volatile
    private var teleported = false
    private var jumping = false

    override fun onDisabled() {
        teleported = false
        jumping = false
    }

    // Accepting it is when the client actually stands there; that also tells the server our real view, so
    // turning down happens only afterwards, or the server would keep a rotation we no longer send
    @Suppress("unused")
    private val teleportHandler = handler<PacketEvent> { event ->
        if (event.origin == TransferOrigin.OUTGOING && event.packet is ServerboundAcceptTeleportationPacket) {
            teleported = true
        }
    }

    @Suppress("unused")
    private val jumpHandler = handler<MovementInputEvent> { event ->
        if (jumping) {
            event.jump = true
        }
    }

    @Suppress("unused")
    private val launchHandler = tickHandler {
        val pearl = hotbarSlotOf(Items.ENDER_PEARL)
        if (pearl == null || hotbarSlotOf(Items.WIND_CHARGE) == null) {
            report("missing")
            enabled = false
            return@tickHandler
        }

        val look = Rotation(player.yRot, player.xRot)
        if (throwFrom(pearl) { rotations.toRotationTarget(look) }) {
            teleported = false
            tickUntil { teleported || it >= FLIGHT_TIMEOUT }
            val down = Rotation(player.yRot, FEET_PITCH)
            val charge = hotbarSlotOf(Items.WIND_CHARGE)
            if (teleported && charge != null && throwFrom(charge) { rotations.toRotationTarget(down) } && jump) {
                // The throw returns a tick later, when the jump and the burst below add up, as the client's
                // AutoWindCharge times it
                jumping = true
                waitTicks(1)
                jumping = false
            }
        }
        enabled = false
    }

}
