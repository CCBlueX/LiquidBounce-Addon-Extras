package net.ccbluex.liquidbounce.extras.modules.grinding

import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.inventory.ContainerItemSlot
import net.ccbluex.liquidbounce.utils.inventory.InventoryAction
import net.ccbluex.liquidbounce.utils.inventory.InventoryConstraints
import net.ccbluex.liquidbounce.utils.inventory.inventoryPlan
import net.minecraft.client.gui.screens.inventory.AnvilScreen
import net.minecraft.world.inventory.AnvilMenu

/**
 * Repairs damaged gear by combining two of the same kind in an open anvil. Enchanted items are never
 * sacrificed. A pair the anvil prices above the limit goes back, and the anvil is left alone until it is
 * opened again.
 *
 * @agent gpt-6-astra
 * @agent claude-opus-5-5
 */
object ModuleAutoAnvilRepair : ClientModule("AutoAnvilRepair", ExtrasCategories.GRINDING) {

    private val maxLevelCost by int("MaxLevelCost", 5, 1..39, "levels")
    private val constraints = tree(InventoryConstraints())

    private var rejectedMenu = -1

    @Suppress("unused")
    private val repairHandler = inventoryPlan(constraints) {
        val screen = mc.gui.screen() as? AnvilScreen ?: return@inventoryPlan
        val menu = screen.menu
        if (menu.containerId == rejectedMenu || !menu.carried.isEmpty) {
            return@inventoryPlan
        }

        fun quickMove(slot: Int) = InventoryAction.Click.performQuickMove(screen, ContainerItemSlot(slot))

        val inputs = listOf(AnvilMenu.INPUT_SLOT, AnvilMenu.ADDITIONAL_SLOT).filter { menu.getSlot(it).hasItem() }
        val actions = when (inputs.size) {
            2 if isWorthIt(menu) -> listOf(quickMove(AnvilMenu.RESULT_SLOT))
            2 -> {
                rejectedMenu = menu.containerId
                inputs.map(::quickMove)
            }
            // Half a pair is a click the server has not confirmed yet; its updates for the clicks before can
            // briefly take it back
            1 -> return@inventoryPlan
            else -> findPair(menu)?.toList()?.map(::quickMove) ?: return@inventoryPlan
        }
        perform(actions)
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
