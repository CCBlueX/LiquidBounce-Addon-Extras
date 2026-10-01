package net.ccbluex.liquidbounce.extras.hud

import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.DisconnectEvent
import net.ccbluex.liquidbounce.event.events.InputHandleEvent
import net.ccbluex.liquidbounce.event.events.MouseButtonEvent
import net.ccbluex.liquidbounce.event.events.UseCooldownEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleAutoClicker
import net.ccbluex.liquidbounce.utils.client.mc
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Clicks over the last second, counted from input: swing packets would also count mining, and item use
 * packets every held right click.
 */
object ClickCounter : EventListener {

    private val leftClicks = ClickWindow()
    private val rightClicks = ClickWindow()
    private var pendingRightClicks = 0
    private var itemUses = 0

    val left get() = leftClicks.count()
    val right get() = rightClicks.count()

    private val playing get() = mc.player != null && mc.gui.screen() == null

    @Suppress("unused")
    private val mouseHandler = handler<MouseButtonEvent> { event ->
        if (playing && event.isLeftClick) leftClicks.add()
        if (playing && event.isRightClick) pendingRightClicks++
    }

    @Suppress("unused")
    private val itemUseHandler = handler<UseCooldownEvent> {
        if (playing) {
            itemUses++
        }
    }

    @Suppress("unused")
    private val inputHandler = handler<InputHandleEvent> {
        if (playing) {
            val attack = ModuleAutoClicker.AttackButton
            if (attack.running) {
                repeat(attack.clicker.clickAmount ?: 0) { leftClicks.add() }
            }
            // A right click also starts using the item, so count the two once; uses without a click come
            // from a held button, FastPlace or AutoClicker
            repeat(maxOf(pendingRightClicks, itemUses)) { rightClicks.add() }
        }
        pendingRightClicks = 0
        itemUses = 0
    }

    @Suppress("unused")
    private val disconnectHandler = handler<DisconnectEvent> {
        leftClicks.clear()
        rightClicks.clear()
    }

}

class ClickWindow(private val clock: TimeSource = TimeSource.Monotonic) {

    private val clicks = ArrayDeque<TimeMark>()

    fun add() {
        prune()
        clicks.addLast(clock.markNow())
    }

    fun count(): Int {
        prune()
        return clicks.size
    }

    fun clear() = clicks.clear()

    private fun prune() {
        while (clicks.firstOrNull()?.let { it.elapsedNow() >= 1.seconds } == true) {
            clicks.removeFirst()
        }
    }

}
