package net.ccbluex.liquidbounce.extras.modules

import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.event.events.ChunkLoadEvent
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.notification
import net.ccbluex.liquidbounce.utils.client.regular
import net.ccbluex.liquidbounce.utils.client.variable
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity

/**
 * Reports chunks with many containers as they load. Chests, barrels, shulker boxes and hoppers all
 * arrive with the chunk data, so a single event and a count is all it takes.
 */
object ModuleStashFinder : ClientModule("StashFinder", ExtrasCategories.EXTRAS) {

    private val minimum by int("Minimum", 4, 1..64, "containers")
    private val notify by boolean("Notify", true)

    private val reported = HashSet<Long>()

    override fun onDisabled() {
        reported.clear()
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        reported.clear()
    }

    @Suppress("unused")
    private val chunkHandler = handler<ChunkLoadEvent> { event ->
        val containers = world.getChunk(event.x, event.z).blockEntities.values
            .count { it is RandomizableContainerBlockEntity }
        if (containers < minimum || !reported.add(ChunkPos.pack(event.x, event.z))) {
            return@handler
        }

        val where = "${event.x shl 4} ${event.z shl 4}"
        chat(regular("$containers containers at ").append(variable(where)), this)
        if (notify) {
            notification("StashFinder", "$containers containers at $where", NotificationEvent.Severity.INFO)
        }
    }

}
