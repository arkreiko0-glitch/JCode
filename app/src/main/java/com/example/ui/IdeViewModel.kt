package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiMultiProviderClient
import com.example.data.AiChatMessageEntity
import com.example.data.AiKeyProfileEntity
import com.example.data.AiMemoryNoteEntity
import com.example.data.AiProviderType
import com.example.data.AiSkillEntity
import com.example.data.BottomPanelTab
import com.example.data.BrowserRunTarget
import com.example.data.CodeDiagnostic
import com.example.data.DiagnosticSeverity
import com.example.data.ExtensionEntity
import com.example.data.GitFileChange
import com.example.data.IdeDatabase
import com.example.data.IdePreferences
import com.example.data.IdeRepository
import com.example.data.IdeSettings
import com.example.data.OpenEditorTab
import com.example.data.OrientationMode
import com.example.data.SearchHit
import com.example.data.SidebarTab
import com.example.data.TerminalCursorStyle
import com.example.data.TerminalEntry
import com.example.data.TerminalEntryType
import com.example.data.WorkspaceFileManager
import com.example.data.WorkspaceNode
import com.example.data.WorkspaceSnapshotEntity
import com.example.engine.CodeExecutionEngine
import com.example.engine.CodeLinter
import com.example.engine.LocalWebServer
import com.example.engine.SyntaxHighlighter
import com.example.ui.theme.IdeThemes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class IdeUiState(
    val workspaceNodes: List<WorkspaceNode> = emptyList(),
    val openTabs: List<OpenEditorTab> = emptyList(),
    val activeTabPath: String? = null,
    val activeSidebarTab: SidebarTab = SidebarTab.EXPLORER,
    val isSidebarVisible: Boolean = true,
    val activeBottomTab: BottomPanelTab = BottomPanelTab.TERMINAL,
    val isBottomPanelVisible: Boolean = true,
    val isBrowserPaneVisible: Boolean = false,
    val browserUrl: String = "",
    val browserConsoleLogs: List<String> = emptyList(),
    val browserReloadNonce: Int = 0,
    val diagnosticsByFile: Map<String, List<CodeDiagnostic>> = emptyMap(),
    val terminalEntries: List<TerminalEntry> = emptyList(),
    val terminalCwd: String = "",
    val terminalHistory: List<String> = emptyList(),
    val outputLogs: List<String> = emptyList(),
    val searchQuery: String = "",
    val searchHits: List<SearchHit> = emptyList(),
    val gitChanges: List<GitFileChange> = emptyList(),
    val downloadingExtensionIds: Set<String> = emptySet(),
    val isCommandPaletteOpen: Boolean = false,
    val isAiSending: Boolean = false,
    val aiStatusBanner: String? = null,
    val testingKeyId: Long? = null,
    val keyTestResult: String? = null,
    val serverUrl: String = "",
    val toastMessage: String? = null
) {
    val activeTab: OpenEditorTab?
        get() = openTabs.firstOrNull { it.relativePath == activeTabPath }

    val allDiagnostics: List<CodeDiagnostic>
        get() = diagnosticsByFile.values.flatten()

    val errorCount: Int
        get() = allDiagnostics.count { it.severity == DiagnosticSeverity.ERROR }

    val warningCount: Int
        get() = allDiagnostics.count { it.severity == DiagnosticSeverity.WARNING }
}

sealed interface IdeEffect {
    data class OpenExternalBrowser(val url: String, val preferChrome: Boolean) : IdeEffect
    data class SetOrientation(val mode: OrientationMode) : IdeEffect
}

class IdeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = IdeDatabase.getInstance(application)
    private val repository = IdeRepository(db)
    val preferences = IdePreferences(application)
    private val fileManager = WorkspaceFileManager(application)
    private val webServer = LocalWebServer(fileManager.rootDir)
    private val executionEngine = CodeExecutionEngine(application, fileManager)
    private val aiClient = AiMultiProviderClient()

    private val _uiState = MutableStateFlow(IdeUiState())
    val uiState: StateFlow<IdeUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<IdeEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    val settings: StateFlow<IdeSettings> = preferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), IdeSettings())

    val extensions: StateFlow<List<ExtensionEntity>> = repository.extensions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val aiKeyProfiles: StateFlow<List<AiKeyProfileEntity>> = repository.aiKeyProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memoryNotes: StateFlow<List<AiMemoryNoteEntity>> = repository.memoryNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val skills: StateFlow<List<AiSkillEntity>> = repository.skills
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val chatMessages: StateFlow<List<AiChatMessageEntity>> = repository.chatMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val snapshots: StateFlow<List<WorkspaceSnapshotEntity>> = repository.snapshots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var lintJob: Job? = null
    private var entrySeq = 1L

    init {
        viewModelScope.launch {
            repository.seedDefaultsIfEmpty()
            val port = withContext(Dispatchers.IO) { webServer.start() }
            val baseUrl = "http://127.0.0.1:$port"
            _uiState.update {
                it.copy(
                    serverUrl = baseUrl,
                    browserUrl = "$baseUrl/index.html",
                    terminalEntries = listOf(
                        TerminalEntry(nextId(), "Zenncode Fast Terminal v2.0 • Localhost $baseUrl", TerminalEntryType.INFO),
                        TerminalEntry(nextId(), "Type 'help', 'ls', 'run main.py', 'run app.js', 'sql \"SELECT 1+1\"', or 'serve'.", TerminalEntryType.OUTPUT)
                    )
                )
            }
            refreshWorkspaceTree()
            openFile("index.html")
            refreshGitDiff()
            refreshAllDiagnostics()
        }
    }

    override fun onCleared() {
        super.onCleared()
        webServer.stop()
    }

    private fun nextId(): Long = entrySeq++

    fun refreshWorkspaceTree() {
        val nodes = fileManager.listTree()
        _uiState.update { it.copy(workspaceNodes = nodes) }
    }

    fun openFile(relativePath: String) {
        val existing = _uiState.value.openTabs.firstOrNull { it.relativePath == relativePath }
        if (existing != null) {
            _uiState.update { it.copy(activeTabPath = relativePath) }
            return
        }
        val content = fileManager.readFile(relativePath)
        val fileName = relativePath.substringAfterLast('/')
        val lang = WorkspaceFileManager.detectLanguageId(fileName)
        val newTab = OpenEditorTab(
            relativePath = relativePath,
            fileName = fileName,
            content = content,
            savedContent = content,
            languageId = lang
        )
        _uiState.update { state ->
            state.copy(
                openTabs = state.openTabs + newTab,
                activeTabPath = relativePath
            )
        }
        scheduleLint(relativePath, content, lang)
    }

    fun closeTab(relativePath: String) {
        _uiState.update { state ->
            val remaining = state.openTabs.filterNot { it.relativePath == relativePath }
            val newActive = if (state.activeTabPath == relativePath) {
                remaining.lastOrNull()?.relativePath
            } else {
                state.activeTabPath
            }
            state.copy(openTabs = remaining, activeTabPath = newActive)
        }
    }

    fun selectTab(relativePath: String) {
        _uiState.update { it.copy(activeTabPath = relativePath) }
    }

    fun updateActiveEditorContent(newContent: String, cursorLine: Int? = null, cursorCol: Int? = null) {
        val activePath = _uiState.value.activeTabPath ?: return
        var activeLang = "plaintext"
        _uiState.update { state ->
            val updatedTabs = state.openTabs.map { tab ->
                if (tab.relativePath == activePath) {
                    activeLang = tab.languageId
                    val pushUndo = if (newContent != tab.content) {
                        (tab.undoStack + tab.content).takeLast(35)
                    } else tab.undoStack
                    tab.copy(
                        content = newContent,
                        cursorLine = cursorLine ?: tab.cursorLine,
                        cursorColumn = cursorCol ?: tab.cursorColumn,
                        undoStack = pushUndo,
                        redoStack = emptyList()
                    )
                } else tab
            }
            state.copy(openTabs = updatedTabs)
        }
        if (settings.value.autoSave) {
            fileManager.writeFile(activePath, newContent)
            markTabSaved(activePath, newContent)
        }
        scheduleLint(activePath, newContent, activeLang)
    }

    fun updateCursorPosition(line: Int, column: Int) {
        val activePath = _uiState.value.activeTabPath ?: return
        _uiState.update { state ->
            state.copy(
                openTabs = state.openTabs.map { tab ->
                    if (tab.relativePath == activePath) tab.copy(cursorLine = line, cursorColumn = column)
                    else tab
                }
            )
        }
    }

    fun undoActiveEditor() {
        val tab = _uiState.value.activeTab ?: return
        val prev = tab.undoStack.lastOrNull() ?: return
        _uiState.update { state ->
            state.copy(
                openTabs = state.openTabs.map { t ->
                    if (t.relativePath == tab.relativePath) {
                        t.copy(
                            content = prev,
                            undoStack = t.undoStack.dropLast(1),
                            redoStack = (t.redoStack + t.content).takeLast(35)
                        )
                    } else t
                }
            )
        }
        if (settings.value.autoSave) {
            fileManager.writeFile(tab.relativePath, prev)
            markTabSaved(tab.relativePath, prev)
        }
        scheduleLint(tab.relativePath, prev, tab.languageId)
    }

    fun redoActiveEditor() {
        val tab = _uiState.value.activeTab ?: return
        val next = tab.redoStack.lastOrNull() ?: return
        _uiState.update { state ->
            state.copy(
                openTabs = state.openTabs.map { t ->
                    if (t.relativePath == tab.relativePath) {
                        t.copy(
                            content = next,
                            undoStack = (t.undoStack + t.content).takeLast(35),
                            redoStack = t.redoStack.dropLast(1)
                        )
                    } else t
                }
            )
        }
        if (settings.value.autoSave) {
            fileManager.writeFile(tab.relativePath, next)
            markTabSaved(tab.relativePath, next)
        }
        scheduleLint(tab.relativePath, next, tab.languageId)
    }

    fun saveActiveFile() {
        val tab = _uiState.value.activeTab ?: return
        fileManager.writeFile(tab.relativePath, tab.content)
        markTabSaved(tab.relativePath, tab.content)
        refreshWorkspaceTree()
        refreshGitDiff()
        showToast("Saved ${tab.fileName}")
    }

    fun formatActiveFile() {
        val tab = _uiState.value.activeTab ?: return
        val formatted = SyntaxHighlighter.formatCode(tab.content, tab.languageId, settings.value.tabSize)
        if (formatted != tab.content) {
            updateActiveEditorContent(formatted)
            showToast("Formatted ${tab.fileName}")
        }
    }

    private fun markTabSaved(path: String, savedText: String) {
        _uiState.update { state ->
            state.copy(
                openTabs = state.openTabs.map {
                    if (it.relativePath == path) it.copy(savedContent = savedText) else it
                }
            )
        }
    }

    private fun scheduleLint(path: String, content: String, languageId: String) {
        lintJob?.cancel()
        lintJob = viewModelScope.launch(Dispatchers.Default) {
            delay(140)
            val diags = CodeLinter.analyze(path, content, languageId)
            _uiState.update { state ->
                state.copy(diagnosticsByFile = state.diagnosticsByFile + (path to diags))
            }
        }
    }

    fun refreshAllDiagnostics() {
        viewModelScope.launch(Dispatchers.Default) {
            val map = linkedMapOf<String, List<CodeDiagnostic>>()
            for (node in fileManager.listTree()) {
                if (!node.isDirectory) {
                    val content = fileManager.readFile(node.relativePath)
                    val lang = WorkspaceFileManager.detectLanguageId(node.name)
                    map[node.relativePath] = CodeLinter.analyze(node.relativePath, content, lang)
                }
            }
            _uiState.update { it.copy(diagnosticsByFile = map) }
        }
    }

    fun createNewFile(relativePath: String) {
        val cleaned = relativePath.trim().trimStart('/')
        if (cleaned.isEmpty()) return
        val lang = WorkspaceFileManager.detectLanguageId(cleaned)
        val matchedExt = extensions.value.firstOrNull { ext ->
            ext.isInstalled && ext.fileExtensions.split(",").any { cleaned.endsWith(it.trim(), ignoreCase = true) }
        }
        val starter = matchedExt?.sampleTemplate ?: when (lang) {
            "html" -> "<!DOCTYPE html>\n<html>\n<body>\n  <h1>New Page</h1>\n</body>\n</html>\n"
            "javascript" -> "console.log('Hello from $cleaned');\n"
            "python" -> "print('Hello from $cleaned')\n"
            else -> ""
        }
        if (fileManager.createFile(cleaned, starter)) {
            refreshWorkspaceTree()
            openFile(cleaned)
            refreshGitDiff()
            showToast("Created $cleaned")
        } else {
            showToast("File already exists or invalid path")
        }
    }

    fun createNewFolder(relativePath: String) {
        val cleaned = relativePath.trim().trimStart('/')
        if (cleaned.isEmpty()) return
        if (fileManager.createDirectory(cleaned)) {
            refreshWorkspaceTree()
            showToast("Folder created: $cleaned")
        }
    }

    fun deleteWorkspacePath(relativePath: String) {
        if (fileManager.deletePath(relativePath)) {
            closeTab(relativePath)
            refreshWorkspaceTree()
            refreshGitDiff()
            _uiState.update { state ->
                state.copy(diagnosticsByFile = state.diagnosticsByFile - relativePath)
            }
            showToast("Deleted $relativePath")
        }
    }

    fun renameWorkspacePath(oldPath: String, newName: String) {
        val newPath = fileManager.renamePath(oldPath, newName) ?: return
        _uiState.update { state ->
            state.copy(
                openTabs = state.openTabs.map {
                    if (it.relativePath == oldPath) {
                        val fn = newPath.substringAfterLast('/')
                        it.copy(
                            relativePath = newPath,
                            fileName = fn,
                            languageId = WorkspaceFileManager.detectLanguageId(fn)
                        )
                    } else it
                },
                activeTabPath = if (state.activeTabPath == oldPath) newPath else state.activeTabPath
            )
        }
        refreshWorkspaceTree()
        refreshGitDiff()
    }

    fun importFileContent(fileName: String, content: String) {
        val safeName = fileName.ifBlank { "imported_${System.currentTimeMillis()}.txt" }
        fileManager.writeFile(safeName, content)
        refreshWorkspaceTree()
        openFile(safeName)
        refreshGitDiff()
        showToast("Imported $safeName")
    }

    // Sidebar & Layout
    fun selectSidebarTab(tab: SidebarTab) {
        _uiState.update { state ->
            if (state.activeSidebarTab == tab && state.isSidebarVisible) {
                state.copy(isSidebarVisible = false)
            } else {
                state.copy(
                    activeSidebarTab = tab,
                    isSidebarVisible = true,
                    isBrowserPaneVisible = if (tab == SidebarTab.BROWSER) true else state.isBrowserPaneVisible
                )
            }
        }
    }

    fun setSidebarVisible(visible: Boolean) {
        _uiState.update { it.copy(isSidebarVisible = visible) }
    }

    fun selectBottomTab(tab: BottomPanelTab) {
        _uiState.update { it.copy(activeBottomTab = tab, isBottomPanelVisible = true) }
    }

    fun toggleBottomPanel() {
        _uiState.update { it.copy(isBottomPanelVisible = !it.isBottomPanelVisible) }
    }

    fun toggleBrowserPane(forceOpen: Boolean? = null) {
        _uiState.update { state ->
            val next = forceOpen ?: !state.isBrowserPaneVisible
            state.copy(isBrowserPaneVisible = next)
        }
    }

    fun toggleCommandPalette(open: Boolean? = null) {
        _uiState.update { it.copy(isCommandPaletteOpen = open ?: !it.isCommandPaletteOpen) }
    }

    // Zoom & Orientation
    fun zoomInEditor() {
        viewModelScope.launch {
            preferences.setEditorFontSize(settings.value.editorFontSizeSp + 1.5f)
        }
    }

    fun zoomOutEditor() {
        viewModelScope.launch {
            preferences.setEditorFontSize(settings.value.editorFontSizeSp - 1.5f)
        }
    }

    fun resetZoomEditor() {
        viewModelScope.launch { preferences.setEditorFontSize(13.5f) }
    }

    fun setEditorZoomScale(factor: Float) {
        viewModelScope.launch {
            preferences.setEditorFontSize(settings.value.editorFontSizeSp * factor)
        }
    }

    fun toggleLandscapeMode() {
        val next = when (settings.value.orientationMode) {
            OrientationMode.AUTO -> OrientationMode.LANDSCAPE
            OrientationMode.LANDSCAPE -> OrientationMode.PORTRAIT
            OrientationMode.PORTRAIT -> OrientationMode.AUTO
        }
        setOrientationMode(next)
    }

    fun setOrientationMode(mode: OrientationMode) {
        viewModelScope.launch {
            preferences.setOrientationMode(mode)
            _effects.emit(IdeEffect.SetOrientation(mode))
            showToast("Orientation: ${mode.label}")
        }
    }

    // Browser & Run Code
    fun setBrowserRunTarget(target: BrowserRunTarget) {
        viewModelScope.launch { preferences.setBrowserRunTarget(target) }
    }

    fun navigateBuiltInBrowser(url: String) {
        val clean = when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("localhost") || url.startsWith("127.0.0.1") -> "http://$url"
            else -> "https://$url"
        }
        _uiState.update {
            it.copy(
                browserUrl = clean,
                isBrowserPaneVisible = true,
                browserReloadNonce = it.browserReloadNonce + 1
            )
        }
    }

    fun reloadBuiltInBrowser() {
        _uiState.update { it.copy(browserReloadNonce = it.browserReloadNonce + 1) }
    }

    fun addBrowserConsoleLog(logLine: String) {
        _uiState.update {
            it.copy(browserConsoleLogs = (it.browserConsoleLogs + logLine).takeLast(100))
        }
    }

    fun clearBrowserConsoleLogs() {
        _uiState.update { it.copy(browserConsoleLogs = emptyList()) }
    }

    fun openUrlInChrome(url: String) {
        viewModelScope.launch {
            _effects.emit(IdeEffect.OpenExternalBrowser(url, preferChrome = true))
        }
    }

    fun runActiveFile() {
        val tab = _uiState.value.activeTab ?: run {
            showToast("Open a file first to run")
            return
        }
        saveActiveFile()

        // Web files launch in user's chosen Browser target (Built-in Browser or Google Chrome)
        if (tab.languageId in setOf("html", "css", "markdown")) {
            val targetFile = if (tab.languageId == "html") tab.relativePath else "index.html"
            val url = webServer.urlFor(targetFile)
            if (tab.languageId == "markdown") {
                val htmlOut = renderMarkdownPreviewHtml(tab.fileName, tab.content)
                fileManager.writeFile(".zenncode_preview.html", htmlOut)
                launchBrowserUrl(webServer.urlFor(".zenncode_preview.html"))
            } else {
                launchBrowserUrl(url)
            }
            appendOutput("Served $targetFile at $url (${settings.value.browserRunTarget.label})")
            return
        }

        // Check if language pack is installed for optional languages
        val neededExt = extensions.value.firstOrNull { ext ->
            ext.category == "LANGUAGE" && ext.fileExtensions.split(",").any { tab.fileName.endsWith(it.trim(), true) }
        }
        if (neededExt != null && !neededExt.isInstalled && tab.languageId in setOf("python", "typescript")) {
            appendTerminal("Extension '${neededExt.name}' not installed yet. Installing automatically or visit Extensions tab...", TerminalEntryType.WARNING)
            installExtension(neededExt)
        }

        viewModelScope.launch {
            appendTerminal("$ run ${tab.relativePath}", TerminalEntryType.COMMAND)
            val result = executionEngine.runCode(tab.relativePath, tab.content, tab.languageId)
            appendTerminal(
                "${result.output}\n[Finished in ${result.durationMs}ms]",
                if (result.success) TerminalEntryType.SUCCESS else TerminalEntryType.ERROR
            )
            appendOutput("[${tab.relativePath}] (${result.durationMs}ms):\n${result.output}")
            _uiState.update { it.copy(isBottomPanelVisible = true, activeBottomTab = BottomPanelTab.TERMINAL) }
        }
    }

    fun launchBrowserUrl(url: String) {
        if (settings.value.browserRunTarget == BrowserRunTarget.GOOGLE_CHROME) {
            openUrlInChrome(url)
        } else {
            _uiState.update {
                it.copy(
                    browserUrl = url,
                    isBrowserPaneVisible = true,
                    browserReloadNonce = it.browserReloadNonce + 1
                )
            }
        }
    }

    private fun renderMarkdownPreviewHtml(title: String, md: String): String {
        val escaped = md
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .lines()
            .joinToString("\n") { line ->
                when {
                    line.startsWith("### ") -> "<h3>${line.drop(4)}</h3>"
                    line.startsWith("## ") -> "<h2>${line.drop(3)}</h2>"
                    line.startsWith("# ") -> "<h1>${line.drop(2)}</h1>"
                    line.startsWith("- ") -> "<li>${line.drop(2)}</li>"
                    line.isBlank() -> "<br/>"
                    else -> "<p>$line</p>"
                }
            }
        return """
            <!DOCTYPE html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>$title</title>
            <style>body{font-family:system-ui,sans-serif;background:#0f172a;color:#e2e8f0;padding:24px;line-height:1.6;max-width:720px;margin:auto}h1,h2,h3{color:#38bdf8}code{background:#1e293b;padding:2px 6px;border-radius:4px}</style>
            </head><body>$escaped</body></html>
        """.trimIndent()
    }

    // Built-in Fast Terminal
    fun executeTerminalCommand(rawCommand: String) {
        val cmd = rawCommand.trim()
        if (cmd.isEmpty()) return

        val promptPrefix = "${settings.value.terminalPrompt}:~/${_uiState.value.terminalCwd}$ "
        appendTerminal("$promptPrefix$cmd", TerminalEntryType.COMMAND)
        _uiState.update {
            it.copy(terminalHistory = (it.terminalHistory + cmd).takeLast(50))
        }

        val parts = splitCommandArgs(cmd)
        val verb = parts.firstOrNull()?.lowercase() ?: return
        val args = parts.drop(1)

        viewModelScope.launch {
            when (verb) {
                "clear", "cls" -> _uiState.update { it.copy(terminalEntries = emptyList()) }
                "help" -> appendTerminal(
                    """
                    Zenncode Terminal Commands:
                      ls, pwd, cd <dir>, cat <file>, touch <file>, mkdir <dir>, rm <path>
                      mv <src> <dst>, cp <src> <dst>, echo <text>, grep <pattern>, find [name]
                      run <file>        Execute JS/TS/Python/SQL/JSON or open HTML in browser
                      python <file>     Run Python script
                      node <file>       Run JavaScript file
                      js <code>         Evaluate inline JS expression
                      sql <query>       Run SQLite query
                      curl <url>        HTTP GET request
                      serve             Show localhost server URL
                      preview [file]    Open in Built-in Browser
                      chrome [file]     Open in Google Chrome
                      git status|log    Workspace snapshot status/history
                      git commit <msg>  Create snapshot commit
                      zoom <size>       Set editor font size (8-28)
                      theme <id>        Switch IDE theme
                      sh <os_cmd>       Run Android /system/bin/sh command
                      clear             Clear terminal buffer
                    """.trimIndent(),
                    TerminalEntryType.INFO
                )
                "pwd" -> appendTerminal("/zenncode_workspace/${_uiState.value.terminalCwd}".trimEnd('/'), TerminalEntryType.OUTPUT)
                "cd" -> {
                    val target = args.firstOrNull() ?: ""
                    if (target.isEmpty() || target == "~" || target == "/") {
                        _uiState.update { it.copy(terminalCwd = "") }
                    } else if (target == "..") {
                        val parent = _uiState.value.terminalCwd.substringBeforeLast('/', "")
                        val next = if (parent == _uiState.value.terminalCwd) "" else parent
                        _uiState.update { it.copy(terminalCwd = next) }
                    } else {
                        val rel = resolveTerminalRelPath(target)
                        val dir = fileManager.resolveSafe(rel)
                        if (dir.exists() && dir.isDirectory) {
                            _uiState.update { it.copy(terminalCwd = rel) }
                        } else {
                            appendTerminal("cd: no such directory: $target", TerminalEntryType.ERROR)
                        }
                    }
                }
                "ls", "dir" -> {
                    val cwd = _uiState.value.terminalCwd
                    val dir = if (cwd.isEmpty()) fileManager.rootDir else fileManager.resolveSafe(cwd)
                    val list = dir.listFiles()?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name })
                        ?.joinToString("\n") { f ->
                            if (f.isDirectory) "📁 ${f.name}/" else "📄 ${f.name.padEnd(22)} ${f.length()} B"
                        }.orEmpty()
                    appendTerminal(list.ifEmpty { "(empty directory)" }, TerminalEntryType.OUTPUT)
                }
                "cat" -> {
                    val fileArg = args.firstOrNull()
                    if (fileArg == null) appendTerminal("Usage: cat <file>", TerminalEntryType.ERROR)
                    else {
                        val rel = resolveTerminalRelPath(fileArg)
                        val f = fileManager.resolveSafe(rel)
                        if (f.exists() && f.isFile) appendTerminal(f.readText(), TerminalEntryType.OUTPUT)
                        else appendTerminal("cat: $fileArg: No such file", TerminalEntryType.ERROR)
                    }
                }
                "touch" -> {
                    val fileArg = args.firstOrNull() ?: return@launch
                    val rel = resolveTerminalRelPath(fileArg)
                    if (fileManager.createFile(rel, "")) {
                        refreshWorkspaceTree()
                        appendTerminal("Created $rel", TerminalEntryType.SUCCESS)
                    } else appendTerminal("File already exists: $rel", TerminalEntryType.WARNING)
                }
                "mkdir" -> {
                    val dirArg = args.firstOrNull() ?: return@launch
                    val rel = resolveTerminalRelPath(dirArg)
                    if (fileManager.createDirectory(rel)) {
                        refreshWorkspaceTree()
                        appendTerminal("Created directory $rel", TerminalEntryType.SUCCESS)
                    } else appendTerminal("mkdir failed: $rel", TerminalEntryType.ERROR)
                }
                "rm" -> {
                    val target = args.firstOrNull() ?: return@launch
                    val rel = resolveTerminalRelPath(target)
                    if (fileManager.deletePath(rel)) {
                        closeTab(rel)
                        refreshWorkspaceTree()
                        appendTerminal("Removed $rel", TerminalEntryType.SUCCESS)
                    } else appendTerminal("rm: cannot remove '$target'", TerminalEntryType.ERROR)
                }
                "mv" -> {
                    if (args.size < 2) appendTerminal("Usage: mv <src> <newName>", TerminalEntryType.ERROR)
                    else {
                        val rel = resolveTerminalRelPath(args[0])
                        val res = fileManager.renamePath(rel, args[1])
                        if (res != null) {
                            refreshWorkspaceTree()
                            appendTerminal("Renamed -> $res", TerminalEntryType.SUCCESS)
                        } else appendTerminal("mv failed", TerminalEntryType.ERROR)
                    }
                }
                "cp" -> {
                    if (args.size < 2) appendTerminal("Usage: cp <src> <dst>", TerminalEntryType.ERROR)
                    else {
                        val src = resolveTerminalRelPath(args[0])
                        val dst = resolveTerminalRelPath(args[1])
                        val content = fileManager.readFile(src)
                        fileManager.writeFile(dst, content)
                        refreshWorkspaceTree()
                        appendTerminal("Copied $src -> $dst", TerminalEntryType.SUCCESS)
                    }
                }
                "echo" -> appendTerminal(args.joinToString(" "), TerminalEntryType.OUTPUT)
                "grep" -> {
                    val pattern = args.firstOrNull() ?: ""
                    val hits = fileManager.searchInWorkspace(pattern)
                    if (hits.isEmpty()) appendTerminal("No matches for '$pattern'", TerminalEntryType.OUTPUT)
                    else appendTerminal(hits.take(25).joinToString("\n") { "${it.relativePath}:${it.line}: ${it.preview}" }, TerminalEntryType.OUTPUT)
                }
                "find" -> {
                    val q = args.firstOrNull().orEmpty()
                    val matches = fileManager.listTree().filter { it.relativePath.contains(q, true) }
                    appendTerminal(matches.joinToString("\n") { it.relativePath }.ifEmpty { "No files found" }, TerminalEntryType.OUTPUT)
                }
                "run", "python", "py", "node" -> {
                    val target = args.firstOrNull() ?: _uiState.value.activeTabPath
                    if (target == null) appendTerminal("Usage: $verb <file>", TerminalEntryType.ERROR)
                    else {
                        val rel = resolveTerminalRelPath(target)
                        val f = fileManager.resolveSafe(rel)
                        if (!f.exists()) {
                            appendTerminal("$verb: file '$rel' not found", TerminalEntryType.ERROR)
                        } else {
                            val content = _uiState.value.openTabs.firstOrNull { it.relativePath == rel }?.content ?: f.readText()
                            val lang = when (verb) {
                                "python", "py" -> "python"
                                "node" -> "javascript"
                                else -> WorkspaceFileManager.detectLanguageId(f.name)
                            }
                            if (lang in setOf("html", "css", "markdown")) {
                                val url = webServer.urlFor(if (lang == "html") rel else "index.html")
                                launchBrowserUrl(url)
                                appendTerminal("Opened $url in ${settings.value.browserRunTarget.label}", TerminalEntryType.SUCCESS)
                            } else {
                                val res = executionEngine.runCode(rel, content, lang)
                                appendTerminal("${res.output}\n[${res.durationMs}ms]", if (res.success) TerminalEntryType.SUCCESS else TerminalEntryType.ERROR)
                            }
                        }
                    }
                }
                "js" -> {
                    val code = cmd.removePrefix("js").trim()
                    val res = executionEngine.runCode("repl.js", code, "javascript")
                    appendTerminal(res.output, if (res.success) TerminalEntryType.SUCCESS else TerminalEntryType.ERROR)
                }
                "sql" -> {
                    val query = cmd.removePrefix("sql").trim().removeSurrounding("\"")
                    val res = executionEngine.runCode("repl.sql", query, "sql")
                    appendTerminal(res.output, if (res.success) TerminalEntryType.SUCCESS else TerminalEntryType.ERROR)
                }
                "curl" -> {
                    val url = args.firstOrNull()
                    if (url == null) appendTerminal("Usage: curl <url>", TerminalEntryType.ERROR)
                    else {
                        appendTerminal("Fetching $url ...", TerminalEntryType.INFO)
                        val res = executionEngine.curlGet(url)
                        appendTerminal(res.output, if (res.success) TerminalEntryType.OUTPUT else TerminalEntryType.ERROR)
                    }
                }
                "serve" -> appendTerminal("Local Web Server active: ${_uiState.value.serverUrl}", TerminalEntryType.SUCCESS)
                "preview" -> {
                    val target = args.firstOrNull() ?: "index.html"
                    val url = webServer.urlFor(resolveTerminalRelPath(target))
                    _uiState.update { it.copy(browserUrl = url, isBrowserPaneVisible = true, browserReloadNonce = it.browserReloadNonce + 1) }
                    appendTerminal("Built-in Browser -> $url", TerminalEntryType.SUCCESS)
                }
                "chrome" -> {
                    val target = args.firstOrNull() ?: "index.html"
                    val url = webServer.urlFor(resolveTerminalRelPath(target))
                    openUrlInChrome(url)
                    appendTerminal("Launched Google Chrome -> $url", TerminalEntryType.SUCCESS)
                }
                "git" -> handleGitTerminalCommand(args)
                "zoom" -> {
                    val size = args.firstOrNull()?.toFloatOrNull()
                    if (size != null) {
                        preferences.setEditorFontSize(size)
                        appendTerminal("Editor zoom font size set to ${size.coerceIn(8f, 28f)}sp", TerminalEntryType.SUCCESS)
                    } else appendTerminal("Current editor font size: ${settings.value.editorFontSizeSp}sp", TerminalEntryType.INFO)
                }
                "theme" -> {
                    val id = args.firstOrNull()
                    if (id == null) {
                        appendTerminal("Themes: ${IdeThemes.all.joinToString { it.id }}", TerminalEntryType.INFO)
                    } else {
                        val found = IdeThemes.all.firstOrNull { it.id.contains(id, true) || it.name.contains(id, true) }
                        if (found != null) {
                            preferences.setThemeId(found.id)
                            appendTerminal("Theme switched to ${found.name}", TerminalEntryType.SUCCESS)
                        } else appendTerminal("Theme '$id' not found", TerminalEntryType.ERROR)
                    }
                }
                "env" -> appendTerminal("IDE=Zenncode\nSERVER=${_uiState.value.serverUrl}\nTHEME=${settings.value.themeId}\nTARGET=${settings.value.browserRunTarget.name}", TerminalEntryType.OUTPUT)
                "whoami" -> appendTerminal("zenn-developer", TerminalEntryType.OUTPUT)
                "date" -> appendTerminal(java.util.Date().toString(), TerminalEntryType.OUTPUT)
                "sh" -> {
                    val osCmd = cmd.removePrefix("sh").trim()
                    if (osCmd.isEmpty()) appendTerminal("Usage: sh <command>", TerminalEntryType.ERROR)
                    else {
                        val res = executionEngine.runSystemShell(osCmd, fileManager.rootDir)
                        appendTerminal(res.output, if (res.success) TerminalEntryType.OUTPUT else TerminalEntryType.ERROR)
                    }
                }
                else -> {
                    // Fallback: attempt safe Android shell command directly
                    val res = executionEngine.runSystemShell(cmd, fileManager.rootDir)
                    appendTerminal(res.output, if (res.success) TerminalEntryType.OUTPUT else TerminalEntryType.ERROR)
                }
            }
        }
    }

    private suspend fun handleGitTerminalCommand(args: List<String>) {
        when (args.firstOrNull()?.lowercase()) {
            "status" -> {
                refreshGitDiff()
                val changes = _uiState.value.gitChanges
                if (changes.isEmpty()) appendTerminal("On branch main\nNothing to commit, working tree clean", TerminalEntryType.SUCCESS)
                else appendTerminal(changes.joinToString("\n") { "  ${it.status}  ${it.relativePath} (+${it.addedLines}/-${it.removedLines})" }, TerminalEntryType.WARNING)
            }
            "commit" -> {
                val msg = args.drop(1).joinToString(" ").removeSurrounding("\"").ifBlank { "Snapshot commit" }
                commitSnapshot(msg)
                appendTerminal("[main] Committed: $msg", TerminalEntryType.SUCCESS)
            }
            "log" -> {
                val snaps = snapshots.value
                if (snaps.isEmpty()) appendTerminal("No commits yet. Use 'git commit \"msg\"'", TerminalEntryType.INFO)
                else appendTerminal(snaps.take(10).joinToString("\n") { "${it.commitHash} - ${it.message} (${it.fileCount} files)" }, TerminalEntryType.OUTPUT)
            }
            else -> appendTerminal("Supported: git status | git commit <msg> | git log", TerminalEntryType.INFO)
        }
    }

    private fun resolveTerminalRelPath(arg: String): String {
        val cwd = _uiState.value.terminalCwd
        val cleaned = arg.trim().trimStart('/')
        return if (cwd.isEmpty()) cleaned else "$cwd/$cleaned"
    }

    private fun splitCommandArgs(input: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuote: Char? = null
        for (c in input) {
            when {
                inQuote != null && c == inQuote -> inQuote = null
                inQuote == null && (c == '"' || c == '\'') -> inQuote = c
                inQuote == null && c.isWhitespace() -> {
                    if (current.isNotEmpty()) {
                        result.add(current.toString())
                        current.clear()
                    }
                }
                else -> current.append(c)
            }
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }

    fun appendTerminal(text: String, type: TerminalEntryType) {
        _uiState.update {
            it.copy(terminalEntries = (it.terminalEntries + TerminalEntry(nextId(), text, type)).takeLast(200))
        }
    }

    fun clearTerminal() {
        _uiState.update { it.copy(terminalEntries = emptyList()) }
    }

    private fun appendOutput(text: String) {
        _uiState.update { it.copy(outputLogs = (it.outputLogs + text).takeLast(100)) }
    }

    // Search & Git Source Control
    fun searchWorkspace(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        viewModelScope.launch(Dispatchers.IO) {
            val hits = fileManager.searchInWorkspace(query)
            _uiState.update { it.copy(searchHits = hits) }
        }
    }

    fun refreshGitDiff() {
        viewModelScope.launch(Dispatchers.IO) {
            val latest = repository.getLatestSnapshot()
            val changes = fileManager.computeGitDiff(latest?.filesJson)
            _uiState.update { it.copy(gitChanges = changes) }
        }
    }

    fun commitSnapshot(message: String) {
        val msg = message.trim().ifBlank { "Update workspace" }
        viewModelScope.launch(Dispatchers.IO) {
            val filesMap = fileManager.exportAllFilesMap()
            val json = fileManager.serializeFilesMap(filesMap)
            val hash = UUID.randomUUID().toString().take(7)
            repository.saveSnapshot(
                WorkspaceSnapshotEntity(
                    commitHash = hash,
                    message = msg,
                    filesJson = json,
                    fileCount = filesMap.size
                )
            )
            refreshGitDiff()
            showToast("Committed [$hash]: $msg")
        }
    }

    // Extensions Marketplace
    fun installExtension(ext: ExtensionEntity) {
        if (_uiState.value.downloadingExtensionIds.contains(ext.id)) return
        viewModelScope.launch {
            _uiState.update { it.copy(downloadingExtensionIds = it.downloadingExtensionIds + ext.id) }
            delay(550)
            repository.updateExtension(ext.copy(isInstalled = true, isEnabled = true))
            _uiState.update { it.copy(downloadingExtensionIds = it.downloadingExtensionIds - ext.id) }
            showToast("Installed ${ext.name}")
        }
    }

    fun toggleExtensionEnabled(ext: ExtensionEntity) {
        viewModelScope.launch {
            repository.updateExtension(ext.copy(isEnabled = !ext.isEnabled))
        }
    }

    fun uninstallExtension(ext: ExtensionEntity) {
        viewModelScope.launch {
            repository.updateExtension(ext.copy(isInstalled = false, isEnabled = false))
            showToast("Uninstalled ${ext.name}")
        }
    }

    fun createSampleFileFromExtension(ext: ExtensionEntity) {
        val firstExt = ext.fileExtensions.split(",").firstOrNull()?.trim()?.takeIf { it.startsWith(".") } ?: ".txt"
        val name = "sample_${ext.languageId}$firstExt"
        if (fileManager.createFile(name, ext.sampleTemplate)) {
            refreshWorkspaceTree()
            openFile(name)
            refreshGitDiff()
            showToast("Created $name")
        } else {
            openFile(name)
        }
    }

    // Settings
    fun setThemeId(id: String) = viewModelScope.launch { preferences.setThemeId(id) }
    fun setEditorFontSize(sizeSp: Float) = viewModelScope.launch { preferences.setEditorFontSize(sizeSp) }
    fun setWordWrap(enabled: Boolean) = viewModelScope.launch { preferences.setWordWrap(enabled) }
    fun setShowLineNumbers(enabled: Boolean) = viewModelScope.launch { preferences.setShowLineNumbers(enabled) }
    fun setTabSize(size: Int) = viewModelScope.launch { preferences.setTabSize(size) }
    fun setTerminalFontSize(size: Float) = viewModelScope.launch { preferences.setTerminalFontSize(size) }
    fun setTerminalCursorStyle(style: TerminalCursorStyle) = viewModelScope.launch { preferences.setTerminalCursorStyle(style) }
    fun setTerminalPrompt(prompt: String) = viewModelScope.launch { preferences.setTerminalPrompt(prompt) }
    fun setAutoSave(enabled: Boolean) = viewModelScope.launch { preferences.setAutoSave(enabled) }

    // AI Studio: Keys (Auto-Rollback Pool), Shared Memory, Skills, Chat
    fun addOrUpdateAiKey(
        id: Long = 0L,
        label: String,
        provider: AiProviderType,
        baseUrl: String,
        modelId: String,
        apiKey: String
    ) {
        viewModelScope.launch {
            val currentCount = repository.getAiKeyProfilesOnce().size
            val entity = AiKeyProfileEntity(
                id = id,
                label = label.ifBlank { "${provider.displayName} #${currentCount + 1}" },
                provider = provider.name,
                baseUrl = baseUrl.ifBlank { provider.defaultBaseUrl },
                modelId = modelId.ifBlank { provider.defaultModel },
                apiKey = apiKey.trim(),
                priorityOrder = if (id == 0L) currentCount else aiKeyProfiles.value.firstOrNull { it.id == id }?.priorityOrder ?: currentCount,
                isEnabled = true,
                lastStatusMessage = "Ready"
            )
            repository.saveAiKeyProfile(entity)
            showToast("Saved API Key: ${entity.label}")
        }
    }

    fun toggleAiKeyEnabled(profile: AiKeyProfileEntity) {
        viewModelScope.launch {
            repository.updateAiKeyProfile(profile.copy(isEnabled = !profile.isEnabled))
        }
    }

    fun moveAiKeyPriority(profile: AiKeyProfileEntity, moveUp: Boolean) {
        viewModelScope.launch {
            val sorted = repository.getAiKeyProfilesOnce()
                .sortedWith(compareBy<AiKeyProfileEntity> { it.priorityOrder }.thenBy { it.id })
                .toMutableList()
            val idx = sorted.indexOfFirst { it.id == profile.id }
            if (idx == -1) return@launch
            val swapIdx = if (moveUp) idx - 1 else idx + 1
            if (swapIdx !in sorted.indices) return@launch
            val item = sorted.removeAt(idx)
            sorted.add(swapIdx, item)
            sorted.forEachIndexed { order, p ->
                repository.updateAiKeyProfile(p.copy(priorityOrder = order))
            }
        }
    }

    fun deleteAiKey(id: Long) {
        viewModelScope.launch { repository.deleteAiKeyProfile(id) }
    }

    fun testAiKey(profile: AiKeyProfileEntity) {
        viewModelScope.launch {
            _uiState.update { it.copy(testingKeyId = profile.id, keyTestResult = null) }
            val result = aiClient.testKey(profile)
            result.fold(
                onSuccess = { reply ->
                    repository.updateAiKeyProfile(profile.copy(lastStatusMessage = "Verified OK", failureCount = 0))
                    _uiState.update { it.copy(testingKeyId = null, keyTestResult = "${profile.label}: Connected ($reply)") }
                },
                onFailure = { err ->
                    repository.updateAiKeyProfile(profile.copy(lastStatusMessage = "Test failed: ${err.message}", failureCount = profile.failureCount + 1))
                    _uiState.update { it.copy(testingKeyId = null, keyTestResult = "${profile.label} failed: ${err.message}") }
                }
            )
        }
    }

    fun addMemoryNote(title: String, content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            repository.saveMemoryNote(
                AiMemoryNoteEntity(
                    title = title.ifBlank { "Project Note" },
                    content = content.trim(),
                    isPinned = true
                )
            )
            showToast("Saved to Shared Memory")
        }
    }

    fun toggleMemoryPin(note: AiMemoryNoteEntity) {
        viewModelScope.launch {
            repository.updateMemoryNote(note.copy(isPinned = !note.isPinned))
        }
    }

    fun deleteMemoryNote(id: Long) {
        viewModelScope.launch { repository.deleteMemoryNote(id) }
    }

    fun addCustomSkill(name: String, description: String, systemPrompt: String) {
        if (name.isBlank() || systemPrompt.isBlank()) return
        viewModelScope.launch {
            repository.saveSkill(
                AiSkillEntity(
                    name = name.trim(),
                    description = description.ifBlank { "Custom user skill" },
                    systemPrompt = systemPrompt.trim(),
                    isEnabled = true,
                    isBuiltIn = false
                )
            )
            showToast("Added Skill: $name")
        }
    }

    fun toggleSkill(skill: AiSkillEntity) {
        viewModelScope.launch {
            repository.updateSkill(skill.copy(isEnabled = !skill.isEnabled))
        }
    }

    fun deleteSkill(id: Long) {
        viewModelScope.launch { repository.deleteSkill(id) }
    }

    fun sendAiPrompt(promptText: String, includeActiveFile: Boolean = true) {
        val prompt = promptText.trim()
        if (prompt.isEmpty() || _uiState.value.isAiSending) return

        viewModelScope.launch {
            _uiState.update { it.copy(isAiSending = true, aiStatusBanner = "Dispatching with Auto-Rollback Pool...") }
            val historySnapshot = repository.getChatMessagesOnce()
            repository.addChatMessage(AiChatMessageEntity(role = "USER", content = prompt))

            val activeTab = _uiState.value.activeTab
            val diags = activeTab?.let { _uiState.value.diagnosticsByFile[it.relativePath].orEmpty() }.orEmpty()
            val workspaceCtx = if (includeActiveFile && activeTab != null) {
                buildString {
                    appendLine("Active File: ${activeTab.relativePath} (${activeTab.languageId})")
                    if (diags.isNotEmpty()) {
                        appendLine("Diagnostics:")
                        diags.take(10).forEach { d ->
                            appendLine("- Line ${d.line}: [${d.severity}] ${d.message}")
                        }
                    }
                    appendLine("Code:\n```${activeTab.languageId}\n${activeTab.content.take(4500)}\n```")
                }
            } else ""

            val outcome = aiClient.sendWithAutoRollback(
                profiles = repository.getAiKeyProfilesOnce(),
                chatHistory = historySnapshot,
                memoryNotes = repository.getMemoryNotesOnce(),
                activeSkills = repository.getSkillsOnce().filter { it.isEnabled },
                workspaceContext = workspaceCtx,
                userPrompt = prompt,
                onProfileFailure = { failed, reason ->
                    repository.updateAiKeyProfile(
                        failed.copy(
                            failureCount = failed.failureCount + 1,
                            lastStatusMessage = "Rollback: $reason"
                        )
                    )
                    _uiState.update {
                        it.copy(aiStatusBanner = "${failed.label} failed ($reason) -> Auto-rolling back...")
                    }
                },
                onProfileSuccess = { used ->
                    repository.updateAiKeyProfile(
                        used.copy(
                            lastStatusMessage = "Active (OK)",
                            lastUsedAt = System.currentTimeMillis()
                        )
                    )
                }
            )

            // Log rollback events visibly if any failover occurred
            if (outcome.rollbackEvents.isNotEmpty()) {
                val rollbackSummary = outcome.rollbackEvents.joinToString("\n") { ev ->
                    val nextLabel = ev.nextKeyLabel?.let { "$it (${ev.nextModel})" } ?: "No fallback left"
                    "Auto-Rollback: ${ev.failedKeyLabel} [${ev.failedModel}] failed (${ev.errorReason}) -> $nextLabel"
                }
                repository.addChatMessage(
                    AiChatMessageEntity(
                        role = "ROLLBACK",
                        content = rollbackSummary
                    )
                )
            }

            if (outcome.success && outcome.usedProfile != null) {
                repository.addChatMessage(
                    AiChatMessageEntity(
                        role = "ASSISTANT",
                        content = outcome.responseText,
                        modelUsed = outcome.usedProfile.modelId,
                        keyLabelUsed = outcome.usedProfile.label
                    )
                )
                _uiState.update {
                    it.copy(
                        isAiSending = false,
                        aiStatusBanner = "Responded via ${outcome.usedProfile.label} (${outcome.usedProfile.modelId})"
                    )
                }
            } else {
                repository.addChatMessage(
                    AiChatMessageEntity(
                        role = "ROLLBACK",
                        content = outcome.errorSummary ?: "All AI keys failed."
                    )
                )
                _uiState.update {
                    it.copy(
                        isAiSending = false,
                        aiStatusBanner = outcome.errorSummary
                    )
                }
            }
        }
    }

    fun askAiToFixDiagnostic(diagnostic: CodeDiagnostic) {
        openFile(diagnostic.filePath)
        _uiState.update {
            it.copy(activeSidebarTab = SidebarTab.AI_AGENT, isSidebarVisible = true)
        }
        sendAiPrompt(
            "Fix this ${diagnostic.severity.name.lowercase()} in `${diagnostic.filePath}` at line ${diagnostic.line}: ${diagnostic.message}",
            includeActiveFile = true
        )
    }

    fun applyCodeToActiveEditor(code: String) {
        val active = _uiState.value.activeTab
        if (active == null) {
            showToast("Open a file in the editor first")
            return
        }
        updateActiveEditorContent(code)
        showToast("Applied code to ${active.fileName}")
    }

    fun clearAiChat() {
        viewModelScope.launch { repository.clearChat() }
    }

    fun showToast(msg: String) {
        _uiState.update { it.copy(toastMessage = msg) }
    }

    fun consumeToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }
}
