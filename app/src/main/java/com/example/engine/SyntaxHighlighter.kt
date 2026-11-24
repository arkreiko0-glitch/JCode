package com.example.engine

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.IdePalette
import org.json.JSONArray
import org.json.JSONObject

object SyntaxHighlighter {

    private val commonKeywords = setOf(
        "if", "else", "elif", "for", "while", "do", "switch", "case", "break", "continue",
        "return", "function", "def", "class", "interface", "struct", "enum", "import",
        "from", "export", "package", "public", "private", "protected", "const", "let", "var",
        "val", "fun", "async", "await", "try", "catch", "except", "finally", "throw", "raise",
        "new", "this", "self", "super", "true", "false", "True", "False", "null", "None",
        "undefined", "in", "is", "not", "and", "or", "as", "fn", "mut", "pub", "impl", "use",
        "go", "defer", "chan", "select", "void", "int", "float", "double", "boolean", "string",
        "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "CREATE", "TABLE", "UPDATE",
        "SET", "DELETE", "ORDER", "BY", "GROUP", "JOIN", "ON", "PRIMARY", "KEY", "NOT", "NULL",
        "INTEGER", "TEXT", "REAL", "DESC", "ASC", "LIMIT", "select", "from", "where", "insert",
        "into", "values", "create", "table", "update", "set", "delete", "order", "by", "limit"
    )

    private val numberRegex = Regex("\\b\\d+(\\.\\d+)?\\b")
    private val wordRegex = Regex("\\b[A-Za-z_][A-Za-z0-9_]*\\b")
    private val stringRegex = Regex("(\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|`(?:\\\\.|[^`\\\\])*`)")
    private val htmlTagRegex = Regex("</?[A-Za-z][A-Za-z0-9-]*")
    private val cssPropRegex = Regex("([a-z-]+)(?=\\s*:)")

    fun highlight(code: String, languageId: String, palette: IdePalette): AnnotatedString {
        // Keep highlighting super lightweight for very large files (>45k chars)
        if (code.length > 45_000) {
            return AnnotatedString(code)
        }
        return buildAnnotatedString {
            append(code)

            if (languageId == "markdown") {
                var offset = 0
                for (line in code.lines()) {
                    val trimmed = line.trimStart()
                    if (trimmed.startsWith("#")) {
                        addStyle(
                            SpanStyle(color = palette.syntaxKeyword, fontWeight = FontWeight.Bold),
                            offset,
                            offset + line.length
                        )
                    } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                        addStyle(SpanStyle(color = palette.syntaxOperator), offset, offset + 2)
                    }
                    offset += line.length + 1
                }
                return@buildAnnotatedString
            }

            // Keywords & functions
            for (match in wordRegex.findAll(code)) {
                val word = match.value
                val start = match.range.first
                val end = match.range.last + 1
                if (word in commonKeywords) {
                    addStyle(
                        SpanStyle(color = palette.syntaxKeyword, fontWeight = FontWeight.Medium),
                        start,
                        end
                    )
                } else if (end < code.length && code[end] == '(') {
                    addStyle(SpanStyle(color = palette.syntaxFunction), start, end)
                } else if (word.firstOrNull()?.isUpperCase() == true) {
                    addStyle(SpanStyle(color = palette.syntaxType), start, end)
                }
            }

            // HTML / XML tags
            if (languageId == "html") {
                for (match in htmlTagRegex.findAll(code)) {
                    addStyle(
                        SpanStyle(color = palette.syntaxTag, fontWeight = FontWeight.Medium),
                        match.range.first,
                        match.range.last + 1
                    )
                }
            }

            // CSS properties
            if (languageId == "css") {
                for (match in cssPropRegex.findAll(code)) {
                    addStyle(
                        SpanStyle(color = palette.syntaxAttribute),
                        match.range.first,
                        match.range.last + 1
                    )
                }
            }

            // Numbers
            for (match in numberRegex.findAll(code)) {
                addStyle(SpanStyle(color = palette.syntaxNumber), match.range.first, match.range.last + 1)
            }

            // Strings (applied after keywords so strings override keywords inside quotes)
            for (match in stringRegex.findAll(code)) {
                addStyle(SpanStyle(color = palette.syntaxString), match.range.first, match.range.last + 1)
            }

            // Comments (applied last so comments override everything inside them)
            highlightComments(code, languageId, palette)
        }
    }

    private fun AnnotatedString.Builder.highlightComments(
        code: String,
        languageId: String,
        palette: IdePalette
    ) {
        val commentStyle = SpanStyle(color = palette.syntaxComment, fontStyle = FontStyle.Italic)
        val linePrefix = when (languageId) {
            "python", "shell", "yaml" -> "#"
            "sql" -> "--"
            else -> "//"
        }

        var index = 0
        while (index < code.length) {
            if (languageId == "html") {
                val start = code.indexOf("<!--", index)
                if (start == -1) break
                val end = code.indexOf("-->", start + 4).let { if (it == -1) code.length else it + 3 }
                addStyle(commentStyle, start, end)
                index = end
                continue
            }

            // Block comments /* ... */
            if (languageId !in setOf("python", "shell", "yaml")) {
                val blockStart = code.indexOf("/*", index)
                val lineStart = code.indexOf(linePrefix, index)
                if (blockStart != -1 && (lineStart == -1 || blockStart < lineStart)) {
                    val blockEnd = code.indexOf("*/", blockStart + 2).let { if (it == -1) code.length else it + 2 }
                    addStyle(commentStyle, blockStart, blockEnd)
                    index = blockEnd
                    continue
                }
            }

            val start = code.indexOf(linePrefix, index)
            if (start == -1) break
            val lineEnd = code.indexOf('\n', start).let { if (it == -1) code.length else it }
            addStyle(commentStyle, start, lineEnd)
            index = lineEnd + 1
        }
    }

    fun formatCode(code: String, languageId: String, tabSize: Int): String {
        if (languageId == "json") {
            val trimmed = code.trim()
            return runCatching {
                when {
                    trimmed.startsWith("{") -> JSONObject(trimmed).toString(tabSize) + "\n"
                    trimmed.startsWith("[") -> JSONArray(trimmed).toString(tabSize) + "\n"
                    else -> code
                }
            }.getOrDefault(code)
        }

        // Clean trailing spaces and normalize indentation for brace-based languages
        if (languageId in setOf("javascript", "typescript", "css", "kotlin", "java", "c", "cpp", "rust", "go")) {
            val indentUnit = " ".repeat(tabSize)
            var depth = 0
            val out = StringBuilder()
            for (rawLine in code.lines()) {
                val trimmed = rawLine.trim()
                if (trimmed.isEmpty()) {
                    out.append('\n')
                    continue
                }
                if (trimmed.startsWith("}") || trimmed.startsWith("]") || trimmed.startsWith(")")) {
                    depth = (depth - 1).coerceAtLeast(0)
                }
                out.append(indentUnit.repeat(depth)).append(trimmed).append('\n')
                val opens = trimmed.count { it == '{' }
                val closes = trimmed.count { it == '}' }
                val delta = opens - closes
                if (delta > 0) depth += delta
                else if (delta < 0 && !trimmed.startsWith("}")) {
                    depth = (depth + delta).coerceAtLeast(0)
                }
            }
            return out.toString()
        }
        return code.lines().joinToString("\n") { it.trimEnd() } + if (code.endsWith("\n")) "\n" else ""
    }
}
