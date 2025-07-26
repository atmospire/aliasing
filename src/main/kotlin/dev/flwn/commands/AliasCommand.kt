package dev.flwn.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import dev.flwn.AtmospireAliasing.db
import dev.flwn.AtmospireAliasing.logger
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import org.mapdb.Serializer
import java.util.concurrent.ConcurrentMap


object AliasCommand {
    const val MAP_NAME = "aliases"

    fun executeAdd(context: CommandContext<ServerCommandSource?>?): Int {
        val source = context?.source

        // get arguments from the command context
        val alias = StringArgumentType.getString(context, "alias")
            .replace("\\s".toRegex(), "_") // Replace spaces with underscores
        val command = StringArgumentType.getString(context, "command")

        val map: ConcurrentMap<String?, String?>? =
            db?.hashMap(MAP_NAME, Serializer.STRING, Serializer.STRING)?.createOrOpen()

        if (map?.containsKey(alias) == true) {
            source?.sendError(Text.literal("Alias '$alias' already exists.").withColor(0xFF0000))
            return -1
        }
        map?.put(alias, command)

        val commandManager = source?.server?.commandManager

        commandManager?.dispatcher?.register(
            CommandManager.literal(alias).executes { context: CommandContext<ServerCommandSource?>? ->
                commandManager.executeWithPrefix(context?.source, command ?: "")
                1
            })

        // Send the updated command tree to all players
        for (player in source?.server?.playerManager?.playerList ?: emptyList()) {
            commandManager?.sendCommandTree(player)
        }

        db?.commit()
        source?.sendFeedback({ Text.literal("Alias '$alias' added for command '$command'").withColor(0x00FF00) }, false)
        return 1
    }

    fun executeRemove(context: CommandContext<ServerCommandSource?>?): Int {
        val source = context?.source
        val alias = StringArgumentType.getString(context, "alias")

        val map: ConcurrentMap<String?, String?>? =
            db?.hashMap(MAP_NAME, Serializer.STRING, Serializer.STRING)?.createOrOpen()

        if (map?.containsKey(alias) != true) {
            source?.sendError(Text.literal("Alias '$alias' does not exist.").withColor(0xFF0000))
            return -1
        } else {
            map.remove(alias)

            // Remove the command from the command manager
            val commandManager = source?.server?.commandManager
            commandManager?.dispatcher?.getRoot()?.children?.remove(
                commandManager.dispatcher?.getRoot()?.getChild(alias)
            )

            // Send the updated command tree to all players
            for (player in source?.server?.playerManager?.playerList ?: emptyList()) {
                commandManager?.sendCommandTree(player)
            }

            source?.sendFeedback({ Text.literal("Alias '$alias' removed.").withColor(0x00FF00) }, false)
            logger.info("Alias '$alias' removed from MapDB database and command manager.")
        }

        db?.commit()
        return 1
    }

    fun executeList(context: CommandContext<ServerCommandSource?>?): Int {
        val source = context?.source

        val map: ConcurrentMap<String?, String?>? =
            db?.hashMap(MAP_NAME, Serializer.STRING, Serializer.STRING)?.createOrOpen()

        val output = Text.literal("Stored command aliases:\n").withColor(0x00FF00)

        for ((index, entry) in (map?.entries ?: emptySet()).withIndex()) {
            val alias = entry.key
            val command = entry.value
            if (alias != null && command != null) {
                output.append(Text.literal("- /$alias : \"/$command\"").withColor(0xFFA500))
                map?.entries?.count()?.minus(1)?.let {
                    if (index < it) {
                        output.append(Text.literal("\n"))
                    }
                }
            }
        }
        if (map.isNullOrEmpty()) {
            output.append(Text.literal("No stored aliases found.").withColor(0xFF0000))
        }

        source?.sendFeedback({ output }, false)
        logger.info("Listed ${map?.entries?.count() ?: 0} stored aliases from MapDB database for ${source?.name}.")

        return 1
    }

    fun registerStoredCommands(dispatcher: CommandDispatcher<ServerCommandSource?>) {
        logger.info("Registering stored command aliases...")

        val map: ConcurrentMap<String?, String?>? =
            db?.hashMap(MAP_NAME, Serializer.STRING, Serializer.STRING)?.createOrOpen()

        for (entry in map?.entries ?: emptySet()) {
            val alias = entry.key
            val command = entry.value
            if (alias != null && command != null) {
                dispatcher.register(
                    CommandManager.literal(alias).executes { ctx: CommandContext<ServerCommandSource?>? ->
                        ctx?.source?.server?.commandManager?.executeWithPrefix(ctx.source, command)
                        1
                    })
            }
        }

        logger.info("Registered ${map?.entries?.count() ?: 0} stored aliases from MapDB database.")
    }
}