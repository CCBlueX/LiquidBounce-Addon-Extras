package net.ccbluex.liquidbounce.extras.modules.grinding

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.ccbluex.liquidbounce.event.eventListenerScope
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickConditional
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.aiming.NoRotationMode
import net.ccbluex.liquidbounce.utils.aiming.NormalRotationMode
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.utils.raytraceBox
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.entity.interactEntity
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.raytracing.isLookingAtEntity
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket
import net.minecraft.world.entity.EntityEvent
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.animal.panda.Panda
import net.minecraft.world.level.block.Blocks
import java.util.UUID
import kotlin.math.min

/**
 * Feeds adult animals in reach that have a partner nearby. Hearts from the server confirm the feeding; the
 * breeding cooldown that follows never reaches the client, so a confirmed animal is left alone for that long.
 */
object ModuleAutoBreed : ClientModule("AutoBreed", ExtrasCategories.GRINDING) {

    private const val AIM_TIMEOUT = 20

    // How far the breed goal looks for a partner
    private const val PARTNER_RANGE = 8.0

    // Hearts that have not come by then mean the server refused
    private const val RETRY = 100

    // 600 ticks in love, then 6000 before the next time
    private const val COOLDOWN = 6600

    private val animals by entityTypes("Animals", BreedingRules.supported.toCollection(linkedSetOf()))
    private val range by float("Range", 3f, 1f..6f, "blocks")
    private val delay by int("Delay", 10, 0..40, "ticks")
    private val rotationMode = modes(this, "RotationMode") {
        arrayOf(NormalRotationMode(it, this), NoRotationMode(it, this))
    }

    /** Animals the server showed hearts for, until their cooldown is over. */
    val confirmed: Set<UUID>
        field = HashSet<UUID>()

    private val skipped = HashMap<UUID, Job>()

    @Suppress("unused")
    private val feedHandler = tickHandler {
        // Sneaking opens a horse's inventory instead
        if (mc.gui.screen() != null || player.isUsingItem || player.isShiftKeyDown) {
            return@tickHandler
        }
        val reach = min(range.toDouble(), player.entityInteractionRange())
        val nearby = world.getEntitiesOfClass(Animal::class.java, player.boundingBox.inflate(reach + PARTNER_RANGE)) {
            it.type in animals && BreedingRules.canBreed(it) && (it !is Panda || it.seesBamboo())
        }
        val (animal, food) = nearby.asSequence()
            .filter { it.uuid !in skipped && hasPartner(it, nearby) }
            .sortedBy { it.distanceToSqr(player) }
            .filter { raytraceBox(player.eyePosition, it.boundingBox, reach, 0.0) != null }
            .firstNotNullOfOrNull { animal ->
                Slots.OffhandWithHotbar.findSlot { BreedingRules.isFood(animal, it) }?.let { animal to it }
            } ?: return@tickHandler

        var fed = false
        tickConditional(AIM_TIMEOUT) {
            if (animal.uuid in skipped) {
                return@tickConditional true
            }
            val (rotation, _) = raytraceBox(player.eyePosition, animal.boundingBox, reach, 0.0)
                ?: return@tickConditional true
            rotationMode.activeMode.rotate(rotation, isFinished = { aimedAt(animal, reach) != null }) {
                val hit = aimedAt(animal, reach) ?: return@rotate
                if (!food.isOffHand) {
                    SilentHotbar.selectSlotSilently(this@ModuleAutoBreed, food, delay + 1)
                }
                interactEntity(animal, hit, food.useHand)
                skip(animal.uuid, RETRY)
                fed = true
            }
            fed
        }
        if (fed) {
            waitTicks(delay)
        }
    }

    @Suppress("unused")
    private val heartsHandler = handler<PacketEvent> { event ->
        val packet = event.packet as? ClientboundEntityEventPacket ?: return@handler
        if (packet.eventId == EntityEvent.IN_LOVE_HEARTS) {
            mc.execute { confirm(packet) }
        }
    }

    override fun onDisabled() {
        skipped.values.forEach(Job::cancel)
        skipped.clear()
        confirmed.clear()
    }

    private fun confirm(packet: ClientboundEntityEventPacket) {
        if (!running) {
            return
        }
        val animal = packet.getEntity(world) as? Animal ?: return
        if (animal.type in animals && confirmed.add(animal.uuid)) {
            skip(animal.uuid, COOLDOWN)
        }
    }

    private fun skip(animal: UUID, ticks: Int) {
        skipped.put(animal, eventListenerScope.launch {
            waitTicks(ticks)
            skipped.remove(animal)
            confirmed.remove(animal)
        })?.cancel()
    }

    private fun hasPartner(animal: Animal, nearby: List<Animal>) =
        nearby.any { BreedingRules.arePartners(animal, it) && animal.distanceTo(it) < PARTNER_RANGE }

    // Pandas only mate with bamboo around, see Panda.PandaBreedGoal
    private fun Panda.seesBamboo(): Boolean {
        val pos = blockPosition()
        return BlockPos.betweenClosed(pos.offset(-7, 0, -7), pos.offset(7, 2, 7))
            .any { world.getBlockState(it).`is`(Blocks.BAMBOO) }
    }

    private fun aimedAt(animal: Animal, reach: Double) = isLookingAtEntity(
        toEntity = animal,
        rotation = RotationManager.serverRotation,
        range = reach,
        throughWallsRange = 0.0,
    )

}
