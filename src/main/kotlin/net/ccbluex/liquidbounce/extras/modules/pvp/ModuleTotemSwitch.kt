package net.ccbluex.liquidbounce.extras.modules.pvp

import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.tickUntil
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.combat.findEnemies
import net.ccbluex.liquidbounce.utils.entity.getDamageFromExplosion
import net.ccbluex.liquidbounce.utils.entity.getEffectiveDamage
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.tags.ItemTags
import net.minecraft.world.attribute.EnvironmentAttributes
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemAttributeModifiers
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RespawnAnchorBlock
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Holds a hotbar totem while the worst hit you could take next would kill you: crystals and charged anchors
 * around you, spots an enemy could crystal, an enemy coming down on you with a mace, or a sword hit at low
 * health, and optionally while your own ender pearl is in the air. Once nothing threatens you for a while, the
 * slot you had goes back into your hand.
 */
object ModuleTotemSwitch : ClientModule("TotemSwitch", ExtrasCategories.PVP) {

    private const val CRYSTAL_POWER = 6f
    private const val ANCHOR_POWER = 5f
    private const val CRIT = 1.5f
    private const val SMASH_MIN_FALL = 1.5

    private val margin by float("Margin", 2f, 0f..10f, "HP")
    private val range by float("Range", 6f, 2f..12f, "blocks")
    private val crystals by boolean("Crystals", true)
    private val anchors by boolean("Anchors", true)
    private val placements by boolean("Placements", true)
    private val enemyReach by float("EnemyReach", 4.5f, 1f..6f, "blocks")
    private val mace by boolean("Mace", true)
    private val sword by boolean("Sword", true)
    private val lowHealth by float("LowHealth", 8f, 1f..20f, "HP")
    private val pearl by boolean("Pearl", false)
    private val returnDelay by int("ReturnDelay", 10, 0..60, "ticks")

    /** The slot to give back, while the totem this module picked is in your hand. */
    var previousSlot: Int? = null
        private set

    @Suppress("unused")
    private val totemHandler = tickHandler {
        if (paused() || !inDanger()) {
            return@tickHandler
        }
        val totem = Slots.Hotbar.findSlot(Items.TOTEM_OF_UNDYING)?.hotbarIndex ?: return@tickHandler
        val previous = player.inventory.selectedSlot
        if (previous == totem) {
            return@tickHandler
        }
        player.inventory.selectedSlot = totem
        previousSlot = previous

        // Scrolling away hands the hotbar back to you
        var safe = 0
        tickUntil {
            safe = if (inDanger()) 0 else safe + 1
            player.inventory.selectedSlot != totem || safe > returnDelay
        }
        if (player.inventory.selectedSlot == totem) {
            player.inventory.selectedSlot = previous
        }
        previousSlot = null
    }

    override fun onDisabled() {
        val previous = previousSlot ?: return
        mc.player?.inventory?.selectedSlot = previous
        previousSlot = null
    }

    private fun paused() = mc.gui.screen() is AbstractContainerScreen<*> || player.isBlocking ||
        SilentHotbar.isSlotModified() || ModuleCrystalMacro.busy || crystalling()

    private fun crystalling(): Boolean {
        val options = mc.options
        val holding = player.mainHandItem.`is`(Items.END_CRYSTAL) || player.offhandItem.`is`(Items.END_CRYSTAL)
        val aimedAtCrystal = (mc.hitResult as? EntityHitResult)?.entity is EndCrystal
        return holding && options.keyUse.isDown || aimedAtCrystal && options.keyAttack.isDown
    }

    private fun inDanger(): Boolean {
        val limit = player.health + player.absorptionAmount - margin
        val pearlInFlight = pearl && world.entitiesForRendering().any { it is ThrownEnderpearl && it.owner === player }
        return pearlInFlight || threats(limit).any { it >= limit }
    }

    private fun threats(limit: Float) = sequence {
        val enemies = world.findEnemies(0f, range + enemyReach).mapNotNull { it.key() as? LivingEntity }
        if (crystals) {
            world.getEntitiesOfClass(EndCrystal::class.java, player.boundingBox.inflate(range.toDouble()))
                .forEach { yield(explosion(it.position(), CRYSTAL_POWER, null, limit)) }
        }
        for (enemy in enemies) {
            if (mace) {
                yield(smash(enemy))
            }
            if (sword && player.health <= lowHealth) {
                yield(slash(enemy))
            }
        }
        if (anchors || placements) {
            yieldAll(blockThreats(enemies, limit))
        }
    }

    // A crystal further out than the range cannot kill, so the cube around you is all there is to look at
    private fun blockThreats(enemies: List<LivingEntity>, limit: Float) = sequence {
        val span = ceil(range).toInt()
        val center = player.blockPosition()
        val reachSquared = enemyReach * enemyReach.toDouble()
        for (pos in BlockPos.betweenClosed(center.offset(-span, -span, -span), center.offset(span, span, span))) {
            val state = world.getBlockState(pos)
            val middle = Vec3.atCenterOf(pos)
            when {
                anchors && state.`is`(Blocks.RESPAWN_ANCHOR) && state.getValue(RespawnAnchorBlock.CHARGE) > 0 &&
                    world.environmentAttributes().getValue(EnvironmentAttributes.RESPAWN_ANCHOR_WORKS, pos) != true ->
                    yield(explosion(middle, ANCHOR_POWER, pos.immutable(), limit))

                placements && (state.`is`(Blocks.OBSIDIAN) || state.`is`(Blocks.BEDROCK)) &&
                    world.isEmptyBlock(pos.above()) && world.isEmptyBlock(pos.above(2)) &&
                    enemies.any { it.eyePosition.distanceToSqr(middle) <= reachSquared } ->
                    yield(explosion(Vec3.atBottomCenterOf(pos.above()), CRYSTAL_POWER, null, limit))
            }
        }
    }

    private fun explosion(center: Vec3, power: Float, removed: BlockPos?, limit: Float): Float {
        // Most spots are too far to matter even without cover, which saves tracing the rays
        val reach = power * 2
        val impact = 1 - sqrt(player.distanceToSqr(center)) / reach
        if (impact <= 0) {
            return 0f
        }
        val uncovered = ((impact * impact + impact) / 2 * 7 * reach + 1).toFloat()
        if (player.getEffectiveDamage(player.damageSources().explosion(null, null), uncovered) < limit) {
            return 0f
        }
        return player.getDamageFromExplosion(center, power, exclude = removed?.let(::listOf))
    }

    // Damage by fall height, see MaceItem.getAttackDamageBonus
    private fun smash(enemy: LivingEntity): Float {
        val fall = enemy.y - player.y
        val horizontal = enemy.position().subtract(player.position()).horizontalDistance()
        if (!enemy.mainHandItem.`is`(Items.MACE) || fall <= SMASH_MIN_FALL || enemy.y >= enemy.yOld ||
            horizontal > enemyReach
        ) {
            return 0f
        }
        val bonus = when {
            fall <= 3 -> 4 * fall
            fall <= 8 -> 12 + 2 * (fall - 3)
            else -> 22 + fall - 8
        }
        return hit(enemy, weaponDamage(enemy.mainHandItem) + bonus.toFloat())
    }

    private fun slash(enemy: LivingEntity): Float {
        val stack = enemy.mainHandItem
        if (!stack.`is`(ItemTags.SWORDS) || enemy.distanceTo(player) > enemyReach) {
            return 0f
        }
        val damage = weaponDamage(stack)
        return hit(enemy, if (enemy is Player) damage * CRIT else damage)
    }

    private fun hit(enemy: LivingEntity, damage: Float): Float {
        val source = if (enemy is Player) {
            player.damageSources().playerAttack(enemy)
        } else {
            player.damageSources().mobAttack(enemy)
        }
        return player.getEffectiveDamage(source, damage)
    }

    private fun weaponDamage(stack: ItemStack) =
        stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
            .compute(Attributes.ATTACK_DAMAGE, 1.0, EquipmentSlot.MAINHAND).toFloat()

}
