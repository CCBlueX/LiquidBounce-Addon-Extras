package net.ccbluex.liquidbounce.extras.modules

import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationsValueGroup
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.item.Items
import net.minecraft.world.phys.EntityHitResult

/**
 * Shears every sheep in reach. Aims through the client's rotation engine (the `Rotations` settings
 * are the same ones every built-in module has), takes the shears without changing the visible hotbar
 * slot, and interacts once the server-side rotation has arrived.
 */
object ModuleAutoShearer : ClientModule("AutoShearer", ExtrasCategories.EXTRAS) {

    private val range by float("Range", 4f, 1f..6f, "blocks")
    private val rotations = tree(RotationsValueGroup(this))

    @Suppress("unused")
    private val shearHandler = tickHandler {
        val shears = Slots.Hotbar.findSlot(Items.SHEARS) ?: return@tickHandler
        val sheep = world
            .getEntities(EntityTypes.SHEEP, player.boundingBox.inflate(range.toDouble())) { it.readyForShearing() }
            .minByOrNull { it.distanceToSqr(player) } ?: return@tickHandler

        val rotation = Rotation.lookingAt(sheep.boundingBox.center, player.eyePosition)
        RotationManager.setRotationTarget(
            rotation,
            valueGroup = rotations,
            priority = Priority.IMPORTANT_FOR_USAGE_1,
            provider = this@ModuleAutoShearer,
        )
        if (RotationManager.serverRotation.rotationDeltaTo(rotation).length() > 5f) {
            return@tickHandler
        }

        SilentHotbar.selectSlotSilently(this@ModuleAutoShearer, shears, 20)
        interaction.interact(player, sheep, EntityHitResult(sheep), InteractionHand.MAIN_HAND)
        player.swing(InteractionHand.MAIN_HAND)
        waitTicks(10)
    }

}
