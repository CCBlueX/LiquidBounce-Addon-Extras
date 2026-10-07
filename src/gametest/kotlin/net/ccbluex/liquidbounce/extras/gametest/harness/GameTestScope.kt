package net.ccbluex.liquidbounce.extras.gametest.harness

import net.fabricmc.fabric.api.client.gametest.v1.TestInput
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import org.apache.logging.log4j.LogManager

/**
 * What a game test works with: the client, driven through real input, and the server, driven through its
 * console like an admin would. The server's state is the truth; the client only shows its prediction.
 * Actions on either side live in `ClientActions.kt` and `ServerActions.kt`.
 */
class GameTestScope internal constructor(
    val context: ClientGameTestContext,
    val server: PaperServer,
) : AutoCloseable {

    /** Grass level of the superflat world. Every test starts here, looking south. */
    val origin = BlockPos(0, -60, 0)

    val input: TestInput get() = context.input

    val playerName: String by lazy { client { it.user.name } }

    internal val verdict = Verdict(server) { playerName }

    private val logger = LogManager.getLogger("Extras/GameTest")
    private var joined = false

    fun <T> client(action: (Minecraft) -> T): T = context.computeOnClient<T, RuntimeException>(action)

    fun ticks(count: Int) = context.waitTicks(count)

    fun awaitClient(what: String, timeout: Int = DEFAULT_TIMEOUT, condition: (Minecraft) -> Boolean) {
        try {
            context.waitFor(condition, timeout)
        } catch (failure: AssertionError) {
            throw AssertionError("Timed out after $timeout ticks waiting for $what", failure)
        }
    }

    fun run(command: String) = server.run(command)

    /** Ticks until a console line from [since] on [matches]; the client must never stand still meanwhile. */
    @IgnorableReturnValue
    fun awaitConsole(
        what: String,
        since: Int = 0,
        timeout: Int = DEFAULT_TIMEOUT,
        matches: (ConsoleLine) -> Boolean,
    ): ConsoleLine {
        repeat(timeout) {
            server.console.drop(since).firstOrNull(matches)?.let { return it }
            check(server.alive) { "Paper stopped while waiting for $what, see ${server.log}" }
            context.waitTick()
        }
        throw AssertionError("Timed out after $timeout ticks waiting for $what, see ${server.log}")
    }

    /**
     * Teleports and waits until the client stands there with the area rendered. Grim holds a player in
     * chunks the client has not received yet by setting them back; those setbacks belong to the trip.
     */
    fun travel(pos: BlockPos, yaw: Float = 0f, pitch: Float = 0f, dimension: ResourceKey<Level>? = null) {
        val since = server.console.size
        teleport(pos, yaw, pitch, dimension)
        // A player in a chunk the client does not have yet keeps its last on-ground state, and there is
        // nothing to render yet either, so the chunk itself has to be there first
        awaitClient("the player to arrive at $pos") { minecraft ->
            val player = minecraft.player!!
            val level = minecraft.level!!
            (dimension == null || level.dimension() == dimension) && level.hasChunkAt(pos) &&
                player.blockPosition() == pos && player.onGround() && minecraft.levelRenderer.hasRenderedAllSections()
        }
        // Grim lets go only once it has seen the client confirm the chunk, which can take a few round trips
        repeat(SETTLE_ATTEMPTS) {
            val checked = server.console.size
            ticks(20)
            if (server.console.drop(checked).none { it.report == ProbeReport.SETBACK }) {
                verdict.excuseSetbacks(since until checked)
                return
            }
        }
        throw AssertionError("Grim kept setting the player back at $pos, see ${server.log}")
    }

    /**
     * The negative control: [action] must make the server object at least once. Those objections do not
     * count against the test.
     */
    fun expectViolation(what: String, action: () -> Unit) {
        val since = server.console.size
        action()
        awaitConsole(what, since) { verdict.isViolation(it) }
        ticks(40)
        verdict.excuse(since until server.console.size)
    }

    internal fun awaitReady() {
        awaitConsole("Paper to start", timeout = BOOT_TIMEOUT) { it.message.startsWith("Done (") }
        val ready = awaitConsole("Grim and the probe to load") { it.report == ProbeReport.READY }
        logger.info("{} runs against Paper with {}", server.directory.fileName, ready.message)
        GAME_RULES.forEach { run("gamerule $it") }
        run("time set day")
    }

    internal fun join() {
        joined = true
        // The runner resets window and options before every test; the README's screenshots need room
        input.resizeWindow(1280, 720)
        client { it.options.guiScale().set(2) }
        client { minecraft ->
            val address = "127.0.0.1:${server.port}"
            ConnectScreen.startConnecting(
                TitleScreen(), minecraft, ServerAddress.parseString(address),
                ServerData("Extras game test", address, ServerData.Type.OTHER), false, null,
            )
        }
        awaitClient("the client to join", JOIN_TIMEOUT) { it.player != null && it.level != null }
        travel(origin)
    }

    fun reconnect() {
        close()
        join()
    }

    /** Leaves the server like a player would, so that the quit is expected. */
    override fun close() {
        if (!joined) {
            return
        }
        ticks(20)
        val since = server.console.size
        // Like the pause menu: Minecraft.disconnect alone drops the world but leaves the connection open
        client { minecraft ->
            minecraft.level?.disconnect(ClientLevel.DEFAULT_QUIT_MESSAGE)
            minecraft.disconnect(TitleScreen(), false)
        }
        awaitClient("the client to leave") { it.level == null && it.gui.screen() is TitleScreen }
        awaitConsole("the server to see the player leave", since) { it.report == ProbeReport.QUIT }
        verdict.departed(since until server.console.size)
        joined = false
    }

    internal companion object {
        const val DEFAULT_TIMEOUT = 200
        const val JOIN_TIMEOUT = 20 * 60
        const val SETTLE_ATTEMPTS = 10
        // The first boot also downloads the vanilla server and patches it
        const val BOOT_TIMEOUT = 20 * 300
        val GAME_RULES = listOf(
            "minecraft:advance_time false",
            "minecraft:advance_weather false",
            "minecraft:spawn_mobs false",
            "minecraft:random_tick_speed 0",
            "minecraft:show_advancement_messages false",
            "minecraft:respawn_radius 0",
        )
    }

}
