package net.ccbluex.liquidbounce.extras.gametest.tests

import net.ccbluex.liquidbounce.extras.gametest.harness.PaperGameTest
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket

/**
 * Plain vanilla movement has to pass, or no other verdict means anything. Then the negative control: a
 * client claiming to be five blocks up has to be caught.
 */
class HarnessGameTest : PaperGameTest({
    input.holdKey { it.keyUp }
    ticks(40)
    input.holdKey { it.keySprint }
    input.holdKey { it.keyJump }
    ticks(60)
    input.releaseKey { it.keyJump }
    input.releaseKey { it.keySprint }
    input.releaseKey { it.keyUp }
    ticks(20)

    expectViolation("Grim to catch a move five blocks up") {
        client { minecraft ->
            val player = minecraft.player!!
            minecraft.connection!!.send(ServerboundMovePlayerPacket.Pos(player.x, player.y + 5, player.z, true, false))
        }
    }
})
