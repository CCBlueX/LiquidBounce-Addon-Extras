package net.ccbluex.liquidbounce.extras.modules.pvp

import com.mojang.blaze3d.platform.InputConstants
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.eventListenerScope
import net.ccbluex.liquidbounce.event.events.AttackEntityEvent
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.KeyEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationsValueGroup
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.combat.attackEntity
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.entity.wouldBlockHit
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.item.isAxe
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.raytracing.isLookingAtEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.MaceItem
import net.minecraft.world.level.block.Blocks
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.random.Random

/**
 * Holds the best mace while you fall onto an enemy and can swing it for you: Breach for short falls, Density
 * for long ones. A raised shield takes an axe hit first, which disables it for the mace that follows. An elytra
 * makes way for a chestplate before the impact, since a mace only smashes when you are not gliding.
 */
object ModuleMaceAssist : ClientModule("MaceAssist", ExtrasCategories.PVP) {

    // Ticks the mace stays selected without a refresh, so it goes once nobody is below any more
    private const val HOLD = 2

    private const val SEARCH_DEPTH = 64.0

    // Sinking faster than this while gliding, in blocks per tick, is a dive and no take-off
    private const val DIVE_SPEED = -0.5

    // The glide ends on our side only once the server says so, about a round trip after the swap
    private const val GLIDE_END_TICKS = 5

    private const val MS_PER_TICK = 50.0
    private const val PERCENT = 100f

    // Blocks between two looks for a cobweb on the line of sight
    private const val WEB_STEP = 0.1

    private val minFall by float("MinFall", 3f, 1.5f..30f, "blocks")
    private val radius by float("Radius", 4f, 0.5f..12f, "blocks")
    private val densityFrom by float("DensityFrom", 8f, 1.5f..60f, "blocks")
    private val autoHit by enumChoice("AutoHit", AutoHit.OFF)
    private val maxYaw by float("MaxYaw", 90f, 1f..180f, "degrees")
    private val chestplate by boolean("Chestplate", true)
    private val chestplateDistance by float("ChestplateDistance", 10f, 2f..30f, "blocks")
    private val switchBack by int("SwitchBack", 100, 0..2000, "ms")
    private val rotations = tree(RotationsValueGroup(this))

    enum class AutoHit(override val tag: String) : Tagged {
        OFF("Off"),
        TRIGGER("Trigger"),
        AIM("Aim"),
    }

    enum class StunTrigger(override val tag: String) : Tagged {
        ALWAYS("Always"),
        CHANCE("Chance"),
        KEY("Key"),
    }

    object StunSlam : ToggleableValueGroup(ModuleMaceAssist, "StunSlam", true) {
        val trigger by enumChoice("Trigger", StunTrigger.ALWAYS)
        val chance by int("Chance", 50, 0..100, "%")
        val scaleWithFall by boolean("ScaleWithFall", false)
        val stunKey by key("Key")
        val axeHold by int("AxeHold", 0, 0..10, "ticks")
        val cooldown by int("Cooldown", 0, 0..5000, "ms")

        /** The chance in percent that the current fall stuns a raised shield first; full from DensityFrom on. */
        val chanceNow: Float
            get() = if (scaleWithFall) {
                chance * (player.fallDistance / densityFrom).toFloat().coerceIn(0f, 1f)
            } else {
                chance.toFloat()
            }
    }

    init {
        tree(StunSlam)
    }

    /** The enemy below you the mace is held for. */
    var target: LivingEntity? = null
        private set

    // The server resets the fall on a smash, the client does not
    private var struck = false

    private var axeHit: Job? = null
    private var stunCooldown: Job? = null
    private var stunKeyHeld = false

    @Suppress("unused")
    private val maceHandler = handler<GameTickEvent> {
        if (player.onGround() || player.isFallFlying) {
            struck = false
        }
        val enemy = if (struck) null else enemyBelow()
        target = enemy
        enemy ?: return@handler

        // A use and an attack in the same tick is not something a hand does. The way down while the glide still
        // ends comes on top of the distance, or a fast dive reaches the ground before the mace may smash.
        val swapAt = chestplateDistance + player.deltaMovement.length() * GLIDE_END_TICKS
        if (chestplate && isDiving() && player.distanceTo(enemy) <= swapAt && wearChest(glider = false)) {
            return@handler
        }
        // A web on the way catches the swing, and you with it
        val aims = autoHit == AutoHit.AIM && !webBetween(enemy)
        // Turning starts with the fall, not with the smash: a fall closes the last blocks within a tick or two
        if (aims && player.deltaMovement.y < 0.0) {
            turnTo(enemy)
        }
        if (axeHit?.isActive == true || !MaceItem.canSmashAttack(player) || player.fallDistance < minFall) {
            return@handler
        }
        val mace = bestMace(player.fallDistance, densityFrom) ?: return@handler
        SilentHotbar.selectSlotSilently(this@ModuleMaceAssist, mace, HOLD)
        // The rotation that already went out has to face the enemy; the next movement packet may be the landing
        if ((aims || autoHit == AutoHit.TRIGGER) && inReach(enemy)) {
            attackEntity(enemy, SwingMode.DO_NOT_HIDE)
        }
    }

    @Suppress("unused")
    private val attackHandler = handler<AttackEntityEvent> { event ->
        if (!SilentHotbar.isSlotModifiedBy(this) || !player.mainHandItem.isMace) {
            return@handler
        }
        val enemy = event.entity as? LivingEntity
        val axe = Slots.Hotbar.findSlot { it.isAxe }
        if (enemy != null && axe != null && wantsStun(enemy)) {
            stun(event, enemy, axe)
            return@handler
        }
        struck = true
        SilentHotbar.selectSlotSilently(this, SilentHotbar.serversideSlot, ticks(switchBack))
    }

    @Suppress("unused")
    private val keyHandler = handler<KeyEvent> { event ->
        if (event.key == StunSlam.stunKey && event.action != InputConstants.REPEAT) {
            stunKeyHeld = event.action == InputConstants.PRESS
        }
    }

    override fun onDisabled() {
        SilentHotbar.resetSlot(this)
        target = null
        struck = false
        axeHit = null
        stunCooldown = null
        stunKeyHeld = false
    }

    private fun wantsStun(enemy: LivingEntity): Boolean {
        if (!StunSlam.enabled || !enemy.wouldBlockHit || ModuleShieldDrain.draining || stunCooldown?.isActive == true) {
            return false
        }
        return when (StunSlam.trigger) {
            StunTrigger.ALWAYS -> true
            StunTrigger.CHANCE -> Random.nextFloat() * PERCENT < StunSlam.chanceNow
            StunTrigger.KEY -> stunKeyHeld
        }
    }

    private fun stun(event: AttackEntityEvent, enemy: LivingEntity, axe: HotbarItemSlot) {
        if (StunSlam.cooldown > 0) {
            stunCooldown = eventListenerScope.launch { waitTicks(ticks(StunSlam.cooldown)) }
        }
        val hold = StunSlam.axeHold
        if (hold == 0) {
            // The mace comes back with the next tick, by then the shield is down
            SilentHotbar.selectSlotSilently(this, axe, 1)
            return
        }
        // The server resets the swing charge on a slot change; the axe gets its time before it swings
        event.cancelEvent()
        SilentHotbar.selectSlotSilently(this, axe, hold + 1)
        axeHit = eventListenerScope.launch {
            waitTicks(hold)
            if (enemy.isAlive && inReach(enemy)) {
                attackEntity(enemy, SwingMode.DO_NOT_HIDE)
            }
        }
    }

    private fun ticks(ms: Int) = ceil(ms / MS_PER_TICK).toInt().coerceAtLeast(1)

    private fun enemyBelow() = world.getEntitiesOfClass(
        LivingEntity::class.java,
        player.boundingBox.inflate(radius.toDouble(), 0.0, radius.toDouble()).expandTowards(0.0, -SEARCH_DEPTH, 0.0),
    ) { it !== player && it.isAlive && it.y < player.y && it.shouldBeAttacked() }
        .filter { horizontalDistance(it) <= radius }
        .minByOrNull { it.distanceToSqr(player) }

    private fun horizontalDistance(entity: LivingEntity) =
        player.position().subtract(entity.position()).horizontalDistance()

    private fun isDiving() = player.isFallFlying && wearsGlider && player.deltaMovement.y < DIVE_SPEED &&
        !ModuleElytraDive.climbing

    private fun webBetween(enemy: LivingEntity): Boolean {
        val eyes = player.eyePosition
        val aim = enemy.boundingBox.center
        val steps = ceil(eyes.distanceTo(aim) / WEB_STEP).toInt().coerceAtLeast(1)
        return (0..steps).any {
            world.getBlockState(BlockPos.containing(eyes.lerp(aim, it / steps.toDouble()))).`is`(Blocks.COBWEB)
        }
    }

    private fun turnTo(enemy: LivingEntity) {
        // Where the eyes are when the next movement packet goes out
        val rotation = Rotation.lookingAt(enemy.boundingBox.center, player.eyePosition.add(player.deltaMovement))
        if (abs(RotationManager.serverRotation.rotationDeltaTo(rotation).deltaYaw) <= maxYaw) {
            val target = rotations.toRotationTarget(rotation)
            RotationManager.setRotationTarget(target, Priority.IMPORTANT_FOR_USAGE_2, this)
        }
    }

    private fun inReach(enemy: LivingEntity) = isLookingAtEntity(
        toEntity = enemy,
        rotation = RotationManager.serverRotation,
        range = player.entityInteractionRange(),
        throughWallsRange = 0.0,
    ) != null

}
