package com.waa.assistant.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.waa.assistant.data.model.ConversationEntity
import com.waa.assistant.data.model.LogEntity
import com.waa.assistant.data.model.MessageEntity
import com.waa.assistant.data.model.ReplyJobEntity
import com.waa.assistant.data.model.KnowledgeEntry

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        ReplyJobEntity::class,
        LogEntity::class,
        KnowledgeEntry::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversations(): ConversationDao
    abstract fun messages(): MessageDao
    abstract fun jobs(): JobDao
    abstract fun logs(): LogDao
    abstract fun knowledge(): KnowledgeDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "waa.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
        }
    }
}
