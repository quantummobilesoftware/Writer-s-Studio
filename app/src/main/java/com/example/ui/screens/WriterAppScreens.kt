package com.example.ui.screens

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.entity.*
import com.example.data.models.BlockStyle
import com.example.data.models.EditorBlock
import com.example.ui.viewmodel.DocumentMetrics
import com.example.ui.viewmodel.WriterViewModel
import com.example.util.ImageStorageUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

// --- Theme Tones ---
object WriterThemeColors {
    // Elegant Dark (Inkwell Pro Theme)
    val DarkBg = Color(0xFF11121C)
    val DarkSurface = Color(0xFF1B1C26)
    val DarkText = Color(0xFFF7F5F0)
    val PrimaryAmber = Color(0xFFD0BCFF)
    
    // Light Pure White Theme
    val LightBg = Color(0xFFFFFFFF)
    val LightSurface = Color(0xFFFFFFFF)
    val LightText = Color(0xFF111111)
    val SecondarySlate = Color(0xFF4B6584)
}

@Composable
fun WriterAppMainLayout(viewModel: WriterViewModel) {
    val context = LocalContext.current
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val colorPalette by viewModel.colorPalette.collectAsStateWithLifecycle()

    val isLocked by viewModel.isAppLocked.collectAsStateWithLifecycle()
    val isPinConfigured by viewModel.isPinConfigured.collectAsStateWithLifecycle()

    val currentBg = when (themeMode) {
        "LIGHT" -> WriterThemeColors.LightBg
        "SEPIA" -> Color(0xFFF4ECD8)
        else -> WriterThemeColors.DarkBg
    }

    val currentTextColors = when (themeMode) {
        "LIGHT" -> WriterThemeColors.LightText
        "SEPIA" -> Color(0xFF4F3824)
        else -> WriterThemeColors.DarkText
    }

    val activePrimaryColor = when (colorPalette) {
        "BLUE" -> if (themeMode == "DARK") Color(0xFF8AB4F8) else Color(0xFF165EC0)
        "GREEN" -> if (themeMode == "DARK") Color(0xFF81C995) else Color(0xFF0F6D2E)
        "ORANGE" -> if (themeMode == "DARK") Color(0xFFFFB066) else Color(0xFFD35400)
        "RED" -> if (themeMode == "DARK") Color(0xFFFF8A80) else Color(0xFF962D22)
        "CORAL" -> if (themeMode == "DARK") Color(0xFFFE8B77) else Color(0xFFD85D4E)
        else -> if (themeMode == "DARK") WriterThemeColors.PrimaryAmber else WriterThemeColors.SecondarySlate
    }

    MaterialTheme(
        colorScheme = if (themeMode == "DARK") {
            darkColorScheme(
                primary = activePrimaryColor,
                onPrimary = Color(0xFF381E72),
                background = WriterThemeColors.DarkBg,
                onBackground = WriterThemeColors.DarkText,
                surface = WriterThemeColors.DarkSurface,
                onSurface = WriterThemeColors.DarkText,
                primaryContainer = activePrimaryColor,
                onPrimaryContainer = Color(0xFF381E72),
                secondaryContainer = Color(0xFF49454F),
                onSecondaryContainer = Color(0xFFCAC4D0),
                surfaceVariant = Color(0xFF211F26),
                onSurfaceVariant = Color(0xFFCAC4D0),
                outline = Color(0xFF49454F)
            )
        } else {
            lightColorScheme(
                primary = activePrimaryColor,
                background = if (themeMode == "LIGHT") WriterThemeColors.LightBg else Color(0xFFF4ECD8),
                surface = if (themeMode == "LIGHT") WriterThemeColors.LightSurface else Color(0xFFFDF8EE),
                onPrimary = Color.White,
                onBackground = if (themeMode == "LIGHT") WriterThemeColors.LightText else Color(0xFF4F3824),
                onSurface = if (themeMode == "LIGHT") WriterThemeColors.LightText else Color(0xFF4F3824)
            )
        }
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = currentBg
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isLocked && isPinConfigured) {
                    AppLockScreen(viewModel)
                } else {
                    NavigationScaffold(viewModel, themeMode, onThemeChange = { viewModel.setThemeMode(it) })
                }
            }
        }
    }
}

// --- Lock Screen ---
@Composable
fun AppLockScreen(viewModel: WriterViewModel) {
    var enteredPin by remember { mutableStateOf("") }
    var shakeTrigger by remember { mutableStateOf(false) }

    LaunchedEffect(enteredPin) {
        if (enteredPin.length == 4) {
            val success = viewModel.verifyAppPin(enteredPin)
            if (!success) {
                shakeTrigger = true
                delay(500)
                enteredPin = ""
                shakeTrigger = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WriterThemeColors.DarkBg)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = "App Locked",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Вход в Writer's Studio",
            color = WriterThemeColors.DarkText,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Введите 4-значный PIN-код защиты",
            color = Color.Gray,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(32.dp))

        // Dots indicating length
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            for (i in 1..4) {
                val filled = enteredPin.length >= i
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (filled) MaterialTheme.colorScheme.primary else Color.DarkGray)
                        .border(1.dp, Color.Gray, CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Beautiful numpad
        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("C", "0", "⌫")
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            for (row in keys) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    for (key in row) {
                        Button(
                            onClick = {
                                when (key) {
                                    "C" -> enteredPin = ""
                                    "⌫" -> if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                                    else -> if (enteredPin.length < 4) enteredPin += key
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (key in listOf("C", "⌫")) Color(0xFF2C3E50) else WriterThemeColors.DarkSurface
                            ),
                            shape = CircleShape,
                            modifier = Modifier
                                .size(72.dp)
                                .testTag("pin_key_$key")
                        ) {
                            Text(
                                text = key,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Navigation Controller Layout ---
@Composable
fun NavigationScaffold(
    viewModel: WriterViewModel,
    currTheme: String,
    onThemeChange: (String) -> Unit
) {
    var activeTab by remember { mutableStateOf("PROJECTS") } // PROJECTS, STATS, SETTINGS
    val selectedProj by viewModel.selectedProject.collectAsStateWithLifecycle()
    val activeDoc by viewModel.activeDocument.collectAsStateWithLifecycle()
    val isPrompterMode = remember { mutableStateOf(false) }
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        if (isPrompterMode.value && activeDoc != null) {
            PrompterScreen(viewModel = viewModel, onClose = { isPrompterMode.value = false })
        } else if (activeDoc != null) {
            DocumentEditorScreen(
                viewModel = viewModel,
                onBack = { viewModel.selectDocument(null) },
                onLaunchPrompter = { isPrompterMode.value = true }
            )
        } else if (selectedProj != null) {
            ProjectWorkspaceScreen(viewModel = viewModel, onBack = { viewModel.selectProject(null) })
        } else {
            Scaffold(
                bottomBar = {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        NavigationBarItem(
                            selected = activeTab == "PROJECTS",
                            onClick = { activeTab = "PROJECTS" },
                            icon = { Icon(Icons.Outlined.Book, l("cabinet", appLanguage)) },
                            label = { Text(l("cabinet", appLanguage)) },
                            modifier = Modifier.testTag("nav_tab_projects")
                        )
                        NavigationBarItem(
                            selected = activeTab == "STATS",
                            onClick = { activeTab = "STATS" },
                            icon = { Icon(Icons.Outlined.BarChart, l("progress", appLanguage)) },
                            label = { Text(l("progress", appLanguage)) },
                            modifier = Modifier.testTag("nav_tab_stats")
                        )
                        NavigationBarItem(
                            selected = activeTab == "SETTINGS",
                            onClick = { activeTab = "SETTINGS" },
                            icon = { Icon(Icons.Outlined.Settings, l("options", appLanguage)) },
                            label = { Text(l("options", appLanguage)) },
                            modifier = Modifier.testTag("nav_tab_settings")
                        )
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    when (activeTab) {
                        "PROJECTS" -> ProjectsDashboardScreen(viewModel)
                        "STATS" -> WriterStatsScreen(viewModel)
                        "SETTINGS" -> AppSettingsScreen(viewModel, currTheme, onThemeChange)
                    }
                }
            }
        }
    }
}

// --- PROJECTS SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsDashboardScreen(viewModel: WriterViewModel) {
    val activeProjs by viewModel.activeProjects.collectAsStateWithLifecycle()
    val archivedProjs by viewModel.archivedProjects.collectAsStateWithLifecycle()
    val trashProjs by viewModel.trashProjects.collectAsStateWithLifecycle()

    val searchQuery by viewModel.projectSearchQuery.collectAsStateWithLifecycle()
    val sortBy by viewModel.projectSortBy.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var currentSubTab by remember { mutableStateOf("ACTIVE") } // ACTIVE, FAVORITES, ARCHIVE, TRASH

    var isSearchPanelVisible by remember { mutableStateOf(false) }
    var searchCategoryFilter by remember { mutableStateOf("ALL") } // ALL, BOOK, SCREENPLAY, STORY, TEXT

    val unlockedIds by viewModel.unlockedProjectIds.collectAsStateWithLifecycle()
    var passwordProjectToUnlock by remember { mutableStateOf<WorkspaceProject?>(null) }
    var unlockingPassword by remember { mutableStateOf("") }
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val interfaceStyle by viewModel.interfaceStyle.collectAsStateWithLifecycle()

    // Unified List Provider with category filtration
    val filteredList = remember(activeProjs, archivedProjs, trashProjs, currentSubTab, searchQuery, sortBy, searchCategoryFilter) {
        val rawList = when (currentSubTab) {
            "ARCHIVE" -> archivedProjs
            "TRASH" -> trashProjs
            "FAVORITES" -> activeProjs.filter { it.isFavorite }
            else -> activeProjs
        }
        val categoryFiltered = if (searchCategoryFilter == "ALL") rawList else {
            rawList.filter { it.type == searchCategoryFilter }
        }
        val searched = if (searchQuery.isEmpty()) categoryFiltered else {
            categoryFiltered.filter { it.title.contains(searchQuery, ignoreCase = true) || it.type.contains(searchQuery, ignoreCase = true) }
        }
        when (sortBy) {
            "NAME" -> searched.sortedBy { it.title }
            "CREATED" -> searched.sortedBy { it.createdAt }
            else -> searched.sortedByDescending { it.updatedAt }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                ),
                navigationIcon = {
                    if (interfaceStyle == "PIXEL") {
                        Box(
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { /* Decorative Menu option */ },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menu",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Create,
                            contentDescription = "Feather Quill Logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Writer's Studio",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (interfaceStyle == "PIXEL") {
                        Box(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSearchPanelVisible) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .clickable { isSearchPanelVisible = !isSearchPanelVisible },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = l("search", appLanguage),
                                tint = if (isSearchPanelVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        IconButton(onClick = { isSearchPanelVisible = !isSearchPanelVisible }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = l("search", appLanguage),
                                tint = if (isSearchPanelVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (currentSubTab == "ACTIVE" || currentSubTab == "FAVORITES") {
                ExtendedFloatingActionButton(
                    onClick = { showCreateDialog = true },
                    icon = { Icon(Icons.Filled.Add, l("new_project", appLanguage)) },
                    text = { Text(l("new_project", appLanguage), fontWeight = FontWeight.Bold) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color(0xFF11121C),
                    modifier = Modifier.testTag("create_project_fab")
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // SEARCH & FILTER PANEL (opens when magnifier icon is clicked)
            AnimatedVisibility(visible = isSearchPanelVisible) {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = l("search_filter", appLanguage),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Input query
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setProjectSearchQuery(it) },
                            placeholder = { Text(l("search_placeholder", appLanguage)) },
                            leadingIcon = { Icon(Icons.Default.Search, "Search Icon", tint = Color.Gray, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setProjectSearchQuery("") }) {
                                        Icon(Icons.Default.Close, "Clear", modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("project_search_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Sorters selection
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(l("sort_by", appLanguage), fontSize = 11.sp, color = Color.Gray, modifier = Modifier.width(76.dp))
                            listOf("UPDATED" to l("sort_changed", appLanguage), "NAME" to l("sort_name", appLanguage), "CREATED" to l("sort_created", appLanguage)).forEach { (sortId, label) ->
                                val isSelected = sortBy == sortId
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setProjectSortBy(sortId) },
                                    label = { Text(label, fontSize = 11.sp) },
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )
                            }
                        }

                        // Categories filtering UI (All, Book, Screenplay, Story, Text)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(l("category", appLanguage), fontSize = 11.sp, color = Color.Gray, modifier = Modifier.width(76.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf("ALL" to l("cat_all", appLanguage), "BOOK" to l("cat_book", appLanguage), "SCREENPLAY" to l("cat_screenplay", appLanguage), "STORY" to l("cat_story", appLanguage), "TEXT" to l("cat_text", appLanguage)).forEach { (typeId, label) ->
                                        val isSelected = searchCategoryFilter == typeId
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { searchCategoryFilter = typeId },
                                            label = { Text(label, fontSize = 10.sp) },
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Tab Selectors
            if (interfaceStyle == "PIXEL") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tabConfig = listOf(
                        Triple("ACTIVE", l("projects", appLanguage), Icons.Default.FolderOpen),
                        Triple("FAVORITES", l("favorites", appLanguage), Icons.Default.StarBorder),
                        Triple("ARCHIVE", l("archive", appLanguage), Icons.Default.Archive),
                        Triple("TRASH", l("trash", appLanguage), Icons.Default.Delete)
                    )
                    
                    tabConfig.forEach { (tabId, label, icon) ->
                        val isSelected = currentSubTab == tabId
                        DashboardTabItem(
                            label = label,
                            icon = icon,
                            isSelected = isSelected,
                            onClick = { currentSubTab = tabId },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "ACTIVE" to l("projects", appLanguage),
                        "FAVORITES" to l("favorites", appLanguage),
                        "ARCHIVE" to l("archive", appLanguage),
                        "TRASH" to l("trash", appLanguage)
                    ).forEach { (tabId, label) ->
                        val isSelected = currentSubTab == tabId
                        FilterChip(
                            selected = isSelected,
                            onClick = { currentSubTab = tabId },
                            label = { Text(label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (filteredList.isEmpty()) {
                if (interfaceStyle == "PIXEL") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                        shape = RoundedCornerShape(32.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            EmptyStateIllustration()
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = l("no_projects_yet", appLanguage),
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                text = l("create_first_project_desc", appLanguage),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Empty",
                                modifier = Modifier.size(72.dp),
                                tint = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = l("no_projects_yet", appLanguage),
                                color = Color.Gray,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredList) { proj ->
                        ProjectItemRow(
                            project = proj,
                            onExplore = {
                                if (proj.passwordHash.isNullOrEmpty() || unlockedIds.contains(proj.id)) {
                                    viewModel.selectProject(proj)
                                } else {
                                    passwordProjectToUnlock = proj
                                    unlockingPassword = ""
                                }
                            },
                            onFavoriteToggle = { viewModel.toggleFavoriteProject(proj) },
                            onArchiveToggle = { viewModel.toggleArchiveProject(proj) },
                            onTrashToggle = { viewModel.sendToTrash(proj) },
                            onRestore = { viewModel.restoreFromTrash(proj) },
                            onDeletePermanent = { viewModel.permanentDeleteProject(proj) },
                            isTrashMode = currentSubTab == "TRASH",
                            appLanguage = appLanguage
                        )
                    }
                }
            }
        }
    }

    // --- Create Dialog ---
    if (showCreateDialog) {
        var pTitle by remember { mutableStateOf("") }
        var pType by remember { mutableStateOf("BOOK") } // SCREENPLAY, BOOK, STORY, TEXT
        var pPassword by remember { mutableStateOf("") }
        val colorsPalette = listOf("#6200EE", "#D32F2F", "#388E3C", "#1976D2", "#FBC02D", "#7B1FA2")
        var selectedCol by remember { mutableStateOf("#6200EE") }

        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showCreateDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { showCreateDialog = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {}
                        .wrapContentHeight(),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = Color(0xFF1B1C26),
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 80.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Drag Handle
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .background(Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                                .align(Alignment.CenterHorizontally)
                        )

                        // Title
                        Text(
                            text = l("new_project_dialog_title", appLanguage),
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = Color.White
                        )

                        // 2. Project Title Field
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = pTitle,
                                onValueChange = { pTitle = it },
                                placeholder = { Text(l("project_name_placeholder_new", appLanguage), color = Color.Gray) },
                                label = { Text(l("project_title_hint", appLanguage), color = Color(0xFFC5B3FF)) }, 
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("new_project_title_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFFC5B3FF),
                                    unfocusedBorderColor = Color(0xFF32333E),
                                    focusedContainerColor = Color(0xFF11121C).copy(alpha = 0.3f),
                                    unfocusedContainerColor = Color(0xFF11121C).copy(alpha = 0.3f),
                                    focusedLabelColor = Color(0xFFC5B3FF),
                                    unfocusedLabelColor = Color.Gray
                                ),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                        }

                        // 3. Project type
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = l("project_type", appLanguage),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val types = listOf(
                                    Triple("BOOK", l("cat_book", appLanguage), Icons.Default.Book),
                                    Triple("SCREENPLAY", l("cat_screenplay", appLanguage), Icons.Default.Movie),
                                    Triple("STORY", l("cat_story", appLanguage), Icons.Default.Description),
                                    Triple("TEXT", l("cat_text", appLanguage), Icons.Default.Title)
                                )
                                types.forEach { (typeId, label, icon) ->
                                    val isSelected = pType == typeId
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .border(
                                                width = if (isSelected) 0.dp else 1.dp,
                                                color = if (isSelected) Color.Transparent else Color(0xFF32333E),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable { pType = typeId },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) Color(0xFFC5B3FF) else Color.Transparent
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 12.dp, horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = label,
                                                tint = if (isSelected) Color(0xFF11121C) else Color.Gray,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = label,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isSelected) Color(0xFF11121C) else Color.Gray,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 4. Color indicator
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = l("color_indicator", appLanguage),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                colorsPalette.forEach { col ->
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .border(
                                                width = if (selectedCol == col) 2.dp else 0.dp,
                                                color = if (selectedCol == col) Color(0xFFD0BCFF) else Color.Transparent,
                                                shape = CircleShape
                                            )
                                            .padding(4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                                .background(Color(android.graphics.Color.parseColor(col)))
                                                .clickable { selectedCol = col },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (selectedCol == col) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 5. Access password
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = l("access_password_optional", appLanguage),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFC5B3FF)
                            )
                            var passwordVisible by remember { mutableStateOf(false) }
                            OutlinedTextField(
                                value = pPassword,
                                onValueChange = { pPassword = it },
                                placeholder = { Text(l("enter_password", appLanguage), color = Color.Gray) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFFC5B3FF),
                                    unfocusedBorderColor = Color(0xFF32333E),
                                    focusedContainerColor = Color(0xFF11121C).copy(alpha = 0.3f),
                                    unfocusedContainerColor = Color(0xFF11121C).copy(alpha = 0.3f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = "Toggle password visibility",
                                            tint = Color.Gray
                                        )
                                    }
                                }
                            )
                            Text(
                                text = l("password_hint_desc", appLanguage),
                                color = Color.Gray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }

                        // Actions Button Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { showCreateDialog = false }) {
                                Text(
                                    text = l("cancel", appLanguage),
                                    color = Color(0xFFC5B3FF),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                               )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Button(
                                onClick = {
                                    if (pTitle.isNotEmpty()) {
                                        viewModel.createProject(
                                            title = pTitle,
                                            type = pType,
                                            colorHex = selectedCol,
                                            password = pPassword.ifEmpty { null }
                                        )
                                        showCreateDialog = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFD0BCFF),
                                    contentColor = Color(0xFF11121C)
                                ),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier
                                    .testTag("confirm_create_project_button")
                            ) {
                                Text(
                                    text = l("create", appLanguage),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Password Verification Dialog ---
    if (passwordProjectToUnlock != null) {
        AlertDialog(
            onDismissRequest = { passwordProjectToUnlock = null },
            title = { Text(l("protected_project", appLanguage)) },
            text = {
                Column {
                    Text(l("project_locked_hint", appLanguage))
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = unlockingPassword,
                        onValueChange = { unlockingPassword = it },
                        label = { Text(l("password", appLanguage)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val proj = passwordProjectToUnlock ?: return@Button
                        val matched = viewModel.verifyProjectPassword(proj, unlockingPassword)
                        if (matched) {
                             viewModel.selectProject(proj)
                             passwordProjectToUnlock = null
                        } else {
                             // Incorrect visual feedback
                        }
                    }
                ) { Text(l("enter_btn", appLanguage)) }
            },
            dismissButton = {
                TextButton(onClick = { passwordProjectToUnlock = null }) { Text(l("cancel", appLanguage)) }
            }
        )
    }
}

// --- PROJECT ROW ITEM ---
@Composable
fun ProjectItemRow(
    project: WorkspaceProject,
    onExplore: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onArchiveToggle: () -> Unit,
    onTrashToggle: () -> Unit,
    onRestore: () -> Unit,
    onDeletePermanent: () -> Unit,
    isTrashMode: Boolean,
    appLanguage: String
) {
    val projectColor = remember(project.colorHex) {
        try { Color(android.graphics.Color.parseColor(project.colorHex)) } catch (e: Exception) { Color(0xFF6200EE) }
    }

    val typeLabel = when (project.type) {
        "SCREENPLAY" -> l("cat_screenplay", appLanguage)
        "STORY" -> l("cat_story", appLanguage)
        "TEXT" -> l("cat_text", appLanguage)
        else -> l("cat_book", appLanguage)
    }

    Card(
        onClick = onExplore,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("project_card_${project.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left color sign
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(54.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(projectColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = project.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!project.passwordHash.isNullOrEmpty()) {
                        Icon(Icons.Filled.Lock, l("protected_project", appLanguage), modifier = Modifier.size(14.dp), Color.Gray)
                    }
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFE8DEF8), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = typeLabel.uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1D192B)
                        )
                    }
                }
                Text(
                    text = "${l("modified", appLanguage)}: ${SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault()).format(Date(project.updatedAt))}",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            if (isTrashMode) {
                IconButton(onClick = onRestore, modifier = Modifier.testTag("restore_project_btn")) {
                    Icon(Icons.Outlined.Restore, l("restore", appLanguage), tint = Color.Green)
                }
                IconButton(onClick = onDeletePermanent, modifier = Modifier.testTag("hard_delete_project_btn")) {
                    Icon(Icons.Outlined.DeleteForever, l("delete_permanent", appLanguage), tint = Color.Red)
                }
            } else {
                IconButton(onClick = onFavoriteToggle, modifier = Modifier.testTag("fav_project_btn")) {
                    Icon(
                        imageVector = if (project.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = l("favorites", appLanguage),
                        tint = if (project.isFavorite) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
                IconButton(onClick = onArchiveToggle, modifier = Modifier.testTag("archive_project_btn")) {
                    Icon(
                        imageVector = if (project.isArchived) Icons.Filled.Unarchive else Icons.Outlined.Archive,
                        contentDescription = l("archive", appLanguage),
                        tint = Color.Gray
                    )
                }
                IconButton(onClick = onTrashToggle, modifier = Modifier.testTag("trash_project_btn")) {
                    Icon(Icons.Outlined.Delete, l("to_trash", appLanguage), tint = Color.Gray)
                }
            }
        }
    }
}

// --- PROJECT WORKSPACE SCREEN (Folders & Documents) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectWorkspaceScreen(viewModel: WriterViewModel, onBack: () -> Unit) {
    val project by viewModel.selectedProject.collectAsStateWithLifecycle()
    val folders by viewModel.currentFolders.collectAsStateWithLifecycle()
    val activeFolder by viewModel.selectedFolder.collectAsStateWithLifecycle()

    val rootDocs by viewModel.currentRootDocuments.collectAsStateWithLifecycle()
    val folderDocs by viewModel.currentFolderDocuments.collectAsStateWithLifecycle()

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateDocumentDialog by remember { mutableStateOf(false) }
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()

    val currentLevelFolders = remember(folders, activeFolder) {
        folders.filter { it.parentFolderId == activeFolder?.id }
    }

    val currentLevelDocuments = if (activeFolder == null) rootDocs else folderDocs

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(project?.title ?: l("project_docs", appLanguage), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (activeFolder != null) {
                            // Go back to parent folder
                            val parentId = activeFolder?.parentFolderId
                            val parentFolder = folders.find { it.id == parentId }
                            viewModel.selectFolder(parentFolder)
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, l("close", appLanguage))
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateFolderDialog = true }) {
                        Icon(Icons.Filled.CreateNewFolder, l("create_folder_title", appLanguage))
                    }
                    IconButton(onClick = { showCreateDocumentDialog = true }, modifier = Modifier.testTag("workspace_create_doc_btn")) {
                        Icon(Icons.Filled.NoteAdd, l("create_doc_title", appLanguage))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Breadcrumbs indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Home, l("project_root", appLanguage), modifier = Modifier.size(16.dp), tint = Color.Gray)
                Text(
                    text = " " + l("project_root", appLanguage),
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.clickable { viewModel.selectFolder(null) }
                )
                if (activeFolder != null) {
                    Text(" / ", fontSize = 12.sp, color = Color.Gray)
                    Icon(Icons.Default.FolderOpen, "Folder", modifier = Modifier.size(14.dp), tint = Color.Gray)
                    Text(" ${activeFolder?.name}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (currentLevelFolders.isEmpty() && currentLevelDocuments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.EditNote, "Empty", modifier = Modifier.size(72.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(l("create_work_hint", appLanguage), color = Color.Gray, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    // List Folders first
                    items(currentLevelFolders) { fold ->
                        Card(
                            onClick = { viewModel.selectFolder(fold) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Folder, l("folders", appLanguage), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(fold.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    Text(l("project_folder", appLanguage), fontSize = 11.sp, color = Color.Gray)
                                }
                                IconButton(onClick = { viewModel.deleteFolder(fold.id) }) {
                                    Icon(Icons.Default.Delete, l("delete", appLanguage), tint = Color.Gray)
                                }
                            }
                        }
                    }

                    // List documents
                    items(currentLevelDocuments) { doc ->
                        Card(
                            onClick = { viewModel.selectDocument(doc) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("document_card_${doc.id}"),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Notes, l("document", appLanguage), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(doc.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text(
                                        text = "${l("document", appLanguage)} • ${l("modified", appLanguage)}: ${SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault()).format(Date(doc.updatedAt))}",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                                Icon(Icons.Default.Edit, l("edit", appLanguage), tint = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }

    // CREATE FOLDER DIALOG
    if (showCreateFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text(l("create_folder_title", appLanguage)) },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text(l("folder_name", appLanguage)) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (folderName.isNotEmpty()) {
                        viewModel.createFolder(folderName)
                        showCreateFolderDialog = false
                    }
                }) { Text(l("create", appLanguage)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text(l("cancel", appLanguage)) }
            }
        )
    }

    // CREATE DOCUMENT DIALOG
    if (showCreateDocumentDialog) {
        val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
        var docName by remember { mutableStateOf("") }
        var isPlainText by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showCreateDocumentDialog = false },
            title = { Text(l("create_doc_title", appLanguage)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = docName,
                        onValueChange = { docName = it },
                        label = { Text(l("doc_name", appLanguage)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_document_name_input")
                    )

                    Text(l("select_editor_mode", appLanguage), fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { isPlainText = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isPlainText) MaterialTheme.colorScheme.primary else Color.Gray
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(l("type_doc", appLanguage), fontSize = 11.sp, maxLines = 1)
                        }

                        Button(
                            onClick = { isPlainText = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPlainText) MaterialTheme.colorScheme.primary else Color.Gray
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(l("type_txt", appLanguage), fontSize = 11.sp, maxLines = 1)
                        }
                    }

                    Text(
                        text = if (isPlainText) l("basic_mode_desc", appLanguage) else l("pro_mode_desc", appLanguage),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (docName.isNotEmpty()) {
                            viewModel.createDocument(docName, isPlainText)
                            showCreateDocumentDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_create_document_button")
                ) { Text(l("create", appLanguage)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDocumentDialog = false }) { Text(l("cancel", appLanguage)) }
            }
        )
    }
}

// --- DOCUMENT RICH TEXT EDITOR SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentEditorScreen(
    viewModel: WriterViewModel,
    onBack: () -> Unit,
    onLaunchPrompter: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val document by viewModel.activeDocument.collectAsStateWithLifecycle()
    val blocks by viewModel.editorBlocks.collectAsStateWithLifecycle()
    val history by viewModel.activeDocumentHistory.collectAsStateWithLifecycle()

    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    var isTranslitEnabled by remember { mutableStateOf(false) }
    var isVirtualKeyboardVisible by remember { mutableStateOf(false) }

    var activeBlockIndex by remember { mutableStateOf(0) }
    val metrics = viewModel.getDocumentMetrics()

    // Search and Replace States
    var isSearchPanelVisible by remember { mutableStateOf(false) }
    var searchQueryText by remember { mutableStateOf("") }
    var replaceQueryText by remember { mutableStateOf("") }

    // Version snapshots pane
    var isHistoryPanelVisible by remember { mutableStateOf(false) }

    if (document?.isPlainText == true) {
        // --- BASIC MODE / SIMPLE TXT WRITER ---
        val textFileContent = remember(blocks) {
            blocks.firstOrNull()?.text ?: ""
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        var editingTitle by remember(document?.title) { mutableStateOf(document?.title ?: "") }
                        BasicTextField(
                            value = editingTitle,
                            onValueChange = {
                                editingTitle = it
                                viewModel.updateDocumentTitle(it)
                            },
                            textStyle = TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.fillMaxWidth(0.7f)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            viewModel.saveActiveDocumentImmediate()
                            onBack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, l("close", appLanguage))
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchPanelVisible = !isSearchPanelVisible }) {
                            Icon(Icons.Filled.Search, l("search", appLanguage))
                        }
                        IconButton(onClick = { isHistoryPanelVisible = !isHistoryPanelVisible }) {
                            Icon(Icons.Filled.History, l("versions", appLanguage))
                        }
                        IconButton(onClick = { viewModel.saveActiveDocumentImmediate() }, modifier = Modifier.testTag("editor_manual_save_btn")) {
                            Icon(Icons.Filled.Save, l("manual_save", appLanguage))
                        }
                        IconButton(onClick = onLaunchPrompter, modifier = Modifier.testTag("editor_prompter_btn")) {
                            Icon(Icons.Filled.PlayArrow, l("prompter", appLanguage))
                        }
                    }
                )
            },
             bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${l("words", appLanguage)}: ${metrics.words}  |  ${l("chars", appLanguage)}: ${metrics.characters}", fontSize = 11.sp, color = Color.Gray)
                        Text("${l("reading_time", appLanguage)}: ~${metrics.readingTimeMinutes} ${l("min", appLanguage)}", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Search panel if toggled
                    AnimatedVisibility(visible = isSearchPanelVisible) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row {
                                    OutlinedTextField(
                                        value = searchQueryText,
                                        onValueChange = { searchQueryText = it },
                                        label = { Text(l("find_replace", appLanguage)) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedTextField(
                                        value = replaceQueryText,
                                        onValueChange = { replaceQueryText = it },
                                        label = { Text(l("chars", appLanguage)) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                    TextButton(
                                        onClick = {
                                            if (searchQueryText.isNotEmpty()) {
                                                val b = blocks.firstOrNull() ?: EditorBlock(type = "paragraph", text = "")
                                                val updatedText = b.text.replace(searchQueryText, replaceQueryText, ignoreCase = true)
                                                viewModel.updateEditorBlocks(listOf(b.copy(text = updatedText)))
                                                Toast.makeText(context, if (appLanguage == "ru") "Текст заменен" else "Text replaced", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    ) { Text("OK") }
                                }
                            }
                        }
                    }

                    // Text Area Editor Workspace
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        BasicTextField(
                            value = textFileContent,
                            onValueChange = { newVal ->
                                val activeBlock = blocks.firstOrNull() ?: EditorBlock(type = "paragraph", text = "")
                                viewModel.updateEditorBlocks(
                                    listOf(activeBlock.copy(text = newVal)),
                                    updateHistory = false
                                )
                            },
                            textStyle = TextStyle(
                                fontSize = 16.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 24.sp
                            ),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("basic_text_editor_input")
                        )

                        if (textFileContent.isEmpty()) {
                            Text(
                                text = l("empty_text", appLanguage),
                                style = TextStyle(fontSize = 16.sp, color = Color.LightGray.copy(alpha = 0.5f))
                            )
                        }
                    }
                }

                // SNAPSHOTS PANEL in basic mode
                AnimatedVisibility(
                    visible = isHistoryPanelVisible,
                    enter = slideInHorizontally(initialOffsetX = { it }),
                    exit = slideOutHorizontally(targetOffsetX = { it }),
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(280.dp)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, Color.LightGray)
                        .align(Alignment.TopEnd)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(l("versions_subtitle", appLanguage), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(12.dp))

                        var snapLabel by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = snapLabel,
                            onValueChange = { snapLabel = it },
                            label = { Text(l("save_snapshot", appLanguage)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                if (snapLabel.isNotEmpty()) {
                                    viewModel.createManualSnapshot(snapLabel)
                                    snapLabel = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) { Text("OK") }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(l("saved_versions", appLanguage), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)

                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                            items(history) { hist ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable { viewModel.restoreSnapshot(hist) }
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(hist.snapshotName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(hist.timestamp)), fontSize = 10.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return
    }

    // Image Picker Result launcher
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && document != null) {
            val copiedPath = ImageStorageUtil.copyImageToProject(context, uri)
            if (copiedPath != null) {
                // Insert a new Image block in the editor blocks
                val blocksList = blocks.toMutableList()
                val newBlock = EditorBlock(
                    type = "image",
                    imageUrl = copiedPath,
                    imageCaption = "Изображение в документе"
                )
                // Add next to active block or at bottom
                if (activeBlockIndex in 0 until blocks.size) {
                    blocksList.add(activeBlockIndex + 1, newBlock)
                    activeBlockIndex++
                } else {
                    blocksList.add(newBlock)
                    activeBlockIndex = blocksList.size - 1
                }
                viewModel.updateEditorBlocks(blocksList)
            } else {
                Toast.makeText(context, "Ошибка импорта изображения", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    var editingTitle by remember(document?.title) { mutableStateOf(document?.title ?: "") }
                    BasicTextField(
                        value = editingTitle,
                        onValueChange = {
                            editingTitle = it
                            viewModel.updateDocumentTitle(it)
                        },
                        textStyle = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth(0.7f)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.saveActiveDocumentImmediate()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, l("close", appLanguage))
                    }
                },
                actions = {
                    IconButton(onClick = { isSearchPanelVisible = !isSearchPanelVisible }) {
                        Icon(Icons.Filled.Search, l("find_replace", appLanguage))
                    }
                    IconButton(onClick = { isHistoryPanelVisible = !isHistoryPanelVisible }) {
                        Icon(Icons.Filled.History, l("versions", appLanguage))
                    }
                    IconButton(onClick = { viewModel.saveActiveDocumentImmediate() }, modifier = Modifier.testTag("editor_manual_save_btn")) {
                        Icon(Icons.Filled.Save, l("manual_save", appLanguage))
                    }
                    IconButton(onClick = onLaunchPrompter, modifier = Modifier.testTag("editor_prompter_btn")) {
                        Icon(Icons.Filled.PlayArrow, l("prompter", appLanguage))
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(8.dp)
            ) {
                // Live metadata counters
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${l("words", appLanguage)}: ${metrics.words}  |  ${l("chars", appLanguage)}: ${metrics.characters}", fontSize = 11.sp, color = Color.Gray)
                    Text("${l("pages", appLanguage)}: ${metrics.pages}  |  ${l("reading_time", appLanguage)}: ~${metrics.readingTimeMinutes} ${l("min", appLanguage)}", fontSize = 11.sp, color = Color.Gray)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // FORMATTING HORIZONTAL BAR
                Row(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Undo / Redo
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = viewModel.canUndo,
                        modifier = Modifier.size(38.dp)
                    ) { Icon(Icons.Default.Undo, "Отменить", modifier = Modifier.size(22.dp), tint = if (viewModel.canUndo) MaterialTheme.colorScheme.primary else Color.LightGray) }

                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = viewModel.canRedo,
                        modifier = Modifier.size(38.dp)
                    ) { Icon(Icons.Default.Redo, "Вернуть", modifier = Modifier.size(22.dp), tint = if (viewModel.canRedo) MaterialTheme.colorScheme.primary else Color.LightGray) }

                    Box(modifier = Modifier.width(1.5.dp).height(24.dp).background(Color.Gray))

                    // Text modifications for selected block
                    IconButton(
                        onClick = {
                            val activeBlock = blocks.getOrNull(activeBlockIndex) ?: return@IconButton
                            val isBold = activeBlock.style.isBold
                            val updated = blocks.mapIndexed { index, b ->
                                if (index == activeBlockIndex) b.copy(style = b.style.copy(isBold = !isBold)) else b
                            }
                            viewModel.updateEditorBlocks(updated)
                        },
                        modifier = Modifier.size(38.dp).testTag("format_bold")
                    ) {
                        Text("B", fontWeight = FontWeight.Black, fontSize = 16.sp, color = if ((blocks.getOrNull(activeBlockIndex)?.style?.isBold) == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }

                    IconButton(
                        onClick = {
                            val activeBlock = blocks.getOrNull(activeBlockIndex) ?: return@IconButton
                            val isItalic = activeBlock.style.isItalic
                            val updated = blocks.mapIndexed { index, b ->
                                if (index == activeBlockIndex) b.copy(style = b.style.copy(isItalic = !isItalic)) else b
                            }
                            viewModel.updateEditorBlocks(updated)
                        },
                        modifier = Modifier.size(38.dp).testTag("format_italic")
                    ) {
                        Text("I", fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 16.sp, color = if ((blocks.getOrNull(activeBlockIndex)?.style?.isItalic) == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }

                    IconButton(
                        onClick = {
                            val activeBlock = blocks.getOrNull(activeBlockIndex) ?: return@IconButton
                            val isUnderline = activeBlock.style.isUnderline
                            val updated = blocks.mapIndexed { index, b ->
                                if (index == activeBlockIndex) b.copy(style = b.style.copy(isUnderline = !isUnderline)) else b
                            }
                            viewModel.updateEditorBlocks(updated)
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Text("U", style = TextStyle(textDecoration = TextDecoration.Underline), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if ((blocks.getOrNull(activeBlockIndex)?.style?.isUnderline) == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }

                    Box(modifier = Modifier.width(1.5.dp).height(24.dp).background(Color.Gray))

                    // Insert local photos
                    IconButton(
                        onClick = { pickerLauncher.launch("image/*") },
                        modifier = Modifier.size(38.dp).testTag("editor_insert_image")
                    ) {
                        Icon(Icons.Filled.Image, "Вставить картинку", modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                    }

                    // Add block downwards
                    IconButton(
                        onClick = {
                            val list = blocks.toMutableList()
                            val insertIdx = if (activeBlockIndex in 0 until list.size) activeBlockIndex + 1 else list.size
                            list.add(insertIdx, EditorBlock(type = "paragraph", text = ""))
                            viewModel.updateEditorBlocks(list)
                            activeBlockIndex = insertIdx
                        },
                        modifier = Modifier.size(38.dp).testTag("editor_add_block")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddCircle,
                            contentDescription = "Вставить строку",
                            tint = Color.Gray,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // SEARCH & REPLACE PANEL
                AnimatedVisibility(visible = isSearchPanelVisible) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row {
                                OutlinedTextField(
                                    value = searchQueryText,
                                    onValueChange = { searchQueryText = it },
                                    label = { Text("Найти текст") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedTextField(
                                    value = replaceQueryText,
                                    onValueChange = { replaceQueryText = it },
                                    label = { Text("Заменить на") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                TextButton(
                                    onClick = {
                                        if (searchQueryText.isNotEmpty()) {
                                            val updated = blocks.map { b ->
                                                if (b.text.contains(searchQueryText, ignoreCase = true)) {
                                                    b.copy(text = b.text.replace(searchQueryText, replaceQueryText, ignoreCase = true))
                                                } else b
                                            }
                                            viewModel.updateEditorBlocks(updated)
                                            Toast.makeText(context, "Текст заменен", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) { Text("Заменить все") }
                            }
                        }
                    }
                }

                // SCREENPLAY FORMAT PRESETS EXTRAS FOR WRITERS
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = listOf(
                        "scene" to "Сцена",
                        "action" to "Опис.",
                        "character" to "Перс.",
                        "dialogue" to "Репл.",
                        "parenthetical" to "Дейс."
                    )
                    presets.forEach { (typeId, label) ->
                        val isCurr = blocks.getOrNull(activeBlockIndex)?.type == typeId
                        ElevatedFilterChip(
                            selected = isCurr,
                            onClick = {
                                val updated = blocks.mapIndexed { idx, block ->
                                    if (idx == activeBlockIndex) {
                                        block.copy(
                                            type = typeId,
                                            alignment = when (typeId) {
                                                "character", "dialogue", "parenthetical" -> "CENTER"
                                                "transition" -> "RIGHT"
                                                else -> "LEFT"
                                            }
                                        )
                                    } else block
                                }
                                viewModel.updateEditorBlocks(updated)
                            },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                    IconButton(
                        onClick = {
                            val list = blocks.toMutableList()
                            if (activeBlockIndex in 0 until list.size) {
                                list.removeAt(activeBlockIndex)
                                if (list.isEmpty()) list.add(EditorBlock(type = "paragraph", text = ""))
                                activeBlockIndex = Math.max(0, activeBlockIndex - 1)
                                viewModel.updateEditorBlocks(list)
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Delete, "Стереть блок", tint = Color.Gray)
                    }
                }

                // BLOCKS LIST
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    state = rememberLazyListState()
                ) {
                    itemsIndexed(blocks) { index, block ->
                        val isSelected = activeBlockIndex == index

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (isSelected) 1.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { activeBlockIndex = index }
                                .padding(8.dp)
                        ) {
                            if (block.type == "image") {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    AsyncImage(
                                        model = block.imageUrl,
                                        contentDescription = "Inline Image",
                                        modifier = Modifier
                                            .fillMaxWidth(block.imageSize)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    // Custom Width slider
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(0.8f)
                                    ) {
                                        Text("Шир:", fontSize = 10.sp, color = Color.Gray)
                                        Slider(
                                            value = block.imageSize,
                                            onValueChange = { newVal ->
                                                val updated = blocks.mapIndexed { idx, b ->
                                                    if (idx == index) b.copy(imageSize = newVal) else b
                                                }
                                                viewModel.updateEditorBlocks(updated)
                                            },
                                            valueRange = 0.2f..1.0f,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    // Caption editor
                                    BasicTextField(
                                        value = block.imageCaption ?: "",
                                        onValueChange = { newVal ->
                                            val updated = blocks.mapIndexed { idx, b ->
                                                if (idx == index) b.copy(imageCaption = newVal) else b
                                            }
                                            viewModel.updateEditorBlocks(updated, updateHistory = false)
                                        },
                                        textStyle = TextStyle(
                                            fontSize = 12.sp,
                                            fontStyle = FontStyle.Italic,
                                            color = Color.Gray,
                                            textAlign = TextAlign.Center
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            } else {
                                // Screenplay indent variables
                                val calculatedPadding = when (block.type) {
                                    "character" -> PaddingValues(horizontal = 48.dp, vertical = 2.dp)
                                    "dialogue" -> PaddingValues(horizontal = 32.dp, vertical = 2.dp)
                                    "parenthetical" -> PaddingValues(horizontal = 40.dp, vertical = 1.dp)
                                    else -> PaddingValues(0.dp)
                                }

                                val textStyle = TextStyle(
                                    fontSize = when (block.type) {
                                        "h1" -> 22.sp
                                        "h2" -> 18.sp
                                        "h3" -> 16.sp
                                        "quote" -> 13.sp
                                        "character" -> 14.sp
                                        else -> 14.sp
                                    },
                                    fontWeight = when (block.type) {
                                        "h1", "h2", "h3", "character", "scene" -> FontWeight.Bold
                                        else -> if (block.style.isBold) FontWeight.Bold else FontWeight.Normal
                                    },
                                    fontStyle = when (block.type) {
                                        "quote", "parenthetical" -> FontStyle.Italic
                                        else -> if (block.style.isItalic) FontStyle.Italic else FontStyle.Normal
                                    },
                                    textDecoration = when {
                                        block.style.isUnderline -> TextDecoration.Underline
                                        block.style.isStrikethrough -> TextDecoration.LineThrough
                                        else -> TextDecoration.None
                                    },
                                    textAlign = when (block.alignment) {
                                        "CENTER" -> TextAlign.Center
                                        "RIGHT" -> TextAlign.Right
                                        "JUSTIFY" -> TextAlign.Justify
                                        else -> TextAlign.Left
                                    },
                                    color = if (block.type == "quote") Color.Gray else MaterialTheme.colorScheme.onSurface,
                                    fontFamily = if (block.type in listOf("character", "dialogue", "parenthetical", "scene")) FontFamily.Monospace else FontFamily.Serif
                                )

                                Box(modifier = Modifier.padding(calculatedPadding)) {
                                    BasicTextField(
                                        value = block.text,
                                        onValueChange = { newVal ->
                                            val updatedValue = if (isTranslitEnabled && newVal.length > block.text.length) {
                                                val addedText = newVal.substring(block.text.length)
                                                block.text + transliterateEnToRu(addedText)
                                            } else newVal

                                            val updated = blocks.mapIndexed { idx, b ->
                                                if (idx == index) b.copy(text = updatedValue) else b
                                            }
                                            viewModel.updateEditorBlocks(updated, updateHistory = false)
                                        },
                                        textStyle = textStyle,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("editor_text_field_$index")
                                    )
                                    if (block.text.isEmpty()) {
                                        Text(
                                            text = when (block.type) {
                                                "scene" -> "ИНТ. ГОСТИНАЯ - ДЕНЬ"
                                                "character" -> "ПЕРСОНАЖ"
                                                "dialogue" -> "Реплика персонажа..."
                                                "parenthetical" -> "(шепотом)"
                                                "h1" -> "Заголовок 1..."
                                                else -> "Введите текст..."
                                            },
                                            style = textStyle.copy(color = Color.LightGray.copy(alpha = 0.5f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SNAPSHOT HISTORY SIDE BAR DRAWER POPUP OVERLAY
            AnimatedVisibility(
                visible = isHistoryPanelVisible,
                enter = slideInHorizontally(initialOffsetX = { it }),
                exit = slideOutHorizontally(targetOffsetX = { it }),
                modifier = Modifier
                    .fillMaxHeight()
                    .width(280.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, Color.LightGray)
                    .align(Alignment.TopEnd)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Версии и Ревизии", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    var snapLabel by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = snapLabel,
                        onValueChange = { snapLabel = it },
                        label = { Text("Сохранить снимок") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (snapLabel.isNotEmpty()) {
                                viewModel.createManualSnapshot(snapLabel)
                                snapLabel = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("save_snapshot_btn")
                    ) { Text("Сделать снимок") }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Сохраненные ревизии:", fontSize = 12.sp, color = Color.Gray)
                    
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(history) { snap ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                onClick = { viewModel.restoreSnapshot(snap) }
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(snap.snapshotName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(
                                        text = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(snap.timestamp)),
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                    Button(onClick = { isHistoryPanelVisible = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("Закрыть")
                    }
                }
            }
        }
    }
}

// --- IMMERSIVE TELEPROMPTER MODE SCREEN ---
@Composable
fun PrompterScreen(viewModel: WriterViewModel, onClose: () -> Unit) {
    val activeDoc by viewModel.activeDocument.collectAsStateWithLifecycle()
    val blocks by viewModel.editorBlocks.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()

    var isMirrorH by remember { mutableStateOf(false) }
    var isMirrorV by remember { mutableStateOf(false) }
    var scrollSpeed by remember { mutableStateOf(10f) } // 1 to 50
    var isScrolling by remember { mutableStateOf(false) }
    var textFontSize by remember { mutableStateOf(26f) }
    var prompterFont by remember { mutableStateOf("SERIF") } // SERIF, SANS_SERIF, MONO

    val prompterTheme = remember { mutableStateOf("DARK") } // DARK (Black-White), AMBER (Black-Amber), BRIGHT (White-Black)

    val prompterBg = when (prompterTheme.value) {
        "AMBER" -> Color(0xFF0F0B00)
        "BRIGHT" -> Color.White
        else -> Color.Black
    }

    val prompterText = when (prompterTheme.value) {
        "AMBER" -> Color(0xFFFFA500)
        "BRIGHT" -> Color.Black
        else -> Color.White
    }

    val fullManuscriptText = remember(blocks) {
        blocks.filter { b -> b.type != "image" }.joinToString("\n\n") { it.text }
    }

    val scrollState = rememberScrollState()

    // Autoscroller Routine Continuous Ticker Thread
    LaunchedEffect(isScrolling, scrollSpeed) {
        if (isScrolling) {
            while (true) {
                // Approximate 60FPS incremental pixel scrolls
                val step = scrollSpeed * 0.15f
                scrollState.scrollBy(step)
                delay(16)
            }
        }
    }

    // Speech estimation timers
    val wordsCount = remember(fullManuscriptText) { fullManuscriptText.split("\\s+".toRegex()).size }
    val remainingTimeMin = remember(scrollState.value, scrollState.maxValue) {
        val pct = if (scrollState.maxValue > 0) 1f - (scrollState.value.toFloat() / scrollState.maxValue) else 1f
        val estDurationMin = wordsCount / 150f // standard speech minutes
        (estDurationMin * pct).toInt()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(prompterBg)
    ) {
        // SCROLL CONTENT CONTAINER WITH horizontal/vertical GPU graphics transforms
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { isScrolling = !isScrolling } // Instant single-tap pause/play
                .graphicsLayer {
                    rotationX = if (isMirrorV) 180f else 0f
                    rotationY = if (isMirrorH) 180f else 0f
                }
                .padding(horizontal = 32.dp, vertical = 72.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = fullManuscriptText,
                    fontSize = textFontSize.sp,
                    color = prompterText,
                    lineHeight = (textFontSize * 1.5).sp,
                    fontFamily = when (prompterFont) {
                        "SANS_SERIF" -> FontFamily.SansSerif
                        "MONO" -> FontFamily.Monospace
                        else -> FontFamily.Serif
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(200.dp)) // padding so reader can finish reading
            }
        }

        // CONTROL OVERLAY CONTAINER (Semi-transparent top and bottom buttons)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Stats Row: Timer Remaining Speech
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${l("speech_live", appLanguage)}${remainingTimeMin}${l("minutes_left", appLanguage)}", color = Color.LightGray, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { isMirrorH = !isMirrorH },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isMirrorH) WriterThemeColors.PrimaryAmber else Color.DarkGray),
                        modifier = Modifier.testTag("prompter_mirror_h")
                    ) { Text(l("mirror_h", appLanguage), fontSize = 10.sp) }
                    Button(
                        onClick = { isMirrorV = !isMirrorV },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isMirrorV) WriterThemeColors.PrimaryAmber else Color.DarkGray)
                    ) { Text(l("mirror_v", appLanguage), fontSize = 10.sp) }
                }
            }

            // Speed Slider & Font Size
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Speed, l("speed", appLanguage), tint = Color.LightGray)
                Spacer(modifier = Modifier.width(8.dp))
                Slider(
                    value = scrollSpeed,
                    onValueChange = { scrollSpeed = it },
                    valueRange = 1f..50f,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("${l("speed", appLanguage)}: ${scrollSpeed.toInt()}", color = Color.White, fontSize = 12.sp)
            }

            // Font & Theme Options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(l("prompter_theme", appLanguage), color = Color.LightGray, fontSize = 11.sp)
                listOf(
                    "DARK" to l("theme_dark_prompter", appLanguage),
                    "AMBER" to l("theme_amber_prompter", appLanguage),
                    "BRIGHT" to l("theme_bright_prompter", appLanguage)
                ).forEach { (themeId, label) ->
                    val isSel = prompterTheme.value == themeId
                    Text(
                        text = label,
                        color = if (isSel) WriterThemeColors.PrimaryAmber else Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clickable { prompterTheme.value = themeId }
                            .padding(horizontal = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Icon(
                    imageVector = if (isScrolling) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = l("prompter", appLanguage),
                    tint = Color.White,
                    modifier = Modifier
                        .size(36.dp)
                        .clickable { isScrolling = !isScrolling }
                        .testTag("prompter_play_pause")
                )

                Spacer(modifier = Modifier.width(24.dp))

                Button(onClick = onClose, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                    Text(l("exit", appLanguage))
                }
            }
        }
    }
}

// --- PRODUCTIVITY STATS SCREEN ---
@Composable
fun WriterStatsScreen(viewModel: WriterViewModel) {
    val statsList by viewModel.statistics.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val interfaceStyle by viewModel.interfaceStyle.collectAsStateWithLifecycle()

    val totalWords = remember(statsList) { statsList.sumOf { it.wordsCount } }
    val totalChars = remember(statsList) { statsList.sumOf { it.charsCount } }

    val currentStreak = remember(statsList) {
        // Calculate writing streaks based on non-zero productivity dates
        var streak = 0
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = sdf.format(Date())
        
        // Quick iteration count
        val activeDates = statsList.filter { it.wordsCount > 0 }.map { it.dateString }.toSet()
        var checkCal = Calendar.getInstance()
        while (true) {
            val dateStr = sdf.format(checkCal.time)
            if (activeDates.contains(dateStr)) {
                streak++
                checkCal.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }
        streak
    }

    if (interfaceStyle == "PIXEL") {
        val streakUnit = if (currentStreak == 1) {
            when (appLanguage) {
                "ru" -> "день"
                "es" -> "dia"
                else -> "day"
            }
        } else {
            l("days_short", appLanguage)
        }

        val wordsSuffix = if (totalWords == 1) {
            when (appLanguage) {
                "ru" -> "слово"
                "es" -> "palabra"
                else -> "word"
            }
        } else {
            l("words_suffix", appLanguage)
        }

        val charsSuffix = if (totalChars == 1) {
            when (appLanguage) {
                "ru" -> "символ"
                "es" -> "caracter"
                else -> "char"
            }
        } else {
            l("chars_suffix", appLanguage)
        }

        val cleanStreakLabel = remember(appLanguage) {
            l("stats_streak", appLanguage).substringBefore(" (").replace(":", "").trim()
        }
        val cleanWordsLabel = remember(appLanguage) {
            l("stats_words", appLanguage).replace(":", "").trim()
        }
        val cleanCharsLabel = remember(appLanguage) {
            l("stats_chars", appLanguage).replace(":", "").trim()
        }

        val last7DaysStats = remember(statsList) {
            val list = mutableListOf<ProductivityStat>()
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -6)
            for (i in 0 until 7) {
                val dateStr = sdf.format(cal.time)
                val match = statsList.find { it.dateString == dateStr }
                list.add(match ?: ProductivityStat(dateString = dateStr, wordsCount = 0, charsCount = 0))
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            list
        }

        val maxItemOriginal = remember(last7DaysStats) { last7DaysStats.maxOfOrNull { it.wordsCount } ?: 0 }
        val maxItem = if (maxItemOriginal > 0) maxItemOriginal else 100
        val midValue = maxItem / 2

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Screen Title
            Text(
                text = l("stats_title", appLanguage),
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = Color.White
            )

            // 1. Streak Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1B1C26))
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF28243D)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Whatshot,
                            contentDescription = null,
                            tint = Color(0xFFC5B3FF),
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = cleanStreakLabel,
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$currentStreak $streakUnit",
                            color = Color(0xFFC5B3FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Text("🔥", fontSize = 24.sp)
                }
            }

            // 2. Total Words & Chars Combined Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1B1C26)
                )
            ) {
                Column {
                    // Words Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF231F32)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Notes,
                                contentDescription = null,
                                tint = Color(0xFFC5B3FF),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Text(
                            text = cleanWordsLabel,
                            color = Color.Gray,
                            fontSize = 15.sp
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        Text(
                            text = "$totalWords $wordsSuffix",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.testTag("stats_total_words")
                        )
                    }

                    Divider(
                        color = Color(0xFF2E2F3F),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    // Characters Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF231F32)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Title,
                                contentDescription = null,
                                tint = Color(0xFFC5B3FF),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Text(
                            text = cleanCharsLabel,
                            color = Color.Gray,
                            fontSize = 15.sp
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        Text(
                            text = "$totalChars $charsSuffix",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.testTag("stats_total_chars")
                        )
                    }
                }
            }

            // 3. Productivity Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Equalizer,
                    contentDescription = null,
                    tint = Color(0xFFC5B3FF),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (appLanguage == "ru") "Продуктивность" else if (appLanguage == "es") "Productividad" else "Productivity",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                Text(
                    text = if (appLanguage == "ru") "(последние 7 дней)" else if (appLanguage == "es") "(últimos 7 días)" else "(Last 7 days)",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }

            // 4. Productivity Graph Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1B1C26)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Left Column (Y Axis)
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(end = 12.dp, bottom = 6.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(text = "$maxItem", fontSize = 10.sp, color = Color.Gray)
                            Text(text = "$midValue", fontSize = 10.sp, color = Color.Gray)
                            Text(text = "0", fontSize = 10.sp, color = Color.Gray)
                        }

                        // Right Graph Canvas
                        Canvas(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(bottom = 6.dp)
                        ) {
                            // Draw horizontal dashed lines
                            val pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            val gridLineYPositions = listOf(0.05f * size.height, 0.5f * size.height, 0.95f * size.height)
                            gridLineYPositions.forEach { yPos ->
                                drawLine(
                                    color = Color.Gray.copy(alpha = 0.2f),
                                    start = androidx.compose.ui.geometry.Offset(0f, yPos),
                                    end = androidx.compose.ui.geometry.Offset(size.width, yPos),
                                    pathEffect = pathEffect,
                                    strokeWidth = 2f
                                )
                            }

                            // Bars
                            val blockWidth = size.width / 7
                            val barWidth = blockWidth * 0.45f

                            last7DaysStats.forEachIndexed { idx, stat ->
                                val pct = stat.wordsCount.toFloat() / maxItem
                                val maxBarHeight = size.height * 0.9f
                                val barHeight = maxBarHeight * pct

                                if (barHeight > 0f) {
                                    val left = idx * blockWidth + (blockWidth - barWidth) / 2
                                    val top = size.height * 0.95f - barHeight

                                    drawRoundRect(
                                        color = Color(0xFFC5B3FF),
                                        topLeft = androidx.compose.ui.geometry.Offset(left, top),
                                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Labels below bar chart
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp), // offset slightly for Y-axis spacing
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        last7DaysStats.forEachIndexed { idx, _ ->
                            val daysAgo = 6 - idx
                            val label = if (daysAgo == 0) {
                                when (appLanguage) {
                                    "ru" -> "Сегодня"
                                    "es" -> "Hoy"
                                    else -> "Today"
                                }
                            } else {
                                when (appLanguage) {
                                    "ru" -> "$daysAgo д. назад"
                                    "es" -> "Hace $daysAgo"
                                    else -> if (daysAgo == 1) "1 day" else "$daysAgo days"
                                }
                            }
                            Text(
                                text = label,
                                fontSize = 8.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // 5. Keep it up card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1B1C26))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF28243D)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFFC5B3FF),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = if (appLanguage == "ru") "Так держать!" else if (appLanguage == "es") "¡Sigue así!" else "Keep it up!",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (appLanguage == "ru") "Постоянство — ключ к великим историям." else if (appLanguage == "es") "La constancia es la clave para grandes historias." else "Consistency is the key to great stories.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    } else {
        // Fallback / Classic Stats Screen
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(l("stats_title", appLanguage), fontWeight = FontWeight.Bold, fontSize = 22.sp)

            // Metrics Card
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(l("stats_streak", appLanguage), fontWeight = FontWeight.SemiBold)
                        Text("$currentStreak ${l("days_short", appLanguage)} 🔥", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                    }
                    Divider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(l("stats_words", appLanguage))
                        Text("$totalWords ${l("words_suffix", appLanguage)}", fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(l("stats_chars", appLanguage))
                        Text("$totalChars ${l("chars_suffix", appLanguage)}", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Productivity Graph placeholder using custom CANVAS
            Text(l("productivity_title", appLanguage), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                    ) {
                        // Draw a simple bar chart
                        val itemsToDraw = statsList.take(7).reversed()
                        if (itemsToDraw.isEmpty()) {
                            // Empty states indications
                            return@Canvas
                        }
                        val maxItem = itemsToDraw.maxOfOrNull { it.wordsCount } ?: 1
                        val blockWidth = size.width / 7
                        itemsToDraw.forEachIndexed { idx, stat ->
                            val pct = stat.wordsCount.toFloat() / maxItem
                            val barHeight = size.height * 0.7f * pct
                            drawRect(
                                color = Color(0xFF6200EE),
                                topLeft = androidx.compose.ui.geometry.Offset(
                                    x = idx * blockWidth + (blockWidth * 0.2f),
                                    y = size.height - barHeight - 20f
                                ),
                                size = androidx.compose.ui.geometry.Size(
                                    width = blockWidth * 0.6f,
                                    height = barHeight
                                )
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(l("earlier", appLanguage), fontSize = 10.sp, color = Color.Gray)
                        Text(l("today", appLanguage), fontSize = 10.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}

// --- SYSTEM PREFERENCES SETTINGS SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    viewModel: WriterViewModel,
    currTheme: String,
    onThemeChange: (String) -> Unit
) {
    val context = LocalContext.current
    var inputPinValue by remember { mutableStateOf("") }
    val isPinConfigured by viewModel.isPinConfigured.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val interfaceStyle by viewModel.interfaceStyle.collectAsStateWithLifecycle()

    var backupPayloadLocal by remember { mutableStateOf("") }

    val backupRestorerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val text = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                viewModel.runRestore(text) { success ->
                    if (success) {
                        Toast.makeText(context, l("backup_success", appLanguage), Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, l("backup_error", appLanguage), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, l("read_error", appLanguage), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(l("sys_settings", appLanguage), fontWeight = FontWeight.Bold, fontSize = 22.sp)

        // Language settings
        Text(l("app_language", appLanguage), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                "system" to l("lang_system", appLanguage),
                "ru" to l("lang_ru", appLanguage),
                "en" to l("lang_en", appLanguage),
                "es" to l("lang_es", appLanguage)
            ).forEach { (langCode, label) ->
                val isSelected = appLanguage == langCode
                Button(
                    onClick = { viewModel.setAppLanguage(langCode) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray
                    ),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) { Text(label, fontSize = 10.sp, maxLines = 1) }
            }
        }

        Divider()

        // Custom theme toggles
        Text(l("theme", appLanguage), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("LIGHT" to l("theme_light", appLanguage), "DARK" to l("theme_dark", appLanguage), "SEPIA" to l("theme_sepia", appLanguage)).forEach { (themeId, label) ->
                val isSelected = currTheme == themeId
                Button(
                    onClick = { onThemeChange(themeId) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray
                    ),
                    modifier = Modifier.weight(1f)
                ) { Text(label, fontSize = 12.sp) }
            }
        }

        Divider()

        // Color Palette Selector
        val colorPalette by viewModel.colorPalette.collectAsStateWithLifecycle()
        Text(l("color_palette", appLanguage), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val palettes = listOf(
                "AMBER" to (if (currTheme == "DARK") Color(0xFFD0BCFF) else Color(0xFF4B6584)),
                "BLUE" to (if (currTheme == "DARK") Color(0xFF8AB4F8) else Color(0xFF165EC0)),
                "GREEN" to (if (currTheme == "DARK") Color(0xFF81C995) else Color(0xFF0F6D2E)),
                "ORANGE" to (if (currTheme == "DARK") Color(0xFFFFB066) else Color(0xFFD35400)),
                "RED" to (if (currTheme == "DARK") Color(0xFFFF8A80) else Color(0xFF962D22)),
                "CORAL" to (if (currTheme == "DARK") Color(0xFFFE8B77) else Color(0xFFD85D4E))
            )
            palettes.forEach { (paletteId, colorVal) ->
                val isSelected = colorPalette == paletteId
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(colorVal)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Gray,
                            shape = CircleShape
                        )
                        .clickable { viewModel.setColorPalette(paletteId) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Selected",
                            tint = if (currTheme == "DARK") Color.Black else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Divider()

        // Interface Style Selector
        Text(l("interface_style", appLanguage), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "PIXEL" to l("style_pixel", appLanguage),
                "CLASSIC" to l("style_classic", appLanguage)
            ).forEach { (styleId, label) ->
                val isSelected = interfaceStyle == styleId
                Button(
                    onClick = { viewModel.setInterfaceStyle(styleId) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray
                    ),
                    modifier = Modifier.weight(1f)
                ) { Text(label, fontSize = 12.sp) }
            }
        }

        Divider()

        // MASTER LOCK CONFIGS
        Text(l("security", appLanguage), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(l("security_sub", appLanguage))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputPinValue,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) inputPinValue = it },
                        label = { Text(l("four_digits", appLanguage)) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pin_setup_input"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (inputPinValue.length == 4) {
                                viewModel.setupAppPin(inputPinValue)
                                inputPinValue = ""
                                Toast.makeText(context, l("pin_saved", appLanguage), Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.testTag("pin_setup_save_btn")
                    ) { Text(l("set", appLanguage)) }
                }

                if (isPinConfigured) {
                    Button(
                        onClick = {
                            viewModel.setupAppPin(null)
                            Toast.makeText(context, l("pin_removed", appLanguage), Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(l("disable_pin", appLanguage)) }
                }
            }
        }

        Divider()

        // LOCAL BACKUPS
        Text(l("backups", appLanguage), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        viewModel.runBackup(context) { txt ->
                            if (txt != null) {
                                backupPayloadLocal = txt
                                // Save backup to file inside external cache and share it
                                try {
                                    val backupFile = File(context.cacheDir, "writers_studio_backup.json")
                                    FileOutputStream(backupFile).use { fos ->
                                        fos.write(txt.toByteArray())
                                    }
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", backupFile)
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, l("export_backup", appLanguage)))
                                } catch (e: Exception) {
                                    Toast.makeText(context, l("export_error", appLanguage), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("backup_export_btn")
                ) { Text(l("export_backup", appLanguage)) }

                Button(
                    onClick = { backupRestorerLauncher.launch(arrayOf("application/json", "*/*")) },
                    modifier = Modifier.fillMaxWidth().testTag("backup_restore_btn")
                ) { Text(l("import_backup", appLanguage)) }
            }
        }
    }
}

// --- LOCALIZATION & TRANSLITERATION DICTIONARY UTILITIES ---
fun l(key: String, lang: String): String {
    val resolvedLang = when (lang) {
        "system" -> {
            val sysLang = java.util.Locale.getDefault().language
            if (sysLang == "ru" || sysLang == "es") sysLang else "en"
        }
        "ru" -> "ru"
        "es" -> "es"
        else -> "en"
    }

    val dictionary = mapOf(
        "ru" to mapOf(
            "my_studio" to "Моя Творческая Студия",
            "cabinet" to "Кабинет",
            "progress" to "Успехи",
            "settings" to "Настройки",
            "options" to "Опции",
            "new_project" to "Новый проект",
            "search" to "Поиск",
            "active" to "Активные",
            "projects" to "Проекты",
            "favorites" to "Избранное",
            "archive" to "Архив",
            "trash" to "Корзина",
            "folders" to "Папки",
            "documents" to "Документы",
            "create" to "Создать",
            "cancel" to "Отмена",
            "versions" to "Версии",
            "versions_subtitle" to "Версии и Ревизии",
            "saved_versions" to "Сохраненные ревизии:",
            "save_snapshot" to "Сделать снимок",
            "save_snapshot_label" to "Сохранить снимок",
            "close" to "Закрыть",
            "find_replace" to "Найти и Заменить",
            "prompter" to "Суфлер",
            "manual_save" to "Сохранить",
            "words" to "Слов",
            "chars" to "Симв",
            "pages" to "Стр",
            "reading_time" to "Чтение",
            "min" to "мин",
            "sys_settings" to "Настройки",
            "theme" to "Тема приложения:",
            "theme_light" to "Светлая",
            "theme_dark" to "Темная",
            "theme_sepia" to "Сепия",
            "color_palette" to "Цветовая палитра:",
            "security" to "Безопасность и PIN-код доступа:",
            "security_sub" to "Введите 4-значный пароль для блокировки Writer's Studio:",
            "four_digits" to "4 цифры",
            "set" to "Задать",
            "disable_pin" to "Отключить PIN защиту",
            "backups" to "Резервное копирование XML/JSON:",
            "export_backup" to "Экспортировать Бэкап",
            "import_backup" to "Импортировать Бэкап",
            "app_language" to "Язык интерфейса:",
            "lang_system" to "Системный",
            "lang_ru" to "Русский",
            "lang_en" to "English",
            "lang_es" to "Español",
            "interface_style" to "Стиль интерфейса:",
            "style_pixel" to "Pixel Style",
            "style_classic" to "Classic Style",
            "editor_pro" to "Режим Профи (Блочный)",
            "editor_basic" to "Режим Базовый (Простой Текст)",
            "create_item" to "Создать новую запись",
            "enter_title" to "Введите заголовок",
            "create_folder_title" to "Создать Папку",
            "folder_name" to "Имя папки",
            "create_doc_title" to "Создать Документ",
            "doc_name" to "Имя документа",
            "unnamed" to "Без имени",
            "delete" to "Удалить",
            "edit" to "Редактировать",
            "empty_text" to "Введите text...",
            "stats_title" to "Статистика Писателя",
            "stats_streak" to "Писательский стрик (дней):",
            "stats_words" to "Всего написано слов:",
            "stats_chars" to "Всего написано символов:",
            "stats_daily" to "Ежедневная продуктивность",
            "select_editor_mode" to "Режим редактора:",
            "type_doc" to "Блочный документ (Pro)",
            "type_txt" to "Текстовый файл (Basic)",
            "format_bold" to "Полужирный",
            "format_italic" to "Курсив",
            "format_underline" to "Подчеркнутый",
            "project_docs" to "Документы проекта",
            "project_root" to "Корень проекта",
            "create_work_hint" to "Создайте папку или десктопный документ для работы",
            "search_filter" to "Поиск и Фильтрация",
            "sort_by" to "Сортировка: ",
            "sort_changed" to "Изм.",
            "sort_name" to "Имя",
            "sort_created" to "Ср.",
            "category" to "Категория: ",
            "search_placeholder" to "Введите название...",
            "cat_all" to "Все",
            "cat_book" to "Книга",
            "cat_screenplay" to "Сценарий",
            "cat_story" to "Рассказ",
            "cat_text" to "Текст",
            "modified" to "Изменен",
            "restore" to "Восстановить",
            "delete_permanent" to "Стереть навсегда",
            "to_trash" to "В корзину",
            "new_project_dialog_title" to "Новый литературный проект",
            "project_name_label" to "Название проекта (книга, сценарий...)",
            "project_title_hint" to "Название проекта",
            "project_name_placeholder_new" to "Название проекта (книга, сценарий...)",
            "color_indicator" to "Цветовой индикатор",
            "access_password_optional" to "Пароль доступа (опционально)",
            "enter_password" to "Введите пароль",
            "password_hint_desc" to "Оставьте пустым, чтобы проект оставался приватным.",
            "project_type" to "Тип проекта:",
            "color_marker" to "Цветовой маркер:",
            "access_password" to "Пароль доступа (опционально)",
            "no_projects_in_section" to "Нет проектов в этом разделе",
            "protected_project" to "Защищенный проект",
            "project_locked_hint" to "Этот проект заблокирован паролем. Пожалуйста, введите пароль:",
            "password" to "Пароль",
            "enter_btn" to "Вход",
            "project_folder" to "Папка проекта",
            "document" to "Документ",
            "speech_live" to "Живая речь: ~",
            "minutes_left" to " минут до конца",
            "mirror_h" to "Зерк H",
            "mirror_v" to "Зерк V",
            "speed" to "Скорость",
            "prompter_theme" to "Тема:",
            "theme_dark_prompter" to "Темная",
            "theme_amber_prompter" to "Янтарь",
            "theme_bright_prompter" to "Светлая",
            "exit" to "Выйти",
            "basic_mode_desc" to "Базовый режим — стандартная работа с текстовыми файлами. Весь контент редактируется в одном большом поле ввода.",
            "pro_mode_desc" to "Про режим — блочный режим сценарной / книжной верстки, возможностью вставки картинок.",
            "days_short" to "дн.",
            "words_suffix" to "слов",
            "chars_suffix" to "зн.",
            "productivity_title" to "Продуктивность (Последние дни):",
            "earlier" to "Ранее",
            "today" to "Сегодня",
            "backup_success" to "Резервная копия успешно восстановлена!",
            "backup_error" to "Ошибка восстановления ревизии",
            "read_error" to "Ошибка чтения файла",
            "pin_saved" to "Пароль установлен",
            "pin_removed" to "Пароль удален",
            "export_error" to "Ошибка сохранения резервной копии",
            "keyboard_cyrillic" to "Ввод (Кириллица)",
            "no_projects_yet" to "Проектов пока нет",
            "create_first_project_desc" to "Создайте свой первый проект, чтобы начать писать и воплотить свои идеи в жизнь."
        ),
        "en" to mapOf(
            "my_studio" to "My Creative Studio",
            "cabinet" to "Cabinet",
            "progress" to "Progress",
            "settings" to "Settings",
            "options" to "Options",
            "new_project" to "New Project",
            "search" to "Search",
            "active" to "Active",
            "projects" to "Projects",
            "favorites" to "Favorites",
            "archive" to "Archive",
            "trash" to "Trash",
            "folders" to "Folders",
            "documents" to "Documents",
            "create" to "Create",
            "cancel" to "Cancel",
            "versions" to "Versions",
            "versions_subtitle" to "Versions & Snapshots",
            "saved_versions" to "Saved revisions:",
            "save_snapshot" to "Create snapshot",
            "save_snapshot_label" to "Snapshot label",
            "close" to "Close",
            "find_replace" to "Find & Replace",
            "prompter" to "Prompter",
            "manual_save" to "Save",
            "words" to "Words",
            "chars" to "Chars",
            "pages" to "Pages",
            "reading_time" to "Reading",
            "min" to "min",
            "sys_settings" to "Settings",
            "theme" to "App Theme:",
            "theme_light" to "Light",
            "theme_dark" to "Dark",
            "theme_sepia" to "Sepia",
            "color_palette" to "Color Palette:",
            "security" to "Security and PIN protection:",
            "security_sub" to "Enter 4-digit password to lock Writer's Studio:",
            "four_digits" to "4 digits",
            "set" to "Set",
            "disable_pin" to "Disable PIN Protection",
            "backups" to "XML/JSON Backups & Restore:",
            "export_backup" to "Export Backup",
            "import_backup" to "Import Backup",
            "app_language" to "Interface Language:",
            "lang_system" to "System",
            "lang_ru" to "Russian",
            "lang_en" to "English",
            "lang_es" to "Spanish",
            "interface_style" to "Interface Style:",
            "style_pixel" to "Pixel Style",
            "style_classic" to "Classic Style",
            "editor_pro" to "Pro Mode (Block-based)",
            "editor_basic" to "Basic Mode (Plain Text)",
            "create_item" to "Create new record",
            "enter_title" to "Enter title",
            "create_folder_title" to "Create Folder",
            "folder_name" to "Folder name",
            "create_doc_title" to "Create Document",
            "doc_name" to "Document name",
            "unnamed" to "Unnamed",
            "delete" to "Delete",
            "edit" to "Edit",
            "empty_text" to "Type text here...",
            "stats_title" to "Writer's Statistics",
            "stats_streak" to "Writing Streak (days):",
            "stats_words" to "Total Words Written:",
            "stats_chars" to "Total Characters Written:",
            "stats_daily" to "Daily Productivity Log",
            "select_editor_mode" to "Editor Type:",
            "type_doc" to "Rich Block Document (Pro)",
            "type_txt" to "Simple Text File (Basic)",
            "format_bold" to "Bold",
            "format_italic" to "Italic",
            "format_underline" to "Underline",
            "project_docs" to "Project Documents",
            "project_root" to "Project root",
            "create_work_hint" to "Create a folder or document to start writing",
            "search_filter" to "Search & Filters",
            "sort_by" to "Sort by: ",
            "sort_changed" to "Mod.",
            "sort_name" to "Name",
            "sort_created" to "Crd.",
            "category" to "Category: ",
            "search_placeholder" to "Enter name...",
            "cat_all" to "All",
            "cat_book" to "Book",
            "cat_screenplay" to "Screenplay",
            "cat_story" to "Story",
            "cat_text" to "Text",
            "modified" to "Modified",
            "restore" to "Restore",
            "delete_permanent" to "Delete permanently",
            "to_trash" to "Move to trash",
            "new_project_dialog_title" to "New Literary Project",
            "project_name_label" to "Project title (book, screenplay...)",
            "project_title_hint" to "Project title",
            "project_name_placeholder_new" to "Project title (book, screenplay...)",
            "color_indicator" to "Color indicator",
            "access_password_optional" to "Access password (optional)",
            "enter_password" to "Enter password",
            "password_hint_desc" to "Leave empty to keep the project private.",
            "project_type" to "Project type:",
            "color_marker" to "Color indicator:",
            "access_password" to "Access password (optional)",
            "no_projects_in_section" to "No projects in this section",
            "protected_project" to "Protected Project",
            "project_locked_hint" to "This project is password protected. Please enter password:",
            "password" to "Password",
            "enter_btn" to "Enter",
            "project_folder" to "Project folder",
            "document" to "Document",
            "speech_live" to "Live speech: ~",
            "minutes_left" to " minutes remaining",
            "mirror_h" to "Mirror H",
            "mirror_v" to "Mirror V",
            "speed" to "Speed",
            "prompter_theme" to "Theme:",
            "theme_dark_prompter" to "Dark",
            "theme_amber_prompter" to "Amber",
            "theme_bright_prompter" to "Bright",
            "exit" to "Exit",
            "basic_mode_desc" to "Basic mode — standard plain text editing. The entire document is managed in a single text field.",
            "pro_mode_desc" to "Pro mode — structured book format with scene tags, block-by-block text fields and rich media insertions.",
            "days_short" to "days",
            "words_suffix" to "words",
            "chars_suffix" to "chars",
            "productivity_title" to "Productivity (Last days):",
            "earlier" to "Earlier",
            "today" to "Today",
            "backup_success" to "Backup successfully restored!",
            "backup_error" to "Error restoring backup",
            "read_error" to "Error reading file",
            "pin_saved" to "PIN configured",
            "pin_removed" to "PIN disabled",
            "export_error" to "Error exporting backup",
            "keyboard_cyrillic" to "On-Screen Cyrillic Input",
            "no_projects_yet" to "No projects yet",
            "create_first_project_desc" to "Create your first project to start writing and bring your ideas to life."
        ),
        "es" to mapOf(
            "my_studio" to "Estudio de Escritor",
            "cabinet" to "Escritorio",
            "progress" to "Progreso",
            "settings" to "Ajustes",
            "options" to "Opciones",
            "new_project" to "Nuevo Proyecto",
            "search" to "Buscar",
            "active" to "Activo",
            "projects" to "Proyectos",
            "favorites" to "Favoritos",
            "archive" to "Archivo",
            "trash" to "Papelera",
            "folders" to "Carpetas",
            "documents" to "Documentos",
            "create" to "Crear",
            "cancel" to "Cancelar",
            "versions" to "Versiones",
            "versions_subtitle" to "Versiones y Copias",
            "saved_versions" to "Revisiones guardadas:",
            "save_snapshot" to "Crear instantánea",
            "save_snapshot_label" to "Etiqueta",
            "close" to "Cerrar",
            "find_replace" to "Buscar y Reemplazar",
            "prompter" to "Prompter",
            "manual_save" to "Guardar",
            "words" to "Palabras",
            "chars" to "Caract",
            "pages" to "Págs",
            "reading_time" to "Lectura",
            "min" to "min",
            "sys_settings" to "Ajustes",
            "theme" to "Tema de la aplicación:",
            "theme_light" to "Claro",
            "theme_dark" to "Oscuro",
            "theme_sepia" to "Sepia",
            "color_palette" to "Paleta de colores:",
            "security" to "Seguridad y PIN de acceso:",
            "security_sub" to "Ingrese una contraseña de 4 dígitos para bloquear el Estudio:",
            "four_digits" to "4 dígitos",
            "set" to "Establecer",
            "disable_pin" to "Desactivar PIN",
            "backups" to "Copias de seguridad XML/JSON:",
            "export_backup" to "Exportar copia",
            "import_backup" to "Importar copia",
            "app_language" to "Idioma de la interfaz:",
            "lang_system" to "Sistema",
            "lang_ru" to "Ruso",
            "lang_en" to "Inglés",
            "lang_es" to "Español",
            "interface_style" to "Estilo de interfaz:",
            "style_pixel" to "Pixel Style",
            "style_classic" to "Classic Style",
            "editor_pro" to "Modo Pro (Bloques)",
            "editor_basic" to "Modo Básico (Texto)",
            "create_item" to "Nueva entrada",
            "enter_title" to "Ingrese título",
            "create_folder_title" to "Crear Carpeta",
            "folder_name" to "Nombre de carpeta",
            "create_doc_title" to "Crear Documento",
            "doc_name" to "Nombre del documento",
            "unnamed" to "Sin nombre",
            "delete" to "Eliminar",
            "edit" to "Editar",
            "empty_text" to "Escriba texto aquí...",
            "stats_title" to "Estadísticas del Escritor",
            "stats_streak" to "Racha de escritura (días):",
            "stats_words" to "Total de palabras:",
            "stats_chars" to "Total de caracteres:",
            "stats_daily" to "Productividad diaria",
            "select_editor_mode" to "Tipo de editor:",
            "type_doc" to "Documento Pro (Bloques)",
            "type_txt" to "Archivo de texto (Básico)",
            "format_bold" to "Negrita",
            "format_italic" to "Cursiva",
            "format_underline" to "Subrayado",
            "project_docs" to "Documentos del proyecto",
            "project_root" to "Raíz del proyecto",
            "create_work_hint" to "Cree una carpeta o documento para comenzar",
            "search_filter" to "Buscar y Filtros",
            "sort_by" to "Ordenar por: ",
            "sort_changed" to "Mod.",
            "sort_name" to "Nombre",
            "sort_created" to "Creado",
            "category" to "Categoría: ",
            "search_placeholder" to "Ingrese nombre...",
            "cat_all" to "Todo",
            "cat_book" to "Libro",
            "cat_screenplay" to "Guion",
            "cat_story" to "Relato",
            "cat_text" to "Texto",
            "modified" to "Modificado",
            "restore" to "Restaurar",
            "delete_permanent" to "Eliminar definitivamente",
            "to_trash" to "Mover a la papelera",
            "new_project_dialog_title" to "Nuevo proyecto literario",
            "project_name_label" to "Nombre del proyecto (libro, guión...)",
            "project_title_hint" to "Título del proyecto",
            "project_name_placeholder_new" to "Título del proyecto (libro, guión...)",
            "color_indicator" to "Indicador de color",
            "access_password_optional" to "Contraseña de acceso (opcional)",
            "enter_password" to "Introducir contraseña",
            "password_hint_desc" to "Déjelo vacío para mantener el proyecto privado.",
            "project_type" to "Tipo de proyecto:",
            "color_marker" to "Marcador de color:",
            "access_password" to "Contraseña de acceso (opcional)",
            "no_projects_in_section" to "No hay proyectos en esta sección",
            "protected_project" to "Proyecto protegido",
            "project_locked_hint" to "Este proyecto está protegido por contraseña. Por favor ingrese la contraseña:",
            "password" to "Contraseña",
            "enter_btn" to "Entrar",
            "project_folder" to "Carpeta del proyecto",
            "document" to "Documento",
            "speech_live" to "Discurso en vivo: ~",
            "minutes_left" to " minutos restantes",
            "mirror_h" to "Espejo H",
            "mirror_v" to "Espejo V",
            "speed" to "Velocidad",
            "prompter_theme" to "Tema:",
            "theme_dark_prompter" to "Oscuro",
            "theme_amber_prompter" to "Ámbar",
            "theme_bright_prompter" to "Claro",
            "exit" to "Salir",
            "basic_mode_desc" to "Modo Básico — edición estándar de texto plano. Todo el documento se gestiona en un solo campo.",
            "pro_mode_desc" to "Modo Pro — formato estructurado con etiquetas, campos por bloques e inserción de imágenes.",
            "days_short" to "días",
            "words_suffix" to "palabras",
            "chars_suffix" to "caract.",
            "productivity_title" to "Productividad (Últimos días):",
            "earlier" to "Antes",
            "today" to "Hoy",
            "backup_success" to "¡Copia de seguridad restaurada con éxito!",
            "backup_error" to "Error al restaurar copia",
            "read_error" to "Error al leer el archivo",
            "pin_saved" to "Contraseña establecida",
            "pin_removed" to "Contraseña eliminada",
            "export_error" to "Error al guardar copia de seguridad",
            "keyboard_cyrillic" to "Entrada de cirílico en pantalla",
            "no_projects_yet" to "No hay proyectos aún",
            "create_first_project_desc" to "Crea tu primer proyecto para empezar a escribir y dar vida a tus ideas."
        )
    )
    return dictionary[resolvedLang]?.get(key) ?: (dictionary["ru"]?.get(key) ?: key)
}

fun transliterateEnToRu(input: String): String {
    val digraphs = mapOf(
        "yo" to "ё", "Yo" to "Ё", "YO" to "Ё",
        "zh" to "ж", "Zh" to "Ж", "ZH" to "Ж",
        "kh" to "х", "Kh" to "Х", "KH" to "Х",
        "ts" to "ц", "Ts" to "Ц", "TS" to "Ц",
        "ch" to "ч", "Ch" to "Ч", "CH" to "CH",
        "sh" to "ш", "Sh" to "Ш", "SH" to "Ш",
        "shch" to "щ", "Shch" to "Щ", "SHCH" to "Щ",
        "yu" to "ю", "Yu" to "Ю", "YU" to "Ю",
        "ya" to "я", "Ya" to "Я", "YA" to "Я",
        "eh" to "э", "Eh" to "Э", "EH" to "Э"
    )
    val monografts = mapOf(
        'a' to 'а', 'A' to 'А',
        'b' to 'б', 'B' to 'Б',
        'v' to 'в', 'V' to 'В',
        'g' to 'г', 'G' to 'Г',
        'd' to 'д', 'D' to 'Д',
        'e' to 'е', 'E' to 'Е',
        'z' to 'з', 'Z' to 'З',
        'i' to 'и', 'I' to 'И',
        'y' to 'ы', 'Y' to 'Ы',
        'k' to 'к', 'K' to 'К',
        'l' to 'л', 'L' to 'Л',
        'm' to 'м', 'M' to 'М',
        'n' to 'н', 'N' to 'Н',
        'o' to 'о', 'O' to 'О',
        'p' to 'п', 'P' to 'П',
        'r' to 'р', 'R' to 'Р',
        's' to 'с', 'S' to 'С',
        't' to 'т', 'T' to 'Т',
        'u' to 'у', 'U' to 'У',
        'f' to 'ф', 'F' to 'Ф',
        'j' to 'й', 'J' to 'Й',
        'h' to 'х', 'H' to 'Х',
        'c' to 'ц', 'C' to 'Ц',
        'w' to 'в', 'W' to 'В',
        'q' to 'я', 'Q' to 'Я',
        'x' to 'х', 'X' to 'Х',
        '\'' to 'ь', '\"' to 'ъ',
        '`' to 'ё'
    )
    var result = input
    for ((key, value) in digraphs) {
        result = result.replace(key, value)
    }
    val sb = java.lang.StringBuilder()
    for (char in result) {
        sb.append(monografts[char] ?: char)
    }
    return sb.toString()
}

@Composable
fun CyrillicKeyboardPanel(onKeyTapped: (String) -> Unit, onBackspace: () -> Unit, onClose: () -> Unit, lang: String) {
    val keyboardRows = listOf(
        listOf("й", "ц", "у", "к", "е", "н", "г", "ш", "щ", "з", "х", "ъ"),
        listOf("ф", "ы", "в", "а", "п", "р", "о", "л", "д", "ж", "э"),
        listOf("я", "ч", "с", "м", "и", "т", "ь", "б", "ю", " ", "ё")
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = l("keyboard_cyrillic", lang),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row {
                    IconButton(onClick = onBackspace, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Backspace, "Backspace")
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }
            }
            
            keyboardRows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    row.forEach { char ->
                        Button(
                            onClick = { onKeyTapped(char) },
                            modifier = Modifier
                                .weight(if (char == " ") 2f else 1f)
                                .height(36.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (char == " ") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (char == " ") "Space" else char,
                                fontSize = 11.sp,
                                color = if (char == " ") Color.White else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardTabItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val contentColor = if (isSelected) {
        Color(0xFF11121C)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .height(72.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun EmptyStateIllustration() {
    Box(
        modifier = Modifier
            .size(170.dp)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        val pathColor = Color(0x33CAC4D0)
        val starColor = Color(0xFFA6ABB6)
        
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawContext.transform.rotate(15f, center)
            drawCircle(
                color = pathColor,
                radius = size.width * 0.42f,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            )
            drawContext.transform.rotate(-15f, center)
            
            drawFourPointStar(this, center.x - size.width * 0.38f, center.y - size.height * 0.15f, 5.dp.toPx(), starColor)
            drawFourPointStar(this, center.x + size.width * 0.15f, center.y - size.height * 0.32f, 7.dp.toPx(), starColor)
            drawFourPointStar(this, center.x + size.width * 0.32f, center.y + size.height * 0.12f, 5.dp.toPx(), starColor)
        }
        
        Box(
            modifier = Modifier
                .size(96.dp)
                .graphicsLayer {
                    rotationZ = -5f
                    shadowElevation = 8.dp.toPx()
                }
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF6B6E8F),
                            Color(0xFF383A59)
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .offset(x = 12.dp, y = (-7).dp)
                    .width(42.dp)
                    .height(14.dp)
                    .background(
                        color = Color(0xFF626584),
                        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                    )
            )
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp, start = 4.dp, end = 4.dp, bottom = 4.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF5A5C7E),
                                Color(0xFF2C2E4C)
                            )
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
            )
        }
    }
}

private fun drawFourPointStar(
    drawScope: androidx.compose.ui.graphics.drawscope.DrawScope,
    centerX: Float,
    centerY: Float,
    sizePx: Float,
    color: Color
) {
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(centerX, centerY - sizePx)
        quadraticTo(centerX, centerY, centerX + sizePx, centerY)
        quadraticTo(centerX, centerY, centerX, centerY + sizePx)
        quadraticTo(centerX, centerY, centerX - sizePx, centerY)
        quadraticTo(centerX, centerY, centerX, centerY - sizePx)
        close()
    }
    drawScope.drawPath(path = path, color = color)
}
