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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Commit
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BrowserRunTarget
import com.example.data.ExtensionEntity
import com.example.data.GitFileChange
import com.example.data.IdeSettings
import com.example.data.OrientationMode
import com.example.data.SearchHit
import com.example.data.WorkspaceNode
import com.example.data.WorkspaceSnapshotEntity
import com.example.ui.theme.IdeThemes
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalIdePalette

@Composable
fun ExplorerPanel(
    nodes: List<WorkspaceNode>,
    activeFilePath: String?,
    onOpenFile: (String) -> Unit,
    onCreateFile: (String) -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenamePath: (String, String) -> Unit,
    onDeletePath: (String) -> Unit,
    onImportFileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalIdePalette.current
    var showCreateDialog by remember { mutableStateOf<Boolean?>(null) } // true=file, false=folder
    var renameTarget by remember { mutableStateOf<WorkspaceNode?>(null) }
    var draftName by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.sidebarBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "EXPLORER",
                color = palette.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    draftName = ""
                    showCreateDialog = true
                },
                modifier = Modifier.size(26.dp).testTag("explorer_new_file")
            ) {
                Icon(Icons.Default.NoteAdd, contentDescription = "New File", tint = palette.textPrimary, modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = {
                    draftName = ""
                    showCreateDialog = false
                },
                modifier = Modifier.size(26.dp)
            ) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder", tint = palette.textPrimary, modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = onImportFileClick,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = "Import File", tint = palette.textPrimary, modifier = Modifier.size(16.dp))
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            items(nodes, key = { it.relativePath }) { node ->
                val isSelected = node.relativePath == activeFilePath
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) palette.selectionBg else palette.sidebarBg)
                        .clickable {
                            if (!node.isDirectory) onOpenFile(node.relativePath)
                        }
                        .padding(
                            start = (10 + node.depth * 14).dp,
                            end = 6.dp,
                            top = 6.dp,
                            bottom = 6.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (node.isDirectory) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = palette.warningColor, modifier = Modifier.size(16.dp))
                    } else {
                        FileBadge(fileName = node.name)
                    }
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = node.name,
                        color = if (isSelected) palette.textPrimary else palette.textPrimary.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.DriveFileRenameOutline,
                        contentDescription = "Rename",
                        tint = palette.textMuted,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable {
                                draftName = node.name
                                renameTarget = node
                            }
                            .padding(1.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = palette.textMuted,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onDeletePath(node.relativePath) }
                            .padding(1.dp)
                    )
                }
            }
        }
    }

    showCreateDialog?.let { isFile ->
        AlertDialog(
            onDismissRequest = { showCreateDialog = null },
            containerColor = palette.surfaceElevated,
            titleContentColor = palette.textPrimary,
            title = { Text(if (isFile) "New File" else "New Folder") },
            text = {
                SidebarInputField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    placeholder = if (isFile) "e.g. script.py or src/app.js" else "e.g. components"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (draftName.isNotBlank()) {
                        if (isFile) onCreateFile(draftName) else onCreateFolder(draftName)
                    }
                    showCreateDialog = null
                }) { Text("Create", color = palette.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = null }) { Text("Cancel", color = palette.textMuted) }
            }
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = palette.surfaceElevated,
            titleContentColor = palette.textPrimary,
            title = { Text("Rename ${target.name}") },
            text = {
                SidebarInputField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    placeholder = "New name"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (draftName.isNotBlank()) onRenamePath(target.relativePath, draftName)
                    renameTarget = null
                }) { Text("Rename", color = palette.accent) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel", color = palette.textMuted) }
            }
        )
    }
}

@Composable
private fun FileBadge(fileName: String) {
    val palette = LocalIdePalette.current
    val ext = fileName.substringAfterLast('.', "").lowercase()
    val (label, color) = when (ext) {
        "html", "htm" -> "H" to palette.syntaxTag
        "css" -> "#" to palette.infoColor
        "js", "mjs" -> "JS" to palette.warningColor
        "ts", "tsx" -> "TS" to palette.accent
        "py" -> "PY" to palette.successColor
        "kt", "kts" -> "KT" to palette.syntaxKeyword
        "java" -> "JV" to palette.errorColor
        "sql" -> "DB" to palette.syntaxType
        "json" -> "{}" to palette.warningColor
        "md" -> "MD" to palette.textMuted
        else -> "•" to palette.textMuted
    }
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SearchPanel(
    query: String,
    hits: List<SearchHit>,
    onQueryChange: (String) -> Unit,
    onJumpToHit: (SearchHit) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalIdePalette.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.sidebarBg)
            .padding(10.dp)
    ) {
        Text("SEARCH WORKSPACE", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        SidebarInputField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "Search text across all files..."
        )
        Spacer(Modifier.height(8.dp))
        Text("${hits.size} result(s)", color = palette.textMuted, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(hits) { hit ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.surfaceElevated)
                        .clickable { onJumpToHit(hit) }
                        .padding(8.dp)
                ) {
                    Text("${hit.relativePath}:${hit.line}", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(hit.preview, color = palette.textPrimary, fontSize = 11.sp, fontFamily = JetBrainsMonoFontFamily, maxLines = 2)
                }
            }
        }
    }
}

@Composable
fun SourceControlPanel(
    changes: List<GitFileChange>,
    snapshots: List<WorkspaceSnapshotEntity>,
    onCommit: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalIdePalette.current
    var commitMessage by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.sidebarBg)
            .padding(10.dp)
    ) {
        Text("SOURCE CONTROL (GIT)", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        SidebarInputField(
            value = commitMessage,
            onValueChange = { commitMessage = it },
            placeholder = "Commit message..."
        )
        Spacer(Modifier.height(6.dp))
        Surface(
            color = palette.accent,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onCommit(commitMessage)
                    commitMessage = ""
                }
        ) {
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Commit, contentDescription = null, tint = palette.onAccent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Commit Snapshot (${changes.size})", color = palette.onAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(10.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (changes.isEmpty()) {
                item {
                    Text("Working tree clean", color = palette.successColor, fontSize = 12.sp)
                }
            } else {
                items(changes, key = { it.relativePath }) { change ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(palette.surfaceElevated)
                            .clickable { onOpenFile(change.relativePath) }
                            .padding(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = change.status,
                                color = when (change.status) {
                                    "A" -> palette.successColor
                                    "D" -> palette.errorColor
                                    else -> palette.warningColor
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(change.relativePath, color = palette.textPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text("+${change.addedLines}/-${change.removedLines}", color = palette.textMuted, fontSize = 10.sp)
                        }
                        if (change.diffPreview.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = change.diffPreview,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.sp,
                                color = palette.textMuted,
                                maxLines = 5
                            )
                        }
                    }
                }
            }

            if (snapshots.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("COMMIT HISTORY", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                items(snapshots.take(10), key = { it.id }) { snap ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(palette.panelBg)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(snap.commitHash, color = palette.accent, fontFamily = JetBrainsMonoFontFamily, fontSize = 11.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(snap.message, color = palette.textPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun ExtensionsPanel(
    extensions: List<ExtensionEntity>,
    downloadingIds: Set<String>,
    onInstall: (ExtensionEntity) -> Unit,
    onToggleEnable: (ExtensionEntity) -> Unit,
    onUninstall: (ExtensionEntity) -> Unit,
    onCreateSample: (ExtensionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalIdePalette.current
    var search by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }

    val filtered = extensions.filter { ext ->
        val catOk = selectedCategory == "ALL" || ext.category == selectedCategory
        val qOk = search.isBlank() ||
            ext.name.contains(search, true) ||
            ext.description.contains(search, true) ||
            ext.fileExtensions.contains(search, true)
        catOk && qOk
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.sidebarBg)
            .padding(10.dp)
    ) {
        Text("EXTENSIONS MARKETPLACE", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        SidebarInputField(
            value = search,
            onValueChange = { search = it },
            placeholder = "Search languages, linters, tools..."
        )
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf("ALL", "LANGUAGE", "LINTER", "TOOL")) { cat ->
                val active = selectedCategory == cat
                Surface(
                    color = if (active) palette.accent else palette.surfaceElevated,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.clickable { selectedCategory = cat }
                ) {
                    Text(
                        text = cat,
                        color = if (active) palette.onAccent else palette.textPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered, key = { it.id }) { ext ->
                val isDownloading = downloadingIds.contains(ext.id)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.surfaceElevated)
                        .border(0.5.dp, palette.border, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Extension, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ext.name, color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("${ext.publisher} • v${ext.version} • ${ext.sizeLabel} • ${ext.downloadsLabel}", color = palette.textMuted, fontSize = 10.sp)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(ext.description, color = palette.textPrimary.copy(alpha = 0.85f), fontSize = 11.sp, lineHeight = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(ext.fileExtensions, color = palette.accent, fontFamily = JetBrainsMonoFontFamily, fontSize = 10.sp, modifier = Modifier.weight(1f))

                        if (isDownloading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = palette.accent)
                        } else if (!ext.isInstalled) {
                            Surface(
                                color = palette.accent,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .clickable { onInstall(ext) }
                                    .testTag("install_ext_${ext.languageId}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, tint = palette.onAccent, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Install", color = palette.onAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            if (ext.sampleTemplate.isNotBlank()) {
                                Surface(
                                    color = palette.panelBg,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.clickable { onCreateSample(ext) }
                                ) {
                                    Text("+ Sample", color = palette.accent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp))
                                }
                            }
                            Surface(
                                color = if (ext.isEnabled) palette.successColor.copy(alpha = 0.2f) else palette.panelBg,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.clickable { onToggleEnable(ext) }
                            ) {
                                Text(
                                    text = if (ext.isEnabled) "Enabled" else "Disabled",
                                    color = if (ext.isEnabled) palette.successColor else palette.textMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Uninstall ${ext.name}",
                                tint = palette.errorColor,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { onUninstall(ext) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsThemesPanel(
    settings: IdeSettings,
    onSelectTheme: (String) -> Unit,
    onSetEditorFontSize: (Float) -> Unit,
    onSetWordWrap: (Boolean) -> Unit,
    onSetLineNumbers: (Boolean) -> Unit,
    onSetAutoSave: (Boolean) -> Unit,
    onSetTabSize: (Int) -> Unit,
    onSetBrowserTarget: (BrowserRunTarget) -> Unit,
    onSetOrientation: (OrientationMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalIdePalette.current
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(palette.sidebarBg)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("VS CODE THEMES", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        items(IdeThemes.all, key = { it.id }) { theme ->
            val selected = theme.id == settings.themeId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) palette.selectionBg else palette.surfaceElevated)
                    .border(
                        width = if (selected) 1.dp else 0.5.dp,
                        color = if (selected) palette.accent else palette.border,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelectTheme(theme.id) }
                    .padding(10.dp)
                    .testTag("theme_item_${theme.id}"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(theme.editorBg).border(0.5.dp, palette.border, CircleShape))
                    Box(Modifier.size(14.dp).clip(CircleShape).background(theme.accent))
                    Box(Modifier.size(14.dp).clip(CircleShape).background(theme.syntaxKeyword))
                    Box(Modifier.size(14.dp).clip(CircleShape).background(theme.syntaxString))
                }
                Spacer(Modifier.width(10.dp))
                Text(theme.name, color = palette.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                if (selected) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Active Theme", tint = palette.accent, modifier = Modifier.size(16.dp))
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Text("SCREEN & RUN TARGET", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Orientation Mode", color = palette.textPrimary, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OrientationMode.entries.forEach { mode ->
                    val active = settings.orientationMode == mode
                    Surface(
                        color = if (active) palette.accent else palette.surfaceElevated,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable { onSetOrientation(mode) }
                    ) {
                        Text(
                            text = mode.label,
                            color = if (active) palette.onAccent else palette.textPrimary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Web Run Target", color = palette.textPrimary, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BrowserRunTarget.entries.forEach { target ->
                    val active = settings.browserRunTarget == target
                    Surface(
                        color = if (active) palette.accent else palette.surfaceElevated,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable { onSetBrowserTarget(target) }
                    ) {
                        Text(
                            text = target.label,
                            color = if (active) palette.onAccent else palette.textPrimary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Text("EDITOR SETTINGS", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Editor Zoom / Font Size: ${settings.editorFontSizeSp.toInt()}sp", color = palette.textPrimary, fontSize = 12.sp)
            Slider(
                value = settings.editorFontSizeSp,
                onValueChange = onSetEditorFontSize,
                valueRange = 8f..28f
            )
            SettingSwitchRow("Word Wrap", settings.wordWrap, onSetWordWrap)
            SettingSwitchRow("Line Numbers Gutter", settings.showLineNumbers, onSetLineNumbers)
            SettingSwitchRow("Auto Save", settings.autoSave, onSetAutoSave)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tab Width", color = palette.textPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                listOf(2, 4).forEach { size ->
                    Surface(
                        color = if (settings.tabSize == size) palette.accent else palette.surfaceElevated,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .clickable { onSetTabSize(size) }
                    ) {
                        Text(
                            text = "$size spaces",
                            color = if (settings.tabSize == size) palette.onAccent else palette.textPrimary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val palette = LocalIdePalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = palette.textPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SidebarInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalIdePalette.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(palette.editorBg)
            .border(0.5.dp, palette.border, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = palette.textMuted, fontSize = 12.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = palette.textPrimary, fontSize = 12.sp),
            cursorBrush = SolidColor(palette.accent),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
