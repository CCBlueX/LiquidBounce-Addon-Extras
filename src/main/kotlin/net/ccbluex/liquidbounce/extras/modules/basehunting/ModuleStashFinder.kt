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
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.ShulkerBoxBlock

object ModuleStashFinder : ClientModule("StashFinder", ExtrasCategories.BASE_HUNTING) {
    private val minimum by int("Minimum", 4, 1..64, "containers")
    private val notify by boolean("Notify", true)

    private val storageBlocks = linkedSetOf(
        Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL, Blocks.ENDER_CHEST, Blocks.FURNACE,
        Blocks.BLAST_FURNACE, Blocks.SMOKER, Blocks.DISPENSER, Blocks.DROPPER, Blocks.HOPPER,
    ).apply { addAll(BuiltInRegistries.BLOCK.filterIsInstance<ShulkerBoxBlock>()) }
    private val containers by blocks("Containers", storageBlocks.toCollection(linkedSetOf()))

    // Trial-chamber support filter from Meteor Client, see README Credits.
    private val excludedSupports by blocks("ExcludedSupports", linkedSetOf(
        Blocks.COPPER_BLOCK.weathering().oxidized(), Blocks.CUT_COPPER.weathering().oxidized(),
        Blocks.COPPER_BLOCK.waxed().unaffected(), Blocks.COPPER_BLOCK.waxed().oxidized(),
        Blocks.CUT_COPPER.waxed().oxidized(), Blocks.COPPER_BULB.waxed().unaffected(),
        Blocks.TUFF_BRICKS, Blocks.BARREL,
    ))
    @Volatile
    private var watched = containers.toSet()
    private val tracker = BlockTracker { state -> state.block.takeIf { it in watched } }

    val stashes: Map<ChunkPos, Int>
        field = HashMap<ChunkPos, Int>()
    val counts: Map<ChunkPos, Map<String, Int>>
        field = HashMap<ChunkPos, Map<String, Int>>()

    @Suppress("unused")
    private val scanHandler = tickHandler {
        if (watched != containers) {
            ChunkScanner.unsubscribe(tracker)
            watched = containers.toSet()
            ChunkScanner.subscribe(tracker)
        }
        if (!WorldJournal.loading) scan()
        waitTicks(10)
    }

    private fun scan() {
        val matches = tracker.iterate().map { it.key.immutable() to it.value }
            .filter { (pos, block) ->
                block in containers && world.hasChunkAt(pos) && world.getBlockState(pos).block === block &&
                    world.getBlockState(pos.below()).block !in excludedSupports
            }.groupBy { (pos, _) -> ChunkPos.containing(pos) }
        val current = matches.filterValues { it.size >= minimum }
        stashes.keys.retainAll(current.keys)
        counts.keys.retainAll(current.keys)
        for ((chunk, blocks) in current) {
            val breakdown = blocks.groupingBy { (_, block) -> BuiltInRegistries.BLOCK.getKey(block).toString() }
                .eachCount()
            if (counts[chunk] != breakdown) {
                val position = blocks.minBy { (pos, _) -> pos.asLong() }.first
                if (!WorldJournal.ready || WorldJournal.record("stash", chunk, position, breakdown)) {
                    report("found", blocks.size, chunk.coordinates, notify = notify)
                }
            }
            stashes[chunk] = blocks.size
            counts[chunk] = breakdown
        }
    }

    @Suppress("unused")
    private val worldHandler = WorldEvents.handler<WorldChangeEvent> { event ->
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

    private fun clear() {
        stashes.clear()
        counts.clear()
    }
}
