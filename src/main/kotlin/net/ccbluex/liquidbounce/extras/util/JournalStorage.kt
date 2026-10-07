package net.ccbluex.liquidbounce.extras.util

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import net.minecraft.core.BlockPos
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

data class SavedPosition(val x: Int, val y: Int, val z: Int) {
    constructor(pos: BlockPos) : this(pos.x, pos.y, pos.z)
    fun blockPos() = BlockPos(x, y, z)
}

data class Finding(
    val id: String,
    val position: SavedPosition,
    val evidence: Map<String, Int>,
    val firstSeen: Long,
    val lastSeen: Long,
)

data class Journal(
    val version: Int = 1,
    val findings: Map<String, Finding> = emptyMap(),
    val waypoints: Map<String, SavedPosition> = emptyMap(),
)

/** All reads and writes share a queue, including reads immediately following a save. */
class JournalStorage(private val directory: Path) : AutoCloseable {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val executor = Executors.newSingleThreadExecutor(
        Thread.ofPlatform().name("Extras journal").factory(),
    )
    private val unreadable = hashSetOf<String>()

    fun file(key: String): Path {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        return directory.resolve(digest.joinToString("") { "%02x".format(it) } + ".json")
    }

    fun load(key: String): CompletableFuture<Journal> = CompletableFuture.supplyAsync({
        val path = file(key)
        try {
            val journal = if (Files.exists(path)) read(path) else Journal()
            unreadable.remove(key)
            journal
        } catch (failure: Exception) {
            unreadable.add(key)
            throw failure
        }
    }, executor)

    private fun read(path: Path): Journal {
        val json = Files.newBufferedReader(path).use { JsonParser.parseReader(it).asJsonObject }
        require(json.get("version")?.asInt == 1) { "Unsupported journal version: $path" }
        require(json.get("findings")?.isJsonObject == true && json.get("waypoints")?.isJsonObject == true)
        val journal = gson.fromJson(json, Journal::class.java)
        require(journal.waypoints.size <= 16)
        for ((name, pos) in journal.waypoints) {
            require(name.isNotBlank() && name.length <= 32)
            validate(pos)
        }
        for ((id, finding) in journal.findings) {
            require(id == finding.id && id.matches(Regex("(base|stash):-?\\d+:-?\\d+")))
            require(finding.evidence.isNotEmpty() && finding.evidence.values.all { it > 0 })
            require(finding.firstSeen > 0 && finding.lastSeen >= finding.firstSeen)
            validate(finding.position)
        }
        return journal
    }

    private fun validate(pos: SavedPosition) {
        require(pos.x in -30_000_000..30_000_000 && pos.z in -30_000_000..30_000_000)
        require(pos.y in -2048..2048)
    }

    fun save(key: String, journal: Journal): CompletableFuture<Void> = CompletableFuture.runAsync({
        check(key !in unreadable) { "Preserving unreadable journal: ${file(key)}" }
        Files.createDirectories(directory)
        val target = file(key)
        val temporary = Files.createTempFile(directory, "journal-", ".tmp")
        try {
            Files.newBufferedWriter(temporary).use { gson.toJson(journal, it) }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }, executor)

    override fun close() = executor.shutdown()
}
