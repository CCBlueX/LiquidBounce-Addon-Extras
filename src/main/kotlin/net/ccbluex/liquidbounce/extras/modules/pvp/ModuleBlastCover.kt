package net.ccbluex.liquidbounce.extras.modules.pvp

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.eventListenerScope
import net.ccbluex.liquidbounce.event.tickConditional
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.tickUntil
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationsValueGroup
import net.ccbluex.liquidbounce.utils.block.isInteractable
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.raytracing.traceFromPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.RespawnAnchorBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.HitResult

/**
 * Puts obsidian or glowstone between you and an anchor before it goes off: before your own click on a charged
 * anchor, and before AnchorAura or AnchorMacro set one off. Those wait while a cover is pending. With the Before
 * charge timing, the cover already goes up before the anchor is charged. Only blasts within the damage window
 * get a cover, and each anchor at most Limit of them.
 */
object ModuleBlastCover : ClientModule("BlastCover", ExtrasCategories.PVP) {

    // A cover that could not go up lets the next click through instead of holding it forever
    private const val SKIP_TICKS = 40

    private val mode by enumChoice("Mode", Mode.ROTATE)
    private val material by enumChoice("Block", Material.OBSIDIAN)
    private val timing by enumChoice("Timing", Timing.AFTER_CHARGE)
    private val minDamage by float("MinDamage", 2f, 0f..20f, "HP")
    private val maxDamage by float("MaxDamage", 100f, 0f..100f, "HP")
    private val limit by int("Limit", 0, 0..10, "covers")
    private val reach by float("Reach", 4.5f, 1f..6f, "blocks")
    private val timeout by int("Timeout", 20, 5..60, "ticks")
    private val rotations = tree(RotationsValueGroup(this))

    /** The anchor a cover goes up for. */
    var pending: BlockPos? = null
        private set

    private val skipped = HashMap<BlockPos, Job>()
    private val covers = HashMap<BlockPos, Int>()

    @Suppress("unused")
    private val coverHandler = tickHandler {
        covers.keys.removeIf { !it.isAnchor() }
        val anchor = pending ?: return@tickHandler
        val covered = when (mode) {
            Mode.ROTATE -> clickAimed(rotations, timeout, { coverFor(anchor)?.let { placeClick(it, range) } }) {
                use(it, coverSlot() ?: return@clickAimed)
            }
            Mode.CROSSHAIR -> placeOnCrosshair(anchor)
        }
        if (covered) {
            covers.merge(anchor, 1, Int::plus)
        } else if (coverFor(anchor) != null) {
            skip(anchor)
        }
        pending = null
    }

    private val range get() = reach.toDouble()

    /** Asked for a manual click on [anchor]; true holds it back until the cover is up. */
    fun intercepts(anchor: BlockPos): Boolean {
        if (!running || anchor in skipped) {
            return false
        }
        if (pending != null) {
            return true
        }
        val state = world.getBlockState(anchor)
        if (state.block !is RespawnAnchorBlock || !detonatesOnUse(state) && !chargesOnUse(state) ||
            coverFor(anchor) == null
        ) {
            return false
        }
        pending = anchor
        return true
    }

    /** With the Before charge timing, lets the anchor modules wait for a cover before they charge [anchor]. */
    suspend fun coverBeforeCharge(anchor: BlockPos) {
        if (timing == Timing.BEFORE_CHARGE) {
            coverBefore(anchor)
        }
    }

    /** Lets the anchor modules wait for a cover before they set [anchor] off. */
    suspend fun coverBefore(anchor: BlockPos) {
        if (!running || anchor in skipped || coverFor(anchor) == null) {
            return
        }
        pending = anchor
        tickUntil { pending != anchor || it > timeout * 2 }
    }

    private suspend fun placeOnCrosshair(anchor: BlockPos): Boolean {
        var placed = false
        tickConditional(timeout) {
            val cell = coverFor(anchor) ?: return@tickConditional true
            val hit = traceFromPlayer(RotationManager.serverRotation, range)
            val slot = coverSlot()
            if (slot != null && hit.type == HitResult.Type.BLOCK && placedAt(hit) == cell &&
                !world.getBlockState(hit.blockPos).isInteractable
            ) {
                use(hit, slot)
                placed = true
            }
            placed
        }
        return placed
    }

    /** Where a cover for [anchor] goes, or null when the blast would not hurt enough or nothing helps. */
    private fun coverFor(anchor: BlockPos): BlockPos? {
        if (coverSlot() == null) {
            return null
        }
        val open = player.anchorDamage(anchor)
        if (open !in minDamage..maxDamage || limit > 0 && (covers[anchor] ?: 0) >= limit) {
            return null
        }
        val (cell, covered) = bestCover(anchor, range) ?: return null
        return cell.takeIf { covered < open }
    }

    private fun coverSlot() = Slots.Hotbar.findSlot(material.item) ?: Slots.Hotbar.findSlot(material.other)

    private fun skip(anchor: BlockPos) {
        skipped.put(anchor, eventListenerScope.launch {
            waitTicks(SKIP_TICKS)
            skipped.remove(anchor)
        })?.cancel()
    }

    private fun chargesOnUse(state: BlockState) = timing == Timing.BEFORE_CHARGE &&
        state.getValue(RespawnAnchorBlock.CHARGE) < RespawnAnchorBlock.MAX_CHARGES &&
        (player.mainHandItem.`is`(Items.GLOWSTONE) || player.offhandItem.`is`(Items.GLOWSTONE))

    override fun onDisabled() {
        pending = null
        covers.clear()
        skipped.values.forEach(Job::cancel)
        skipped.clear()
    }

    private enum class Mode(override val tag: String) : Tagged {
        CROSSHAIR("Crosshair"),
        ROTATE("Rotate"),
    }

    private enum class Timing(override val tag: String) : Tagged {
        BEFORE_CHARGE("BeforeCharge"),
        AFTER_CHARGE("AfterCharge"),
    }

    private enum class Material(override val tag: String, val item: Item, val other: Item) : Tagged {
        OBSIDIAN("Obsidian", Items.OBSIDIAN, Items.GLOWSTONE),
        GLOWSTONE("Glowstone", Items.GLOWSTONE, Items.OBSIDIAN),
    }

}
