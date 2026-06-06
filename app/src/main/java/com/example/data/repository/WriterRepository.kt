package com.example.data.repository

import android.content.Context
import com.example.data.entity.*
import com.example.data.local.WriterDatabase
import com.example.data.models.EditorBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WriterRepository(private val db: WriterDatabase) {

    private val projectDao = db.projectDao()
    private val folderDao = db.folderDao()
    private val documentDao = db.documentDao()
    private val historyDao = db.documentHistoryDao()
    private val prompterDao = db.prompterSettingsDao()
    private val statsDao = db.statsDao()
    private val settingsDao = db.settingsDao()

    // --- Projects ---
    fun getActiveProjectsFlow(ownerEmail: String): Flow<List<WorkspaceProject>> = projectDao.getAllActiveProjectsFlow(ownerEmail)
    fun getArchivedProjectsFlow(ownerEmail: String): Flow<List<WorkspaceProject>> = projectDao.getArchivedProjectsFlow(ownerEmail)
    fun getTrashProjectsFlow(ownerEmail: String): Flow<List<WorkspaceProject>> = projectDao.getTrashProjectsFlow(ownerEmail)

    suspend fun getProjectById(id: Long): WorkspaceProject? = withContext(Dispatchers.IO) {
        projectDao.getProjectById(id)
    }

    suspend fun createProject(title: String, type: String, colorHex: String, password: String? = null, ownerEmail: String = "local"): Long = withContext(Dispatchers.IO) {
        val allProjs = projectDao.getAllProjectsDirect()
        val maxSort = allProjs.maxOfOrNull { it.sortOrder } ?: 0
        val proj = WorkspaceProject(
            title = title,
            type = type,
            colorHex = colorHex,
            passwordHash = password,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            sortOrder = maxSort + 1,
            ownerEmail = ownerEmail
        )
        projectDao.insertProject(proj)
    }

    suspend fun updateProject(project: WorkspaceProject) = withContext(Dispatchers.IO) {
        projectDao.updateProject(project.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun sendToTrash(projectId: Long) = withContext(Dispatchers.IO) {
        projectDao.sendToTrash(projectId)
    }

    suspend fun restoreFromTrash(projectId: Long) = withContext(Dispatchers.IO) {
        projectDao.restoreFromTrash(projectId)
    }

    suspend fun permanentDeleteProject(projectId: Long) = withContext(Dispatchers.IO) {
        projectDao.hardDeleteProject(projectId)
    }

    // --- Folders ---
    fun getFoldersForProject(projectId: Long): Flow<List<Folder>> = folderDao.getFoldersForProjectFlow(projectId)

    suspend fun createFolder(projectId: Long, name: String, parentFolderId: Long? = null): Long = withContext(Dispatchers.IO) {
        val f = Folder(projectId = projectId, name = name, parentFolderId = parentFolderId)
        folderDao.insertFolder(f)
    }

    suspend fun deleteFolder(folderId: Long) = withContext(Dispatchers.IO) {
        folderDao.deleteFolder(folderId)
    }

    suspend fun updateFolder(folder: Folder) = withContext(Dispatchers.IO) {
        folderDao.updateFolder(folder)
    }

    // --- Documents ---
    fun getRootDocuments(projectId: Long): Flow<List<Document>> = documentDao.getRootDocumentsFlow(projectId)
    fun getDocumentsByFolder(projectId: Long, folderId: Long): Flow<List<Document>> = documentDao.getDocumentsByFolderFlow(projectId, folderId)

    suspend fun getDocumentById(id: Long): Document? = withContext(Dispatchers.IO) {
        documentDao.getDocumentById(id)
    }

    suspend fun createDocument(projectId: Long, folderId: Long?, title: String, content: List<EditorBlock> = emptyList(), isPlainText: Boolean = false): Long = withContext(Dispatchers.IO) {
        val converters = com.example.data.local.Converters()
        val d = Document(
            projectId = projectId,
            folderId = folderId,
            title = title,
            contentBlocksJson = converters.fromBlocksList(content),
            isPlainText = isPlainText,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        documentDao.insertDocument(d)
    }

    suspend fun updateDocument(document: Document, statsIncrementWords: Int = 0, statsIncrementChars: Int = 0) = withContext(Dispatchers.IO) {
        documentDao.updateDocument(document.copy(updatedAt = System.currentTimeMillis()))
        if (statsIncrementWords > 0 || statsIncrementChars > 0) {
            logProductivity(statsIncrementWords, statsIncrementChars)
        }
    }

    suspend fun deleteDocument(id: Long) = withContext(Dispatchers.IO) {
        documentDao.deleteDocument(id)
    }

    // --- Document History ---
    fun getHistoryForDocument(documentId: Long): Flow<List<DocumentHistory>> = historyDao.getHistoryForDocumentFlow(documentId)

    suspend fun saveHistorySnapshot(documentId: Long, contentBlocksJson: String, description: String) = withContext(Dispatchers.IO) {
        val h = DocumentHistory(
            documentId = documentId,
            contentBlocksJson = contentBlocksJson,
            snapshotName = description,
            timestamp = System.currentTimeMillis()
        )
        historyDao.insertHistory(h)
    }

    suspend fun deleteHistory(id: Long) = withContext(Dispatchers.IO) {
        historyDao.deleteHistory(id)
    }

    // --- Prompter Settings ---
    suspend fun getPrompterSettings(documentId: Long): PrompterSettings = withContext(Dispatchers.IO) {
        prompterDao.getPrompterSettings(documentId) ?: PrompterSettings(documentId = documentId)
    }

    suspend fun savePrompterSettings(settings: PrompterSettings) = withContext(Dispatchers.IO) {
        prompterDao.savePrompterSettings(settings)
    }

    // --- Security Settings ---
    suspend fun isAppPinSet(): Boolean = withContext(Dispatchers.IO) {
        settingsDao.getSetting("app_pin") != null
    }

    suspend fun getAppPin(): String? = withContext(Dispatchers.IO) {
        settingsDao.getSetting("app_pin")
    }

    suspend fun setAppPin(pin: String?) = withContext(Dispatchers.IO) {
        if (pin.isNullOrEmpty()) {
            settingsDao.saveSetting(AppSetting("pin_enabled", "false"))
            settingsDao.saveSetting(AppSetting("app_pin", ""))
        } else {
            settingsDao.saveSetting(AppSetting("pin_enabled", "true"))
            settingsDao.saveSetting(AppSetting("app_pin", pin))
        }
    }

    suspend fun isPinEnabled(): Boolean = withContext(Dispatchers.IO) {
        settingsDao.getSetting("pin_enabled") == "true"
    }

    suspend fun getAppLanguage(): String = withContext(Dispatchers.IO) {
        settingsDao.getSetting("app_language") ?: "system"
    }

    suspend fun saveAppLanguage(lang: String) = withContext(Dispatchers.IO) {
        settingsDao.saveSetting(AppSetting("app_language", lang))
    }

    suspend fun getThemeMode(): String = withContext(Dispatchers.IO) {
        settingsDao.getSetting("theme_mode") ?: "DARK"
    }

    suspend fun saveThemeMode(theme: String) = withContext(Dispatchers.IO) {
        settingsDao.saveSetting(AppSetting("theme_mode", theme))
    }

    suspend fun getColorPalette(): String = withContext(Dispatchers.IO) {
        settingsDao.getSetting("color_palette") ?: "GREY"
    }

    suspend fun saveColorPalette(palette: String) = withContext(Dispatchers.IO) {
        settingsDao.saveSetting(AppSetting("color_palette", palette))
    }

    suspend fun getInterfaceStyle(): String = withContext(Dispatchers.IO) {
        settingsDao.getSetting("interface_style") ?: "PIXEL"
    }

    suspend fun saveInterfaceStyle(style: String) = withContext(Dispatchers.IO) {
        settingsDao.saveSetting(AppSetting("interface_style", style))
    }

    suspend fun getAuthorName(): String = withContext(Dispatchers.IO) {
        settingsDao.getSetting("author_name") ?: "Писатель"
    }

    suspend fun saveAuthorName(name: String) = withContext(Dispatchers.IO) {
        settingsDao.saveSetting(AppSetting("author_name", name))
    }

    suspend fun getAuthorBio(): String = withContext(Dispatchers.IO) {
        settingsDao.getSetting("author_bio") ?: "Вдохновение рождается во время работы."
    }

    suspend fun saveAuthorBio(bio: String) = withContext(Dispatchers.IO) {
        settingsDao.saveSetting(AppSetting("author_bio", bio))
    }

    suspend fun getAuthorEmail(): String = withContext(Dispatchers.IO) {
        settingsDao.getSetting("author_email") ?: ""
    }

    suspend fun saveAuthorEmail(email: String) = withContext(Dispatchers.IO) {
        settingsDao.saveSetting(AppSetting("author_email", email))
    }

    suspend fun getLocalAuthorName(): String = withContext(Dispatchers.IO) {
        settingsDao.getSetting("local_author_name") ?: "Писатель"
    }

    suspend fun saveLocalAuthorName(name: String) = withContext(Dispatchers.IO) {
        settingsDao.saveSetting(AppSetting("local_author_name", name))
    }

    suspend fun getLocalAuthorBio(): String = withContext(Dispatchers.IO) {
        settingsDao.getSetting("local_author_bio") ?: "Вдохновение рождается во время работы."
    }

    suspend fun saveLocalAuthorBio(bio: String) = withContext(Dispatchers.IO) {
        settingsDao.saveSetting(AppSetting("local_author_bio", bio))
    }

    suspend fun getLocalAuthorAvatar(): String = withContext(Dispatchers.IO) {
        settingsDao.getSetting("local_author_avatar") ?: ""
    }

    suspend fun saveLocalAuthorAvatar(path: String) = withContext(Dispatchers.IO) {
        settingsDao.saveSetting(AppSetting("local_author_avatar", path))
    }

    suspend fun getAuthorAvatar(): String = withContext(Dispatchers.IO) {
        settingsDao.getSetting("author_avatar") ?: ""
    }

    suspend fun saveAuthorAvatar(path: String) = withContext(Dispatchers.IO) {
        settingsDao.saveSetting(AppSetting("author_avatar", path))
    }

    suspend fun getCloudSyncEnabled(): Boolean = withContext(Dispatchers.IO) {
        settingsDao.getSetting("cloud_sync_enabled") == "true"
    }

    suspend fun saveCloudSyncEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        settingsDao.saveSetting(AppSetting("cloud_sync_enabled", enabled.toString()))
    }

    suspend fun getGoogleUserId(): String = withContext(Dispatchers.IO) {
        settingsDao.getSetting("google_user_id") ?: ""
    }

    suspend fun saveGoogleUserId(id: String) = withContext(Dispatchers.IO) {
        settingsDao.saveSetting(AppSetting("google_user_id", id))
    }

    suspend fun getPendingSyncProjectIds(): Set<Long> = withContext(Dispatchers.IO) {
        val str = settingsDao.getSetting("sync_pending_pids") ?: ""
        if (str.isEmpty()) return@withContext emptySet()
        str.split(",").mapNotNull { it.toLongOrNull() }.toSet()
    }

    suspend fun savePendingSyncProjectIds(ids: Set<Long>) = withContext(Dispatchers.IO) {
        val str = ids.joinToString(",")
        settingsDao.saveSetting(AppSetting("sync_pending_pids", str))
    }

    // --- Productivity Statistics ---
    val statsFlow: Flow<List<ProductivityStat>> = statsDao.getAllStatsFlow()

    private suspend fun logProductivity(words: Int, chars: Int) {
        val dateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val existing = statsDao.getStatForDate(dateString)
        if (existing != null) {
            statsDao.insertStat(
                existing.copy(
                    wordsCount = existing.wordsCount + words,
                    charsCount = existing.charsCount + chars,
                    minutesSpent = existing.minutesSpent + 1 // Add a default productivity minute
                )
            )
        } else {
            statsDao.insertStat(
                ProductivityStat(
                    dateString = dateString,
                    wordsCount = words,
                    charsCount = chars,
                    minutesSpent = 1
                )
            )
        }
    }

    // --- Backup & Restore ---
    suspend fun exportBackupJson(context: Context): String = withContext(Dispatchers.IO) {
        val backupObj = JSONObject()
        backupObj.put("backup_version", 1)
        backupObj.put("timestamp", System.currentTimeMillis())

        val projectsArr = JSONArray()
        for (item in projectDao.getAllProjectsDirect()) {
            projectsArr.put(JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("type", item.type)
                put("colorHex", item.colorHex)
                put("isFavorite", item.isFavorite)
                put("isArchived", item.isArchived)
                put("isInTrash", item.isInTrash)
                put("passwordHash", item.passwordHash ?: "")
                put("createdAt", item.createdAt)
                put("updatedAt", item.updatedAt)
                put("sortOrder", item.sortOrder)
                put("ownerEmail", item.ownerEmail)
            })
        }
        backupObj.put("projects", projectsArr)

        val foldersArr = JSONArray()
        for (item in folderDao.getAllFoldersDirect()) {
            foldersArr.put(JSONObject().apply {
                put("id", item.id)
                put("projectId", item.projectId)
                put("name", item.name)
                put("parentFolderId", item.parentFolderId ?: -1L)
                put("createdAt", item.createdAt)
                put("sortOrder", item.sortOrder)
            })
        }
        backupObj.put("folders", foldersArr)

        val docsArr = JSONArray()
        for (item in documentDao.getAllDocumentsDirect()) {
            docsArr.put(JSONObject().apply {
                put("id", item.id)
                put("projectId", item.projectId)
                put("folderId", item.folderId ?: -1L)
                put("title", item.title)
                put("contentBlocksJson", item.contentBlocksJson)
                put("sortOrder", item.sortOrder)
                put("passwordHash", item.passwordHash ?: "")
                put("isPlainText", item.isPlainText)
                put("createdAt", item.createdAt)
                put("updatedAt", item.updatedAt)
            })
        }
        backupObj.put("documents", docsArr)

        val historyArr = JSONArray()
        for (item in historyDao.getAllHistoryDirect()) {
            historyArr.put(JSONObject().apply {
                put("id", item.id)
                put("documentId", item.documentId)
                put("contentBlocksJson", item.contentBlocksJson)
                put("snapshotName", item.snapshotName)
                put("timestamp", item.timestamp)
            })
        }
        backupObj.put("history", historyArr)

        val prompterArr = JSONArray()
        for (item in prompterDao.getAllPrompterSettingsDirect()) {
            prompterArr.put(JSONObject().apply {
                put("documentId", item.documentId)
                put("scrollSpeed", item.scrollSpeed.toDouble())
                put("fontSize", item.fontSize.toDouble())
                put("fontFamily", item.fontFamily)
                put("textColorHex", item.textColorHex)
                put("bgColorHex", item.bgColorHex)
                put("mirrorHorizontal", item.mirrorHorizontal)
                put("mirrorVertical", item.mirrorVertical)
            })
        }
        backupObj.put("prompter_settings", prompterArr)

        val statsArr = JSONArray()
        for (item in statsDao.getAllStatsDirect()) {
            statsArr.put(JSONObject().apply {
                put("dateString", item.dateString)
                put("wordsCount", item.wordsCount)
                put("charsCount", item.charsCount)
                put("minutesSpent", item.minutesSpent)
            })
        }
        backupObj.put("stats", statsArr)

        val settingsArr = JSONArray()
        for (item in settingsDao.getAllSettingsDirect()) {
            settingsArr.put(JSONObject().apply {
                put("configKey", item.configKey)
                put("configValue", item.configValue)
            })
        }
        backupObj.put("settings", settingsArr)

        backupObj.toString()
    }

    suspend fun importBackupJson(backupData: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(backupData)
            val version = root.optInt("backup_version", 0)
            if (version == 0) return@withContext false

            // Restore Projects
            if (root.has("projects")) {
                val arr = root.getJSONArray("projects")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val proj = WorkspaceProject(
                        id = obj.getLong("id"),
                        title = obj.getString("title"),
                        type = obj.optString("type", "BOOK"),
                        colorHex = obj.optString("colorHex", "#6200EE"),
                        isFavorite = obj.optBoolean("isFavorite", false),
                        isArchived = obj.optBoolean("isArchived", false),
                        isInTrash = obj.optBoolean("isInTrash", false),
                        passwordHash = obj.optString("passwordHash").ifEmpty { null },
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                        sortOrder = obj.optInt("sortOrder", 0),
                        ownerEmail = obj.optString("ownerEmail", "local")
                    )
                    projectDao.insertProject(proj)
                }
            }

            // Restore Folders
            if (root.has("folders")) {
                val arr = root.getJSONArray("folders")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val parentIdRaw = obj.optLong("parentFolderId", -1L)
                    val f = Folder(
                        id = obj.getLong("id"),
                        projectId = obj.getLong("projectId"),
                        name = obj.getString("name"),
                        parentFolderId = if (parentIdRaw == -1L) null else parentIdRaw,
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        sortOrder = obj.optInt("sortOrder", 0)
                    )
                    folderDao.insertFolder(f)
                }
            }

            // Restore Documents
            if (root.has("documents")) {
                val arr = root.getJSONArray("documents")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val folderIdRaw = obj.optLong("folderId", -1L)
                    val contentBlocks = obj.getString("contentBlocksJson")
                    val isPlainTextImported = if (obj.has("isPlainText")) {
                        obj.optBoolean("isPlainText", false)
                    } else {
                        // Older backup file fallback: auto-detect if it was a plain text document
                        try {
                            val blocksArray = org.json.JSONArray(contentBlocks)
                            if (blocksArray.length() <= 1) {
                                if (blocksArray.length() == 0) {
                                    true
                                } else {
                                    val firstBlock = blocksArray.getJSONObject(0)
                                    val blockType = firstBlock.optString("type", "paragraph")
                                    blockType == "paragraph"
                                }
                            } else {
                                false
                            }
                        } catch (e: Exception) {
                            false
                        }
                    }
                    val d = Document(
                        id = obj.getLong("id"),
                        projectId = obj.getLong("projectId"),
                        folderId = if (folderIdRaw == -1L) null else folderIdRaw,
                        title = obj.getString("title"),
                        contentBlocksJson = contentBlocks,
                        sortOrder = obj.optInt("sortOrder", 0),
                        passwordHash = obj.optString("passwordHash").ifEmpty { null },
                        isPlainText = isPlainTextImported,
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                    documentDao.insertDocument(d)
                }
            }

            // Restore History
            if (root.has("history")) {
                val arr = root.getJSONArray("history")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val h = DocumentHistory(
                        id = obj.getLong("id"),
                        documentId = obj.getLong("documentId"),
                        contentBlocksJson = obj.getString("contentBlocksJson"),
                        snapshotName = obj.getString("snapshotName"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                    historyDao.insertHistory(h)
                }
            }

            // Restore Prompter Settings
            if (root.has("prompter_settings")) {
                val arr = root.getJSONArray("prompter_settings")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val s = PrompterSettings(
                        documentId = obj.getLong("documentId"),
                        scrollSpeed = obj.optDouble("scrollSpeed", 10.0).toFloat(),
                        fontSize = obj.optDouble("fontSize", 24.0).toFloat(),
                        fontFamily = obj.optString("fontFamily", "SANS_SERIF"),
                        textColorHex = obj.optString("textColorHex", "#FFFFFF"),
                        bgColorHex = obj.optString("bgColorHex", "#000000"),
                        mirrorHorizontal = obj.optBoolean("mirrorHorizontal", false),
                        mirrorVertical = obj.optBoolean("mirrorVertical", false)
                    )
                    prompterDao.savePrompterSettings(s)
                }
            }

            // Restore Stats
            if (root.has("stats")) {
                val arr = root.getJSONArray("stats")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val s = ProductivityStat(
                        dateString = obj.getString("dateString"),
                        wordsCount = obj.optInt("wordsCount", 0),
                        charsCount = obj.optInt("charsCount", 0),
                        minutesSpent = obj.optInt("minutesSpent", 0)
                    )
                    statsDao.insertStat(s)
                }
            }

            // Restore Settings
            if (root.has("settings")) {
                val arr = root.getJSONArray("settings")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val s = AppSetting(
                        configKey = obj.getString("configKey"),
                        configValue = obj.getString("configValue")
                    )
                    settingsDao.saveSetting(s)
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun getAllProjectsDirectList(): List<WorkspaceProject> = projectDao.getAllProjectsDirect()
    suspend fun getAllFoldersDirectList(): List<Folder> = folderDao.getAllFoldersDirect()
    suspend fun getAllDocumentsDirectList(): List<Document> = documentDao.getAllDocumentsDirect()

    // --- Google Drive Synchronization Helpers ---
    suspend fun getProjectUuid(projectId: Long): String = withContext(Dispatchers.IO) {
        val existing = settingsDao.getSetting("drive_uuid_$projectId")
        if (!existing.isNullOrEmpty()) return@withContext existing
        val newUuid = java.util.UUID.randomUUID().toString()
        settingsDao.saveSetting(AppSetting("drive_uuid_$projectId", newUuid))
        newUuid
    }

    suspend fun getProjectIdByUuid(uuid: String): Long? = withContext(Dispatchers.IO) {
        val settings = settingsDao.getAllSettingsDirect()
        val matched = settings.firstOrNull { it.configKey.startsWith("drive_uuid_") && it.configValue == uuid } ?: return@withContext null
        matched.configKey.removePrefix("drive_uuid_").toLongOrNull()
    }

    suspend fun serializeProjectToJson(projectId: Long): String = withContext(Dispatchers.IO) {
        val project = getProjectById(projectId) ?: return@withContext ""
        val folders = folderDao.getAllFoldersDirect().filter { it.projectId == projectId }
        val documents = documentDao.getAllProjectDocuments(projectId)
        val uuid = getProjectUuid(projectId)

        val root = JSONObject()
        root.put("uuid", uuid)
        root.put("title", project.title)
        root.put("type", project.type)
        root.put("colorHex", project.colorHex)
        root.put("isFavorite", project.isFavorite)
        root.put("isArchived", project.isArchived)
        root.put("isInTrash", project.isInTrash)
        root.put("passwordHash", project.passwordHash ?: "")
        root.put("createdAt", project.createdAt)
        root.put("updatedAt", project.updatedAt)
        root.put("sortOrder", project.sortOrder)
        root.put("ownerEmail", project.ownerEmail)

        // Folders
        val foldersArr = JSONArray()
        for (f in folders) {
            foldersArr.put(JSONObject().apply {
                put("id", f.id)
                put("name", f.name)
                put("parentFolderId", f.parentFolderId ?: -1L)
                put("createdAt", f.createdAt)
                put("sortOrder", f.sortOrder)
            })
        }
        root.put("folders", foldersArr)

        // Documents
        val docsArr = JSONArray()
        for (d in documents) {
            docsArr.put(JSONObject().apply {
                put("id", d.id)
                put("folderId", d.folderId ?: -1L)
                put("title", d.title)
                put("contentBlocksJson", d.contentBlocksJson)
                put("sortOrder", d.sortOrder)
                put("passwordHash", d.passwordHash ?: "")
                put("isPlainText", d.isPlainText)
                put("createdAt", d.createdAt)
                put("updatedAt", d.updatedAt)
            })
        }
        root.put("documents", docsArr)

        root.toString()
    }

    suspend fun importProjectFromJson(jsonString: String, ownerEmail: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            val uuid = root.getString("uuid")
            val title = root.getString("title")
            val type = root.optString("type", "BOOK")
            val colorHex = root.optString("colorHex", "#6200EE")
            val isFavorite = root.optBoolean("isFavorite", false)
            val isArchived = root.optBoolean("isArchived", false)
            val isInTrash = root.optBoolean("isInTrash", false)
            val passwordHash = root.optString("passwordHash").ifEmpty { null }
            val createdAt = root.optLong("createdAt", System.currentTimeMillis())
            val updatedAt = root.optLong("updatedAt", System.currentTimeMillis())
            val sortOrder = root.optInt("sortOrder", 0)
            val ownerEmailToUse = ownerEmail ?: root.optString("ownerEmail", "local")

            val existingProjectId = getProjectIdByUuid(uuid)
            val localProjectId: Long

            if (existingProjectId != null) {
                val existingProj = getProjectById(existingProjectId)
                if (existingProj != null) {
                    if (existingProj.updatedAt >= updatedAt) {
                        android.util.Log.d("WriterRepository", "Local project $existingProjectId is up-to-date or newer. Skipping import.")
                        return@withContext true
                    }

                    val updatedProj = existingProj.copy(
                        title = title,
                        type = type,
                        colorHex = colorHex,
                        isFavorite = isFavorite,
                        isArchived = isArchived,
                        isInTrash = isInTrash,
                        passwordHash = passwordHash,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                        sortOrder = sortOrder,
                        ownerEmail = ownerEmailToUse
                    )
                    projectDao.insertProject(updatedProj)
                    localProjectId = existingProjectId

                    val existingFolders = folderDao.getAllFoldersDirect().filter { it.projectId == localProjectId }
                    for (f in existingFolders) {
                        folderDao.deleteFolder(f.id)
                    }
                    val existingDocs = documentDao.getAllProjectDocuments(localProjectId)
                    for (d in existingDocs) {
                        documentDao.deleteDocument(d.id)
                    }
                } else {
                    settingsDao.saveSetting(AppSetting("drive_uuid_$existingProjectId", ""))
                    val proj = WorkspaceProject(
                        title = title,
                        type = type,
                        colorHex = colorHex,
                        isFavorite = isFavorite,
                        isArchived = isArchived,
                        isInTrash = isInTrash,
                        passwordHash = passwordHash,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                        sortOrder = sortOrder,
                        ownerEmail = ownerEmailToUse
                    )
                    localProjectId = projectDao.insertProject(proj)
                    settingsDao.saveSetting(AppSetting("drive_uuid_$localProjectId", uuid))
                }
            } else {
                val proj = WorkspaceProject(
                    title = title,
                    type = type,
                    colorHex = colorHex,
                    isFavorite = isFavorite,
                    isArchived = isArchived,
                    isInTrash = isInTrash,
                    passwordHash = passwordHash,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    sortOrder = sortOrder,
                    ownerEmail = ownerEmailToUse
                )
                localProjectId = projectDao.insertProject(proj)
                settingsDao.saveSetting(AppSetting("drive_uuid_$localProjectId", uuid))
            }

            // Folders import list
            val foldersArr = root.optJSONArray("folders") ?: JSONArray()
            val folderIdMap = mutableMapOf<Long, Long>()

            val rawFolders = mutableListOf<JSONObject>()
            for (i in 0 until foldersArr.length()) {
                rawFolders.add(foldersArr.getJSONObject(i))
            }

            var progress = true
            while (rawFolders.isNotEmpty() && progress) {
                progress = false
                val iterator = rawFolders.iterator()
                while (iterator.hasNext()) {
                    val fObj = iterator.next()
                    val originalId = fObj.getLong("id")
                    val parentIdRaw = fObj.getLong("parentFolderId")

                    val hasParent = parentIdRaw != -1L
                    val isParentInserted = !hasParent || folderIdMap.containsKey(parentIdRaw)

                    if (isParentInserted) {
                        val resolvedParentId = if (hasParent) folderIdMap[parentIdRaw] else null
                        val newFolder = Folder(
                            projectId = localProjectId,
                            name = fObj.getString("name"),
                            parentFolderId = resolvedParentId,
                            createdAt = fObj.optLong("createdAt", System.currentTimeMillis()),
                            sortOrder = fObj.optInt("sortOrder", 0)
                        )
                        val insertedFolderId = folderDao.insertFolder(newFolder)
                        folderIdMap[originalId] = insertedFolderId
                        iterator.remove()
                        progress = true
                    }
                }

                if (!progress && rawFolders.isNotEmpty()) {
                    val fObj = rawFolders.removeAt(0)
                    val originalId = fObj.getLong("id")
                    val newFolder = Folder(
                        projectId = localProjectId,
                        name = fObj.getString("name"),
                        parentFolderId = null,
                        createdAt = fObj.optLong("createdAt", System.currentTimeMillis()),
                        sortOrder = fObj.optInt("sortOrder", 0)
                    )
                    val insertedFolderId = folderDao.insertFolder(newFolder)
                    folderIdMap[originalId] = insertedFolderId
                    progress = true
                }
            }

            // Documents import list
            val docsArr = root.optJSONArray("documents") ?: JSONArray()
            for (i in 0 until docsArr.length()) {
                val dObj = docsArr.getJSONObject(i)
                val originalFolderId = dObj.getLong("folderId")
                val resolvedFolderId = if (originalFolderId != -1L) folderIdMap[originalFolderId] else null

                val newDoc = Document(
                    projectId = localProjectId,
                    folderId = resolvedFolderId,
                    title = dObj.getString("title"),
                    contentBlocksJson = dObj.getString("contentBlocksJson"),
                    sortOrder = dObj.optInt("sortOrder", 0),
                    passwordHash = dObj.optString("passwordHash").ifEmpty { null },
                    isPlainText = dObj.optBoolean("isPlainText", false),
                    createdAt = dObj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = dObj.optLong("updatedAt", System.currentTimeMillis())
                )
                documentDao.insertDocument(newDoc)
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun cleanUpSyncedProjectsOnLogout() = withContext(Dispatchers.IO) {
        try {
            val settings = settingsDao.getAllSettingsDirect()
            val syncedProjectKeys = settings.filter { it.configKey.startsWith("drive_uuid_") }
            for (setting in syncedProjectKeys) {
                val projectId = setting.configKey.removePrefix("drive_uuid_").toLongOrNull() ?: continue
                val uuid = setting.configValue
                if (uuid.isNotEmpty()) {
                    // Delete everything related to this projectId

                    // 1. Delete documents, histories
                    val docs = documentDao.getAllProjectDocuments(projectId)
                    for (doc in docs) {
                        try {
                            val histories = historyDao.getAllHistoryDirect().filter { it.documentId == doc.id }
                            for (h in histories) {
                                historyDao.deleteHistory(h.id)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        documentDao.deleteDocument(doc.id)
                    }

                    // 2. Delete folders
                    val folders = folderDao.getAllFoldersDirect().filter { it.projectId == projectId }
                    for (f in folders) {
                        folderDao.deleteFolder(f.id)
                    }

                    // 3. Delete the project itself
                    projectDao.hardDeleteProject(projectId)

                    // 4. Delete the setting mapping and sync ticks
                    settingsDao.deleteSetting(setting.configKey)
                    settingsDao.deleteSetting("last_sync_local_tick_$projectId")
                    settingsDao.deleteSetting("last_sync_remote_tick_$projectId")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getLastSyncLocal(projectId: Long): Long = withContext(Dispatchers.IO) {
        settingsDao.getSetting("last_sync_local_tick_$projectId")?.toLongOrNull() ?: 0L
    }

    suspend fun getLastSyncRemote(projectId: Long): Long = withContext(Dispatchers.IO) {
        settingsDao.getSetting("last_sync_remote_tick_$projectId")?.toLongOrNull() ?: 0L
    }

    suspend fun saveSyncTicks(projectId: Long, localTick: Long, remoteTick: Long) = withContext(Dispatchers.IO) {
        settingsDao.saveSetting(AppSetting("last_sync_local_tick_$projectId", localTick.toString()))
        settingsDao.saveSetting(AppSetting("last_sync_remote_tick_$projectId", remoteTick.toString()))
    }
}

