package net.ccbluex.liquidbounce.extras.gametest.harness

import net.ccbluex.gametest.harness.TestWorld
import net.ccbluex.gametest.harness.action.awaitServer
import net.minecraft.core.BlockPos

/**
 * Real terrain at noon, for the screenshots in `docs/`: a meadow on a hill, a birch grove to the east. The server
 * levels a grass pad around `origin` (8, 127, 8) for the fixtures, [PAD] blocks in every direction, and leaves the
 * landscape beyond it as it is.
 */
val TestWorld.Companion.SURFACE: TestWorld get() = surface

private const val SEED = 20261111L
private const val PAD = 15
private const val PAD_DEPTH = 6
private const val PAD_CLEARANCE = 24
private val ORIGIN = BlockPos(8, 127, 8)
private val PAD_FROM = ORIGIN.offset(-PAD, 0, -PAD)
private val PAD_TO = ORIGIN.offset(PAD, 0, PAD)

private val surface = TestWorld.generated(SEED, ORIGIN) {
    run("forceload add ${PAD_FROM.x} ${PAD_FROM.z} ${PAD_TO.x} ${PAD_TO.z}")
    val corners = listOf(
        PAD_FROM, PAD_TO, BlockPos(PAD_FROM.x, ORIGIN.y, PAD_TO.z), BlockPos(PAD_TO.x, ORIGIN.y, PAD_FROM.z),
    )
    for (corner in corners) {
        awaitServer("loaded ${corner.x} ${corner.y} ${corner.z}")
    }
    run("fill ${PAD_FROM.below(PAD_DEPTH).command} ${PAD_TO.below(2).command} minecraft:dirt")
    run("fill ${PAD_FROM.below().command} ${PAD_TO.below().command} minecraft:grass_block")
    run("fill ${PAD_FROM.command} ${PAD_TO.above(PAD_CLEARANCE).command} minecraft:air")
    run("setworldspawn ${ORIGIN.command}")
    run("time set noon")
    run("weather clear")
    awaitServer("block ${PAD_TO.x} ${PAD_TO.y} ${PAD_TO.z} minecraft:air")
}

private val BlockPos.command get() = "$x $y $z"
