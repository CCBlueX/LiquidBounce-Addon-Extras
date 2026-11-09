package net.ccbluex.liquidbounce.extras.modules.pvp

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.eventListenerScope
import net.ccbluex.liquidbounce.event.events.AttackEntityEvent
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.RotationUpdateEvent
import net.ccbluex.liquidbounce.event.events.ScheduleInventoryActionEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.tickUntil
import net.ccbluex.liquidbounce.event.waitSeconds
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationsValueGroup
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.features.MovementCorrection
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager
import net.ccbluex.liquidbounce.utils.inventory.PlayerInventoryConstraints
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.inventory.useHotbarSlotOrOffhand
import net.ccbluex.liquidbounce.utils.item.isChestArmor
import net.ccbluex.liquidbounce.utils.item.isGlider
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.MaceItem
import net.minecraft.world.phys.Vec3

/**
 * Takes off for you: puts the elytra on, jumps, opens it and fires a rocket, and after a while puts the
 * chestplate back on. Elytra, rockets and chestplate come from the inventory when the hotbar has none, but only
 * on the ground. While gliding, the jump key fires another rocket. ReDive takes off again once it lands after a
 * mace hit, climbs above the same enemy and steers back down onto it.
 */
object ModuleElytraDive : ClientModule("ElytraDive", ExtrasCategories.PVP) {

    private const val TAKE_OFF_TIMEOUT = 20
    private const val LAND_TIMEOUT = 20
    private const val CLIMB_TIMEOUT = 200
    private const val TAKE_OFF_ATTEMPTS = 2
    private const val CLIMB_ATTEMPTS = 3
    private const val DIVE_TIMEOUT = 200

    // Climbing slower than this, in blocks per tick, a rocket is due
    private const val CLIMB_SPEED = 0.4

    private val flightTime by int("FlightTime", 5, 0..60, "seconds")
    private val rocketOnJump by boolean("RocketOnJump", true)
    private val rocketDelay by int("RocketDelay", 10, 1..60, "ticks")
    private val requireLand by boolean("RequireLand", true)
    private val constraints = tree(PlayerInventoryConstraints())

    object ReDive : ToggleableValueGroup(ModuleElytraDive, "ReDive", false) {
        val height by float("Height", 12f, 4f..40f, "blocks")
        val releaseDistance by float("ReleaseDistance", 5f, 2f..20f, "blocks")
        // A rotation that moves the camera between ticks makes rocket uses disagree with the movement packets
        val rotations = tree(RotationsValueGroup(this, MovementCorrection.STRICT))
    }

    init {
        tree(ReDive)
    }

    /** The point ReDive steers the glide to. */
    var steerAt: Vec3? = null
        private set

    /** Whether ReDive is on its way up above the enemy, where a sag before the next rocket is no dive. */
    var climbing = false
        private set

    private var jumpInput = false
    private var flight: Job? = null

    private enum class Use { ELYTRA, CHESTPLATE, ROCKET }

    private var pendingUse: Use? = null

    /** What goes from the inventory to the hotbar right now. */
    private var fetching: ((ItemStack) -> Boolean)? = null

    @Suppress("unused")
    private val fetchHandler = handler<ScheduleInventoryActionEvent> { event ->
        fetching?.let { event.pullToHotbar(constraints, it) }
    }

    /*
     * Coroutines resume before the client picks this tick's rotation. A use from there would carry last tick's
     * rotation while the movement packet after it carries the new one, which Grim flags as BadPacketsJ. So uses
     * wait for this handler, which runs after the rotation update and before the movement packet.
     */
    @Suppress("unused")
    private val useHandler = handler<GameTickEvent> {
        pendingUse ?: return@handler
        when (pendingUse ?: return@handler) {
            Use.ELYTRA -> wearChest(glider = true)
            Use.CHESTPLATE -> wearChest(glider = false)
            Use.ROCKET -> fireRocket()
        }
        pendingUse = null
    }

    @Suppress("unused")
    private val inputHandler = handler<MovementInputEvent> { event ->
        if (jumpInput) {
            event.jump = true
        }
    }

    @Suppress("unused")
    private val rocketHandler = tickHandler {
        if (rocketOnJump && steerAt == null && player.isFallFlying && mc.options.keyJump.isDown) {
            use(Use.ROCKET)
            waitTicks(rocketDelay)
        }
    }

    @Suppress("unused")
    private val steerHandler = handler<RotationUpdateEvent> {
        val point = steerAt ?: return@handler
        RotationManager.setRotationTarget(
            ReDive.rotations.toRotationTarget(Rotation.lookingAt(point, player.eyePosition)),
            // Below aiming modules, so MaceAssist can turn to the enemy at the end of the dive
            Priority.IMPORTANT_FOR_USAGE_1,
            this,
        )
    }

    @Suppress("unused")
    private val hitHandler = handler<AttackEntityEvent> { event ->
        val enemy = event.entity as? LivingEntity ?: return@handler
        if (ReDive.enabled && flight?.isActive != true && player.mainHandItem.isMace &&
            MaceItem.canSmashAttack(player)
        ) {
            fly(enemy)
        }
    }

    // Runs once the module is on; a coroutine launched from onEnabled would find it still off
    override suspend fun enabledEffect() {
        if (requireLand) {
            // A mace hit on the way down may start a ReDive first
            tickUntil { player.onGround() || flight != null }
        }
        if (flight == null) {
            fly(null)
        }
    }

    override fun onDisabled() {
        flight?.cancel()
        flight = null
        steerAt = null
        climbing = false
        jumpInput = false
        pendingUse = null
        fetching = null
    }

    private fun fly(enemy: LivingEntity?) {
        flight?.cancel()
        flight = eventListenerScope.launch {
            try {
                if (enemy != null) {
                    // A smash leaves us a block or two above the ground, too low to open the glide on the way
                    // down; the jump from the ground gives it room
                    tickUntil { player.onGround() || it >= LAND_TIMEOUT }
                    // A rocket that does not carry ends the climb on the ground, and the next take-off starts over
                    repeat(CLIMB_ATTEMPTS) {
                        if (!takeOff() || diveAt(enemy)) {
                            return@launch
                        }
                    }
                } else if (takeOff() && flightTime > 0) {
                    waitSeconds(flightTime)
                    if (wearsGlider) {
                        restoreChestplate()
                    }
                }
            } finally {
                steerAt = null
                climbing = false
                jumpInput = false
            }
        }
    }

    private suspend fun takeOff(): Boolean {
        // Inventory clicks in mid-air are what inventory checks catch
        if (player.onGround()) {
            if (!wearsGlider) {
                fetch(::isWearableGlider)
            }
            if (Slots.OffhandWithHotbar.findSlot(Items.FIREWORK_ROCKET) == null) {
                fetch { it.`is`(Items.FIREWORK_ROCKET) }
            }
        }
        if (!wearsGlider) {
            if (hotbarSlot(::isWearableGlider) == null) {
                return false
            }
            use(Use.ELYTRA)
            waitTicks(1)
        }
        // Landing in between turns the press that should open the glide into a jump, then it takes another
        repeat(TAKE_OFF_ATTEMPTS) {
            if (player.onGround()) {
                pressJump()
                tickUntil { !player.onGround() || it >= TAKE_OFF_TIMEOUT }
            }
            // The glide opens on a fresh jump press in the air
            waitTicks(1)
            pressJump()
            if (player.isFallFlying) {
                use(Use.ROCKET)
                return true
            }
        }
        return false
    }

    /** Back to the chestplate; one from the inventory waits for the ground. */
    private suspend fun restoreChestplate() {
        val chestplate = { stack: ItemStack -> stack.isChestArmor }
        if (hotbarSlot(chestplate) == null && inInventory(chestplate)) {
            tickUntil { player.onGround() }
            fetch(chestplate)
        }
        use(Use.CHESTPLATE)
    }

    /** Brings a stack [wanted] accepts from the inventory onto the hotbar, unless one is there already. */
    private suspend fun fetch(wanted: (ItemStack) -> Boolean) {
        if (hotbarSlot(wanted) != null || !inInventory(wanted)) {
            return
        }
        fetching = wanted
        try {
            tickUntil { hotbarSlot(wanted) != null || it >= PULL_TIMEOUT }
        } finally {
            fetching = null
        }
        // Grim counts moving or using items before the inventory closed again as actions inside it
        tickUntil { !InventoryManager.isInventoryOpen || it >= PULL_TIMEOUT }
    }

    private fun isWearableGlider(stack: ItemStack) = stack.isGlider && !stack.nextDamageWillBreak()

    private suspend fun pressJump() {
        jumpInput = true
        waitTicks(1)
        jumpInput = false
    }

    /** Climbs above [enemy] and dives back onto it; false when the glide ended on the way up. */
    private suspend fun diveAt(enemy: LivingEntity): Boolean {
        var lastRocket = -rocketDelay
        climbing = true
        tickUntil { tick ->
            val above = enemy.position().add(0.0, ReDive.height.toDouble(), 0.0)
            steerAt = above
            if (player.deltaMovement.y < CLIMB_SPEED && tick - lastRocket >= rocketDelay) {
                use(Use.ROCKET)
                lastRocket = tick
            }
            !player.isFallFlying || !enemy.isAlive || player.y >= above.y || tick >= CLIMB_TIMEOUT
        }
        climbing = false
        if (!player.isFallFlying && enemy.isAlive) {
            steerAt = null
            return false
        }
        tickUntil { tick ->
            steerAt = enemy.boundingBox.center
            !player.isFallFlying || !enemy.isAlive || player.distanceTo(enemy) <= ReDive.releaseDistance ||
                tick >= DIVE_TIMEOUT
        }
        steerAt = null
        // Gliding, a mace does not smash
        if (player.isFallFlying && wearsGlider) {
            use(Use.CHESTPLATE)
        }
        return true
    }

    // Goes out later this tick, see useHandler
    private fun use(use: Use) {
        pendingUse = use
    }

    private fun fireRocket() {
        val rocket = Slots.OffhandWithHotbar.findSlot(Items.FIREWORK_ROCKET) ?: return
        useHotbarSlotOrOffhand(rocket)
    }

}
