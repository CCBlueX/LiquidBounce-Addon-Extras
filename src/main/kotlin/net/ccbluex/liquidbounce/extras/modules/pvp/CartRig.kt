package net.ccbluex.liquidbounce.extras.modules.pvp

import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.KeybindIsPressedEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickConditional
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationMode
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.block.doPlacement
import net.ccbluex.liquidbounce.utils.block.targetfinding.BlockOffsetOptions
import net.ccbluex.liquidbounce.utils.block.targetfinding.BlockPlacementTargetFindingOptions
import net.ccbluex.liquidbounce.utils.block.targetfinding.CenterTargetPositionFactory
import net.ccbluex.liquidbounce.utils.block.targetfinding.FaceHandlingOptions
import net.ccbluex.liquidbounce.utils.block.targetfinding.PlayerLocationOnPlacement
import net.ccbluex.liquidbounce.utils.block.targetfinding.findBestBlockPlacementTarget
import net.ccbluex.liquidbounce.utils.block.targetfinding.verifyClick
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.client.world
import net.ccbluex.liquidbounce.utils.entity.interactBlock
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.minecraft.core.BlockPos
import net.minecraft.tags.BlockTags
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT
import net.minecraft.world.item.CrossbowItem

/**
 * The hands of the cart modules: places blocks, puts carts on rails and shoots them, each only after the
 * server has the rotation for it.
 */
internal class CartRig(
    private val owner: ClientModule,
    private val rotationMode: ModeValueGroup<RotationMode>,
) : EventListener {

    /** While set and the bow is drawn, the use key counts as held; vanilla releases the bow once it is cleared. */
    private var drawing = false
        set(value) {
            if (field != value) {
                RigShots.drawing += if (value) 1 else -1
            }
            field = value
        }

    val reach get() = player.blockInteractionRange()

    override fun parent() = owner

    @Suppress("unused")
    private val useKeyHandler = handler<KeybindIsPressedEvent> { event ->
        if (drawing && player.isUsingItem && event.keyBinding === mc.options.keyUse) {
            event.isPressed = true
        }
    }

    fun reset() {
        drawing = false
    }

    /** Whether [slot]'s block, or fire for flint and steel, can go to [pos] from here. */
    fun canPlace(pos: BlockPos, slot: HotbarItemSlot) = placement(pos, slot) != null

    /** Places [slot]'s block, or fire for flint and steel, at [pos]. */
    @IgnorableReturnValue
    suspend fun place(pos: BlockPos, slot: HotbarItemSlot): Boolean {
        var placed = false
        tickConditional(AIM_TIMEOUT) {
            val target = placement(pos, slot) ?: return@tickConditional true
            rotationMode.activeMode.rotate(
                target.rotation,
                isFinished = { target.verifyClick(RotationManager.serverRotation) != null },
            ) {
                val hit = target.verifyClick(RotationManager.serverRotation) ?: return@rotate
                if (!placed) {
                    select(slot)
                    doPlacement(hit, RotationManager.serverRotation, slot.useHand)
                    placed = true
                }
            }
            placed
        }
        return placed
    }

    /**
     * Lays a [rail] unless one is there and stacks TNT minecarts on it until [count] sit there. Returns one
     * once the server has spawned it.
     */
    suspend fun setUp(rail: BlockPos, count: Int): MinecartTNT? {
        if (!world.getBlockState(rail).`is`(BlockTags.RAILS)) {
            val railSlot = Slots.OffhandWithHotbar.findSlot { it.isRail } ?: return null
            if (!place(rail, railSlot)) {
                return null
            }
        }
        while (CartAim.carts(rail).size < count) {
            val slot = Slots.OffhandWithHotbar.findSlot { it.isTntCart } ?: break
            val before = CartAim.carts(rail).size
            if (!putCart(rail, slot) || tickConditional(SPAWN_TIMEOUT) { CartAim.carts(rail).size > before }) {
                break
            }
        }
        return CartAim.carts(rail).firstOrNull()
    }

    /**
     * Sets the [cart] off with an arrow, lighting a fire on the arrow's path first unless it burns already.
     * [release] holds the shot back: the bow stays drawn, the crossbow loaded, until it agrees.
     */
    @IgnorableReturnValue
    suspend fun ignite(
        cart: MinecartTNT,
        igniter: Igniter,
        charge: Int,
        release: () -> Boolean = { true },
    ): Boolean {
        if (!igniter.burns) {
            val flint = igniter.flint ?: return false
            val cell = CartAim.fireCell(cart.blockPosition(), CartAim.aimPoint(cart)) ?: return false
            if (!world.getBlockState(cell).`is`(BlockTags.FIRE) && !place(cell, flint)) {
                return false
            }
        }
        return if (igniter.launcher.itemStack.item is CrossbowItem) {
            fireCrossbow(cart, igniter.launcher, release)
        } else {
            drawBow(cart, igniter.launcher, charge, release)
        }
    }

    private suspend fun putCart(rail: BlockPos, slot: HotbarItemSlot): Boolean {
        var used = false
        tickConditional(AIM_TIMEOUT) {
            if (!world.getBlockState(rail).`is`(BlockTags.RAILS)) {
                return@tickConditional true
            }
            rotationMode.activeMode.rotate(
                Rotation.lookingAt(CartAim.railPoint(rail), player.eyePosition),
                isFinished = { CartAim.railHit(rail, reach) != null },
            ) {
                val hit = CartAim.railHit(rail, reach) ?: return@rotate
                if (!used) {
                    select(slot)
                    interactBlock(hit, slot.useHand)
                    used = true
                }
            }
            used
        }
        return used
    }

    private suspend fun fireCrossbow(target: Entity, launcher: HotbarItemSlot, release: () -> Boolean): Boolean {
        var fired = false
        tickConditional(SHOT_TIMEOUT) {
            val rotation = CartAim.shotAt(target, launcher.itemStack, 0)
            if (!target.isAlive || rotation == null) {
                return@tickConditional true
            }
            rotationMode.activeMode.rotate(rotation, isFinished = { CartAim.aimed(rotation) && release() }) {
                if (!fired) {
                    select(launcher)
                    useLauncher(launcher)
                    RigShots.fired()
                    fired = true
                }
            }
            fired
        }
        return fired
    }

    private suspend fun drawBow(
        target: Entity,
        launcher: HotbarItemSlot,
        charge: Int,
        release: () -> Boolean,
    ): Boolean {
        var started = false
        var fired = false
        tickConditional(SHOT_TIMEOUT) {
            val rotation = CartAim.shotAt(target, launcher.itemStack, charge)
            if (!target.isAlive || rotation == null || started && !player.isUsingItem) {
                return@tickConditional true
            }
            if (!started) {
                rotationMode.activeMode.rotate(rotation, isFinished = { CartAim.aimed(rotation) }) {
                    if (!started) {
                        // Held for the whole draw
                        SilentHotbar.selectSlotSilently(owner, launcher, SHOT_TIMEOUT)
                        useLauncher(launcher)
                        drawing = true
                        started = true
                    }
                }
            } else {
                rotationMode.activeMode.rotate(rotation, isFinished = { false }) {}
                // Vanilla lets go of the bow as soon as the use key is no longer held
                if (player.ticksUsingItem >= charge && CartAim.aimed(rotation) && release()) {
                    drawing = false
                    RigShots.fired()
                    fired = true
                }
            }
            fired
        }
        drawing = false
        return fired
    }

    private fun placement(pos: BlockPos, slot: HotbarItemSlot) = findBestBlockPlacementTarget(
        pos,
        BlockPlacementTargetFindingOptions(
            BlockOffsetOptions.Default,
            FaceHandlingOptions(CenterTargetPositionFactory),
            slot.itemStack,
            PlayerLocationOnPlacement(),
        ),
    )?.takeIf { it.interactionPoint.distanceTo(player.eyePosition) <= reach }

    private fun select(slot: HotbarItemSlot) {
        if (!slot.isOffHand) {
            SilentHotbar.selectSlotSilently(owner, slot, SLOT_HOLD)
        }
    }

    private companion object {
        const val AIM_TIMEOUT = 20
        const val SHOT_TIMEOUT = 80
        const val SPAWN_TIMEOUT = 20
        const val SLOT_HOLD = 3
    }

}

/** Shots the cart modules fire on their own, which [ModuleCartBomb] must not take for yours. */
internal object RigShots {

    private const val WINDOW = 2

    private var lastShot = Int.MIN_VALUE / 2

    var drawing = 0

    fun fired() {
        lastShot = player.tickCount
    }

    // A respawned player counts its ticks from zero again
    fun recent() = drawing > 0 || player.tickCount - lastShot in 0..WINDOW

}
