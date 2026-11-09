package net.ccbluex.liquidbounce.extras.modules.pvp

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.eventListenerScope
import net.ccbluex.liquidbounce.event.events.AttackEntityEvent
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickConditional
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.aiming.NormalRotationMode
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.utils.raytraceBox
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.combat.attackEntity
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.item.getEnchantment
import net.ccbluex.liquidbounce.utils.item.isAxe
import net.ccbluex.liquidbounce.utils.item.isSword
import net.ccbluex.liquidbounce.utils.raytracing.isLookingAtEntity
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityEvent
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.MaceItem
import net.minecraft.world.item.component.PiercingWeapon
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.floor
import kotlin.math.max
import kotlin.random.Random

/**
 * Spear tricks. Reach stabs with a spear what your sword, axe or mace cannot reach. DoubleTap follows a charge
 * hit that lands while you fall with a mace hit on the same enemy. Indicator shows what your charge would deal to
 * the nearest enemy right now.
 */
object ModuleSpearAssist : ClientModule("SpearAssist", ExtrasCategories.PVP) {

    // Ticks the mace waits for the enemy to come in reach after the charge hit
    private const val FOLLOW_UP = 5

    // Ticks between the server's charge hit and us handling its entity event
    private const val HIT_LAG_TICKS = 3

    private const val BAR_WIDTH = 60
    private const val BAR_HEIGHT = 3
    private const val TEXT_OFFSET = 12
    private const val TICKS_PER_SECOND = 20.0
    private const val PERCENT = 100

    object Reach : ToggleableValueGroup(ModuleSpearAssist, "Reach", true) {
        val chance by int("Chance", 100, 0..100, "%")
        val weapons by multiEnumChoice("Weapons", Weapon.SWORD, Weapon.AXE, Weapon.MACE)
    }

    enum class Weapon(override val tag: String, val matches: (ItemStack) -> Boolean) : Tagged {
        SWORD("Sword", { it.isSword }),
        AXE("Axe", { it.isAxe }),
        MACE("Mace", { it.isMace }),
    }

    object DoubleTap : ToggleableValueGroup(ModuleSpearAssist, "DoubleTap", true) {
        val minFall by float("MinFall", 3f, 1.5f..30f, "blocks")
        val densityFrom by float("DensityFrom", 8f, 1.5f..60f, "blocks")
        val aimMode = modes(this, "RotationMode") { arrayOf(NormalRotationMode(it, this)) }
    }

    object Indicator : ToggleableValueGroup(ModuleSpearAssist, "Indicator", true) {
        val range by float("Range", 12f, 3f..64f, "blocks")
        val color by color("Color", Color4b(90, 200, 255))
    }

    init {
        tree(Reach)
        tree(DoubleTap)
        tree(Indicator)
    }

    /** What a charge hit would deal to [estimateTarget] right now, 0 while it would not hit; null when not charging. */
    var estimate: Int? = null
        private set

    var estimateTarget: LivingEntity? = null
        private set

    private var pendingStab: Job? = null

    /**
     * Called by the attack key before vanilla handles it. Returns true when it takes the click: the held weapon
     * misses an enemy a spear would hit, or a stab is already on its way.
     */
    @JvmStatic
    fun stabBeyondReach(): Boolean {
        if (!running || !Reach.enabled || player.isHandsBusy || interaction.isSpectator) {
            return false
        }
        if (pendingStab?.isActive == true) {
            return true
        }
        val held = player.mainHandItem
        if (Reach.weapons.none { it.matches(held) } || player.cannotAttackWithItem(held, 0) || !rollsReach()) {
            return false
        }
        val spear = reachSpear() ?: return false
        val hit = spearTarget(spear)?.takeIf(::isBeyondReach) ?: return false
        stabWhenCharged(spear, hit.entity)
        return true
    }

    @Suppress("unused")
    private val attackHandler = handler<AttackEntityEvent> { event ->
        // Aura modules aim further than the hand reaches
        if (!Reach.enabled || Reach.weapons.none { it.matches(player.mainHandItem) }) {
            return@handler
        }
        val spear = reachSpear()?.takeIf { rollsReach() } ?: return@handler
        val hit = spearTarget(spear) ?: return@handler
        if (hit.entity === event.entity && isBeyondReach(hit)) {
            event.cancelEvent()
            stabWhenCharged(spear, hit.entity)
        }
    }

    @Suppress("unused")
    private val chargeHitHandler = handler<PacketEvent> { event ->
        val packet = event.packet as? ClientboundEntityEventPacket ?: return@handler
        if (packet.eventId == EntityEvent.KINETIC_HIT) {
            mc.execute { onChargeHit(packet) }
        }
    }

    @Suppress("unused")
    private val indicatorHandler = handler<OverlayRenderEvent> { event ->
        val damage = estimate ?: return@handler
        val target = estimateTarget ?: return@handler
        val context = event.context
        val x = context.guiWidth() / 2
        val y = context.guiHeight() / 2 + TEXT_OFFSET
        val color = if (damage > 0) Indicator.color else Color4b.GRAY
        context.centeredText(mc.font, message("estimate", damage).string, x, y, color.argb)
        val filled = (BAR_WIDTH * (damage / target.health.coerceAtLeast(1f)).coerceAtMost(1f)).toInt()
        val barY = y + mc.font.lineHeight + 1
        context.fill(x - BAR_WIDTH / 2, barY, x + BAR_WIDTH / 2, barY + BAR_HEIGHT, Color4b.BLACK.alpha(140).argb)
        context.fill(x - BAR_WIDTH / 2, barY, x - BAR_WIDTH / 2 + filled, barY + BAR_HEIGHT, color.argb)
    }

    @Suppress("unused")
    private val estimateHandler = handler<GameTickEvent> {
        estimateTarget = null
        estimate = null
        if (!Indicator.enabled || !player.isUsingItem) {
            return@handler
        }
        val kinetic = player.useItem.get(DataComponents.KINETIC_WEAPON) ?: return@handler
        val area = player.boundingBox.inflate(Indicator.range.toDouble())
        val target = world.getEntitiesOfClass(LivingEntity::class.java, area) {
            it !== player && it.isAlive && it.shouldBeAttacked()
        }.minByOrNull { it.distanceToSqr(player) } ?: return@handler

        // The server's formula, see KineticWeapon.damageEntities
        val look = RotationManager.serverRotation.directionVector
        val ownSpeed = look.dot(velocity(player).scale(TICKS_PER_SECOND))
        val relativeSpeed = max(0.0, ownSpeed - look.dot(velocity(target).scale(TICKS_PER_SECOND)))
        val ticksUsed = player.ticksUsingItem - kinetic.delayTicks()
        val hits = kinetic.damageConditions()
            .filter { ticksUsed >= 0 && it.test(ticksUsed, ownSpeed, relativeSpeed, 1.0) }
        estimateTarget = target
        estimate = if (hits.isPresent) {
            player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE).toInt() +
                floor(relativeSpeed * kinetic.damageMultiplier()).toInt()
        } else {
            0
        }
    }

    override fun onDisabled() {
        SilentHotbar.resetSlot(this)
        pendingStab = null
        estimate = null
        estimateTarget = null
    }

    private fun onChargeHit(packet: ClientboundEntityEventPacket) {
        val fallen = player.fallDistance >= DoubleTap.minFall
        if (!running || !DoubleTap.enabled || packet.getEntity(world) !== player || !fallen) {
            return
        }
        val spear = player.useItem.takeIf { it.has(DataComponents.KINETIC_WEAPON) } ?: return
        // The server does not say whom it hit, and by the time we hear of it we have fallen on past where it hit
        val reach = spear.get(DataComponents.ATTACK_RANGE)?.effectiveMaxRange(player)?.toDouble() ?: return
        val search = reach + player.deltaMovement.length() * HIT_LAG_TICKS
        val eyes = player.eyePosition
        val enemy = world.getEntitiesOfClass(LivingEntity::class.java, player.boundingBox.inflate(search)) {
            isEnemy(it) && it.boundingBox.distanceToSqr(eyes) <= search * search
        }.minByOrNull { it.boundingBox.distanceToSqr(eyes) } ?: return
        val mace = bestMace(player.fallDistance, DoubleTap.densityFrom) ?: return

        // Switching to the mace ends the charge on both sides. A release would come first, and Grim drops an attack
        // in the tick of a release, while waiting a tick falls past the enemy
        SilentHotbar.selectSlotSilently(this, mace, FOLLOW_UP + 1)
        eventListenerScope.launch {
            var hit = false
            tickConditional(FOLLOW_UP) {
                if (hit || !enemy.isAlive || !MaceItem.canSmashAttack(player)) {
                    return@tickConditional true
                }
                val reachNow = player.entityInteractionRange()
                val (rotation, _) = raytraceBox(player.eyePosition, enemy.boundingBox, reachNow, 0.0)
                    ?: return@tickConditional false
                DoubleTap.aimMode.activeMode.rotate(rotation, isFinished = { inReach(enemy) }) {
                    if (!hit && inReach(enemy)) {
                        attackEntity(enemy, SwingMode.DO_NOT_HIDE)
                        hit = true
                    }
                }
                false
            }
        }
    }

    private fun inReach(enemy: LivingEntity) = isLookingAtEntity(
        toEntity = enemy,
        rotation = RotationManager.serverRotation,
        range = player.entityInteractionRange(),
        throughWallsRange = 0.0,
    ) != null

    private fun isBeyondReach(hit: EntityHitResult) =
        player.eyePosition.distanceTo(hit.location) > player.entityInteractionRange()

    private fun rollsReach() = Random.nextInt(PERCENT) < Reach.chance

    // What the server measures as well; remote entities have no motion of their own on the client
    private fun velocity(entity: Entity) = Vec3(entity.x - entity.xo, entity.y - entity.yo, entity.z - entity.zo)

    private fun isEnemy(entity: Entity) =
        entity is LivingEntity && PiercingWeapon.canHitEntity(player, entity) && entity.shouldBeAttacked()

    // Lunge would throw you forward, so those are left for InstantLunge
    private fun reachSpear(): HotbarItemSlot? = Slots.Hotbar.findSlot {
        it.has(DataComponents.PIERCING_WEAPON) && it.has(DataComponents.ATTACK_RANGE) &&
            it.getEnchantment(Enchantments.LUNGE) == 0 && !it.nextDamageWillBreak()
    }

    private fun spearTarget(spear: HotbarItemSlot): EntityHitResult? {
        val range = spear.itemStack.get(DataComponents.ATTACK_RANGE) ?: return null
        return range.getClosesetHit(player, 1f, ::isEnemy) as? EntityHitResult
    }

    // The spear waits in hand until it is charged; Paper resets the charge on the switch
    private fun stabWhenCharged(spear: HotbarItemSlot, target: Entity) {
        if (pendingStab?.isActive == true) {
            return
        }
        val charge = spear.itemStack.chargeTicks
        SilentHotbar.selectSlotSilently(this, spear, charge + 1)
        pendingStab = eventListenerScope.launch {
            waitTicks(charge)
            val stack = spear.itemStack
            val piercing = stack.get(DataComponents.PIERCING_WEAPON)
            if (piercing == null || spearTarget(spear)?.entity !== target) {
                SilentHotbar.resetSlot(this@ModuleSpearAssist)
                return@launch
            }
            // Back to the weapon you held with the next tick
            SilentHotbar.selectSlotSilently(this@ModuleSpearAssist, spear, 1)
            interaction.piercingAttack(stack.attackAnimation, piercing)
        }
    }

}
