package net.ccbluex.liquidbounce.extras.modules.extras

import kotlinx.coroutines.launch
import net.ccbluex.liquidbounce.event.eventListenerScope
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.extras.util.coordinates
import net.ccbluex.liquidbounce.extras.util.report
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.item.BlockItem
import net.minecraft.world.level.block.SignBlock
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps trap bait out of your world: spawn packets of the selected entities near you are dropped, and a sign
 * editor the server opens without you placing or clicking that sign never shows, or closes when it already does.
 */
object ModuleAntiTrap : ClientModule("AntiTrap", ExtrasCategories.EXTRAS) {

    // How long the server may take to answer a sign click with its editor
    private const val EDITOR_WINDOW = 40

    private val range by float("Range", 16f, 1f..64f, "blocks")
    private val armorStands by boolean("ArmorStands", true)
    private val minecarts by boolean("Minecarts", true)
    private val itemFrames by boolean("ItemFrames", true)
    private val boats by boolean("Boats", true)
    private val endCrystals by boolean("EndCrystals", true)
    private val removeLoaded by boolean("RemoveLoaded", true)
    private val refuseSignEditors by boolean("RefuseSignEditors", true)

    /** Ids of the entities kept out of the world. */
    val keptOut: Set<Int>
        field = ConcurrentHashMap.newKeySet<Int>()

    /** Signs whose editor may open, counted per click on them. */
    private val expectedEditors = ConcurrentHashMap<BlockPos, Int>()

    /** The sign whose editor was last let through for a click on it. */
    @Volatile
    private var askedEditor: BlockPos? = null

    // Spawn and editor packets arrive on the network thread
    @Suppress("unused")
    private val packetHandler = handler<PacketEvent>(EventPriorityConvention.FIRST_PRIORITY) { event ->
        when (val packet = event.packet) {
            is ClientboundAddEntityPacket ->
                if (isBait(packet.type) && isNear(packet.x, packet.y, packet.z)) {
                    keptOut += packet.id
                    event.cancelEvent()
                }

            is ClientboundOpenSignEditorPacket ->
                if (expectedEditors.remove(packet.pos) != null) {
                    askedEditor = packet.pos
                } else if (refuseSignEditors) {
                    event.cancelEvent()
                    mc.execute { report("refusedEditor", packet.pos.coordinates) }
                }

            is ServerboundUseItemOnPacket -> expectEditor(packet)
        }
    }

    @Suppress("unused")
    private val removeHandler = handler<GameTickEvent> {
        if (!removeLoaded) {
            return@handler
        }
        val loaded = world.entitiesForRendering().filter { isBait(it.type) && isNear(it.x, it.y, it.z) }
        for (entity in loaded) {
            keptOut += entity.id
            world.removeEntity(entity.id, Entity.RemovalReason.DISCARDED)
        }
    }

    // An editor already open when the module or the setting came on never passed the packet check
    @Suppress("unused")
    private val openEditorHandler = handler<GameTickEvent> {
        val editor = mc.gui.screen() as? AbstractSignEditScreen ?: return@handler
        val sign = editor.sign.blockPos
        if (refuseSignEditors && sign != askedEditor) {
            report("closedEditor", sign.coordinates)
            editor.onClose()
        }
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> { clear() }

    override fun onDisabled() = clear()

    private fun clear() {
        keptOut.clear()
        expectedEditors.clear()
        askedEditor = null
    }

    /**
     * Placing a sign or clicking one is what makes the server open its editor. The packet leaves after the
     * client predicted the click, so a placed sign already stands in the clicked block (grass, snow) or against
     * the clicked face.
     */
    private fun expectEditor(packet: ServerboundUseItemOnPacket) {
        val clicked = packet.hitResult.blockPos
        val signs = listOf(clicked, clicked.relative(packet.hitResult.direction))
            .filter { world.getBlockState(it).block is SignBlock }
        if (signs.isEmpty()) {
            return
        }
        signs.forEach { expectedEditors.merge(it, 1, Int::plus) }
        eventListenerScope.launch {
            waitTicks(EDITOR_WINDOW)
            for (sign in signs) {
                expectedEditors.computeIfPresent(sign) { _, clicks -> (clicks - 1).takeIf { it > 0 } }
            }
        }
    }

    private fun isNear(x: Double, y: Double, z: Double): Boolean {
        val player = mc.player ?: return false
        return player.distanceToSqr(x, y, z) <= range * range
    }

    // Entity types carry no class to test against, but every boat, raft and minecart is named as one
    private fun isBait(type: EntityType<*>): Boolean {
        val name = BuiltInRegistries.ENTITY_TYPE.getKey(type).path
        return when {
            type == EntityTypes.ARMOR_STAND -> armorStands
            name.endsWith("minecart") -> minecarts
            type == EntityTypes.ITEM_FRAME || type == EntityTypes.GLOW_ITEM_FRAME -> itemFrames
            name.endsWith("_boat") || name.endsWith("_raft") -> boats
            type == EntityTypes.END_CRYSTAL -> endCrystals
            else -> false
        }
    }

}
