package net.ccbluex.liquidbounce.extras.modules.pvp

import net.ccbluex.liquidbounce.additions.realSelectedSlot
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.client.world
import net.ccbluex.liquidbounce.utils.entity.getDamageFromExplosion
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.world.respawnAnchorWorks
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RespawnAnchorBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext

// RespawnAnchorBlock.explode, outside the Nether
private const val POWER = 5f

private val COVER_SIDES = Direction.entries.filter { it != Direction.DOWN }

fun BlockPos.isAnchor() = world.getBlockState(this).block is RespawnAnchorBlock

fun BlockPos.charges(): Int {
    val state = world.getBlockState(this)
    return if (state.block is RespawnAnchorBlock) state.getValue(RespawnAnchorBlock.CHARGE) else 0
}

/** What the blast of an anchor at [anchor] does to this entity, with [cover] standing as a full block. */
fun LivingEntity.anchorDamage(anchor: BlockPos, cover: BlockPos? = null): Float {
    val center = Vec3.atCenterOf(anchor)
    // The anchor is gone before the blast traces its rays
    return getDamageFromExplosion(
        pos = center,
        power = POWER,
        exclude = listOf(anchor),
        include = cover,
        damageSource = level().damageSources().badRespawnPointExplosion(center),
    )
}

/**
 * Whether using the main hand on [state] sets the anchor off rather than charging it.
 *
 * @see RespawnAnchorBlock.useItemOn
 */
fun detonatesOnUse(state: BlockState): Boolean {
    val charges = state.getValue(RespawnAnchorBlock.CHARGE)
    if (world.respawnAnchorWorks || charges == 0) {
        return false
    }
    if (player.isSecondaryUseActive && !(player.mainHandItem.isEmpty && player.offhandItem.isEmpty)) {
        return false
    }
    return charges == RespawnAnchorBlock.MAX_CHARGES ||
        !player.mainHandItem.`is`(Items.GLOWSTONE) && !player.offhandItem.`is`(Items.GLOWSTONE)
}

/**
 * A hotbar slot that sets an anchor off: an empty hand or any block but glowstone, the held one first.
 * Glowstone in the offhand would charge the anchor instead, see [RespawnAnchorBlock.useItemOn].
 */
fun detonator(): HotbarItemSlot? {
    if (player.offhandItem.`is`(Items.GLOWSTONE)) {
        return null
    }
    val selected = player.inventory.realSelectedSlot
    return HotbarItemSlot.entries.filter { !it.isOffHand }
        .sortedByDescending { it.hotbarIndex == selected }
        .firstOrNull { it.itemStack.setsOff() }
}

private fun ItemStack.setsOff() = isEmpty || item is BlockItem && !`is`(Items.GLOWSTONE)

/** Whether a block can go to [pos] now: replaceable and no entity in the way. */
fun canPlace(pos: BlockPos, state: BlockState = Blocks.OBSIDIAN.defaultBlockState()) =
    world.getBlockState(pos).canBeReplaced() && world.isUnobstructed(state, pos, CollisionContext.empty())

/**
 * The neighbour of [anchor] that keeps the most of its blast off the player, and the damage left behind it.
 * Cells [avoid] rejects stay free.
 */
fun bestCover(anchor: BlockPos, range: Double, avoid: (BlockPos) -> Boolean = { false }): Pair<BlockPos, Float>? =
    COVER_SIDES.asSequence()
        .map { anchor.relative(it) }
        .filter { !avoid(it) && canPlace(it) }
        .map { it to player.anchorDamage(anchor, it) }
        .sortedBy { it.second }
        .firstOrNull { placeClick(it.first, range) != null }
