package net.ccbluex.liquidbounce.extras.modules.basehunting

import net.ccbluex.liquidbounce.event.events.ChunkLoadEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.extras.util.coordinates
import net.ccbluex.liquidbounce.extras.util.report
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.entity.BlockEntityTypes
import net.minecraft.world.level.chunk.LevelChunk

object ModuleStashFinder : ClientModule("StashFinder", ExtrasCategories.BASE_HUNTING) {

    private val minimum by int("Minimum", 4, 1..64, "containers")
    private val notify by boolean("Notify", true)

    val stashes: Map<ChunkPos, Int>
        field = HashMap<ChunkPos, Int>()

    private val storage: Set<BlockEntityType<*>> = [
        BlockEntityTypes.CHEST, BlockEntityTypes.TRAPPED_CHEST, BlockEntityTypes.BARREL,
        BlockEntityTypes.SHULKER_BOX, BlockEntityTypes.ENDER_CHEST, BlockEntityTypes.FURNACE,
        BlockEntityTypes.BLAST_FURNACE, BlockEntityTypes.SMOKER, BlockEntityTypes.DISPENSER,
        BlockEntityTypes.DROPPER, BlockEntityTypes.HOPPER,
    ]

    // Trial chambers are full of containers. Telling them apart by what the containers stand on comes from
    // Meteor Client's StashFinder, see README Credits.
    private val trialChamberSupport: Set<Block> = [
        Blocks.COPPER_BLOCK.weathering().oxidized(), Blocks.CUT_COPPER.weathering().oxidized(),
        Blocks.COPPER_BLOCK.waxed().unaffected(), Blocks.COPPER_BLOCK.waxed().oxidized(),
        Blocks.CUT_COPPER.waxed().oxidized(), Blocks.COPPER_BULB.waxed().unaffected(),
        Blocks.TUFF_BRICKS, Blocks.BARREL,
    ]

    @Suppress("unused")
    private val chunkLoadHandler = handler<ChunkLoadEvent> { event ->
        val chunk = world.getChunk(event.x, event.z)
        val containers = chunk.blockEntities.values.count { chunk.isStash(it) }
        if (containers >= minimum && stashes.put(chunk.pos, containers) != containers) {
            report("found", containers, chunk.pos.coordinates, notify = notify)
        }
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> { stashes.clear() }

    override fun onDisabled() = stashes.clear()

    private fun LevelChunk.isStash(entity: BlockEntity) =
        entity.type in storage && getBlockState(entity.blockPos.below()).block !in trialChamberSupport

}
