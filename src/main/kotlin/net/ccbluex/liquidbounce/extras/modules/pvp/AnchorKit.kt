package net.ccbluex.liquidbounce.extras.modules.pvp

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.eventListenerScope
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.RespawnAnchorBlock
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult

/**
 * What the anchor modules remember together: anchors that exploded or that we placed, for a few seconds, so
 * nothing acts on a block the server already took away, and who holds manual clicks on anchors back.
 */
object AnchorKit : EventListener {

    private const val SPENT_TICKS = 40
    private const val PLACED_TICKS = 200

    /** Anchors that exploded or vanished a moment ago; the client may still show them. */
    val spent: Set<BlockPos>
        field = HashSet<BlockPos>()

    /** Anchors this client placed in the last few seconds. */
    val placed: Set<BlockPos>
        field = HashSet<BlockPos>()

    // One timer per set: an anchor placed again where one just exploded must not keep the spot spent forever
    private val placedExpiries = HashMap<BlockPos, Job>()
    private val spentExpiries = HashMap<BlockPos, Job>()
    private val guards = HashSet<Any>()

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        placedExpiries.values.forEach(Job::cancel)
        spentExpiries.values.forEach(Job::cancel)
        placedExpiries.clear()
        spentExpiries.clear()
        placed.clear()
        spent.clear()
    }

    fun rememberPlaced(pos: BlockPos) = remember(pos, placed, placedExpiries, PLACED_TICKS)

    fun spend(pos: BlockPos) {
        placed.remove(pos)
        placedExpiries.remove(pos)?.cancel()
        remember(pos, spent, spentExpiries, SPENT_TICKS)
    }

    /** Moves own anchors that are gone, by explosion, mining or a refused placement, to [spent]. */
    fun forgetCleared() {
        placed.filter { !it.isAnchor() }.forEach(::spend)
    }

    private fun remember(
        pos: BlockPos,
        positions: MutableSet<BlockPos>,
        expiries: MutableMap<BlockPos, Job>,
        ticks: Int,
    ) {
        positions += pos
        expiries.put(pos, eventListenerScope.launch {
            waitTicks(ticks)
            positions.remove(pos)
            expiries.remove(pos)
        })?.cancel()
    }

    /** Holds manual clicks on anchors back while [block] runs, so no extra glowstone or early blast gets in. */
    suspend fun <T> guard(owner: Any, enabled: Boolean, block: suspend () -> T): T {
        if (!enabled) {
            return block()
        }
        guards += owner
        try {
            return block()
        } finally {
            guards -= owner
        }
    }

    /** Asked by the game before it uses an item on what the crosshair shows. */
    @JvmStatic
    fun blocksUse(hit: HitResult?): Boolean {
        val level = mc.level
        if (hit !is BlockHitResult || hit.type != HitResult.Type.BLOCK || level == null) {
            return false
        }
        return level.getBlockState(hit.blockPos).block is RespawnAnchorBlock &&
            (guards.isNotEmpty() || ModuleBlastCover.intercepts(hit.blockPos))
    }

}
