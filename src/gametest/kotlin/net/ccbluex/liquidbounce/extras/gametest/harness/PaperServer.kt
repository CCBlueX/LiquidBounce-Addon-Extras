package net.ccbluex.liquidbounce.extras.gametest.harness

import java.net.ServerSocket
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText

/**
 * A Paper server with Grim in its own process, driven through its console the way an admin would. Every
 * line it prints lands in [console], in order.
 */
class PaperServer private constructor(
    val directory: Path,
    val port: Int,
    private val process: Process,
) : AutoCloseable {

    val console: List<ConsoleLine>
        field = CopyOnWriteArrayList<ConsoleLine>()

    private val stdin = process.outputWriter()

    init {
        Thread.ofVirtual().name("Paper console").start {
            process.inputReader().useLines { lines -> lines.forEach { console += ConsoleLine.parse(it) } }
        }
    }

    val alive get() = process.isAlive

    val log: Path get() = directory.resolve("logs/latest.log")

    fun run(command: String) = synchronized(stdin) {
        stdin.write(command)
        stdin.newLine()
        stdin.flush()
    }

    override fun close() {
        if (process.isAlive) {
            run("stop")
        }
        if (!process.waitFor(1, TimeUnit.MINUTES)) {
            process.destroyForcibly()
        }
    }

    companion object {

        @OptIn(ExperimentalPathApi::class)
        fun start(name: String): PaperServer {
            val distribution = GameTestEnvironment.distribution
            val root = GameTestEnvironment.paperDirectory
            val directory = root.resolve("instances").resolve(name).apply { deleteRecursively() }.createDirectories()
            val port = ServerSocket(0).use { it.localPort }
            directory.resolve("server.properties").writeText(properties(port))

            val java = ProcessHandle.current().info().command().orElseThrow()
            val process = ProcessBuilder(
                java,
                "-Xmx2G",
                // Paperclip patches the server once into this shared directory instead of every instance
                "-DbundlerRepoDir=${root.resolve("bundle").absolutePathString()}",
                "-Dcom.mojang.eula.agree=true",
                "-Dterminal.jline=false",
                "-Dterminal.ansi=false",
                "-Dpaper.disableStartupVersionCheck=true",
                "-jar", distribution.paper.absolutePathString(),
                "--nogui",
                "--add-plugin", distribution.grim.absolutePathString(),
                "--add-plugin", GameTestEnvironment.probe.absolutePathString(),
            ).directory(directory.toFile()).redirectErrorStream(true).start()
            return PaperServer(directory, port, process)
        }

        private fun properties(port: Int) = """
            server-ip=127.0.0.1
            server-port=$port
            online-mode=false
            enforce-secure-profile=false
            white-list=false
            spawn-protection=0
            level-type=minecraft\:flat
            generator-settings={"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}]}
            level-seed=extras
            generate-structures=false
            view-distance=4
            simulation-distance=4
            difficulty=peaceful
            gamemode=survival
            pause-when-empty-seconds=-1
            player-idle-timeout=0
            sync-chunk-writes=false
        """.trimIndent()

    }

}

@JvmRecord
data class ConsoleLine(val level: String, val message: String) {

    /** What the `paper-probe` plugin reported on this line, if it is one of its lines. */
    val report: ProbeReport?
        get() = message.removePrefix(PROBE_PREFIX).takeIf { it != message }
            ?.substringBefore(' ')
            ?.let { tag -> ProbeReport.entries.firstOrNull { it.name == tag } }

    override fun toString() = if (level.isEmpty()) message else "[$level] $message"

    companion object {

        private const val PROBE_PREFIX = "[ExtrasProbe] "
        private val FORMAT = Regex("""^\[\d{2}:\d{2}:\d{2} (\w+)]: (.*)$""")

        fun parse(line: String) =
            FORMAT.matchEntire(line)?.let { ConsoleLine(it.groupValues[1], it.groupValues[2]) } ?: ConsoleLine("", line)

    }

}

enum class ProbeReport { READY, FLAG, SETBACK, FAILMOVE, KICK, QUIT }
