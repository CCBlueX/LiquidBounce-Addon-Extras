package net.ccbluex.liquidbounce.extras.modules.pvp

import net.ccbluex.liquidbounce.event.events.AttackEntityEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.item.getEnchantment
import net.ccbluex.liquidbounce.utils.item.isSword
import net.minecraft.world.item.MaceItem
import net.minecraft.world.item.enchantment.Enchantments
import kotlin.random.Random

/**
 * Now and then swings the mace with the most Breach in place of your sword, which takes armor off that hit.
 * The sword is back with the next tick.
 */
object ModuleBreachSwap : ClientModule("BreachSwap", ExtrasCategories.PVP) {

    private val chance by int("Chance", 50, 0..100, "%")

    @Suppress("unused")
    private val attackHandler = handler<AttackEntityEvent> {
        // A falling hit is a smash, which is MaceAssist's business
        if (!player.mainHandItem.isSword || MaceItem.canSmashAttack(player) || Random.nextInt(100) >= chance) {
            return@handler
        }
        val mace = Slots.Hotbar
            .filter { it.itemStack.isMace && it.itemStack.getEnchantment(Enchantments.BREACH) > 0 }
            .maxByOrNull { it.itemStack.getEnchantment(Enchantments.BREACH) } ?: return@handler
        SilentHotbar.selectSlotSilently(this, mace, 1)
    }

    override fun onDisabled() = SilentHotbar.resetSlot(this)

}
