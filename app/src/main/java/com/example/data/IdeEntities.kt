package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "extensions")
data class ExtensionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val publisher: String,
    val category: String, // "LANGUAGE", "LINTER", "TOOL"
    val languageId: String,
    val version: String,
    val description: String,
    val fileExtensions: String, // comma-separated, e.g. ".py,.pyw"
    val sizeLabel: String,
    val sampleTemplate: String,
    val isInstalled: Boolean,
    val isEnabled: Boolean,
    val downloadsLabel: String
)

@Entity(tableName = "ai_key_profiles")
data class AiKeyProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val provider: String, // AiProviderType.name
    val baseUrl: String,
    val modelId: String,
    val apiKey: String,
    val priorityOrder: Int,
    val isEnabled: Boolean = true,
    val failureCount: Int = 0,
    val lastStatusMessage: String = "Ready",
    val lastUsedAt: Long = 0L
)

@Entity(tableName = "ai_memory_notes")
data class AiMemoryNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val isPinned: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_skills")
data class AiSkillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val systemPrompt: String,
    val isEnabled: Boolean = true,
    val isBuiltIn: Boolean = false
)

@Entity(tableName = "ai_chat_messages")
data class AiChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // "USER", "ASSISTANT", "ROLLBACK"
    val content: String,
    val modelUsed: String = "",
    val keyLabelUsed: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "workspace_snapshots")
data class WorkspaceSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val commitHash: String,
    val message: String,
    val filesJson: String,
    val fileCount: Int,
    val timestamp: Long = System.currentTimeMillis()
)
