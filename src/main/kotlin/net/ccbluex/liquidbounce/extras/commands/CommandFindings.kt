package net.ccbluex.liquidbounce.extras.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
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
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.regular
import java.time.Instant

object CommandFindings : CommandRegistrar {
    override fun register(dispatcher: CommandDispatcher<ClientCommandSource>) {
        dispatcher.register("findings") {
            literal("list") {
                exec {
                    requireReady()
                    if (WorldJournal.findings.isEmpty()) chat(regular(t("empty")))
                    WorldJournal.findings.values.forEach { show(it) }
                    Command.SINGLE_SUCCESS
                }
            }
            literal("show") {
                argument("id", ClientStringArgumentType.word()) { id ->
                    exec { ctx -> show(find(ctx.get(id))); Command.SINGLE_SUCCESS }
                }
            }
            literal("forget") {
                argument("id", ClientStringArgumentType.word()) { id ->
                    exec { ctx ->
                        val finding = find(ctx.get(id))
                        WorldJournal.forget(finding.id)
                        chat(regular(t("forgotten", finding.id)))
                        Command.SINGLE_SUCCESS
                    }
                }
            }
            literal("waypoint") {
                argument("id", ClientStringArgumentType.word()) { id ->
                    argument("name", ClientStringArgumentType.word()) { name ->
                        exec { ctx ->
                            val finding = find(ctx.get(id))
                            val label = ctx.get(name)
                            if (!ModuleWaypoints.add(label, finding.position.blockPos())) {
                                throw CommandException(t("rejected"))
                            }
                            ModuleWaypoints.enabled = true
                            ModuleWaypoints.select(label)
                            chat(regular(t("saved", label)))
                            Command.SINGLE_SUCCESS
                        }
                    }
                }
            }
        }
    }

    private fun CmdI18n.requireReady() {
        if (!WorldJournal.ready) throw CommandException(t("notReady"))
    }

    private fun CmdI18n.find(id: String): Finding {
        requireReady()
        return WorldJournal.findings[id] ?: throw CommandException(t("unknown", id))
    }

    private fun CmdI18n.show(finding: Finding) {
        chat(regular(t("entry", finding.id, finding.position.blockPos().coordinates,
            finding.evidence.entries.joinToString { (reason, count) -> "$reason=$count" },
            Instant.ofEpochMilli(finding.lastSeen).toString())))
    }
}
