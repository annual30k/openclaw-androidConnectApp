package com.rethinkingstudio.clawlink.ui.screens.chat.components

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal sealed class ToolDisplayContent {
    data class Markdown(val text: String) : ToolDisplayContent()
    data class Code(val language: String?, val code: String) : ToolDisplayContent()
    data class TerminalCommand(val command: String, val workdir: String?) : ToolDisplayContent()
    data class TerminalOutput(val text: String, val isError: Boolean, val workdir: String?) : ToolDisplayContent()
    data class Text(val text: String) : ToolDisplayContent()

    fun previewText(): String = when (this) {
        is Markdown -> text.condensedToolPreview()
        is Code -> {
            if (language.normalizedCodeLanguageOrNull() == "json") {
                jsonPreviewSummary(code)?.let { "JSON: $it" } ?: "JSON"
            } else {
                val preview = code.condensedToolPreview()
                val label = language.displayCodeLanguageLabel()
                when {
                    preview.isBlank() && label != null -> label
                    label != null -> "$label: $preview"
                    preview.isBlank() -> "Code"
                    else -> preview
                }
            }
        }
        is TerminalCommand -> listOf(command.condensedToolPreview().ifBlank { "Shell command" }, workdir).filter { !it.isNullOrBlank() }.joinToString(" - ")
        is TerminalOutput -> {
            val prefix = if (isError) "Shell error" else "Shell output"
            listOf(text.condensedToolPreview().ifBlank { prefix }.let { if (it == prefix) it else "$prefix: $it" }, workdir).filter { !it.isNullOrBlank() }.joinToString(" - ")
        }
        is Text -> jsonPreviewSummary(text)?.let { "JSON: $it" } ?: text.condensedToolPreview().ifBlank { text.trim() }
    }
}

private val toolPreferredKeys = listOf("content", "markdown", "text", "body", "message", "value", "result", "output")
private const val toolPreviewAnalysisPrefixLength = 4_000

internal fun ChatMessage.visibleToolContentBlocks(showInvocationProcess: Boolean): List<RelayChatContentBlock> {
    return if (showInvocationProcess) toolContentBlocks else toolContentBlocks.filter { it.isToolResultBlock }
}

internal fun ChatMessage.associatedToolCallBlock(block: RelayChatContentBlock): RelayChatContentBlock? {
    if (block.isToolCallBlock) return block
    val callId = block.resolvedToolCallId()?.trim()
    if (!callId.isNullOrEmpty()) {
        toolContentBlocks.firstOrNull { it.isToolCallBlock && it.resolvedToolCallId() == callId }?.let { return it }
    }
    return toolContentBlocks.firstOrNull { it.isToolCallBlock }
}

internal fun RelayChatContentBlock.toolDisplayContent(associatedToolCallBlock: RelayChatContentBlock? = null): ToolDisplayContent {
    val documentPath = toolDocumentPath() ?: associatedToolCallBlock?.toolDocumentPath()
    markdownDisplayText(documentPath)?.let { return ToolDisplayContent.Markdown(it) }
    structuredToolSnippet()?.let { return it }
    val toolName = (associatedToolCallBlock?.resolvedName ?: resolvedName).orEmpty().trim().lowercase()
    val workdir = toolWorkingDirectory(associatedToolCallBlock)
    val fallback = displayText() ?: resolvedPayload()?.renderedText(toolPreferredKeys) ?: resolvedArguments()?.renderedText(toolPreferredKeys) ?: ""
    val trimmed = fallback.trim()
    if (trimmed.isEmpty()) return ToolDisplayContent.Text(choose("Completed", "已完成"))
    prettyPrintedJson(trimmed)?.let { return ToolDisplayContent.Code("json", it) }
    if (toolName.isCommandToolName()) {
        if (isToolCallBlock) { val command = toolCommandText(); if (!command.isNullOrBlank()) return ToolDisplayContent.TerminalCommand(command.trim(), workdir) }
        if (isToolResultBlock) { return ToolDisplayContent.TerminalOutput(trimmed, isError == true, workdir) }
    }
    if ((isToolCallBlock || toolName == "shell") && trimmed.looksLikeCommandLine()) return ToolDisplayContent.TerminalCommand(trimmed, workdir)
    if (trimmed.looksLikeCodeSnippet()) return ToolDisplayContent.Code(null, trimmed)
    return ToolDisplayContent.Text(trimmed)
}

internal fun RelayChatContentBlock.toolPreviewText(associatedToolCallBlock: RelayChatContentBlock? = null): String {
    val toolName = (associatedToolCallBlock?.resolvedName ?: resolvedName).orEmpty().trim().lowercase()
    val workdir = toolWorkingDirectory(associatedToolCallBlock)

    if (toolName.isCommandToolName()) {
        if (isToolCallBlock) {
            val command = toolCommandText()?.trim().orEmpty()
            if (command.isNotBlank()) {
                return listOf(command.toolPreviewSource().condensedToolPreview().ifBlank { "Shell command" }, workdir)
                    .filter { !it.isNullOrBlank() }
                    .joinToString(" - ")
            }
        }
        if (isToolResultBlock) {
            val output = fallbackToolPreviewText().orEmpty()
            val prefix = if (isError == true) "Shell error" else "Shell output"
            val preview = output.toolPreviewSource().condensedToolPreview()
            return listOf(if (preview.isBlank()) prefix else "$prefix: $preview", workdir)
                .filter { !it.isNullOrBlank() }
                .joinToString(" - ")
        }
    }

    val fallback = fallbackToolPreviewText().orEmpty()
    val trimmed = fallback.trim()
    if (trimmed.isBlank()) return choose("Completed", "已完成")

    val previewSource = trimmed.toolPreviewSource()
    jsonPreviewSummary(previewSource)?.let { summary ->
        if (summary.isNotBlank()) return "JSON: $summary"
    }

    if ((isToolCallBlock || toolName == "shell") && previewSource.looksLikeCommandLine()) {
        return listOf(previewSource.condensedToolPreview().ifBlank { "Shell command" }, workdir)
            .filter { !it.isNullOrBlank() }
            .joinToString(" - ")
    }

    return previewSource.condensedToolPreview().ifBlank { previewSource.trim() }
}

private fun RelayChatContentBlock.markdownDisplayText(documentPath: String?): String? {
    val candidate = when {
        isToolCallBlock -> resolvedArguments()?.renderedText(listOf("content", "markdown", "text", "body", "message", "value"))
        isToolResultBlock -> resolvedPayload()?.renderedText(listOf("content", "markdown", "text", "body", "message", "value")) ?: displayText()
        else -> displayText()
    }?.normalizeToolDisplayText()?.trim().orEmpty()
    if (candidate.isBlank()) return null
    if (hasExplicitMarkdownHint(documentPath) || candidate.shouldRenderToolMarkdown(resolvedName, documentPath)) return candidate
    return null
}

private fun RelayChatContentBlock.structuredToolSnippet(): ToolDisplayContent? {
    listOf(resolvedArguments(), resolvedPayload()).forEach { value ->
        val code = value?.stringValuesForKeys(listOf("code", "content", "text", "body", "script", "source", "value", "message", "result", "output"))?.trim()
        if (code.isNullOrBlank()) return@forEach
        val language = value.stringValuesForKeys(listOf("language", "lang", "syntax", "codeLanguage", "languageId", "format"))?.normalizedCodeLanguage()
        when {
            language in listOf("markdown", "md", "mdx") -> return ToolDisplayContent.Markdown(code)
            language in listOf("shell", "bash", "sh", "zsh", "terminal") || code.looksLikeCommandLine() -> return ToolDisplayContent.TerminalCommand(code, toolWorkingDirectory(null))
            language != null || code.looksLikeCodeSnippet() -> return ToolDisplayContent.Code(language, code)
        }
    }
    return null
}

private fun RelayChatContentBlock.displayText(): String? {
    return listOfNotNull(text, preview, result?.renderedText(toolPreferredKeys), partialResult?.renderedText(toolPreferredKeys), content?.renderedText(toolPreferredKeys), output?.renderedText(toolPreferredKeys), error?.renderedText(toolPreferredKeys), status).firstOrNull { it.isNotBlank() }?.trim()
}

private fun RelayChatContentBlock.fallbackToolPreviewText(): String? {
    return displayText() ?: resolvedPayload()?.renderedText(toolPreferredKeys) ?: resolvedArguments()?.renderedText(toolPreferredKeys)
}

private fun RelayChatContentBlock.resolvedArguments() = arguments ?: args
private fun RelayChatContentBlock.resolvedPayload() = result ?: partialResult ?: content ?: output ?: error
private fun RelayChatContentBlock.resolvedToolCallId() = toolCallId ?: toolUseId

internal fun RelayChatContentBlock.toolDocumentPath(): String? {
    val keys = listOf("path", "filePath", "file_path", "targetPath", "target_path")
    return resolvedArguments()?.stringValuesForKeys(keys) ?: resolvedPayload()?.stringValuesForKeys(keys)
}

private fun RelayChatContentBlock.toolCommandText(): String? {
    val keys = listOf("command", "cmd", "script", "code", "input", "text", "value")
    return resolvedArguments()?.stringValuesForKeys(keys) ?: args?.stringValuesForKeys(keys) ?: text
}

private fun RelayChatContentBlock.toolWorkingDirectory(associatedToolCallBlock: RelayChatContentBlock?): String? {
    val keys = listOf("workdir", "cwd", "workingDirectory", "working_directory")
    return resolvedArguments()?.stringValuesForKeys(keys) ?: resolvedPayload()?.stringValuesForKeys(keys)
        ?: associatedToolCallBlock?.resolvedArguments()?.stringValuesForKeys(keys) ?: associatedToolCallBlock?.resolvedPayload()?.stringValuesForKeys(keys)
}

private fun RelayChatContentBlock.hasExplicitMarkdownHint(documentPath: String?): Boolean {
    if (documentPath?.isMarkdownDocumentPath() == true) return true
    val hints = listOf(
        resolvedArguments()?.stringValuesForKeys(listOf("format", "contentFormat", "mimeType", "mediaType", "type", "language", "lang")),
        resolvedPayload()?.stringValuesForKeys(listOf("format", "contentFormat", "mimeType", "mediaType", "type", "language", "lang"))
    )
    return hints.any { it?.trim()?.lowercase() in listOf("markdown", "md", "mdx", "text/markdown") }
}

private fun String.shouldRenderToolMarkdown(toolName: String?, documentPath: String?): Boolean {
    if (documentPath?.isMarkdownDocumentPath() == true) return true
    val normalized = normalizeToolDisplayText().trim()
    if (normalized.isBlank()) return false
    if (normalized.looksLikeMarkdownStructure()) return true
    val normalizedToolName = toolName?.trim()?.lowercase().orEmpty()
    if (normalizedToolName in listOf("read", "write", "append", "prepend", "insert", "edit", "multiedit", "replace")) return normalized.looksLikeMarkdownDocument()
    return normalized.looksLikeMarkdownDocument()
}

private fun String.looksLikeMarkdownStructure(): Boolean {
    val patterns = listOf(Regex("""(?m)^#{1,6}\s+\S"""), Regex("""(?m)^[-*+]\s+\S"""), Regex("""(?m)^\d+\.\s+\S"""), Regex("""(?m)^>\s+\S"""), Regex("""(?m)^```"""), Regex("""\[[^\]]+]\([^)]+\)"""), Regex("""!\[[^]]*]\([^)]+\)"""), Regex("""(?m)^\|.+\|\s*$"""))
    return patterns.any { it.containsMatchIn(this) } || contains("**") || contains("__") || contains("`")
}

private fun String.looksLikeMarkdownDocument(): Boolean {
    val lines = trim().lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.isEmpty()) return false
    var score = 0
    lines.take(24).forEach { line ->
        when {
            Regex("""^#{1,6}\s+\S""").containsMatchIn(line) -> score += 2
            Regex("""^[-*+]\s+\S""").containsMatchIn(line) -> score += 1
            Regex("""^\d+\.\s+\S""").containsMatchIn(line) -> score += 1
            Regex("""^>\s+\S""").containsMatchIn(line) -> score += 1
            line.startsWith("|") && line.endsWith("|") && line.count { it == '|' } >= 2 -> score += 2
            Regex("""\[[^\]]+]\([^)]+\)""").containsMatchIn(line) -> score += 1
            line.contains("**") || line.contains("__") -> score += 1
        }
    }
    if (lines.first() == "---" || lines.first() == "+++") score += 1
    return score > 0
}

private fun String.looksLikeCommandLine(): Boolean {
    val normalized = trim()
    if (normalized.startsWith("$ ") || normalized.startsWith("> ")) return true
    val prefixes = listOf("git ", "npm ", "pnpm ", "yarn ", "bun ", "npx ", "node ", "python ", "python3 ", "pip ", "uv ", "curl ", "wget ", "brew ", "docker ", "kubectl ", "ssh ", "scp ", "cd ", "ls ", "pwd ", "mkdir ", "rm ", "cp ", "mv ", "cat ", "sed ", "awk ", "xcodebuild ", "swift ", "bash ", "sh ", "zsh ")
    return prefixes.any { normalized.startsWith(it) }
}

private fun String.looksLikeCodeSnippet(): Boolean {
    val trimmed = trim()
    if (trimmed.isBlank() || trimmed.contains("```")) return false
    val codeSignals = listOf("fun ", "class ", "struct ", "import ", "const ", "let ", "var ", "=>", "</", "{", "}", ";")
    val lines = trimmed.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val signalCount = lines.take(20).sumOf { line -> codeSignals.count { line.contains(it) } }
    return signalCount >= 2 || (lines.size >= 3 && signalCount >= 1)
}

private fun String.isMarkdownDocumentPath(): Boolean {
    val lower = trim().lowercase()
    return lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".mdx")
}

private fun String.isCommandToolName(): Boolean {
    return this in listOf("shell", "bash", "terminal", "exec", "run", "command", "python", "node")
}

internal fun String.normalizedCodeLanguage(): String? {
    val lower = trim().lowercase()
    return when (lower) {
        "js", "jsx", "mjs", "cjs" -> "javascript"
        "ts", "tsx" -> "typescript"
        "py" -> "python"
        "rb" -> "ruby"
        "sh", "shell", "zsh", "fish", "shellscript" -> "bash"
        "yml" -> "yaml"
        "md", "mdx", "text/markdown" -> "markdown"
        "c#" -> "csharp"
        "c++" -> "cpp"
        "objc", "objectivec", "objective-c" -> "objectivec"
        "ps1", "pwsh" -> "powershell"
        "bat", "cmd" -> "batch"
        "" -> null
        else -> lower
    }
}

internal fun String?.normalizedCodeLanguageOrNull(): String? = this?.normalizedCodeLanguage()

internal fun String?.displayCodeLanguageLabel(): String? {
    return when (val normalized = normalizedCodeLanguageOrNull()) {
        null -> null
        "javascript" -> "JavaScript"
        "typescript" -> "TypeScript"
        "python" -> "Python"
        "ruby" -> "Ruby"
        "bash" -> "Bash"
        "yaml" -> "YAML"
        "markdown" -> "Markdown"
        "csharp" -> "C#"
        "cpp" -> "C++"
        "powershell" -> "PowerShell"
        "batch" -> "Batch"
        "json" -> "JSON"
        "xml" -> "XML"
        "html" -> "HTML"
        "css" -> "CSS"
        "sql" -> "SQL"
        "swift" -> "Swift"
        "objectivec" -> "Objective-C"
        "java" -> "Java"
        "kotlin" -> "Kotlin"
        "go" -> "Go"
        "rust" -> "Rust"
        else -> normalized.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}

private fun String.normalizeToolDisplayText(): String {
    return decodeEscapedMarkdownText().replace("\r\n", "\n").replace("\r", "\n").trim()
}

internal fun String.decodeEscapedMarkdownText(): String {
    return replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"")
}

internal fun String.condensedToolPreview(): String {
    val firstLine = lines().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return ""
    val cleaned = firstLine.replace(Regex("""^\s{0,3}(#{1,6}\s+|[-*+]\s+|\d+\.\s+|>\s+)"""), "").trim().ifEmpty { firstLine }
    return if (cleaned.length > 80) cleaned.take(80) + "..." else cleaned
}

private fun String.toolPreviewSource(): String =
    if (length > toolPreviewAnalysisPrefixLength) take(toolPreviewAnalysisPrefixLength) else this

private val prettyJson = Json { prettyPrint = true }

private fun prettyPrintedJson(text: String): String? {
    return runCatching {
        val element = Json.parseToJsonElement(text)
        prettyJson.encodeToString(JsonElement.serializer(), element)
    }.getOrNull()
}

private fun jsonPreviewSummary(text: String): String? {
    val element = runCatching { Json.parseToJsonElement(text) }.getOrNull() ?: return null
    return when (element) {
        is kotlinx.serialization.json.JsonObject -> element.entries.take(3).joinToString(", ") { (key, value) -> "$key=${value.jsonPreviewValue()}" }
        is kotlinx.serialization.json.JsonArray -> "${element.size} items"
        else -> element.jsonPreviewValue()
    }.takeIf { it.isNotBlank() }
}

private fun JsonElement.jsonPreviewValue(): String {
    return when (this) {
        is JsonPrimitive -> contentOrNull ?: toString()
        is kotlinx.serialization.json.JsonObject -> "{...}"
        is kotlinx.serialization.json.JsonArray -> "[${size}]"
        else -> toString()
    }
}
