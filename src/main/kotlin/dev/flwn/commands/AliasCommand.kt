package dev.flwn.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import dev.flwn.AtmospireAliasing.kottage
import dev.flwn.AtmospireAliasing.logger
import io.github.irgaly.kottage.KottageList
import io.github.irgaly.kottage.KottageStorage
import io.github.irgaly.kottage.add
import kotlinx.coroutines.runBlocking
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text


object AliasCommand {
    val STORAGE_NAME = "stored_aliases"
    val LIST_NAME = "alias_list"

    fun executeAdd(context: CommandContext<ServerCommandSource?>?): Int {
        val source = context?.source

        // get arguments from the command context
        val alias = StringArgumentType.getString(context, "alias").replace("\\s".toRegex(), "_") // Replace spaces with underscores
        val command = StringArgumentType.getString(context, "command")

        // Open Kottage database as storage mode
        val storage: KottageStorage = kottage.storage(STORAGE_NAME)
        val list: KottageList = storage.list(LIST_NAME)

        runBlocking {
            if (storage.exists(alias)) {
                source?.sendError(Text.literal("Alias '$alias' already exists.").withColor(0xFF0000))
                return@runBlocking -1
            }
            list.add(alias, command)
        }

        val commandManager = source?.server?.commandManager

        commandManager?.dispatcher?.register(
            CommandManager.literal(alias)
                .executes { context: CommandContext<ServerCommandSource?>? ->
                    commandManager.executeWithPrefix(context?.source, command ?: "")
                    1
                }
        )

        // Send the updated command tree to all players
        for (player in source?.server?.playerManager?.playerList ?: emptyList()) {
            commandManager?.sendCommandTree(player)
        }

        source?.sendFeedback({ Text.literal("Alias '$alias' added for command '$command'").withColor(0x00FF00) }, false)
        return 1
    }

    fun executeRemove(context: CommandContext<ServerCommandSource?>?): Int {
        val source = context?.source
        val alias = StringArgumentType.getString(context, "alias")

        // Open Kottage database as storage mode
        val storage: KottageStorage = kottage.storage(STORAGE_NAME)

        runBlocking {
            if (!storage.exists(alias)) {
                source?.sendError(Text.literal("Alias '$alias' does not exist.").withColor(0xFF0000))
                return@runBlocking -1
            } else {
                storage.remove(alias)

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
                logger.info("Alias '$alias' removed from Kottage database and command manager.")
            }
        }

        return 1
    }

    fun executeList(context: CommandContext<ServerCommandSource?>?): Int {
        val source = context?.source

        val storage: KottageStorage = kottage.storage(STORAGE_NAME)
        val list: KottageList = storage.list(LIST_NAME)

        val output = Text.literal("Stored command aliases:\n").withColor(0x00FF00)
        runBlocking {
            for (i in 0 until list.getSize()) {
                val listEntry = list.getByIndex(i)
                val alias = listEntry?.entry<String>()?.key
                val command = listEntry?.entry<String>()?.get()
                if (alias != null && command != null) {
                    output.append(Text.literal("- /$alias : \"/$command\"").withColor(0xFFA500))
                    if (i < list.getSize() - 1) {
                        output.append(Text.literal("\n"))
                    }
                }
            }
            if (list.getSize() == 0L) {
                output.append(Text.literal("No stored aliases found.").withColor(0xFF0000))
            }

            source?.sendFeedback({ output }, false)
            logger.info("Listed ${list.getSize()} stored aliases from Kottage database for ${source?.name}.")
        }

        return 1
    }

    suspend fun registerStoredCommands(dispatcher: CommandDispatcher<ServerCommandSource?>) {
        logger.info("Registering stored command aliases...")

        // Open Kottage database as storage mode
        val storage: KottageStorage = kottage.storage(STORAGE_NAME)
        val list: KottageList = storage.list(LIST_NAME)

        for (i in 0 until list.getSize()) {
            val listEntry = list.getByIndex(i)
            val alias = listEntry?.entry<String>()?.key
            val command = listEntry?.entry<String>()?.get()
            if (alias != null && command != null) {
                dispatcher.register(
                    CommandManager.literal(alias)
                        .executes { ctx: CommandContext<ServerCommandSource?>? ->
                            ctx?.source?.server?.commandManager?.executeWithPrefix(ctx?.source, command)
                            1
                        }
                )
            }
        }

        logger.info("Registered ${list.getSize()} stored aliases from Kottage database.")
    }
}