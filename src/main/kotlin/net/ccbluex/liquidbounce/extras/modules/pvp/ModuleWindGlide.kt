package net.ccbluex.liquidbounce.extras.modules.pvp

import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.tickUntil
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.extras.util.report
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.aiming.RotationsValueGroup
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.inventory.useHotbarSlotOrOffhand
import net.ccbluex.liquidbounce.utils.kotlin.random
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.Items
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import kotlin.math.hypot

/**
 * Puts on an elytra and glides, throwing a wind charge steeply down whenever the ground comes close, so the
 * burst lifts us again. Stops once a chestplate is worn again.
 */
object ModuleWindGlide : ClientModule("WindGlide", ExtrasCategories.PVP, disableOnQuit = true) {

    private const val EQUIP_TIMEOUT = 10
    private const val FALL_TIMEOUT = 20
    private const val SCAN_DEPTH = 16.0

    private val height by float("Height", 3f, 1f..8f, "blocks")
    private val speedFactor by float("SpeedFactor", 0.5f, 0f..4f)
    private val pitch by floatRange("Pitch", FEET_PITCH - 3f..FEET_PITCH, 45f..90f)
    private val rotations = tree(RotationsValueGroup(this))

    /** Whether the elytra went on, so a chestplate afterwards means we are done. */
    private var equipped = false
    private var jumping = false

    override fun onDisabled() {
        equipped = false
        jumping = false
    }

    @Suppress("unused")
    private val jumpHandler = handler<MovementInputEvent> { event ->
        if (jumping) {
            event.jump = true
        }
    }

    @Suppress("unused")
    private val glideHandler = tickHandler {
        if (!player.getItemBySlot(EquipmentSlot.CHEST).has(DataComponents.GLIDER)) {
            if (equipped) {
                enabled = false
            } else {
                equip()
            }
            return@tickHandler
        }
        equipped = true

        if (!player.isFallFlying) {
            deploy()
            return@tickHandler
        }

        val charge = hotbarSlotOf(Items.WIND_CHARGE) ?: return@tickHandler
        val speed = hypot(player.deltaMovement.x, player.deltaMovement.z)
        if (isReady(charge) && aboveGround() < height - speed * speedFactor) {
            throwDown(charge)
        }
    }

    @IgnorableReturnValue
    private suspend fun throwDown(charge: HotbarItemSlot): Boolean {
        val down = pitch.random()
        return throwFrom(charge) { rotations.briefTarget(Rotation(player.yRot, down)) }
    }

    private suspend fun equip() {
        val elytra = Slots.OffhandWithHotbar.findSlot { it.has(DataComponents.GLIDER) }
        if (elytra == null) {
            report("noElytra")
            enabled = false
            return
        }
        useHotbarSlotOrOffhand(elytra, 1, player.yRot, player.xRot)
        tickUntil { player.getItemBySlot(EquipmentSlot.CHEST).has(DataComponents.GLIDER) || it >= EQUIP_TIMEOUT }
    }

    /**
     * Launches off the ground with a charge and the jump a tick later, then lets go of the key and presses it
     * again once falling, which opens the elytra high enough for the first charge in the air to reach.
     */
    private suspend fun deploy() {
        if (player.onGround()) {
            val charge = hotbarSlotOf(Items.WIND_CHARGE)
            if (charge != null && isReady(charge) && throwDown(charge)) {
                waitTicks(1)
            }
            press()
        }
        tickUntil { !player.onGround() && player.deltaMovement.y < 0.0 || it >= FALL_TIMEOUT }
        if (!player.onGround()) {
            press()
        }
    }

    private suspend fun press() {
        jumping = true
        waitTicks(1)
        jumping = false
        waitTicks(1)
    }

    private fun aboveGround(): Double {
        val from = player.position()
        val below = from.subtract(0.0, SCAN_DEPTH, 0.0)
        val hit = world.clip(ClipContext(from, below, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player))
        return if (hit.type == HitResult.Type.MISS) SCAN_DEPTH else from.y - hit.location.y
    }

}
