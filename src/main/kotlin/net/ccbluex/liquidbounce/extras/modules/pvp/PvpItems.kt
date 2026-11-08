package net.ccbluex.liquidbounce.extras.modules.pvp

import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.inventory.useHotbarSlotOrOffhand
import net.ccbluex.liquidbounce.utils.item.attackSpeed
import net.ccbluex.liquidbounce.utils.item.getEnchantment
import net.ccbluex.liquidbounce.utils.item.isChestArmor
import net.ccbluex.liquidbounce.utils.item.isGlider
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.MaceItem
import net.minecraft.world.item.enchantment.Enchantments
import kotlin.math.ceil

internal val ItemStack.isMace get() = item is MaceItem

/** The mace that hits hardest after falling [fallen] blocks: Breach below [densityFrom], Density from there on. */
internal fun bestMace(fallen: Double, densityFrom: Float): HotbarItemSlot? {
    val (first, second) = if (fallen >= densityFrom) {
        Enchantments.DENSITY to Enchantments.BREACH
    } else {
        Enchantments.BREACH to Enchantments.DENSITY
    }
    return Slots.Hotbar.filter { it.itemStack.isMace }.maxWithOrNull(
        compareBy({ it.itemStack.getEnchantment(first) }, { it.itemStack.getEnchantment(second) }),
    )
}

internal val wearsGlider get() = player.getItemBySlot(EquipmentSlot.CHEST).isGlider

/**
 * Ticks a weapon has to be held before it hits at full charge. Paper resets the charge on every slot change,
 * and a spear does not stab at all before it is full.
 */
internal val ItemStack.chargeTicks get() = ceil(TICKS_PER_SECOND / attackSpeed).toInt()

private const val TICKS_PER_SECOND = 20.0

/**
 * Puts a hotbar chestplate or elytra on in place of the chest piece, like right-clicking armor does. From the
 * hotbar this is a use, while an inventory click in mid-air is what inventory checks catch.
 */
@IgnorableReturnValue
context(requester: EventListener)
internal fun wearChest(glider: Boolean): Boolean {
    val slot = Slots.Hotbar.findSlot { if (glider) it.isGlider && !it.nextDamageWillBreak() else it.isChestArmor }
        ?: return false
    return useHotbarSlotOrOffhand(slot).consumesAction()
}
