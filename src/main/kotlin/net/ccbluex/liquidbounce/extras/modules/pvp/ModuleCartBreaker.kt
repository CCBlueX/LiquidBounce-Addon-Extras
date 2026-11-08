package net.ccbluex.liquidbounce.extras.modules.pvp

import net.ccbluex.liquidbounce.event.tickConditional
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.aiming.NoRotationMode
import net.ccbluex.liquidbounce.utils.aiming.NormalRotationMode
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.utils.raytraceBox
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.combat.attackEntity
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.inventory.findClosestSlot
import net.ccbluex.liquidbounce.utils.item.isSword
import net.ccbluex.liquidbounce.utils.raytracing.isLookingAtEntity
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT
import kotlin.math.min

/**
 * Breaks TNT minecarts others put down while they are still unlit. A stationary cart that is hit drops as an
 * item instead of going off. Carts we placed ourselves are left alone.
 */
object ModuleCartBreaker : ClientModule("CartBreaker", ExtrasCategories.PVP) {

    private const val AIM_TIMEOUT = 20

    // A sword hit right after switching does a fifth of its damage, too little to break a cart
    private const val READY_COOLDOWN = 0.9f

    // Aimed at the very edge of the reach, rounding the rotation can put the hit just beyond it
    private const val AIM_MARGIN = 0.3

    private val range by float("Range", 3.5f, 1f..6f, "blocks")
    private val fov by float("FOV", 120f, 10f..360f, "°")
    private val reaction by intRange("Reaction", 3..6, 0..20, "ticks")
    private val switchToSword by boolean("SwitchToSword", true)
    private val rotationMode = modes(this, "RotationMode") {
        arrayOf(NormalRotationMode(it, this), NoRotationMode(it, this))
    }

    /** The cart being broken. */
    var target: MinecartTNT? = null
        private set

    @Suppress("unused")
    private val breakHandler = tickHandler {
        val reach = min(range.toDouble(), player.entityInteractionRange())
        val aimReach = reach - AIM_MARGIN
        val cart = world.getEntitiesOfClass(MinecartTNT::class.java, player.boundingBox.inflate(reach)) {
            isForeign(it) && inView(it) && raytraceBox(player.eyePosition, it.boundingBox, aimReach, 0.0) != null
        }.minByOrNull { it.distanceToSqr(player) } ?: return@tickHandler

        target = cart
        try {
            waitTicks(reaction.random())
            breakCart(cart, reach)
        } finally {
            target = null
        }
    }

    override fun onDisabled() {
        target = null
    }

    private suspend fun breakCart(cart: MinecartTNT, reach: Double) {
        val sword = if (switchToSword) Slots.Hotbar.findClosestSlot { it.isSword } else null
        var swung = false
        tickConditional(AIM_TIMEOUT) {
            if (!isForeign(cart)) {
                return@tickConditional true
            }
            val (rotation, _) = raytraceBox(player.eyePosition, cart.boundingBox, reach - AIM_MARGIN, 0.0)
                ?: return@tickConditional true
            if (sword != null && !sword.isSelected) {
                // Switched early, so that the sword has recovered by the time the aim is on the cart
                SilentHotbar.selectSlotSilently(this@ModuleCartBreaker, sword, AIM_TIMEOUT)
            }
            rotationMode.activeMode.rotate(
                rotation,
                isFinished = { aimedAt(cart, reach) && player.getAttackStrengthScale(0.5f) >= READY_COOLDOWN },
            ) {
                if (!swung && aimedAt(cart, reach)) {
                    attackEntity(cart, SwingMode.DO_NOT_HIDE)
                    swung = true
                }
            }
            swung
        }
    }

    private fun isForeign(cart: MinecartTNT) = cart.isAlive && !cart.isPrimed && cart.id !in OwnCarts

    private fun inView(cart: MinecartTNT): Boolean {
        val toCart = Rotation.lookingAt(cart.boundingBox.center, player.eyePosition)
        return Rotation(player.yRot, player.xRot).directionAngleTo(toCart) <= fov / 2
    }

    private fun aimedAt(cart: MinecartTNT, reach: Double) = isLookingAtEntity(
        toEntity = cart,
        rotation = RotationManager.serverRotation,
        range = reach,
        throughWallsRange = 0.0,
    ) != null

}
