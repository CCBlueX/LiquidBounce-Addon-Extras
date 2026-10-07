package net.ccbluex.liquidbounce.extras.modules.grinding

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.eventListenerScope
import net.ccbluex.liquidbounce.event.tickConditional
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.extras.util.BlockTracker
import net.ccbluex.liquidbounce.utils.aiming.NormalRotationMode
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.utils.raytraceBlockRotation
import net.ccbluex.liquidbounce.utils.block.ChunkScanner
import net.ccbluex.liquidbounce.utils.entity.interactBlock
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.SignBlock
import net.minecraft.world.level.block.entity.SignBlockEntity
import net.minecraft.world.level.block.entity.SignTextSlot
import net.minecraft.world.phys.HitResult
import kotlin.math.min

private const val AIM_TIMEOUT = 20
private const val PAUSE = 20

// Long enough for the write to arrive, after which a sign that still needs text was refused
private const val RETRY = 200

/**
 * Opens the signs around you whose side facing you [ModuleAutoSign] would write, blank ones unless
 * `Overwrite` is on, and lets it write them.
 */
class SignAura(private val module: ModuleAutoSign) : ToggleableValueGroup(module, "Nearby", false) {

    private val range by float("Range", 3f, 1f..4.5f, "blocks")
    private val overwrite by boolean("Overwrite", false)
    private val rotationMode = modes(this, "RotationMode") { arrayOf(NormalRotationMode(it, module)) }

    private val signs = BlockTracker { state -> state.block as? SignBlock }
    private val opened = HashMap<Pair<BlockPos, SignTextSlot>, Job>()

    @Suppress("unused")
    private val openHandler = tickHandler {
        if (!canOpen()) {
            return@tickHandler
        }
        val reach = min(range.toDouble(), player.blockInteractionRange())
        val sign = signs.allPositions()
            .mapNotNull { world.getBlockEntity(it) as? SignBlockEntity }
            .filter(::needsText)
            .sortedBy { it.blockPos.distToCenterSqr(player.eyePosition) }
            .firstOrNull { raytraceBlockRotation(player.eyePosition, it.blockPos, it.blockState, reach, 0.0) != null }
            ?: return@tickHandler

        var interacted = false
        tickConditional(AIM_TIMEOUT) {
            if (!canOpen() || !needsText(sign)) {
                return@tickConditional true
            }
            val (rotation, _) = raytraceBlockRotation(player.eyePosition, sign.blockPos, sign.blockState, reach, 0.0)
                ?: return@tickConditional true
            rotationMode.activeMode.rotate(rotation, isFinished = { aimedAt(sign.blockPos, reach) != null }) {
                val hit = aimedAt(sign.blockPos, reach) ?: return@rotate
                remember(sign.blockPos to sign.getSlotPlayerIsFacing(player))
                val _ = interactBlock(hit)
                interacted = true
            }
            interacted
        }
        if (interacted) {
            waitTicks(PAUSE)
        }
    }

    override fun onEnabled() = ChunkScanner.subscribe(signs)

    override fun onDisabled() {
        ChunkScanner.unsubscribe(signs)
        opened.values.forEach(Job::cancel)
        opened.clear()
    }

    // An empty main hand opens the editor instead of dyeing, waxing or placing a hanging sign
    private fun canOpen() = mc.gui.screen() == null && player.mainHandItem.isEmpty && !player.isUsingItem &&
        !player.isShiftKeyDown && player.mayBuild()

    private fun needsText(sign: SignBlockEntity): Boolean {
        val side = sign.getSlotPlayerIsFacing(player)
        val desired = module.textFor(side)
        val text = sign.getText(side)
        if (desired.isEmpty() || sign.isWaxed || !text.hasEditableText(false) || sign.blockPos to side in opened) {
            return false
        }
        return (overwrite || !text.hasMessage(false)) && text.getMessages(false).map { it.string } != desired
    }

    private fun remember(side: Pair<BlockPos, SignTextSlot>) {
        opened.put(side, eventListenerScope.launch {
            waitTicks(RETRY)
            opened.remove(side)
        })?.cancel()
    }

    private fun aimedAt(pos: BlockPos, reach: Double) = world.clip(
        ClipContext(
            player.eyePosition, player.eyePosition.add(RotationManager.serverRotation.viewVector.scale(reach)),
            ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player,
        ),
    ).takeIf { it.type == HitResult.Type.BLOCK && it.blockPos == pos }

}
