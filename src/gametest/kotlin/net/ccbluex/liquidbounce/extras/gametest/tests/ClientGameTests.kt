package net.ccbluex.liquidbounce.extras.gametest.tests

import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.extras.gametest.harness.GameTestScope
import net.ccbluex.liquidbounce.extras.gametest.harness.PaperGameTest
import net.ccbluex.liquidbounce.extras.gametest.harness.chatContains
import net.ccbluex.liquidbounce.extras.gametest.harness.command
import net.ccbluex.liquidbounce.extras.gametest.harness.screenshot
import net.ccbluex.liquidbounce.extras.gametest.harness.setting
import net.ccbluex.liquidbounce.extras.hud.ClickCounter
import net.ccbluex.liquidbounce.extras.hud.ClockHud
import net.ccbluex.liquidbounce.extras.hud.FpsHud
import net.ccbluex.liquidbounce.extras.hud.MetricHudComponent
import net.ccbluex.liquidbounce.extras.hud.PingHud
import net.ccbluex.liquidbounce.extras.hud.ResourcesHud
import net.ccbluex.liquidbounce.extras.hud.ResourcesHud.Resource
import net.ccbluex.liquidbounce.extras.hud.TpsHud
import net.ccbluex.liquidbounce.extras.hud.SpeedometerHud
import net.ccbluex.liquidbounce.extras.hud.extrasHudComponents
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleClickGui
import net.ccbluex.liquidbounce.integration.interop.persistant.PersistentLocalStorage
import net.ccbluex.liquidbounce.integration.screen.impl.CustomStandaloneMinecraftScreen
import net.ccbluex.liquidbounce.integration.theme.ThemeManager
import net.ccbluex.liquidbounce.integration.theme.component.HudComponentManager
import net.ccbluex.liquidbounce.lang.LanguageManager
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention
import net.ccbluex.liquidbounce.utils.render.Alignment
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSetTimePacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import com.mojang.blaze3d.platform.InputConstants
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Collections

/**
 * Plain vanilla movement has to pass, or no other verdict means anything. Then the negative control: a
 * client claiming to be five blocks up has to be caught.
 */
class HarnessGameTest : PaperGameTest({
    input.holdKey { it.keyUp }
    ticks(40)
    input.holdKey { it.keySprint }
    input.holdKey { it.keyJump }
    ticks(60)
    input.releaseKey { it.keyJump }
    input.releaseKey { it.keySprint }
    input.releaseKey { it.keyUp }
    ticks(20)

    expectViolation("Grim to catch a move five blocks up") {
        client { minecraft ->
            val player = minecraft.player!!
            minecraft.connection!!.send(ServerboundMovePlayerPacket.Pos(player.x, player.y + 5, player.z, true, false))
        }
    }
})

class AddonGameTest : PaperGameTest({
    client {
        val modules = ModuleManager.filter { it.category in ExtrasCategories.all }
        check(modules.size == 51) { "expected 51 add-on modules, found ${modules.map { it.name }}" }
        check(ExtrasCategories.all.all { ModuleCategories.byName(it.tag) === it }) { "a category is missing" }
        for (module in modules) {
            check(LanguageManager.hasFallbackTranslation(module.descriptionKey!!)) {
                "${module.name} has no description"
            }
        }
    }

    // Panels read the browser's localStorage as they mount, before the page copies the client's storage into
    // it. The page cached on joining predates this layout, the next one syncs it, the one after shows it.
    client {
        for (category in ModuleCategories.entries) {
            val index = ExtrasCategories.all.indexOf(category)
            PersistentLocalStorage.map["clickgui.panel.${category.tag}"] = if (index < 0) {
                """{"top":20,"left":-10000,"expanded":false,"scrollTop":0,"zIndex":0}"""
            } else {
                """{"top":150,"left":${20 + index * 300},"expanded":true,"scrollTop":0,"zIndex":1}"""
            }
        }
        ModuleClickGui.invalidate()
        ModuleClickGui.enabled = true
    }
    awaitClickGui()
    client { ModuleClickGui.invalidate() }
    awaitClickGui()
    screenshot("ClickGui")
    client { ModuleClickGui.enabled = false }
})

private fun GameTestScope.awaitClickGui() {
    awaitClient("the ClickGUI page to load") {
        val browser = (it.gui.screen() as? CustomStandaloneMinecraftScreen)?.browser
        browser != null && browser.isInitialized && browser.state.isCompleted
    }
    ticks(40)
}

class HudGameTest : PaperGameTest({
    client {
        val catalog = HudComponentManager.getComponentCatalog(ThemeManager.theme!!.metadata.id)
        for (component in extrasHudComponents) {
            check(catalog.any { it.name == component.name && it.canAdd && it.singleton }) {
                "${component.name} is not in the HUD editor's catalog"
            }
            check(HudComponentManager.addComponent(component.id.toString()) === component)
            check(HudComponentManager.addComponent(component.id.toString()) == null) {
                "${component.name} could be added twice"
            }
            check(component.text.string.isNotBlank()) { "${component.name} shows nothing" }
        }
    }

    // Settings and placement survive a save and a load of the add-on's config
    client {
        val config = ConfigSystem.configs.first { it.name == "extras-hud" }
        val defaults = ConfigSystem.serializeValueGroup(config)
        SpeedometerHud.setting<String>("Prefix").set("Moving: ")
        SpeedometerHud.setting<Boolean>("ShowUnit").set(false)
        SpeedometerHud.alignment.setFrom(Alignment(Alignment.ScreenAxisX.RIGHT, 32, Alignment.ScreenAxisY.BOTTOM, 48))
        config.saveToDisk()
        ConfigSystem.deserializeValueGroup(config, defaults)
        config.loadFromDisk()
        check(SpeedometerHud.enabled && SpeedometerHud.text.string.startsWith("Moving: "))
        check(!SpeedometerHud.text.string.endsWith("b/s"))
        check(SpeedometerHud.alignment.horizontalAlignment == Alignment.ScreenAxisX.RIGHT)
        ConfigSystem.deserializeValueGroup(config, defaults)
        config.saveToDisk()
    }

    repeat(3) { input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT) }
    repeat(2) { input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT) }
    val clicks = client { ClickCounter.left to ClickCounter.right }
    check(clicks == 3 to 2) { "counted $clicks clicks instead of (3, 2)" }
    awaitClient("the clicks to leave the one second window", timeout = 40) {
        ClickCounter.left == 0 && ClickCounter.right == 0
    }

    input.holdKey { it.keyUp }
    ticks(20)
    screenshot("Hud")
    input.releaseKey { it.keyUp }
    client { extrasHudComponents.forEach { it.enabled = false } }
})

class HudGraphGameTest : PaperGameTest({
    val metrics = listOf(FpsHud, TpsHud, PingHud)
    client {
        for (metric in metrics) {
            check(HudComponentManager.addComponent(metric.id.toString()) === metric)
            val withGraph = metric.guiScaledHeight
            metric.setting<Boolean>("Graph").set(false)
            check(metric.guiScaledHeight < withGraph) { "${metric.name}'s graph takes no room" }
            metric.setting<Boolean>("Graph").set(true)
        }
    }

    awaitClient("the first samples") { metrics.all { it.history.size >= 2 } }
    // The server's tick rate needs two time updates before TPS has a reading
    awaitClient("a full graph", timeout = MetricHudComponent.SAMPLES * (MetricHudComponent.SAMPLE_TICKS + 1) + 100) {
        metrics.all { it.history.size == MetricHudComponent.SAMPLES }
    }
    ticks(MetricHudComponent.SAMPLE_TICKS * 2)
    client {
        check(metrics.all { it.history.size == MetricHudComponent.SAMPLES }) { "the graph outgrew its samples" }
        check(FpsHud.reading > 0) { "the FPS read ${FpsHud.reading}" }
        // On the same machine the latency is far below the good limit
        check(PingHud.text.valueColor() == PingHud.colorSetting("GoodColor")) { "the ping is not graded good" }
    }
    screenshot("HudGraphs")

    TimeUpdateBlocker().use {
        awaitClient("TPS to go stale", timeout = 100) { TpsHud.stale }
        client {
            check(TpsHud.text.string == "TPS: 0.00") { "a silent server reads '${TpsHud.text.string}'" }
            check(TpsHud.text.valueColor() == TpsHud.colorSetting("BadColor")) { "a silent server is not graded bad" }
        }
        awaitClient("a stale sample", timeout = 40) { TpsHud.history.last() == 0.0 }
    }
    awaitClient("TPS to recover", timeout = 60) { !TpsHud.stale && TpsHud.reading!! > 0 }

    client {
        metrics.forEach { it.enabled = false }
        check(metrics.all { it.history.isEmpty() }) { "disabling kept the graphs" }
    }
})

private fun Component.valueColor() = siblings.first().style.color?.value

private fun MetricHudComponent.colorSetting(name: String) = setting<Color4b>(name).get().argb and 0xFFFFFF

/** Drops the server's time updates while it is open, as if the server stopped ticking. */
private class TimeUpdateBlocker : EventListener, AutoCloseable {

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent>(priority = EventPriorityConvention.FIRST_PRIORITY) { event ->
        if (event.packet is ClientboundSetTimePacket) {
            event.cancelEvent()
        }
    }

    override fun close() = EventManager.unregisterEventHandler(this)

}

class ResourcesGameTest : PaperGameTest({
    client { check(HudComponentManager.addComponent(ResourcesHud.id.toString()) === ResourcesHud) }
    run("give $playerName minecraft:totem_of_undying 3")
    run("give $playerName minecraft:golden_apple 4")
    run("give $playerName minecraft:enchanted_golden_apple 2")
    run("give $playerName minecraft:experience_bottle 64")
    run("give $playerName minecraft:experience_bottle 10")
    val stocked = mapOf(Resource.TOTEMS to 3, Resource.GAPPLES to 6, Resource.CRYSTALS to 0, Resource.BOTTLES to 74)
    awaitClient("the counts") { ResourcesHud.counts == stocked }
    client { check(ResourcesHud.low == setOf(Resource.CRYSTALS)) { "low are ${ResourcesHud.low}" } }

    NotificationLog().use { log ->
        run("clear $playerName minecraft:totem_of_undying 2")
        awaitClient("the totems to run low") { Resource.TOTEMS in ResourcesHud.low }
        ticks(5)
        check(log.messages.size == 1 && "Totems low: 1 / 2" in log.messages.single()) { "notified ${log.messages}" }
        client {
            val entries = ResourcesHud.text.siblings.first().siblings
            check(entries.first { it.string.startsWith("Totems") }.style.color != null) { "the low totems are not red" }
            check(entries.first { it.string.startsWith("Gapples") }.style.color == null) { "the gapples are red" }
        }
        screenshot("Resources")

        run("clear $playerName minecraft:totem_of_undying 1")
        awaitClient("the last totem gone") { ResourcesHud.counts[Resource.TOTEMS] == 0 }
        run("give $playerName minecraft:end_crystal 64")
        awaitClient("the crystals") { ResourcesHud.counts[Resource.CRYSTALS] == 64 }
        ticks(5)
        check(log.messages.size == 1) { "staying low or restocking notified again: ${log.messages}" }
    }

    client {
        ResourcesHud.enabled = false
        check(ResourcesHud.counts.isEmpty()) { "disabling kept the counts" }
    }
})

/** Notifications shown while it is open. */
private class NotificationLog : EventListener, AutoCloseable {

    val messages: MutableList<String> = Collections.synchronizedList(mutableListOf())

    @Suppress("unused")
    private val notificationHandler = handler<NotificationEvent> { messages += it.message }

    override fun close() = EventManager.unregisterEventHandler(this)

}

class ClockGameTest : PaperGameTest({
    client { check(HudComponentManager.addComponent(ClockHud.id.toString()) === ClockHud) }
    val full = client { ClockHud.text.string }
    check(Regex("""(Mon|Tue|Wed|Thu|Fri|Sat|Sun) \d{2}:\d{2}:\d{2}""").matches(full)) { "the clock reads '$full'" }

    val short = client {
        ClockHud.setting<Boolean>("Weekday").set(false)
        ClockHud.setting<Boolean>("Seconds").set(false)
        ClockHud.text.string
    }
    check(Regex("""\d{2}:\d{2}""").matches(short)) { "without weekday and seconds the clock reads '$short'" }
    client {
        ClockHud.setting<Boolean>("Weekday").set(true)
        ClockHud.setting<Boolean>("Seconds").set(true)
    }

    client {
        val odd = LocalDateTime.of(2026, 11, 9, 10, 20, 31)
        check(ClockHud.format(odd) == "Mon 10:20:31") { "a steady colon reads '${ClockHud.format(odd)}'" }
        ClockHud.setting<Boolean>("BlinkingColon").set(true)
        check(ClockHud.format(odd) == "Mon 10 20 31") { "the colon is still on: '${ClockHud.format(odd)}'" }
        check(ClockHud.format(odd.withSecond(30)) == "Mon 10:20:30") { "the colon stays off in even seconds" }

        val plain = ClockHud.guiScaledHeight
        ClockHud.setting<Boolean>("MinuteBar").set(true)
        check(ClockHud.guiScaledHeight > plain) { "the minute bar takes no room" }

        check(ClockHud.minuteProgress(LocalTime.of(10, 20, 30)) == 0.5f)
        check(ClockHud.minuteProgress(LocalTime.of(10, 20, 0)) == 0f)
        val now = LocalTime.now()
        val ahead = (ClockHud.minuteProgress * 60 - now.second + 60) % 60
        check(ahead < 2) { "the bar is at ${ClockHud.minuteProgress} at second ${now.second}" }
    }
    screenshot("Clock")

    client {
        ClockHud.setting<Boolean>("BlinkingColon").set(false)
        ClockHud.setting<Boolean>("MinuteBar").set(false)
        ClockHud.enabled = false
    }
})

class WhereGameTest : PaperGameTest({
    command("where")
    awaitClient("the position in chat") { it.chatContains("You are at 0 -60 0") }
    screenshot("Where")

    val since = server.console.size
    command("where share")
    awaitConsole("the shared position in the server chat", since) { "<$playerName> I am at 0 -60 0" in it.message }
})
