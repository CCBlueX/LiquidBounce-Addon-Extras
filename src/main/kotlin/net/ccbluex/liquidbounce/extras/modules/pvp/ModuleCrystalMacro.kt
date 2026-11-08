package net.ccbluex.liquidbounce.extras.modules.pvp

import com.mojang.blaze3d.platform.InputConstants
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.eventListenerScope
import net.ccbluex.liquidbounce.event.events.AttackEntityEvent
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.KeyEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickConditional
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.features.global.GlobalSettingsTarget
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.aiming.NoRotationMode
import net.ccbluex.liquidbounce.utils.aiming.NormalRotationMode
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.utils.raytraceBlockRotation
import net.ccbluex.liquidbounce.utils.aiming.utils.raytraceBox
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.block.doPlacement
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.combat.Targets
import net.ccbluex.liquidbounce.utils.combat.attackEntity
import net.ccbluex.liquidbounce.utils.combat.findEnemy
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.entity.interactBlock
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.raytracing.isLookingAtEntity
import net.ccbluex.liquidbounce.utils.raytracing.traceFromPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.tags.ItemTags
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext

/**
 * Crystals the block you look at: obsidian first if it is not a valid base, then the crystal, then the hits
 * that blow it up. Every click waits for the server to have the rotation it needs.
 */
@Suppress("TooManyFunctions")
object ModuleCrystalMacro : ClientModule("CrystalMacro", ExtrasCategories.PVP) {

    private const val AIM_TIMEOUT = 10
    private const val SPAWN_TIMEOUT = 20
    private const val GONE_TIMEOUT = 20
    private const val HURT_TIMEOUT = 10
    private const val ENEMY_RANGE = 12f
    private const val LOOT_RADIUS = 6.0

    private val trigger by key("Key")
    private val onSwordHit by boolean("OnSwordHit", false)
    private val breaks by enumChoice("Breaks", Breaks.ONCE)
    private val hurtTimeSync by boolean("HurtTimeSync", false)
    private val groundOnly by boolean("GroundOnly", true)
    private val lootPause by int("LootPause", 100, 0..600, "ticks")
    private val range by float("Range", 4.5f, 1f..6f, "blocks")
    private val slotResetDelay by int("SlotResetDelay", 2, 0..20, "ticks")
    private val swingMode by enumChoice("Swing", SwingMode.DO_NOT_HIDE)
    private val rotationMode = modes(this, "RotationMode") {
        arrayOf(NormalRotationMode(it, this), NoRotationMode(it, this))
    }

    /** Whether a run is between its first click and its last hit. */
    var busy = false
        private set

    /** Where an enemy died, as long as its loot may lie there and [lootPause] has not run out. */
    var lootSite: Vec3? = null
        private set

    private var pending = false
    private var lootTimer: Job? = null
    private val mourned = HashSet<Int>()

    @Suppress("unused")
    private val macroHandler = tickHandler {
        val triggered = pending
        pending = false
        val free = mc.gui.screen() == null && !player.isUsingItem && (!groundOnly || player.onGround())
        if (!triggered || !free || lootLies()) {
            return@tickHandler
        }
        busy = true
        try {
            crystal()
        } finally {
            busy = false
        }
    }

    @Suppress("unused")
    private val keyHandler = handler<KeyEvent> { event ->
        if (event.action == InputConstants.PRESS && event.key == trigger && !busy) {
            pending = true
        }
    }

    @Suppress("unused")
    private val swordHandler = handler<AttackEntityEvent> { event ->
        if (onSwordHit && !busy && player.mainHandItem.`is`(ItemTags.SWORDS) && event.entity.shouldBeAttacked()) {
            pending = true
        }
    }

    @Suppress("unused")
    private val deathHandler = handler<GameTickEvent> {
        if (lootPause == 0) {
            return@handler
        }
        val enemies = GlobalSettingsTarget.combat + Targets.DEAD
        val dying = world.entitiesForRendering().filter {
            it is LivingEntity && it !== player && it.isDeadOrDying && it.shouldBeAttacked(enemies)
        }
        mourned.retainAll(dying.mapTo(HashSet()) { it.id })
        dying.filter { mourned.add(it.id) }.forEach { mourn(it.position()) }
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        forgetLoot()
        mourned.clear()
    }

    override fun onDisabled() {
        forgetLoot()
        mourned.clear()
        pending = false
        busy = false
        SilentHotbar.resetSlot(this)
    }

    private fun mourn(pos: Vec3) {
        lootSite = pos
        lootTimer?.cancel()
        lootTimer = eventListenerScope.launch {
            waitTicks(lootPause)
            lootSite = null
        }
    }

    private fun forgetLoot() {
        lootTimer?.cancel()
        lootTimer = null
        lootSite = null
    }

    private fun lootLies(): Boolean {
        val site = lootSite ?: return false
        val lies = world.getEntitiesOfClass(ItemEntity::class.java, AABB.ofSize(site, 1.0, 1.0, 1.0)
            .inflate(LOOT_RADIUS)) { isLoot(it.item) }.isNotEmpty()
        if (!lies) {
            forgetLoot()
        }
        return lies
    }

    private fun isLoot(stack: ItemStack) =
        stack.`is`(Items.TOTEM_OF_UNDYING) || stack.`is`(Items.GOLDEN_APPLE) ||
            stack.`is`(Items.ENCHANTED_GOLDEN_APPLE) || stack.`is`(Items.END_CRYSTAL) ||
            stack.get(DataComponents.EQUIPPABLE)?.slot()?.type == EquipmentSlot.Type.HUMANOID_ARMOR

    private suspend fun crystal() {
        val crystals = Slots.OffhandWithHotbar.findSlot(Items.END_CRYSTAL) ?: return
        val looked = traceFromPlayer(player.rotation, range.toDouble())
        if (looked.type != HitResult.Type.BLOCK) {
            return
        }
        val base = if (isBase(looked.blockPos)) looked.blockPos else placeObsidian(looked) ?: return
        if (!world.isEmptyBlock(base.above()) || !placeCrystal(base, crystals)) {
            return
        }

        val spot = AABB(base.above())
        var crystal: EndCrystal? = null
        tickConditional(SPAWN_TIMEOUT) {
            crystal = world.getEntitiesOfClass(EndCrystal::class.java, spot).firstOrNull()
            crystal != null
        }
        crystal?.let { blowUp(it) }
    }

    private fun isBase(pos: BlockPos) =
        world.getBlockState(pos).let { it.`is`(Blocks.OBSIDIAN) || it.`is`(Blocks.BEDROCK) }

    private suspend fun placeObsidian(looked: BlockHitResult): BlockPos? {
        val obsidian = Slots.OffhandWithHotbar.findSlot(Items.OBSIDIAN) ?: return null
        val pos = looked.blockPos.relative(looked.direction)
        val state = Blocks.OBSIDIAN.defaultBlockState()
        if (!world.getBlockState(pos).canBeReplaced() || !world.isUnobstructed(state, pos, CollisionContext.empty())) {
            return null
        }
        val rotation = Rotation.lookingAt(looked.location, player.eyePosition)
        val placed = aimThen({ rotation }, { clickable(looked.blockPos)?.direction == looked.direction }) {
            val hit = clickable(looked.blockPos) ?: return@aimThen
            select(obsidian)
            doPlacement(hit, RotationManager.serverRotation, obsidian.useHand, swingMode = swingMode)
        }
        if (!placed) {
            return null
        }
        tickConditional(SPAWN_TIMEOUT) { isBase(pos) }
        return pos.takeIf { isBase(it) }
    }

    private suspend fun placeCrystal(base: BlockPos, crystals: HotbarItemSlot) = aimThen(
        rotation = {
            raytraceBlockRotation(player.eyePosition, base, world.getBlockState(base), range.toDouble(), 0.0)
                ?.rotation
        },
        aimed = { clickable(base) != null },
    ) {
        val hit = clickable(base) ?: return@aimThen
        select(crystals)
        interactBlock(hit, crystals.useHand, swingMode)
    }

    private suspend fun blowUp(crystal: EndCrystal) {
        val hits = when (breaks) {
            Breaks.ONCE -> 1
            Breaks.TWICE -> 2
            Breaks.UNTIL_GONE -> GONE_TIMEOUT
        }
        repeat(hits) {
            if (crystal.isRemoved) {
                return
            }
            awaitHurtTime()
            val hit = aimThen(
                rotation = { raytraceBox(player.eyePosition, crystal.boundingBox, range.toDouble(), 0.0)?.rotation },
                aimed = { aimedAt(crystal) },
            ) {
                attackEntity(crystal, swingMode)
            }
            if (!hit) {
                return
            }
            waitTicks(1)
        }
    }

    // A hit lands only once the target's invulnerability from the last one is over
    private suspend fun awaitHurtTime() {
        if (!hurtTimeSync) {
            return
        }
        val enemy = world.findEnemy(0f..ENEMY_RANGE) as? LivingEntity ?: return
        tickConditional(HURT_TIMEOUT) { enemy.isRemoved || enemy.hurtTime == 0 }
    }

    private fun select(slot: HotbarItemSlot) {
        if (!slot.isOffHand) {
            SilentHotbar.selectSlotSilently(this, slot, slotResetDelay + 1)
        }
    }

    private fun clickable(pos: BlockPos) =
        traceFromPlayer(RotationManager.serverRotation, range.toDouble()).takeIf {
            it.type == HitResult.Type.BLOCK && it.blockPos == pos
        }

    private fun aimedAt(crystal: EndCrystal) = !crystal.isRemoved && isLookingAtEntity(
        toEntity = crystal,
        rotation = RotationManager.serverRotation,
        range = range.toDouble(),
        throughWallsRange = 0.0,
    ) != null

    /** Aims with [rotation] and runs [action] once the server has a rotation that passes [aimed]. */
    private suspend fun aimThen(rotation: () -> Rotation?, aimed: () -> Boolean, action: () -> Unit): Boolean {
        var done = false
        tickConditional(AIM_TIMEOUT) {
            val target = rotation() ?: return@tickConditional true
            rotationMode.activeMode.rotate(target, isFinished = { aimed() }) {
                if (!done && aimed()) {
                    action()
                    done = true
                }
            }
            done
        }
        return done
    }

    private enum class Breaks(override val tag: String) : Tagged {
        ONCE("Once"),
        TWICE("Twice"),
        UNTIL_GONE("UntilGone"),
    }

}
