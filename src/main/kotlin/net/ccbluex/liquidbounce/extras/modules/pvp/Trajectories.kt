package net.ccbluex.liquidbounce.extras.modules.pvp

import net.ccbluex.liquidbounce.utils.render.trajectory.TrajectoryType

// Looked up by tag: after 0.42.0 LiquidBounce renamed the constants (EnderPearl to ENDER_PEARL)
internal val ARROW_TRAJECTORY = trajectoryType("Arrow")
internal val PEARL_TRAJECTORY = trajectoryType("EnderPearl")

private fun trajectoryType(tag: String) = TrajectoryType.entries.first { it.tag == tag }
