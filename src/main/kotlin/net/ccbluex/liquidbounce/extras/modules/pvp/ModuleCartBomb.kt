package net.ccbluex.liquidbounce.extras.modules.pvp

import com.mojang.blaze3d.platform.InputConstants
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.event.eventListenerScope
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.KeyEvent
import net.ccbluex.liquidbounce.event.events.KeybindIsPressedEvent
import net.ccbluex.liquidbounce.event.events.ScheduleInventoryActionEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.tickConditional
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.aiming.NoRotationMode
import net.ccbluex.liquidbounce.utils.aiming.NormalRotationMode
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.inventory.PlayerInventoryConstraints
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.render.trajectory.TrajectoryInfo
import net.ccbluex.liquidbounce.utils.render.trajectory.TrajectoryInfoRenderer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.BlockTags
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.CrossbowItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult

/**
 * Lays a rail with TNT minecarts on the block you look at and sets them off with an arrow, or lays them where
 * your own shot comes down.
 */
object ModuleCartBomb : ClientModule("CartBomb", ExtrasCategories.PVP) {

    private const val MAX_FLIGHT = 200

    // An arrow lands up to a tick off the prediction; after that it has missed the cart
    private const val ARRIVAL_SLACK = 3

    // Below this a bow lets go without shooting
    private const val MIN_POWER = 0.1f

    private val bombKey by key("Key")
    private val onShot by boolean("OnShot", true)
    // Rail, cart and their rotations take about this long
    private val lead by int("Lead", 8, 4..40, "ticks")
    private val carts by int("Carts", 1, 1..4)
    private val ignite by boolean("Ignite", true)
    private val charge by int("Charge", 20, 3..20, "ticks")
    private val switchBack by boolean("SwitchBack", true)

    private object SafeBlock : ToggleableValueGroup(this, "SafeBlock", false) {
        val blocks by blocks("Blocks", linkedSetOf(Blocks.OBSIDIAN, Blocks.COBBLESTONE))
    }

    /** While you draw a bow, aims at a cart that already sits next to an enemy and lets go once the draw is enough. */
    private object HitPlaced : ToggleableValueGroup(this, "HitPlaced", false) {
        val range by float("Range", 8f, 4f..16f, "blocks")
        val nearEnemy by float("NearEnemy", 4f, 1f..8f, "blocks")
        val minDraw by int("MinDraw", 10, 3..20, "ticks")
    }

    /** A second key that stacks more carts in one go, with a little time between them. */
    private object Overload : ValueGroup("Overload") {
        val bind by key("Key")
        val carts by int("Carts", 3, 2..8)
        val delay by intRange("Delay", 1..3, 0..10, "ticks")
        val hotbarOnly by boolean("HotbarOnly", true)
        val constraints = tree(PlayerInventoryConstraints())
    }

    init {
        tree(SafeBlock)
        tree(HitPlaced)
        tree(Overload)
    }

    private val rotationMode = modes(this, "RotationMode") {
        arrayOf(NormalRotationMode(it, this), NoRotationMode(it, this))
    }

    private val rig = CartRig(this, rotationMode)

    private var job: Job? = null

    /** The bow being drawn by hand and for how long, or a loaded crossbow in hand, as of the last tick. */
    private var drawnBow = 0
    private var loadedCrossbow = false
    private var aim: Rotation? = null

    /** Set while vanilla is to let go of your bow: the use key counts as released. */
    private var releasing = false
    private var pulling = false

    /** Where our last shot was predicted to land, as the spot the rail goes to. */
    var shotRail: BlockPos? = null
        private set

    @Suppress("unused")
    private val keyHandler = handler<KeyEvent> { event ->
        val overload = event.key == Overload.bind
        if (event.action != InputConstants.PRESS || event.key != bombKey && !overload || mc.gui.screen() != null) {
            return@handler
        }
        val hit = mc.hitResult as? BlockHitResult ?: return@handler
        if (hit.type != HitResult.Type.BLOCK) {
            return@handler
        }
        val rail = when {
            world.getBlockState(hit.blockPos).`is`(BlockTags.RAILS) -> hit.blockPos
            hit.direction == Direction.UP -> hit.blockPos.above()
            else -> return@handler
        }
        launch { if (overload) overload(rail) else bomb(rail, ignite) }
    }

    @Suppress("unused")
    private val pullHandler = handler<ScheduleInventoryActionEvent> { event ->
        if (pulling) {
            event.pullToHotbar(Overload.constraints) { it.isTntCart }
        }
    }

    @Suppress("unused")
    private val releaseHandler = handler<KeybindIsPressedEvent> { event ->
        if (releasing && event.keyBinding === mc.options.keyUse) {
            event.isPressed = false
        }
    }

    @Suppress("unused")
    private val hitPlacedHandler = tickHandler {
        val bow = player.useItem.takeIf { player.isUsingItem && it.item is BowItem }
        if (bow == null) {
            releasing = false
            return@tickHandler
        }
        if (!HitPlaced.enabled || releasing || job?.isActive == true || RigShots.drawing > 0) {
            return@tickHandler
        }
        val cart = placedCart() ?: return@tickHandler
        // The arrow leaves with next tick's draw
        val draw = maxOf(player.ticksUsingItem + 1, HitPlaced.minDraw)
        val rotation = CartAim.shotAt(cart, bow, draw) ?: return@tickHandler
        rotationMode.activeMode.rotate(rotation, isFinished = { CartAim.aimed(rotation) }) {
            if (player.ticksUsingItem >= HitPlaced.minDraw && CartAim.aimed(rotation)) {
                releasing = true
                RigShots.fired()
            }
        }
    }

    /**
     * Notices our own shots: a bow let go, or a crossbow that is no longer loaded. The rotation the shot went
     * out with is the one the server had the tick before.
     */
    @Suppress("unused")
    private val shotHandler = handler<GameTickEvent> {
        val busy = job?.isActive == true || RigShots.recent()
        val using = player.useItem.takeIf { player.isUsingItem }
        val crossbow = player.mainHandItem.isLoadedCrossbow || player.offhandItem.isLoadedCrossbow
        val released = drawnBow.takeIf { it > 0 && using == null }
        val fired = loadedCrossbow && !crossbow
        val rotation = aim

        drawnBow = if (using?.item is BowItem && !busy) player.ticksUsingItem else 0
        loadedCrossbow = crossbow && !busy
        aim = RotationManager.serverRotation

        if (!onShot || busy || rotation == null) {
            return@handler
        }
        val info = when {
            released != null && BowItem.getPowerForTime(released) >= MIN_POWER ->
                TrajectoryInfo.bowWithUsageDuration(released)
            fired -> TrajectoryInfo.CROSSBOW_ARROW
            else -> null
        } ?: return@handler
        val burning = released != null && (player.mainHandItem.isFlameBow || player.offhandItem.isFlameBow)
        val (rail, ticks) = landing(info, rotation) ?: return@handler
        if (rail.distToCenterSqr(player.eyePosition) <= rig.reach * rig.reach &&
            world.getBlockState(rail).let { it.canBeReplaced() || it.`is`(BlockTags.RAILS) }
        ) {
            shotRail = rail
            // Detected a tick after the shot
            launch { shotBomb(rail, ticks - 1, burning) }
        }
    }

    override fun onDisabled() {
        job = null
        drawnBow = 0
        loadedCrossbow = false
        aim = null
        shotRail = null
        releasing = false
        pulling = false
        rig.reset()
    }

    private fun launch(action: suspend () -> Unit) {
        if (job?.isActive != true) {
            job = eventListenerScope.launch { action() }
        }
    }

    /**
     * A burning arrow sets the carts off itself, if they are down in time. Any other arrow would only break
     * them, so they go down once it has landed.
     */
    private suspend fun shotBomb(rail: BlockPos, arrival: Int, burning: Boolean) {
        val shot = world.gameTime
        if (burning && arrival >= lead) {
            val cart = rig.setUp(rail, carts) ?: return
            waitTicks((arrival - (world.gameTime - shot)).toInt().coerceAtLeast(0) + ARRIVAL_SLACK)
            if (cart.isAlive && !cart.isPrimed && ignite) {
                Igniter.find()?.let { rig.ignite(cart, it, charge) }
            }
        } else {
            waitTicks(arrival + ARRIVAL_SLACK)
            bomb(rail, ignite)
        }
    }

    private suspend fun overload(rail: BlockPos) {
        for (index in 0 until Overload.carts) {
            if (index > 0) {
                waitTicks(Overload.delay.random())
            }
            if (!Overload.hotbarOnly && hotbarSlot { it.isTntCart } == null && inInventory { it.isTntCart }) {
                pulling = true
                val timedOut = tickConditional(PULL_TIMEOUT) { hotbarSlot { it.isTntCart } != null }
                pulling = false
                if (timedOut) {
                    break
                }
            }
            val before = CartAim.carts(rail).size
            if (rig.setUp(rail, before + 1) == null || CartAim.carts(rail).size == before) {
                break
            }
        }
        bomb(rail, ignite, CartAim.carts(rail).size)
    }

    private suspend fun bomb(rail: BlockPos, ignite: Boolean, count: Int = carts) {
        val cart = rig.setUp(rail, count) ?: return
        if (ignite) {
            val igniter = Igniter.find() ?: return
            if (SafeBlock.enabled) {
                shield(cart)
            }
            rig.ignite(cart, igniter, charge)
        }
        if (switchBack) {
            SilentHotbar.resetSlot(this)
            Slots.Hotbar.findSlot { it.isLauncher() }?.hotbarIndex?.let { player.inventory.selectedSlot = it }
        }
    }

    /** A block next to our feet on the cart's side, unless it would be in the arrow's way. */
    private suspend fun shield(cart: MinecartTNT) {
        val slot = Slots.OffhandWithHotbar.findSlot { (it.item as? BlockItem)?.block in SafeBlock.blocks } ?: return
        val toCart = cart.position().subtract(player.position())
        val cell = player.blockPosition().relative(Direction.getApproximateNearest(toCart.x, 0.0, toCart.z))
        if (world.getBlockState(cell).canBeReplaced() &&
            !AABB(cell).clip(CartAim.arrowOrigin, CartAim.aimPoint(cart)).isPresent
        ) {
            rig.place(cell, slot)
        }
    }

    /** The rail spot where an arrow shot with [info] facing [rotation] comes down, and in how many ticks. */
    private fun landing(info: TrajectoryInfo, rotation: Rotation): Pair<BlockPos, Int>? {
        val simulation = TrajectoryInfoRenderer.getHypotheticalTrajectory(player, info, ARROW_TRAJECTORY, rotation)
            .runSimulation(MAX_FLIGHT)
        val hit = simulation.hitResult as? BlockHitResult ?: return null
        if (hit.type != HitResult.Type.BLOCK || hit.direction != Direction.UP) {
            return null
        }
        return hit.blockPos.above() to simulation.positions.size
    }

    /** The unlit cart closest to where you look, in [HitPlaced.range] and next to someone to hit. */
    private fun placedCart(): MinecartTNT? {
        val look = player.lookAngle
        val near = HitPlaced.nearEnemy.toDouble()
        val enemies = world.entitiesForRendering().filter { it !== player && it.shouldBeAttacked() }
        return world.getEntitiesOfClass(
            MinecartTNT::class.java, player.boundingBox.inflate(HitPlaced.range.toDouble()),
        ) { cart ->
            cart.isAlive && !cart.isPrimed && CartAim.clearShot(CartAim.aimPoint(cart)) &&
                enemies.any { it.distanceToSqr(cart) <= near * near }
        }.minByOrNull { look.dot(CartAim.aimPoint(it).subtract(player.eyePosition).normalize()).unaryMinus() }
    }

    private fun ItemStack.isLauncher() = item is BowItem || item is CrossbowItem

}
