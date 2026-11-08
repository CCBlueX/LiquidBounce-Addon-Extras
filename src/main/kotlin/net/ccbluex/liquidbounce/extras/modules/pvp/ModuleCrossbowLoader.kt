package net.ccbluex.liquidbounce.extras.modules.pvp

import net.ccbluex.liquidbounce.event.events.KeybindIsPressedEvent
import net.ccbluex.liquidbounce.event.events.ScheduleInventoryActionEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.tickUntil
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.markAsError
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.PlayerInventoryConstraints
import net.minecraft.world.item.CrossbowItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Loads a crossbow and switches itself off: takes one from the hotbar or the inventory, holds use until it is loaded,
 * and goes back to the slot in hand. KeepInHand holds the loaded crossbow instead until it is fired or put away.
 */
object ModuleCrossbowLoader : ClientModule("CrossbowLoader", ExtrasCategories.PVP, disableOnQuit = true) {

    private const val LOAD_TIMEOUT = 100
    private const val RELEASE_TIMEOUT = 5

    // The server loads it on its own count of use ticks, which can trail ours
    private const val MARGIN = 2

    private val keepInHand by boolean("KeepInHand", false)
    private val constraints = tree(PlayerInventoryConstraints())

    private var pulling = false
    private var holdingUse = false

    @Suppress("unused")
    private val pullHandler = handler<ScheduleInventoryActionEvent> { event ->
        if (pulling) {
            event.pullToHotbar(constraints, ::isUnloaded)
        }
    }

    @Suppress("unused")
    private val useKeyHandler = handler<KeybindIsPressedEvent> { event ->
        if (holdingUse && event.keyBinding == mc.options.keyUse) {
            event.isPressed = true
        }
    }

    @Suppress("unused")
    private val loadHandler = tickHandler {
        val ready = hotbarSlot(::isUnloaded)
        when {
            player.getProjectile(ItemStack(Items.CROSSBOW)).isEmpty -> report("noAmmo")
            ready != null -> load(ready)
            inInventory(::isUnloaded) -> pullAndLoad()
            else -> report("noCrossbow")
        }
        enabled = false
    }

    override fun onDisabled() {
        pulling = false
        holdingUse = false
        SilentHotbar.resetSlot(this)
    }

    private suspend fun pullAndLoad() {
        pulling = true
        tickUntil { hotbarSlot(::isUnloaded) != null || it >= PULL_TIMEOUT }
        pulling = false
        hotbarSlot(::isUnloaded)?.let { load(it) }
    }

    private suspend fun load(slot: HotbarItemSlot) {
        SilentHotbar.selectSlotSilently(this, slot, 1)
        holdingUse = true
        tickUntil {
            SilentHotbar.selectSlotSilently(this, slot, 1)
            val crossbow = slot.itemStack
            !crossbow.`is`(Items.CROSSBOW) || it >= LOAD_TIMEOUT || CrossbowItem.isCharged(crossbow) &&
                player.ticksUsingItem >= CrossbowItem.getChargeDuration(crossbow, player) + MARGIN
        }
        holdingUse = false
        // Vanilla lets go once the key is up; the slot may only change after that
        tickUntil {
            SilentHotbar.selectSlotSilently(this, slot, 1)
            !player.isUsingItem || it >= RELEASE_TIMEOUT
        }
        if (keepInHand && CrossbowItem.isCharged(slot.itemStack)) {
            hold(slot)
        }
        SilentHotbar.resetSlot(this)
    }

    /** Holds [slot] for real, so a click fires it; once it is fired, the old slot comes back. */
    private suspend fun hold(slot: HotbarItemSlot) {
        val index = slot.hotbarIndex ?: return
        val previous = player.inventory.selectedSlot
        player.inventory.selectedSlot = index
        SilentHotbar.resetSlot(this)
        tickUntil { player.inventory.selectedSlot != index || !CrossbowItem.isCharged(slot.itemStack) }
        // Put away by hand, it stays put away
        if (player.inventory.selectedSlot == index) {
            player.inventory.selectedSlot = previous
        }
    }

    private fun report(key: String) = chat(markAsError(message(key)), this)

    private fun isUnloaded(stack: ItemStack) = stack.`is`(Items.CROSSBOW) && !CrossbowItem.isCharged(stack)

}
