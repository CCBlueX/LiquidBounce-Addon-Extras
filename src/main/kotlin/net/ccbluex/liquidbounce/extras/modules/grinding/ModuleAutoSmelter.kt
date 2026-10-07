package net.ccbluex.liquidbounce.extras.modules.grinding

import net.ccbluex.liquidbounce.event.events.ScheduleInventoryActionEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.inventory.ContainerItemSlot
import net.ccbluex.liquidbounce.utils.inventory.InventoryAction
import net.ccbluex.liquidbounce.utils.inventory.InventoryConstraints
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.world.inventory.AbstractFurnaceMenu
import net.minecraft.world.inventory.AbstractFurnaceMenu.FUEL_SLOT
import net.minecraft.world.inventory.AbstractFurnaceMenu.INGREDIENT_SLOT
import net.minecraft.world.inventory.AbstractFurnaceMenu.RESULT_SLOT
import net.minecraft.world.inventory.AbstractFurnaceMenu.SLOT_COUNT
import net.minecraft.world.inventory.BlastFurnaceMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.SmokerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.RecipePropertySet

/**
 * Keeps an open furnace, blast furnace or smoker going: takes out what is done, loads allowed ingredients and
 * puts in a little fuel once the fire is out. What does not fit the inventory stays in the furnace.
 */
object ModuleAutoSmelter : ClientModule("AutoSmelter", ExtrasCategories.GRINDING) {

    private val inputs by items(
        "Inputs",
        linkedSetOf(
            Items.IRON_ORE, Items.DEEPSLATE_IRON_ORE, Items.RAW_IRON,
            Items.GOLD_ORE, Items.DEEPSLATE_GOLD_ORE, Items.RAW_GOLD,
            Items.COPPER_ORE, Items.DEEPSLATE_COPPER_ORE, Items.RAW_COPPER,
        ),
    )
    private val fuels by items("Fuels", linkedSetOf(Items.COAL, Items.CHARCOAL))
    private val refill by int("Refill", 1, 1..64, "items")
    private val constraints = tree(InventoryConstraints())

    @Suppress("unused")
    private val smeltHandler = handler<ScheduleInventoryActionEvent> { event ->
        val screen = mc.gui.screen() as? AbstractFurnaceScreen<*> ?: return@handler
        val menu = screen.menu
        if (!menu.carried.isEmpty) {
            return@handler
        }

        val fuel = menu.getSlot(FUEL_SLOT).item
        val inventory = SLOT_COUNT until menu.slots.size
        val actions = when {
            fits(menu, RESULT_SLOT) -> listOf(quickMove(screen, RESULT_SLOT))
            // What is left of a lava bucket
            fuel.`is`(Items.BUCKET) && fits(menu, FUEL_SLOT) -> listOf(quickMove(screen, FUEL_SLOT))
            !menu.getSlot(INGREDIENT_SLOT).hasItem() ->
                inventory.firstOrNull { isInput(menu, it) }?.let { listOf(quickMove(screen, it)) }
            fuel.isEmpty && !menu.isLit && isInput(menu, INGREDIENT_SLOT) ->
                inventory.firstOrNull { isFuel(menu, it) }?.let { refuel(screen, it) }
            else -> null
        } ?: return@handler
        event.schedule(constraints, actions)
    }

    private fun isInput(menu: AbstractFurnaceMenu, slot: Int): Boolean {
        val recipes = when (menu) {
            is BlastFurnaceMenu -> RecipePropertySet.BLAST_FURNACE_INPUT
            is SmokerMenu -> RecipePropertySet.SMOKER_INPUT
            else -> RecipePropertySet.FURNACE_INPUT
        }
        val stack = menu.getSlot(slot).item
        return stack.item in inputs && world.recipeAccess().propertySet(recipes).test(stack)
    }

    private fun isFuel(menu: AbstractFurnaceMenu, slot: Int): Boolean {
        val stack = menu.getSlot(slot).item
        return stack.item in fuels && stack.has(DataComponents.COOKING_FUEL)
    }

    private fun fits(menu: AbstractFurnaceMenu, slot: Int): Boolean {
        val stack = menu.getSlot(slot).item
        return !stack.isEmpty && (SLOT_COUNT until menu.slots.size).any {
            val other = menu.getSlot(it).item
            other.isEmpty || ItemStack.isSameItemSameComponents(other, stack) && other.count < other.maxStackSize
        }
    }

    private fun quickMove(screen: AbstractFurnaceScreen<*>, slot: Int) =
        InventoryAction.Click.performQuickMove(screen, ContainerItemSlot(slot))

    // Like a player would: pick up the stack, drop single items into the slot, put the rest back
    private fun refuel(screen: AbstractFurnaceScreen<*>, from: Int) = buildList {
        val source = ContainerItemSlot(from)
        val target = ContainerItemSlot(FUEL_SLOT)
        add(InventoryAction.Click.performPickup(screen, source))
        if (refill >= screen.menu.getSlot(from).item.count) {
            add(InventoryAction.Click.performPickup(screen, target))
        } else {
            repeat(refill) { add(InventoryAction.Click(screen, target, 1, ContainerInput.PICKUP)) }
            add(InventoryAction.Click.performPickup(screen, source))
        }
    }

}
