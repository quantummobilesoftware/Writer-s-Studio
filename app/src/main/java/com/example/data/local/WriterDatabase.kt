package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.entity.*

@Database(
    entities = [
        WorkspaceProject::class,
        Folder::class,
        Document::class,
        DocumentHistory::class,
        PrompterSettings::class,
        ProductivityStat::class,
        AppSetting::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class WriterDatabase : RoomDatabase() {
    
    abstract fun projectDao(): ProjectDao
    abstract fun folderDao(): FolderDao
    abstract fun documentDao(): DocumentDao
    abstract fun documentHistoryDao(): DocumentHistoryDao
    abstract fun prompterSettingsDao(): PrompterSettingsDao
    abstract fun statsDao(): StatsDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var INSTANCE: WriterDatabase? = null

        fun getDatabase(context: Context): WriterDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WriterDatabase::class.java,
                    "writer_studio_database"
                )
                .fallbackToDestructiveMigration() // ensures safety during rapid development
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
