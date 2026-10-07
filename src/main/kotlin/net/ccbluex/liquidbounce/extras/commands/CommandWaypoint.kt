package net.ccbluex.liquidbounce.extras.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType.integer
import net.ccbluex.liquidbounce.extras.modules.qol.ModuleWaypoints
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
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.regular
import net.ccbluex.liquidbounce.utils.client.variable
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.Vec3i

/** `.waypoint add|remove|select <name>`, `.waypoint list` and `.waypoint deselect`, on top of [ModuleWaypoints]. */
object CommandWaypoint : CommandRegistrar {

    override fun register(dispatcher: CommandDispatcher<ClientCommandSource>) {
        dispatcher.register("waypoint") {
            val names = suggestions<ClientCommandSource> { ModuleWaypoints.waypoints.keys }
            literal("add") {
                argument("name", ClientStringArgumentType.word()) { name ->
                    optional("x", integer()) { x ->
                        optional("y", integer()) { y ->
                            optional("z", integer()) { z ->
                                exec { context ->
                                    add(context.get(name), position(context.get(x), context.get(y), context.get(z)))
                                }
                            }
                        }
                    }
                }
            }
            literal("remove") {
                argument("name", ClientStringArgumentType.word(), names) { name ->
                    exec { context -> remove(context.get(name)) }
                }
            }
            literal("list") {
                exec { list() }
            }
            literal("select") {
                argument("name", ClientStringArgumentType.word(), names) { name ->
                    exec { context -> select(context.get(name)) }
                }
            }
            literal("deselect") {
                exec {
                    ModuleWaypoints.select(null)
                    Command.SINGLE_SUCCESS
                }
            }
        }
    }

    private fun CmdI18n.position(x: Int?, y: Int?, z: Int?): Vec3i? {
        if (x == null) {
            return null
        }
        if (y == null || z == null) {
            throw CommandException(t("add.coordinates"))
        }
        return Vec3i(x, y, z)
    }

    private fun CmdI18n.add(name: String, position: Vec3i?): Int {
        val player = requireJournal()
        if (!ModuleWaypoints.add(name, position ?: player.blockPosition())) {
            throw CommandException(t("add.rejected", ModuleWaypoints.LIMIT, ModuleWaypoints.NAME_LENGTH))
        }
        ModuleWaypoints.enabled = true
        chat(regular(t("add.added", variable(name))))
        return Command.SINGLE_SUCCESS
    }

    private fun CmdI18n.remove(name: String): Int {
        requireJournal()
        if (!ModuleWaypoints.remove(name)) {
            throw CommandException(t("remove.unknown", name))
        }
        chat(regular(t("remove.removed", variable(name))))
        return Command.SINGLE_SUCCESS
    }

    private fun CmdI18n.list(): Int {
        requireJournal()
        val waypoints = ModuleWaypoints.waypoints
        if (waypoints.isEmpty()) {
            chat(regular(t("list.empty")))
        }
        for ((name, pos) in waypoints) {
            chat(regular(t("list.entry", variable(name), variable(pos.coordinates))))
        }
        return Command.SINGLE_SUCCESS
    }

    private fun CmdI18n.select(name: String): Int {
        requireJournal()
        if (!ModuleWaypoints.select(name)) {
            throw CommandException(t("remove.unknown", name))
        }
        ModuleWaypoints.enabled = true
        chat(regular(t("select.selected", variable(name))))
        return Command.SINGLE_SUCCESS
    }

    @IgnorableReturnValue
    private fun CmdI18n.requireJournal(): LocalPlayer {
        val player = mc.player ?: throw CommandException(t("notInGame"))
        if (!WorldJournal.ready) {
            throw CommandException(t("unreadable"))
        }
        return player
    }

}
