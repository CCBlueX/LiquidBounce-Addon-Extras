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
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.ShulkerBoxBlock

/** Chunks with many containers, counted by block type and followed as they change. */
object ModuleStashFinder : ClientModule("StashFinder", ExtrasCategories.BASE_HUNTING) {

    private val minimum by int("Minimum", 4, 1..64, "containers")
    private val notify by boolean("Notify", true)

    private val containers by blocks(
        "Containers",
        linkedSetOf(
            Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL, Blocks.ENDER_CHEST, Blocks.FURNACE,
            Blocks.BLAST_FURNACE, Blocks.SMOKER, Blocks.DISPENSER, Blocks.DROPPER, Blocks.HOPPER,
        ).apply { addAll(BuiltInRegistries.BLOCK.filterIsInstance<ShulkerBoxBlock>()) },
    ).onChanged { rescan() }

    // Trial chambers are full of containers. Telling them apart by what the containers stand on comes from
    // Meteor Client's StashFinder, see README Credits.
    private val excludedSupports by blocks(
        "ExcludedSupports",
        linkedSetOf(
            Blocks.COPPER_BLOCK.weathering().oxidized(), Blocks.CUT_COPPER.weathering().oxidized(),
            Blocks.COPPER_BLOCK.waxed().unaffected(), Blocks.COPPER_BLOCK.waxed().oxidized(),
            Blocks.CUT_COPPER.waxed().oxidized(), Blocks.COPPER_BULB.waxed().unaffected(),
            Blocks.TUFF_BRICKS, Blocks.BARREL,
        ),
    )

    /** Containers by block of the chunks found in this world, until a loaded one has too few. */
    val stashes: Map<ChunkPos, Map<String, Int>>
        field = HashMap<ChunkPos, Map<String, Int>>()

    private val tracker = BlockTracker { state -> state.block.takeIf { it in containers } }

    @Suppress("unused")
    private val inspectHandler = tickHandler {
        inspectChunks()
        waitTicks(20)
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> { stashes.clear() }

    override fun onEnabled() = ChunkScanner.subscribe(tracker)

    override fun onDisabled() {
        ChunkScanner.unsubscribe(tracker)
        stashes.clear()
    }

    private fun inspectChunks() {
        val found = tracker.iterate()
            .filter { (pos, _) -> world.getBlockState(pos.below()).block !in excludedSupports }
            .groupBy { (pos, _) -> ChunkPos.containing(pos) }
            .filterValues { it.size >= minimum }

        for ((chunk, blocks) in found) {
            val counts = blocks.groupingBy { BuiltInRegistries.BLOCK.getKey(it.value).toString() }.eachCount()
            if (stashes.put(chunk, counts) != counts) {
                report("found", blocks.size, chunk.coordinates, notify = notify)
            }
        }
        stashes.keys.removeIf { it !in found && world.hasChunk(it.x, it.z) }
    }

    private fun rescan() {
        if (running) {
            ChunkScanner.unsubscribe(tracker)
            ChunkScanner.subscribe(tracker)
        }
    }

}
