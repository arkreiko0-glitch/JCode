package com.example.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.BottomPanelTab
import com.example.data.BrowserRunTarget
import com.example.data.OrientationMode
import com.example.data.SidebarTab
import com.example.ui.components.AiAssistantPanel
import com.example.ui.components.BottomDockPanel
import com.example.ui.components.BuiltInBrowserPane
import com.example.ui.components.CodeEditorPane
import com.example.ui.components.CommandPaletteDialog
import com.example.ui.components.ExplorerPanel
import com.example.ui.components.ExtensionsPanel
import com.example.ui.components.PaletteCommand
import com.example.ui.components.SearchPanel
import com.example.ui.components.SettingsThemesPanel
import com.example.ui.components.SourceControlPanel
import com.example.ui.theme.IdeThemes
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.ZenncodeTheme
import kotlin.math.roundToInt

@Composable
fun ZenncodeApp(viewModel: IdeViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val extensions by viewModel.extensions.collectAsStateWithLifecycle()
    val aiKeys by viewModel.aiKeyProfiles.collectAsStateWithLifecycle()
    val memoryNotes by viewModel.memoryNotes.collectAsStateWithLifecycle()
    val skills by viewModel.skills.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val snapshots by viewModel.snapshots.collectAsStateWithLifecycle()

    val palette = remember(settings.themeId) { IdeThemes.byId(settings.themeId) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var jumpToLineRequest by remember { mutableStateOf<Pair<String, Int>?>(null) }

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val content = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (content != null) {
                var name = "imported.txt"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && cursor.moveToFirst()) {
                        name = cursor.getString(idx)
                    }
                }
                viewModel.importFileContent(name, content)
            }
        }
    }

    LaunchedEffect(uiState.toastMessage) {
        val msg = uiState.toastMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeToast()
        }
    }

    ZenncodeTheme(palette = palette) {
        val idePalette = LocalIdePalette.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(idePalette.activityBarBg)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top VS Code-style Workbench Header
                TopWorkbenchBar(
                    activeFileName = uiState.activeTab?.fileName ?: "Zenncode",
                    browserTarget = settings.browserRunTarget,
                    orientationMode = settings.orientationMode,
                    isBrowserOpen = uiState.isBrowserPaneVisible,
                    isTerminalOpen = uiState.isBottomPanelVisible,
                    onOpenCommandPalette = { viewModel.toggleCommandPalette(true) },
                    onToggleBrowserTarget = {
                        val next = if (settings.browserRunTarget == BrowserRunTarget.BUILTIN_BROWSER) {
                            BrowserRunTarget.GOOGLE_CHROME
                        } else {
                            BrowserRunTarget.BUILTIN_BROWSER
                        }
                        viewModel.setBrowserRunTarget(next)
                    },
                    onRunActive = { viewModel.runActiveFile() },
                    onToggleOrientation = { viewModel.toggleLandscapeMode() },
                    onToggleBrowserPane = { viewModel.toggleBrowserPane() },
                    onToggleTerminal = { viewModel.toggleBottomPanel() }
                )

                // Main Adaptive Workspace Area
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val isWideLayout = maxWidth >= 640.dp

                    if (!isWideLayout && (uiState.isSidebarVisible || uiState.isBrowserPaneVisible)) {
                        BackHandler {
                            if (uiState.isBrowserPaneVisible) viewModel.toggleBrowserPane(false)
                            else if (uiState.isSidebarVisible) viewModel.setSidebarVisible(false)
                        }
                    }

                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left Activity Bar
                        ActivityRail(
                            activeTab = uiState.activeSidebarTab,
                            isSidebarVisible = uiState.isSidebarVisible,
                            errorCount = uiState.errorCount,
                            onSelectTab = { tab -> viewModel.selectSidebarTab(tab) }
                        )

                        // In Wide/Landscape mode, Sidebar is docked side-by-side
                        if (isWideLayout && uiState.isSidebarVisible && uiState.activeSidebarTab != SidebarTab.BROWSER) {
                            Box(
                                modifier = Modifier
                                    .width(265.dp)
                                    .fillMaxHeight()
                                    .border(0.5.dp, idePalette.border)
                            ) {
                                SidebarContentRouter(
                                    activeTab = uiState.activeSidebarTab,
                                    uiState = uiState,
                                    settings = settings,
                                    extensions = extensions,
                                    aiKeys = aiKeys,
                                    memoryNotes = memoryNotes,
                                    skills = skills,
                                    chatMessages = chatMessages,
                                    snapshots = snapshots,
                                    viewModel = viewModel,
                                    onJumpToFileLine = { path, line ->
                                        viewModel.openFile(path)
                                        jumpToLineRequest = path to line
                                    },
                                    onImportClick = { importFileLauncher.launch(arrayOf("*/*")) }
                                )
                            }
                        }

                        // Center Editor + Bottom Terminal/Problems Dock
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                val activeTab = uiState.activeTab
                                val activeDiagnostics = activeTab?.let {
                                    uiState.diagnosticsByFile[it.relativePath].orEmpty()
                                }.orEmpty()

                                CodeEditorPane(
                                    openTabs = uiState.openTabs,
                                    activeTab = activeTab,
                                    diagnostics = activeDiagnostics,
                                    settings = settings,
                                    jumpToLineRequest = jumpToLineRequest,
                                    onJumpConsumed = { jumpToLineRequest = null },
                                    onSelectTab = { viewModel.selectTab(it) },
                                    onCloseTab = { viewModel.closeTab(it) },
                                    onContentChange = { content, line, col ->
                                        viewModel.updateActiveEditorContent(content, line, col)
                                    },
                                    onCursorChange = { line, col ->
                                        viewModel.updateCursorPosition(line, col)
                                    },
                                    onSave = { viewModel.saveActiveFile() },
                                    onUndo = { viewModel.undoActiveEditor() },
                                    onRedo = { viewModel.redoActiveEditor() },
                                    onFormat = { viewModel.formatActiveFile() },
                                    onRun = { viewModel.runActiveFile() },
                                    onZoomIn = { viewModel.zoomInEditor() },
                                    onZoomOut = { viewModel.zoomOutEditor() },
                                    onResetZoom = { viewModel.resetZoomEditor() },
                                    onPinchZoom = { viewModel.setEditorZoomScale(it) },
                                    onOpenProblems = { viewModel.selectBottomTab(BottomPanelTab.PROBLEMS) },
                                    onAskAiFix = { diag -> viewModel.askAiToFixDiagnostic(diag) },
                                    modifier = Modifier.weight(1f)
                                )

                                AnimatedVisibility(visible = uiState.isBottomPanelVisible) {
                                    val dockHeight = if (isWideLayout) 168.dp else 210.dp
                                    BottomDockPanel(
                                        activeTab = uiState.activeBottomTab,
                                        terminalEntries = uiState.terminalEntries,
                                        terminalCwd = uiState.terminalCwd,
                                        terminalHistory = uiState.terminalHistory,
                                        diagnostics = uiState.allDiagnostics,
                                        outputLogs = uiState.outputLogs,
                                        settings = settings,
                                        onSelectTab = { viewModel.selectBottomTab(it) },
                                        onClosePanel = { viewModel.toggleBottomPanel() },
                                        onRunTerminalCommand = { viewModel.executeTerminalCommand(it) },
                                        onClearTerminal = { viewModel.clearTerminal() },
                                        onJumpToDiagnostic = { diag ->
                                            viewModel.openFile(diag.filePath)
                                            jumpToLineRequest = diag.filePath to diag.line
                                        },
                                        onUpdateTerminalFontSize = { viewModel.setTerminalFontSize(it) },
                                        onUpdateTerminalCursor = { viewModel.setTerminalCursorStyle(it) },
                                        onUpdateTerminalPrompt = { viewModel.setTerminalPrompt(it) },
                                        modifier = Modifier.height(dockHeight)
                                    )
                                }
                            }

                            // In Portrait/Compact mode, Sidebar overlays cleanly beside the Activity Rail
                            if (!isWideLayout && uiState.isSidebarVisible && uiState.activeSidebarTab != SidebarTab.BROWSER) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.84f)
                                            .fillMaxHeight()
                                            .border(0.5.dp, idePalette.border)
                                    ) {
                                        SidebarContentRouter(
                                            activeTab = uiState.activeSidebarTab,
                                            uiState = uiState,
                                            settings = settings,
                                            extensions = extensions,
                                            aiKeys = aiKeys,
                                            memoryNotes = memoryNotes,
                                            skills = skills,
                                            chatMessages = chatMessages,
                                            snapshots = snapshots,
                                            viewModel = viewModel,
                                            onJumpToFileLine = { path, line ->
                                                viewModel.openFile(path)
                                                jumpToLineRequest = path to line
                                                viewModel.setSidebarVisible(false)
                                            },
                                            onImportClick = { importFileLauncher.launch(arrayOf("*/*")) }
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .background(idePalette.activityBarBg.copy(alpha = 0.55f))
                                            .clickable { viewModel.setSidebarVisible(false) }
                                    )
                                }
                            }

                            // In Portrait/Compact mode, Built-in Browser opens over center workspace
                            if (!isWideLayout && uiState.isBrowserPaneVisible) {
                                BuiltInBrowserPane(
                                    url = uiState.browserUrl,
                                    reloadNonce = uiState.browserReloadNonce,
                                    runTarget = settings.browserRunTarget,
                                    consoleLogs = uiState.browserConsoleLogs,
                                    onNavigate = { viewModel.navigateBuiltInBrowser(it) },
                                    onReload = { viewModel.reloadBuiltInBrowser() },
                                    onSelectRunTarget = { viewModel.setBrowserRunTarget(it) },
                                    onOpenInChrome = { viewModel.openUrlInChrome(it) },
                                    onConsoleLog = { viewModel.addBrowserConsoleLog(it) },
                                    onClearConsole = { viewModel.clearBrowserConsoleLogs() },
                                    onClose = { viewModel.toggleBrowserPane(false) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        // In Wide/Landscape mode, Built-in Browser sits side-by-side on the right!
                        if (isWideLayout && uiState.isBrowserPaneVisible) {
                            BuiltInBrowserPane(
                                url = uiState.browserUrl,
                                reloadNonce = uiState.browserReloadNonce,
                                runTarget = settings.browserRunTarget,
                                consoleLogs = uiState.browserConsoleLogs,
                                onNavigate = { viewModel.navigateBuiltInBrowser(it) },
                                onReload = { viewModel.reloadBuiltInBrowser() },
                                onSelectRunTarget = { viewModel.setBrowserRunTarget(it) },
                                onOpenInChrome = { viewModel.openUrlInChrome(it) },
                                onConsoleLog = { viewModel.addBrowserConsoleLog(it) },
                                onClearConsole = { viewModel.clearBrowserConsoleLogs() },
                                onClose = { viewModel.toggleBrowserPane(false) },
                                modifier = Modifier
                                    .width(340.dp)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }

                // Bottom VS Code Status Bar
                StatusBar(
                    activeTab = uiState.activeTab,
                    errorCount = uiState.errorCount,
                    warningCount = uiState.warningCount,
                    zoomPct = ((settings.editorFontSizeSp / 13.5f) * 100f).roundToInt(),
                    themeName = palette.name,
                    serverUrl = uiState.serverUrl,
                    onClickProblems = { viewModel.selectBottomTab(BottomPanelTab.PROBLEMS) },
                    onResetZoom = { viewModel.resetZoomEditor() },
                    onOpenThemes = { viewModel.selectSidebarTab(SidebarTab.SETTINGS) }
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
            )
        }

        if (uiState.isCommandPaletteOpen) {
            val commands = remember(settings.themeId) {
                listOf(
                    PaletteCommand("run", "Run Active File", "Run", "▶") { viewModel.runActiveFile() },
                    PaletteCommand("format", "Format Document", "Editor") { viewModel.formatActiveFile() },
                    PaletteCommand("save", "Save Active File", "File") { viewModel.saveActiveFile() },
                    PaletteCommand("term", "Toggle Terminal", "View") { viewModel.toggleBottomPanel() },
                    PaletteCommand("browser", "Toggle Built-in Browser", "View") { viewModel.toggleBrowserPane() },
                    PaletteCommand("chrome", "Open Project in Google Chrome", "Run") {
                        viewModel.openUrlInChrome("${uiState.serverUrl}/index.html")
                    },
                    PaletteCommand("landscape", "Toggle Landscape / Portrait", "View") { viewModel.toggleLandscapeMode() },
                    PaletteCommand("zoom_in", "Zoom In Editor", "View", "+") { viewModel.zoomInEditor() },
                    PaletteCommand("zoom_out", "Zoom Out Editor", "View", "-") { viewModel.zoomOutEditor() },
                    PaletteCommand("ext", "Open Extensions Marketplace", "View") { viewModel.selectSidebarTab(SidebarTab.EXTENSIONS) },
                    PaletteCommand("ai", "Open AI Studio & Keys", "AI") { viewModel.selectSidebarTab(SidebarTab.AI_AGENT) }
                ) + IdeThemes.all.map { theme ->
                    PaletteCommand("theme_${theme.id}", "Switch Theme -> ${theme.name}", "Theme") {
                        viewModel.setThemeId(theme.id)
                    }
                }
            }
            CommandPaletteDialog(
                workspaceFiles = uiState.workspaceNodes,
                commands = commands,
                onOpenFile = { viewModel.openFile(it) },
                onDismiss = { viewModel.toggleCommandPalette(false) }
            )
        }
    }
}

@Composable
private fun TopWorkbenchBar(
    activeFileName: String,
    browserTarget: BrowserRunTarget,
    orientationMode: OrientationMode,
    isBrowserOpen: Boolean,
    isTerminalOpen: Boolean,
    onOpenCommandPalette: () -> Unit,
    onToggleBrowserTarget: () -> Unit,
    onRunActive: () -> Unit,
    onToggleOrientation: () -> Unit,
    onToggleBrowserPane: () -> Unit,
    onToggleTerminal: () -> Unit
) {
    val palette = LocalIdePalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(palette.activityBarBg)
            .border(0.5.dp, palette.border)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(palette.accent.copy(alpha = 0.2f))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Text("Z", color = palette.accent, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        }

        // Command Palette Pill
        Row(
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(palette.surfaceElevated)
                .border(0.5.dp, palette.border, RoundedCornerShape(6.dp))
                .clickable { onOpenCommandPalette() }
                .padding(horizontal = 8.dp)
                .testTag("command_palette_button"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, contentDescription = "Command Palette", tint = palette.textMuted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Zenncode • $activeFileName",
                color = palette.textPrimary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Run Target Toggle (Built-in vs Chrome)
        Surface(
            color = palette.surfaceElevated,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier
                .clickable { onToggleBrowserTarget() }
                .testTag("top_run_target_toggle")
        ) {
            Text(
                text = if (browserTarget == BrowserRunTarget.BUILTIN_BROWSER) "WebView" else "Chrome",
                color = palette.accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
            )
        }

        // Run Button
        Surface(
            color = palette.successColor.copy(alpha = 0.2f),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier
                .clickable { onRunActive() }
                .testTag("top_run_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Run", tint = palette.successColor, modifier = Modifier.size(15.dp))
                Text("Run", color = palette.successColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        IconButton(
            onClick = onToggleOrientation,
            modifier = Modifier.size(28.dp).testTag("orientation_toggle_button")
        ) {
            Icon(
                imageVector = Icons.Default.ScreenRotation,
                contentDescription = "Orientation: ${orientationMode.label}",
                tint = if (orientationMode == OrientationMode.LANDSCAPE) palette.accent else palette.textMuted,
                modifier = Modifier.size(16.dp)
            )
        }

        IconButton(
            onClick = onToggleBrowserPane,
            modifier = Modifier.size(28.dp).testTag("toggle_browser_button")
        ) {
            Icon(
                imageVector = Icons.Default.Public,
                contentDescription = "Toggle Built-in Browser",
                tint = if (isBrowserOpen) palette.accent else palette.textMuted,
                modifier = Modifier.size(16.dp)
            )
        }

        IconButton(
            onClick = onToggleTerminal,
            modifier = Modifier.size(28.dp).testTag("toggle_terminal_button")
        ) {
            Icon(
                imageVector = Icons.Default.Terminal,
                contentDescription = "Toggle Terminal",
                tint = if (isTerminalOpen) palette.accent else palette.textMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ActivityRail(
    activeTab: SidebarTab,
    isSidebarVisible: Boolean,
    errorCount: Int,
    onSelectTab: (SidebarTab) -> Unit
) {
    val palette = LocalIdePalette.current
    val items: List<Pair<SidebarTab, ImageVector>> = listOf(
        SidebarTab.EXPLORER to Icons.Default.FolderCopy,
        SidebarTab.SEARCH to Icons.Default.Search,
        SidebarTab.SOURCE_CONTROL to Icons.Default.AccountTree,
        SidebarTab.EXTENSIONS to Icons.Default.Extension,
        SidebarTab.BROWSER to Icons.Default.Public,
        SidebarTab.AI_AGENT to Icons.Default.AutoAwesome,
        SidebarTab.SETTINGS to Icons.Default.Palette
    )

    Column(
        modifier = Modifier
            .width(44.dp)
            .fillMaxHeight()
            .background(palette.activityBarBg)
            .border(0.5.dp, palette.border)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { (tab, icon) ->
            val selected = isSidebarVisible && activeTab == tab
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) palette.accent.copy(alpha = 0.18f) else palette.activityBarBg)
                    .clickable { onSelectTab(tab) }
                    .testTag("activity_${tab.name.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = tab.label,
                    tint = if (selected) palette.accent else palette.textMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.weight(1f))

        if (errorCount > 0) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(palette.errorColor.copy(alpha = 0.2f))
                    .padding(horizontal = 5.dp, vertical = 3.dp)
            ) {
                Text("$errorCount", color = palette.errorColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SidebarContentRouter(
    activeTab: SidebarTab,
    uiState: IdeUiState,
    settings: com.example.data.IdeSettings,
    extensions: List<com.example.data.ExtensionEntity>,
    aiKeys: List<com.example.data.AiKeyProfileEntity>,
    memoryNotes: List<com.example.data.AiMemoryNoteEntity>,
    skills: List<com.example.data.AiSkillEntity>,
    chatMessages: List<com.example.data.AiChatMessageEntity>,
    snapshots: List<com.example.data.WorkspaceSnapshotEntity>,
    viewModel: IdeViewModel,
    onJumpToFileLine: (String, Int) -> Unit,
    onImportClick: () -> Unit
) {
    when (activeTab) {
        SidebarTab.EXPLORER -> ExplorerPanel(
            nodes = uiState.workspaceNodes,
            activeFilePath = uiState.activeTabPath,
            onOpenFile = { viewModel.openFile(it) },
            onCreateFile = { viewModel.createNewFile(it) },
            onCreateFolder = { viewModel.createNewFolder(it) },
            onRenamePath = { old, newName -> viewModel.renameWorkspacePath(old, newName) },
            onDeletePath = { viewModel.deleteWorkspacePath(it) },
            onImportFileClick = onImportClick
        )
        SidebarTab.SEARCH -> SearchPanel(
            query = uiState.searchQuery,
            hits = uiState.searchHits,
            onQueryChange = { viewModel.searchWorkspace(it) },
            onJumpToHit = { hit -> onJumpToFileLine(hit.relativePath, hit.line) }
        )
        SidebarTab.SOURCE_CONTROL -> SourceControlPanel(
            changes = uiState.gitChanges,
            snapshots = snapshots,
            onCommit = { viewModel.commitSnapshot(it) },
            onOpenFile = { viewModel.openFile(it) }
        )
        SidebarTab.EXTENSIONS -> ExtensionsPanel(
            extensions = extensions,
            downloadingIds = uiState.downloadingExtensionIds,
            onInstall = { viewModel.installExtension(it) },
            onToggleEnable = { viewModel.toggleExtensionEnabled(it) },
            onUninstall = { viewModel.uninstallExtension(it) },
            onCreateSample = { viewModel.createSampleFileFromExtension(it) }
        )
        SidebarTab.AI_AGENT -> AiAssistantPanel(
            chatMessages = chatMessages,
            keyProfiles = aiKeys,
            memoryNotes = memoryNotes,
            skills = skills,
            isSending = uiState.isAiSending,
            statusBanner = uiState.aiStatusBanner,
            testingKeyId = uiState.testingKeyId,
            keyTestResult = uiState.keyTestResult,
            onSendPrompt = { prompt, includeCtx -> viewModel.sendAiPrompt(prompt, includeCtx) },
            onApplyCodeToEditor = { viewModel.applyCodeToActiveEditor(it) },
            onClearChat = { viewModel.clearAiChat() },
            onAddKey = { label, provider, base, model, key ->
                viewModel.addOrUpdateAiKey(0L, label, provider, base, model, key)
            },
            onToggleKeyEnabled = { viewModel.toggleAiKeyEnabled(it) },
            onMoveKeyPriority = { profile, up -> viewModel.moveAiKeyPriority(profile, up) },
            onDeleteKey = { viewModel.deleteAiKey(it) },
            onTestKey = { viewModel.testAiKey(it) },
            onAddMemory = { t, c -> viewModel.addMemoryNote(t, c) },
            onToggleMemoryPin = { viewModel.toggleMemoryPin(it) },
            onDeleteMemory = { viewModel.deleteMemoryNote(it) },
            onAddSkill = { n, d, p -> viewModel.addCustomSkill(n, d, p) },
            onToggleSkill = { viewModel.toggleSkill(it) },
            onDeleteSkill = { viewModel.deleteSkill(it) }
        )
        SidebarTab.SETTINGS -> SettingsThemesPanel(
            settings = settings,
            onSelectTheme = { viewModel.setThemeId(it) },
            onSetEditorFontSize = { viewModel.setEditorFontSize(it) },
            onSetWordWrap = { viewModel.setWordWrap(it) },
            onSetLineNumbers = { viewModel.setShowLineNumbers(it) },
            onSetAutoSave = { viewModel.setAutoSave(it) },
            onSetTabSize = { viewModel.setTabSize(it) },
            onSetBrowserTarget = { viewModel.setBrowserRunTarget(it) },
            onSetOrientation = { viewModel.setOrientationMode(it) }
        )
        SidebarTab.BROWSER -> Unit
    }
}

@Composable
private fun StatusBar(
    activeTab: com.example.data.OpenEditorTab?,
    errorCount: Int,
    warningCount: Int,
    zoomPct: Int,
    themeName: String,
    serverUrl: String,
    onClickProblems: () -> Unit,
    onResetZoom: () -> Unit,
    onOpenThemes: () -> Unit
) {
    val palette = LocalIdePalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(palette.statusBarBg)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("main*", color = palette.statusBarFg, fontSize = 10.sp, fontWeight = FontWeight.Bold)

        Row(
            modifier = Modifier.clickable { onClickProblems() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ErrorOutline, contentDescription = "Errors", tint = palette.statusBarFg, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(2.dp))
            Text("$errorCount", color = palette.statusBarFg, fontSize = 10.sp)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.WarningAmber, contentDescription = "Warnings", tint = palette.statusBarFg, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(2.dp))
            Text("$warningCount", color = palette.statusBarFg, fontSize = 10.sp)
        }

        Spacer(Modifier.weight(1f))

        if (activeTab != null) {
            Text(
                text = "Ln ${activeTab.cursorLine}, Col ${activeTab.cursorColumn}",
                color = palette.statusBarFg,
                fontSize = 10.sp,
                fontFamily = JetBrainsMonoFontFamily
            )
            Text(
                text = activeTab.languageId.uppercase(),
                color = palette.statusBarFg,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Text(
            text = "$zoomPct%",
            color = palette.statusBarFg,
            fontSize = 10.sp,
            modifier = Modifier.clickable { onResetZoom() }
        )

        Text(
            text = themeName,
            color = palette.statusBarFg,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable { onOpenThemes() }
        )
    }
}
