package com.rethinkingstudio.clawlink.ui.screens.chat.components

import com.rethinkingstudio.clawlink.core.models.chat.ChatSlashCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SlashCommandPanelTest {
    @Test
    fun hermesSuggestionsDoNotIncludeOpenClawDefaults() {
        val suggestions = slashCommandSuggestions(
            input = "/",
            remoteCommands = listOf(
                ChatSlashCommand(
                    command = "/retry",
                    title = "retry",
                    detail = "Retry the last Hermes message"
                )
            ),
            includeDefaultActions = false
        )

        assertEquals(listOf("/retry"), suggestions.map { it.command })
        assertFalse(suggestions.any { it.command == "/doctor" })
    }

    @Test
    fun suggestionsAreCappedForLargeLazyCatalogs() {
        val remoteCommands = (1..30).map { index ->
            ChatSlashCommand(
                command = "/cmd$index",
                title = "cmd$index",
                detail = "Command $index"
            )
        }

        val suggestions = slashCommandSuggestions(
            input = "/",
            remoteCommands = remoteCommands,
            includeDefaultActions = false
        )

        assertEquals(16, suggestions.size)
    }

    @Test
    fun suggestionsSupportFuzzyCommandMatching() {
        val suggestions = slashCommandSuggestions(
            input = "/mdl",
            remoteCommands = listOf(
                ChatSlashCommand(command = "/model", title = "model", detail = "Switch model"),
                ChatSlashCommand(command = "/channels list", title = "channels list", detail = "List channels")
            ),
            includeDefaultActions = false
        )

        assertEquals(listOf("/model"), suggestions.map { it.command })
    }
}
