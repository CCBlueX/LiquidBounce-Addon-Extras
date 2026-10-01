package net.ccbluex.liquidbounce.extras.modules.extras

import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.extras.util.coordinates
import net.ccbluex.liquidbounce.extras.util.report
import net.ccbluex.liquidbounce.features.misc.FriendManager
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.minecraft.world.entity.player.Player
import java.util.UUID

/** Reports players who are not your friends as they come within the radius. */
object ModuleIntruderAlert : ClientModule("IntruderAlert", ExtrasCategories.EXTRAS) {

    private const val EXIT_MARGIN = 4

    private val radius by int("Radius", 32, 4..128, "blocks")
    private val notify by boolean("Notify", true)

    val intruders: Set<UUID>
        field = HashSet<UUID>()

    @Suppress("unused")
    private val watchHandler = handler<GameTickEvent> {
        val strangers = world.players().filter(::isStranger)
        // Someone pacing along the boundary would otherwise be reported with every step
        val stillNear = strangers.filter { player.distanceTo(it) <= radius + EXIT_MARGIN }.mapTo(HashSet()) { it.uuid }
        intruders.retainAll(stillNear)
        for (stranger in strangers) {
            if (player.distanceTo(stranger) <= radius && intruders.add(stranger.uuid)) {
                report("entered", stranger.gameProfile.name, stranger.blockPosition().coordinates, notify = notify)
            }
        }
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> { intruders.clear() }

    override fun onDisabled() = intruders.clear()

    private fun isStranger(other: Player) =
        other !== player && other.isAlive && !other.isSpectator && !FriendManager.isFriend(other)

}
