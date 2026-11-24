package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BottomPanelTab
import com.example.data.CodeDiagnostic
import com.example.data.DiagnosticSeverity
import com.example.data.IdeSettings
import com.example.data.TerminalCursorStyle
import com.example.data.TerminalEntry
import com.example.data.TerminalEntryType
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalIdePalette

@Composable
fun BottomDockPanel(
    activeTab: BottomPanelTab,
    terminalEntries: List<TerminalEntry>,
    terminalCwd: String,
    terminalHistory: List<String>,
    diagnostics: List<CodeDiagnostic>,
    outputLogs: List<String>,
    settings: IdeSettings,
    onSelectTab: (BottomPanelTab) -> Unit,
    onClosePanel: () -> Unit,
    onRunTerminalCommand: (String) -> Unit,
    onClearTerminal: () -> Unit,
    onJumpToDiagnostic: (CodeDiagnostic) -> Unit,
    onUpdateTerminalFontSize: (Float) -> Unit,
    onUpdateTerminalCursor: (TerminalCursorStyle) -> Unit,
    onUpdateTerminalPrompt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalIdePalette.current
    var showTerminalCustomize by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.panelBg)
            .border(0.5.dp, palette.border)
    ) {
        // Bottom Dock Header Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .background(palette.tabBarBg)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomPanelTab.entries.forEach { tab ->
                val selected = tab == activeTab
                val errCount = diagnostics.count { it.severity == DiagnosticSeverity.ERROR }
                val label = if (tab == BottomPanelTab.PROBLEMS && diagnostics.isNotEmpty()) {
                    "Problems (${diagnostics.size})"
                } else tab.label

                Row(
                    modifier = Modifier
                        .clickable { onSelectTab(tab) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label.uppercase(),
                        color = if (selected) palette.accent else palette.textMuted,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                    if (tab == BottomPanelTab.PROBLEMS && errCount > 0) {
                        Spacer(Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(palette.errorColor.copy(alpha = 0.2f))
                                .padding(horizontal = 4.dp)
                        ) {
                            Text("$errCount", color = palette.errorColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            if (activeTab == BottomPanelTab.TERMINAL) {
                IconButton(onClick = { showTerminalCustomize = true }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Tune, contentDescription = "Customize Terminal", tint = palette.textMuted, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onClearTerminal, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Terminal", tint = palette.textMuted, modifier = Modifier.size(16.dp))
                }
            }
            IconButton(onClick = onClosePanel, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close Panel", tint = palette.textMuted, modifier = Modifier.size(16.dp))
            }
        }

        when (activeTab) {
            BottomPanelTab.TERMINAL -> TerminalTabView(
                entries = terminalEntries,
                cwd = terminalCwd,
                history = terminalHistory,
                settings = settings,
                onRunCommand = onRunTerminalCommand,
                modifier = Modifier.weight(1f)
            )
            BottomPanelTab.PROBLEMS -> ProblemsTabView(
                diagnostics = diagnostics,
                onJumpToDiagnostic = onJumpToDiagnostic,
                modifier = Modifier.weight(1f)
            )
            BottomPanelTab.OUTPUT -> OutputTabView(
                logs = outputLogs,
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showTerminalCustomize) {
        var promptDraft by remember(settings.terminalPrompt) { mutableStateOf(settings.terminalPrompt) }
        AlertDialog(
            onDismissRequest = { showTerminalCustomize = false },
            containerColor = palette.surfaceElevated,
            titleContentColor = palette.textPrimary,
            textContentColor = palette.textPrimary,
            title = { Text("Customize Terminal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Font Size: ${settings.terminalFontSizeSp.toInt()}sp", fontSize = 13.sp)
                    Slider(
                        value = settings.terminalFontSizeSp,
                        onValueChange = onUpdateTerminalFontSize,
                        valueRange = 9f..22f
                    )
                    Text("Cursor Style", fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TerminalCursorStyle.entries.forEach { style ->
                            val active = style == settings.terminalCursorStyle
                            Surface(
                                color = if (active) palette.accent else palette.panelBg,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.clickable { onUpdateTerminalCursor(style) }
                            ) {
                                Text(
                                    text = style.label,
                                    color = if (active) palette.onAccent else palette.textPrimary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    Text("Shell Prompt Prefix", fontSize = 13.sp)
                    BasicTextField(
                        value = promptDraft,
                        onValueChange = { promptDraft = it },
                        singleLine = true,
                        textStyle = TextStyle(color = palette.textPrimary, fontFamily = JetBrainsMonoFontFamily, fontSize = 13.sp),
                        cursorBrush = SolidColor(palette.accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(palette.terminalBg)
                            .border(0.5.dp, palette.border, RoundedCornerShape(6.dp))
                            .padding(10.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateTerminalPrompt(promptDraft)
                    showTerminalCustomize = false
                }) {
                    Text("Apply", color = palette.accent)
                }
            }
        )
    }
}

@Composable
private fun TerminalTabView(
    entries: List<TerminalEntry>,
    cwd: String,
    history: List<String>,
    settings: IdeSettings,
    onRunCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalIdePalette.current
    var commandText by remember { mutableStateOf("") }
    var historyCursor by remember(history.size) { mutableIntStateOf(history.size) }
    val listState = rememberLazyListState()

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            listState.animateScrollToItem(entries.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.terminalBg)
    ) {
        // Quick macro commands strip
        val quickCmds = listOf("ls", "run main.py", "run app.js", "run queries.sql", "serve", "git status", "help", "clear")
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.panelBg)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(quickCmds) { cmd ->
                Surface(
                    color = palette.surfaceElevated,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.clickable { onRunCommand(cmd) }
                ) {
                    Text(
                        text = cmd,
                        color = palette.accent,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        // Terminal Scrollback Output
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            items(entries, key = { it.id }) { entry ->
                val color = when (entry.type) {
                    TerminalEntryType.COMMAND -> palette.terminalPrompt
                    TerminalEntryType.SUCCESS -> palette.successColor
                    TerminalEntryType.ERROR -> palette.errorColor
                    TerminalEntryType.WARNING -> palette.warningColor
                    TerminalEntryType.INFO -> palette.infoColor
                    TerminalEntryType.OUTPUT -> palette.terminalFg
                }
                Text(
                    text = entry.text,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = settings.terminalFontSizeSp.sp,
                    lineHeight = (settings.terminalFontSizeSp * 1.4f).sp,
                    color = color
                )
            }
        }

        // Fast Input Prompt Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.terminalBg)
                .border(0.5.dp, palette.border)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val cursorGlyph = when (settings.terminalCursorStyle) {
                TerminalCursorStyle.BLOCK -> "█"
                TerminalCursorStyle.UNDERLINE -> "_"
                TerminalCursorStyle.BAR -> "|"
            }
            Icon(Icons.Default.Terminal, contentDescription = null, tint = palette.terminalPrompt, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${settings.terminalPrompt}:~/${cwd}$ ",
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = settings.terminalFontSizeSp.sp,
                color = palette.terminalPrompt,
                fontWeight = FontWeight.Bold
            )
            Box(modifier = Modifier.weight(1f)) {
                if (commandText.isEmpty()) {
                    Text(
                        text = "$cursorGlyph type command...",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = settings.terminalFontSizeSp.sp,
                        color = palette.textMuted.copy(alpha = 0.6f)
                    )
                }
                BasicTextField(
                    value = commandText,
                    onValueChange = { commandText = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = settings.terminalFontSizeSp.sp,
                        color = palette.terminalFg
                    ),
                    cursorBrush = SolidColor(palette.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (commandText.isNotBlank()) {
                                onRunCommand(commandText)
                                commandText = ""
                            }
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("terminal_input")
                )
            }
            if (history.isNotEmpty()) {
                IconButton(
                    onClick = {
                        if (historyCursor > 0) {
                            historyCursor -= 1
                            commandText = history.getOrElse(historyCursor) { "" }
                        }
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Previous Command", tint = palette.textMuted, modifier = Modifier.size(15.dp))
                }
            }
            IconButton(
                onClick = {
                    if (commandText.isNotBlank()) {
                        onRunCommand(commandText)
                        commandText = ""
                    }
                },
                modifier = Modifier
                    .size(30.dp)
                    .testTag("terminal_send_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Execute Command", tint = palette.accent, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ProblemsTabView(
    diagnostics: List<CodeDiagnostic>,
    onJumpToDiagnostic: (CodeDiagnostic) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalIdePalette.current
    if (diagnostics.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No problems detected in workspace.", color = palette.successColor, fontSize = 13.sp)
        }
        return
    }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(diagnostics) { diag ->
            val isErr = diag.severity == DiagnosticSeverity.ERROR
            val color = if (isErr) palette.errorColor else palette.warningColor
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(palette.surfaceElevated)
                    .clickable { onJumpToDiagnostic(diag) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isErr) Icons.Default.Error else Icons.Default.Warning,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(diag.message, color = palette.textPrimary, fontSize = 12.sp)
                    Text(
                        text = "${diag.filePath}  [Ln ${diag.line}, Col ${diag.column}]  (${diag.source})",
                        color = palette.textMuted,
                        fontSize = 11.sp,
                        fontFamily = JetBrainsMonoFontFamily
                    )
                }
            }
        }
    }
}

@Composable
private fun OutputTabView(
    logs: List<String>,
    modifier: Modifier = Modifier
) {
    val palette = LocalIdePalette.current
    if (logs.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No execution output yet. Press Run ▶ to execute code.", color = palette.textMuted, fontSize = 12.sp)
        }
        return
    }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(logs) { line ->
            Text(
                text = line,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 12.sp,
                color = palette.textPrimary
            )
        }
    }
}
