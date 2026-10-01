package net.ccbluex.liquidbounce.extras.util

import net.ccbluex.liquidbounce.utils.block.AbstractBlockLocationTracker
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk

/**
 * Follows every block [classify] gives a value to, fed by the client's `ChunkScanner`. [classify] runs on
 * the scanner's worker threads, so it must only look at the state it is given.
 */
class BlockTracker<T : Any>(private val classify: (BlockState) -> T?) :
    AbstractBlockLocationTracker.BlockPos2State<T>() {

    override val shouldCallRecordBlockOnChunkUpdate get() = false

    // findBlocks skips whole sections whose palette cannot contain a match
    override fun chunkUpdate(chunk: LevelChunk) =
        chunk.findBlocks({ classify(it) != null }) { pos, state -> track(pos, classify(state)!!) }

    override fun getStateFor(pos: BlockPos, state: BlockState) = classify(state)

}
