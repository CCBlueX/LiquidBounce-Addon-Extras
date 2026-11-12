package net.ccbluex.liquidbounce.extras.modules.basehunting

import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.extras.util.coordinates
import net.ccbluex.liquidbounce.extras.util.report
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.block.ChunkScanner
import net.minecraft.core.BlockPos
import net.minecraft.util.Util
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.LadderBlock
import net.minecraft.world.level.block.TrapDoorBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import java.util.concurrent.ConcurrentHashMap

/**
 * Blocks a player just placed nearby. Everything a chunk brings along when it loads counts as old, and
 * so does everything placed while it is still settling in.
 */
object ModuleSuspiciousBlockDetector : ClientModule("SuspiciousBlockDetector", ExtrasCategories.BASE_HUNTING) {

    private const val MILLIS_PER_TICK = 50L

    private val radius by int("Radius", 64, 8..128, "blocks")
    private val settleTicks by int("SettleTicks", 40, 5..200, "ticks")
    private val notify by boolean("Notify", true)

    val findings: Set<BlockPos>
        field = LinkedHashSet<BlockPos>()

    /** Whether [pos] was scanned and its chunk has settled in, so that what is placed there now gets reported. */
    fun isSettled(pos: BlockPos) = Watcher.isSettled(pos)

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> { findings.clear() }

    override fun onEnabled() = ChunkScanner.subscribe(Watcher)

    override fun onDisabled() {
        ChunkScanner.unsubscribe(Watcher)
        findings.clear()
    }

    private fun placed(pos: BlockPos, block: Block) {
        if (running && pos.distToCenterSqr(player.position()) <= radius * radius && findings.add(pos)) {
            report("placed", block.name.string, pos.coordinates, notify = notify)
        }
    }

    private fun isSuspicious(block: Block) = when (block) {
        is DoorBlock, is TrapDoorBlock, is ChestBlock, is LadderBlock, is BedBlock -> true
        else -> block === Blocks.OBSIDIAN || block === Blocks.IRON_BLOCK || block === Blocks.GOLD_BLOCK
    }

    private object Watcher : ChunkScanner.BlockChangeSubscriber {

        private val known = ConcurrentHashMap<BlockPos, Block>()

        /**
         * By [Util.getMillis], when each chunk that loaded lately has settled in. A clock and no coroutine: chunks load
         * on the scanner's threads, and a coroutine that starts before the module counts as running is cancelled.
         */
        private val settledAt = ConcurrentHashMap<ChunkPos, Long>()

        override val shouldCallRecordBlockOnChunkUpdate get() = false

        override fun chunkUpdate(chunk: LevelChunk) {
            settle(chunk.pos)
            chunk.findBlocks({ isSuspicious(it.block) }) { pos, state -> known[pos.immutable()] = state.block }
        }

        override fun recordBlock(pos: BlockPos, state: BlockState, cleared: Boolean) {
            val block = state.block
            val previous = if (isSuspicious(block)) known.put(pos.immutable(), block) else known.remove(pos)
            if (isSuspicious(block) && previous !== block && !isSettling(ChunkPos.containing(pos))) {
                val placedAt = pos.immutable()
                mc.execute { placed(placedAt, block) }
            }
        }

        override fun clearChunk(pos: ChunkPos) {
            settledAt.remove(pos)
            known.keys.removeIf(pos::contains)
        }

        override fun clearAllChunks() {
            settledAt.clear()
            known.clear()
        }

        fun isSettled(pos: BlockPos) = known.containsKey(pos) && !isSettling(ChunkPos.containing(pos))

        private fun isSettling(chunk: ChunkPos) = settledAt[chunk]?.let { Util.getMillis() < it } == true

        private fun settle(chunk: ChunkPos) {
            settledAt[chunk] = Util.getMillis() + settleTicks * MILLIS_PER_TICK
        }

    }

}
