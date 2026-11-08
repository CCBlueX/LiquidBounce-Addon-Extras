package net.ccbluex.liquidbounce.extras.modules.pvp

import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.minecraft.tags.ItemTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RespawnAnchorBlock
import net.minecraft.world.phys.BlockHitResult

/**
 * Drops clicks of yours that lose fights. Only what you click yourself is checked, see
 * `MinecraftMisplayGuardMixin`; other modules act as they are told.
 */
object ModuleMisplayGuard : ClientModule("MisplayGuard", ExtrasCategories.PVP) {

    private val chargedAnchor by boolean("ChargedAnchor", true)
    private val strayGlowstone by boolean("StrayGlowstone", true)
    private val enderChest by boolean("EnderChest", true)
    private val combatItems by items(
        "CombatItems",
        linkedSetOf(Items.END_CRYSTAL, Items.RESPAWN_ANCHOR, Items.GLOWSTONE, Items.OBSIDIAN, Items.TOTEM_OF_UNDYING),
    )
    private val groundFirework by boolean("GroundFirework", true)
    private val shieldHit by boolean("ShieldHit", true)

    /** Whether clicking [hit] with [hand] should not happen. */
    fun blocksUseOn(hand: InteractionHand, hit: BlockHitResult): Boolean {
        if (!running) {
            return false
        }
        val stack = player.getItemInHand(hand)
        val state = world.getBlockState(hit.blockPos)
        return when {
            // Another charge instead of the explosion
            stack.`is`(Items.GLOWSTONE) && state.`is`(Blocks.RESPAWN_ANCHOR) ->
                chargedAnchor && state.getValue(RespawnAnchorBlock.CHARGE) in 1 until RespawnAnchorBlock.MAX_CHARGES
            stack.`is`(Items.GLOWSTONE) -> strayGlowstone
            stack.`is`(Items.FIREWORK_ROCKET) ->
                groundFirework && player.onGround() && !player.offhandItem.`is`(Items.CROSSBOW)
            state.`is`(Blocks.ENDER_CHEST) -> enderChest && !player.isSecondaryUseActive &&
                InteractionHand.entries.any { player.getItemInHand(it).item in combatItems }
            else -> false
        }
    }

    /** Whether hitting [entity] should not happen. */
    fun blocksAttack(entity: Entity) =
        running && shieldHit && entity is Player && entity.isBlocking && player.mainHandItem.`is`(ItemTags.SWORDS)

}
