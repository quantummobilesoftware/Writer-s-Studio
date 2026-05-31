package com.example.data.local

import androidx.room.*
import com.example.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE isInTrash = 0 AND isArchived = 0 ORDER BY updatedAt DESC")
    fun getAllActiveProjectsFlow(): Flow<List<WorkspaceProject>>

    @Query("SELECT * FROM projects WHERE isArchived = 1 AND isInTrash = 0 ORDER BY updatedAt DESC")
    fun getArchivedProjectsFlow(): Flow<List<WorkspaceProject>>

    @Query("SELECT * FROM projects WHERE isInTrash = 1 ORDER BY updatedAt DESC")
    fun getTrashProjectsFlow(): Flow<List<WorkspaceProject>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: Long): WorkspaceProject?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: WorkspaceProject): Long

    @Update
    suspend fun updateProject(project: WorkspaceProject)

    @Query("UPDATE projects SET isInTrash = 1, updatedAt = :timestamp WHERE id = :projectId")
    suspend fun sendToTrash(projectId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE projects SET isInTrash = 0, updatedAt = :timestamp WHERE id = :projectId")
    suspend fun restoreFromTrash(projectId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun hardDeleteProject(projectId: Long)

    @Query("SELECT * FROM projects")
    suspend fun getAllProjectsDirect(): List<WorkspaceProject>
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE projectId = :projectId ORDER BY name ASC")
    fun getFoldersForProjectFlow(projectId: Long): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE id = :folderId LIMIT 1")
    suspend fun getFolderById(folderId: Long): Folder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: Folder): Long

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun deleteFolder(folderId: Long)

    @Query("SELECT * FROM folders")
    suspend fun getAllFoldersDirect(): List<Folder>
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE projectId = :projectId AND folderId IS NULL ORDER BY sortOrder ASC, createdAt ASC")
    fun getRootDocumentsFlow(projectId: Long): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE projectId = :projectId AND folderId = :folderId ORDER BY sortOrder ASC, createdAt ASC")
    fun getDocumentsByFolderFlow(projectId: Long, folderId: Long): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE projectId = :projectId ORDER BY sortOrder ASC")
    fun getAllProjectDocuments(projectId: Long): List<Document>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: Long): Document?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: Document): Long

    @Update
    suspend fun updateDocument(document: Document)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocument(id: Long)

    @Query("SELECT * FROM documents")
    suspend fun getAllDocumentsDirect(): List<Document>
}

@Dao
interface DocumentHistoryDao {
    @Query("SELECT * FROM document_history WHERE documentId = :documentId ORDER BY timestamp DESC")
    fun getHistoryForDocumentFlow(documentId: Long): Flow<List<DocumentHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: DocumentHistory): Long

    @Query("DELETE FROM document_history WHERE id = :id")
    suspend fun deleteHistory(id: Long)

    @Query("SELECT * FROM document_history")
    suspend fun getAllHistoryDirect(): List<DocumentHistory>
}

@Dao
interface PrompterSettingsDao {
    @Query("SELECT * FROM prompter_settings WHERE documentId = :documentId LIMIT 1")
    suspend fun getPrompterSettings(documentId: Long): PrompterSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePrompterSettings(settings: PrompterSettings)

    @Query("SELECT * FROM prompter_settings")
    suspend fun getAllPrompterSettingsDirect(): List<PrompterSettings>
}

@Dao
interface StatsDao {
    @Query("SELECT * FROM productivity_stats ORDER BY dateString DESC")
    fun getAllStatsFlow(): Flow<List<ProductivityStat>>

    @Query("SELECT * FROM productivity_stats WHERE dateString = :dateString LIMIT 1")
    suspend fun getStatForDate(dateString: String): ProductivityStat?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStat(stat: ProductivityStat)

    @Query("SELECT * FROM productivity_stats")
    suspend fun getAllStatsDirect(): List<ProductivityStat>
}

@Dao
interface SettingsDao {
    @Query("SELECT configValue FROM app_settings WHERE configKey = :key LIMIT 1")
    suspend fun getSetting(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSetting(setting: AppSetting)

    @Query("SELECT * FROM app_settings")
    suspend fun getAllSettingsDirect(): List<AppSetting>
}
