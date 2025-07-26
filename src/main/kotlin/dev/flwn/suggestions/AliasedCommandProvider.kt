package dev.flwn.suggestions

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import dev.flwn.AtmospireAliasing.db
import dev.flwn.commands.AliasCommand.MAP_NAME
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import org.mapdb.Serializer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentMap


class AliasedCommandProvider : SuggestionProvider<ServerCommandSource?> {
    @Throws(CommandSyntaxException::class)
    override fun getSuggestions(
        context: CommandContext<ServerCommandSource?>?,
        builder: SuggestionsBuilder?
    ): CompletableFuture<Suggestions?>? {
        val map: ConcurrentMap<String?, String?>? =
            db?.hashMap(MAP_NAME, Serializer.STRING, Serializer.STRING)?.createOrOpen()

        for (entry in map?.entries ?: emptySet()) {
            val alias = entry.key
            val command = entry.value
            if (alias != null && command != null) {
                // Add the alias to the suggestions builder
                builder?.suggest(alias, Text.literal("Alias for command: /$command"))
            }
        }

        return builder?.buildFuture()
    }
}