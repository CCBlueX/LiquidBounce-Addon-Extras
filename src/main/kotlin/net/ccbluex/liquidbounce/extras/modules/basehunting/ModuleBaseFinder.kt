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
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.vehicle.boat.AbstractBoat
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks

/** Chunks with the blocks and entities players leave behind where they live. */
object ModuleBaseFinder : ClientModule("BaseFinder", ExtrasCategories.BASE_HUNTING) {

    private val landmarks by boolean("Landmarks", true)
    private val workstations by int("Workstations", 4, 1..32, "blocks")
    private val entities by int("Entities", 2, 1..16, "entities")
    private val notify by boolean("Notify", true)

    val bases: Set<ChunkPos>
        field = HashSet<ChunkPos>()

    private enum class Evidence { LANDMARK, WORKSTATION }

    // Never part of generated structures
    private val landmarkBlocks: Set<Block> = [
        Blocks.BEACON, Blocks.ENCHANTING_TABLE, Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL,
        Blocks.RESPAWN_ANCHOR, Blocks.LODESTONE, Blocks.CONDUIT, Blocks.CRAFTER, Blocks.ENDER_CHEST,
    ]

    // Villages have these too, hence the threshold
    private val workstationBlocks: Set<Block> = [
        Blocks.CRAFTING_TABLE, Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER, Blocks.BREWING_STAND,
        Blocks.SMITHING_TABLE, Blocks.CARTOGRAPHY_TABLE, Blocks.FLETCHING_TABLE, Blocks.LOOM,
        Blocks.STONECUTTER, Blocks.GRINDSTONE,
    ]

    private val tracker = BlockTracker { state ->
        when (state.block) {
            in landmarkBlocks -> Evidence.LANDMARK
            in workstationBlocks -> Evidence.WORKSTATION
            else -> null
        }
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

    private fun inspectChunks() {
        val blocks = tracker.iterate()
            .groupBy({ (pos, _) -> ChunkPos.containing(pos) }, { (_, evidence) -> evidence })
        val placedEntities = world.entitiesForRendering()
            .filter(::isPlaced)
            .groupingBy(Entity::chunkPosition)
            .eachCount()

        for (chunk in blocks.keys + placedEntities.keys) {
            if (chunk in bases) {
                continue
            }

            val evidence = blocks[chunk].orEmpty()
            val workstationCount = evidence.count { it == Evidence.WORKSTATION }
            val entityCount = placedEntities[chunk] ?: 0
            when {
                landmarks && Evidence.LANDMARK in evidence -> report("landmark", chunk.coordinates, notify = notify)
                workstationCount >= workstations ->
                    report("workstations", chunk.coordinates, workstationCount, notify = notify)
                entityCount >= entities -> report("entities", chunk.coordinates, entityCount, notify = notify)
                else -> continue
            }
            bases += chunk
        }
    }

    private fun isPlaced(entity: Entity) = when (entity) {
        is Player -> false
        is ItemFrame -> !entity.item.isEmpty
        is ArmorStand, is AbstractBoat -> true
        else -> entity.hasCustomName()
    }

}
