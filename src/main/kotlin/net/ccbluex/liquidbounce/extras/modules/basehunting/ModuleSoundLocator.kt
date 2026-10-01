package net.ccbluex.liquidbounce.extras.modules.basehunting

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.ccbluex.liquidbounce.event.eventListenerScope
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.waitSeconds
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.extras.util.coordinates
import net.ccbluex.liquidbounce.extras.util.drawMarkers
import net.ccbluex.liquidbounce.extras.util.report
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents

/** Marks where the server played selected sounds. A sound repeating at one spot keeps its marker alive. */
object ModuleSoundLocator : ClientModule("SoundLocator", ExtrasCategories.BASE_HUNTING) {

    private val sounds by sounds(
        "Sounds",
        linkedSetOf(
            SoundEvents.GENERIC_EXPLODE.value(), SoundEvents.CHEST_OPEN, SoundEvents.CHEST_CLOSE,
            SoundEvents.BARREL_OPEN, SoundEvents.BARREL_CLOSE, SoundEvents.SHULKER_BOX_OPEN,
            SoundEvents.SHULKER_BOX_CLOSE, SoundEvents.ENDER_CHEST_OPEN, SoundEvents.ENDER_CHEST_CLOSE,
            SoundEvents.PORTAL_AMBIENT, SoundEvents.PORTAL_TRIGGER, SoundEvents.END_PORTAL_SPAWN,
        ),
    )
    private val lifetime by int("Lifetime", 30, 1..300, "seconds")
    private val chat by boolean("Chat", false)
    private val color by color("Color", Color4b(60, 240, 220, 100))

    val markers: Map<BlockPos, SoundEvent>
        field = LinkedHashMap<BlockPos, SoundEvent>()

    private val expiries = HashMap<BlockPos, Job>()

    @Suppress("unused")
    private val soundHandler = handler<PacketEvent> { event ->
        val packet = event.packet as? ClientboundSoundPacket ?: return@handler
        val sound = packet.sound.value()
        if (sound in sounds) {
            val pos = BlockPos.containing(packet.x, packet.y, packet.z)
            mc.execute { mark(pos, sound) }
        }
    }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event -> event.drawMarkers(markers.keys, color) }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> { clear() }

    override fun onDisabled() = clear()

    private fun mark(pos: BlockPos, sound: SoundEvent) {
        if (!running) {
            return
        }
        if (markers.put(pos, sound) != sound && chat) {
            report("heard", sound.location, pos.coordinates)
        }
        val expiry = eventListenerScope.launch {
            waitSeconds(lifetime)
            markers.remove(pos)
            expiries.remove(pos)
        }
        expiries.put(pos, expiry)?.cancel()
    }

    private fun clear() {
        expiries.values.forEach(Job::cancel)
        expiries.clear()
        markers.clear()
    }

}
