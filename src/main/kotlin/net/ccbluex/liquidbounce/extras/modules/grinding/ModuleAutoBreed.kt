package net.ccbluex.liquidbounce.extras.modules.grinding

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.ccbluex.liquidbounce.event.eventListenerScope
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.extras.util.WorldEvents
import net.ccbluex.liquidbounce.event.tickConditional
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.extras.util.BlockTracker
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.aiming.NormalRotationMode
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.utils.raytraceBox
import net.ccbluex.liquidbounce.utils.block.ChunkScanner
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.entity.interactEntity
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.raytracing.isLookingAtEntity
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.animal.panda.Panda
import net.minecraft.world.level.block.Blocks
import java.util.UUID

object ModuleAutoBreed : ClientModule("AutoBreed", ExtrasCategories.GRINDING) {
    private val animals by entityTypes("Animals", BreedingRules.supported.toCollection(linkedSetOf()))
    private val range by float("Range", 3f, 1f..6f, "blocks")
    private val delay by int("Delay", 10, 1..40, "ticks")
    private val rotationMode = modes(this, "RotationMode") { arrayOf(NormalRotationMode(it, this)) }
    private val bamboo = BlockTracker { state -> state.block.takeIf { it === Blocks.BAMBOO } }
    private val suppressed = hashMapOf<UUID, Job>()

    val confirmed: Set<UUID>
        field = HashSet<UUID>()

    @Suppress("unused")
    private val feedHandler = tickHandler {
        if (mc.gui.screen() != null || player.isUsingItem || player.isShiftKeyDown) return@tickHandler
        val reach = minOf(range.toDouble(), player.entityInteractionRange())
        val candidates = world.getEntitiesOfClass(Animal::class.java, player.boundingBox.inflate(reach + 2)) {
            it.type in animals && it.type in BreedingRules.supported && BreedingRules.eligible(it) && habitat(it)
        }
        val target = candidates.asSequence().filter { it.uuid !in suppressed }
            .filter { first -> candidates.any { BreedingRules.partners(first, it) && first.distanceToSqr(it) < 64 } }
            .filter { Slots.OffhandWithHotbar.findSlot { stack -> BreedingRules.food(it, stack) } != null }
            .sortedBy { it.distanceToSqr(player) }
            .firstOrNull { raytraceBox(player.eyePosition, it.boundingBox, reach, 0.0) != null }
            ?: return@tickHandler
        val level = world
        var attempted = false
        var active = true
        try {
            tickConditional(40) {
                if (world !== level || !BreedingRules.eligible(target) || target.uuid in suppressed) {
                    return@tickConditional true
                }
                val ray = raytraceBox(player.eyePosition, target.boundingBox, reach, 0.0)
                    ?: return@tickConditional true
                rotationMode.activeMode.rotate(ray.rotation, isFinished = { aimedAt(target, reach) != null }) {
                    if (!active || !running || mc.level !== level || attempted) return@rotate
                    if (!BreedingRules.eligible(target) || mc.gui.screen() != null ||
                        player.isUsingItem || player.isShiftKeyDown
                    ) {
                        return@rotate
                    }
                    val hit = aimedAt(target, reach) ?: return@rotate
                    val food = Slots.OffhandWithHotbar.findSlot { BreedingRules.food(target, it) } ?: return@rotate
                    if (!food.isOffHand) SilentHotbar.selectSlotSilently(this@ModuleAutoBreed, food, delay + 1)
                    suppress(target.uuid, 100)
                    val _ = interactEntity(target, hit, food.useHand)
                    attempted = true
                }
                attempted
            }
        } finally {
            active = false
        }
        waitTicks(delay)
    }

    @Suppress("unused")
    private val confirmationHandler = handler<PacketEvent> { event ->
        if (event.origin != TransferOrigin.INCOMING || event.isCancelled) return@handler
        val packet = event.packet as? ClientboundEntityEventPacket ?: return@handler
        if (packet.eventId.toInt() != 18) return@handler
        val animal = packet.getEntity(world) as? Animal ?: return@handler
        if (animal.type !in animals || !confirmed.add(animal.uuid)) return@handler
        // The server sends hearts, but not the 6000-tick age cooldown. Allow love time as well.
        suppress(animal.uuid, 6600)
    }

    private fun suppress(id: UUID, ticks: Int) {
        suppressed.remove(id)?.cancel()
        suppressed[id] = eventListenerScope.launch {
            waitTicks(ticks)
            suppressed.remove(id)
            confirmed.remove(id)
        }
    }

    private fun habitat(animal: Animal): Boolean = animal !is Panda || bamboo.allPositions().any { pos ->
        val origin = animal.blockPosition()
        pos.y in origin.y..origin.y + 2 && kotlin.math.abs(pos.x - origin.x) < 8 &&
            kotlin.math.abs(pos.z - origin.z) < 8
    }

    private fun aimedAt(animal: Animal, reach: Double) = isLookingAtEntity(
        toEntity = animal, rotation = RotationManager.serverRotation, range = reach, throughWallsRange = 0.0,
    )

    @Suppress("unused")
    private val worldHandler = WorldEvents.handler<WorldChangeEvent> { event ->
        clear()
        if (event.world == null) onDisabled() else if (enabled) onEnabled()
    }

    override fun onEnabled() {
        ChunkScanner.unsubscribe(bamboo)
        ChunkScanner.subscribe(bamboo)
    }

    override fun onDisabled() {
        ChunkScanner.unsubscribe(bamboo)
        clear()
    }

    private fun clear() {
        suppressed.values.forEach(Job::cancel)
        suppressed.clear()
        confirmed.clear()
    }
}
