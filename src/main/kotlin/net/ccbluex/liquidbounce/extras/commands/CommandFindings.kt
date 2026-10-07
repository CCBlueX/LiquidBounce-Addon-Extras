package net.ccbluex.liquidbounce.extras.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import net.ccbluex.liquidbounce.extras.modules.basehunting.ModuleBaseFinder
import net.ccbluex.liquidbounce.extras.modules.basehunting.ModuleStashFinder
import net.ccbluex.liquidbounce.extras.modules.qol.ModuleWaypoints
import net.ccbluex.liquidbounce.extras.util.Finding
import net.ccbluex.liquidbounce.extras.util.WorldJournal
import net.ccbluex.liquidbounce.extras.util.coordinates
import net.ccbluex.liquidbounce.features.command.CommandException
import net.ccbluex.liquidbounce.features.command.CommandRegistrar
import net.ccbluex.liquidbounce.features.command.arguments.ClientStringArgumentType
import net.ccbluex.liquidbounce.features.command.brigadier.ClientCommandSource
import net.ccbluex.liquidbounce.features.command.brigadier.CmdI18n
import net.ccbluex.liquidbounce.features.command.brigadier.get
import net.ccbluex.liquidbounce.features.command.brigadier.register
import net.ccbluex.liquidbounce.features.command.brigadier.suggestions
import net.ccbluex.liquidbounce.lang.translation
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.regular
import net.ccbluex.liquidbounce.utils.client.variable
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** `.findings list`, `.findings show|forget <id>` and `.findings waypoint <id> <name>`, on top of [WorldJournal]. */
object CommandFindings : CommandRegistrar {

    private val time = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())

    override fun register(dispatcher: CommandDispatcher<ClientCommandSource>) {
        dispatcher.register("findings") {
            val ids = suggestions<ClientCommandSource> { WorldJournal.findings.keys }
            literal("list") {
                exec { list() }
            }
            literal("show") {
                argument("id", ClientStringArgumentType.word(), ids) { id ->
                    exec { context -> show(context.get(id)) }
                }
            }
            literal("forget") {
                argument("id", ClientStringArgumentType.word(), ids) { id ->
                    exec { context -> forget(context.get(id)) }
                }
            }
            literal("waypoint") {
                argument("id", ClientStringArgumentType.word(), ids) { id ->
                    argument("name", ClientStringArgumentType.word()) { name ->
                        exec { context -> waypoint(context.get(id), context.get(name)) }
                    }
                }
            }
        }
    }

    private fun CmdI18n.list(): Int {
        requireJournal()
        if (WorldJournal.findings.isEmpty()) {
            chat(regular(t("list.empty")))
        }
        WorldJournal.findings.forEach { (id, finding) -> tell(id, finding) }
        return Command.SINGLE_SUCCESS
    }

    private fun CmdI18n.show(id: String): Int {
        tell(id, find(id))
        return Command.SINGLE_SUCCESS
    }

    private fun CmdI18n.forget(id: String): Int {
        requireJournal()
        if (!WorldJournal.forget(id)) {
            throw CommandException(t("unknown", id))
        }
        chat(regular(t("forget.forgotten", variable(id))))
        return Command.SINGLE_SUCCESS
    }

    private fun CmdI18n.waypoint(id: String, name: String): Int {
        if (!ModuleWaypoints.add(name, find(id).position)) {
            val limits = arrayOf(ModuleWaypoints.LIMIT, ModuleWaypoints.NAME_LENGTH)
            throw CommandException(translation("liquidbounce.command.waypoint.add.rejected", *limits))
        }
        ModuleWaypoints.enabled = true
        ModuleWaypoints.select(name)
        chat(regular(t("waypoint.saved", variable(name))))
        return Command.SINGLE_SUCCESS
    }

    private fun CmdI18n.tell(id: String, finding: Finding) {
        val evidence = when (id.substringBefore(':')) {
            "base" -> ModuleBaseFinder.describe(finding.evidence)
            else -> ModuleStashFinder.describe(finding.evidence)
        }
        val position = variable(finding.position.coordinates)
        chat(regular(t("list.entry", variable(id), position, evidence, time.format(finding.lastSeen))))
    }

    private fun CmdI18n.find(id: String): Finding {
        requireJournal()
        return WorldJournal.findings[id] ?: throw CommandException(t("unknown", id))
    }

    private fun CmdI18n.requireJournal() {
        if (mc.player == null) {
            throw CommandException(t("notInGame"))
        }
        if (!WorldJournal.ready) {
            throw CommandException(t("unreadable"))
        }
    }

}
