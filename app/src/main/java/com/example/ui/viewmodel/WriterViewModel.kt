package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.*
import com.example.data.entity.*
import com.example.data.local.WriterDatabase
import com.example.data.models.BlockStyle
import com.example.data.models.EditorBlock
import com.example.data.repository.WriterRepository
import com.example.data.remote.GoogleDriveService
import com.example.util.FormatExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import android.os.Environment
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    private val _googleUserId = MutableStateFlow("")
    val googleUserId: StateFlow<String> = _googleUserId.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private var appContext: Context? = null

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
            _googleUserId.value = repository.getGoogleUserId()
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
            if (enabled) {
                appContext?.let { syncAllWithDrive(it) }
            }
        }
    }

    fun linkGoogleAccount(
        context: Context,
        gId: String,
        displayName: String,
        email: String,
        photoUrl: String
    ) {
        Log.d("GoogleSignIn", "[Google Link Initiated] Linking google account: Email='$email', ID='$gId', Name='$displayName'")
        viewModelScope.launch {
            try {
                repository.saveAuthorName(displayName)
                repository.saveAuthorEmail(email)
                repository.saveAuthorAvatar(photoUrl)
                repository.saveGoogleUserId(gId)
                repository.saveCloudSyncEnabled(true)

                _authorName.value = displayName
                _authorEmail.value = email
                _authorAvatar.value = photoUrl
                _googleUserId.value = gId
                _cloudSyncEnabled.value = true
                Log.d("GoogleSignIn", "[Google Link Saved] Account data successfully stored locally in SQLite settings DB.")

                syncAllWithDrive(context)
            } catch (e: Exception) {
                Log.e("GoogleSignIn", "[Google Link Failed] Error saving account details: ${e.message}", e)
            }
        }
    }

    fun disconnectGoogleAccount(context: Context) {
        Log.d("GoogleSignIn", "[Google Disconnect] Dissociating Google account and disabling sync.")
        viewModelScope.launch {
            try {
                // Sign out Google Client so user picker forces account select next time
                val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                    com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
                ).build()
                val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
                client.signOut().addOnCompleteListener {
                    Log.d("GoogleSignIn", "[Google Disconnect] GoogleSignInClient signOut complete.")
                }
            } catch (e: Exception) {
                Log.e("GoogleSignIn", "[Google Disconnect] Error during Google Play Services client signOut: ${e.message}", e)
            }

            try {
                repository.saveAuthorName("Писатель")
                repository.saveAuthorBio("Вдохновение рождается во время работы.")
                repository.saveAuthorEmail("")
                repository.saveAuthorAvatar("")
                repository.saveGoogleUserId("")
                repository.saveCloudSyncEnabled(false)

                _authorName.value = "Писатель"
                _authorBio.value = "Вдохновение рождается во время работы."
                _authorEmail.value = ""
                _authorAvatar.value = ""
                _googleUserId.value = ""
                _cloudSyncEnabled.value = false
                Log.d("GoogleSignIn", "[Google Disconnect] Stored credentials reset to default offline writer profile.")
            } catch (e: Exception) {
                Log.e("GoogleSignIn", "[Google Disconnect] Error clearing account details: ${e.message}", e)
            }
        }
    }

    fun checkAndRestoreGoogleSession(context: Context) {
        viewModelScope.launch {
            Log.d("GoogleSignIn", "[Session Check] --- Starting startup session check ---")
            val storedUserId = repository.getGoogleUserId()
            val storedEmail = repository.getAuthorEmail()
            val storedName = repository.getAuthorName()
            Log.d("GoogleSignIn", "[Session Check] Stored settings credentials: id='$storedUserId', email='$storedEmail', name='$storedName'")

            // 1. Double check cached auth session using GoogleSignIn API
            try {
                val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
                if (account != null) {
                    val email = account.email ?: ""
                    val displayName = account.displayName ?: "Google User"
                    val photoUrl = account.photoUrl?.toString() ?: ""
                    val gId = account.id ?: ""

                    Log.d("GoogleSignIn", "[Session Check] Found cached GoogleSignIn account session: email='$email', id='$gId'")

                    if (email.isNotEmpty()) {
                        repository.saveAuthorName(displayName)
                        repository.saveAuthorEmail(email)
                        repository.saveAuthorAvatar(photoUrl)
                        repository.saveGoogleUserId(gId)
                        repository.saveCloudSyncEnabled(true)

                        _authorName.value = displayName
                        _authorEmail.value = email
                        _authorAvatar.value = photoUrl
                        _googleUserId.value = gId
                        _cloudSyncEnabled.value = true

                        Log.d("GoogleSignIn", "[Session Check] Session restored successfully from cached Google API account.")
                        return@launch
                    }
                } else {
                    Log.d("GoogleSignIn", "[Session Check] No active cached account found via GoogleSignIn.getLastSignedInAccount.")
                }
            } catch (e: Exception) {
                Log.e("GoogleSignIn", "[Session Check] Exception verifying GoogleSignIn cache: ${e.message}", e)
            }

            // 2. Fallback to Silent Sign-in if we have locally stored credentials but play service cache is cold
            if (storedUserId.isNotEmpty() && storedEmail.isNotEmpty()) {
                Log.d("GoogleSignIn", "[Session Check] Found local DB credentials but cold Play Services cache. Attempting silent sign-in...")
                try {
                    val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                        com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
                    )
                        .requestEmail()
                        .requestProfile()
                        .requestScopes(com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.file"))
                        .build()
                    val signInClient = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
                    signInClient.silentSignIn().addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val silentAccount = task.result
                            Log.d("GoogleSignIn", "[Session Check] Silent Sign-In succeeded for '${silentAccount?.email}'")
                            if (silentAccount != null) {
                                val email = silentAccount.email ?: ""
                                val displayName = silentAccount.displayName ?: "Google User"
                                val photoUrl = silentAccount.photoUrl?.toString() ?: ""
                                val gId = silentAccount.id ?: ""

                                viewModelScope.launch {
                                    repository.saveAuthorName(displayName)
                                    repository.saveAuthorEmail(email)
                                    repository.saveAuthorAvatar(photoUrl)
                                    repository.saveGoogleUserId(gId)
                                    repository.saveCloudSyncEnabled(true)

                                    _authorName.value = displayName
                                    _authorEmail.value = email
                                    _authorAvatar.value = photoUrl
                                    _googleUserId.value = gId
                                    _cloudSyncEnabled.value = true
                                    Log.d("GoogleSignIn", "[Session Check] Local profile successfully synchronized with silenly logged-in Google account.")
                                }
                            }
                        } else {
                            Log.w("GoogleSignIn", "[Session Check] Silent Sign-In failed: ${task.exception?.message}")
                            // Keep DB account anyway (sandbox/offline tolerance)
                            Log.d("GoogleSignIn", "[Session Check] Maintaining DB cached state as fallback to preserve unsynced local content.")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GoogleSignIn", "[Session Check] Silentsignin setup error: ${e.message}", e)
                }
            } else {
                Log.d("GoogleSignIn", "[Session Check] No active Google authorization exists locally or remotely. Staying in offline mode.")
            }
        }
    }

    fun syncAllWithDrive(context: Context) {
        val email = _authorEmail.value
        if (email.isEmpty()) return
        if (!_cloudSyncEnabled.value) return

        viewModelScope.launch {
            if (_isSyncing.value) return@launch
            _isSyncing.value = true
            try {
                Log.d("WriterViewModel", "Starting full sync with Google Drive")
                val folderId = GoogleDriveService.getOrCreateAppFolder(context, email)
                if (folderId == null) {
                    Log.e("WriterViewModel", "Failed to get or create Google Drive app folder")
                    _isSyncing.value = false
                    return@launch
                }

                val remoteFiles = GoogleDriveService.listAllProjectFiles(context, email, folderId)
                val remoteUuids = remoteFiles.map { it.uuid }.toSet()
                val localProjects = repository.getAllProjectsDirectList()

                val localUuidsToPids = mutableMapOf<String, Long>()
                val localPidsToUuids = mutableMapOf<Long, String>()
                for (p in localProjects) {
                    val uuid = repository.getProjectUuid(p.id)
                    localUuidsToPids[uuid] = p.id
                    localPidsToUuids[p.id] = uuid
                }

                for (remoteFile in remoteFiles) {
                    val localPID = localUuidsToPids[remoteFile.uuid]
                    if (localPID != null) {
                        val localProj = repository.getProjectById(localPID)
                        if (localProj != null) {
                            if (remoteFile.modifiedTime > localProj.updatedAt) {
                                Log.d("WriterViewModel", "Remote project ${localProj.title} is newer. Downloading...")
                                val fileContent = GoogleDriveService.downloadFileContent(context, email, remoteFile.fileId)
                                if (fileContent != null) {
                                    repository.importProjectFromJson(fileContent)
                                }
                            } else if (localProj.updatedAt > remoteFile.modifiedTime) {
                                Log.d("WriterViewModel", "Local project ${localProj.title} is newer. Uploading...")
                                val fileContent = repository.serializeProjectToJson(localProj.id)
                                GoogleDriveService.uploadFileMedia(context, email, remoteFile.fileId, fileContent)
                            }
                        }
                    } else {
                        Log.d("WriterViewModel", "Found brand-new remote project with UUID ${remoteFile.uuid}. Downloading...")
                        val fileContent = GoogleDriveService.downloadFileContent(context, email, remoteFile.fileId)
                        if (fileContent != null) {
                            repository.importProjectFromJson(fileContent)
                        }
                    }
                }

                for (localProj in localProjects) {
                    val uuid = localPidsToUuids[localProj.id] ?: continue
                    if (uuid !in remoteUuids) {
                        Log.d("WriterViewModel", "Local project ${localProj.title} has no remote counterpart. Uploading...")
                        val fileContent = repository.serializeProjectToJson(localProj.id)
                        GoogleDriveService.createAndUploadProjectFile(context, email, folderId, uuid, fileContent)
                    }
                }

                repository.savePendingSyncProjectIds(emptySet())
                Log.d("WriterViewModel", "Google Drive sync completed successfully")
            } catch (e: Exception) {
                Log.e("WriterViewModel", "Error in syncAllWithDrive: ${e.message}", e)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun markProjectDirtyAndSync(context: Context, projectId: Long) {
        viewModelScope.launch {
            val pending = repository.getPendingSyncProjectIds().toMutableSet()
            pending.add(projectId)
            repository.savePendingSyncProjectIds(pending)
            syncPendingProjects(context)
        }
    }

    fun syncPendingProjects(context: Context) {
        val email = _authorEmail.value
        if (email.isEmpty() || !_cloudSyncEnabled.value) return

        viewModelScope.launch {
            val pending = repository.getPendingSyncProjectIds()
            if (pending.isEmpty()) return@launch

            val folderId = GoogleDriveService.getOrCreateAppFolder(context, email) ?: return@launch
            val syncedSuccessfully = mutableSetOf<Long>()

            for (pid in pending) {
                try {
                    val uuid = repository.getProjectUuid(pid)
                    val projectContent = repository.serializeProjectToJson(pid)
                    if (projectContent.isEmpty()) {
                        syncedSuccessfully.add(pid)
                        continue
                    }

                    val existingFile = GoogleDriveService.findProjectFile(context, email, folderId, uuid)
                    val success = if (existingFile != null) {
                        GoogleDriveService.uploadFileMedia(context, email, existingFile.first, projectContent)
                    } else {
                        GoogleDriveService.createAndUploadProjectFile(context, email, folderId, uuid, projectContent)
                    }

                    if (success) {
                        syncedSuccessfully.add(pid)
                    }
                } catch (e: Exception) {
                    Log.e("WriterViewModel", "Failed to sync pending project $pid: ${e.message}")
                }
            }

            if (syncedSuccessfully.isNotEmpty()) {
                val updatedPending = repository.getPendingSyncProjectIds().toMutableSet()
                updatedPending.removeAll(syncedSuccessfully)
                repository.savePendingSyncProjectIds(updatedPending)
            }
        }
    }

    private fun triggerProjectSync(projectId: Long) {
        val context = appContext ?: return
        markProjectDirtyAndSync(context, projectId)
    }

    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null

    fun initNetworkListener(context: Context) {
        appContext = context.applicationContext
        val appContextLoc = context.applicationContext
        val cm = appContextLoc.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return

        if (networkCallback != null) return

        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                super.onAvailable(network)
                Log.d("WriterViewModel", "Network restored. Triggering auto-sync of pending queue.")
                syncPendingProjects(appContextLoc)
                syncAllWithDrive(appContextLoc)
            }
        }
        networkCallback = callback
        try {
            val request = android.net.NetworkRequest.Builder()
                .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            e.printStackTrace()
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
            val newId = repository.createProject(title, type, colorHex, password)
            triggerProjectSync(newId)
        }
    }

    fun updateProject(project: WorkspaceProject) {
        viewModelScope.launch {
            repository.updateProject(project)
            triggerProjectSync(project.id)
        }
    }

    fun toggleFavoriteProject(project: WorkspaceProject) {
        viewModelScope.launch {
            repository.updateProject(project.copy(isFavorite = !project.isFavorite))
            triggerProjectSync(project.id)
        }
    }

    fun toggleArchiveProject(project: WorkspaceProject) {
        viewModelScope.launch {
            repository.updateProject(project.copy(isArchived = !project.isArchived))
            triggerProjectSync(project.id)
        }
    }

    fun sendToTrash(project: WorkspaceProject) {
        viewModelScope.launch {
            repository.sendToTrash(project.id)
            triggerProjectSync(project.id)
        }
    }

    fun restoreFromTrash(project: WorkspaceProject) {
        viewModelScope.launch {
            repository.restoreFromTrash(project.id)
            triggerProjectSync(project.id)
        }
    }

    fun permanentDeleteProject(project: WorkspaceProject) {
        viewModelScope.launch {
            val uuid = repository.getProjectUuid(project.id)
            repository.permanentDeleteProject(project.id)
            
            val context = appContext
            val email = _authorEmail.value
            if (context != null && email.isNotEmpty() && _cloudSyncEnabled.value) {
                val folderId = GoogleDriveService.getOrCreateAppFolder(context, email)
                if (folderId != null) {
                    val fileInfo = GoogleDriveService.findProjectFile(context, email, folderId, uuid)
                    if (fileInfo != null) {
                        GoogleDriveService.deleteFile(context, email, fileInfo.first)
                    }
                }
            }
        }
    }

    fun renameProject(project: WorkspaceProject, newTitle: String) {
        viewModelScope.launch {
            if (newTitle.isNotBlank()) {
                repository.updateProject(project.copy(title = newTitle))
                triggerProjectSync(project.id)
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
                triggerProjectSync(project.id)
            }
        }
    }

    // --- Folders Management ---
    fun selectFolder(folder: Folder?) {
        _selectedFolder.value = folder
        _activeDocument.value = null
    }

    fun navigateBackFolder() {
        val current = _selectedFolder.value
        if (current != null) {
            val parentId = current.parentFolderId
            if (parentId == null) {
                _selectedFolder.value = null
            } else {
                val parent = currentFolders.value.find { it.id == parentId }
                _selectedFolder.value = parent
            }
        }
    }

    fun createFolder(name: String) {
        val project = _selectedProject.value ?: return
        viewModelScope.launch {
            repository.createFolder(project.id, name, _selectedFolder.value?.id)
            triggerProjectSync(project.id)
        }
    }

    fun deleteFolder(folderId: Long) {
        val projectId = _selectedProject.value?.id
        viewModelScope.launch {
            repository.deleteFolder(folderId)
            if (_selectedFolder.value?.id == folderId) {
                _selectedFolder.value = null
            }
            projectId?.let { triggerProjectSync(it) }
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
                triggerProjectSync(folder.projectId)
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
                triggerProjectSync(document.projectId)
            }
        }
    }

    fun renameFolder(folder: Folder, newName: String) {
        viewModelScope.launch {
            if (newName.isNotBlank()) {
                repository.updateFolder(folder.copy(name = newName))
                triggerProjectSync(folder.projectId)
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
                triggerProjectSync(document.projectId)
            }
        }
    }

    // --- Documents Management & Editor Core ---
    fun selectDocument(document: Document?) {
        _activeDocument.value = document
        undoStack.clear()
        redoStack.clear()
        updateCanUndoRedo()
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
            triggerProjectSync(project.id)
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
            triggerProjectSync(doc.projectId)
        }
    }

    fun deleteDocument(id: Long) {
        viewModelScope.launch {
            val doc = repository.getDocumentById(id)
            repository.deleteDocument(id)
            if (_activeDocument.value?.id == id) {
                selectDocument(null)
            }
            doc?.let { triggerProjectSync(it.projectId) }
        }
    }

    private var activeTypingUndoState: List<EditorBlock>? = null
    private var typingUndoJob: Job? = null

    fun updateEditorBlocks(newBlocks: List<EditorBlock>, updateHistory: Boolean = true) {
        if (updateHistory) {
            typingUndoJob?.cancel()
            typingUndoJob = null
            activeTypingUndoState = null

            // Keep maximum 50 levels of Undo/Redo to limit memory footprint
            if (undoStack.size >= 50) undoStack.removeAt(0)
            undoStack.add(_editorBlocks.value.toList())
            redoStack.clear()
        } else {
            // Coalesced typing undo logic
            if (activeTypingUndoState == null) {
                activeTypingUndoState = _editorBlocks.value.toList()
                if (undoStack.size >= 50) undoStack.removeAt(0)
                undoStack.add(_editorBlocks.value.toList())
                redoStack.clear()
            }
            typingUndoJob?.cancel()
            typingUndoJob = viewModelScope.launch {
                delay(1200) // Finish typing session after 1.2s of inactivity
                activeTypingUndoState = null
            }
        }
        _editorBlocks.value = newBlocks
        triggerAutosave()
        updateCanUndoRedo()
    }

    fun undo() {
        typingUndoJob?.cancel()
        typingUndoJob = null
        activeTypingUndoState = null

        if (undoStack.isNotEmpty()) {
            val previousState = undoStack.removeAt(undoStack.size - 1)
            redoStack.add(_editorBlocks.value.toList())
            _editorBlocks.value = previousState
            saveActiveDocumentImmediate()
            updateCanUndoRedo()
        }
    }

    fun redo() {
        typingUndoJob?.cancel()
        typingUndoJob = null
        activeTypingUndoState = null

        if (redoStack.isNotEmpty()) {
            val nextState = redoStack.removeAt(redoStack.size - 1)
            undoStack.add(_editorBlocks.value.toList())
            _editorBlocks.value = nextState
            saveActiveDocumentImmediate()
            updateCanUndoRedo()
        }
    }

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private fun updateCanUndoRedo() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

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
            triggerProjectSync(updatedDoc.projectId)
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
    fun exportCurrentDocument(
        context: Context,
        format: String,
        pageSizeName: String = "A4",
        fontPreference: String = "SERIF",
        lineSpacingMultiplier: Float = 1.15f,
        includePageNumbers: Boolean = true
    ): File? {
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
                "PDF" -> FormatExporter.exportToPdf(
                    title = doc.title,
                    blocks = blocks,
                    outputStream = fos,
                    pageSizeName = pageSizeName,
                    fontPreference = fontPreference,
                    lineSpacingMultiplier = lineSpacingMultiplier,
                    includePageNumbers = includePageNumbers
                )
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

    fun syncProjectsToInternalMemory(context: Context, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Get all projects, folders, documents
                val projects = repository.getAllProjectsDirectList()
                val folders = repository.getAllFoldersDirectList()
                val documents = repository.getAllDocumentsDirectList()

                // 2. Define root folder inside standard public Documents folder
                var rootDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "WriterStudioProjects")
                var isWritable = false
                try {
                    if (!rootDir.exists()) {
                        rootDir.mkdirs()
                    }
                    val testFile = File(rootDir, ".write_test")
                    testFile.writeText("test")
                    testFile.delete()
                    isWritable = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Fallback directory in external storage
                if (!isWritable) {
                    rootDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "WriterStudioProjects")
                    if (!rootDir.exists()) {
                        rootDir.mkdirs()
                    }
                }

                // Clean-up previous export
                if (rootDir.exists()) {
                    rootDir.deleteRecursively()
                }
                rootDir.mkdirs()

                // 3. Subcategories based on active, archived, or trashed states
                val lang = appLanguage.value
                val activeDirName = if (lang == "ru") "Активные" else "Active"
                val archiveDirName = if (lang == "ru") "Архив" else "Archive"
                val trashDirName = if (lang == "ru") "Корзина" else "Trash"

                val activeDir = File(rootDir, activeDirName).apply { mkdirs() }
                val archiveDir = File(rootDir, archiveDirName).apply { mkdirs() }
                val trashDir = File(rootDir, trashDirName).apply { mkdirs() }

                val converters = com.example.data.local.Converters()

                for (project in projects) {
                    val targetParentDir = when {
                        project.isInTrash -> trashDir
                        project.isArchived -> archiveDir
                        else -> activeDir
                    }

                    // Sanitize project name
                    val projSlug = project.title.replace("[\\\\/*?:\"<>|]".toRegex(), "_").trim()
                    val projDirName = if (projSlug.isEmpty()) "Без названия (${project.id})" else projSlug
                    val projFolder = File(targetParentDir, projDirName).apply { mkdirs() }

                    // Handle folders inside this project recursively (infinite depth safe)
                    val projectFolders = folders.filter { f -> f.projectId == project.id }
                    val createdFoldersMap = mutableMapOf<Long, File>()
                    val remainingFolders = projectFolders.toMutableList()
                    var iterations = 0
                    while (remainingFolders.isNotEmpty() && iterations < 20) {
                        val iterator = remainingFolders.iterator()
                        var resolvedAny = false
                        while (iterator.hasNext()) {
                            val folder = iterator.next()
                            val parentDir = if (folder.parentFolderId == null) {
                                projFolder
                            } else {
                                createdFoldersMap[folder.parentFolderId]
                            }

                            if (parentDir != null) {
                                val folderSlug = folder.name.replace("[\\\\/*?:\"<>|]".toRegex(), "_").trim()
                                val folderDirName = if (folderSlug.isEmpty()) "Новая папка (${folder.id})" else folderSlug
                                val dir = File(parentDir, folderDirName).apply { mkdirs() }
                                createdFoldersMap[folder.id] = dir
                                iterator.remove()
                                resolvedAny = true
                            }
                        }
                        if (!resolvedAny) {
                            break
                        }
                        iterations++
                    }

                    // Fallback for any remaining unmapped or cylic folders
                    for (folder in remainingFolders) {
                        val folderSlug = folder.name.replace("[\\\\/*?:\"<>|]".toRegex(), "_").trim()
                        val folderDirName = if (folderSlug.isEmpty()) "Новая папка (${folder.id})" else folderSlug
                        val dir = File(projFolder, folderDirName).apply { mkdirs() }
                        createdFoldersMap[folder.id] = dir
                    }

                    // Export all markdown/blocks documents for this project
                    val projectDocuments = documents.filter { d -> d.projectId == project.id }
                    for (doc in projectDocuments) {
                        val parentDir = if (doc.folderId != null) {
                            createdFoldersMap[doc.folderId] ?: projFolder
                        } else {
                            projFolder
                        }

                        val docSlug = doc.title.replace("[\\\\/*?:\"<>|]".toRegex(), "_").trim()
                        val docFileName = if (docSlug.isEmpty()) "Без названия (${doc.id})" else docSlug

                        // Export as text document
                        val txtFile = File(parentDir, "$docFileName.txt")
                        val blocks = converters.toBlocksList(doc.contentBlocksJson)
                        val textBuilder = StringBuilder()
                        for (block in blocks) {
                            if (block.type == "image") {
                                textBuilder.append("[Изображение: ${block.imageCaption ?: "без названия"}]\n")
                            } else {
                                textBuilder.append(block.text)
                                textBuilder.append("\n")
                            }
                        }
                        txtFile.writeText(textBuilder.toString())

                        // Export full JSON with styling metadata alongside
                        val jsonFile = File(parentDir, "$docFileName.json")
                        val docObj = JSONObject().apply {
                            put("id", doc.id)
                            put("projectId", doc.projectId)
                            put("title", doc.title)
                            put("contentBlocksJson", doc.contentBlocksJson)
                            put("sortOrder", doc.sortOrder)
                            put("isPlainText", doc.isPlainText)
                            put("createdAt", doc.createdAt)
                            put("updatedAt", doc.updatedAt)
                        }
                        jsonFile.writeText(docObj.toString(4))
                    }
                }

                withContext(Dispatchers.Main) {
                    onComplete(true, rootDir.absolutePath)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onComplete(false, e.localizedMessage ?: "Unknown Error")
                }
            }
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
