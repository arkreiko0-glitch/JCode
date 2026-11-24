package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExtensionDao {
    @Query("SELECT * FROM extensions ORDER BY isInstalled DESC, name ASC")
    fun observeAll(): Flow<List<ExtensionEntity>>

    @Query("SELECT * FROM extensions")
    suspend fun getAllOnce(): List<ExtensionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ExtensionEntity>)

    @Update
    suspend fun update(item: ExtensionEntity)
}

@Dao
interface AiKeyProfileDao {
    @Query("SELECT * FROM ai_key_profiles ORDER BY priorityOrder ASC, id ASC")
    fun observeAll(): Flow<List<AiKeyProfileEntity>>

    @Query("SELECT * FROM ai_key_profiles ORDER BY priorityOrder ASC, id ASC")
    suspend fun getAllOnce(): List<AiKeyProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: AiKeyProfileEntity): Long

    @Update
    suspend fun update(profile: AiKeyProfileEntity)

    @Query("DELETE FROM ai_key_profiles WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface AiMemoryNoteDao {
    @Query("SELECT * FROM ai_memory_notes ORDER BY isPinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<AiMemoryNoteEntity>>

    @Query("SELECT * FROM ai_memory_notes ORDER BY isPinned DESC, updatedAt DESC")
    suspend fun getAllOnce(): List<AiMemoryNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: AiMemoryNoteEntity): Long

    @Update
    suspend fun update(note: AiMemoryNoteEntity)

    @Query("DELETE FROM ai_memory_notes WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface AiSkillDao {
    @Query("SELECT * FROM ai_skills ORDER BY isEnabled DESC, isBuiltIn DESC, name ASC")
    fun observeAll(): Flow<List<AiSkillEntity>>

    @Query("SELECT * FROM ai_skills")
    suspend fun getAllOnce(): List<AiSkillEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(skills: List<AiSkillEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(skill: AiSkillEntity): Long

    @Update
    suspend fun update(skill: AiSkillEntity)

    @Query("DELETE FROM ai_skills WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface AiChatMessageDao {
    @Query("SELECT * FROM ai_chat_messages ORDER BY timestamp ASC, id ASC")
    fun observeAll(): Flow<List<AiChatMessageEntity>>

    @Query("SELECT * FROM ai_chat_messages ORDER BY timestamp ASC, id ASC")
    suspend fun getAllOnce(): List<AiChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: AiChatMessageEntity): Long

    @Query("DELETE FROM ai_chat_messages")
    suspend fun clearAll()
}

@Dao
interface WorkspaceSnapshotDao {
    @Query("SELECT * FROM workspace_snapshots ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<WorkspaceSnapshotEntity>>

    @Query("SELECT * FROM workspace_snapshots ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): WorkspaceSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: WorkspaceSnapshotEntity): Long
}
