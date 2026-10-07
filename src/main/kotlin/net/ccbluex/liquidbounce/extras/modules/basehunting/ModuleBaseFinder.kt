package net.ccbluex.liquidbounce.extras.modules.basehunting

import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.extras.util.BlockTracker
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
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.SignBlock
import net.minecraft.world.level.block.entity.SignBlockEntity
import net.minecraft.world.level.block.entity.SignTextSlot

/** Chunks with the blocks and entities players leave behind where they live. */
object ModuleBaseFinder : ClientModule("BaseFinder", ExtrasCategories.BASE_HUNTING) {

    private val landmarks by boolean("Landmarks", true)
    private val workstations by int("Workstations", 4, 1..32, "blocks")
    private val entities by int("Entities", 2, 1..16, "entities")
    private val writtenSigns by boolean("WrittenSigns", true)
    private val developedVillagers by boolean("DevelopedVillagers", true)
    private val notify by boolean("Notify", true)

    // Never part of generated structures, apart from the ender chest on an End city floor
    private val landmarkBlocks by blocks(
        "LandmarkBlocks",
        linkedSetOf(
            Blocks.BEACON, Blocks.ENCHANTING_TABLE, Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.RESPAWN_ANCHOR,
            Blocks.LODESTONE, Blocks.CONDUIT, Blocks.CRAFTER, Blocks.ENDER_CHEST,
        ),
    ).onChanged { rescan() }

    // Villages have these too, trail ruins and mansions a damaged anvil, hence the threshold
    private val workstationBlocks by blocks(
        "WorkstationBlocks",
        linkedSetOf(
            Blocks.CRAFTING_TABLE, Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER, Blocks.BREWING_STAND,
            Blocks.SMITHING_TABLE, Blocks.CARTOGRAPHY_TABLE, Blocks.FLETCHING_TABLE, Blocks.LOOM,
            Blocks.STONECUTTER, Blocks.GRINDSTONE, Blocks.DAMAGED_ANVIL,
        ),
    ).onChanged { rescan() }

    /** Evidence by kind of the chunks found in this world, until a loaded one stops showing it. */
    val bases: Map<ChunkPos, Map<String, Int>>
        field = HashMap<ChunkPos, Map<String, Int>>()

    private val tracker = BlockTracker { state ->
        state.block.takeIf { it in landmarkBlocks || it in workstationBlocks || it is SignBlock }
    }

    @Suppress("unused")
    private val inspectHandler = tickHandler {
        inspectChunks()
        waitTicks(20)
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> { bases.clear() }

    override fun onEnabled() = ChunkScanner.subscribe(tracker)

    override fun onDisabled() {
        ChunkScanner.unsubscribe(tracker)
        bases.clear()
    }

    private fun describe(evidence: Map<String, Int>) =
        evidence.entries.joinToString { (kind, count) -> message("evidence.$kind", count).string }

    private fun inspectChunks() {
        val clues = clues().groupBy { (pos, _) -> ChunkPos.containing(pos) }
        val found = clues.mapValues { (_, chunkClues) ->
            chunkClues.groupingBy { (_, kind) -> kind }.eachCount().filter { (kind, count) -> count >= minimum(kind) }
        }.filterValues { it.isNotEmpty() }

        for ((chunk, evidence) in found) {
            if (bases.put(chunk, evidence) != evidence) {
                report("found", chunk.coordinates, describe(evidence), notify = notify)
            }
        }
        bases.keys.removeIf { it !in found && world.hasChunk(it.x, it.z) }
    }

    private fun clues() = buildList {
        for ((pos, block) in tracker.iterate()) {
            val kind = when {
                landmarks && block in landmarkBlocks -> "landmark"
                block in workstationBlocks -> "workstations"
                writtenSigns && block is SignBlock && isWritten(pos) -> "signs"
                else -> continue
            }
            add(pos to kind)
        }
        for (entity in world.entitiesForRendering()) {
            val kind = when {
                developedVillagers && entity is Villager && entity.villagerData.level > 1 -> "villagers"
                isPlaced(entity) -> "entities"
                else -> continue
            }
            add(entity.blockPosition() to kind)
        }
    }

    private fun minimum(kind: String) = when (kind) {
        "workstations" -> workstations
        "entities" -> entities
        else -> 1
    }

    // Written signs and traded villagers as evidence come from Trouser-Streak's BaseFinder, see README Credits.
    private fun isWritten(pos: BlockPos): Boolean {
        val sign = world.getBlockEntity(pos) as? SignBlockEntity ?: return false
        return SignTextSlot.entries.any { sign.getText(it).hasMessage(false) }
    }

    private fun isPlaced(entity: Entity) = when (entity) {
        is Player -> false
        // End ships carry an elytra in an item frame
        is ItemFrame -> !entity.item.isEmpty && !(world.dimension() == Level.END && entity.item.`is`(Items.ELYTRA))
        is ArmorStand, is AbstractBoat -> true
        else -> entity.hasCustomName()
    }

    private fun rescan() {
        if (running) {
            ChunkScanner.unsubscribe(tracker)
            ChunkScanner.subscribe(tracker)
        }
    }

}
