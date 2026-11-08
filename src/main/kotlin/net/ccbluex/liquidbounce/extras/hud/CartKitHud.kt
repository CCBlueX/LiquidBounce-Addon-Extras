package net.ccbluex.liquidbounce.extras.hud

import net.ccbluex.liquidbounce.extras.modules.pvp.canShoot
import net.ccbluex.liquidbounce.extras.modules.pvp.isLoadedCrossbow
import net.ccbluex.liquidbounce.extras.modules.pvp.isRail
import net.ccbluex.liquidbounce.extras.modules.pvp.isTntCart
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.CrossbowItem
import kotlin.math.roundToInt

/** Whether the TNT minecart kit is ready: rails, carts, something to shoot them with, and its draw. */
object CartKitHud : TextHudComponent(
    "CartKit", "Rails, TNT minecarts and a launcher in your hotbar, the draw, and live carts around you.", "",
    row = 9,
) {

    private const val BAR = 10

    private val radius by int("Radius", 16, 4..64)

    override fun value(): Component {
        val player = mc.player ?: return Component.literal("N/A")
        val rails = Slots.OffhandWithHotbar.sumOf { if (it.itemStack.isRail) it.itemStack.count else 0 }
        val carts = Slots.OffhandWithHotbar.sumOf { if (it.itemStack.isTntCart) it.itemStack.count else 0 }
        val launcher = Slots.OffhandWithHotbar.map { it.itemStack }
            .filter { it.item is BowItem || it.item is CrossbowItem }
            .maxByOrNull { if (it.canShoot()) 1 else 0 }
        val live = player.level().getEntitiesOfClass(
            MinecartTNT::class.java, player.boundingBox.inflate(radius.toDouble()),
        ) { it.isAlive }.size

        return Component.empty()
            .append(count("Rails ", rails))
            .append("  ")
            .append(count("Carts ", carts))
            .append("  ")
            .append(
                when {
                    launcher == null -> Component.literal("No launcher").withStyle(ChatFormatting.RED)
                    else -> Component.literal(if (launcher.item is CrossbowItem) "Crossbow " else "Bow ")
                        .withStyle(ready(launcher.canShoot()))
                },
            )
            .append(bar(draw()))
            .append("  Live $live")
    }

    /** How far the bow or crossbow in use is drawn, a loaded crossbow counting as full. */
    private fun draw(): Float {
        val player = mc.player ?: return 0f
        val using = player.useItem
        return when {
            player.isUsingItem && using.item is BowItem -> BowItem.getPowerForTime(player.ticksUsingItem)
            player.isUsingItem && using.item is CrossbowItem ->
                player.ticksUsingItem.toFloat() / CrossbowItem.getChargeDuration(using, player)
            Slots.OffhandWithHotbar.any { it.itemStack.isLoadedCrossbow } -> 1f
            else -> 0f
        }.coerceIn(0f, 1f)
    }

    private fun bar(progress: Float): MutableComponent {
        val filled = (progress * BAR).roundToInt()
        return Component.literal("|".repeat(filled)).withStyle(ChatFormatting.GREEN)
            .append(Component.literal("|".repeat(BAR - filled)).withStyle(ChatFormatting.DARK_GRAY))
    }

    private fun count(label: String, amount: Int) =
        Component.literal("$label$amount").withStyle(ready(amount > 0))

    private fun ready(ready: Boolean) = if (ready) ChatFormatting.GREEN else ChatFormatting.RED

}
