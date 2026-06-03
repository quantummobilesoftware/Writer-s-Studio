package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.*
import com.example.data.entity.*
import com.example.data.local.WriterDatabase
import com.example.data.models.BlockStyle
import com.example.data.models.EditorBlock
import com.example.data.repository.WriterRepository
import com.example.util.FormatExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WriterViewModel(private val repository: WriterRepository) : ViewModel() {

    // --- Active Projects ---
    val activeProjects: StateFlow<List<WorkspaceProject>> = repository.activeProjectsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val archivedProjects: StateFlow<List<WorkspaceProject>> = repository.archivedProjectsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashProjects: StateFlow<List<WorkspaceProject>> = repository.trashProjectsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val statistics: StateFlow<List<ProductivityStat>> = repository.statsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Search & Filters ---
    private val _projectSearchQuery = MutableStateFlow("")
    val projectSearchQuery: StateFlow<String> = _projectSearchQuery.asStateFlow()

    private val _projectSortBy = MutableStateFlow("MANUAL") // NAME, CREATED, UPDATED, MANUAL
    val projectSortBy: StateFlow<String> = _projectSortBy.asStateFlow()

    private val _workspaceSortBy = MutableStateFlow("MANUAL") // MANUAL, NAME_ASC, NAME_DESC, DATE_CREATED_DESC, DATE_CREATED_ASC, DATE_MODIFIED_DESC
    val workspaceSortBy: StateFlow<String> = _workspaceSortBy.asStateFlow()

    fun setProjectSearchQuery(query: String) {
        _projectSearchQuery.value = query
    }

    fun setProjectSortBy(sortBy: String) {
        _projectSortBy.value = sortBy
    }

    fun setWorkspaceSortBy(sortBy: String) {
        _workspaceSortBy.value = sortBy
    }

    // --- Navigation Selection States ---
    private val _selectedProject = MutableStateFlow<WorkspaceProject?>(null)
    val selectedProject: StateFlow<WorkspaceProject?> = _selectedProject.asStateFlow()

    private val _selectedFolder = MutableStateFlow<Folder?>(null)
    val selectedFolder: StateFlow<Folder?> = _selectedFolder.asStateFlow()

    private val _activeDocument = MutableStateFlow<Document?>(null)
    val activeDocument: StateFlow<Document?> = _activeDocument.asStateFlow()

    private val _appLanguage = MutableStateFlow("system")
    val appLanguage: StateFlow<String> = _appLanguage.asStateFlow()

    private val _themeMode = MutableStateFlow("DARK")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _colorPalette = MutableStateFlow("GREY")
    val colorPalette: StateFlow<String> = _colorPalette.asStateFlow()

    private val _interfaceStyle = MutableStateFlow("PIXEL")
    val interfaceStyle: StateFlow<String> = _interfaceStyle.asStateFlow()

    private val _authorName = MutableStateFlow("Писатель")
    val authorName: StateFlow<String> = _authorName.asStateFlow()

    private val _authorBio = MutableStateFlow("Вдохновение рождается во время работы.")
    val authorBio: StateFlow<String> = _authorBio.asStateFlow()

    private val _authorEmail = MutableStateFlow("")
    val authorEmail: StateFlow<String> = _authorEmail.asStateFlow()

    private val _authorAvatar = MutableStateFlow("")
    val authorAvatar: StateFlow<String> = _authorAvatar.asStateFlow()

    private val _cloudSyncEnabled = MutableStateFlow(false)
    val cloudSyncEnabled: StateFlow<Boolean> = _cloudSyncEnabled.asStateFlow()

    // Loaded folders & documents list
    val currentFolders: StateFlow<List<Folder>> = _selectedProject
        .flatMapLatest { project ->
            if (project == null) flowOf(emptyList())
            else repository.getFoldersForProject(project.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentRootDocuments: StateFlow<List<Document>> = _selectedProject
        .flatMapLatest { project ->
            if (project == null) flowOf(emptyList())
            else repository.getRootDocuments(project.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentFolderDocuments: StateFlow<List<Document>> = combine(_selectedProject, _selectedFolder) { project, folder ->
        Pair(project, folder)
    }
    .flatMapLatest { (project, folder) ->
        if (project == null || folder == null) flowOf(emptyList())
        else repository.getDocumentsByFolder(project.id, folder.id)
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Editor State variables
    private val _editorBlocks = MutableStateFlow<List<EditorBlock>>(emptyList())
    val editorBlocks: StateFlow<List<EditorBlock>> = _editorBlocks.asStateFlow()

    // Undo/Redo Stacks for Editor
    private val undoStack = mutableListOf<List<EditorBlock>>()
    private val redoStack = mutableListOf<List<EditorBlock>>()

    // Document Snapshot history
    val activeDocumentHistory: StateFlow<List<DocumentHistory>> = _activeDocument
        .flatMapLatest { doc ->
            if (doc == null) flowOf(emptyList())
            else repository.getHistoryForDocument(doc.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Prompter Settings
    private val _activePrompterSettings = MutableStateFlow<PrompterSettings?>(null)
    val activePrompterSettings: StateFlow<PrompterSettings?> = _activePrompterSettings.asStateFlow()

    // App Security Configurations
    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private val _isPinConfigured = MutableStateFlow(false)
    val isPinConfigured: StateFlow<Boolean> = _isPinConfigured.asStateFlow()

    private var cachedAppPin: String? = null

    // Verification state for lock keys
    private val _unlockedProjectIds = MutableStateFlow<Set<Long>>(emptySet())
    val unlockedProjectIds: StateFlow<Set<Long>> = _unlockedProjectIds.asStateFlow()

    // Autosave job
    private var autosaveJob: Job? = null

    init {
        checkAppPinStatus(lockIfEnabled = true)
        loadAppLanguage()
    }

    private fun loadAppLanguage() {
        viewModelScope.launch {
            _appLanguage.value = repository.getAppLanguage()
            _themeMode.value = repository.getThemeMode()
            _colorPalette.value = repository.getColorPalette()
            _interfaceStyle.value = repository.getInterfaceStyle()
            _authorName.value = repository.getAuthorName()
            _authorBio.value = repository.getAuthorBio()
            _authorEmail.value = repository.getAuthorEmail()
            _authorAvatar.value = repository.getAuthorAvatar()
            _cloudSyncEnabled.value = repository.getCloudSyncEnabled()
        }
    }

    fun setAuthorAvatar(path: String) {
        viewModelScope.launch {
            repository.saveAuthorAvatar(path)
            _authorAvatar.value = path
        }
    }

    fun setCloudSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.saveCloudSyncEnabled(enabled)
            _cloudSyncEnabled.value = enabled
        }
    }

    fun setAuthorProfile(name: String, bio: String, email: String) {
        viewModelScope.launch {
            repository.saveAuthorName(name)
            repository.saveAuthorBio(bio)
            repository.saveAuthorEmail(email)
            _authorName.value = name
            _authorBio.value = bio
            _authorEmail.value = email
        }
    }

    fun setAppLanguage(lang: String) {
        viewModelScope.launch {
            repository.saveAppLanguage(lang)
            _appLanguage.value = lang
        }
    }

    fun setThemeMode(themeId: String) {
        viewModelScope.launch {
            repository.saveThemeMode(themeId)
            _themeMode.value = themeId
        }
    }

    fun setColorPalette(paletteId: String) {
        viewModelScope.launch {
            repository.saveColorPalette(paletteId)
            _colorPalette.value = paletteId
        }
    }

    fun setInterfaceStyle(styleId: String) {
        viewModelScope.launch {
            repository.saveInterfaceStyle(styleId)
            _interfaceStyle.value = styleId
        }
    }

    // --- App Locks ---
    fun checkAppPinStatus(lockIfEnabled: Boolean = false) {
        viewModelScope.launch {
            val enabled = repository.isPinEnabled()
            cachedAppPin = repository.getAppPin()
            _isPinConfigured.value = enabled
            if (lockIfEnabled) {
                _isAppLocked.value = enabled
            }
        }
    }

    fun verifyAppPin(pin: String): Boolean {
        val isCorrect = cachedAppPin == pin
        if (isCorrect) {
            _isAppLocked.value = false
        }
        return isCorrect
    }

    fun setupAppPin(pin: String?) {
        viewModelScope.launch {
            repository.setAppPin(pin)
            checkAppPinStatus(lockIfEnabled = false)
        }
    }

    fun lockApp() {
        if (_isPinConfigured.value) {
            _isAppLocked.value = true
        }
    }

    // --- Projects Management ---
    fun selectProject(project: WorkspaceProject?) {
        _selectedProject.value = project
        _selectedFolder.value = null
        _activeDocument.value = null
    }

    fun verifyProjectPassword(project: WorkspaceProject, pass: String): Boolean {
        return if (project.passwordHash == pass) {
            _unlockedProjectIds.value = _unlockedProjectIds.value + project.id
            true
        } else false
    }

    fun createProject(title: String, type: String, colorHex: String, password: String? = null) {
        viewModelScope.launch {
            repository.createProject(title, type, colorHex, password)
        }
    }

    fun updateProject(project: WorkspaceProject) {
        viewModelScope.launch {
            repository.updateProject(project)
        }
    }

    fun toggleFavoriteProject(project: WorkspaceProject) {
        viewModelScope.launch {
            repository.updateProject(project.copy(isFavorite = !project.isFavorite))
        }
    }

    fun toggleArchiveProject(project: WorkspaceProject) {
        viewModelScope.launch {
            repository.updateProject(project.copy(isArchived = !project.isArchived))
        }
    }

    fun sendToTrash(project: WorkspaceProject) {
        viewModelScope.launch {
            repository.sendToTrash(project.id)
        }
    }

    fun restoreFromTrash(project: WorkspaceProject) {
        viewModelScope.launch {
            repository.restoreFromTrash(project.id)
        }
    }

    fun permanentDeleteProject(project: WorkspaceProject) {
        viewModelScope.launch {
            repository.permanentDeleteProject(project.id)
        }
    }

    fun renameProject(project: WorkspaceProject, newTitle: String) {
        viewModelScope.launch {
            if (newTitle.isNotBlank()) {
                repository.updateProject(project.copy(title = newTitle))
            }
        }
    }

    fun moveProject(project: WorkspaceProject, up: Boolean, currentSubTab: String) {
        viewModelScope.launch {
            val rawList = when (currentSubTab) {
                "ARCHIVE" -> archivedProjects.value
                "TRASH" -> trashProjects.value
                "FAVORITES" -> activeProjects.value.filter { it.isFavorite }
                else -> activeProjects.value
            }
            val currentList = rawList.toMutableList()
            val index = currentList.indexOfFirst { it.id == project.id }
            if (index == -1) return@launch
            
            val targetIndex = if (up) index - 1 else index + 1
            if (targetIndex in 0 until currentList.size) {
                for (i in currentList.indices) {
                    currentList[i] = currentList[i].copy(sortOrder = i)
                }
                val temp = currentList[index]
                currentList[index] = currentList[targetIndex].copy(sortOrder = index)
                currentList[targetIndex] = temp.copy(sortOrder = targetIndex)
                
                for (p in currentList) {
                    repository.updateProject(p)
                }
            }
        }
    }

    // --- Folders Management ---
    fun selectFolder(folder: Folder?) {
        _selectedFolder.value = folder
        _activeDocument.value = null
    }

    fun createFolder(name: String) {
        val project = _selectedProject.value ?: return
        viewModelScope.launch {
            repository.createFolder(project.id, name, _selectedFolder.value?.id)
        }
    }

    fun deleteFolder(folderId: Long) {
        viewModelScope.launch {
            repository.deleteFolder(folderId)
            if (_selectedFolder.value?.id == folderId) {
                _selectedFolder.value = null
            }
        }
    }

    fun moveFolder(folder: Folder, up: Boolean) {
        viewModelScope.launch {
            val parentId = folder.parentFolderId
            val foldersInLevel = currentFolders.value
                .filter { it.parentFolderId == parentId }
                .sortedBy { it.sortOrder }
                .toMutableList()
            
            val index = foldersInLevel.indexOfFirst { it.id == folder.id }
            if (index == -1) return@launch
            
            val targetIndex = if (up) index - 1 else index + 1
            if (targetIndex in 0 until foldersInLevel.size) {
                for (i in foldersInLevel.indices) {
                    foldersInLevel[i] = foldersInLevel[i].copy(sortOrder = i)
                }
                val temp = foldersInLevel[index]
                foldersInLevel[index] = foldersInLevel[targetIndex].copy(sortOrder = index)
                foldersInLevel[targetIndex] = temp.copy(sortOrder = targetIndex)
                
                for (f in foldersInLevel) {
                    repository.updateFolder(f)
                }
            }
        }
    }

    fun moveDocument(document: Document, up: Boolean) {
        viewModelScope.launch {
            val list = if (document.folderId == null) {
                currentRootDocuments.value
            } else {
                currentFolderDocuments.value
            }.sortedBy { it.sortOrder }.toMutableList()
            
            val index = list.indexOfFirst { it.id == document.id }
            if (index == -1) return@launch
            
            val targetIndex = if (up) index - 1 else index + 1
            if (targetIndex in 0 until list.size) {
                for (i in list.indices) {
                    list[i] = list[i].copy(sortOrder = i)
                }
                val temp = list[index]
                list[index] = list[targetIndex].copy(sortOrder = index)
                list[targetIndex] = temp.copy(sortOrder = targetIndex)
                
                for (d in list) {
                    repository.updateDocument(d)
                }
            }
        }
    }

    fun renameFolder(folder: Folder, newName: String) {
        viewModelScope.launch {
            if (newName.isNotBlank()) {
                repository.updateFolder(folder.copy(name = newName))
            }
        }
    }

    fun renameDocumentInWorkspace(document: Document, newTitle: String) {
        viewModelScope.launch {
            if (newTitle.isNotBlank()) {
                val updated = document.copy(title = newTitle, updatedAt = System.currentTimeMillis())
                repository.updateDocument(updated)
                if (_activeDocument.value?.id == document.id) {
                    _activeDocument.value = updated
                }
            }
        }
    }

    // --- Documents Management & Editor Core ---
    fun selectDocument(document: Document?) {
        _activeDocument.value = document
        undoStack.clear()
        redoStack.clear()
        if (document == null) {
            _editorBlocks.value = emptyList()
            _activePrompterSettings.value = null
        } else {
            val converters = com.example.data.local.Converters()
            _editorBlocks.value = converters.toBlocksList(document.contentBlocksJson)
            
            // Core initial blocks fallback
            if (_editorBlocks.value.isEmpty()) {
                _editorBlocks.value = listOf(EditorBlock(type = "paragraph", text = ""))
            }
            
            // Load Prompter
            viewModelScope.launch {
                _activePrompterSettings.value = repository.getPrompterSettings(document.id)
            }
        }
        autosaveJob?.cancel()
    }

    fun createDocument(title: String, isPlainText: Boolean = false) {
        val project = _selectedProject.value ?: return
        val folder = _selectedFolder.value
        viewModelScope.launch {
            val initialContent = listOf(EditorBlock(type = "paragraph", text = ""))
            val newId = repository.createDocument(project.id, folder?.id, title, initialContent, isPlainText)
            val createdDoc = repository.getDocumentById(newId)
            selectDocument(createdDoc)
        }
    }

    fun updateDocumentTitle(newTitle: String) {
        val doc = _activeDocument.value ?: return
        _activeDocument.value = doc.copy(title = newTitle)
        saveActiveDocumentImmediate()
    }

    fun deleteActiveDocument() {
        val doc = _activeDocument.value ?: return
        viewModelScope.launch {
            repository.deleteDocument(doc.id)
            selectDocument(null)
        }
    }

    fun deleteDocument(id: Long) {
        viewModelScope.launch {
            repository.deleteDocument(id)
            if (_activeDocument.value?.id == id) {
                selectDocument(null)
            }
        }
    }

    fun updateEditorBlocks(newBlocks: List<EditorBlock>, updateHistory: Boolean = true) {
        if (updateHistory) {
            // Keep maximum 50 levels of Undo/Redo to limit memory footprint
            if (undoStack.size >= 50) undoStack.removeAt(0)
            undoStack.add(_editorBlocks.value.toList())
            redoStack.clear()
        }
        _editorBlocks.value = newBlocks
        triggerAutosave()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val previousState = undoStack.removeAt(undoStack.size - 1)
            redoStack.add(_editorBlocks.value.toList())
            _editorBlocks.value = previousState
            saveActiveDocumentImmediate()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val nextState = redoStack.removeAt(redoStack.size - 1)
            undoStack.add(_editorBlocks.value.toList())
            _editorBlocks.value = nextState
            saveActiveDocumentImmediate()
        }
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    private fun triggerAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(2000) // 2s debounce for autosave
            saveActiveDocumentImmediate()
        }
    }

    fun saveActiveDocumentImmediate() {
        val doc = _activeDocument.value ?: return
        val blocks = _editorBlocks.value
        val converters = com.example.data.local.Converters()
        val blocksJson = converters.fromBlocksList(blocks)
        
        // Calculate written counts increments for statistics
        val originalText = converters.toBlocksList(doc.contentBlocksJson).joinToString(" ") { it.text }
        val newText = blocks.joinToString(" ") { it.text }
        
        val origWords = countWords(originalText)
        val newWords = countWords(newText)
        val wordDiffVal = if (newWords > origWords) newWords - origWords else 0
        
        val origChars = originalText.length
        val newChars = newText.length
        val charDiffVal = if (newChars > origChars) newChars - origChars else 0

        viewModelScope.launch {
            val updatedDoc = doc.copy(contentBlocksJson = blocksJson, updatedAt = System.currentTimeMillis())
            repository.updateDocument(updatedDoc, statsIncrementWords = wordDiffVal, statsIncrementChars = charDiffVal)
            if (_activeDocument.value?.id == doc.id) {
                _activeDocument.value = updatedDoc
            }
        }
    }

    // --- Document Versioning Snapshot History ---
    fun createManualSnapshot(name: String) {
        val doc = _activeDocument.value ?: return
        val converters = com.example.data.local.Converters()
        val content = converters.fromBlocksList(_editorBlocks.value)
        viewModelScope.launch {
            repository.saveHistorySnapshot(doc.id, content, name)
        }
    }

    fun restoreSnapshot(snapshot: DocumentHistory) {
        val converters = com.example.data.local.Converters()
        val restoredBlocks = converters.toBlocksList(snapshot.contentBlocksJson)
        updateEditorBlocks(restoredBlocks, updateHistory = true)
        saveActiveDocumentImmediate()
    }

    fun deleteSnapshot(snapshotId: Long) {
        viewModelScope.launch {
            repository.deleteHistory(snapshotId)
        }
    }

    // --- Prompter Settings Management ---
    fun updatePrompterSettings(settings: PrompterSettings) {
        _activePrompterSettings.value = settings
        viewModelScope.launch {
            repository.savePrompterSettings(settings)
        }
    }

    // --- Metrics counting functions ---
    fun getDocumentMetrics(): DocumentMetrics {
        val blocks = _editorBlocks.value
        var characters = 0
        var words = 0
        for (b in blocks) {
            characters += b.text.length
            words += countWords(b.text)
        }
        // Approximate pages: 300 words standard page density
        val pages = Math.max(1, (words / 300.0).toInt())
        // Reading time: standard adult comfortable reading speed is 200 words/min
        val readingTimeMin = Math.max(1, (words / 200.0).toInt())
        return DocumentMetrics(words, characters, pages, readingTimeMin)
    }

    private fun countWords(input: String): Int {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return 0
        return trimmed.split("\\s+".toRegex()).size
    }

    // --- Exports and Imports ---
    fun exportCurrentDocument(context: Context, format: String): File? {
        val doc = _activeDocument.value ?: return null
        val blocks = _editorBlocks.value
        val dir = context.cacheDir
        val fileSuffix = when (format) {
            "PDF" -> ".pdf"
            "DOCX" -> ".docx"
            "RTF" -> ".rtf"
            else -> ".txt"
        }
        val file = File(dir, "${doc.title.replace(" ", "_")}$fileSuffix")
        try {
            val fos = FileOutputStream(file)
            when (format) {
                "PDF" -> FormatExporter.exportToPdf(doc.title, blocks, fos)
                "DOCX" -> FormatExporter.exportToDocx(doc.title, blocks, fos)
                "RTF" -> FormatExporter.exportToRtf(blocks, fos)
                else -> FormatExporter.exportToTxt(blocks, fos)
            }
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun importExternalText(context: Context, uriString: String, fileExtension: String) {
        val project = _selectedProject.value ?: return
        val folder = _selectedFolder.value
        
        viewModelScope.launch {
            try {
                val uri = android.net.Uri.parse(uriString)
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@launch
                
                // Read imported blocks
                val blocks = when (fileExtension.uppercase()) {
                    "DOCX", "HTML" -> FormatExporter.importFromDocx(inputStream)
                    else -> FormatExporter.importFromTxt(inputStream)
                }
                
                var filename = "Импорт ${System.currentTimeMillis()}"
                try {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameCol = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameCol != -1 && cursor.moveToFirst()) {
                            val displayName = cursor.getString(nameCol)
                            if (!displayName.isNullOrBlank()) {
                                filename = displayName.substringBeforeLast(".")
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                val isPlainText = fileExtension.uppercase() == "TXT" || fileExtension.uppercase() == "TEXT"
                repository.createDocument(project.id, folder?.id, filename, blocks, isPlainText = isPlainText)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- Backup & Restore Orchestration ---
    fun runBackup(context: Context, onComplete: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val backupText = repository.exportBackupJson(context)
                onComplete(backupText)
            } catch (e: Exception) {
                onComplete(null)
            }
        }
    }

    fun runRestore(backupText: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = repository.importBackupJson(backupText)
            onComplete(result)
        }
    }
}

data class DocumentMetrics(
    val words: Int,
    val characters: Int,
    val pages: Int,
    val readingTimeMinutes: Int
)

class WriterViewModelFactory(private val repository: WriterRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WriterViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WriterViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
