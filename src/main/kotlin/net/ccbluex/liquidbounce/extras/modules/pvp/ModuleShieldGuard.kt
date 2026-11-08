package net.ccbluex.liquidbounce.extras.modules.pvp

import net.ccbluex.liquidbounce.event.events.AttackEntityEvent
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.KeybindIsPressedEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.combat.getEntitiesBoxInRange
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.entity.squaredBoxedDistanceTo
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.item.isAxe
import net.ccbluex.liquidbounce.utils.item.isSword
import net.minecraft.network.protocol.game.ClientboundAnimatePacket
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemUseAnimation
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3

/**
 * Raises a shield when a close enemy can swing again or runs at you, and lowers it while you fall onto a target, so
 * your own hit can be a critical one. While an axe has the shield disabled, a sword is in hand instead. Gliding,
 * the shield stays down.
 */
object ModuleShieldGuard : ClientModule("ShieldGuard", ExtrasCategories.PVP, disableOnQuit = true) {

    // ClientboundAnimatePacket.SWING_MAIN_HAND, not public
    private const val SWING_MAIN_HAND = 0
    private const val BASE_ATTACK_SPEED = 4.0
    private const val MOB_ATTACK_INTERVAL = 20.0

    // A swing counts as ready a little early; the guess cannot know the enemy's ping
    private const val COOLDOWN_SLACK = 0.9
    private const val CRIT_STRENGTH = 0.9f

    private val range by float("Range", 8f, 2f..16f, "blocks")
    private val reach by float("Reach", 3.5f, 2f..6f, "blocks")
    private val cooldown by boolean("Cooldown", true)
    private val rush by boolean("Rush", true)
    private val rushSpeed by float("RushSpeed", 0.25f, 0.05f..1f, "blocks/tick")
    private val crits by boolean("Crits", true)
    private val sword by boolean("Sword", true)
    private val rising by boolean("Rising", false)
    private val weaponsOnly by boolean("WeaponsOnly", false)
    private val lowerForAxes by boolean("LowerForAxes", false)
    private val holdAttacks by boolean("HoldAttacks", true)

    /** The enemy the shield is up against. */
    var threat: LivingEntity? = null
        private set

    private val swings = HashMap<Int, Int>()
    private var blocking = false

    @Suppress("unused")
    private val swingHandler = handler<PacketEvent> { event ->
        val packet = event.packet as? ClientboundAnimatePacket ?: return@handler
        if (packet.action == SWING_MAIN_HAND) {
            mc.execute { mc.player?.let { swings[packet.id] = it.tickCount } }
        }
    }

    @Suppress("unused")
    private val useKeyHandler = handler<KeybindIsPressedEvent> { event ->
        if (blocking && event.keyBinding == mc.options.keyUse) {
            event.isPressed = true
        }
    }

    // Vanilla drops attack clicks while an item is in use, so a hit behind a raised shield is no human's
    @Suppress("unused")
    private val attackHandler = handler<AttackEntityEvent> { event ->
        if (holdAttacks && blocking && player.isUsingItem) {
            event.cancelEvent()
        }
    }

    @Suppress("unused")
    private val guardHandler = handler<GameTickEvent> {
        val shield = shieldSlot()
        val threat = findThreat()
        this.threat = threat
        blocking = false
        when {
            shield == null -> {}
            player.cooldowns.isOnCooldown(shield.itemStack) -> if (sword) {
                Slots.Hotbar.findSlot { it.isSword }?.let { SilentHotbar.selectSlotSilently(this, it, 1) }
            }
            // Nothing to block while gliding, and the use key belongs to the rockets
            player.isFallFlying -> {}
            // An axe would disable it: taking the hit costs less than five seconds without a shield
            threat == null || lowerForAxes && threat.mainHandItem.isAxe -> {}
            !fallingOntoTarget() -> {
                blocking = true
                SilentHotbar.selectSlotSilently(this, shield, 1)
            }
        }
    }

    override fun onDisabled() {
        blocking = false
        threat = null
        swings.clear()
        SilentHotbar.resetSlot(this)
    }

    /** A shield in the offhand when the main hand has no use of its own, otherwise one on the hotbar. */
    private fun shieldSlot(): HotbarItemSlot? {
        if (player.offhandItem.`is`(Items.SHIELD) && player.mainHandItem.useAnimation == ItemUseAnimation.NONE) {
            return HotbarItemSlot.OFFHAND
        }
        return Slots.Hotbar.findSlot { it.`is`(Items.SHIELD) }
    }

    private fun findThreat(): LivingEntity? {
        val eyes = player.eyePosition
        // On the way up no hit of ours could be critical, so the shield costs nothing
        val rising = rising && !player.onGround() && !player.isFallFlying && player.deltaMovement.y > 0.0
        return world.getEntitiesBoxInRange(eyes, range.toDouble()) { it is LivingEntity && it.shouldBeAttacked() }
            .map { it as LivingEntity }
            .filter { !weaponsOnly || it.mainHandItem.isMeleeWeapon }
            .filter { inReach(it) && (rising || cooldown && swingReady(it)) || rush && rushes(it) }
            .minByOrNull { it.squaredBoxedDistanceTo(eyes) }
    }

    private fun inReach(enemy: LivingEntity): Boolean {
        val reach = reach.toDouble()
        return player.squaredBoxedDistanceTo(enemy.eyePosition) <= reach * reach
    }

    private fun swingReady(enemy: LivingEntity): Boolean {
        val lastSwing = swings[enemy.id] ?: return true
        return player.tickCount - lastSwing >= attackInterval(enemy) * COOLDOWN_SLACK
    }

    private val ItemStack.isMeleeWeapon get() = isSword || isAxe || isMace

    /** How long [enemy] needs between full swings: its weapon's attack speed for players, a second for mobs. */
    private fun attackInterval(enemy: LivingEntity): Double {
        if (enemy !is Player) {
            return MOB_ATTACK_INTERVAL
        }
        var speed = BASE_ATTACK_SPEED
        enemy.mainHandItem.forEachModifier(EquipmentSlot.MAINHAND) { attribute, modifier ->
            if (attribute == Attributes.ATTACK_SPEED && modifier.operation == AttributeModifier.Operation.ADD_VALUE) {
                speed += modifier.amount
            }
        }
        return 20.0 / speed.coerceAtLeast(0.1)
    }

    private fun rushes(enemy: LivingEntity): Boolean {
        val towardsUs = Vec3(player.x - enemy.x, 0.0, player.z - enemy.z).normalize()
        val motion = Vec3(enemy.x - enemy.xo, 0.0, enemy.z - enemy.zo)
        return motion.dot(towardsUs) >= rushSpeed
    }

    /** Falling with a full swing and a target in reach: the hit would be critical, which needs the shield down. */
    private fun fallingOntoTarget(): Boolean {
        if (!crits || player.onGround() || player.deltaMovement.y >= 0.0 || player.fallDistance <= 0.0) {
            return false
        }
        if (player.getAttackStrengthScale(0.5f) < CRIT_STRENGTH) {
            return false
        }
        val reach = player.entityInteractionRange()
        return world.getEntitiesBoxInRange(player.eyePosition, reach) { it.shouldBeAttacked() }.isNotEmpty()
    }

}
