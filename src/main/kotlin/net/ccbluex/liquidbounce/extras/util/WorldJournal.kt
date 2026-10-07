package net.ccbluex.liquidbounce.extras.util

import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.events.ServerConnectEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.logger
import net.minecraft.core.BlockPos
import net.ccbluex.liquidbounce.lang.translation
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.storage.LevelResource
import java.util.Locale

object WorldJournal : EventListener, MinecraftShortcuts {
    private val storage = JournalStorage(ConfigSystem.rootFolder.toPath().resolve("extras-worlds"))
    private var key: String? = null
    private var generation = 0
    private var data = Journal()
    private var endpoint: String? = null

    @Suppress("unused")
    private val connectionHandler = handler<ServerConnectEvent> { event ->
        endpoint = "server:${event.address.host.lowercase(Locale.ROOT)}:${event.address.port}"
    }

    var ready = false
        private set
    var loading = false
        private set

    val findings get() = data.findings
    val waypoints get() = data.waypoints

    @Suppress("unused")
    private val worldHandler = handler<WorldChangeEvent> { event ->
        generation++
        ready = false
        loading = false
        key = null
        data = Journal()
        val level = event.world ?: return@handler
        val identity = mc.singleplayerServer?.getWorldPath(LevelResource.ROOT)?.toAbsolutePath()?.normalize()?.let {
            "local:$it"
        } ?: endpoint ?: return@handler
        val nextKey = "$identity|${level.dimension().identifier()}"
        key = nextKey
        loading = true
        val expected = generation
        storage.load(nextKey).whenComplete { loaded, failure ->
            mc.execute {
                if (generation == expected) {
                    loading = false
                    if (failure == null) {
                        data = loaded
                        ready = true
                    } else {
                        reportFailure(failure)
                    }
                }
            }
        }
    }

    fun record(kind: String, chunk: ChunkPos, pos: BlockPos, evidence: Map<String, Int>): Boolean {
        if (!ready) return false
        val id = "$kind:${chunk.x}:${chunk.z}"
        val previous = findings[id]
        val now = System.currentTimeMillis()
        val finding = Finding(id, SavedPosition(pos), evidence.toMap(), previous?.firstSeen ?: now, now)
        data = data.copy(findings = findings + (id to finding))
        save()
        return previous == null || previous.evidence != evidence
    }

    fun forget(id: String): Boolean {
        if (!ready || id !in findings) return false
        data = data.copy(findings = findings - id)
        save()
        return true
    }

    fun addWaypoint(name: String, pos: BlockPos): Boolean {
        if (!ready || name.isBlank() || name.length > 32 || waypoints.size >= 16 && name !in waypoints) return false
        data = data.copy(waypoints = waypoints + (name to SavedPosition(pos)))
        save()
        return true
    }

    fun removeWaypoint(name: String): Boolean {
        if (!ready || name !in waypoints) return false
        data = data.copy(waypoints = waypoints - name)
        save()
        return true
    }

    private fun save() {
        val currentKey = key ?: return
        val expected = generation
        storage.save(currentKey, data).exceptionally { failure ->
            mc.execute {
                if (generation == expected && ready) {
                    ready = false
                    reportFailure(failure)
                }
            }
            null
        }
    }

    private fun reportFailure(failure: Throwable) {
        logger.warn("Unable to read or save Extras world journal", failure)
        chat(translation("liquidbounce.extras.journal.failed"))
    }

    fun stop() = storage.close()
}
