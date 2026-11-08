package net.ccbluex.liquidbounce.extras.gametest.tests

import com.mojang.blaze3d.platform.InputConstants
import net.ccbluex.liquidbounce.extras.gametest.harness.GameTestScope
import net.ccbluex.liquidbounce.extras.gametest.harness.PaperGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.awaitServer
import net.ccbluex.liquidbounce.extras.gametest.harness.disable
import net.ccbluex.liquidbounce.extras.gametest.harness.enable
import net.ccbluex.liquidbounce.extras.gametest.harness.screenshot
import net.ccbluex.liquidbounce.extras.gametest.harness.setting
import net.ccbluex.liquidbounce.extras.gametest.harness.summon
import net.ccbluex.liquidbounce.extras.modules.pvp.ModuleSwingRedirect
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.entity.monster.zombie.Husk
import kotlin.math.abs

class SwingRedirectGameTest : PaperGameTest({
    // Within reach, but a quarter turn away from the crosshair
    standIn(origin.east(2))
    enable(ModuleSwingRedirect)
    ticks(5)
    check(client { ModuleSwingRedirect.aim } == null) { "aimed at a target far off the crosshair" }
    input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT)
    ticks(20)
    awaitServer("entity @e[tag=$STAND_IN,nbt={Health:20.0f}]")

    // Off the crosshair by less than MaxYaw: the swing at the air lands on it
    run("tp @e[tag=$STAND_IN] 1.5 -60 2.5")
    awaitClient("an aim at the stand-in") { ModuleSwingRedirect.aim?.target is Husk }
    screenshot("SwingRedirect")
    input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT)
    awaitServer("entity @e[tag=$STAND_IN,nbt={Health:19.0f}]")
    awaitClient("the rotation back") { abs(Mth.wrapDegrees(RotationManager.serverRotation.yaw)) < 5f }

    // ContinuousTracking: the server faces the stand-in, about 27 degrees to the left, before and between swings
    client { ModuleSwingRedirect.setting<Boolean>("ContinuousTracking").set(true) }
    ticks(3)
    repeat(20) {
        val yaw = client { Mth.wrapDegrees(RotationManager.serverRotation.yaw) }
        check(yaw in -32f..-20f) { "not tracking the stand-in: $yaw" }
        ticks(1)
    }
    awaitServer("entity @e[tag=$STAND_IN,nbt={Health:19.0f}]")
    input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT)
    awaitServer("entity @e[tag=$STAND_IN,nbt={Health:18.0f}]")

    disable(ModuleSwingRedirect)
    check(client { ModuleSwingRedirect.aim } == null) { "the aim survived disabling" }
})

private const val STAND_IN = "standin"

/**
 * A still, unarmored husk as the enemy, since the server cannot provide a second player. Hostile mobs need a
 * difficulty above peaceful; a husk does not burn in daylight. A fist takes exactly one point off it.
 */
private fun GameTestScope.standIn(pos: BlockPos) {
    run("difficulty easy")
    summon("minecraft:husk", pos, """{NoAI:1b,PersistenceRequired:1b,Silent:1b,OnGround:1b,Tags:["$STAND_IN"]}""")
    run("attribute @e[tag=$STAND_IN,limit=1] minecraft:armor base set 0")
    awaitClient("the stand-in") { it.standIn() != null }
    // A mob without AI never moves, so the client learns that it stands on the ground from the periodic sync
    awaitClient("the stand-in on the ground", timeout = 100) { it.standIn()!!.onGround() }
}

private fun Minecraft.standIn() = level!!.entitiesForRendering().firstOrNull { it is Husk } as Husk?
