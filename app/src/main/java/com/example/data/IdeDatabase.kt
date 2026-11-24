package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ExtensionEntity::class,
        AiKeyProfileEntity::class,
        AiMemoryNoteEntity::class,
        AiSkillEntity::class,
        AiChatMessageEntity::class,
        WorkspaceSnapshotEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class IdeDatabase : RoomDatabase() {
    abstract fun extensionDao(): ExtensionDao
    abstract fun aiKeyProfileDao(): AiKeyProfileDao
    abstract fun aiMemoryNoteDao(): AiMemoryNoteDao
    abstract fun aiSkillDao(): AiSkillDao
    abstract fun aiChatMessageDao(): AiChatMessageDao
    abstract fun workspaceSnapshotDao(): WorkspaceSnapshotDao

    companion object {
        @Volatile
        private var INSTANCE: IdeDatabase? = null

        fun getInstance(context: Context): IdeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    IdeDatabase::class.java,
                    "zenncode_ide.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
