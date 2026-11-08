package net.ccbluex.liquidbounce.extras.modules.pvp

import net.ccbluex.liquidbounce.event.events.ScheduleInventoryActionEvent
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.InventoryAction
import net.ccbluex.liquidbounce.utils.inventory.InventoryConstraints
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.minecraft.world.item.ItemStack

/** How long an item may take from the inventory to the hotbar before the module gives up. */
internal const val PULL_TIMEOUT = 40

/**
 * Swaps the first inventory stack that [wanted] accepts onto the hotbar, unless one is there already: into an empty
 * slot, or the last one not in hand.
 */
internal fun ScheduleInventoryActionEvent.pullToHotbar(
    constraints: InventoryConstraints,
    wanted: (ItemStack) -> Boolean,
) {
    if (Slots.Hotbar.findSlot(wanted) != null || !player.containerMenu.carried.isEmpty) {
        return
    }
    val from = Slots.Inventory.findSlot(wanted) ?: return
    val to = Slots.Hotbar.firstOrNull { it.itemStack.isEmpty }
        ?: Slots.Hotbar.last { it.hotbarIndex != SilentHotbar.clientsideSlot }
    schedule(constraints, InventoryAction.Click.performSwap(from = from, to = to))
}

internal fun hotbarSlot(wanted: (ItemStack) -> Boolean): HotbarItemSlot? = Slots.Hotbar.findSlot(wanted)

internal fun inInventory(wanted: (ItemStack) -> Boolean) = Slots.Inventory.findSlot(wanted) != null
