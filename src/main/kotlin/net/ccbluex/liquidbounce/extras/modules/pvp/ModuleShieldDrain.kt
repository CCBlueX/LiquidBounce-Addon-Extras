package net.ccbluex.liquidbounce.extras.modules.pvp

import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.aiming.NormalRotationMode
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.utils.raytraceBox
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.combat.attackEntity
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.raytracing.isLookingAtEntity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.EntityHitResult

/**
 * Hits an enemy who holds up a shield several times a tick; every blocked hit costs the shield durability.
 * A mace hits the shield harder than most weapons.
 */
object ModuleShieldDrain : ClientModule("ShieldDrain", ExtrasCategories.PVP) {

    private const val MAX_HITS = 5

    private val range by float("Range", 3f, 1f..3f, "blocks")
    private val hits by int("Hits", 1, 1..MAX_HITS, "per tick")
    private val mace by boolean("Mace", true)
    private val minFall by float("MinFall", 0f, 0f..30f, "blocks")
    private val pauseOnGround by boolean("PauseOnGround", false)
    private val crosshairOnly by boolean("CrosshairOnly", false)
    private val aimMode = modes(this, "RotationMode") { arrayOf(NormalRotationMode(it, this)) }

    /** The shield bearer being drained. */
    var target: LivingEntity? = null
        private set

    /** Whether a shield is being drained right now; MaceAssist leaves its stun to it meanwhile. */
    val draining get() = running && target != null

    @Suppress("unused")
    private val drainHandler = tickHandler {
        target = if (mayDrain()) findEnemy() else null
        val enemy = target ?: return@tickHandler
        val (rotation, _) = raytraceBox(player.eyePosition, enemy.boundingBox, range.toDouble(), 0.0)
            ?: return@tickHandler
        aimMode.activeMode.rotate(rotation, isFinished = { aimedAt(enemy) }) {
            if (!aimedAt(enemy) || !enemy.isBlocking) {
                return@rotate
            }
            if (mace) {
                bestMace(0.0, Float.MAX_VALUE)?.let { SilentHotbar.selectSlotSilently(this@ModuleShieldDrain, it, 1) }
            }
            repeat(hits) { attackEntity(enemy, SwingMode.DO_NOT_HIDE) }
        }
    }

    override fun onDisabled() {
        SilentHotbar.resetSlot(this)
        target = null
    }

    // A hand busy eating or in a menu does not swing
    private fun mayDrain() = mc.gui.screen() == null && !player.isUsingItem && player.fallDistance >= minFall &&
        !(pauseOnGround && player.onGround())

    private fun findEnemy(): LivingEntity? {
        if (crosshairOnly) {
            return ((mc.hitResult as? EntityHitResult)?.entity as? LivingEntity)?.takeIf {
                isShieldBearer(it) && it.distanceTo(player) <= range
            }
        }
        val area = player.boundingBox.inflate(range.toDouble())
        return world.getEntitiesOfClass(LivingEntity::class.java, area, ::isShieldBearer)
            .minByOrNull { it.distanceToSqr(player) }
    }

    private fun isShieldBearer(entity: LivingEntity) =
        entity !== player && entity.isAlive && entity.isBlocking && entity.shouldBeAttacked()

    private fun aimedAt(enemy: LivingEntity) = isLookingAtEntity(
        toEntity = enemy,
        rotation = RotationManager.serverRotation,
        range = range.toDouble(),
        throughWallsRange = 0.0,
    ) != null

}
