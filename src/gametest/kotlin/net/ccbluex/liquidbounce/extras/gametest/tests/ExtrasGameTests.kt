package net.ccbluex.liquidbounce.extras.gametest.tests

import com.mojang.authlib.GameProfile
import com.mojang.blaze3d.platform.InputConstants
import net.ccbluex.gametest.harness.GameTestScope
import net.ccbluex.liquidbounce.extras.gametest.harness.PaperGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.SURFACE
import net.ccbluex.gametest.harness.TestWorld
import net.ccbluex.gametest.harness.action.aim
import net.ccbluex.gametest.harness.action.awaitAnswer
import net.ccbluex.gametest.harness.action.awaitServer
import net.ccbluex.gametest.harness.action.chat
import net.ccbluex.gametest.harness.action.chatContains
import net.ccbluex.liquidbounce.gametest.harness.disable
import net.ccbluex.liquidbounce.gametest.harness.enable
import net.ccbluex.gametest.harness.action.hangItemFrame
import net.ccbluex.liquidbounce.extras.gametest.harness.screenshot
import net.ccbluex.gametest.harness.action.setBlock
import net.ccbluex.liquidbounce.gametest.harness.setting
import net.ccbluex.gametest.harness.action.summon
import net.ccbluex.liquidbounce.extras.modules.extras.ModuleAntiTrap
import net.ccbluex.liquidbounce.extras.modules.extras.ModuleDisguise
import net.ccbluex.liquidbounce.extras.modules.extras.ModuleIntruderAlert
import net.ccbluex.liquidbounce.extras.modules.extras.ModuleMessageAura
import net.ccbluex.liquidbounce.extras.modules.extras.ModulePacketCanceller
import net.ccbluex.liquidbounce.extras.util.coordinates
import net.ccbluex.liquidbounce.features.misc.FriendManager
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.player.RemotePlayer
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.ObjectContents
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.vehicle.boat.AbstractBoat
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.SignBlock
import net.minecraft.world.phys.Vec3
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
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

class IntruderAlertGameTest : PaperGameTest(TestWorld.SURFACE, {
    client { FriendManager.add(FriendManager.Friend("ExtrasVisitor", null)) }
    // Looking north, at faces the sun lights; client-only players face south
    travel(origin, yaw = 180f)
    val pos = origin.offset(2, 0, -7)
    val visitor = showPlayer("ExtrasVisitor", pos)
    enable(ModuleIntruderAlert)
    // Long enough for the "Enabled" toast to go, so the screenshot catches the alert's notification
    ticks(100)
    check(chat().none { "ExtrasVisitor is nearby" in it }) { "reported a friend" }

    client { FriendManager.remove("ExtrasVisitor") }
    awaitClient("the intruder report") { it.chatContains("ExtrasVisitor is nearby at ${pos.coordinates}") }
    ticks(20)
    check(chat().count { "ExtrasVisitor is nearby" in it } == 1) { "reported the same intruder twice" }
    screenshot("IntruderAlert")
    hidePlayer(visitor)
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

class AntiTrapGameTest : PaperGameTest({
    enable(ModuleAntiTrap)
    ticks(5)
    val bait = origin.south(5)
    summon("minecraft:armor_stand", bait)
    summon("minecraft:minecart", bait.east(2))
    summon("minecraft:bamboo_chest_raft", bait.west(2))
    summon("minecraft:end_crystal", bait.east(4))
    hangItemFrame(bait.west(4).above(), Direction.SOUTH, "minecraft:diamond")
    summon("minecraft:armor_stand", origin.south(28))
    awaitClient("the armor stand beyond the range") { it.level!!.entitiesForRendering().any { e -> e is ArmorStand } }
    awaitServer("entity @e[type=minecraft:item_frame]")
    awaitServer("entity @e[type=minecraft:end_crystal]")
    ticks(20)
    check(client { entitiesAround(it) }.isEmpty()) { "bait showed up: ${client { entitiesAround(it) }}" }
    check(client { ModuleAntiTrap.keptOut.size } == 5) { "kept out ${client { ModuleAntiTrap.keptOut }}" }

    // A boat let in stays until loaded bait is removed as well
    client {
        ModuleAntiTrap.setting<Boolean>("Boats").set(false)
        ModuleAntiTrap.setting<Boolean>("RemoveLoaded").set(false)
    }
    summon("minecraft:oak_boat", bait.east(6))
    awaitClient("the boat once boats are let in") { it.level!!.entitiesForRendering().any { e -> e is AbstractBoat } }
    client { ModuleAntiTrap.setting<Boolean>("Boats").set(true) }
    ticks(20)
    check(client { entitiesAround(it) }.size == 1) { "a loaded boat was removed without RemoveLoaded" }
    client { ModuleAntiTrap.setting<Boolean>("RemoveLoaded").set(true) }
    awaitClient("the loaded boat to be removed") { entitiesAround(it).isEmpty() }

    // The server opening an editor on its own is refused, a sign of our own still gets one
    val trap = origin.south(2)
    setBlock(trap, "minecraft:oak_sign[rotation=8]")
    awaitClient("the trap sign") { it.level!!.getBlockState(trap).block is SignBlock }
    run("opensign $playerName ${trap.x} ${trap.y} ${trap.z}")
    awaitClient("the refusal") { it.chatContains("Refused the sign editor the server opened at 0 -60 2") }
    repeat(20) {
        check(client { it.gui.screen() !is AbstractSignEditScreen }) { "the server's sign editor opened" }
        ticks(1)
    }

    run("item replace entity $playerName hotbar.0 with minecraft:oak_sign")
    awaitClient("the sign in hand") { it.player!!.mainHandItem.`is`(Items.OAK_SIGN) }
    val own = origin.south(2).east(2)
    aim(own.below())
    input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT)
    context.waitForScreen(AbstractSignEditScreen::class.java)
    ticks(5)
    check(client { it.gui.screen() is AbstractSignEditScreen }) { "the editor of our own sign was closed" }
    input.typeChars("Mine")
    context.clickScreenButton("gui.done")
    awaitAnswer("data get block ${own.x} ${own.y} ${own.z} front_text.messages[0]", "Mine")

    disable(ModuleAntiTrap)
    check(client { ModuleAntiTrap.keptOut.isEmpty() }) { "disabling kept the list of kept out entities" }
    // Without the module the server's editor opens, so the refusal above was the module's
    run("opensign $playerName ${trap.x} ${trap.y} ${trap.z}")
    context.waitForScreen(AbstractSignEditScreen::class.java)

    // An editor nobody asked for that is already open closes once the module is on
    enable(ModuleAntiTrap)
    awaitClient("the open editor to close") { it.gui.screen() !is AbstractSignEditScreen }
    awaitClient("the report") { it.chatContains("Closed the sign editor at 0 -60 2 you did not open") }
    disable(ModuleAntiTrap)
})

class DisguiseGameTest : PaperGameTest(TestWorld.SURFACE, {
    client { FriendManager.add(FriendManager.Friend("ExtrasFriend", null)) }
    travel(origin, yaw = 180f)
    val friend = showPlayer("ExtrasFriend", origin.north(4).east(2))
    val stranger = showPlayer("ExtrasStranger", origin.north(6).west(1))
    enable(ModuleDisguise)
    awaitClient("the stranger in disguise, not the nearer friend") { ModuleDisguise.target?.name == "ExtrasStranger" }
    client {
        val line = shown(Component.literal("<ExtrasStranger> hi ExtrasFriend"))
        check(line == "<Notch [Admin]> hi ExtrasFriend") { "chat reads '$line'" }
        check(ModuleDisguise.disguise("ExtrasStrangers") == "ExtrasStrangers") { "a longer name was disguised" }
        check(nameTag(it, stranger)?.let(::shown) == "Notch [Admin]") { "a tier showed while it is off" }
    }

    // The tier goes on the nametag of the stranger, nowhere else
    client { ModuleDisguise.setting<ModuleDisguise.Tier>("Tier").set(ModuleDisguise.Tier.HT1) }
    client {
        val label = checkNotNull(nameTag(it, stranger)) { "the stranger has no nametag" }
        check(shown(label).endsWith(" HT1 | Notch [Admin]")) { "the nametag reads '${shown(label)}'" }
        val parts = label.siblings
        check(parts.any { part -> part.contents is ObjectContents }) { "the nametag has no gamemode icon" }
        val tier = parts.first { part -> part.string == "HT1" }
        check(tier.style.color?.value == ModuleDisguise.Tier.HT1.color) { "HT1 is in ${tier.style.color}" }
        check(nameTag(it, friend)?.let(::shown) == "ExtrasFriend") { "the friend got a tier" }
        val line = shown(Component.literal("<ExtrasStranger> hi"))
        check(line == "<Notch [Admin]> hi") { "the tier reached chat: '$line'" }
    }
    try {
        awaitClient("the skin from Mojang", timeout = 1200) { skinChanged(it, stranger) }
    } catch (timeout: AssertionError) {
        // Mojang turns a busy CI address away for minutes; that says nothing about the module
        if (!mojangRefuses()) {
            throw timeout
        }
    }
    check(client { !skinChanged(it, friend) }) { "the friend got the skin" }
    run("""tellraw $playerName "ExtrasStranger wants to trade"""")
    awaitClient("the server's line") { it.chatContains("ExtrasStranger wants to trade") }
    ticks(5)
    screenshot("Disguise")
    client { ModuleDisguise.setting<ModuleDisguise.Tier>("Tier").set(ModuleDisguise.Tier.OFF) }

    disable(ModuleDisguise)
    check(client { ModuleDisguise.target == null && !skinChanged(it, stranger) }) { "disabling kept the disguise" }
    check(client { shown(Component.literal("ExtrasStranger")) } == "ExtrasStranger") { "the name stayed disguised" }
    hidePlayer(friend)
    hidePlayer(stranger)
    client { FriendManager.remove("ExtrasFriend") }
})

/** Entities other than us within AntiTrap's default range. */
private fun entitiesAround(minecraft: Minecraft): List<String> {
    val player = minecraft.player!!
    return minecraft.level!!.entitiesForRendering()
        .filter { it !is Player && it.distanceTo(player) <= 16f }
        .map { it.type.toShortString() }
}

private fun shown(text: Component): String {
    val shown = StringBuilder()
    ModuleDisguise.disguise(text.visualOrderText).accept { _, _, codePoint ->
        shown.appendCodePoint(codePoint)
        true
    }
    return shown.toString()
}

/** The label the game puts above the head of entity [id] this frame. */
private fun nameTag(minecraft: Minecraft, id: Int): Component? {
    val entity = minecraft.level!!.getEntity(id)!!
    return minecraft.entityRenderDispatcher.getRenderer(entity).createRenderState(entity, 1f).nameTag
}

private fun mojangRefuses(): Boolean {
    val request = HttpRequest.newBuilder(URI("https://api.mojang.com/users/profiles/minecraft/Notch")).build()
    val status = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
    return status == HTTP_TOO_MANY_REQUESTS
}

private const val HTTP_TOO_MANY_REQUESTS = 429

private fun skinChanged(minecraft: Minecraft, id: Int): Boolean {
    val player = minecraft.level!!.getEntity(id) as AbstractClientPlayer
    return player.skin != DefaultPlayerSkin.get(player.uuid)
}

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
