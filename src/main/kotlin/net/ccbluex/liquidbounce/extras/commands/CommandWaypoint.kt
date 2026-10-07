package net.ccbluex.liquidbounce.extras.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType.integer
import net.minecraft.core.BlockPos
import net.ccbluex.liquidbounce.extras.util.WorldJournal
import net.ccbluex.liquidbounce.extras.modules.qol.ModuleWaypoints
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

/** `.waypoint add|remove <name>` and `.waypoint list`, on top of [ModuleWaypoints]. */
object CommandWaypoint : CommandRegistrar {

    override fun register(dispatcher: CommandDispatcher<ClientCommandSource>) {
        dispatcher.register("waypoint") {
            literal("add") {
                argument("name", ClientStringArgumentType.word()) { name ->
                    optional("x", integer(-30_000_000, 30_000_000)) { x ->
                        optional("y", integer(-2048, 2048)) { y ->
                            optional("z", integer(-30_000_000, 30_000_000)) { z ->
                                exec { ctx ->
                                    add(ctx.get(name), coordinates(ctx.get(x), ctx.get(y), ctx.get(z)))
                                }
                            }
                        }
                    }
                }
            }
            literal("remove") {
                val names = suggestions<ClientCommandSource> { ModuleWaypoints.waypoints.keys }
                argument("name", ClientStringArgumentType.word(), names) { name ->
                    exec { context -> remove(context.get(name)) }
                }
            }
            literal("list") {
                exec { list() }
            }
            literal("select") {
                argument("name", ClientStringArgumentType.word()) { name ->
                    exec { ctx ->
                        requireReady()
                        val selected = ctx.get(name)
                        if (!ModuleWaypoints.select(selected)) throw CommandException(t("remove.unknown", selected))
                        ModuleWaypoints.enabled = true
                        chat(regular(t("select.selected", selected)))
                        Command.SINGLE_SUCCESS
                    }
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

    private fun CmdI18n.requireReady() {
        if (!WorldJournal.ready) throw CommandException(t("notReady"))
    }

    private fun CmdI18n.coordinates(x: Int?, y: Int?, z: Int?): BlockPos? {
        if (x == null) return null
        if (y == null || z == null) throw CommandException(t("coordinates"))
        return BlockPos(x, y, z)
    }

    private fun CmdI18n.add(name: String, position: BlockPos? = null): Int {
        requireReady()
        val player = mc.player ?: throw CommandException(t("notInGame"))
        if (!ModuleWaypoints.add(name, position ?: player.blockPosition())) {
            throw CommandException(t("add.rejected", ModuleWaypoints.LIMIT, ModuleWaypoints.NAME_LENGTH))
        }
        ModuleWaypoints.enabled = true
        chat(regular(t("add.added", variable(name))))
        return Command.SINGLE_SUCCESS
    }

    private fun CmdI18n.remove(name: String): Int {
        requireReady()
        if (!ModuleWaypoints.remove(name)) {
            throw CommandException(t("remove.unknown", name))
        }
        chat(regular(t("remove.removed", variable(name))))
        return Command.SINGLE_SUCCESS
    }

    private fun CmdI18n.list(): Int {
        requireReady()
        val waypoints = ModuleWaypoints.waypoints
        if (waypoints.isEmpty()) {
            chat(regular(t("list.empty")))
        }
        for ((name, pos) in waypoints) {
            chat(regular(t("list.entry", variable(name), variable(pos.coordinates))))
        }
        return Command.SINGLE_SUCCESS
    }

}
