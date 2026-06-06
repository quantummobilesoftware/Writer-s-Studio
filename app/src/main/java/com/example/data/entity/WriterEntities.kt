package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class WorkspaceProject(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: String = "BOOK", // SCREENPLAY, BOOK, STORY, TEXT
    val colorHex: String = "#6200EE", // custom project dynamic colors
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val isInTrash: Boolean = false,
    val passwordHash: String? = null, // password dynamic block layer
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0,
    val ownerEmail: String = "local"
)

@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val name: String,
    val parentFolderId: Long? = null, // Supports subfolders
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0
)

@Entity(tableName = "documents")
data class Document(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val folderId: Long? = null, // null means root of the project
    val title: String,
    val contentBlocksJson: String = "[]", // Serialized List<EditorBlock>
    val sortOrder: Int = 0,
    val passwordHash: String? = null, // document specific password lock
    val isPlainText: Boolean = false, // false for Pro (blocks), true for Basic (TXT editor)
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "document_history")
data class DocumentHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val contentBlocksJson: String,
    val snapshotName: String, // e.g. "Auto-save 12:45" or "Draft V1"
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "prompter_settings")
data class PrompterSettings(
    @PrimaryKey val documentId: Long,
    val scrollSpeed: Float = 10f, // 1 to 50 speed factor
    val fontSize: Float = 24f, // sp text size
    val fontFamily: String = "SANS_SERIF", // SANS_SERIF, SERIF, MONOSPACE, CURSIVE
    val textColorHex: String = "#FFFFFF",
    val bgColorHex: String = "#000000",
    val mirrorHorizontal: Boolean = false,
    val mirrorVertical: Boolean = false
)

@Entity(tableName = "productivity_stats")
data class ProductivityStat(
    @PrimaryKey val dateString: String, // "YYYY-MM-DD" style key
    val wordsCount: Int = 0,
    val charsCount: Int = 0,
    val minutesSpent: Int = 0
)

@Entity(tableName = "app_settings")
data class AppSetting(
    @PrimaryKey val configKey: String, // e.g. "app_pin", "pin_enabled", "theme_preference"
    val configValue: String
)
