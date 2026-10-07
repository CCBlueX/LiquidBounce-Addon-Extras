package net.ccbluex.liquidbounce.extras.modules.grinding

import net.ccbluex.liquidbounce.event.events.ScheduleInventoryActionEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.inventory.ContainerItemSlot
import net.ccbluex.liquidbounce.utils.inventory.InventoryAction
import net.ccbluex.liquidbounce.utils.inventory.InventoryConstraints
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.AbstractFurnaceMenu
import net.minecraft.world.inventory.BlastFurnaceMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.SmokerMenu
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.RecipePropertySet

object ModuleAutoSmelter : ClientModule("AutoSmelter", ExtrasCategories.GRINDING) {
    private val inputs by items("Inputs", linkedSetOf(
        Items.IRON_ORE, Items.DEEPSLATE_IRON_ORE, Items.RAW_IRON,
        Items.GOLD_ORE, Items.DEEPSLATE_GOLD_ORE, Items.RAW_GOLD,
        Items.COPPER_ORE, Items.DEEPSLATE_COPPER_ORE, Items.RAW_COPPER,
    ))
    private val fuels by items("Fuels", linkedSetOf(Items.COAL, Items.CHARCOAL))
    private val refill by int("Refill", 1, 1..64, "items")
    private val constraints = tree(InventoryConstraints())

    @Suppress("unused")
    private val smeltHandler = handler<ScheduleInventoryActionEvent> { event ->
        val screen = mc.gui.screen() as? AbstractContainerScreen<*> ?: return@handler
        val menu = screen.menu as? AbstractFurnaceMenu ?: return@handler
        if (player.isUsingItem) return@handler
        if (!menu.carried.isEmpty || player.isSprinting || !player.onGround() ||
            player.input.moveVector.lengthSquared() > 0 || player.deltaMovement.horizontalDistanceSqr() > 0.0001
        ) {
            return@handler
        }

        val result = menu.getSlot(AbstractFurnaceMenu.RESULT_SLOT).item
        if (!result.isEmpty) {
            if (hasSpace(menu, result)) {
                event.schedule(constraints, InventoryAction.Click.performQuickMove(
                    screen, ContainerItemSlot(AbstractFurnaceMenu.RESULT_SLOT),
                ))
            }
            return@handler
        }

        val fuelSlot = menu.getSlot(AbstractFurnaceMenu.FUEL_SLOT)
        val fuel = fuelSlot.item
        // A bucket left by lava belongs back in the player's inventory, even after the last input finishes.
        if (fuel.`is`(Items.BUCKET) && hasSpace(menu, fuel)) {
            event.schedule(constraints, InventoryAction.Click.performQuickMove(
                screen, ContainerItemSlot(AbstractFurnaceMenu.FUEL_SLOT),
            ))
            return@handler
        }
        val input = menu.getSlot(AbstractFurnaceMenu.INGREDIENT_SLOT).item
        val recipe = when (menu) {
            is BlastFurnaceMenu -> RecipePropertySet.BLAST_FURNACE_INPUT
            is SmokerMenu -> RecipePropertySet.SMOKER_INPUT
            else -> RecipePropertySet.FURNACE_INPUT
        }
        val accepted = world.recipeAccess().propertySet(recipe)
        if (input.isEmpty) {
            val from = (AbstractFurnaceMenu.SLOT_COUNT until menu.slots.size).firstOrNull {
                val stack = menu.getSlot(it).item
                stack.item in inputs && accepted.test(stack)
            } ?: return@handler
            event.schedule(constraints, transfer(screen, from, AbstractFurnaceMenu.INGREDIENT_SLOT,
                menu.getSlot(from).item.count))
            return@handler
        }

        if (input.item !in inputs || !accepted.test(input)) return@handler
        if (!fuel.isEmpty || menu.litProgress > 0) return@handler
        val from = (AbstractFurnaceMenu.SLOT_COUNT until menu.slots.size).firstOrNull {
            val stack = menu.getSlot(it).item
            !stack.isEmpty && stack.item in fuels && fuelSlot.mayPlace(stack) && !stack.`is`(Items.BUCKET)
        } ?: return@handler
        event.schedule(constraints, transfer(screen, from, AbstractFurnaceMenu.FUEL_SLOT,
            minOf(refill, menu.getSlot(from).item.count, fuelSlot.maxStackSize)))
    }

    private fun hasSpace(menu: AbstractFurnaceMenu, stack: net.minecraft.world.item.ItemStack): Boolean =
        (AbstractFurnaceMenu.SLOT_COUNT until menu.slots.size).sumOf { index ->
            val slot = menu.getSlot(index)
            val existing = slot.item
            when {
                existing.isEmpty -> slot.getMaxStackSize(stack)
                net.minecraft.world.item.ItemStack.isSameItemSameComponents(existing, stack) ->
                    (slot.getMaxStackSize(stack) - existing.count).coerceAtLeast(0)
                else -> 0
            }
        } >= stack.count

    private fun transfer(screen: AbstractContainerScreen<*>, from: Int, to: Int, amount: Int): List<InventoryAction> {
        val source = ContainerItemSlot(from)
        val destination = ContainerItemSlot(to)
        return buildList {
            add(InventoryAction.Click.performPickup(screen, source))
            if (amount == screen.menu.getSlot(from).item.count) {
                add(InventoryAction.Click.performPickup(screen, destination))
            } else {
                repeat(amount) { add(InventoryAction.Click(screen, destination, 1, ContainerInput.PICKUP)) }
                add(InventoryAction.Click.performPickup(screen, source))
            }
        }
    }
}
