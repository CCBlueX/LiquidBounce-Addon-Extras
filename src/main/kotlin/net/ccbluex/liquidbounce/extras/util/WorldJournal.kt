package net.ccbluex.liquidbounce.extras.util

import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.config.gson.publicGson
import net.ccbluex.liquidbounce.config.gson.util.readJson
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.ServerConnectEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.utils.client.logger
import net.minecraft.core.Vec3i
import net.minecraft.world.level.storage.LevelResource
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/**
 * What the add-on remembers about a server or singleplayer world, one file each in [folder], kept apart by
 * dimension. A file that cannot be read stays untouched, and nothing is saved until the next world.
 */
object WorldJournal : EventListener, MinecraftShortcuts {

    private class Dimension(val waypoints: MutableMap<String, Vec3i> = linkedMapOf())

    val folder = ConfigSystem.rootFolder.resolve("extras-worlds")

    private var server: String? = null
    private var identity: String? = null
    private var file: File? = null
    private var dimensions = HashMap<String, Dimension>()

    val ready get() = file != null

    val waypoints: Map<String, Vec3i> get() = current()?.waypoints.orEmpty()

    // The level of a server arrives before the player that knows the connection
    @Suppress("unused")
    private val connectHandler = handler<ServerConnectEvent> { event -> server = event.serverInfo.ip }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> { event ->
        val next = event.world?.let { identify() }
        if (next == identity) {
            return@handler
        }
        identity = next
        dimensions = HashMap()
        file = next?.let { folder.resolve("$it.json") }?.takeIf(::read)
    }

    @IgnorableReturnValue
    fun putWaypoint(name: String, pos: Vec3i) = edit { waypoints[name] = pos }

    @IgnorableReturnValue
    fun removeWaypoint(name: String) = name in waypoints && edit { waypoints.remove(name) }

    @IgnorableReturnValue
    private inline fun edit(change: Dimension.() -> Unit): Boolean {
        val file = file ?: return false
        dimensions.getOrPut(dimension() ?: return false, ::Dimension).change()
        write(file)
        return true
    }

    private fun current() = dimensions[dimension()]

    private fun dimension() = mc.level?.dimension()?.identifier()?.toString()

    private fun identify(): String? {
        val name = mc.singleplayerServer?.let { "local-${it.getWorldPath(LevelResource.ROOT).normalize().fileName}" }
            ?: server?.let { "server-${it.lowercase()}" }
            ?: return null
        return name.replace(Regex("[^\\w.-]"), "_")
    }

    private fun read(file: File) = runCatching {
        if (file.exists()) {
            dimensions = file.readJson()
        }
    }.onFailure { logger.warn("Unable to read {}", file, it) }.isSuccess

    private fun write(file: File) {
        runCatching {
            folder.mkdirs()
            val temporary = File(folder, "${file.name}.tmp")
            temporary.writeText(publicGson.toJson(dimensions))
            Files.move(temporary.toPath(), file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        }.onFailure { logger.warn("Unable to save {}", file, it) }
    }

}
