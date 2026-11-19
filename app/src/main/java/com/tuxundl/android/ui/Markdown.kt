package com.tuxundl.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * A deliberately small markdown renderer for release notes (1.6.1).
 *
 * The update page used to print the release body as one plain Text,
 * so every `#`, `**` and `-` of the GitHub-flavoured markdown showed
 * up raw in front of the user. Pulling in a full markdown library
 * for one page is not worth the APK weight; the release notes this
 * project publishes only ever use a small subset, and that subset is
 * what is rendered here:
 *
 * - headings (`#` … `####`)
 * - bold (`**…**`), italic (`*…*`), strikethrough (`~~…~~`)
 * - inline code (backticks) and links (rendered as their text)
 * - bullet lists (`-` / `*`), numbered lists, blockquotes (`>`)
 * - horizontal rules and fenced code blocks
 *
 * Anything unrecognised degrades to plain text — never to visible
 * markup soup. Line-based parsing: each source line is one block,
 * which matches how the project's notes are authored.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (block in blocks) {
            when (block) {
                is MarkdownBlock.Heading -> Text(
                    text = markdownInline(block.text),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                )

                is MarkdownBlock.Paragraph -> Text(
                    text = markdownInline(block.text),
                    style = style,
                    color = color,
                )

                is MarkdownBlock.Bullet -> Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "•",
                        style = style,
                        color = color,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        text = markdownInline(block.text),
                        style = style,
                        color = color,
                        modifier = Modifier.weight(1f),
                    )
                }

                is MarkdownBlock.Numbered -> Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = block.marker,
                        style = style,
                        color = color,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        text = markdownInline(block.text),
                        style = style,
                        color = color,
                        modifier = Modifier.weight(1f),
                    )
                }

                is MarkdownBlock.Quote -> Text(
                    text = markdownInline(block.text),
                    style = style.copy(fontStyle = FontStyle.Italic),
                    color = color,
                    modifier = Modifier.padding(start = 12.dp),
                )

                is MarkdownBlock.Code -> Text(
                    text = block.text,
                    style = style.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                )

                MarkdownBlock.Rule -> HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
    }
}

private sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class Bullet(val text: String) : MarkdownBlock
    data class Numbered(val marker: String, val text: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class Code(val text: String) : MarkdownBlock
    object Rule : MarkdownBlock
}

private val NUMBERED_ITEM = Regex("""^(\d{1,3})[.)]\s+(.*)$""")

private fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val codeLines = mutableListOf<String>()
    var inCode = false

    fun flushCode() {
        if (codeLines.isNotEmpty()) {
            blocks += MarkdownBlock.Code(codeLines.joinToString("\n"))
            codeLines.clear()
        }
    }

    for (rawLine in markdown.lines()) {
        val line = rawLine.trimEnd()

        if (line.trimStart().startsWith("```")) {
            if (inCode) {
                flushCode()
                inCode = false
            } else {
                inCode = true
            }
            continue
        }

        if (inCode) {
            codeLines += rawLine
            continue
        }

        val trimmed = line.trim()

        if (trimmed.isEmpty()) continue

        when {
            trimmed == "---" || trimmed == "***" || trimmed == "___" ->
                blocks += MarkdownBlock.Rule

            trimmed.startsWith("#") -> {
                val level = trimmed.takeWhile { it == '#' }.length
                val text = trimmed.drop(level).trim()
                if (level in 1..4 && text.isNotEmpty()) {
                    blocks += MarkdownBlock.Heading(level, text)
                } else {
                    blocks += MarkdownBlock.Paragraph(trimmed)
                }
            }

            trimmed.startsWith("- ") || trimmed.startsWith("* ") ->
                blocks += MarkdownBlock.Bullet(trimmed.drop(2).trim())

            trimmed.startsWith("> ") ->
                blocks += MarkdownBlock.Quote(trimmed.drop(2).trim())

            else -> {
                val numbered = NUMBERED_ITEM.matchEntire(trimmed)
                if (numbered != null) {
                    blocks += MarkdownBlock.Numbered(
                        marker = numbered.groupValues[1] + ".",
                        text = numbered.groupValues[2].trim(),
                    )
                } else {
                    blocks += MarkdownBlock.Paragraph(trimmed)
                }
            }
        }
    }

    flushCode()
    return blocks
}

/**
 * Inline spans: `**bold**`, `*italic*`, `~~strike~~`, backtick code
 * and `[text](url)` links (rendered as their text). Unbalanced
 * markers are left as literal characters.
 */
private fun markdownInline(text: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0

        fun peekPair(marker: String): Int {
            val end = text.indexOf(marker, i + marker.length)
            return end
        }

        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = peekPair("**")
                    if (end > i + 2) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(markdownInline(text.substring(i + 2, end)))
                        }
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }

                text.startsWith("~~", i) -> {
                    val end = peekPair("~~")
                    if (end > i + 2) {
                        withStyle(
                            SpanStyle(textDecoration = TextDecoration.LineThrough),
                        ) {
                            append(markdownInline(text.substring(i + 2, end)))
                        }
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }

                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i + 1) {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }

                text[i] == '*' && (i + 1 >= text.length || text[i + 1] != '*') -> {
                    val end = text.indexOf('*', i + 1)
                    if (end > i + 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(markdownInline(text.substring(i + 1, end)))
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }

                text[i] == '[' -> {
                    val close = text.indexOf(']', i + 1)
                    val paren = if (close >= 0 && close + 1 < text.length &&
                        text[close + 1] == '('
                    ) {
                        text.indexOf(')', close + 2)
                    } else {
                        -1
                    }
                    if (close > i + 1 && paren > close + 1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                            append(text.substring(i + 1, close))
                        }
                        i = paren + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }

                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}
