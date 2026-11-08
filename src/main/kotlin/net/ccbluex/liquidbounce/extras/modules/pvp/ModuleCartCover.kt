package net.ccbluex.liquidbounce.extras.modules.pvp

import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.aiming.NoRotationMode
import net.ccbluex.liquidbounce.utils.aiming.NormalRotationMode
import net.ccbluex.liquidbounce.utils.entity.getDamageFromExplosion
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT
import net.minecraft.world.item.BlockItem
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.PoweredRailBlock
import net.minecraft.world.phys.AABB
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Walls you in against TNT minecarts that are lit or about to be. Each block goes where it cuts the predicted
 * damage the most, until the prediction is low enough.
 */
object ModuleCartCover : ClientModule("CartCover", ExtrasCategories.PVP) {

    // MinecartTNT.explode: 4 plus up to 1.5 per block of speed, capped at 5
    private const val BASE_POWER = 4f
    private const val SPEED_POWER = 1.5f
    private const val MAX_SPEED = 5.0

    private val triggerDamage by float("TriggerDamage", 6f, 1f..40f, "HP")
    private val safeDamage by float("SafeDamage", 4f, 0f..40f, "HP")
    private val blocks by blocks(
        "Blocks",
        linkedSetOf(Blocks.OBSIDIAN, Blocks.COBBLESTONE, Blocks.OAK_PLANKS, Blocks.OAK_LOG),
    )
    private val unlit by boolean("Unlit", false)
    private val protect by enumChoice("Protect", Protect.BOTH)
    private val maxBlocks by int("MaxBlocks", 26, 1..26)
    private val rotationMode = modes(this, "RotationMode") {
        arrayOf(NormalRotationMode(it, this), NoRotationMode(it, this))
    }

    private val rig = CartRig(this, rotationMode)

    /** Blocks placed against each cart, by its entity id; a block counts for the cart that was worst then. */
    private val placedAgainst = HashMap<Int, Int>()

    /** The damage the worst cart around would deal right now, or 0 when none threatens. */
    var predictedDamage = 0f
        private set

    @Suppress("unused")
    private val coverHandler = tickHandler {
        placedAgainst.keys.retainAll { world.getEntity(it) != null }
        val threats = threats()
        predictedDamage = damage(threats)
        if (predictedDamage < triggerDamage) {
            return@tickHandler
        }
        // Each block is placed only once it still helps; placing changes what helps next
        while (predictedDamage >= safeDamage) {
            val slot = Slots.OffhandWithHotbar.findSlot {
                (it.item as? BlockItem)?.block in blocks
            } ?: return@tickHandler
            val worst = worst(threats) ?: return@tickHandler
            if (placedAgainst.getOrDefault(worst.id, 0) >= maxBlocks) {
                return@tickHandler
            }
            val (cell, after) = bestCell(threats, slot) ?: return@tickHandler
            if (after >= predictedDamage || !rig.place(cell, slot)) {
                return@tickHandler
            }
            placedAgainst.merge(worst.id, 1, Int::plus)
            predictedDamage = damage(threats())
        }
    }

    override fun onDisabled() {
        predictedDamage = 0f
        placedAgainst.clear()
        rig.reset()
    }

    private fun threats() = world.getEntitiesOfClass(
        MinecartTNT::class.java, player.boundingBox.inflate(2.0 * maxPower()),
    ) {
        it.isAlive && protect.covers(it.id in OwnCarts) &&
            (it.isPrimed || it.isOnFire || onPoweredActivator(it) || unlit && it.id !in OwnCarts)
    }

    private fun worst(threats: List<MinecartTNT>) =
        threats.maxByOrNull { player.getDamageFromExplosion(it.position(), power(it)) }

    private fun onPoweredActivator(cart: MinecartTNT): Boolean {
        val state = world.getBlockState(cart.blockPosition())
        return state.`is`(Blocks.ACTIVATOR_RAIL) && state.getValue(PoweredRailBlock.POWERED)
    }

    private fun damage(threats: List<MinecartTNT>, cover: BlockPos? = null) = threats.maxOfOrNull { cart ->
        player.getDamageFromExplosion(cart.position(), power(cart), include = cover)
    } ?: 0f

    private fun power(cart: MinecartTNT) =
        BASE_POWER + min(sqrt(cart.deltaMovement.horizontalDistanceSqr()), MAX_SPEED).toFloat() * SPEED_POWER

    private fun maxPower() = BASE_POWER + MAX_SPEED.toFloat() * SPEED_POWER

    private fun bestCell(threats: List<MinecartTNT>, slot: HotbarItemSlot): Pair<BlockPos, Float>? {
        val feet = player.blockPosition()
        val body = player.boundingBox
        return BlockPos.betweenClosed(feet.offset(-1, 0, -1), feet.offset(1, 2, 1))
            .map(BlockPos::immutable)
            .filter { cell ->
                !AABB(cell).intersects(body) && world.getEntities(null, AABB(cell)).isEmpty() &&
                    rig.canPlace(cell, slot)
            }
            .map { it to damage(threats, it) }
            .minByOrNull { it.second }
    }

    private enum class Protect(override val tag: String, val own: Boolean, val enemy: Boolean) : Tagged {
        OWN("Own", true, false),
        ENEMY("Enemy", false, true),
        BOTH("Both", true, true);

        fun covers(isOwn: Boolean) = if (isOwn) own else enemy
    }

}
