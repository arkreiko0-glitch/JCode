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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AiChatMessageEntity
import com.example.data.AiKeyProfileEntity
import com.example.data.AiMemoryNoteEntity
import com.example.data.AiProviderType
import com.example.data.AiSkillEntity
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalIdePalette

private enum class AiSubTab(val label: String) {
    CHAT("Chat"),
    KEYS("API Keys"),
    MEMORY("Memory"),
    SKILLS("Skills")
}

@Composable
fun AiAssistantPanel(
    chatMessages: List<AiChatMessageEntity>,
    keyProfiles: List<AiKeyProfileEntity>,
    memoryNotes: List<AiMemoryNoteEntity>,
    skills: List<AiSkillEntity>,
    isSending: Boolean,
    statusBanner: String?,
    testingKeyId: Long?,
    keyTestResult: String?,
    onSendPrompt: (String, Boolean) -> Unit,
    onApplyCodeToEditor: (String) -> Unit,
    onClearChat: () -> Unit,
    onAddKey: (String, AiProviderType, String, String, String) -> Unit,
    onToggleKeyEnabled: (AiKeyProfileEntity) -> Unit,
    onMoveKeyPriority: (AiKeyProfileEntity, Boolean) -> Unit,
    onDeleteKey: (Long) -> Unit,
    onTestKey: (AiKeyProfileEntity) -> Unit,
    onAddMemory: (String, String) -> Unit,
    onToggleMemoryPin: (AiMemoryNoteEntity) -> Unit,
    onDeleteMemory: (Long) -> Unit,
    onAddSkill: (String, String, String) -> Unit,
    onToggleSkill: (AiSkillEntity) -> Unit,
    onDeleteSkill: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalIdePalette.current
    var subTab by remember { mutableStateOf(AiSubTab.CHAT) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.sidebarBg)
    ) {
        // Header & Sub-Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.tabBarBg)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = palette.accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("AI STUDIO", color = palette.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${keyProfiles.count { it.isEnabled }} keys",
                color = palette.accent,
                fontSize = 10.sp
            )
            Spacer(Modifier.weight(1f))
            if (subTab == AiSubTab.CHAT) {
                IconButton(onClick = onClearChat, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Chat", tint = palette.textMuted, modifier = Modifier.size(15.dp))
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(0.5.dp, palette.border)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AiSubTab.entries.forEach { tab ->
                val active = tab == subTab
                Surface(
                    color = if (active) palette.accent else palette.surfaceElevated,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { subTab = tab }
                        .testTag("ai_tab_${tab.name.lowercase()}")
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab.label,
                            color = if (active) palette.onAccent else palette.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        when (subTab) {
            AiSubTab.CHAT -> AiChatTab(
                messages = chatMessages,
                hasEnabledKeys = keyProfiles.any { it.isEnabled && it.apiKey.isNotBlank() },
                activeSkillsCount = skills.count { it.isEnabled },
                memoryCount = memoryNotes.size,
                isSending = isSending,
                statusBanner = statusBanner,
                onSendPrompt = onSendPrompt,
                onApplyCode = onApplyCodeToEditor,
                onOpenKeysTab = { subTab = AiSubTab.KEYS },
                modifier = Modifier.weight(1f)
            )
            AiSubTab.KEYS -> AiKeysPoolTab(
                profiles = keyProfiles,
                testingKeyId = testingKeyId,
                keyTestResult = keyTestResult,
                onAddKey = onAddKey,
                onToggleEnabled = onToggleKeyEnabled,
                onMovePriority = onMoveKeyPriority,
                onDelete = onDeleteKey,
                onTestKey = onTestKey,
                modifier = Modifier.weight(1f)
            )
            AiSubTab.MEMORY -> AiMemoryTab(
                notes = memoryNotes,
                onAdd = onAddMemory,
                onTogglePin = onToggleMemoryPin,
                onDelete = onDeleteMemory,
                modifier = Modifier.weight(1f)
            )
            AiSubTab.SKILLS -> AiSkillsTab(
                skills = skills,
                onAdd = onAddSkill,
                onToggle = onToggleSkill,
                onDelete = onDeleteSkill,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AiChatTab(
    messages: List<AiChatMessageEntity>,
    hasEnabledKeys: Boolean,
    activeSkillsCount: Int,
    memoryCount: Int,
    isSending: Boolean,
    statusBanner: String?,
    onSendPrompt: (String, Boolean) -> Unit,
    onApplyCode: (String) -> Unit,
    onOpenKeysTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalIdePalette.current
    val clipboard = LocalClipboardManager.current
    var prompt by remember { mutableStateOf("") }
    var includeContext by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (!hasEnabledKeys) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.warningColor.copy(alpha = 0.16f))
                    .clickable { onOpenKeysTab() }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Key, contentDescription = null, tint = palette.warningColor, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Add any AI API Key (Gemini, OpenAI, Claude, Groq...) -> Tap here",
                    color = palette.warningColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (!statusBanner.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.surfaceElevated)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = palette.accent, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(statusBanner, color = palette.textMuted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.surfaceElevated)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Multi-Model Auto-Rollback Ready", color = palette.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Add unlimited API keys for any AI model. If key #1 hits rate limits or fails, Zenncode automatically rolls back to key #2 while keeping the exact same conversation, $memoryCount shared memory note(s), and $activeSkillsCount active skill(s).",
                            color = palette.textPrimary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            items(messages, key = { it.id }) { msg ->
                when (msg.role) {
                    "ROLLBACK" -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(palette.warningColor.copy(alpha = 0.14f))
                                .border(0.5.dp, palette.warningColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = palette.warningColor, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(msg.content, color = palette.warningColor, fontSize = 11.sp)
                        }
                    }
                    "USER" -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.accent.copy(alpha = 0.18f))
                                .padding(9.dp)
                        ) {
                            Text("YOU", color = palette.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(2.dp))
                            Text(msg.content, color = palette.textPrimary, fontSize = 12.sp)
                        }
                    }
                    else -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.surfaceElevated)
                                .padding(9.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${msg.keyLabelUsed.ifBlank { "AI" }} • ${msg.modelUsed}",
                                    color = palette.successColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(msg.content, color = palette.textPrimary, fontSize = 12.sp, lineHeight = 17.sp)

                            val codeBlock = extractFirstCodeBlock(msg.content)
                            if (codeBlock != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        color = palette.accent,
                                        shape = RoundedCornerShape(5.dp),
                                        modifier = Modifier.clickable { onApplyCode(codeBlock) }
                                    ) {
                                        Text(
                                            text = "Apply to Editor",
                                            color = palette.onAccent,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Surface(
                                        color = palette.panelBg,
                                        shape = RoundedCornerShape(5.dp),
                                        modifier = Modifier.clickable {
                                            clipboard.setText(AnnotatedString(codeBlock))
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = palette.textPrimary, modifier = Modifier.size(11.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Copy Code", color = palette.textPrimary, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Composer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.panelBg)
                .border(0.5.dp, palette.border)
                .padding(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Text(
                    text = "Active file context • $activeSkillsCount skills • $memoryCount memory",
                    color = palette.textMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = if (includeContext) palette.accent.copy(alpha = 0.2f) else palette.surfaceElevated,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.clickable { includeContext = !includeContext }
                ) {
                    Text(
                        text = if (includeContext) "Ctx ON" else "Ctx OFF",
                        color = if (includeContext) palette.accent else palette.textMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SidebarInputField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    placeholder = "Ask AI (any model, auto-rollback)...",
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = palette.accent)
                } else {
                    IconButton(
                        onClick = {
                            if (prompt.isNotBlank()) {
                                onSendPrompt(prompt, includeContext)
                                prompt = ""
                            }
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.accent)
                            .testTag("ai_send_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send AI Prompt", tint = palette.onAccent, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

private fun extractFirstCodeBlock(markdown: String): String? {
    val start = markdown.indexOf("```")
    if (start == -1) return null
    val lineEnd = markdown.indexOf('\n', start + 3)
    if (lineEnd == -1) return null
    val end = markdown.indexOf("```", lineEnd + 1)
    if (end == -1) return null
    return markdown.substring(lineEnd + 1, end).trim()
}

@Composable
private fun AiKeysPoolTab(
    profiles: List<AiKeyProfileEntity>,
    testingKeyId: Long?,
    keyTestResult: String?,
    onAddKey: (String, AiProviderType, String, String, String) -> Unit,
    onToggleEnabled: (AiKeyProfileEntity) -> Unit,
    onMovePriority: (AiKeyProfileEntity, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
    onTestKey: (AiKeyProfileEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalIdePalette.current
    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Auto-Rollback Key Pool", color = palette.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Priority #1 runs first; fails over down the list automatically.", color = palette.textMuted, fontSize = 10.sp)
            }
            Surface(
                color = palette.accent,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .clickable { showAddDialog = true }
                    .testTag("add_ai_key_button")
            ) {
                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = palette.onAccent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Add Key", color = palette.onAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (!keyTestResult.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = keyTestResult,
                color = palette.infoColor,
                fontSize = 11.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(palette.infoColor.copy(alpha = 0.14f))
                    .padding(7.dp)
            )
        }

        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(profiles, key = { it.id }) { profile ->
                val priorityNum = profiles.indexOf(profile) + 1
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.surfaceElevated)
                        .border(0.5.dp, palette.border, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(palette.accent.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("#$priorityNum", color = palette.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(profile.label, color = palette.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("${profile.provider} • ${profile.modelId}", color = palette.textMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Switch(checked = profile.isEnabled, onCheckedChange = { onToggleEnabled(profile) })
                    }
                    Text(
                        text = "Key: ••••${profile.apiKey.takeLast(4)}  |  ${profile.lastStatusMessage}",
                        color = if (profile.failureCount > 0) palette.warningColor else palette.textMuted,
                        fontSize = 10.sp,
                        fontFamily = JetBrainsMonoFontFamily,
                        maxLines = 2
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ArrowUpward,
                            contentDescription = "Move Up",
                            tint = palette.textMuted,
                            modifier = Modifier.size(18.dp).clickable { onMovePriority(profile, true) }
                        )
                        Icon(
                            Icons.Default.ArrowDownward,
                            contentDescription = "Move Down",
                            tint = palette.textMuted,
                            modifier = Modifier.size(18.dp).clickable { onMovePriority(profile, false) }
                        )
                        Surface(
                            color = palette.panelBg,
                            shape = RoundedCornerShape(5.dp),
                            modifier = Modifier.clickable { onTestKey(profile) }
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (testingKeyId == profile.id) {
                                    CircularProgressIndicator(modifier = Modifier.size(11.dp), strokeWidth = 1.5.dp, color = palette.accent)
                                } else {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = palette.successColor, modifier = Modifier.size(12.dp))
                                }
                                Spacer(Modifier.width(3.dp))
                                Text("Test", color = palette.textPrimary, fontSize = 10.sp)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Key",
                            tint = palette.errorColor,
                            modifier = Modifier.size(17.dp).clickable { onDelete(profile.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddAiKeyDialog(
            onDismiss = { showAddDialog = false },
            onSave = { label, provider, baseUrl, model, key ->
                onAddKey(label, provider, baseUrl, model, key)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AddAiKeyDialog(
    onDismiss: () -> Unit,
    onSave: (String, AiProviderType, String, String, String) -> Unit
) {
    val palette = LocalIdePalette.current
    var provider by remember { mutableStateOf(AiProviderType.GEMINI) }
    var label by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf(provider.defaultBaseUrl) }
    var modelId by remember { mutableStateOf(provider.defaultModel) }
    var apiKey by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceElevated,
        titleContentColor = palette.textPrimary,
        textContentColor = palette.textPrimary,
        title = { Text("Add AI Model & API Key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Provider", fontSize = 11.sp, color = palette.textMuted)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(AiProviderType.entries) { p ->
                        val active = p == provider
                        Surface(
                            color = if (active) palette.accent else palette.panelBg,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.clickable {
                                provider = p
                                baseUrl = p.defaultBaseUrl
                                modelId = p.defaultModel
                            }
                        ) {
                            Text(
                                text = p.displayName,
                                color = if (active) palette.onAccent else palette.textPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
                SidebarInputField(label, { label = it }, "Label (e.g. Primary Flash / Backup GPT)")
                SidebarInputField(modelId, { modelId = it }, "Model ID (any model)")
                SidebarInputField(baseUrl, { baseUrl = it }, "API Base URL")
                SidebarInputField(apiKey, { apiKey = it }, "Paste API Key")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (apiKey.isNotBlank()) {
                    onSave(label, provider, baseUrl, modelId, apiKey)
                }
            }) { Text("Save Key", color = palette.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = palette.textMuted) }
        }
    )
}

@Composable
private fun AiMemoryTab(
    notes: List<AiMemoryNoteEntity>,
    onAdd: (String, String) -> Unit,
    onTogglePin: (AiMemoryNoteEntity) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalIdePalette.current
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Memory, contentDescription = null, tint = palette.accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Shared Memory (Same across ALL models)", color = palette.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        SidebarInputField(title, { title = it }, "Memory Title (e.g. Coding Rules)")
        SidebarInputField(content, { content = it }, "Fact/Preference all AI models must remember...")
        Surface(
            color = palette.accent,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (content.isNotBlank()) {
                        onAdd(title, content)
                        title = ""
                        content = ""
                    }
                }
        ) {
            Box(Modifier.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                Text("Save to Shared Memory", color = palette.onAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(notes, key = { it.id }) { note ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.surfaceElevated)
                        .padding(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(note.title, color = palette.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = "Pin",
                            tint = if (note.isPinned) palette.accent else palette.textMuted,
                            modifier = Modifier.size(15.dp).clickable { onTogglePin(note) }
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = palette.errorColor,
                            modifier = Modifier.size(15.dp).clickable { onDelete(note.id) }
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(note.content, color = palette.textPrimary, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun AiSkillsTab(
    skills: List<AiSkillEntity>,
    onAdd: (String, String, String) -> Unit,
    onToggle: (AiSkillEntity) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalIdePalette.current
    var showCreate by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Psychology, contentDescription = null, tint = palette.accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Shared AI Skills", color = palette.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Surface(
                color = palette.accent,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.clickable { showCreate = true }
            ) {
                Text("+ New Skill", color = palette.onAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(skills, key = { it.id }) { skill ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.surfaceElevated)
                        .padding(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(skill.name, color = palette.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(skill.description, color = palette.textMuted, fontSize = 10.sp)
                        }
                        Switch(checked = skill.isEnabled, onCheckedChange = { onToggle(skill) })
                    }
                    Text(skill.systemPrompt, color = palette.textPrimary.copy(alpha = 0.8f), fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!skill.isBuiltIn) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete Skill",
                                tint = palette.errorColor,
                                modifier = Modifier.size(16.dp).clickable { onDelete(skill.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var prompt by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            containerColor = palette.surfaceElevated,
            titleContentColor = palette.textPrimary,
            title = { Text("Create Custom AI Skill") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SidebarInputField(name, { name = it }, "Skill Name (e.g. FastAPI Architect)")
                    SidebarInputField(desc, { desc = it }, "Short description")
                    SidebarInputField(prompt, { prompt = it }, "System instruction for all AI models")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank() && prompt.isNotBlank()) {
                        onAdd(name, desc, prompt)
                        showCreate = false
                    }
                }) { Text("Create", color = palette.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("Cancel", color = palette.textMuted) }
            }
        )
    }
}
