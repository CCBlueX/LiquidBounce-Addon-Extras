package net.ccbluex.liquidbounce.extras.modules

import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.render.drawLines
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.engine.type.Vec3f
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.render.withPositionRelativeToCamera
import net.ccbluex.liquidbounce.utils.block.state
import net.minecraft.core.BlockPos
import net.minecraft.world.level.LightLayer

/**
 * Marks blocks where hostile mobs can spawn with a cross: red where they spawn right now, yellow where
 * only the sky light keeps them away. One line batch per colour, collected on the tick and drawn on
 * the render event.
 */
object ModuleLightOverlay : ClientModule("LightOverlay", ExtrasCategories.EXTRAS) {

    private val horizontalRange by int("HorizontalRange", 8, 1..32, "blocks")
    private val verticalRange by int("VerticalRange", 4, 1..16, "blocks")
    private val lightLevel by int("LightLevel", 0, 0..15)
    private val color by color("Color", Color4b(225, 25, 25))
    private val potentialColor by color("PotentialColor", Color4b(225, 225, 25))

    private var always: Array<Vec3f> = emptyArray()
    private var potential: Array<Vec3f> = emptyArray()

    override fun onDisabled() {
        always = emptyArray()
        potential = emptyArray()
    }

    @Suppress("unused")
    private val scanHandler = tickHandler {
        val origin = player.blockPosition()
        val h = horizontalRange
        val v = verticalRange
        val alwaysLines = ArrayList<Vec3f>()
        val potentialLines = ArrayList<Vec3f>()

        for (pos in BlockPos.betweenClosed(origin.offset(-h, -v, -h), origin.offset(h, v, h))) {
            when (spawnAt(pos)) {
                Spawn.NEVER -> continue
                Spawn.ALWAYS -> alwaysLines.addCross(pos)
                Spawn.POTENTIAL -> potentialLines.addCross(pos)
            }
        }

        always = alwaysLines.toTypedArray()
        potential = potentialLines.toTypedArray()
        waitTicks(5)
    }

    // Spawn rule after Meteor Client's BlockUtils.isValidMobSpawn (GPL-3.0):
    // https://github.com/MeteorDevelopment/meteor-client/blob/8819b2e1a5630839d41bf60ac8ce63efbe411deb/src/main/java/meteordevelopment/meteorclient/utils/world/BlockUtils.java#L313-L338
    private fun spawnAt(pos: BlockPos): Spawn {
        val state = pos.state ?: return Spawn.NEVER
        val below = pos.below().state ?: return Spawn.NEVER
        if (!state.isAir || !below.isSolidRender) {
            return Spawn.NEVER
        }

        return when {
            world.getBrightness(LightLayer.BLOCK, pos) > lightLevel -> Spawn.NEVER
            world.getBrightness(LightLayer.SKY, pos) > lightLevel -> Spawn.POTENTIAL
            else -> Spawn.ALWAYS
        }
    }

    private fun MutableList<Vec3f>.addCross(pos: BlockPos) {
        val x = pos.x.toFloat()
        val y = pos.y + 0.01f
        val z = pos.z.toFloat()
        add(Vec3f(x, y, z))
        add(Vec3f(x + 1, y, z + 1))
        add(Vec3f(x + 1, y, z))
        add(Vec3f(x, y, z + 1))
    }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        event.renderEnvironment {
            withPositionRelativeToCamera {
                if (always.isNotEmpty()) drawLines(color.argb, *always)
                if (potential.isNotEmpty()) drawLines(potentialColor.argb, *potential)
            }
        }
    }

    private enum class Spawn { NEVER, POTENTIAL, ALWAYS }

}
