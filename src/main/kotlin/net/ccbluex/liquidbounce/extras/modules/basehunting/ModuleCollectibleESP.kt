package net.ccbluex.liquidbounce.extras.modules.basehunting

import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.extras.util.BlockTracker
import net.ccbluex.liquidbounce.extras.util.drawMarkers
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.block.ChunkScanner
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.AbstractBannerBlock

/** Valuables on display and banners, both put there by players. */
object ModuleCollectibleESP : ClientModule("CollectibleESP", ExtrasCategories.BASE_HUNTING) {

    private val range by int("Range", 64, 8..128, "blocks")
    private val items by items(
        "Items",
        linkedSetOf(
            Items.FILLED_MAP, Items.ELYTRA, Items.TOTEM_OF_UNDYING, Items.ENCHANTED_GOLDEN_APPLE, Items.MACE,
            Items.TRIDENT, Items.BEACON, Items.END_CRYSTAL, Items.NETHERITE_SWORD, Items.NETHERITE_PICKAXE,
            Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS,
            Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE,
            Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS, Items.SHULKER_BOX,
        ),
    )
    private val enchantedGearOnly by boolean("EnchantedGearOnly", false)
    private val banners by boolean("Banners", true)
    private val color by color("Color", Color4b(255, 215, 50, 90))

    val findings: Set<BlockPos>
        field = HashSet<BlockPos>()

    private val bannerTracker = BlockTracker { state -> state.block.takeIf { it is AbstractBannerBlock } }

    @Suppress("unused")
    private val scanHandler = tickHandler {
        val range = range.toDouble()
        findings.clear()
        world.getEntitiesOfClass(ItemFrame::class.java, player.boundingBox.inflate(range))
            .filter { isCollectible(it.item) }
            .mapTo(findings) { it.blockPosition() }
        if (banners) {
            for (pos in bannerTracker.allPositions()) {
                if (pos.closerToCenterThan(player.position(), range)) {
                    findings += pos.immutable()
                }
            }
        }
        waitTicks(10)
    }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event -> event.drawMarkers(findings, color) }

    override fun onEnabled() = ChunkScanner.subscribe(bannerTracker)

    override fun onDisabled() {
        ChunkScanner.unsubscribe(bannerTracker)
        findings.clear()
    }

    private fun isCollectible(stack: ItemStack) = stack.item in items &&
        (!enchantedGearOnly || !stack.has(DataComponents.MAX_DAMAGE) || stack.isEnchanted)

}
