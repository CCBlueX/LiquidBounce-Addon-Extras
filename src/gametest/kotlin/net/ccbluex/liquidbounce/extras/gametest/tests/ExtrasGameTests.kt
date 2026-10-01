package net.ccbluex.liquidbounce.extras.gametest.tests

import com.mojang.authlib.GameProfile
import net.ccbluex.liquidbounce.extras.gametest.harness.GameTestScope
import net.ccbluex.liquidbounce.extras.gametest.harness.PaperGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.chat
import net.ccbluex.liquidbounce.extras.gametest.harness.chatContains
import net.ccbluex.liquidbounce.extras.gametest.harness.disable
import net.ccbluex.liquidbounce.extras.gametest.harness.enable
import net.ccbluex.liquidbounce.extras.gametest.harness.setting
import net.ccbluex.liquidbounce.extras.modules.extras.ModuleMessageAura
import net.ccbluex.liquidbounce.extras.modules.extras.ModulePacketCanceller
import net.ccbluex.liquidbounce.features.misc.FriendManager
import net.minecraft.client.player.RemotePlayer
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import java.util.UUID

class MessageAuraGameTest : PaperGameTest({
    enable(ModuleMessageAura.INSTANCE)
    ticks(5)
    client { FriendManager.add(FriendManager.Friend("ExtrasFriend", null)) }

    val since = server.console.size
    val players = listOf(showPlayer("ExtrasFriend", origin.west(6)), showPlayer("ExtrasVisitor", origin.east(6)))
    awaitConsole("the whisper", since) {
        "$playerName issued server command: /msg ExtrasVisitor Hello from LiquidBounce" in it.message
    }
    ticks(60)
    check(server.console.drop(since).none { "/msg ExtrasFriend" in it.message }) { "whispered to a friend" }

    players.forEach { hidePlayer(it) }
    client { FriendManager.remove("ExtrasFriend") }
})

class PacketCancellerGameTest : PaperGameTest({
    val incoming = ModulePacketCanceller.setting<MutableSet<Identifier>>("Incoming")
    client { incoming.set(sortedSetOf(Identifier.withDefaultNamespace("system_chat"))) }
    enable(ModulePacketCanceller)
    run("""tellraw $playerName "first canary"""")
    ticks(20)
    check(chat().none { "first canary" in it }) { "a dropped chat packet was shown" }

    disable(ModulePacketCanceller)
    run("""tellraw $playerName "second canary"""")
    awaitClient("chat to arrive again") { it.chatContains("second canary") }
    client { incoming.set(sortedSetOf()) }
})

/**
 * A player that exists on this client only. The server has no second player to offer, and these modules
 * only watch players, so nothing they send depends on the stranger being real. Kept out of reach, since
 * the local player would collide with it but the server would not.
 */
private fun GameTestScope.showPlayer(name: String, pos: BlockPos): Int = client { minecraft ->
    val level = minecraft.level!!
    val player = RemotePlayer(level, GameProfile(UUID.nameUUIDFromBytes(name.encodeToByteArray()), name))
    player.id = CLIENT_ONLY_ENTITY_IDS + name.hashCode().mod(1000)
    player.setPos(Vec3.atBottomCenterOf(pos))
    level.addEntity(player)
    player.id
}

private fun GameTestScope.hidePlayer(id: Int) = client { it.level!!.removeEntity(id, Entity.RemovalReason.DISCARDED) }

private const val CLIENT_ONLY_ENTITY_IDS = 1_000_000_000
