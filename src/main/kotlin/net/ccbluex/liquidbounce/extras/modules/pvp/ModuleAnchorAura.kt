package net.ccbluex.liquidbounce.extras.modules.pvp

import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.tickUntil
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.extras.util.drawMarkers
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.render.FontManager
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.aiming.RotationsValueGroup
import net.ccbluex.liquidbounce.utils.client.FloatValueProvider
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.client.regular
import net.ccbluex.liquidbounce.utils.combat.TargetPriority
import net.ccbluex.liquidbounce.utils.combat.TargetTracker
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.render.WorldToScreen
import net.ccbluex.liquidbounce.utils.render.trajectory.TrajectoryInfo
import net.ccbluex.liquidbounce.utils.render.trajectory.TrajectoryInfoRenderer
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import java.util.Locale

/**
 * Places a respawn anchor next to a target, optionally covers it on your side, charges it and sets it off,
 * wherever the blast hurts the target enough and you little enough. Also meets enemy ender pearls where they
 * land.
 */
object ModuleAnchorAura : ClientModule("AnchorAura", ExtrasCategories.PVP, disableOnQuit = true) {

    private const val PEARL_TICKS = 200
    private const val ARRIVAL_RANGE = 2.5
    private const val ARRIVAL_SLACK = 20
    private const val TICKS_PER_SECOND = 20f

    private val targetTracker = tree(TargetTracker(TargetPriority.DISTANCE, FloatValueProvider("Range", 7f, 2f..12f)))
    private val reach by float("Reach", 4.5f, 1f..6f, "blocks")
    private val minDamage by float("MinDamage", 6f, 0f..36f, "HP")
    private val maxSelfDamage by float("MaxSelfDamage", 4f, 0f..36f, "HP")
    private val antiSuicide by boolean("AntiSuicide", true)
    private val cover by boolean("Cover", true)
    private val onlyExisting by boolean("OnlyExisting", false)
    private val placeDelay by intRange("PlaceDelay", 1..3, 0..10, "ticks")
    private val chargeDelay by intRange("ChargeDelay", 1..3, 0..10, "ticks")
    private val detonateDelay by intRange("DetonateDelay", 1..3, 0..10, "ticks")
    private val guard by boolean("Guard", true)
    private val color by color("Color", Color4b(255, 140, 0, 90))
    private val rotations = tree(RotationsValueGroup(this))

    private object PearlPunish : ToggleableValueGroup(ModuleAnchorAura, "PearlPunish", true) {
        val color by color("Color", Color4b(170, 80, 255, 90))
    }

    init {
        tree(PearlPunish)
    }

    /** Where an anchor goes, its cover on your side if it needs one, whom it is for. */
    class Plan(val anchor: BlockPos, val cover: BlockPos?, val target: LivingEntity, val landing: Landing?)

    /** Where an enemy pearl comes down, in how many ticks, and the cells it passes. */
    class Landing(
        val pearl: ThrownEnderpearl,
        val thrower: LivingEntity,
        val spot: Vec3,
        val ticks: Int,
        val path: Set<BlockPos>,
    ) {

        companion object {

            fun of(pearl: ThrownEnderpearl): Landing? {
                val thrower = pearl.owner as? LivingEntity ?: return null
                if (thrower === player || !thrower.shouldBeAttacked()) {
                    return null
                }
                val simulation = TrajectoryInfoRenderer(
                    simulationOwner = thrower,
                    displayOwner = thrower,
                    icon = Items.ENDER_PEARL.defaultInstance,
                    velocity = pearl.deltaMovement,
                    pos = pearl.position(),
                    trajectoryInfo = TrajectoryInfo.GENERIC,
                    trajectoryType = PEARL_TRAJECTORY,
                    type = TrajectoryInfoRenderer.Type.REAL,
                    renderOffset = Vec3.ZERO,
                ).runSimulation(PEARL_TICKS)
                val hit = simulation.hitResult as? BlockHitResult ?: return null
                val path = simulation.positions.mapTo(HashSet()) { BlockPos.containing(it) }
                return Landing(pearl, thrower, hit.location, simulation.positions.size, path)
            }

        }

    }

    private class Shelter(val cover: BlockPos?, val damage: Float)

    var plan: Plan? = null
        private set

    val landings: List<Landing>
        field = ArrayList<Landing>()

    private val range get() = reach.toDouble()

    @Suppress("unused")
    private val pearlHandler = handler<GameTickEvent> {
        landings.clear()
        if (PearlPunish.enabled) {
            world.entitiesForRendering().filterIsInstance<ThrownEnderpearl>().mapNotNullTo(landings, Landing::of)
        }
    }

    @Suppress("unused")
    private val auraHandler = tickHandler {
        AnchorKit.forgetCleared()
        // A cover going up comes first
        if (ModuleBlastCover.pending != null) {
            return@tickHandler
        }
        val next = pearlPlan() ?: targetTracker.select(::targetPlan) ?: return@tickHandler
        plan = next
        try {
            AnchorKit.guard(this@ModuleAnchorAura, guard) { cycle(next) }
        } finally {
            plan = null
        }
        waitTicks(detonateDelay.random())
    }

    @Suppress("unused")
    private val markerHandler = handler<WorldRenderEvent> { event ->
        plan?.let { event.drawMarkers(listOfNotNull(it.anchor, it.cover), color) }
        event.drawMarkers(landings.map { BlockPos.containing(it.spot) }, PearlPunish.color)
    }

    @Suppress("unused")
    private val flightTimeHandler = handler<OverlayRenderEvent> { event ->
        val font = FontManager.FONT_RENDERER
        for (landing in landings) {
            val screen = WorldToScreen.calculateScreenPos(landing.spot.add(0.0, 1.0, 0.0)) ?: continue
            val seconds = String.format(Locale.ROOT, "%.1f", landing.ticks / TICKS_PER_SECOND)
            val text = regular(message("landing", seconds))
            val x = screen.x - font.getStringWidth(text) / 2f
            font.draw(event.context, text, x, screen.y, PearlPunish.color.alpha(255))
        }
    }

    override fun onDisabled() {
        plan = null
        landings.clear()
        targetTracker.reset()
    }

    private suspend fun cycle(plan: Plan) {
        val anchor = plan.anchor
        if (!anchor.isAnchor()) {
            if (!place(anchor, Slots.Hotbar.findSlot(Items.RESPAWN_ANCHOR) ?: return)) {
                return
            }
            AnchorKit.rememberPlaced(anchor)
            waitTicks(placeDelay.random())
        }
        val cell = plan.cover
        if (cell != null && world.getBlockState(cell).canBeReplaced()) {
            if (!place(cell, coverSlot() ?: return)) {
                return
            }
            waitTicks(placeDelay.random())
        }
        if (anchor.charges() == 0) {
            ModuleBlastCover.coverBeforeCharge(anchor)
            if (!activate(anchor, Slots.Hotbar.findSlot(Items.GLOWSTONE) ?: return)) {
                return
            }
            waitTicks(chargeDelay.random())
        }
        plan.landing?.let { landing ->
            tickUntil {
                landing.thrower.isRemoved || it > landing.ticks + ARRIVAL_SLACK ||
                    landing.pearl.isRemoved && landing.thrower.position().closerThan(landing.spot, ARRIVAL_RANGE)
            }
        }
        if (!worthIt(plan)) {
            return
        }
        ModuleBlastCover.coverBefore(anchor)
        if (anchor.charges() == 0 || !activate(anchor, detonator() ?: return)) {
            return
        }
        AnchorKit.spend(anchor)
    }

    private suspend fun place(pos: BlockPos, slot: HotbarItemSlot) =
        clickAimed(rotations, find = { placeClick(pos, range).takeIf { canPlace(pos) } }) {
            use(it, slot)
        }

    private suspend fun activate(anchor: BlockPos, slot: HotbarItemSlot) =
        clickAimed(rotations, find = { if (anchor.isAnchor()) clickOn(anchor, range = range) else null }) {
            use(it, slot)
        }

    /** Checked right before the blast, with the world as it is by then. */
    private fun worthIt(plan: Plan): Boolean {
        val target = plan.target
        return target.isAlive && !target.isRemoved && plan.anchor.charges() > 0 &&
            target.anchorDamage(plan.anchor) >= minDamage && bearable(player.anchorDamage(plan.anchor))
    }

    private fun targetPlan(target: LivingEntity): Plan? {
        val box = target.boundingBox.inflate(1.0)
        val from = BlockPos.containing(box.minX, box.minY, box.minZ)
        return BlockPos.betweenClosed(from, BlockPos.containing(box.maxX, box.maxY, box.maxZ))
            .map { it.immutable() }
            .filter { usable(it) }
            .mapNotNull { anchor ->
                val shelter = shelter(anchor) { false } ?: return@mapNotNull null
                val damage = target.anchorDamage(anchor, shelter.cover)
                if (damage < minDamage) null else Triple(anchor, shelter, damage)
            }
            .sortedWith(compareByDescending<Triple<BlockPos, Shelter, Float>> { it.third }.thenBy { it.second.damage })
            .firstOrNull { (anchor) -> anchor.isAnchor() || placeClick(anchor, range) != null }
            ?.let { (anchor, shelter) -> Plan(anchor, shelter.cover, target, null) }
    }

    /** An anchor next to where a pearl lands, out of its way, that you can stand. */
    private fun pearlPlan(): Plan? {
        if (!PearlPunish.enabled) {
            return null
        }
        for (landing in landings.sortedBy { it.ticks }) {
            val thrower = landing.thrower
            val arrival = thrower.getDimensions(thrower.pose).makeBoundingBox(landing.spot)
            val inTheWay = { pos: BlockPos -> pos in landing.path || arrival.intersects(AABB(pos)) }
            val spot = BlockPos.containing(landing.spot)
            val best = BlockPos.betweenClosed(spot.offset(-1, 0, -1), spot.offset(1, 0, 1))
                .map { it.immutable() }
                .filter { !inTheWay(it) && usable(it) }
                .mapNotNull { anchor -> shelter(anchor, inTheWay)?.let { anchor to it } }
                .sortedBy { (anchor, shelter) -> shelter.damage + anchor.distToCenterSqr(landing.spot) }
                .firstOrNull { (anchor) -> anchor.isAnchor() || placeClick(anchor, range) != null }
                ?: continue
            return Plan(best.first, best.second.cover, thrower, landing)
        }
        return null
    }

    /** An empty cell in reach for a new anchor, or one of ours that is still there; with OnlyExisting any anchor. */
    private fun usable(pos: BlockPos): Boolean {
        if (pos in AnchorKit.spent || pos.distToCenterSqr(player.eyePosition) > range * range) {
            return false
        }
        return when {
            pos.isAnchor() -> onlyExisting || pos in AnchorKit.placed
            onlyExisting -> false
            else -> canPlace(pos, Blocks.RESPAWN_ANCHOR.defaultBlockState())
        }
    }

    /** How the blast of [anchor] stays within your limits: as it is, or behind a cover. */
    private fun shelter(anchor: BlockPos, avoid: (BlockPos) -> Boolean): Shelter? {
        val open = player.anchorDamage(anchor)
        if (bearable(open)) {
            return Shelter(null, open)
        }
        if (!cover || coverSlot() == null) {
            return null
        }
        val (cell, covered) = bestCover(anchor, range, avoid) ?: return null
        return if (bearable(covered)) Shelter(cell, covered) else null
    }

    private fun bearable(damage: Float) =
        damage <= maxSelfDamage && (!antiSuicide || damage < player.health + player.absorptionAmount)

    private fun coverSlot() = Slots.Hotbar.findSlot(Items.OBSIDIAN) ?: Slots.Hotbar.findSlot(Items.GLOWSTONE)

}
