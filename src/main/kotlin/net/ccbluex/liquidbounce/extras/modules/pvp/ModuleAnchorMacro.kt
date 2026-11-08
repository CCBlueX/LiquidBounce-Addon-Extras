package net.ccbluex.liquidbounce.extras.modules.pvp

import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.tickUntil
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationsValueGroup
import net.ccbluex.liquidbounce.utils.block.isInteractable
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.input.InputBind
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.raytracing.traceFromPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RespawnAnchorBlock
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/**
 * While its bind is held: places an anchor where you look, charges it and sets it off. With DoubleAnchor, a
 * second one goes into the same spot right after and waits for the target to be hurtable again.
 */
object ModuleAnchorMacro : ClientModule("AnchorMacro", ExtrasCategories.PVP, bindAction = InputBind.BindAction.HOLD) {

    // How far around the anchor DoubleAnchor looks for whom it is timed to
    private const val TARGET_RANGE = 6.0

    private val switch by enumChoice("Switch", Switch.SILENT)
    private val onlyOwn by boolean("OnlyOwn", true)
    private val antiSuicide by boolean("AntiSuicide", true)
    private val reach by float("Reach", 4.5f, 1f..6f, "blocks")
    private val delay by intRange("Delay", 1..3, 0..10, "ticks")
    private val guard by boolean("Guard", true)
    private val rotations = tree(RotationsValueGroup(this))

    private object DoubleAnchor : ToggleableValueGroup(ModuleAnchorMacro, "DoubleAnchor", false) {
        val hurtTime by int("HurtTime", 0, 0..10, "ticks")
        val timeout by int("Timeout", 20, 1..40, "ticks")
    }

    init {
        tree(DoubleAnchor)
    }

    private val range get() = reach.toDouble()
    private val visible get() = switch == Switch.VISIBLE

    @Suppress("unused")
    private val macroHandler = tickHandler {
        AnchorKit.forgetCleared()
        if (ModuleBlastCover.pending != null) {
            return@tickHandler
        }
        // The crosshair as the server has it, so the click matches the last rotation it got
        val hit = traceFromPlayer(RotationManager.serverRotation, range)
        if (hit.type != HitResult.Type.BLOCK) {
            return@tickHandler
        }
        val state = world.getBlockState(hit.blockPos)
        val anchor = when {
            state.block is RespawnAnchorBlock -> hit.blockPos.takeIf { !onlyOwn || it in AnchorKit.placed }
            state.isInteractable -> null
            else -> placedAt(hit)
                .takeIf { it !in AnchorKit.spent && canPlace(it, Blocks.RESPAWN_ANCHOR.defaultBlockState()) }
        } ?: return@tickHandler
        AnchorKit.guard(this@ModuleAnchorMacro, guard) { cycle(anchor, hit) }
        waitTicks(delay.random())
    }

    private suspend fun cycle(anchor: BlockPos, hit: BlockHitResult) {
        if (!anchor.isAnchor()) {
            use(hit, Slots.Hotbar.findSlot(Items.RESPAWN_ANCHOR) ?: return, visible)
            AnchorKit.rememberPlaced(anchor)
            waitTicks(delay.random())
        }
        if (!chargeAndDetonate(anchor) || !DoubleAnchor.enabled) {
            return
        }

        // The server takes the first one away, then the second goes into the same spot
        tickUntil { !anchor.isAnchor() || it > DoubleAnchor.timeout }
        val placed = clickAimed(rotations, find = { placeClick(anchor, range).takeIf { canPlace(anchor) } }) {
            use(it, Slots.Hotbar.findSlot(Items.RESPAWN_ANCHOR) ?: return@clickAimed, visible)
        }
        if (!placed || !anchor.isAnchor()) {
            return
        }
        AnchorKit.rememberPlaced(anchor)
        waitTicks(delay.random())
        val target = target(anchor)
        val timeout = DoubleAnchor.timeout
        chargeAndDetonate(anchor) {
            tickUntil { target == null || !target.isAlive || target.hurtTime <= DoubleAnchor.hurtTime || it > timeout }
        }
    }

    @IgnorableReturnValue
    private suspend fun chargeAndDetonate(anchor: BlockPos, beforeBlast: suspend () -> Unit = {}): Boolean {
        if (anchor.charges() == 0) {
            ModuleBlastCover.coverBeforeCharge(anchor)
            if (!activate(anchor, Slots.Hotbar.findSlot(Items.GLOWSTONE) ?: return false)) {
                return false
            }
            waitTicks(delay.random())
        }
        beforeBlast()
        ModuleBlastCover.coverBefore(anchor)
        if (antiSuicide && player.anchorDamage(anchor) >= player.health + player.absorptionAmount) {
            return false
        }
        if (anchor.charges() == 0 || !activate(anchor, detonator() ?: return false)) {
            return false
        }
        AnchorKit.spend(anchor)
        return true
    }

    private suspend fun activate(anchor: BlockPos, slot: HotbarItemSlot) =
        clickAimed(rotations, find = { if (anchor.isAnchor()) clickOn(anchor, range = range) else null }) {
            use(it, slot, visible)
        }

    private fun target(anchor: BlockPos): LivingEntity? =
        world.getEntitiesOfClass(LivingEntity::class.java, AABB(anchor).inflate(TARGET_RANGE)) { it.shouldBeAttacked() }
            .minByOrNull { it.distanceToSqr(Vec3.atCenterOf(anchor)) }

    private enum class Switch(override val tag: String) : Tagged {
        SILENT("Silent"),
        VISIBLE("Visible"),
    }

}
