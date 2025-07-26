package dev.flwn

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import dev.flwn.commands.AliasCommand
import dev.flwn.commands.AliasCommand.registerStoredCommands
import dev.flwn.suggestions.AliasedCommandProvider
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import org.mapdb.DB
import org.mapdb.DBMaker.fileDB
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object AtmospireAliasing : ModInitializer {
    const val MOD_ID = "atmospirealiasing"
    val logger: Logger = LoggerFactory.getLogger(MOD_ID)
    var db: DB? = null
        private set

    init {
        // Create the database directory if it doesn't exist
        val dbDir = FabricLoader.getInstance().configDir.resolve(MOD_ID)
        if (!dbDir.toFile().exists()) dbDir.toFile().mkdirs()

        db = fileDB("${FabricLoader.getInstance().configDir}/$MOD_ID/$MOD_ID.db").transactionEnable().make()
    }

    override fun onInitialize() {
        logger.info("Initializing ${MOD_ID}...")
        val metadata = FabricLoader.getInstance().getModContainer(MOD_ID).get().metadata

        // Register commands
        CommandRegistrationCallback.EVENT.register(CommandRegistrationCallback { dispatcher: CommandDispatcher<ServerCommandSource?>, dedicated: CommandRegistryAccess?, environment: CommandManager.RegistrationEnvironment? ->
            registerStoredCommands(dispatcher)

            val alias = CommandManager.literal("alias").then(
                CommandManager.literal("add").requires { source -> source.hasPermissionLevel(4) }.then(
                    CommandManager.argument("alias", StringArgumentType.string()).then(
                        CommandManager.argument("command", StringArgumentType.string())
                            .executes(AliasCommand::executeAdd)
                    )
                )
            ).then(
                CommandManager.literal("remove").requires { source -> source.hasPermissionLevel(4) }.then(
                    CommandManager.argument("alias", StringArgumentType.string()).suggests(AliasedCommandProvider())
                        .executes(AliasCommand::executeRemove)
                )
            ).then(CommandManager.literal("list").requires { source -> source.hasPermissionLevel(4) }
                .executes(AliasCommand::executeList))

            val version = { ctx: CommandContext<ServerCommandSource?>? ->
                ctx?.source?.sendFeedback({
                    Text.literal("${metadata.name} - Version ${metadata.version}").withColor(0xB39EB5)
                }, false)
                1
            }

            val mod = CommandManager.literal(MOD_ID).executes(version).then(
                CommandManager.literal("version").executes(version)
            )

            dispatcher.getRoot().addChild(alias.build())
            dispatcher.getRoot().addChild(mod.build())

            logger.info("${metadata.name} initialized successfully!")
        })

        ServerLifecycleEvents.SERVER_STOPPING.register {
            // Close the database when the server stops
            if (db?.isClosed() == false) {
                db?.close()
                logger.info("Database closed successfully.")
            } else {
                logger.warn("Database was already closed.")
            }
        }
    }
}