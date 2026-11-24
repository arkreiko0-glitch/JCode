package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CodeDiagnostic
import com.example.data.DiagnosticSeverity
import com.example.data.IdeSettings
import com.example.data.OpenEditorTab
import com.example.engine.SyntaxHighlighter
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalIdePalette
import kotlin.math.roundToInt

@Composable
fun CodeEditorPane(
    openTabs: List<OpenEditorTab>,
    activeTab: OpenEditorTab?,
    diagnostics: List<CodeDiagnostic>,
    settings: IdeSettings,
    jumpToLineRequest: Pair<String, Int>?,
    onJumpConsumed: () -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onContentChange: (String, Int, Int) -> Unit,
    onCursorChange: (Int, Int) -> Unit,
    onSave: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onFormat: () -> Unit,
    onRun: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetZoom: () -> Unit,
    onPinchZoom: (Float) -> Unit,
    onOpenProblems: () -> Unit,
    onAskAiFix: (CodeDiagnostic) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalIdePalette.current
    var showFindBar by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var selectedDiagnostic by remember(activeTab?.relativePath) { mutableStateOf<CodeDiagnostic?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.editorBg)
    ) {
        // Editor Tabs Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(palette.tabBarBg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(openTabs, key = { it.relativePath }) { tab ->
                    val isActive = tab.relativePath == activeTab?.relativePath
                    Row(
                        modifier = Modifier
                            .height(38.dp)
                            .background(if (isActive) palette.activeTabBg else palette.tabBarBg)
                            .clickable { onSelectTab(tab.relativePath) }
                            .border(
                                width = 0.5.dp,
                                color = palette.border
                            )
                            .padding(horizontal = 10.dp)
                            .testTag("editor_tab_${tab.fileName}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isActive) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(palette.accent)
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = tab.fileName,
                            color = if (isActive) palette.textPrimary else palette.textMuted,
                            fontSize = 12.sp,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (tab.isDirty) {
                            Spacer(Modifier.width(5.dp))
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(palette.warningColor)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close ${tab.fileName}",
                            tint = palette.textMuted,
                            modifier = Modifier
                                .size(15.dp)
                                .clickable { onCloseTab(tab.relativePath) }
                        )
                    }
                }
            }

            // Quick Action Controls in Tab Strip
            IconButton(
                onClick = onRun,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("editor_run_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Run Active File",
                    tint = palette.successColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (activeTab == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = palette.accent.copy(alpha = 0.5f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Zenncode Studio",
                        color = palette.textPrimary,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Open a file from Explorer or create a new file",
                        color = palette.textMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            return
        }

        // Toolbar + Breadcrumb + Zoom + Diagnostics Badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.editorBg)
                .border(0.5.dp, palette.border)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = activeTab.relativePath,
                color = palette.textMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            val errors = diagnostics.count { it.severity == DiagnosticSeverity.ERROR }
            val warnings = diagnostics.count { it.severity == DiagnosticSeverity.WARNING }
            if (errors > 0 || warnings > 0) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (errors > 0) palette.errorColor.copy(alpha = 0.16f)
                            else palette.warningColor.copy(alpha = 0.16f)
                        )
                        .clickable {
                            selectedDiagnostic = diagnostics.firstOrNull()
                            onOpenProblems()
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (errors > 0) {
                        Icon(Icons.Default.Error, contentDescription = "Errors", tint = palette.errorColor, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("$errors", color = palette.errorColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    if (warnings > 0) {
                        if (errors > 0) Spacer(Modifier.width(5.dp))
                        Icon(Icons.Default.Warning, contentDescription = "Warnings", tint = palette.warningColor, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("$warnings", color = palette.warningColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(6.dp))
            }

            // Zoom Out / Reset / Zoom In
            Icon(
                imageVector = Icons.Default.ZoomOut,
                contentDescription = "Zoom Out",
                tint = palette.textMuted,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onZoomOut() }
                    .padding(3.dp)
                    .testTag("zoom_out_button")
            )
            val zoomPct = ((settings.editorFontSizeSp / 13.5f) * 100f).roundToInt()
            Text(
                text = "$zoomPct%",
                color = palette.textPrimary,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onResetZoom() }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
            Icon(
                imageVector = Icons.Default.ZoomIn,
                contentDescription = "Zoom In",
                tint = palette.textMuted,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onZoomIn() }
                    .padding(3.dp)
                    .testTag("zoom_in_button")
            )

            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Undo,
                contentDescription = "Undo",
                tint = if (activeTab.undoStack.isNotEmpty()) palette.textPrimary else palette.textMuted.copy(alpha = 0.4f),
                modifier = Modifier
                    .size(24.dp)
                    .clickable(enabled = activeTab.undoStack.isNotEmpty()) { onUndo() }
                    .padding(3.dp)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Redo,
                contentDescription = "Redo",
                tint = if (activeTab.redoStack.isNotEmpty()) palette.textPrimary else palette.textMuted.copy(alpha = 0.4f),
                modifier = Modifier
                    .size(24.dp)
                    .clickable(enabled = activeTab.redoStack.isNotEmpty()) { onRedo() }
                    .padding(3.dp)
            )
            Icon(
                imageVector = Icons.Default.FindReplace,
                contentDescription = "Find and Replace",
                tint = if (showFindBar) palette.accent else palette.textMuted,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { showFindBar = !showFindBar }
                    .padding(3.dp)
            )
            Icon(
                imageVector = Icons.Default.AutoFixHigh,
                contentDescription = "Format Code",
                tint = palette.textMuted,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onFormat() }
                    .padding(3.dp)
            )
            Icon(
                imageVector = Icons.Default.Save,
                contentDescription = "Save File",
                tint = if (activeTab.isDirty) palette.accent else palette.textMuted,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onSave() }
                    .padding(3.dp)
            )
        }

        // Inline Find & Replace Bar
        AnimatedVisibility(visible = showFindBar) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.surfaceElevated)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SmallCodeInput(
                    value = findQuery,
                    onValueChange = { findQuery = it },
                    placeholder = "Find...",
                    modifier = Modifier.weight(1f)
                )
                SmallCodeInput(
                    value = replaceQuery,
                    onValueChange = { replaceQuery = it },
                    placeholder = "Replace...",
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = palette.accent,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.clickable {
                        if (findQuery.isNotEmpty()) {
                            val replaced = activeTab.content.replace(findQuery, replaceQuery)
                            onContentChange(replaced, activeTab.cursorLine, activeTab.cursorColumn)
                        }
                    }
                ) {
                    Text(
                        text = "Replace All",
                        color = palette.onAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Inline Error Inspector Card when user taps an error line or when current line has an error
        val lineDiagnostic = selectedDiagnostic
            ?: diagnostics.firstOrNull { it.line == activeTab.cursorLine }
        AnimatedVisibility(visible = lineDiagnostic != null) {
            if (lineDiagnostic != null) {
                val isErr = lineDiagnostic.severity == DiagnosticSeverity.ERROR
                val badgeColor = if (isErr) palette.errorColor else palette.warningColor
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(badgeColor.copy(alpha = 0.14f))
                        .border(0.5.dp, badgeColor.copy(alpha = 0.45f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isErr) Icons.Default.Error else Icons.Default.Warning,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Line ${lineDiagnostic.line}, Col ${lineDiagnostic.column} • ${lineDiagnostic.source}",
                            color = badgeColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = lineDiagnostic.message,
                            color = palette.textPrimary,
                            fontSize = 12.sp
                        )
                    }
                    Surface(
                        color = palette.accent,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable { onAskAiFix(lineDiagnostic) }
                    ) {
                        Text(
                            text = "AI Fix",
                            color = palette.onAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss diagnostic",
                        tint = palette.textMuted,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { selectedDiagnostic = null }
                    )
                }
            }
        }

        // Main Code Editor Body with Line Gutter & Syntax Highlighting
        var textFieldValue by remember(activeTab.relativePath) {
            mutableStateOf(TextFieldValue(activeTab.content))
        }
        LaunchedEffect(activeTab.content) {
            if (textFieldValue.text != activeTab.content) {
                val sel = textFieldValue.selection
                val safeStart = sel.start.coerceIn(0, activeTab.content.length)
                val safeEnd = sel.end.coerceIn(0, activeTab.content.length)
                textFieldValue = TextFieldValue(activeTab.content, TextRange(safeStart, safeEnd))
            }
        }

        val verticalScroll = rememberScrollState()
        val horizontalScroll = rememberScrollState()

        LaunchedEffect(jumpToLineRequest) {
            if (jumpToLineRequest != null && jumpToLineRequest.first == activeTab.relativePath) {
                val targetLine = jumpToLineRequest.second.coerceAtLeast(1)
                val lines = textFieldValue.text.lines()
                var charOffset = 0
                for (i in 0 until (targetLine - 1).coerceAtMost(lines.size)) {
                    charOffset += lines[i].length + 1
                }
                val safeOffset = charOffset.coerceIn(0, textFieldValue.text.length)
                textFieldValue = textFieldValue.copy(selection = TextRange(safeOffset))
                onCursorChange(targetLine, 1)
                val approxPx = ((targetLine - 1) * settings.editorFontSizeSp * 2.2f).toInt()
                verticalScroll.animateScrollTo(approxPx.coerceAtLeast(0))
                onJumpConsumed()
            }
        }

        val syntaxTransformation = remember(activeTab.languageId, palette) {
            VisualTransformation { text ->
                val highlighted = SyntaxHighlighter.highlight(text.text, activeTab.languageId, palette)
                TransformedText(highlighted, OffsetMapping.Identity)
            }
        }

        val lineHeightSp = (settings.editorFontSizeSp * 1.48f).sp
        val lines = remember(textFieldValue.text) { textFieldValue.text.split('\n') }
        val diagByLine = remember(diagnostics) { diagnostics.groupBy { it.line } }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1f && (zoom > 1.03f || zoom < 0.97f)) {
                            onPinchZoom(zoom)
                        }
                    }
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScroll)
                    .padding(vertical = 8.dp)
            ) {
                // Gutter: Line numbers + Error indicators
                if (settings.showLineNumbers) {
                    Column(
                        modifier = Modifier
                            .background(palette.editorGutterBg)
                            .padding(start = 6.dp, end = 8.dp)
                            .widthIn(min = 38.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        lines.forEachIndexed { idx, _ ->
                            val lineNum = idx + 1
                            val lineDiags = diagByLine[lineNum]
                            val hasErr = lineDiags?.any { it.severity == DiagnosticSeverity.ERROR } == true
                            val hasWarn = lineDiags?.any { it.severity == DiagnosticSeverity.WARNING } == true
                            val isCurLine = lineNum == activeTab.cursorLine

                            Row(
                                modifier = Modifier
                                    .height((settings.editorFontSizeSp * 1.48f).dp)
                                    .clickable(enabled = lineDiags != null) {
                                        selectedDiagnostic = lineDiags?.firstOrNull()
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (hasErr || hasWarn) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(if (hasErr) palette.errorColor else palette.warningColor)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(
                                    text = "$lineNum",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = (settings.editorFontSizeSp * 0.85f).sp,
                                    lineHeight = lineHeightSp,
                                    color = when {
                                        hasErr -> palette.errorColor
                                        hasWarn -> palette.warningColor
                                        isCurLine -> palette.accent
                                        else -> palette.textMuted.copy(alpha = 0.65f)
                                    },
                                    fontWeight = if (isCurLine || hasErr) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // Editable Code Surface
                val editorModifier = if (settings.wordWrap) {
                    Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                } else {
                    Modifier
                        .weight(1f)
                        .horizontalScroll(horizontalScroll)
                        .padding(end = 28.dp)
                }

                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { updated ->
                        textFieldValue = updated
                        val cursor = updated.selection.start.coerceIn(0, updated.text.length)
                        val before = updated.text.substring(0, cursor)
                        val line = before.count { it == '\n' } + 1
                        val col = cursor - (before.lastIndexOf('\n').takeIf { it >= 0 } ?: -1)
                        onContentChange(updated.text, line, col.coerceAtLeast(1))
                    },
                    textStyle = TextStyle(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = settings.editorFontSizeSp.sp,
                        lineHeight = lineHeightSp,
                        color = palette.textPrimary
                    ),
                    cursorBrush = SolidColor(palette.accent),
                    visualTransformation = syntaxTransformation,
                    modifier = editorModifier
                        .fillMaxHeight()
                        .testTag("code_editor_input")
                )
            }
        }

        // Mobile Coding Symbol Accessory Bar
        val symbols = remember(settings.tabSize) {
            listOf("  ".repeat(settings.tabSize / 2).ifEmpty { "  " } to "TAB",
                "{" to "{", "}" to "}", "(" to "(", ")" to ")", "[" to "[", "]" to "]",
                ";" to ";", "=" to "=", "=>" to "=>", "\"" to "\"", "'" to "'",
                "<" to "<", ">" to ">", "/" to "/", "$" to "$", ":" to ":"
            )
        }
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.tabBarBg)
                .border(0.5.dp, palette.border)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(symbols) { (insertText, label) ->
                Surface(
                    color = palette.surfaceElevated,
                    shape = RoundedCornerShape(5.dp),
                    modifier = Modifier
                        .height(28.dp)
                        .clickable {
                            val cur = textFieldValue
                            val start = cur.selection.start.coerceIn(0, cur.text.length)
                            val end = cur.selection.end.coerceIn(0, cur.text.length)
                            val newText = cur.text.replaceRange(minOf(start, end), maxOf(start, end), insertText)
                            val newCursor = minOf(start, end) + insertText.length
                            textFieldValue = TextFieldValue(newText, TextRange(newCursor))
                            onContentChange(newText, activeTab.cursorLine, activeTab.cursorColumn + insertText.length)
                        }
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.textPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallCodeInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalIdePalette.current
    Box(
        modifier = modifier
            .height(30.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(palette.editorBg)
            .border(0.5.dp, palette.border, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = palette.textMuted, fontSize = 11.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = palette.textPrimary,
                fontSize = 12.sp,
                fontFamily = JetBrainsMonoFontFamily
            ),
            cursorBrush = SolidColor(palette.accent),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
