package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.WorkspaceNode
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalIdePalette

data class PaletteCommand(
    val id: String,
    val title: String,
    val category: String,
    val shortcut: String = "",
    val onRun: () -> Unit
)

@Composable
fun CommandPaletteDialog(
    workspaceFiles: List<WorkspaceNode>,
    commands: List<PaletteCommand>,
    onOpenFile: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalIdePalette.current
    var query by remember { mutableStateOf(">") }

    val isCommandMode = query.startsWith(">")
    val searchTerm = query.removePrefix(">").trim()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = palette.surfaceElevated,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, palette.accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.editorBg)
                        .border(0.5.dp, palette.border, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = palette.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = palette.textPrimary,
                            fontSize = 13.sp,
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        cursorBrush = SolidColor(palette.accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Tip: Prefix with '>' for IDE commands, or type a filename to open",
                    color = palette.textMuted,
                    fontSize = 10.sp
                )
                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isCommandMode) {
                        val filtered = commands.filter {
                            searchTerm.isEmpty() ||
                                it.title.contains(searchTerm, true) ||
                                it.category.contains(searchTerm, true)
                        }
                        items(filtered, key = { it.id }) { cmd ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(palette.panelBg)
                                    .clickable {
                                        cmd.onRun()
                                        onDismiss()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = palette.accent, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${cmd.category}: ${cmd.title}", color = palette.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                                if (cmd.shortcut.isNotEmpty()) {
                                    Text(cmd.shortcut, color = palette.textMuted, fontSize = 10.sp, fontFamily = JetBrainsMonoFontFamily)
                                }
                            }
                        }
                    } else {
                        val files = workspaceFiles.filter { !it.isDirectory && (searchTerm.isEmpty() || it.relativePath.contains(searchTerm, true)) }
                        items(files, key = { it.relativePath }) { node ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(palette.panelBg)
                                    .clickable {
                                        onOpenFile(node.relativePath)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, tint = palette.accent, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(node.relativePath, color = palette.textPrimary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
