package dev.flwn.suggestions

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import dev.flwn.AtmospireAliasing.kottage
import dev.flwn.commands.AliasCommand.LIST_NAME
import dev.flwn.commands.AliasCommand.STORAGE_NAME
import io.github.irgaly.kottage.KottageList
import io.github.irgaly.kottage.KottageStorage
import kotlinx.coroutines.runBlocking
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import java.util.concurrent.CompletableFuture


class AliasedCommandProvider : SuggestionProvider<ServerCommandSource?> {
    @Throws(CommandSyntaxException::class)
       override fun getSuggestions(
        context: CommandContext<ServerCommandSource?>?,
        builder: SuggestionsBuilder?
    ): CompletableFuture<Suggestions?>? {
        val storage: KottageStorage = kottage.storage(STORAGE_NAME)
        val list: KottageList = storage.list(LIST_NAME)

        runBlocking {
            for (i in 0 until list.getSize()) {
                val listEntry = list.getByIndex(i)
                val alias = listEntry?.entry<String>()?.key
                val command = listEntry?.entry<String>()?.get()
                if (alias != null && command != null) {
                    // Add the alias to the suggestions builder
                    builder?.suggest(alias, Text.literal("Alias for command: /$command"))
                }
            }
        }

        return builder?.buildFuture()
    }
}