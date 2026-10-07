package net.ccbluex.liquidbounce.extras.modules.grinding

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.eventListenerScope
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.extras.util.WorldEvents
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

class SignAura(private val module: ModuleAutoSign) : ToggleableValueGroup(module, "Nearby", false) {
    private val overwrite by boolean("Overwrite", false)
    private val range by float("Range", 3f, 1f..4.5f, "blocks")
    private val rotationMode = modes(this, "RotationMode") { arrayOf(NormalRotationMode(it, module)) }
    private val signs = BlockTracker { state -> state.block.takeIf { it is SignBlock } }
    private val attempted = hashMapOf<Pair<BlockPos, SignTextSlot>, Job>()

    @Suppress("unused")
    private val editHandler = tickHandler {
        if (busy()) return@tickHandler
        val reach = minOf(range.toDouble(), player.blockInteractionRange())
        val sign = signs.allPositions().map(BlockPos::immutable).mapNotNull {
            world.getBlockEntity(it) as? SignBlockEntity
        }.filter(::eligible).sortedBy { it.blockPos.distToCenterSqr(player.eyePosition) }.firstOrNull {
            raytraceBlockRotation(player.eyePosition, it.blockPos, it.blockState, reach, 0.0) != null
        } ?: return@tickHandler
        val level = world
        var used = false
        var active = true
        try {
            tickConditional(40) {
                if (busy() || world !== level || !eligible(sign)) return@tickConditional true
                val ray = raytraceBlockRotation(player.eyePosition, sign.blockPos, sign.blockState, reach, 0.0)
                    ?: return@tickConditional true
                rotationMode.activeMode.rotate(ray.rotation, isFinished = { aimedAt(sign.blockPos, reach) != null }) {
                    if (!active || used || !running || mc.level !== level) return@rotate
                    if (busy() || !eligible(sign)) return@rotate
                    val hit = aimedAt(sign.blockPos, reach) ?: return@rotate
                    val key = sign.blockPos.immutable() to sign.getSlotPlayerIsFacing(player)
                    attempted[key] = eventListenerScope.launch {
                        waitTicks(200)
                        attempted.remove(key)
                    }
                    val _ = interactBlock(hit)
                    used = true
                }
                used
            }
        } finally {
            active = false
        }
        waitTicks(20)
    }

    // An empty main hand avoids dyeing, waxing or placing a hanging sign instead of opening its editor.
    private fun busy() = mc.gui.screen() != null || module.isWriting || player.isUsingItem ||
        player.isShiftKeyDown || !player.mainHandItem.isEmpty

    private fun eligible(sign: SignBlockEntity): Boolean {
        if (sign.isRemoved || sign.isWaxed) return false
        val side = sign.getSlotPlayerIsFacing(player)
        val desired = module.textFor(side)
        if (desired.isEmpty() || sign.blockPos to side in attempted) return false
        val text = sign.getText(side)
        if (!text.hasEditableText(false) || text.hasAnyClickCommands(false)) return false
        val current = text.getMessages(false).map { it.string }
        return current != desired && (overwrite || current.all(String::isBlank))
    }

    private fun aimedAt(pos: BlockPos, reach: Double) = world.clip(ClipContext(
        player.eyePosition, player.eyePosition.add(RotationManager.serverRotation.viewVector.scale(reach)),
        ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player,
    )).takeIf { it.type == HitResult.Type.BLOCK && it.blockPos == pos }

    @Suppress("unused")
    private val worldHandler = WorldEvents.handler<WorldChangeEvent> { event ->
        clear()
        if (event.world == null) onDisabled() else if (enabled && module.enabled) onEnabled()
    }

    override fun onEnabled() {
        ChunkScanner.unsubscribe(signs)
        ChunkScanner.subscribe(signs)
    }

    override fun onDisabled() {
        ChunkScanner.unsubscribe(signs)
        clear()
    }

    private fun clear() {
        attempted.values.forEach(Job::cancel)
        attempted.clear()
    }
}
