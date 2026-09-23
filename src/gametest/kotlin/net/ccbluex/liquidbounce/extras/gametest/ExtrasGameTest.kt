package net.ccbluex.liquidbounce.extras.gametest

import net.ccbluex.liquidbounce.extras.modules.ModuleBlockFinder
import net.ccbluex.liquidbounce.features.command.CommandManager
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleClickGui
import net.ccbluex.liquidbounce.integration.screen.ScreenManager
import net.ccbluex.liquidbounce.integration.interop.persistant.PersistentLocalStorage
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
import net.minecraft.client.multiplayer.chat.GuiMessage
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Mth
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.animal.sheep.Sheep
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.StandingSignBlock
import net.minecraft.world.level.block.entity.SignBlockEntity
import org.apache.logging.log4j.LogManager

/**
 * Starts the client with the add-on, joins a flat world and uses every feature once, taking the
 * screenshots the README shows. Anything logged as an error while doing so fails the run.
 */
class ExtrasGameTest : FabricClientGameTest {

    private val logger = LogManager.getLogger("Extras/GameTest")

    override fun runTest(context: ClientGameTestContext) {
        ErrorLog.install()
        // The ClickGUI keeps its panel layout in the client's local storage: park the built-in panels off
        // screen and open the add-on's, so the screenshot shows only that one.
        for (name in listOf("Combat", "Player", "Movement", "Render", "World", "Misc", "Exploit", "Fun")) {
            PersistentLocalStorage.map["clickgui.panel.$name"] = "{\"top\":20,\"left\":-10000,\"expanded\":false,\"scrollTop\":0,\"zIndex\":0}"
        }
        PersistentLocalStorage.map["clickgui.panel.Extras"] = "{\"top\":150,\"left\":20,\"expanded\":true,\"scrollTop\":0,\"zIndex\":1}"
        // Joining a world while the client's main browser still loads its page aborts that load, which
        // the client treats as fatal; wait for the page.
        context.waitFor({ ScreenManager.mainBrowser != null }, 20 * 120)
        context.waitTicks(40)
        // Room for the ClickGUI without shrinking it.
        context.input.resizeWindow(1280, 720)
        context.client { it.options.guiScale().set(2) }

        context.worldBuilder().create().use { world ->
            world.clientLevel.waitForChunksRender()
            context.waitFor { it.player?.onGround() == true }
            // Picking up wool later would otherwise pop a recipe toast into the screenshots.
            world.server.run { it.commands.performPrefixedCommand(it.createCommandSourceStack(), "recipe give @a *") }
            // The HUD is a page in the client's browser and takes a moment to come up.
            context.waitTicks(200)

            // Speedometer and AutoJump want a walking player.
            context.enable("Speedometer")
            context.input.holdKey { it.keyUp }
            context.waitTicks(20)
            context.takeScreenshot("Speedometer")
            context.disable("Speedometer")

            context.enable("AutoJump")
            context.waitFor({ !it.player!!.onGround() }, 40)
            context.takeScreenshot("AutoJump")
            context.input.releaseKey { it.keyUp }
            context.disable("AutoJump")

            // Nothing to see for these two, they just have to run.
            for (name in listOf("PacketCanceller", "MessageAura")) {
                context.enable(name)
                context.waitTicks(20)
                context.disable(name)
            }

            context.enable("LightOverlay")
            context.waitTicks(20)
            context.takeScreenshot("LightOverlay")
            context.disable("LightOverlay")

            // BlockFinder counts one ore placed in front of the player.
            val direction = context.client { it.player!!.direction }
            val ore = context.client { it.player!!.blockPosition().relative(direction, 3).above() }
            world.server.run { it.overworld().setBlock(ore, Blocks.DIAMOND_ORE.defaultBlockState(), 3) }
            context.client { ModuleBlockFinder.enabled = true }
            context.waitFor({ ModuleBlockFinder.tag == "1" }, 60)
            context.waitTicks(5)
            context.takeScreenshot("BlockFinder")
            context.client { ModuleBlockFinder.enabled = false }
            context.waitTicks(100)

            // The command answers in chat.
            context.client { CommandManager.execute("where") }
            context.waitTicks(2)
            check(context.chatLines().any { "You are at" in it }) { ".where said nothing" }
            context.takeScreenshot("Where")

            // StashFinder: a row of chests in a chunk far away, then a teleport there so it loads.
            val stash = ore.offset(500, -1, 0)
            world.server.run { server ->
                val level = server.overworld()
                for (i in 0 until 5) {
                    level.setBlock(stash.offset(i, 0, 0), Blocks.CHEST.defaultBlockState(), 3)
                }
            }
            context.enable("StashFinder")
            world.server.run { server ->
                val player = server.playerList.players.first()
                player.teleportTo(stash.x - 3.0 + 0.5, stash.y.toDouble(), stash.z + 0.5)
            }
            context.waitFor({ it.chatLines().any { "containers at" in it } }, 200)
            world.clientLevel.waitForChunksRender()
            context.waitTicks(10)
            context.takeScreenshot("StashFinder")
            context.disable("StashFinder")

            // AutoShearer: a sheep in front of the player and shears in the hotbar.
            val front = context.client { it.player!!.blockPosition().relative(direction, 2) }
            val sheepId = world.server.compute { server ->
                val level = server.overworld()
                server.playerList.players.first().inventory.setItem(0, ItemStack(Items.SHEARS))
                EntityTypes.SHEEP.spawn(level, front, EntitySpawnReason.COMMAND)!!.id
            }
            context.waitFor({ (it.level?.getEntity(sheepId) as? Sheep)?.readyForShearing() == true }, 100)
            context.enable("AutoShearer")
            context.waitFor({ (it.level?.getEntity(sheepId) as? Sheep)?.isSheared == true }, 200)
            context.waitTicks(10)
            context.takeScreenshot("AutoShearer")
            context.disable("AutoShearer")

            // AutoSign: the client "writes" the first sign, the server opens the editor for the second and
            // the module fills it in without the editor ever showing.
            val first = front.relative(direction.counterClockWise)
            val second = front.relative(direction.clockWise)
            val facingPlayer = Blocks.OAK_SIGN.defaultBlockState()
                .setValue(StandingSignBlock.ROTATION, Mth.floor((direction.toYRot() + 180f) * 16f / 360f + 0.5f) and 15)
            world.server.run { server ->
                val level = server.overworld()
                level.setBlock(first, facingPlayer, 3)
                level.setBlock(second, facingPlayer, 3)
            }
            context.enable("AutoSign")
            context.client { it.connection!!.send(ServerboundSignUpdatePacket(first, true, "Extras", "wrote this", "", "")) }
            context.waitTicks(2)
            world.server.run { server ->
                val sign = server.overworld().getBlockEntity(second) as SignBlockEntity
                val serverPlayer = server.playerList.players.first()
                sign.setAllowedPlayerEditor(serverPlayer.uuid)
                serverPlayer.openTextEdit(sign, true)
            }
            context.waitFor({ it.signText(second) == "Extras" }, 100)
            check(context.client { it.gui.screen() } !is AbstractSignEditScreen) { "AutoSign left the sign editor open" }
            context.waitTicks(10)
            context.takeScreenshot("AutoSign")
            context.disable("AutoSign")

            // The ClickGUI lists the category.
            context.client { ModuleClickGui.enabled = true }
            context.waitTicks(60)
            context.takeScreenshot("ClickGui")
            context.client { ModuleClickGui.enabled = false }
            context.waitTicks(10)

            // QuickRespawn brings the player back after dying.
            context.enable("QuickRespawn")
            world.server.run { it.playerList.players.first().kill(it.overworld()) }
            context.waitFor({ it.player?.isDeadOrDying == true }, 100)
            context.waitFor({ it.player?.isAlive == true && it.gui.screen() == null }, 200)
            check(context.chatLines().any { "Respawned" in it }) { "QuickRespawn did not announce" }
            context.waitTicks(10)
            context.takeScreenshot("QuickRespawn")
            context.disable("QuickRespawn")
        }

        val errors = ErrorLog.lines
        check(errors.isEmpty()) { "${errors.size} error(s) logged, first: ${errors.first()}" }
        logger.info("Every module and command ran without errors")
    }

}

private fun ClientGameTestContext.enable(name: String) {
    val module = requireNotNull(ModuleManager[name]) { "module $name is not registered" }
    client { module.enabled = true }
}

// Toggling pops a notification; waiting lets it fade before the next screenshot.
private fun ClientGameTestContext.disable(name: String) {
    client { ModuleManager[name]!!.enabled = false }
    waitTicks(100)
}

// The context's lambdas are "failable" with a throwable type Kotlin cannot infer; pin it once.
private fun <T> ClientGameTestContext.client(block: (Minecraft) -> T): T =
    computeOnClient<T, RuntimeException> { block(it) }

private fun TestServerContext.run(block: (MinecraftServer) -> Unit) = runOnServer<RuntimeException> { block(it) }

private fun <T> TestServerContext.compute(block: (MinecraftServer) -> T): T =
    computeOnServer<T, RuntimeException> { block(it) }

private fun Minecraft.signText(pos: BlockPos): String? =
    (level?.getBlockEntity(pos) as? SignBlockEntity)?.frontText?.getMessage(0, false)?.string

// ChatComponent offers no read access to its history.
private val allMessages = ChatComponent::class.java.getDeclaredField("allMessages").apply { isAccessible = true }

private fun ClientGameTestContext.chatLines(): List<String> = client { it.chatLines() }

private fun Minecraft.chatLines(): List<String> {
    @Suppress("UNCHECKED_CAST")
    return (allMessages.get(gui.hud.chat) as List<GuiMessage>).map {
        ChatFormatting.stripFormatting(it.content().string).orEmpty()
    }
}
