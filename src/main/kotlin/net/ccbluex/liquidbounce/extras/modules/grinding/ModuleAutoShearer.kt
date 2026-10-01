package net.ccbluex.liquidbounce.extras.modules.grinding

import net.ccbluex.liquidbounce.event.tickConditional
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.aiming.NoRotationMode
import net.ccbluex.liquidbounce.utils.aiming.NormalRotationMode
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.utils.raytraceBox
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.entity.interactEntity
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.raytracing.isLookingAtEntity
import net.minecraft.world.entity.animal.sheep.Sheep
import net.minecraft.world.item.Items

/**
 * Shears the closest sheep in reach. The interaction goes out only once the server has our rotation,
 * which is what an anticheat compares it against.
 */
object ModuleAutoShearer : ClientModule("AutoShearer", ExtrasCategories.GRINDING) {

    private const val AIM_TIMEOUT = 20

    private val range by float("Range", 3f, 1f..6f, "blocks")
    private val delay by int("Delay", 10, 0..40, "ticks")
    private val antiBreak by boolean("AntiBreak", true)
    private val rotationMode = modes(this, "RotationMode") {
        arrayOf(NormalRotationMode(it, this), NoRotationMode(it, this))
    }

    @Suppress("unused")
    private val shearHandler = tickHandler {
        val shears = Slots.Hotbar.findSlot {
            it.item === Items.SHEARS && (!antiBreak || it.damageValue < it.maxDamage - 1)
        } ?: return@tickHandler
        val sheep = world.getEntitiesOfClass(Sheep::class.java, player.boundingBox.inflate(range.toDouble())) {
            it.readyForShearing()
        }.minByOrNull { it.distanceToSqr(player) } ?: return@tickHandler

        var sheared = false
        tickConditional(AIM_TIMEOUT) {
            val (rotation, _) = raytraceBox(player.eyePosition, sheep.boundingBox, range.toDouble(), 0.0)
                ?: return@tickConditional true
            rotationMode.activeMode.rotate(rotation, isFinished = { aimedAt(sheep) != null }) {
                val hit = aimedAt(sheep) ?: return@rotate
                SilentHotbar.selectSlotSilently(this@ModuleAutoShearer, shears, delay + 1)
                interactEntity(sheep, hit)
                sheared = true
            }
            sheared
        }
        if (sheared) {
            waitTicks(delay)
        }
    }

    private fun aimedAt(sheep: Sheep) = isLookingAtEntity(
        toEntity = sheep,
        rotation = RotationManager.serverRotation,
        range = range.toDouble(),
        throughWallsRange = 0.0,
    )

}
