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
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Blocks with a shield while enabled, taken from the hotbar or the inventory; the slot in hand comes back once it is
 * off. Bound to a key, that is a shield on one press and the weapon on the next, or a shield while the key is held.
 */
object ModuleShieldSwap : ClientModule("ShieldSwap", ExtrasCategories.PVP, disableOnQuit = true) {

    private val constraints = tree(PlayerInventoryConstraints())

    private var pulling = false
    private var blocking = false

    /** The hotbar slot of the shield in hand. */
    var shield: HotbarItemSlot? = null
        private set

    @Suppress("unused")
    private val pullHandler = handler<ScheduleInventoryActionEvent> { event ->
        if (pulling) {
            event.pullToHotbar(constraints, ::isShield)
        }
    }

    @Suppress("unused")
    private val useKeyHandler = handler<KeybindIsPressedEvent> { event ->
        if (blocking && event.keyBinding == mc.options.keyUse) {
            event.isPressed = true
        }
    }

    @Suppress("unused")
    private val blockHandler = tickHandler {
        val slot = hotbarSlot(::isShield) ?: pull() ?: return@tickHandler
        shield = slot
        SilentHotbar.selectSlotSilently(this@ModuleShieldSwap, slot, 1)
        blocking = true
    }

    override fun onDisabled() {
        // Vanilla lets go of the shield once the key is up, or as soon as the slot changes back
        pulling = false
        blocking = false
        shield = null
        SilentHotbar.resetSlot(this)
    }

    private suspend fun pull(): HotbarItemSlot? {
        blocking = false
        if (!inInventory(::isShield)) {
            chat(markAsError(message("noShield")), this)
            enabled = false
            return null
        }
        pulling = true
        tickUntil { hotbarSlot(::isShield) != null || it >= PULL_TIMEOUT }
        pulling = false
        return hotbarSlot(::isShield)
    }

    private fun isShield(stack: ItemStack) = stack.`is`(Items.SHIELD)

}
