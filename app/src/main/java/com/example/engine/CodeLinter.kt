package com.example.engine

import com.example.data.CodeDiagnostic
import com.example.data.DiagnosticSeverity
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object CodeLinter {

    private data class BracketPos(val ch: Char, val line: Int, val col: Int)

    fun analyze(filePath: String, content: String, languageId: String): List<CodeDiagnostic> {
        if (content.isBlank()) return emptyList()
        val diagnostics = mutableListOf<CodeDiagnostic>()

        when (languageId) {
            "json" -> lintJson(filePath, content, diagnostics)
            "python" -> {
                lintBracketsAndStrings(filePath, content, languageId, diagnostics)
                lintPython(filePath, content, diagnostics)
            }
            "html" -> lintHtml(filePath, content, diagnostics)
            "css" -> {
                lintBracketsAndStrings(filePath, content, languageId, diagnostics)
                lintCss(filePath, content, diagnostics)
            }
            "javascript", "typescript" -> {
                lintBracketsAndStrings(filePath, content, languageId, diagnostics)
                lintJsTs(filePath, content, languageId, diagnostics)
            }
            "sql" -> lintSql(filePath, content, diagnostics)
            "kotlin", "java", "c", "cpp", "rust", "go" -> {
                lintBracketsAndStrings(filePath, content, languageId, diagnostics)
                lintCompiledHints(filePath, content, languageId, diagnostics)
            }
            else -> Unit
        }

        return diagnostics.sortedWith(compareBy<CodeDiagnostic> { it.line }.thenBy { it.column })
    }

    private fun lintJson(filePath: String, content: String, out: MutableList<CodeDiagnostic>) {
        lintBracketsAndStrings(filePath, content, "json", out)
        val trimmed = content.trim()
        try {
            if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed)
        } catch (e: JSONException) {
            val msg = e.message ?: "Invalid JSON syntax"
            val charMatch = Regex("character (\\d+)").find(msg)
            val charIndex = charMatch?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceAtMost(content.length) ?: 0
            val prefix = content.substring(0, charIndex)
            val line = prefix.count { it == '\n' } + 1
            val col = charIndex - (prefix.lastIndexOf('\n').takeIf { it >= 0 } ?: -1)
            out.add(
                CodeDiagnostic(
                    filePath = filePath,
                    line = line,
                    column = col.coerceAtLeast(1),
                    severity = DiagnosticSeverity.ERROR,
                    message = "JSON Syntax Error: $msg",
                    source = "JSON"
                )
            )
        } catch (_: RuntimeException) {
            // Fallback for plain JVM unit tests where android.jar stubs org.json
        }
    }

    private fun lintBracketsAndStrings(
        filePath: String,
        content: String,
        languageId: String,
        out: MutableList<CodeDiagnostic>
    ) {
        val stack = ArrayDeque<BracketPos>()
        val pairs = mapOf(')' to '(', '}' to '{', ']' to '[')
        val lines = content.lines()
        val commentPrefix = when (languageId) {
            "python", "shell", "yaml" -> "#"
            "sql" -> "--"
            else -> "//"
        }

        var inBlockComment = false
        lines.forEachIndexed { idx, lineText ->
            val lineNum = idx + 1
            var i = 0
            var inSingle = false
            var inDouble = false
            var inBacktick = false

            while (i < lineText.length) {
                val c = lineText[i]
                val next = lineText.getOrNull(i + 1)

                if (inBlockComment) {
                    if (c == '*' && next == '/') {
                        inBlockComment = false
                        i += 2
                        continue
                    }
                    i++
                    continue
                }

                if (!inSingle && !inDouble && !inBacktick) {
                    if (lineText.startsWith(commentPrefix, i)) break
                    if (languageId != "python" && c == '/' && next == '*') {
                        inBlockComment = true
                        i += 2
                        continue
                    }
                }

                if (c == '\\' && (inSingle || inDouble || inBacktick)) {
                    i += 2
                    continue
                }

                if (c == '"' && !inSingle && !inBacktick) {
                    inDouble = !inDouble
                } else if (c == '\'' && !inDouble && !inBacktick) {
                    inSingle = !inSingle
                } else if (c == '`' && !inSingle && !inDouble && languageId in setOf("javascript", "typescript")) {
                    inBacktick = !inBacktick
                } else if (!inSingle && !inDouble && !inBacktick) {
                    if (c in charArrayOf('(', '{', '[')) {
                        stack.addLast(BracketPos(c, lineNum, i + 1))
                    } else if (c in charArrayOf(')', '}', ']')) {
                        val expected = pairs[c]
                        if (stack.isEmpty()) {
                            out.add(
                                CodeDiagnostic(
                                    filePath = filePath,
                                    line = lineNum,
                                    column = i + 1,
                                    severity = DiagnosticSeverity.ERROR,
                                    message = "Unexpected closing '$c' with no matching '$expected'",
                                    source = "ZennLint"
                                )
                            )
                        } else if (stack.last().ch != expected) {
                            if (stack.any { it.ch == expected }) {
                                while (stack.isNotEmpty() && stack.last().ch != expected) {
                                    val unclosed = stack.removeLast()
                                    out.add(
                                        CodeDiagnostic(
                                            filePath = filePath,
                                            line = unclosed.line,
                                            column = unclosed.col,
                                            severity = DiagnosticSeverity.ERROR,
                                            message = "Unclosed '${unclosed.ch}' opened at line ${unclosed.line}",
                                            source = "ZennLint"
                                        )
                                    )
                                }
                                if (stack.isNotEmpty()) stack.removeLast()
                            } else {
                                val top = stack.removeLast()
                                out.add(
                                    CodeDiagnostic(
                                        filePath = filePath,
                                        line = lineNum,
                                        column = i + 1,
                                        severity = DiagnosticSeverity.ERROR,
                                        message = "Mismatched '$c': expected closing for '${top.ch}' opened at line ${top.line}",
                                        source = "ZennLint"
                                    )
                                )
                            }
                        } else {
                            stack.removeLast()
                        }
                    }
                }
                i++
            }

            if (inDouble || inSingle) {
                // Allow python triple-quoted strings cleanly
                val hasTriple = languageId == "python" && (lineText.contains("\"\"\"") || lineText.contains("'''"))
                if (!hasTriple) {
                    out.add(
                        CodeDiagnostic(
                            filePath = filePath,
                            line = lineNum,
                            column = lineText.length.coerceAtLeast(1),
                            severity = DiagnosticSeverity.ERROR,
                            message = "Unterminated string literal on line $lineNum",
                            source = "ZennLint"
                        )
                    )
                }
            }
        }

        while (stack.isNotEmpty()) {
            val unclosed = stack.removeLast()
            out.add(
                CodeDiagnostic(
                    filePath = filePath,
                    line = unclosed.line,
                    column = unclosed.col,
                    severity = DiagnosticSeverity.ERROR,
                    message = "Unclosed '${unclosed.ch}' opened at line ${unclosed.line}",
                    source = "ZennLint"
                )
            )
        }
    }

    private fun lintPython(filePath: String, content: String, out: MutableList<CodeDiagnostic>) {
        val blockHeaders = Regex("^\\s*(def|class|if|elif|else|for|while|try|except|finally|with)\\b.*")
        content.lines().forEachIndexed { idx, line ->
            val lineNum = idx + 1
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachIndexed
            if (line.contains('\t') && line.startsWith(" ")) {
                out.add(
                    CodeDiagnostic(
                        filePath = filePath,
                        line = lineNum,
                        column = 1,
                        severity = DiagnosticSeverity.ERROR,
                        message = "TabError: inconsistent use of tabs and spaces in indentation",
                        source = "PyLint"
                    )
                )
            }
            if (blockHeaders.matches(line) && !trimmed.endsWith(":") && !trimmed.contains("#")) {
                out.add(
                    CodeDiagnostic(
                        filePath = filePath,
                        line = lineNum,
                        column = line.length,
                        severity = DiagnosticSeverity.ERROR,
                        message = "SyntaxError: expected ':' at end of block statement",
                        source = "PyLint"
                    )
                )
            }
            if (Regex("^\\s*print\\s+[\"']").containsMatchIn(line)) {
                out.add(
                    CodeDiagnostic(
                        filePath = filePath,
                        line = lineNum,
                        column = 1,
                        severity = DiagnosticSeverity.ERROR,
                        message = "SyntaxError: Missing parentheses in call to 'print(...)'",
                        source = "PyLint"
                    )
                )
            }
        }
    }

    private fun lintJsTs(filePath: String, content: String, languageId: String, out: MutableList<CodeDiagnostic>) {
        content.lines().forEachIndexed { idx, line ->
            val lineNum = idx + 1
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("//")) return@forEachIndexed
            if (Regex("\\bvar\\s+").containsMatchIn(trimmed)) {
                out.add(
                    CodeDiagnostic(
                        filePath = filePath,
                        line = lineNum,
                        column = line.indexOf("var") + 1,
                        severity = DiagnosticSeverity.WARNING,
                        message = "Prefer 'const' or 'let' instead of 'var'",
                        source = if (languageId == "typescript") "TSLint" else "ESLint"
                    )
                )
            }
            if (Regex("[^=!]==[^=]").containsMatchIn(trimmed)) {
                out.add(
                    CodeDiagnostic(
                        filePath = filePath,
                        line = lineNum,
                        column = line.indexOf("==") + 1,
                        severity = DiagnosticSeverity.WARNING,
                        message = "Use strict equality '===' instead of '=='",
                        source = "ESLint"
                    )
                )
            }
            if (Regex("^const\\s+[A-Za-z_][A-Za-z0-9_]*\\s*;?$").matches(trimmed)) {
                out.add(
                    CodeDiagnostic(
                        filePath = filePath,
                        line = lineNum,
                        column = 1,
                        severity = DiagnosticSeverity.ERROR,
                        message = "SyntaxError: Missing initializer in 'const' declaration",
                        source = "ESLint"
                    )
                )
            }
        }
    }

    private fun lintHtml(filePath: String, content: String, out: MutableList<CodeDiagnostic>) {
        val voidTags = setOf("meta", "link", "br", "hr", "img", "input", "source", "area", "base", "col", "embed", "param", "track", "wbr")
        val tagRegex = Regex("<(/?)([a-zA-Z][a-zA-Z0-9-]*)([^>]*)>")
        val openStack = ArrayDeque<Pair<String, Int>>()

        content.lines().forEachIndexed { idx, line ->
            val lineNum = idx + 1
            for (match in tagRegex.findAll(line)) {
                val isClose = match.groupValues[1] == "/"
                val tag = match.groupValues[2].lowercase()
                val attrs = match.groupValues[3]
                if (tag in voidTags || attrs.trimEnd().endsWith("/")) continue
                if (!isClose) {
                    openStack.addLast(tag to lineNum)
                } else {
                    if (openStack.isEmpty()) {
                        out.add(
                            CodeDiagnostic(
                                filePath = filePath,
                                line = lineNum,
                                column = match.range.first + 1,
                                severity = DiagnosticSeverity.ERROR,
                                message = "Unexpected closing tag </$tag>",
                                source = "HTMLHint"
                            )
                        )
                    } else if (openStack.last().first != tag) {
                        val expected = openStack.last()
                        out.add(
                            CodeDiagnostic(
                                filePath = filePath,
                                line = lineNum,
                                column = match.range.first + 1,
                                severity = DiagnosticSeverity.ERROR,
                                message = "Mismatched closing tag </$tag>; expected </${expected.first}> opened at line ${expected.second}",
                                source = "HTMLHint"
                            )
                        )
                    } else {
                        openStack.removeLast()
                    }
                }
            }
        }

        while (openStack.isNotEmpty()) {
            val (tag, line) = openStack.removeLast()
            out.add(
                CodeDiagnostic(
                    filePath = filePath,
                    line = line,
                    column = 1,
                    severity = DiagnosticSeverity.WARNING,
                    message = "Tag <$tag> opened at line $line may be unclosed",
                    source = "HTMLHint"
                )
            )
        }
    }

    private fun lintCss(filePath: String, content: String, out: MutableList<CodeDiagnostic>) {
        var insideBlock = false
        content.lines().forEachIndexed { idx, line ->
            val lineNum = idx + 1
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("/*") || trimmed.startsWith("*")) return@forEachIndexed
            if (trimmed.contains("{")) insideBlock = true
            if (insideBlock && trimmed.contains(":") && !trimmed.endsWith(";") && !trimmed.endsWith("{") && !trimmed.endsWith("}")) {
                out.add(
                    CodeDiagnostic(
                        filePath = filePath,
                        line = lineNum,
                        column = line.length,
                        severity = DiagnosticSeverity.WARNING,
                        message = "Missing semicolon ';' at end of CSS property declaration",
                        source = "CSSLint"
                    )
                )
            }
            if (trimmed.contains("}")) insideBlock = false
        }
    }

    private fun lintSql(filePath: String, content: String, out: MutableList<CodeDiagnostic>) {
        content.lines().forEachIndexed { idx, line ->
            val lineNum = idx + 1
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("--")) return@forEachIndexed
            val singleQuotes = trimmed.count { it == '\'' }
            if (singleQuotes % 2 != 0) {
                out.add(
                    CodeDiagnostic(
                        filePath = filePath,
                        line = lineNum,
                        column = line.length,
                        severity = DiagnosticSeverity.ERROR,
                        message = "Unterminated SQL string literal",
                        source = "SQLCheck"
                    )
                )
            }
        }
    }

    private fun lintCompiledHints(
        filePath: String,
        content: String,
        languageId: String,
        out: MutableList<CodeDiagnostic>
    ) {
        if (languageId in setOf("java", "c", "cpp", "rust")) {
            content.lines().forEachIndexed { idx, line ->
                val lineNum = idx + 1
                val t = line.trim()
                if (t.isEmpty() || t.startsWith("//") || t.startsWith("#") || t.startsWith("/*") || t.startsWith("*")) return@forEachIndexed
                if (t.startsWith("return ") && !t.endsWith(";")) {
                    out.add(
                        CodeDiagnostic(
                            filePath = filePath,
                            line = lineNum,
                            column = line.length,
                            severity = DiagnosticSeverity.ERROR,
                            message = "Expected ';' after return statement",
                            source = languageId.uppercase()
                        )
                    )
                }
            }
        }
    }
}
