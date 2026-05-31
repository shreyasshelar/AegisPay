package com.aegispay.android.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Lightweight Markdown renderer for AI triage responses.
 *
 * Handles the subset produced by the AegisPay AI platform:
 *   # H1 / ## H2 / ### H3   — headings
 *   **text**                 — bold inline
 *   `code`                   — inline monospace
 *   - item / * item          — bullet list items
 *   ---                      — horizontal rule (rendered as blank line)
 *   blank lines              — paragraph breaks
 *
 * Mirrors the ReactMarkdown + prose styling added to the web triage screens.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color   = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        markdown.lines().forEach { raw ->
            val line = raw.trimEnd()
            when {
                // H1
                line.startsWith("# ") -> Text(
                    text       = line.removePrefix("# "),
                    style      = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color      = textColor,
                    modifier   = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
                // H2
                line.startsWith("## ") -> Text(
                    text       = line.removePrefix("## "),
                    style      = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color      = textColor,
                    modifier   = Modifier.padding(top = 6.dp, bottom = 1.dp),
                )
                // H3
                line.startsWith("### ") -> Text(
                    text       = line.removePrefix("### "),
                    style      = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color      = textColor,
                    modifier   = Modifier.padding(top = 4.dp),
                )
                // Bullet list item
                line.startsWith("- ") || line.startsWith("* ") -> Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(start = 4.dp),
                ) {
                    Text("•", color = textColor, style = MaterialTheme.typography.bodySmall)
                    Text(
                        text  = buildInlineAnnotated(line.removePrefix("- ").removePrefix("* "), textColor),
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                    )
                }
                // Horizontal rule or blank line — spacer
                line == "---" || line.isBlank() -> Spacer(Modifier.height(4.dp))
                // Normal paragraph line
                else -> Text(
                    text  = buildInlineAnnotated(line, textColor),
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor,
                )
            }
        }
    }
}

/** Renders **bold** and `inline code` within a single line of text. */
private fun buildInlineAnnotated(line: String, textColor: Color): AnnotatedString =
    buildAnnotatedString {
        var remaining = line
        while (remaining.isNotEmpty()) {
            when {
                // **bold**
                remaining.startsWith("**") -> {
                    val end = remaining.indexOf("**", 2)
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(remaining.substring(2, end))
                        }
                        remaining = remaining.substring(end + 2)
                    } else {
                        append(remaining)
                        remaining = ""
                    }
                }
                // `inline code`
                remaining.startsWith("`") -> {
                    val end = remaining.indexOf("`", 1)
                    if (end != -1) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = Color(0xFFE2E8F0),
                                color      = Color(0xFF3B82F6),
                                fontSize   = 11.sp,
                            )
                        ) {
                            append(remaining.substring(1, end))
                        }
                        remaining = remaining.substring(end + 1)
                    } else {
                        append(remaining)
                        remaining = ""
                    }
                }
                else -> {
                    // Advance one character at a time until the next marker
                    val next = minOf(
                        remaining.indexOf("**").let { if (it == -1) remaining.length else it },
                        remaining.indexOf("`").let  { if (it == -1) remaining.length else it },
                    )
                    append(remaining.substring(0, next))
                    remaining = remaining.substring(next)
                }
            }
        }
    }
