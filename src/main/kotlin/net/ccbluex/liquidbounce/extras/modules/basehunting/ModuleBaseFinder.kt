package net.ccbluex.liquidbounce.extras.modules.basehunting

import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.extras.util.WorldEvents
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.extras.util.BlockTracker
import net.ccbluex.liquidbounce.extras.util.WorldJournal
import net.ccbluex.liquidbounce.extras.util.coordinates
import net.ccbluex.liquidbounce.extras.util.report
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.block.ChunkScanner
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.entity.npc.villager.Villager
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.vehicle.boat.AbstractBoat
import net.minecraft.world.item.Items
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.SignBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.SignBlockEntity
import net.minecraft.world.level.block.entity.SignTextSlot

object ModuleBaseFinder : ClientModule("BaseFinder", ExtrasCategories.BASE_HUNTING) {
    private val landmarks by boolean("Landmarks", true)
    private val workstations by int("Workstations", 4, 1..32, "blocks")
    private val entities by int("Entities", 2, 1..16, "entities")
    private val notify by boolean("Notify", true)
    private val writtenSigns by boolean("WrittenSigns", true)
    private val developedVillagers by boolean("DevelopedVillagers", true)

    // Anvils and lodestones can generate in structures.
    private val landmarkBlocks by blocks("LandmarkBlocks", linkedSetOf(
        Blocks.BEACON, Blocks.ENCHANTING_TABLE, Blocks.RESPAWN_ANCHOR, Blocks.CONDUIT, Blocks.ENDER_CHEST,
    ))
    private val workstationBlocks by blocks("WorkstationBlocks", linkedSetOf(
        Blocks.CRAFTING_TABLE, Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER, Blocks.BREWING_STAND,
        Blocks.SMITHING_TABLE, Blocks.CARTOGRAPHY_TABLE, Blocks.FLETCHING_TABLE, Blocks.LOOM,
        Blocks.STONECUTTER, Blocks.GRINDSTONE, Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL,
    ))

    @Volatile
    private var watched = (landmarkBlocks + workstationBlocks).toSet()
    private val tracker = BlockTracker { state ->
        state.block.takeIf { it in watched || it is SignBlock }
    }

    val bases: Set<ChunkPos>
        field = HashSet<ChunkPos>()
    val evidence: Map<ChunkPos, Map<String, Int>>
        field = HashMap<ChunkPos, Map<String, Int>>()

    @Suppress("unused")
    private val inspectHandler = tickHandler {
        val blocks = (landmarkBlocks + workstationBlocks).toSet()
        if (blocks != watched) {
            ChunkScanner.unsubscribe(tracker)
            watched = blocks
            ChunkScanner.subscribe(tracker)
        }
        if (!WorldJournal.loading) inspectChunks()
        waitTicks(20)
    }

    @Suppress("unused")
    private val worldChangeHandler = WorldEvents.handler<WorldChangeEvent> { event ->
        clear()
        if (event.world == null) onDisabled() else if (enabled) onEnabled()
    }

    override fun onEnabled() {
        ChunkScanner.unsubscribe(tracker)
        ChunkScanner.subscribe(tracker)
    }

    override fun onDisabled() {
        ChunkScanner.unsubscribe(tracker)
        clear()
    }

    private fun inspectChunks() {
        val observations = observations()
        val current = observations.mapValues { (_, entries) ->
            entries.groupingBy { it.second }.eachCount().filter { (reason, count) ->
                when (reason) {
                    "workstations" -> count >= workstations
                    "entities" -> count >= entities
                    else -> true
                }
            }
        }.filterValues { it.isNotEmpty() }
        bases.retainAll(current.keys)
        evidence.keys.retainAll(current.keys)
        for ((chunk, reasons) in current) {
            if (evidence[chunk] != reasons) {
                val pos = observations.getValue(chunk).first { it.second in reasons }.first
                if (!WorldJournal.ready || WorldJournal.record("base", chunk, pos, reasons)) {
                    report("found", chunk.coordinates, reasons.entries.joinToString { (reason, count) ->
                        message("evidence.$reason", count).string
                    }, notify = notify)
                }
            }
            bases.add(chunk)
            evidence[chunk] = reasons
        }
    }

    private fun observations(): Map<ChunkPos, List<Pair<BlockPos, String>>> {
        val entries = arrayListOf<Pair<BlockPos, String>>()
        for ((pos, block) in tracker.iterate()) {
            if (!world.hasChunkAt(pos) || world.getBlockState(pos).block !== block) continue
            val reason = when {
                landmarks && block in landmarkBlocks -> "landmark"
                block in workstationBlocks -> "workstations"
                writtenSigns && block is SignBlock && isWritten(pos) -> "signs"
                else -> continue
            }
            entries.add(pos.immutable() to reason)
        }
        for (entity in world.entitiesForRendering()) {
            val reason = when {
                developedVillagers && entity is Villager && entity.villagerData.level > 1 -> "villagers"
                isPlaced(entity) -> "entities"
                else -> continue
            }
            entries.add(entity.blockPosition().immutable() to reason)
        }
        return entries.groupBy { (pos, _) -> ChunkPos.containing(pos) }
    }

    // Written signs and developed villagers as evidence: Trouser-Streak BaseFinder, see README Credits.
    private fun isWritten(pos: BlockPos): Boolean {
        val sign = world.getBlockEntity(pos) as? SignBlockEntity ?: return false
        return SignTextSlot.entries.any { side -> sign.getText(side).getMessages(false).any { it.string.isNotBlank() } }
    }

    private fun clear() {
        bases.clear()
        evidence.clear()
    }

    private fun isPlaced(entity: Entity) = when (entity) {
        is Player -> false
        is ItemFrame -> !entity.item.isEmpty && !(world.dimension() == Level.END && entity.item.`is`(Items.ELYTRA))
        is ArmorStand, is AbstractBoat -> true
        else -> entity.hasCustomName()
    }
}
