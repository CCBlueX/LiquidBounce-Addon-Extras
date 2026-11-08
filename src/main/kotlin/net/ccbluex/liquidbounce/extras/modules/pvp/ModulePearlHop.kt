package net.ccbluex.liquidbounce.extras.modules.pvp

import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.extras.util.report
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.aiming.RotationsValueGroup
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.useHotbarSlotOrOffhand
import net.ccbluex.liquidbounce.utils.kotlin.random
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.item.Items

/**
 * Throws a pearl at our feet at a steep angle for a short hop, once or every time something hits us. Legit
 * stages it like a player would, Silent does it without touching the visible slot or view.
 */
object ModulePearlHop : ClientModule("PearlHop", ExtrasCategories.PVP, disableOnQuit = true) {

    private val mode by enumChoice("Mode", Mode.LEGIT)
    private val pitch by floatRange("Pitch", 70f..80f, 45f..90f)
    private val delay by intRange("Delay", 1..3, 1..10, "ticks")
    private val turnSpeed by floatRange("TurnSpeed", 20f..35f, 5f..90f, "°")

    private object Auto : ToggleableValueGroup(this, "Auto", false) {
        val delay by intRange("Delay", 2..6, 0..20, "ticks")
        val trigger by enumChoice("Trigger", Trigger.PLAYER_MELEE)
    }

    init {
        tree(Auto)
    }

    private val rotations = tree(RotationsValueGroup(this))

    @Volatile
    private var hit = false

    override fun onDisabled() {
        hit = false
    }

    @Suppress("unused")
    private val hitHandler = handler<PacketEvent> { event ->
        val packet = event.packet
        if (event.origin != TransferOrigin.INCOMING || packet !is ClientboundDamageEventPacket ||
            packet.entityId != player.id) {
            return@handler
        }
        if (packet.sourceCauseId != -1 && packet.sourceCauseId != player.id && Auto.trigger.accepts(packet)) {
            hit = true
        }
    }

    @Suppress("unused")
    private val hopHandler = tickHandler {
        if (!Auto.enabled) {
            hop()
            enabled = false
            return@tickHandler
        }
        if (hit) {
            hit = false
            waitTicks(Auto.delay.random())
            hop()
        }
    }

    private suspend fun hop() {
        val pearl = hotbarSlotOf(Items.ENDER_PEARL)
        if (pearl == null) {
            report("missing")
            return
        }
        val down = Rotation(player.yRot, pitch.random())
        if (mode == Mode.SILENT) {
            waitTicks(delay.random())
            throwFrom(pearl) { rotations.toRotationTarget(down) }
        } else {
            staged(pearl, down)
        }
    }

    /** Switch, look down, throw, look back, switch back, each a moment apart. */
    private suspend fun staged(pearl: HotbarItemSlot, down: Rotation) {
        val selected = player.inventory.selectedSlot
        val view = Rotation(player.yRot, player.xRot)
        pearl.hotbarIndex?.let {
            player.inventory.selectedSlot = it
            waitTicks(delay.random())
        }
        if (lookAt(down, turnSpeed) && isReady(pearl)) {
            useHotbarSlotOrOffhand(pearl, 1, player.yRot, player.xRot)
            waitTicks(delay.random())
        }
        lookAt(view, turnSpeed)
        waitTicks(delay.random())
        player.inventory.selectedSlot = selected
    }

    private enum class Mode(override val tag: String) : Tagged {
        LEGIT("Legit"),
        SILENT("Silent"),
    }

    private enum class Trigger(override val tag: String, val accepts: (ClientboundDamageEventPacket) -> Boolean) :
        Tagged {
        PLAYER_MELEE("PlayerMelee", { it.sourceType.`is`(DamageTypes.PLAYER_ATTACK) }),
        ANY("Any", { true }),
    }

}
