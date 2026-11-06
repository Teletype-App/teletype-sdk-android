package app.teletype.sdk.config

private val ID_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9_-]{15,127}$")
private val SNIPPET_HINT =
    Regex("<\\s*script|teletypeExternalId|widget\\.teletype(?:app)?\\.com", RegexOption.IGNORE_CASE)

/**
 * Pull the channel id out of a dashboard `<script>` snippet, or return a
 * bare id unchanged. Returns `""` when the input looks like markup / prose.
 */
fun extractTeletypeExternalId(input: String): String {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return ""

    val assigned = Regex("""teletypeExternalId\s*=\s*["']([^"']+)["']""").find(trimmed)
    if (assigned != null) {
        return assigned.groupValues[1].trim()
    }
    if (SNIPPET_HINT.containsMatchIn(trimmed) || trimmed.any { it.isWhitespace() }) {
        return ""
    }
    return trimmed
}

fun isTeletypeExternalId(id: String): Boolean =
    ID_PATTERN.matches(id) && id.any { it.isLetter() } && id.any { it.isDigit() }

fun looksLikeEmbedSnippet(input: String): Boolean =
    SNIPPET_HINT.containsMatchIn(input)
