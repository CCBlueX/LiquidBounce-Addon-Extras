package net.ccbluex.liquidbounce.extras.modules.grinding

import net.ccbluex.liquidbounce.event.events.ScheduleInventoryActionEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.inventory.ContainerItemSlot
import net.ccbluex.liquidbounce.utils.inventory.InventoryAction
import net.ccbluex.liquidbounce.utils.inventory.InventoryConstraints
import net.minecraft.client.gui.screens.inventory.AnvilScreen
import net.minecraft.world.inventory.AnvilMenu

/**
 * Repairs damaged gear by combining two of the same kind in an open anvil. Enchanted items are never
 * sacrificed. A pair the anvil prices above the limit goes back, and the anvil is left alone until it is
 * opened again.
 */
object ModuleAutoAnvilRepair : ClientModule("AutoAnvilRepair", ExtrasCategories.GRINDING) {

    private val maxLevelCost by int("MaxLevelCost", 5, 1..39, "levels")
    private val constraints = tree(InventoryConstraints())

    private var rejectedMenu = -1

    @Suppress("unused")
    private val repairHandler = handler<ScheduleInventoryActionEvent> { event ->
        val screen = mc.gui.screen() as? AnvilScreen ?: return@handler
        val menu = screen.menu
        if (menu.containerId == rejectedMenu || !menu.carried.isEmpty) {
            return@handler
        }

        fun quickMove(slot: Int) = InventoryAction.Click.performQuickMove(screen, ContainerItemSlot(slot))

        val inputs = listOf(AnvilMenu.INPUT_SLOT, AnvilMenu.ADDITIONAL_SLOT).filter { menu.getSlot(it).hasItem() }
        val actions = when {
            inputs.size == 2 && isWorthIt(menu) -> listOf(quickMove(AnvilMenu.RESULT_SLOT))
            inputs.isNotEmpty() -> {
                rejectedMenu = menu.containerId
                inputs.map(::quickMove)
            }
            else -> findPair(menu)?.toList()?.map(::quickMove) ?: return@handler
        }
        event.schedule(constraints, actions)
    }

    override fun onDisabled() {
        rejectedMenu = -1
    }

    private fun isWorthIt(menu: AnvilMenu) = menu.getSlot(AnvilMenu.RESULT_SLOT).hasItem() &&
        menu.cost in 1..maxLevelCost && (player.abilities.instabuild || player.experienceLevel >= menu.cost)

    private fun findPair(menu: AnvilMenu): Pair<Int, Int>? =
        (AnvilMenu.RESULT_SLOT + 1 until menu.slots.size)
            .filter { index ->
                val stack = menu.getSlot(index).item
                stack.isDamaged && !stack.isEnchanted && stack.count == 1
            }
            .sortedByDescending { menu.getSlot(it).item.damageValue }
            .groupBy { menu.getSlot(it).item.item }
            .values
            .firstOrNull { it.size >= 2 }
            ?.let { (first, second) -> first to second }

}
