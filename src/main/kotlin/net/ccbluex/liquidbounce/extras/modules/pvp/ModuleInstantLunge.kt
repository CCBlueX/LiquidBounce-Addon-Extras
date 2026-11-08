package net.ccbluex.liquidbounce.extras.modules.pvp

import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.extras.util.report
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.item.getEnchantment
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.enchantment.Enchantments

/**
 * Lunges once with your best Lunge spear and turns itself off. The spear is held until it is charged, then
 * the slot you held comes back after a random delay.
 */
object ModuleInstantLunge : ClientModule("InstantLunge", ExtrasCategories.PVP) {

    // The lunge only pushes a player with this much food, see the Lunge enchantment
    private const val MIN_FOOD = 7

    private val switchBack by intRange("SwitchBack", 2..6, 1..20, "ticks")

    @Suppress("unused")
    private val lungeHandler = tickHandler {
        val spear = Slots.Hotbar
            .filter { it.itemStack.has(DataComponents.PIERCING_WEAPON) }
            .filter { it.itemStack.getEnchantment(Enchantments.LUNGE) > 0 }
            .maxByOrNull { it.itemStack.getEnchantment(Enchantments.LUNGE) }
        when {
            spear == null -> report("noSpear")
            player.foodData.foodLevel < MIN_FOOD -> report("hungry")
            player.isFallFlying || player.isInWater || player.isPassenger -> report("blocked")
            else -> {
                val stack = spear.itemStack
                val charge = stack.chargeTicks
                val delay = switchBack.random()
                SilentHotbar.selectSlotSilently(this@ModuleInstantLunge, spear, charge + delay)
                waitTicks(charge)
                val piercing = stack.get(DataComponents.PIERCING_WEAPON) ?: return@tickHandler
                interaction.piercingAttack(stack.attackAnimation, piercing)
                waitTicks(delay)
            }
        }
        enabled = false
    }

    override fun onDisabled() = SilentHotbar.resetSlot(this)

}
