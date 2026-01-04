package com.divonr.pdftomd.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

object MarkdownUtils {

    fun applyBold(value: TextFieldValue): TextFieldValue {
        val text = value.text
        val selection = value.selection
        if (selection.collapsed) return value

        val start = selection.min
        val end = selection.max

        val selectedText = text.substring(start, end)
        val newText = text.replaceRange(start, end, "**$selectedText**")

        // Clear selection, place cursor after the bolded text
        return TextFieldValue(
            text = newText,
            selection = TextRange(start + selectedText.length + 4)
        )
    }

    fun applyItalic(value: TextFieldValue): TextFieldValue {
        val text = value.text
        val selection = value.selection
        if (selection.collapsed) return value

        val start = selection.min
        val end = selection.max

        val selectedText = text.substring(start, end)
        val newText = text.replaceRange(start, end, "*$selectedText*")

        // Clear selection, place cursor after the italicized text
        return TextFieldValue(
            text = newText,
            selection = TextRange(start + selectedText.length + 2)
        )
    }

    fun toggleQuote(value: TextFieldValue): TextFieldValue {
        val text = value.text
        val selection = value.selection

        // Find start of the paragraph containing the start of the selection (min)
        // We use min to ensure consistent behavior regardless of drag direction
        val start = selection.min

        val paragraphStart = text.lastIndexOf('\n', start - 1).let {
            if (it == -1) 0 else it + 1
        }

        // Check if it starts with "> "
        val isQuoted = text.startsWith("> ", startIndex = paragraphStart)

        val newText: String
        val newCursorPos: Int

        if (isQuoted) {
            // Remove "> "
            newText = text.substring(0, paragraphStart) + text.substring(paragraphStart + 2)
            // Collapsed cursor roughly where it was, shifted back
            val shift = if (start >= paragraphStart + 2) 2 else 0
            val desiredPos = (start - shift).coerceAtLeast(paragraphStart)
            newCursorPos = desiredPos.coerceAtMost(newText.length)
        } else {
            // Add "> "
            newText = text.substring(0, paragraphStart) + "> " + text.substring(paragraphStart)
            // Collapsed cursor shifted forward
            newCursorPos = start + 2
        }

        return TextFieldValue(
            text = newText,
            selection = TextRange(newCursorPos)
        )
    }
}
