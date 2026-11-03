package net.ccbluex.liquidbounce.extras.hud

import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.client.notification
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.minecraft.network.chat.Component
import net.minecraft.util.ARGB
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import java.util.EnumMap
import kotlin.math.PI
import kotlin.math.sin

/** Totems, golden apples, end crystals and experience bottles in the inventory, red and pulsing when low. */
object ResourcesHud : TextHudComponent(
    "Resources", "Totems, golden apples, end crystals and experience bottles in your inventory.", "", row = 10,
) {

    private const val PULSE_MILLIS = 1000.0
    private val lowColor = Color4b(255, 85, 85)

    private val notify by boolean("Notify", true)
    private val limits = Resource.entries.associateWith { int("Low${it.label}", it.limit, 0..64) }

    /** How many of each the inventory held on the last tick. */
    val counts: Map<Resource, Int>
        field = EnumMap<Resource, Int>(Resource::class.java)

    /** What fell below its limit; a limit of 0 never warns. */
    val low: Set<Resource> get() = counts.filter { (resource, count) -> count < limit(resource) }.keys

    @Suppress("unused")
    private val countHandler = handler<GameTickEvent> {
        val player = mc.player ?: return@handler
        // Dying empties the inventory, which is no news
        if (!player.isAlive) {
            counts.clear()
            return@handler
        }

        for (resource in Resource.entries) {
            val count = Slots.All.sumOf { if (it.itemStack.item in resource.items) it.itemStack.count else 0 }
            val before = counts.put(resource, count) ?: continue
            val limit = limit(resource)
            if (notify && before >= limit && count < limit) {
                notification(name, "${resource.label} low: $count / $limit", NotificationEvent.Severity.INFO)
            }
        }
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> { counts.clear() }

    override fun onDisabled() = counts.clear()

    override fun value(): Component {
        if (counts.isEmpty()) {
            return Component.literal(UNAVAILABLE)
        }

        val low = low
        val text = Component.empty()
        for ((resource, count) in counts) {
            if (text.siblings.isNotEmpty()) {
                text.append("  ")
            }
            val entry = Component.literal("${resource.label} $count")
            text.append(if (resource in low) entry.withColor(pulse()) else entry)
        }
        return text
    }

    private fun limit(resource: Resource) = limits.getValue(resource).get()

    private fun pulse(): Int {
        val phase = (sin(System.currentTimeMillis() / PULSE_MILLIS * 2 * PI) + 1) / 2
        // Not Color4b.interpolateTo: 0.42.0 only takes a Double, 0.42.1 only a Float
        return ARGB.srgbLerp(phase.toFloat(), lowColor.argb, lowColor.darker().argb) and 0xFFFFFF
    }

    enum class Resource(val label: String, val limit: Int, vararg val items: Item) {
        TOTEMS("Totems", 2, Items.TOTEM_OF_UNDYING),
        GAPPLES("Gapples", 5, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE),
        CRYSTALS("Crystals", 16, Items.END_CRYSTAL),
        BOTTLES("Bottles", 8, Items.EXPERIENCE_BOTTLE),
    }

}
