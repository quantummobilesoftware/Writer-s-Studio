package com.example.ui.screens

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.composed
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
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

fun Modifier.bounceClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "bounceScale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.foundation.LocalIndication.current,
            enabled = enabled,
            onClick = onClick
        )
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bounceCombinedClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "bounceScale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .combinedClickable(
            interactionSource = interactionSource,
            indication = androidx.compose.foundation.LocalIndication.current,
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick
        )
}

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
    val selectedProj by viewModel.selectedProject.collectAsStateWithLifecycle()
    val activeConflict by viewModel.activeConflict.collectAsStateWithLifecycle()

    val currentBg = when (themeMode) {
        "LIGHT" -> WriterThemeColors.LightBg
        "BLACK" -> Color(0xFF000000)
        else -> WriterThemeColors.DarkBg
    }

    val currentTextColors = when (themeMode) {
        "LIGHT" -> WriterThemeColors.LightText
        "BLACK" -> Color(0xFFFFFFFF)
        else -> WriterThemeColors.DarkText
    }

    val basePrimaryColor = when (colorPalette) {
        "BLUE" -> if (themeMode == "DARK" || themeMode == "BLACK") Color(0xFF8AB4F8) else Color(0xFF165EC0)
        "GREEN" -> if (themeMode == "DARK" || themeMode == "BLACK") Color(0xFF81C995) else Color(0xFF0F6D2E)
        "ORANGE" -> if (themeMode == "DARK" || themeMode == "BLACK") Color(0xFFFFB066) else Color(0xFFD35400)
        "RED" -> if (themeMode == "DARK" || themeMode == "BLACK") Color(0xFFFF8A80) else Color(0xFF962D22)
        "CORAL" -> if (themeMode == "DARK" || themeMode == "BLACK") Color(0xFFFE8B77) else Color(0xFFD85D4E)
        "GREY" -> if (themeMode == "DARK" || themeMode == "BLACK") Color(0xFFBEBEBE) else Color(0xFF707070)
        "YELLOW" -> if (themeMode == "DARK" || themeMode == "BLACK") Color(0xFFFAD02C) else Color(0xFFB57C00)
        "PINK" -> if (themeMode == "DARK" || themeMode == "BLACK") Color(0xFFFF80AC) else Color(0xFFD81B60)
        else -> if (themeMode == "DARK" || themeMode == "BLACK") WriterThemeColors.PrimaryAmber else WriterThemeColors.SecondarySlate
    }

    val activePrimaryColor = basePrimaryColor

    MaterialTheme(
        colorScheme = if (themeMode == "DARK" || themeMode == "BLACK") {
            darkColorScheme(
                primary = activePrimaryColor,
                onPrimary = if (themeMode == "BLACK") Color.Black else Color(0xFF381E72),
                background = if (themeMode == "BLACK") Color(0xFF000000) else WriterThemeColors.DarkBg,
                onBackground = if (themeMode == "BLACK") Color(0xFFFFFFFF) else WriterThemeColors.DarkText,
                surface = if (themeMode == "BLACK") Color(0xFF0C0C0C) else WriterThemeColors.DarkSurface,
                onSurface = if (themeMode == "BLACK") Color(0xFFFFFFFF) else WriterThemeColors.DarkText,
                primaryContainer = activePrimaryColor,
                onPrimaryContainer = if (themeMode == "BLACK") Color.Black else Color(0xFF381E72),
                secondaryContainer = if (themeMode == "BLACK") Color(0xFF1A1A1A) else Color(0xFF49454F),
                onSecondaryContainer = if (themeMode == "BLACK") Color(0xFFECECEC) else Color(0xFFCAC4D0),
                surfaceVariant = if (themeMode == "BLACK") Color(0xFF141414) else Color(0xFF211F26),
                onSurfaceVariant = if (themeMode == "BLACK") Color(0xFFCCCCCC) else Color(0xFFCAC4D0),
                outline = if (themeMode == "BLACK") Color(0xFF262626) else Color(0xFF49454F)
            )
        } else {
            lightColorScheme(
                primary = activePrimaryColor,
                background = WriterThemeColors.LightBg,
                surface = WriterThemeColors.LightSurface,
                onPrimary = Color.White,
                onBackground = WriterThemeColors.LightText,
                onSurface = WriterThemeColors.LightText
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

                activeConflict?.let { conflict ->
                    AlertDialog(
                        onDismissRequest = { /* Modal: force resolution selection */ },
                        title = {
                            Text(
                                text = if (viewModel.appLanguage.value == "ru") "Конфликт синхронизации" else "Sync Conflict",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = if (viewModel.appLanguage.value == "ru") {
                                        "Проект \"${conflict.localTitle}\" был изменен локально и в облаке с момента последней синхронизации. Пожалуйста, выберите действие:"
                                    } else {
                                        "The project \"${conflict.localTitle}\" was modified both locally and in the cloud since last sync. Please choose how to resolve:"
                                    },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (viewModel.appLanguage.value == "ru") {
                                        "• Локальная версия: ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(conflict.localUpdatedAt))}"
                                    } else {
                                        "• Local version: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(conflict.localUpdatedAt))}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                                Text(
                                    text = if (viewModel.appLanguage.value == "ru") {
                                        "• Облачная версия: ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(conflict.remoteModifiedTime))}"
                                    } else {
                                        "• Cloud version: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(conflict.remoteModifiedTime))}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        },
                        confirmButton = {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.resolveConflictWithLocal(conflict, context) },
                                    modifier = Modifier.fillMaxWidth().testTag("resolve_keep_local_btn"),
                                    colors = ButtonDefaults.buttonColors(containerColor = activePrimaryColor)
                                ) {
                                    Text(
                                        text = if (viewModel.appLanguage.value == "ru") "Оставить локальную версию" else "Keep Local Version",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                                Button(
                                    onClick = { viewModel.resolveConflictWithCloud(conflict, context) },
                                    modifier = Modifier.fillMaxWidth().testTag("resolve_keep_cloud_btn"),
                                    colors = ButtonDefaults.buttonColors(containerColor = activePrimaryColor)
                                ) {
                                    Text(
                                        text = if (viewModel.appLanguage.value == "ru") "Оставить облачную версию" else "Keep Cloud Version",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                                Button(
                                    onClick = { viewModel.resolveConflictWithMerge(conflict, context) },
                                    modifier = Modifier.fillMaxWidth().testTag("resolve_merge_btn"),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Text(
                                        text = if (viewModel.appLanguage.value == "ru") "Создать объединенную копию" else "Create Merged Copy",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        },
                        dismissButton = null,
                        modifier = Modifier.testTag("sync_conflict_dialog")
                    )
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
    val tabStack = remember { mutableStateListOf("PROJECTS") }
    val activeTab = tabStack.lastOrNull() ?: "PROJECTS"
    
    val selectedProj by viewModel.selectedProject.collectAsStateWithLifecycle()
    val activeDoc by viewModel.activeDocument.collectAsStateWithLifecycle()
    val isPrompterMode = remember { mutableStateOf(false) }
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val activeFolder by viewModel.selectedFolder.collectAsStateWithLifecycle()
    val folders by viewModel.currentFolders.collectAsStateWithLifecycle()

    fun selectTab(tab: String) {
        if (tabStack.lastOrNull() != tab) {
            tabStack.remove(tab)
            tabStack.add(tab)
        }
    }

    // Dynamic clean BackHandler routing
    if (isPrompterMode.value && activeDoc != null) {
        BackHandler {
            isPrompterMode.value = false
        }
    } else if (activeDoc != null) {
        BackHandler {
            viewModel.selectDocument(null)
        }
    } else if (selectedProj != null) {
        BackHandler {
            if (activeFolder != null) {
                viewModel.navigateBackFolder()
            } else {
                viewModel.selectProject(null)
            }
        }
    } else {
        BackHandler(enabled = tabStack.size > 1) {
            tabStack.removeAt(tabStack.lastIndex)
        }
    }

    val screenHierarchy = when {
        isPrompterMode.value && activeDoc != null -> 4
        activeDoc != null -> 3
        selectedProj != null -> 2
        else -> 1
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = screenHierarchy,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally(initialOffsetX = { it }) + fadeIn(animationSpec = tween(300)))
                        .togetherWith(slideOutHorizontally(targetOffsetX = { -it / 3 }) + fadeOut(animationSpec = tween(300)))
                } else {
                    (slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn(animationSpec = tween(300)))
                        .togetherWith(slideOutHorizontally(targetOffsetX = { it }) + fadeOut(animationSpec = tween(300)))
                }
            },
            label = "ScreenTransition"
        ) { targetHierarchy ->
            when (targetHierarchy) {
                4 -> {
                    PrompterScreen(viewModel = viewModel, onClose = { isPrompterMode.value = false })
                }
                3 -> {
                    DocumentEditorScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.selectDocument(null) },
                        onLaunchPrompter = { isPrompterMode.value = true }
                    )
                }
                2 -> {
                    ProjectWorkspaceScreen(viewModel = viewModel, onBack = { viewModel.selectProject(null) })
                }
                else -> {
                    Scaffold(
                        bottomBar = {
                            NavigationBar(
                                windowInsets = WindowInsets.navigationBars
                            ) {
                                NavigationBarItem(
                                    selected = activeTab == "PROJECTS",
                                    onClick = { selectTab("PROJECTS") },
                                    icon = { Icon(Icons.Outlined.Book, l("cabinet", appLanguage)) },
                                    label = { Text(l("cabinet", appLanguage)) },
                                    modifier = Modifier.testTag("nav_tab_projects")
                                )
                                NavigationBarItem(
                                    selected = activeTab == "STATS",
                                    onClick = { selectTab("STATS") },
                                    icon = { Icon(Icons.Outlined.BarChart, l("progress", appLanguage)) },
                                    label = { Text(l("progress", appLanguage)) },
                                    modifier = Modifier.testTag("nav_tab_stats")
                                )
                                NavigationBarItem(
                                    selected = activeTab == "SETTINGS",
                                    onClick = { selectTab("SETTINGS") },
                                    icon = { Icon(Icons.Outlined.Settings, l("options", appLanguage)) },
                                    label = { Text(l("options", appLanguage)) },
                                    modifier = Modifier.testTag("nav_tab_settings")
                                )
                            }
                        }
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                            val tabIndex = when (activeTab) {
                                "PROJECTS" -> 1
                                "STATS" -> 2
                                else -> 3
                            }
                            AnimatedContent(
                                targetState = tabIndex,
                                transitionSpec = {
                                    if (targetState > initialState) {
                                        (slideInHorizontally(initialOffsetX = { it }) + fadeIn(animationSpec = tween(300)))
                                            .togetherWith(slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(animationSpec = tween(300)))
                                    } else {
                                        (slideInHorizontally(initialOffsetX = { -it }) + fadeIn(animationSpec = tween(300)))
                                            .togetherWith(slideOutHorizontally(targetOffsetX = { it }) + fadeOut(animationSpec = tween(300)))
                                    }
                                },
                                label = "TabTransition",
                                modifier = Modifier.fillMaxSize()
                            ) { targetIndex ->
                                when (targetIndex) {
                                    1 -> ProjectsDashboardScreen(viewModel)
                                    2 -> WriterStatsScreen(viewModel)
                                    else -> AppSettingsScreen(viewModel, currTheme, onThemeChange)
                                }
                            }
                        }
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

    var showAccountDialog by remember { mutableStateOf(false) }
    val authorName by viewModel.authorName.collectAsStateWithLifecycle()
    val authorBio by viewModel.authorBio.collectAsStateWithLifecycle()
    val authorAvatar by viewModel.authorAvatar.collectAsStateWithLifecycle()
    val statsList by viewModel.statistics.collectAsStateWithLifecycle()

    var projectToRename by remember { mutableStateOf<WorkspaceProject?>(null) }
    var longPressedProject by remember { mutableStateOf<WorkspaceProject?>(null) }
    var projectToSendToTrash by remember { mutableStateOf<WorkspaceProject?>(null) }
    var projectToDeletePermanently by remember { mutableStateOf<WorkspaceProject?>(null) }

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
            "UPDATED" -> searched.sortedByDescending { it.updatedAt }
            else -> searched // MANUAL (default query order: sortOrder ASC, updatedAt DESC)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                ),
                navigationIcon = {
                    val iconVector = Icons.Outlined.AccountCircle
                    val contentDescription = "Profile Settings"
                    
                    if (interfaceStyle == "PIXEL") {
                        Box(
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), CircleShape)
                                .bounceClickable { showAccountDialog = true }
                                .testTag("account_icon_toggle_pixel"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (authorAvatar.isNotEmpty()) {
                                AsyncImage(
                                    model = authorAvatar,
                                    contentDescription = contentDescription,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = iconVector,
                                    contentDescription = contentDescription,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    } else {
                        IconButton(
                            onClick = { showAccountDialog = true },
                            modifier = Modifier.padding(start = 4.dp).testTag("account_icon_toggle_classic")
                        ) {
                            if (authorAvatar.isNotEmpty()) {
                                AsyncImage(
                                    model = authorAvatar,
                                    contentDescription = contentDescription,
                                    modifier = Modifier.size(28.dp).clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = iconVector,
                                    contentDescription = contentDescription,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
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
                                .bounceClickable { isSearchPanelVisible = !isSearchPanelVisible },
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
                            listOf(
                                "MANUAL" to (if (appLanguage == "ru") "Порядок" else "Manual"),
                                "UPDATED" to l("sort_changed", appLanguage),
                                "NAME" to l("sort_name", appLanguage),
                                "CREATED" to l("sort_created", appLanguage)
                            ).forEach { (sortId, label) ->
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
                            onTrashToggle = { projectToSendToTrash = proj },
                            onRestore = { viewModel.restoreFromTrash(proj) },
                            onDeletePermanent = { projectToDeletePermanently = proj },
                            isTrashMode = currentSubTab == "TRASH",
                            appLanguage = appLanguage,
                            onLongClick = { longPressedProject = proj },
                            onRenameToggle = { projectToRename = proj }
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
        val colorsPalette = listOf("#6200EE", "#D32F2F", "#388E3C", "#1976D2", "#FBC02D", "#7B1FA2", "#808080")
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
                        .navigationBarsPadding()
                        .imePadding()
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
                                label = { Text(l("project_title_hint", appLanguage), color = MaterialTheme.colorScheme.primary) }, 
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("new_project_title_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color(0xFF32333E),
                                    focusedContainerColor = Color(0xFF11121C).copy(alpha = 0.3f),
                                    unfocusedContainerColor = Color(0xFF11121C).copy(alpha = 0.3f),
                                    focusedLabelColor = MaterialTheme.colorScheme.primary,
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
                                            .bounceClickable { pType = typeId },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
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
                                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.Gray,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = label,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.Gray,
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
                                                .bounceClickable { selectedCol = col },
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
                                color = MaterialTheme.colorScheme.primary
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
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
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
                                .padding(top = 12.dp, bottom = 16.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { showCreateDialog = false }) {
                                Text(
                                    text = l("cancel", appLanguage),
                                    color = MaterialTheme.colorScheme.primary,
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

    if (showAccountDialog) {
        val googleUserId by viewModel.googleUserId.collectAsStateWithLifecycle()
        val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
        val cloudSyncEnabled by viewModel.cloudSyncEnabled.collectAsStateWithLifecycle()
        val authorEmail by viewModel.authorEmail.collectAsStateWithLifecycle()

        var editName by remember { mutableStateOf(authorName) }
        var editBio by remember { mutableStateOf(authorBio) }
        var editAvatarPath by remember { mutableStateOf(authorAvatar) }
        
        val localContext = LocalContext.current
        val avatarPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                val copiedPath = ImageStorageUtil.copyImageToProject(localContext, uri)
                if (copiedPath != null) {
                    editAvatarPath = copiedPath
                }
            }
        }

        var authErrorDialogMessage by remember { mutableStateOf<String?>(null) }

        val getAppSha1: (Context) -> String = { ctx ->
            try {
                val packageManager = ctx.packageManager
                val packageName = ctx.packageName
                @Suppress("DEPRECATION")
                val packageInfo = packageManager.getPackageInfo(
                    packageName, 
                    android.content.pm.PackageManager.GET_SIGNATURES
                )
                @Suppress("DEPRECATION")
                val signatures = packageInfo.signatures
                if (signatures != null && signatures.isNotEmpty()) {
                    val sig = signatures[0]
                    val md = java.security.MessageDigest.getInstance("SHA-1")
                    val digest = md.digest(sig.toByteArray())
                    val sb = StringBuilder()
                    for (b in digest) {
                        sb.append(String.format("%02X:", b))
                    }
                    if (sb.isNotEmpty()) sb.setLength(sb.length - 1)
                    sb.toString()
                } else {
                    "No signatures found"
                }
            } catch (e: Exception) {
                android.util.Log.e("GoogleSignIn", "Error getting SHA-1 Signature", e)
                "Error: ${e.message}"
            }
        }

        val googleSignInLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            android.util.Log.d("GoogleSignIn", "[Auth Result] Received result from Google Sign-In activity. ResultCode: ${result.resultCode}")
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                if (account != null) {
                    val email = account.email ?: ""
                    val displayName = account.displayName ?: "Google User"
                    val photoUrl = account.photoUrl?.toString() ?: ""
                    val gId = account.id ?: ""
                    android.util.Log.i("GoogleSignIn", "[Auth Succeeded] Selected Account: Email='$email', Name='$displayName', ID='$gId'")

                    viewModel.linkGoogleAccount(localContext, gId, displayName, email, photoUrl)
                    editName = displayName
                    editAvatarPath = photoUrl
                } else {
                    android.util.Log.w("GoogleSignIn", "[Auth Succeeded Null] Google result succeeded but returned completely empty account.")
                    authErrorDialogMessage = "Received empty account session from Google API."
                }
            } catch (e: com.google.android.gms.common.api.ApiException) {
                val statusCode = e.statusCode
                val statusMessage = com.google.android.gms.common.api.CommonStatusCodes.getStatusCodeString(statusCode)
                android.util.Log.e("GoogleSignIn", "[Auth Failed] ApiException code=$statusCode ($statusMessage). message=${e.message}", e)
                
                authErrorDialogMessage = when (statusCode) {
                    10 -> "Google Sign-In returned Code 10 (DEVELOPER_ERROR).\n\n" +
                            "This means the SHA-1 of your signing key (${getAppSha1(localContext)}) or App Package Name (com.aistudio.writerstudio.gmqfkv) is not registered in the Google Developer Console Client IDs."
                    7 -> "Network error during Google Sign-In. Please check your connection."
                    12501 -> "Google Sign-In was cancelled by the user."
                    else -> "Google API ApiException: ${e.message} (Code $statusCode: $statusMessage)"
                }
            } catch (e: Exception) {
                android.util.Log.e("GoogleSignIn", "[Auth Failed] General unexpected Exception: ${e.message}", e)
                authErrorDialogMessage = "An error occurred during Google Auth: ${e.message}"
            }
        }
        
        val totalWords = remember(statsList) { statsList.sumOf { it.wordsCount } }
        val totalProjectsCount = remember(activeProjs, archivedProjs, trashProjs) { 
            activeProjs.size + archivedProjs.size + trashProjs.size 
        }

        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showAccountDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { showAccountDialog = false },
                contentAlignment = Alignment.Center
            ) {
                val containerColorVal = MaterialTheme.colorScheme.primaryContainer
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .widthIn(max = 400.dp)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {}
                        .wrapContentHeight(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 12.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                val gradientBrush = Brush.verticalGradient(
                                    colors = listOf(
                                        containerColorVal.copy(alpha = 0.25f),
                                        containerColorVal.copy(alpha = 0.02f),
                                        Color.Transparent
                                    ),
                                    startY = 0f,
                                    endY = 120.dp.toPx()
                                )
                                drawRect(
                                    brush = gradientBrush,
                                    size = size.copy(height = 120.dp.toPx())
                                )
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            // Header segment containing Title and Close IconButton
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(end = 40.dp),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text(
                                        text = when (appLanguage) {
                                            "ru" -> "Профиль Автора"
                                            "es" -> "Perfil de Autor"
                                            else -> "Author Profile"
                                        },
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = when (appLanguage) {
                                            "ru" -> "Ваша творческая визитная карточка"
                                            "es" -> "Tu firma creativa"
                                            else -> "Your creative signature"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }
                                IconButton(
                                    onClick = { showAccountDialog = false },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .background(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                            shape = CircleShape
                                        )
                                        .size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // Modern dual-ring Avatar area
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(96.dp)
                                        .bounceClickable { avatarPickerLauncher.launch("image/*") }
                                        .testTag("select_avatar_trigger"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
                                            .padding(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (editAvatarPath.isNotEmpty()) {
                                                AsyncImage(
                                                    model = editAvatarPath,
                                                    contentDescription = "Profile Picture",
                                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Text(
                                                    text = if (editName.isNotEmpty()) editName.take(1).uppercase() else "A",
                                                    style = MaterialTheme.typography.headlineLarge,
                                                    fontWeight = FontWeight.Black,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                    
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(28.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        tonalElevation = 4.dp,
                                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface)
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit photo",
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                                
                                Text(
                                    text = when (appLanguage) {
                                        "ru" -> "Изменить фото"
                                        "es" -> "Cambiar foto"
                                        else -> "Change photo"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clickable { avatarPickerLauncher.launch("image/*") }
                                        .padding(vertical = 2.dp)
                                )
                            }

                            // Sleek inline Statistics Bar
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                ),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Edit,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = totalWords.toString(),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = when (appLanguage) {
                                                "ru" -> "Всего слов"
                                                "es" -> "Palabras"
                                                else -> "Total words"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .height(32.dp)
                                            .width(1.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    )
                                    
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = totalProjectsCount.toString(),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = when (appLanguage) {
                                                "ru" -> "Проектов"
                                                "es" -> "Proyectos"
                                                else -> "Projects"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }

                            // Form input text fields
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                OutlinedTextField(
                                    value = editName,
                                    onValueChange = { editName = it },
                                    label = {
                                        Text(
                                            when (appLanguage) {
                                                "ru" -> "Псевдоним / Имя писателя"
                                                "es" -> "Seudónimo"
                                                else -> "Pen Name"
                                            }
                                        )
                                    },
                                    placeholder = {
                                        Text(
                                            when (appLanguage) {
                                                "ru" -> "Введите псевдоним"
                                                "es" -> "Introduce un seudónimo"
                                                else -> "Enter short pen name"
                                            }
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("profile_name_input"),
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                    )
                                )

                                OutlinedTextField(
                                    value = editBio,
                                    onValueChange = { editBio = it },
                                    label = {
                                        Text(
                                            when (appLanguage) {
                                                "ru" -> "Девиз или О себе"
                                                "es" -> "Biografía / Lema"
                                                else -> "Motto or Bio"
                                            }
                                        )
                                    },
                                    placeholder = {
                                        Text(
                                            when (appLanguage) {
                                                "ru" -> "Ваше творческое кредо..."
                                                "es" -> "Tu credo creativo..."
                                                else -> "Your creative motto..."
                                            }
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("profile_bio_input"),
                                    shape = RoundedCornerShape(14.dp),
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Edit,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                    )
                                )
                            }

                            // Google Sync & Account Management Card
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("google_sync_card"),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Cloud,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = when (appLanguage) {
                                                "ru" -> "Синхронизация Google Drive"
                                                "es" -> "Sincronización de Google Drive"
                                                else -> "Google Drive Sync"
                                            },
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isSyncing) {
                                            Spacer(modifier = Modifier.weight(1f))
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    if (googleUserId.isNotEmpty()) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            // Connected Account Card Layout
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                                        RoundedCornerShape(12.dp)
                                                    )
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                // User Profile Photo
                                                Box(
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (authorAvatar.isNotEmpty()) {
                                                        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                                                            coil.compose.AsyncImage(
                                                                model = authorAvatar,
                                                                contentDescription = "Avatar",
                                                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                            )
                                                        }
                                                    } else {
                                                        Icon(
                                                            imageVector = Icons.Default.AccountCircle,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(32.dp)
                                                        )
                                                    }
                                                }

                                                // User metadata info
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = if (authorName.isNotEmpty()) authorName else "Google User",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = authorEmail,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(6.dp)
                                                                .clip(CircleShape)
                                                                .background(
                                                                    if (isSyncing) MaterialTheme.colorScheme.primary 
                                                                    else Color(0xFF4CAF50)
                                                                )
                                                        )
                                                        Text(
                                                            text = if (isSyncing) {
                                                                when (appLanguage) {
                                                                    "ru" -> "Синхронизация..."
                                                                    "es" -> "Sincronizando..."
                                                                    else -> "Syncing..."
                                                                }
                                                            } else {
                                                                when (appLanguage) {
                                                                    "ru" -> "Подключено"
                                                                    "es" -> "Conectado"
                                                                    else -> "Connected"
                                                                }
                                                            },
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Medium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }

                                            // Sync Toggles / Switches
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = when (appLanguage) {
                                                        "ru" -> "Резервное копирование"
                                                        "es" -> "Sincronización"
                                                        else -> "Auto-Backup Enabled"
                                                    },
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Switch(
                                                    checked = cloudSyncEnabled,
                                                    onCheckedChange = { viewModel.setCloudSyncEnabled(it) },
                                                    modifier = Modifier.testTag("sync_switch")
                                                )
                                            }

                                            // Action Buttons Row (Sync, Switch Account, Sign Out)
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // Sync Now button
                                                Button(
                                                    onClick = { 
                                                        android.util.Log.i("GoogleSignIn", "[Sync Action] User clicked Sync Now button manually.")
                                                        viewModel.syncAllWithDrive(localContext) 
                                                    },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(38.dp)
                                                        .testTag("sync_now_button"),
                                                    shape = RoundedCornerShape(19.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.primary,
                                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                ) {
                                                    Row(
                                                        horizontalArrangement = Arrangement.Center,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Refresh,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = when (appLanguage) {
                                                                "ru" -> "Синхронизировать сейчас"
                                                                "es" -> "Sincronizar ahora"
                                                                else -> "Sync Now"
                                                            },
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    // Switch Account Button
                                                    OutlinedButton(
                                                        onClick = {
                                                            android.util.Log.i("GoogleSignIn", "[Auth Action] User requested to Switch Account.")
                                                            val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                                                                .requestEmail()
                                                                .requestProfile()
                                                                .requestScopes(com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.file"))
                                                                .build()
                                                            val googleSignInClient = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(localContext, gso)
                                                            googleSignInClient.signOut().addOnCompleteListener {
                                                                googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                                            }
                                                        },
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(38.dp)
                                                            .testTag("switch_account_button"),
                                                        shape = RoundedCornerShape(19.dp),
                                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                                    ) {
                                                        Text(
                                                            text = when (appLanguage) {
                                                                "ru" -> "Сменить аккаунт"
                                                                "es" -> "Cambiar cuenta"
                                                                else -> "Switch Account"
                                                            },
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }

                                                    // Sign Out / Disconnect Button
                                                    OutlinedButton(
                                                        onClick = { 
                                                            android.util.Log.i("GoogleSignIn", "[Auth Action] User clicked Disconnect / Sign Out.")
                                                            viewModel.disconnectGoogleAccount(localContext) 
                                                        },
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(38.dp)
                                                            .testTag("sign_out_button"),
                                                        shape = RoundedCornerShape(19.dp),
                                                        colors = ButtonDefaults.outlinedButtonColors(
                                                            contentColor = MaterialTheme.colorScheme.error
                                                        ),
                                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                                                    ) {
                                                        Text(
                                                            text = when (appLanguage) {
                                                                "ru" -> "Выйти"
                                                                "es" -> "Cerrar sesión"
                                                                else -> "Sign Out"
                                                            },
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        // Standard Sign-on Button
                                        Button(
                                            onClick = {
                                                android.util.Log.d("GoogleSignIn", "[Auth Action] User initiating Google Sign-In with scopes.")
                                                val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                                                    .requestEmail()
                                                    .requestProfile()
                                                    .requestScopes(com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.file"))
                                                    .build()
                                                val googleSignInClient = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(localContext, gso)
                                                googleSignInClient.signOut().addOnCompleteListener {
                                                    googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("google_signin_button"),
                                            shape = RoundedCornerShape(24.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            ),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp)
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AccountCircle,
                                                    contentDescription = "Google Logo",
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = when (appLanguage) {
                                                        "ru" -> "Войти через Google"
                                                        "es" -> "Iniciar sesión con Google"
                                                        else -> "Sign in with Google"
                                                    },
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.labelLarge
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Action pill-shaped buttons Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { showAccountDialog = false },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(24.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = when (appLanguage) {
                                            "ru" -> "Отмена"
                                            "es" -> "Cancelar"
                                            else -> "Cancel"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = {
                                        viewModel.setAuthorProfile(editName, editBio, authorEmail)
                                        viewModel.setAuthorAvatar(editAvatarPath)
                                        showAccountDialog = false
                                    },
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .height(48.dp)
                                        .testTag("save_profile_button"),
                                    shape = RoundedCornerShape(24.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(
                                        defaultElevation = 2.dp,
                                        pressedElevation = 4.dp
                                    )
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = when (appLanguage) {
                                                "ru" -> "Сохранить"
                                                "es" -> "Guardar"
                                                else -> "Save"
                                            },
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (authErrorDialogMessage != null) {
                AlertDialog(
                    onDismissRequest = { authErrorDialogMessage = null },
                    title = {
                        Text(
                            text = when (appLanguage) {
                                "ru" -> "Настройка авторизации"
                                "es" -> "Configuración"
                                else -> "Auth Setup Required"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = authErrorDialogMessage!!, style = MaterialTheme.typography.bodyMedium)
                            
                            androidx.compose.material3.HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp), 
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            
                            Text(
                                text = "SHA-1 Fingerprint of this build:\n" + try { getAppSha1(localContext) } catch(e: Exception) { "Unknown" },
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            modifier = Modifier.testTag("sandbox_auth_login"),
                            onClick = {
                                val simulatedEmail = "sandbox.writer@example.com"
                                val simulatedName = "Ivan Petrov"
                                val simulatedAvatar = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde"
                                val simulatedId = "simulated_google_id_123456"
                                android.util.Log.i("GoogleSignIn", "[Auth Sandbox] Falling back to automated Developer Sandbox link with email: $simulatedEmail")
                                viewModel.linkGoogleAccount(localContext, simulatedId, simulatedName, simulatedEmail, simulatedAvatar)
                                editName = simulatedName
                                editAvatarPath = simulatedAvatar
                                authErrorDialogMessage = null
                            }
                        ) {
                            Text(
                                text = when (appLanguage) {
                                    "ru" -> "Вход через Симулятор"
                                    "es" -> "Usar Simulador"
                                    else -> "Use Simulator"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    dismissButton = {
                        Button(
                            onClick = { authErrorDialogMessage = null },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text(
                                text = when (appLanguage) {
                                    "ru" -> "Закрыть"
                                    "es" -> "Cerrar"
                                    else -> "Close"
                                }
                            )
                        }
                    }
                )
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

    if (projectToRename != null) {
        val proj = projectToRename!!
        var newTitle by remember(proj.title) { mutableStateOf(proj.title) }
        AlertDialog(
            onDismissRequest = { projectToRename = null },
            title = {
                Text(
                    text = if (appLanguage == "ru") "Переименовать проект" else "Rename Project",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (appLanguage == "ru") "Введите новое название проекта:" else "Enter new project title:",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rename_project_title_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameProject(proj, newTitle)
                        projectToRename = null
                    },
                    enabled = newTitle.isNotBlank(),
                    modifier = Modifier.testTag("confirm_rename_project_btn")
                ) {
                    Text(if (appLanguage == "ru") "Сохранить" else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToRename = null }) {
                    Text(l("cancel", appLanguage))
                }
            }
        )
    }

    if (longPressedProject != null) {
        val proj = longPressedProject!!
        AlertDialog(
            onDismissRequest = { longPressedProject = null },
            title = {
                Text(
                    text = proj.title,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (appLanguage == "ru") "Действия проекта:" else "Project actions:",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    
                    // Rename Option
                    Button(
                        onClick = {
                            projectToRename = proj
                            longPressedProject = null
                        },
                        modifier = Modifier.fillMaxWidth().testTag("long_press_action_rename"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                            Text(if (appLanguage == "ru") "Переименовать" else "Rename", fontSize = 14.sp)
                        }
                    }

                    // Move Up (Переместить выше) Option
                    val currentList = filteredList
                    val index = currentList.indexOfFirst { it.id == proj.id }
                    val canMoveUp = index > 0
                    
                    Button(
                        onClick = {
                            viewModel.moveProject(proj, up = true, currentSubTab = currentSubTab)
                            longPressedProject = null
                        },
                        enabled = canMoveUp,
                        modifier = Modifier.fillMaxWidth().testTag("long_press_action_move_up"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.ArrowUpward, null, modifier = Modifier.size(18.dp))
                            Text(if (appLanguage == "ru") "Переместить выше" else "Move Up", fontSize = 14.sp)
                        }
                    }

                    // Move Down (Переместить ниже) Option
                    val canMoveDown = index != -1 && index < currentList.size - 1
                    
                    Button(
                        onClick = {
                            viewModel.moveProject(proj, up = false, currentSubTab = currentSubTab)
                            longPressedProject = null
                        },
                        enabled = canMoveDown,
                        modifier = Modifier.fillMaxWidth().testTag("long_press_action_move_down"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.ArrowDownward, null, modifier = Modifier.size(18.dp))
                            Text(if (appLanguage == "ru") "Переместить ниже" else "Move Down", fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { longPressedProject = null }) {
                    Text(l("close", appLanguage))
                }
            }
        )
    }

    if (projectToSendToTrash != null) {
        val proj = projectToSendToTrash!!
        AlertDialog(
            onDismissRequest = { projectToSendToTrash = null },
            title = {
                Text(
                    text = if (appLanguage == "ru") "Удалить проект?" else "Delete Project?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (appLanguage == "ru") {
                        "Вы действительно хотите переместить проект \"${proj.title}\" в корзину?"
                    } else {
                        "Are you sure you want to move the project \"${proj.title}\" to trash?"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.sendToTrash(proj)
                        projectToSendToTrash = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("confirm_send_to_trash_btn")
                ) {
                    Text(if (appLanguage == "ru") "Удалить" else "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToSendToTrash = null }) {
                    Text(l("cancel", appLanguage))
                }
            }
        )
    }

    if (projectToDeletePermanently != null) {
        val proj = projectToDeletePermanently!!
        AlertDialog(
            onDismissRequest = { projectToDeletePermanently = null },
            title = {
                Text(
                    text = if (appLanguage == "ru") "Удалить проект навсегда?" else "Delete Project Permanently?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (appLanguage == "ru") {
                        "Вы действительно хотите безвозвратно удалить проект \"${proj.title}\"? Это действие нельзя отменить!"
                    } else {
                        "Are you sure you want to permanently delete the project \"${proj.title}\"? This action cannot be undone!"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.permanentDeleteProject(proj)
                        projectToDeletePermanently = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("confirm_hard_delete_btn")
                ) {
                    Text(if (appLanguage == "ru") "Стереть" else "Erase")
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDeletePermanently = null }) {
                    Text(l("cancel", appLanguage))
                }
            }
        )
    }
}

// --- PROJECT ROW ITEM ---
@OptIn(ExperimentalFoundationApi::class)
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
    appLanguage: String,
    onLongClick: () -> Unit,
    onRenameToggle: () -> Unit
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
                .bounceCombinedClickable(
                    onClick = onExplore,
                    onLongClick = onLongClick
                )
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

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = project.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!project.passwordHash.isNullOrEmpty()) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = l("protected_project", appLanguage),
                            modifier = Modifier.size(14.dp),
                            tint = Color.Gray
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(projectColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = typeLabel.uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = projectColor,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = "${l("modified", appLanguage)}: ${SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault()).format(Date(project.updatedAt))}",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isTrashMode) {
                IconButton(onClick = onRestore, modifier = Modifier.testTag("restore_project_btn")) {
                    Icon(Icons.Outlined.Restore, l("restore", appLanguage), tint = Color.Gray)
                }
                IconButton(onClick = onDeletePermanent, modifier = Modifier.testTag("hard_delete_project_btn")) {
                    Icon(Icons.Outlined.DeleteForever, l("delete_permanent", appLanguage), tint = Color.Gray)
                }
            } else {
                IconButton(onClick = onRenameToggle, modifier = Modifier.testTag("rename_project_btn")) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Rename Project",
                        tint = Color.Gray
                    )
                }
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
    var documentToDelete by remember { mutableStateOf<Document?>(null) }
    var folderToDelete by remember { mutableStateOf<Folder?>(null) }
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()

    val workspaceSortBy by viewModel.workspaceSortBy.collectAsStateWithLifecycle()

    var showRenameFolderDialog by remember { mutableStateOf(false) }
    var folderToRename by remember { mutableStateOf<Folder?>(null) }
    var showRenameDocumentDialog by remember { mutableStateOf(false) }
    var documentToRename by remember { mutableStateOf<Document?>(null) }
    var renameInputText by remember { mutableStateOf("") }

    val rawLevelFolders = remember(folders, activeFolder) {
        folders.filter { it.parentFolderId == activeFolder?.id }
    }

    val rawLevelDocuments = if (activeFolder == null) rootDocs else folderDocs

    val currentLevelFolders = remember(rawLevelFolders, workspaceSortBy) {
        when (workspaceSortBy) {
            "NAME_ASC" -> rawLevelFolders.sortedBy { it.name.lowercase() }
            "NAME_DESC" -> rawLevelFolders.sortedByDescending { it.name.lowercase() }
            "DATE_CREATED_DESC" -> rawLevelFolders.sortedByDescending { it.createdAt }
            "DATE_CREATED_ASC" -> rawLevelFolders.sortedBy { it.createdAt }
            "DATE_MODIFIED_DESC" -> rawLevelFolders.sortedByDescending { it.createdAt }
            else -> rawLevelFolders.sortedBy { it.sortOrder }
        }
    }

    val currentLevelDocuments = remember(rawLevelDocuments, workspaceSortBy) {
        when (workspaceSortBy) {
            "NAME_ASC" -> rawLevelDocuments.sortedBy { it.title.lowercase() }
            "NAME_DESC" -> rawLevelDocuments.sortedByDescending { it.title.lowercase() }
            "DATE_CREATED_DESC" -> rawLevelDocuments.sortedByDescending { it.createdAt }
            "DATE_CREATED_ASC" -> rawLevelDocuments.sortedBy { it.createdAt }
            "DATE_MODIFIED_DESC" -> rawLevelDocuments.sortedByDescending { it.updatedAt }
            else -> rawLevelDocuments.sortedBy { it.sortOrder }
        }
    }

    val projectColor = MaterialTheme.colorScheme.primary

    val projectDotColor = remember(project) {
        val hex = project?.colorHex ?: "#6200EE"
        try {
            Color(android.graphics.Color.parseColor(hex))
        } catch (e: Exception) {
            Color(0xFF6200EE)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = project?.title ?: l("project_docs", appLanguage),
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(projectDotColor)
                            )
                            val catLabel = l("cat_${(project?.type ?: "BOOK").lowercase()}", appLanguage)
                            Text(
                                text = catLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (activeFolder != null) {
                            viewModel.navigateBackFolder()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, l("close", appLanguage))
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateFolderDialog = true }) {
                        Icon(Icons.Filled.CreateNewFolder, l("create_folder_title", appLanguage), tint = projectColor)
                    }
                    IconButton(onClick = { showCreateDocumentDialog = true }, modifier = Modifier.testTag("workspace_create_doc_btn")) {
                        Icon(Icons.Filled.NoteAdd, l("create_doc_title", appLanguage), tint = projectColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        )
                    )
                )
                .padding(paddingValues)
        ) {
            // Elegant breadcrumbs navigation bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .bounceClickable { viewModel.selectFolder(null) }
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = l("project_root", appLanguage),
                            modifier = Modifier.size(16.dp),
                            tint = projectColor
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = l("project_root", appLanguage),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (activeFolder != null) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowRight,
                            contentDescription = "nav_arrow",
                            modifier = Modifier.size(16.dp).padding(horizontal = 2.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = projectColor.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, projectColor.copy(alpha = 0.25f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = "Folder",
                                    modifier = Modifier.size(14.dp),
                                    tint = projectColor
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = activeFolder?.name ?: "",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = projectColor
                                )
                            }
                        }
                    }
                }
            }

            // Sort selection row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sort,
                    contentDescription = "Sort Icon",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (appLanguage == "ru") "Сортировка: " else "Sort by: ",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val sModes = listOf(
                        "MANUAL" to (if (appLanguage == "ru") "Порядок" else "Manual"),
                        "NAME_ASC" to (if (appLanguage == "ru") "А-Я" else "A-Z"),
                        "NAME_DESC" to (if (appLanguage == "ru") "Я-А" else "Z-A"),
                        "DATE_CREATED_DESC" to (if (appLanguage == "ru") "Новые" else "Newest"),
                        "DATE_CREATED_ASC" to (if (appLanguage == "ru") "Старые" else "Oldest"),
                        "DATE_MODIFIED_DESC" to (if (appLanguage == "ru") "Недавние" else "Recent")
                    )
                    items(sModes) { (sortId, label) ->
                        val isSelected = workspaceSortBy == sortId
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setWorkspaceSortBy(sortId) },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
            }

            if (currentLevelFolders.isEmpty() && currentLevelDocuments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Graphic Canvas empty state illustration with subtle glowing aura
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(140.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                                    .background(
                                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                            colors = listOf(
                                                projectColor.copy(alpha = 0.22f),
                                                projectColor.copy(alpha = 0.05f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                            androidx.compose.foundation.Canvas(modifier = Modifier.size(116.dp)) {
                                drawCircle(
                                    color = projectColor.copy(alpha = 0.15f),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = 1.5.dp.toPx(),
                                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                            floatArrayOf(15f, 15f), 0f
                                        )
                                    )
                                )
                            }
                            Surface(
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 4.dp,
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, projectColor.copy(alpha = 0.35f)),
                                modifier = Modifier.size(72.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.EditNote,
                                        contentDescription = "Empty Folder",
                                        modifier = Modifier.size(38.dp),
                                        tint = projectColor
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = l("create_work_hint", appLanguage),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        
                        Text(
                            text = if (appLanguage == "ru") "Создайте папки для организации ваших идей и пишите в отдельных документах!" else "Structure your project layout using folders and draft documents for any specific scene!",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            ),
                            modifier = Modifier.padding(top = 6.dp, bottom = 24.dp, start = 24.dp, end = 24.dp)
                        )

                        // Elegant Empty-state Context Actions directly on screen
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showCreateFolderDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, projectColor.copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = projectColor
                                )
                            ) {
                                Icon(Icons.Filled.CreateNewFolder, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(l("create_folder_title", appLanguage), fontSize = 11.sp, maxLines = 1)
                            }
                            
                            Button(
                                onClick = { showCreateDocumentDialog = true },
                                modifier = Modifier.weight(1f).testTag("workspace_create_doc_btn"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = projectColor,
                                    contentColor = if (((projectColor.red * 0.299f + projectColor.green * 0.587f + projectColor.blue * 0.114f) > 0.5f)) Color.Black else Color.White
                                )
                            ) {
                                Icon(Icons.Filled.NoteAdd, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(l("create_doc_title", appLanguage), fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    // List Folders first
                    items(currentLevelFolders) { fold ->
                        Card(
                            onClick = { viewModel.selectFolder(fold) },
                            modifier = Modifier
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = projectColor.copy(alpha = 0.15f),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = l("folders", appLanguage),
                                            tint = projectColor,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = fold.name,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = l("project_folder", appLanguage),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                                
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (workspaceSortBy == "MANUAL") {
                                        IconButton(
                                            onClick = { viewModel.moveFolder(fold, up = true) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowUpward,
                                                contentDescription = "Move Up",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.moveFolder(fold, up = false) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDownward,
                                                contentDescription = "Move Down",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            folderToRename = fold
                                            renameInputText = fold.name
                                            showRenameFolderDialog = true
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Rename",
                                            tint = projectColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { folderToDelete = fold },
                                        modifier = Modifier
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f))
                                            .size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = l("delete", appLanguage),
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
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
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .width(4.dp)
                                        .height(64.dp)
                                        .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                                        .background(projectColor)
                                )
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.size(42.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (doc.isPlainText) Icons.AutoMirrored.Filled.Notes else Icons.Default.EditNote,
                                                contentDescription = l("document", appLanguage),
                                                tint = projectColor,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.width(12.dp))
                                    
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = doc.title,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1.5f, fill = false),
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                            
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (doc.isPlainText) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            ) {
                                                Text(
                                                    text = if (doc.isPlainText) "Basic TXT" else "Pro Block",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (doc.isPlainText) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.height(2.dp))
                                        
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Schedule,
                                                contentDescription = "Time",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = "${l("modified", appLanguage)}: ${SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault()).format(Date(doc.updatedAt))}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                    
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (workspaceSortBy == "MANUAL") {
                                            IconButton(
                                                onClick = { viewModel.moveDocument(doc, up = true) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowUpward,
                                                    contentDescription = "Move Up",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { viewModel.moveDocument(doc, up = false) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowDownward,
                                                    contentDescription = "Move Down",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                documentToRename = doc
                                                renameInputText = doc.title
                                                showRenameDocumentDialog = true
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Rename",
                                                tint = projectColor,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { documentToDelete = doc },
                                            modifier = Modifier
                                                .clip(androidx.compose.foundation.shape.CircleShape)
                                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f))
                                                .size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = l("delete", appLanguage),
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
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
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = l("create_folder_title", appLanguage),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text(l("folder_name", appLanguage)) },
                    modifier = Modifier.fillMaxWidth().testTag("new_folder_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderName.isNotEmpty()) {
                            viewModel.createFolder(folderName)
                            showCreateFolderDialog = false
                        }
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(l("create", appLanguage))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text(l("cancel", appLanguage))
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // CREATE DOCUMENT DIALOG
    if (showCreateDocumentDialog) {
        val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
        var docName by remember { mutableStateOf("") }
        var isPlainText by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showCreateDocumentDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = l("create_doc_title", appLanguage),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = docName,
                        onValueChange = { docName = it },
                        label = { Text(l("doc_name", appLanguage)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_document_name_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Text(
                        text = l("select_editor_mode", appLanguage),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { isPlainText = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isPlainText) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (!isPlainText) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(l("type_doc", appLanguage), fontSize = 11.sp, maxLines = 1)
                        }

                        Button(
                            onClick = { isPlainText = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPlainText) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isPlainText) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(l("type_txt", appLanguage), fontSize = 11.sp, maxLines = 1)
                        }
                    }

                    Text(
                        text = if (isPlainText) l("basic_mode_desc", appLanguage) else l("pro_mode_desc", appLanguage),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
                    modifier = Modifier.testTag("confirm_create_document_button"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(l("create", appLanguage))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDocumentDialog = false }) {
                    Text(l("cancel", appLanguage))
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // DELETE DOCUMENT DIALOG
    if (documentToDelete != null) {
        val doc = documentToDelete!!
        AlertDialog(
            onDismissRequest = { documentToDelete = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = if (appLanguage == "ru") "Удалить документ?" else "Delete Document?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            text = {
                Text(
                    text = if (appLanguage == "ru") {
                        "Вы действительно хотите удалить документ \"${doc.title}\"? Это действие нельзя отменить."
                    } else {
                        "Are you sure you want to delete the document \"${doc.title}\"? This action cannot be undone."
                    },
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDocument(doc.id)
                        documentToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (appLanguage == "ru") "Удалить" else "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { documentToDelete = null }) {
                    Text(l("cancel", appLanguage))
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // DELETE FOLDER DIALOG
    if (folderToDelete != null) {
        val fold = folderToDelete!!
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = if (appLanguage == "ru") "Удалить папку?" else "Delete Folder?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            text = {
                Text(
                    text = if (appLanguage == "ru") {
                        "Вы действительно хотите удалить папку \"${fold.name}\" и все её содержимое? Это действие нельзя отменить."
                    } else {
                        "Are you sure you want to delete the folder \"${fold.name}\" and all its contents? This action cannot be undone."
                    },
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteFolder(fold.id)
                        folderToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("confirm_delete_folder_btn"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (appLanguage == "ru") "Удалить" else "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) {
                    Text(l("cancel", appLanguage))
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // RENAME FOLDER DIALOG
    if (showRenameFolderDialog && folderToRename != null) {
        var folderName by remember { mutableStateOf(renameInputText) }
        AlertDialog(
            onDismissRequest = { showRenameFolderDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = if (appLanguage == "ru") "Переименовать папку" else "Rename Folder",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text(l("folder_name", appLanguage)) },
                    modifier = Modifier.fillMaxWidth().testTag("rename_folder_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderName.isNotEmpty()) {
                            viewModel.renameFolder(folderToRename!!, folderName)
                            showRenameFolderDialog = false
                        }
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (appLanguage == "ru") "Сохранить" else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameFolderDialog = false }) {
                    Text(l("cancel", appLanguage))
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // RENAME DOCUMENT DIALOG
    if (showRenameDocumentDialog && documentToRename != null) {
        var docName by remember { mutableStateOf(renameInputText) }
        AlertDialog(
            onDismissRequest = { showRenameDocumentDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = if (appLanguage == "ru") "Переименовать файл" else "Rename Document",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                OutlinedTextField(
                    value = docName,
                    onValueChange = { docName = it },
                    label = { Text(l("doc_name", appLanguage)) },
                    modifier = Modifier.fillMaxWidth().testTag("rename_document_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (docName.isNotEmpty()) {
                            viewModel.renameDocumentInWorkspace(documentToRename!!, docName)
                            showRenameDocumentDialog = false
                        }
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (appLanguage == "ru") "Сохранить" else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDocumentDialog = false }) {
                    Text(l("cancel", appLanguage))
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

// --- DOCUMENT RICH TEXT EDITOR SCREEN ---
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()

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

    // Custom tabs and reading setup
    var editorTab by remember { mutableStateOf("BLOCKS") } // "BLOCKS", "DOCUMENT"
    var isReadingMode by remember { mutableStateOf(false) }
    var readFontSizeScale by remember { mutableStateOf(16f) }
    var showAddBlockBottomSheet by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportFormat by remember { mutableStateOf("PDF") } // "PDF", "DOCX", "RTF", "TXT"
    var exportPageSize by remember { mutableStateOf("A4") } // "A4", "LETTER"
    var exportFontFamily by remember { mutableStateOf("SERIF") } // "SERIF", "SANS_SERIF", "MONOSPACE"
    var exportLineSpacing by remember { mutableStateOf(1.15f) } // 1.0f, 1.15f, 1.5f, 2.0f
    var exportIncludePageNumbers by remember { mutableStateOf(true) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameDocName by remember(document?.title, showRenameDialog) { mutableStateOf(document?.title ?: "") }
    
    var showCreateDialog by remember { mutableStateOf(false) }
    var createDocName by remember { mutableStateOf("") }
    var createDocIsPlainText by remember { mutableStateOf(false) }
    
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMoreActionsBottomSheet by remember { mutableStateOf(false) }
    val collapsedBlocks = remember { mutableStateOf(setOf<String>()) }
    val isKeyboardVisible = WindowInsets.isImeVisible

    // Auto-save logic on changes (3 mins interval or 3 seconds debounce on manual update types)
    LaunchedEffect(blocks) {
        if (blocks.isNotEmpty()) {
            delay(3000)
            viewModel.saveActiveDocumentImmediate()
        }
    }

    val getBlockAccentColor = { type: String ->
        when (type) {
            "scene" -> Color(0xFF2B5C8F)       // Deep Premium Blue
            "character" -> Color(0xFF7B1FA2)   // Majestic Purple
            "dialogue" -> Color(0xFF2E7D32)    // Warm Pine Green
            "parenthetical" -> Color(0xFF00ACC1) // Teal parenthetical
            "transition" -> Color(0xFFE65100)  // Sunset Orange
            "image" -> Color(0xFF5A5C7E)       // Indigo Grey
            else -> Color(0xFF6A7B83)          // Slate Grey
        }
    }

    val getBlockIcon = { type: String ->
        when (type) {
            "scene" -> Icons.Default.Movie
            "character" -> Icons.Default.Person
            "dialogue" -> Icons.Default.ChatBubble
            "parenthetical" -> Icons.Default.FormatQuote
            "transition" -> Icons.Filled.ExitToApp
            "image" -> Icons.Default.Image
            else -> Icons.AutoMirrored.Filled.Notes
        }
    }

    val proLazyListState = rememberLazyListState()

    LaunchedEffect(activeBlockIndex) {
        if (activeBlockIndex in 0 until blocks.size) {
            proLazyListState.animateScrollToItem(activeBlockIndex)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = if (appLanguage == "ru") "Экспорт документа" else "Export Document",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    // FORMAT SELECTOR
                    Text(
                        text = if (appLanguage == "ru") "Формат файла" else "File Format",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val formats = listOf("PDF", "DOCX", "RTF", "TXT")
                        formats.forEach { fmt ->
                            val isSel = exportFormat == fmt
                            Button(
                                onClick = { exportFormat = fmt },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f).testTag("export_format_$fmt"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(fmt, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // PDF-Specific Layout Customizations
                    if (exportFormat == "PDF") {
                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        // PAGE SIZE
                        Text(
                            text = if (appLanguage == "ru") "Размер страницы" else "Page Size",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val sizes = listOf("A4", "LETTER")
                            sizes.forEach { sz ->
                                val isSel = exportPageSize == sz
                                OutlinedButton(
                                    onClick = { exportPageSize = sz },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isSel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        contentColor = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = 1.dp,
                                        color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    ),
                                    modifier = Modifier.weight(1f).testTag("export_size_$sz"),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = if (sz == "LETTER") (if (appLanguage == "ru") "Letter (США)" else "Letter (US)") else "A4",
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        // FONT PREFERENCE
                        Text(
                            text = if (appLanguage == "ru") "Основной шрифт документа" else "Document Primary Font",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val fonts = listOf(
                                "SERIF" to (if (appLanguage == "ru") "С засечками" else "Serif"),
                                "SANS_SERIF" to (if (appLanguage == "ru") "Без засечек" else "Sans-Serif"),
                                "MONOSPACE" to (if (appLanguage == "ru") "Моноширинный" else "Monospace")
                            )
                            fonts.forEach { (key, label) ->
                                val isSel = exportFontFamily == key
                                OutlinedButton(
                                    onClick = { exportFontFamily = key },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isSel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        contentColor = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = 1.dp,
                                        color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    ),
                                    modifier = Modifier.weight(1f).testTag("export_font_$key"),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(label, fontSize = 11.sp)
                                }
                            }
                        }

                        // LINE SPACING MULTIPLIER
                        Text(
                            text = if (appLanguage == "ru") "Межстрочный интервал" else "Line Spacing",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val spacingOptions = listOf(
                                1.0f to "1.0",
                                1.15f to "1.15",
                                1.5f to "1.5",
                                2.0f to "2.0"
                            )
                            spacingOptions.forEach { (multiplier, label) ->
                                val isSel = exportLineSpacing == multiplier
                                OutlinedButton(
                                    onClick = { exportLineSpacing = multiplier },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isSel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        contentColor = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = 1.dp,
                                        color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    ),
                                    modifier = Modifier.weight(1f).testTag("export_spacing_$label"),
                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(label, fontSize = 11.sp)
                                }
                            }
                        }

                        // PAGE NUMBERS
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { exportIncludePageNumbers = !exportIncludePageNumbers }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val labelTxt = if (appLanguage == "ru") "Нумерация страниц" else "Include Page Numbers"
                                val descTxt = if (appLanguage == "ru") "Печатать номера страниц внизу по центру" else "Draw page index centered in footer"
                                Text(
                                    text = labelTxt,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = descTxt,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            androidx.compose.material3.Switch(
                                checked = exportIncludePageNumbers,
                                onCheckedChange = { exportIncludePageNumbers = it },
                                modifier = Modifier.testTag("export_page_numbers_toggle")
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExportDialog = false
                        val file = viewModel.exportCurrentDocument(
                            context = context,
                            format = exportFormat,
                            pageSizeName = exportPageSize,
                            fontPreference = exportFontFamily,
                            lineSpacingMultiplier = exportLineSpacing,
                            includePageNumbers = exportIncludePageNumbers
                        )
                        if (file != null) {
                            try {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = when (exportFormat) {
                                        "PDF" -> "application/pdf"
                                        "DOCX" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                        "RTF" -> "application/rtf"
                                        else -> "text/plain"
                                    }
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(
                                        intent,
                                        if (appLanguage == "ru") "Экспортировать через" else "Export via"
                                    )
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(
                                    context,
                                    if (appLanguage == "ru") "Ошибка отправки файла" else "Failed to send file",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            Toast.makeText(
                                context,
                                if (appLanguage == "ru") "Ошибка генерации файла" else "Failed to generate file",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (appLanguage == "ru") "Экспорт" else "Export File",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text(if (appLanguage == "ru") "Отмена" else "Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Unified Rename Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = if (appLanguage == "ru") "Переименовать документ" else "Rename Document",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                OutlinedTextField(
                    value = renameDocName,
                    onValueChange = { renameDocName = it },
                    label = { Text(if (appLanguage == "ru") "Новое название" else "New Title") },
                    modifier = Modifier.fillMaxWidth().testTag("rename_document_name_input_editor"),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameDocName.isNotBlank()) {
                            viewModel.updateDocumentTitle(renameDocName)
                            showRenameDialog = false
                        }
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (appLanguage == "ru") "Сохранить" else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(if (appLanguage == "ru") "Отмена" else "Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Unified Create Dialog representing cohesive file operations
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = if (appLanguage == "ru") "Создать документ" else "Create Document",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = createDocName,
                        onValueChange = { createDocName = it },
                        label = { Text(if (appLanguage == "ru") "Название документа" else "Document Title") },
                        modifier = Modifier.fillMaxWidth().testTag("new_document_name_input_editor"),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Text(
                        text = if (appLanguage == "ru") "Выберите режим:" else "Select format:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { createDocIsPlainText = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!createDocIsPlainText) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (!createDocIsPlainText) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (appLanguage == "ru") "Блочный (Pro)" else "Block (Pro)", fontSize = 11.sp, maxLines = 1)
                        }
                        Button(
                            onClick = { createDocIsPlainText = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (createDocIsPlainText) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (createDocIsPlainText) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (appLanguage == "ru") "Простой (Basic)" else "Text (Basic)", fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (createDocName.isNotBlank()) {
                            viewModel.createDocument(createDocName, createDocIsPlainText)
                            showCreateDialog = false
                            createDocName = ""
                        }
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (appLanguage == "ru") "Создать" else "Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(if (appLanguage == "ru") "Отмена" else "Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Unified Delete Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = if (appLanguage == "ru") "Удалить документ?" else "Delete Document?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            text = {
                Text(
                    text = if (appLanguage == "ru") {
                        "Вы действительно хотите удалить документ \"${document?.title}\"? Это действие нельзя отменить."
                    } else {
                        "Are you sure you want to delete the document \"${document?.title}\"? This action cannot be undone."
                    },
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        document?.let { doc ->
                            viewModel.deleteDocument(doc.id)
                        }
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (appLanguage == "ru") "Удалить" else "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(if (appLanguage == "ru") "Отмена" else "Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (showMoreActionsBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMoreActionsBottomSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (document?.isPlainText == true) Icons.AutoMirrored.Filled.Notes else Icons.Default.EditNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = document?.title ?: "",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (document?.isPlainText == true) {
                                if (appLanguage == "ru") "Формат: Простой текст (Basic)" else "Format: Plain Text (Basic)"
                            } else {
                                if (appLanguage == "ru") "Формат: Блочный редактор (Pro)" else "Format: Block Editor (Pro)"
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider()

                val menuItems = listOf(
                    Triple(
                        if (appLanguage == "ru") "Переименовать" else "Rename Document",
                        if (appLanguage == "ru") "Изменить название текущего документа" else "Change the title of this document",
                        Icons.Default.Edit
                    ),
                    Triple(
                        if (appLanguage == "ru") "Создать документ" else "Create Document",
                        if (appLanguage == "ru") "Создать новый пустой форматный файл" else "Start a new blank basic or pro file",
                        Icons.Default.Add
                    ),
                    Triple(
                        if (appLanguage == "ru") "Экспорт в PDF" else "Export to PDF",
                        if (appLanguage == "ru") "Сохранить документ в файл формата PDF" else "Generate and share high-quality PDF",
                        Icons.Filled.PictureAsPdf
                    )
                )

                menuItems.forEach { (title, subtitle, icon) ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showMoreActionsBottomSheet = false
                                when (icon) {
                                    Icons.Default.Edit -> showRenameDialog = true
                                    Icons.Default.Add -> showCreateDialog = true
                                    Icons.Filled.PictureAsPdf -> showExportDialog = true
                                }
                            },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = title,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = subtitle,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            showMoreActionsBottomSheet = false
                            showDeleteDialog = true
                        },
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.errorContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = if (appLanguage == "ru") "Удалить" else "Delete",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (appLanguage == "ru") "Удалить" else "Delete Document",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = if (appLanguage == "ru") "Безвозвратно удалить файл из хранилища" else "Permanently delete this document",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }

    if (document?.isPlainText == true) {
        // --- BASIC MODE / SIMPLE TXT WRITER ---
        val textFileContent = remember(blocks) {
            blocks.firstOrNull()?.text ?: ""
        }

        var textFontSize by remember { mutableStateOf(16) }
        var textLineHeight by remember { mutableStateOf(24) }
        var textFontFamilyName by remember { mutableStateOf("SansSerif") }
        var isFormatSettingsVisible by remember { mutableStateOf(false) }

        val basicScrollState = rememberScrollState()
        var localTextValue by remember { mutableStateOf(TextFieldValue(textFileContent)) }
        if (localTextValue.text != textFileContent) {
            localTextValue = localTextValue.copy(
                text = textFileContent,
                selection = if (textFileContent.startsWith("- ") && localTextValue.text.isEmpty()) {
                    TextRange(2)
                } else {
                    TextRange(textFileContent.length)
                }
            )
        }

        val activeFontFamily = when (textFontFamilyName) {
            "Monospace" -> FontFamily.Monospace
            "Serif" -> FontFamily.Serif
            else -> FontFamily.SansSerif
        }

        val fontLabel = if (appLanguage == "ru") "Шрифт" else "Font"
        val sizeLabel = if (appLanguage == "ru") "Размер" else "Size"
        val spacingLabel = if (appLanguage == "ru") "Интервал" else "Spacing"

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = document?.title ?: "",
                            style = TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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
                        IconButton(onClick = { isFormatSettingsVisible = !isFormatSettingsVisible }) {
                            Icon(
                                imageVector = Icons.Default.TextFields,
                                contentDescription = "Font Settings",
                                tint = if (isFormatSettingsVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { isSearchPanelVisible = !isSearchPanelVisible }) {
                            Icon(Icons.Filled.Search, l("search", appLanguage))
                        }
                        IconButton(onClick = { isHistoryPanelVisible = !isHistoryPanelVisible }) {
                            Icon(Icons.Filled.History, l("versions", appLanguage))
                        }
                        FilledIconButton(
                            onClick = onLaunchPrompter,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("editor_prompter_btn")
                                .bounceClickable { onLaunchPrompter() },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = l("prompter", appLanguage),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(onClick = { showMoreActionsBottomSheet = true }, modifier = Modifier.testTag("editor_more_options_btn")) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = if (appLanguage == "ru") "Еще" else "More options",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                )
            },
            bottomBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 2.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Word Count Item
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.MenuBook,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${l("words", appLanguage)}: ${metrics.words}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Char Count Item
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.TextFormat,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${l("chars", appLanguage)}: ${metrics.characters}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Reading Time Item
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "~${metrics.readingTimeMinutes} ${l("min", appLanguage)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            val isKeyboardVisible = WindowInsets.isImeVisible
            val bottomPadding = if (isKeyboardVisible) 0.dp else paddingValues.calculateBottomPadding()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = paddingValues.calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                        top = paddingValues.calculateTopPadding(),
                        end = paddingValues.calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                        bottom = bottomPadding
                    )
                    .imePadding()
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

                    // Format Settings panel if toggled
                    AnimatedVisibility(visible = isFormatSettingsVisible) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                // Font family switcher
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = fontLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf("SansSerif", "Serif", "Monospace").forEach { family ->
                                            val isSelected = textFontFamilyName == family
                                            val displayName = when (family) {
                                                "SansSerif" -> "Sans"
                                                "Serif" -> "Serif"
                                                else -> "Mono"
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                                    .bounceClickable { textFontFamilyName = family }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = displayName,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                )

                                // Font size controls
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = sizeLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                if (textFontSize > 12) {
                                                    textFontSize -= 2
                                                    textLineHeight = (textFontSize * 1.5).toInt()
                                                }
                                            },
                                            modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f), androidx.compose.foundation.shape.CircleShape)
                                        ) {
                                            Text("-", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                        }
                                        Text("${textFontSize}sp", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        IconButton(
                                            onClick = {
                                                if (textFontSize < 32) {
                                                    textFontSize += 2
                                                    textLineHeight = (textFontSize * 1.5).toInt()
                                                }
                                            },
                                            modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f), androidx.compose.foundation.shape.CircleShape)
                                        ) {
                                            Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                )

                                // Line spacing controls
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = spacingLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf(1.2f, 1.5f, 1.8f).forEach { scale ->
                                            val isSelected = Math.abs(textLineHeight.toFloat() / textFontSize.toFloat() - scale) < 0.15f
                                            val labelText = when(scale) {
                                                1.2f -> if (appLanguage == "ru") "Узкий" else "Compact"
                                                1.5f -> if (appLanguage == "ru") "Средний" else "Medium"
                                                else -> if (appLanguage == "ru") "Широкий" else "Wide"
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                                    .bounceClickable { textLineHeight = (textFontSize * scale).toInt() }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = labelText,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Text Area Editor Workspace Card Sheet
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    ) {
                        var containerHeightPx by remember { mutableStateOf(0) }
                        var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

                        LaunchedEffect(localTextValue.selection, textLayoutResult, containerHeightPx) {
                            val layout = textLayoutResult
                            val containerHeight = containerHeightPx
                            if (layout != null && containerHeight > 0) {
                                val cursor = localTextValue.selection.start
                                val safeCursor = cursor.coerceIn(0, layout.layoutInput.text.length)
                                val line = layout.getLineForOffset(safeCursor)
                                val lineTop = layout.getLineTop(line).toInt()
                                val lineBottom = layout.getLineBottom(line).toInt()
                                
                                val currentScroll = basicScrollState.value
                                val visibleMin = currentScroll
                                val visibleMax = currentScroll + containerHeight
                                
                                val padding = 40
                                if (lineTop - padding < visibleMin) {
                                    basicScrollState.animateScrollTo((lineTop - padding).coerceAtLeast(0))
                                } else if (lineBottom + padding > visibleMax) {
                                    basicScrollState.animateScrollTo((lineBottom + padding - containerHeight).coerceAtLeast(0))
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            BasicTextField(
                                value = localTextValue,
                                onValueChange = { newVal ->
                                    val processedVal = processTextFieldValueSmartEditor(localTextValue, newVal)
                                    localTextValue = processedVal
                                    val activeBlock = blocks.firstOrNull() ?: EditorBlock(type = "paragraph", text = "")
                                    viewModel.updateEditorBlocks(
                                        listOf(activeBlock.copy(text = processedVal.text)),
                                        updateHistory = false
                                    )
                                },
                                textStyle = TextStyle(
                                    fontSize = textFontSize.sp,
                                    fontFamily = activeFontFamily,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = textLineHeight.sp
                                ),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .onGloballyPositioned { containerHeightPx = it.size.height }
                                    .verticalScroll(basicScrollState)
                                    .testTag("basic_text_editor_input"),
                                onTextLayout = { textLayoutResult = it }
                            )

                            if (textFileContent.isEmpty()) {
                                Text(
                                    text = l("empty_text", appLanguage),
                                    style = TextStyle(
                                        fontSize = textFontSize.sp,
                                        fontFamily = activeFontFamily,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                )
                            }
                        }
                    }

                    // Keyboard auxiliary toolbar
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = { viewModel.undo() },
                            enabled = canUndo,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Undo,
                                contentDescription = "Undo",
                                tint = if (canUndo) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.35f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.redo() },
                            enabled = canRedo,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Redo,
                                contentDescription = "Redo",
                                tint = if (canRedo) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.35f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(20.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        )

                        val helperSymbols = listOf("—", "«", "»", "“", "”", "•", "¶", "Tab")
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(helperSymbols) { symbol ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                        .bounceClickable {
                                            val oldText = localTextValue.text
                                            val selectionStart = localTextValue.selection.start.coerceIn(0, oldText.length)
                                            val selectionEnd = localTextValue.selection.end.coerceIn(0, oldText.length)
                                            val appendText = if (symbol == "Tab") "    " else symbol
                                            val newText = oldText.substring(0, selectionStart) + appendText + oldText.substring(selectionEnd)
                                            val newSelection = TextRange(selectionStart + appendText.length)
                                            localTextValue = TextFieldValue(text = newText, selection = newSelection)

                                            val activeBlock = blocks.firstOrNull() ?: EditorBlock(type = "paragraph", text = "")
                                            viewModel.updateEditorBlocks(
                                                listOf(activeBlock.copy(text = newText)),
                                                updateHistory = true
                                            )
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = symbol,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
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
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
                                    modifier = Modifier.fillMaxWidth().bounceClickable { viewModel.restoreSnapshot(hist) }
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
    } else {

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
            if (!isReadingMode) {
                Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                    TopAppBar(
                        title = {
                            var editingTitle by remember(document?.title) { mutableStateOf(document?.title ?: "") }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                BasicTextField(
                                    value = editingTitle,
                                    onValueChange = {
                                        editingTitle = it
                                        viewModel.updateDocumentTitle(it)
                                    },
                                    singleLine = true,
                                    maxLines = 1,
                                    textStyle = TextStyle(
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
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
                            IconButton(onClick = onLaunchPrompter, modifier = Modifier.testTag("editor_prompter_btn")) {
                                Icon(Icons.Filled.PlayArrow, l("prompter", appLanguage))
                            }

                             IconButton(onClick = { showMoreActionsBottomSheet = true }, modifier = Modifier.testTag("editor_more_options_btn_pro")) {
                                 Icon(
                                     imageVector = Icons.Default.MoreVert,
                                     contentDescription = if (appLanguage == "ru") "Еще" else "More options",
                                     tint = MaterialTheme.colorScheme.onSurface
                                 )
                             }
                        }
                    )

                    // Navigation Tabs Row
                    TabRow(
                        selectedTabIndex = if (editorTab == "BLOCKS") 0 else 1,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[if (editorTab == "BLOCKS") 0 else 1]),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    ) {
                        Tab(
                            selected = editorTab == "BLOCKS",
                            onClick = { editorTab = "BLOCKS" },
                            text = { Text(if (appLanguage == "ru") "Блоки" else "Blocks", fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Default.GridView, contentDescription = null) }
                        )
                        Tab(
                            selected = editorTab == "DOCUMENT",
                            onClick = { editorTab = "DOCUMENT" },
                            text = { Text(if (appLanguage == "ru") "Документ" else "Document", fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Default.MenuBook, contentDescription = null) }
                        )
                    }
                }
            }
        },
        bottomBar = {
            val isKeyboardVisible = WindowInsets.isImeVisible
            if (!isKeyboardVisible && !isReadingMode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(8.dp)
                ) {
                    // Script stats metadata row
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

                    if (editorTab == "BLOCKS") {
                        // FORMATTING HORIZONTAL BAR
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val activeBlock = blocks.getOrNull(activeBlockIndex)
                            
                            // Undo/Redo Controls
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { viewModel.undo() },
                                    enabled = canUndo,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Undo,
                                        contentDescription = "Отменить",
                                        modifier = Modifier.size(20.dp),
                                        tint = if (canUndo) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.35f)
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.redo() },
                                    enabled = canRedo,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Redo,
                                        contentDescription = "Вернуть",
                                        modifier = Modifier.size(20.dp),
                                        tint = if (canRedo) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.35f)
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(24.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            )

                            // Text Style Options
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val isBold = activeBlock?.style?.isBold == true
                                IconButton(
                                    onClick = {
                                        if (activeBlock != null) {
                                            val updated = blocks.mapIndexed { index, b ->
                                                if (index == activeBlockIndex) b.copy(style = b.style.copy(isBold = !isBold)) else b
                                            }
                                            viewModel.updateEditorBlocks(updated)
                                        }
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("format_bold")
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isBold) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "B",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 15.sp,
                                            color = if (isBold) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                val isItalic = activeBlock?.style?.isItalic == true
                                IconButton(
                                    onClick = {
                                        if (activeBlock != null) {
                                            val updated = blocks.mapIndexed { index, b ->
                                                if (index == activeBlockIndex) b.copy(style = b.style.copy(isItalic = !isItalic)) else b
                                            }
                                            viewModel.updateEditorBlocks(updated)
                                        }
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("format_italic")
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isItalic) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "I",
                                            fontWeight = FontWeight.Bold,
                                            fontStyle = FontStyle.Italic,
                                            fontSize = 15.sp,
                                            color = if (isItalic) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                val isUnderline = activeBlock?.style?.isUnderline == true
                                IconButton(
                                    onClick = {
                                        if (activeBlock != null) {
                                          val updated = blocks.mapIndexed { index, b ->
                                              if (index == activeBlockIndex) b.copy(style = b.style.copy(isUnderline = !isUnderline)) else b
                                          }
                                          viewModel.updateEditorBlocks(updated)
                                        }
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isUnderline) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "U",
                                            style = TextStyle(textDecoration = TextDecoration.Underline),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = if (isUnderline) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(24.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            )

                            // Media picker action
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { pickerLauncher.launch("image/*") },
                                    modifier = Modifier.size(38.dp).testTag("editor_insert_image")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Image,
                                        contentDescription = "Вставить картинку",
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    } else {
                        // DOCUMENT MODE READING BAR CONTROLS
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (appLanguage == "ru") "Режим чтения" else "Reading Mode",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Scaling down font action
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { if (readFontSizeScale > 10f) readFontSizeScale -= 1f },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("A-", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                                
                                Text(
                                    text = "${(readFontSizeScale / 16f * 100).toInt()}%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                
                                // Scaling up font action
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { if (readFontSizeScale < 28f) readFontSizeScale += 1f },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("A+", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                                
                                Spacer(modifier = Modifier.width(6.dp))
                                
                                // Launch Fullscreen Button
                                Button(
                                    onClick = { isReadingMode = true },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Default.Fullscreen, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Text(if (appLanguage == "ru") "На весь экран" else "Fullscreen", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (editorTab == "BLOCKS" && !isKeyboardVisible && !isReadingMode) {
                ExtendedFloatingActionButton(
                    onClick = { showAddBlockBottomSheet = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Добавить блок") },
                    text = { Text(if (appLanguage == "ru") "Добавить блок" else "Add Block") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("fab_add_block")
                )
            }
        }
    ) { paddingValues ->
        val isKeyboardVisible = WindowInsets.isImeVisible
        val bottomPadding = if (isKeyboardVisible) 0.dp else paddingValues.calculateBottomPadding()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = paddingValues.calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    top = paddingValues.calculateTopPadding(),
                    end = paddingValues.calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    bottom = bottomPadding
                )
                .imePadding()
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
                if (!isKeyboardVisible) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val activeBlock = blocks.getOrNull(activeBlockIndex)
                        
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState())
                                .padding(end = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val presets = listOf(
                                Triple("scene", "Сцена", Icons.Filled.Movie),
                                Triple("action", "Опис.", Icons.AutoMirrored.Filled.Notes),
                                Triple("character", "Перс.", Icons.Filled.Person),
                                Triple("dialogue", "Репл.", Icons.Filled.Chat),
                                Triple("parenthetical", "Дейс.", Icons.Filled.FormatQuote)
                            )
                            presets.forEach { (typeId, label, icon) ->
                                val isCurr = activeBlock?.type == typeId
                                val containerColor = if (isCurr) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                }
                                val contentColor = if (isCurr) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(containerColor)
                                        .bounceClickable {
                                            val updated = blocks.mapIndexed { idx, block ->
                                                if (idx == activeBlockIndex) {
                                                    val formattedText = when (typeId) {
                                                        "scene", "character" -> block.text.uppercase()
                                                        "parenthetical" -> {
                                                            val trimmed = block.text.trim()
                                                            if (trimmed.isNotEmpty()) {
                                                                val inner = trimmed.removePrefix("(").removeSuffix(")").trim()
                                                                "($inner)"
                                                            } else ""
                                                        }
                                                        "action", "dialogue" -> {
                                                            if (block.text.isNotEmpty()) {
                                                                block.text.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                                                            } else {
                                                                block.text
                                                            }
                                                        }
                                                        else -> block.text
                                                    }
                                                    block.copy(
                                                        type = typeId,
                                                        text = formattedText,
                                                        alignment = when (typeId) {
                                                            "character", "dialogue", "parenthetical" -> "CENTER"
                                                            "transition" -> "RIGHT"
                                                            else -> "LEFT"
                                                        }
                                                    )
                                                } else block
                                            }
                                            viewModel.updateEditorBlocks(updated)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        modifier = Modifier.size(16.dp),
                                        tint = contentColor
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = contentColor
                                    )
                                }
                            }
                        }

                        // Trash Button
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF381416)) // Burgundy red
                                .bounceClickable {
                                    val list = blocks.toMutableList()
                                    if (activeBlockIndex in 0 until list.size) {
                                        list.removeAt(activeBlockIndex)
                                        if (list.isEmpty()) list.add(EditorBlock(type = "paragraph", text = ""))
                                        activeBlockIndex = Math.max(0, activeBlockIndex - 1)
                                        viewModel.updateEditorBlocks(list)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Стереть блок",
                                tint = Color(0xFFF2B8B5),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                if (editorTab == "BLOCKS") {
                    // MAIN REDESIGNED M3 BLOCKS LIST
                    LazyColumn(
                        state = proLazyListState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(blocks, key = { _, block -> block.id }) { index, block ->
                            val isSelected = index == activeBlockIndex
                            val focusRequester = remember { FocusRequester() }
                            val isCollapsed = collapsedBlocks.value.contains(block.id)

                            LaunchedEffect(isSelected) {
                                if (isSelected) {
                                    try {
                                        focusRequester.requestFocus()
                                    } catch (e: Exception) {
                                        // ignore
                                    }
                                }
                            }

                            val accentColor = getBlockAccentColor(block.type)
                            val containerBg = if (isSelected) {
                                accentColor.copy(alpha = 0.08f)
                            } else {
                                accentColor.copy(alpha = 0.03f)
                            }
                            val borderCol = if (isSelected) {
                                accentColor.copy(alpha = 0.40f)
                            } else {
                                accentColor.copy(alpha = 0.12f)
                            }
                            val borderWidth = if (isSelected) 1.5.dp else 1.dp

                            // Beautiful Card with 20dp corners and left vertical stripe accent
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(containerBg)
                                    .border(borderWidth, borderCol, RoundedCornerShape(20.dp))
                                    .bounceClickable { activeBlockIndex = index }
                            ) {
                                // Left accent stripe
                                Box(
                                    modifier = Modifier
                                        .width(6.dp)
                                        .background(
                                            accentColor,
                                            shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
                                        )
                                        .align(Alignment.CenterStart)
                                        .height(IntrinsicSize.Max)
                                )

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 20.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)
                                ) {
                                    // Card Heading Header
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(accentColor.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = getBlockIcon(block.type),
                                                    contentDescription = null,
                                                    tint = accentColor,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = when (block.type) {
                                                    "scene" -> "СЦЕНА"
                                                    "action" -> "ОПИСАНИЕ"
                                                    "character" -> "ПЕРСОНАЖ"
                                                    "dialogue" -> "РЕПЛИКА"
                                                    "parenthetical" -> "РЕМАРКА"
                                                    "transition" -> "ПЕРЕХОД"
                                                    "image" -> "КАРТИНКА"
                                                    "h1" -> "ЗАГОЛОВОК 1"
                                                    "h2" -> "ЗАГОЛОВОК 2"
                                                    "h3" -> "ЗАГОЛОВОК 3"
                                                    else -> "ПАРАГРАФ"
                                                },
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = accentColor,
                                                letterSpacing = 0.5.sp
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            // Reorder - Shift Block UP
                                            if (index > 0) {
                                                IconButton(
                                                    onClick = {
                                                        val list = blocks.toMutableList()
                                                        val temp = list[index]
                                                        list[index] = list[index - 1]
                                                        list[index - 1] = temp
                                                        viewModel.updateEditorBlocks(list)
                                                        activeBlockIndex = index - 1
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowUpward,
                                                        contentDescription = "Move Up",
                                                        tint = accentColor.copy(alpha = 0.7f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }

                                            // Reorder - Shift Block DOWN
                                            if (index < blocks.size - 1) {
                                                IconButton(
                                                    onClick = {
                                                        val list = blocks.toMutableList()
                                                        val temp = list[index]
                                                        list[index] = list[index + 1]
                                                        list[index + 1] = temp
                                                        viewModel.updateEditorBlocks(list)
                                                        activeBlockIndex = index + 1
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowDownward,
                                                        contentDescription = "Move Down",
                                                        tint = accentColor.copy(alpha = 0.7f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }

                                            // Collapse / Expand Toggle chevron
                                            IconButton(
                                                onClick = {
                                                    if (isCollapsed) {
                                                        collapsedBlocks.value = collapsedBlocks.value - block.id
                                                    } else {
                                                        collapsedBlocks.value = collapsedBlocks.value + block.id
                                                    }
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                                    contentDescription = "Collapse/Expand",
                                                    tint = accentColor.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            // Block item deletion
                                            IconButton(
                                                onClick = {
                                                    val list = blocks.toMutableList()
                                                    list.removeAt(index)
                                                    if (list.isEmpty()) {
                                                        list.add(EditorBlock(type = "paragraph", text = ""))
                                                    }
                                                    viewModel.updateEditorBlocks(list)
                                                    activeBlockIndex = Math.max(0, index - 1)
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete block",
                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (isCollapsed) {
                                        // Compact text preview for collapsed state
                                        val preview = block.text.ifBlank { if (appLanguage == "ru") "<Пустой блок>" else "<Empty block>" }
                                        Text(
                                            text = preview,
                                            fontSize = 13.sp,
                                            fontStyle = FontStyle.Italic,
                                            color = Color.Gray,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                                        )
                                    } else {
                                        // Expanded mode block styling
                                        if (block.type == "image") {
                                            Column(
                                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                AsyncImage(
                                                    model = block.imageUrl,
                                                    contentDescription = "Inline Image",
                                                    modifier = Modifier
                                                        .fillMaxWidth(block.imageSize)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                // Image Width Slider
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
                                                // Image Description / Caption field
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
                                                        color = Color.Gray
                                                    ).copy(textAlign = TextAlign.Center),
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        } else {
                                            val calculatedPadding = when (block.type) {
                                                "character" -> PaddingValues(horizontal = 24.dp, vertical = 2.dp)
                                                "dialogue" -> PaddingValues(horizontal = 16.dp, vertical = 2.dp)
                                                "parenthetical" -> PaddingValues(horizontal = 20.dp, vertical = 1.dp)
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
                                                color = if (block.type == "quote") Color.Gray else MaterialTheme.colorScheme.onSurface,
                                                fontFamily = if (block.type in listOf("character", "dialogue", "parenthetical", "scene")) FontFamily.Monospace else FontFamily.Serif
                                            ).copy(
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
                                                }
                                            )

                                            Box(modifier = Modifier.padding(calculatedPadding)) {
                                                var blockTextValue by remember(block.id) {
                                                    mutableStateOf(
                                                        TextFieldValue(
                                                            text = block.text,
                                                            selection = if (block.text.startsWith("- ")) TextRange(2) else TextRange(block.text.length)
                                                        )
                                                    )
                                                }
                                                if (blockTextValue.text != block.text) {
                                                    blockTextValue = blockTextValue.copy(
                                                        text = block.text,
                                                        selection = if (blockTextValue.selection.start <= block.text.length && blockTextValue.selection.end <= block.text.length) {
                                                            blockTextValue.selection
                                                        } else {
                                                            TextRange(block.text.length)
                                                        }
                                                    )
                                                }

                                                BasicTextField(
                                                    value = blockTextValue,
                                                    onValueChange = label@{ newVal ->
                                                         blockTextValue = newVal
                                                         val rawText = newVal.text
                                                         
                                                         // Backspace on empty block: delete block and move focus to previous block
                                                         if (rawText.isEmpty() && block.text.isEmpty() && index > 0) {
                                                             val list = blocks.toMutableList()
                                                             list.removeAt(index)
                                                             viewModel.updateEditorBlocks(list)
                                                             activeBlockIndex = index - 1
                                                             return@label
                                                         }
                                                         
                                                         if (rawText.contains("\n")) {
                                                             val parts = rawText.split("\n", limit = 2)
                                                             val firstPart = parts.getOrNull(0) ?: ""
                                                             val secondPart = parts.getOrNull(1) ?: ""
                                                             val list = blocks.toMutableList()
                                                             list[index] = block.copy(text = firstPart)
                                                             val nextType = if (block.type == "character") "dialogue" else "paragraph"
                                                             val newBlock = EditorBlock(type = nextType, text = if (firstPart.trimStart().startsWith("- ")) "- " + secondPart else secondPart)
                                                             list.add(index + 1, newBlock)
                                                             viewModel.updateEditorBlocks(list)
                                                             activeBlockIndex = index + 1
                                                             return@label
                                                         }
                                                        var updatedValue = if (isTranslitEnabled && rawText.length > block.text.length) {
                                                            val addedText = rawText.substring(block.text.length)
                                                            block.text + transliterateEnToRu(addedText)
                                                        } else rawText

                                                        updatedValue = when (block.type) {
                                                            "scene", "character" -> updatedValue.uppercase()
                                                            "parenthetical" -> {
                                                                if (block.text.isEmpty() && updatedValue.isNotEmpty() && !updatedValue.startsWith("(")) {
                                                                    val wrapped = "($updatedValue)"
                                                                    blockTextValue = newVal.copy(text = wrapped, selection = TextRange(newVal.selection.start + 1, newVal.selection.end + 1))
                                                                    wrapped
                                                                 } else {
                                                                    updatedValue
                                                                 }
                                                            }
                                                            "action", "dialogue", "h1", "h2", "h3" -> {
                                                                if (block.text.isEmpty() && updatedValue.isNotEmpty()) {
                                                                    updatedValue.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                                                                } else {
                                                                    updatedValue
                                                                 }
                                                            }
                                                            else -> updatedValue
                                                        }

                                                        val updated = blocks.mapIndexed { idx, b ->
                                                            if (idx == index) b.copy(text = if (block.text == "- " && updatedValue == "-") "" else updatedValue) else b
                                                        }
                                                        viewModel.updateEditorBlocks(updated, updateHistory = false)
                                                    },
                                                    textStyle = textStyle,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .focusRequester(focusRequester)
                                                        .onFocusChanged { if (it.isFocused) { activeBlockIndex = index } }
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
                    }
                } else {
                    // ==========================================
                    // --- DOCUMENT PREVIEW / SCRIPT READING MODE ---
                    // ==========================================
                    val readingLazyState = rememberLazyListState()
                    val readerScope = rememberCoroutineScope()
                    
                    // Filter blocks for active Scene Heading records for navigation jump chips
                    val scenesList = remember(blocks) {
                        blocks.filter { b -> b.type == "scene" && b.text.isNotBlank() }
                    }

                    if (isReadingMode) {
                        // Semitransparent floating button to close distraction-free mode
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.TopEnd
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
                                    .clickable { isReadingMode = false },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.FullscreenExit, contentDescription = "Exit Fullscreen", tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }

                    // Scene quick jump index chips bar
                    if (scenesList.isNotEmpty() && !isReadingMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (appLanguage == "ru") "Сцены:" else "Scenes:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                scenesList.forEach { sBlock ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                                            .clickable {
                                                val sIdx = blocks.indexOf(sBlock)
                                                if (sIdx != -1) {
                                                    readerScope.launch {
                                                        readingLazyState.animateScrollToItem(sIdx)
                                                    }
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = sBlock.text.take(30) + if (sBlock.text.length > 30) "..." else "",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Ivory/White script sheet layout body
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        color = if (MaterialTheme.colorScheme.background == Color.White) Color(0xFFFFFFF8) else MaterialTheme.colorScheme.background,
                        tonalElevation = 1.dp
                    ) {
                        if (blocks.isEmpty() || (blocks.size == 1 && blocks[0].text.isBlank())) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (appLanguage == "ru") "Сценарий пуст" else "Script is empty",
                                    fontStyle = FontStyle.Italic,
                                    color = Color.Gray
                                )
                            }
                        } else {
                            LazyColumn(
                                state = readingLazyState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 24.dp),
                                contentPadding = PaddingValues(vertical = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(blocks) { rBlock ->
                                    val formattedContent = rBlock.text
                                    
                                    when (rBlock.type) {
                                        "scene" -> {
                                            Text(
                                                text = formattedContent.uppercase(),
                                                fontSize = readFontSizeScale.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                color = getBlockAccentColor("scene"),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 16.dp, bottom = 4.dp)
                                            )
                                        }
                                        "character" -> {
                                            Text(
                                                text = formattedContent.uppercase(),
                                                fontSize = readFontSizeScale.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                color = getBlockAccentColor("character"),
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 64.dp, end = 64.dp, top = 8.dp, bottom = 2.dp)
                                            )
                                        }
                                        "dialogue" -> {
                                            Text(
                                                text = formattedContent,
                                                fontSize = readFontSizeScale.sp,
                                                fontWeight = FontWeight.Normal,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                textAlign = TextAlign.Left,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 96.dp, end = 48.dp, top = 2.dp, bottom = 8.dp)
                                            )
                                        }
                                        "parenthetical" -> {
                                            Text(
                                                text = formattedContent,
                                                fontSize = (readFontSizeScale - 1).sp,
                                                fontStyle = FontStyle.Italic,
                                                fontFamily = FontFamily.Monospace,
                                                color = getBlockAccentColor("parenthetical"),
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 120.dp, end = 80.dp, top = 1.dp, bottom = 1.dp)
                                            )
                                        }
                                        "transition" -> {
                                            Text(
                                                text = formattedContent.uppercase(),
                                                fontSize = readFontSizeScale.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontStyle = FontStyle.Italic,
                                                fontFamily = FontFamily.Monospace,
                                                color = getBlockAccentColor("transition"),
                                                textAlign = TextAlign.Right,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 8.dp, bottom = 8.dp)
                                            )
                                        }
                                        "image" -> {
                                            if (!rBlock.imageUrl.isNullOrBlank()) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 12.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    AsyncImage(
                                                        model = rBlock.imageUrl,
                                                        contentDescription = "Script inline image illustration",
                                                        modifier = Modifier
                                                            .fillMaxWidth(rBlock.imageSize)
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .border(
                                                                1.dp,
                                                                MaterialTheme.colorScheme.outlineVariant,
                                                                RoundedCornerShape(12.dp)
                                                            )
                                                    )
                                                    if (!rBlock.imageCaption.isNullOrBlank()) {
                                                        Text(
                                                            text = rBlock.imageCaption,
                                                            fontSize = 11.sp,
                                                            fontStyle = FontStyle.Italic,
                                                            color = Color.Gray,
                                                            modifier = Modifier.padding(top = 4.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        else -> {
                                            Text(
                                                text = formattedContent,
                                                fontSize = readFontSizeScale.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                style = TextStyle(lineHeight = (readFontSizeScale * 1.5f).sp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Add the widgets at the bottom of Column if keyboard is visible!
                if (isKeyboardVisible) {
                    Spacer(modifier = Modifier.height(6.dp))

                    // 1. Screenplay Format Presets (Scrollable chips)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val activeBlock = blocks.getOrNull(activeBlockIndex)
                        
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val presets = listOf(
                                Triple("scene", "Сцена", Icons.Filled.Movie),
                                Triple("action", "Опис.", Icons.AutoMirrored.Filled.Notes),
                                Triple("character", "Перс.", Icons.Filled.Person),
                                Triple("dialogue", "Репл.", Icons.Filled.Chat),
                                Triple("parenthetical", "Дейс.", Icons.Filled.FormatQuote)
                            )
                            presets.forEach { (typeId, label, icon) ->
                                val isCurr = activeBlock?.type == typeId
                                ElevatedFilterChip(
                                    selected = isCurr,
                                    onClick = {
                                        val updated = blocks.mapIndexed { idx, block ->
                                            if (idx == activeBlockIndex) {
                                                val formattedText = when (typeId) {
                                                    "scene", "character" -> block.text.uppercase()
                                                    "parenthetical" -> {
                                                        val trimmed = block.text.trim()
                                                        if (trimmed.isNotEmpty()) {
                                                            val inner = trimmed.removePrefix("(").removeSuffix(")").trim()
                                                            "($inner)"
                                                        } else ""
                                                    }
                                                    "action", "dialogue" -> {
                                                        if (block.text.isNotEmpty()) {
                                                            block.text.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                                                        } else {
                                                            block.text
                                                        }
                                                    }
                                                    else -> block.text
                                                }
                                                block.copy(
                                                    type = typeId,
                                                    text = formattedText,
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
                                    leadingIcon = {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = label,
                                            modifier = Modifier.size(12.dp),
                                            tint = if (isCurr) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    },
                                    label = { 
                                        Text(
                                            text = label, 
                                            fontSize = 10.sp, 
                                            fontWeight = if (isCurr) FontWeight.Bold else FontWeight.Medium
                                        ) 
                                    },
                                    colors = FilterChipDefaults.elevatedFilterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(24.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )

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
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.12f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete, 
                                contentDescription = "Стереть блок", 
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 2. Formatting Toolbar (Undo/Redo, B, I, U, Image, Add Block)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val activeBlock = blocks.getOrNull(activeBlockIndex)
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { viewModel.undo() },
                                enabled = canUndo,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Undo,
                                    contentDescription = "Отменить",
                                    modifier = Modifier.size(18.dp),
                                    tint = if (canUndo) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.35f)
                                )
                            }

                            IconButton(
                                onClick = { viewModel.redo() },
                                enabled = canRedo,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Redo,
                                    contentDescription = "Вернуть",
                                    modifier = Modifier.size(18.dp),
                                    tint = if (canRedo) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.35f)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(20.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isBold = activeBlock?.style?.isBold == true
                            IconButton(
                                onClick = {
                                    if (activeBlock != null) {
                                        val updated = blocks.mapIndexed { index, b ->
                                            if (index == activeBlockIndex) b.copy(style = b.style.copy(isBold = !isBold)) else b
                                        }
                                        viewModel.updateEditorBlocks(updated)
                                    }
                                },
                                modifier = Modifier
                                    .size(36.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isBold) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "B",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = if (isBold) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            val isItalic = activeBlock?.style?.isItalic == true
                            IconButton(
                                onClick = {
                                    if (activeBlock != null) {
                                        val updated = blocks.mapIndexed { index, b ->
                                            if (index == activeBlockIndex) b.copy(style = b.style.copy(isItalic = !isItalic)) else b
                                        }
                                        viewModel.updateEditorBlocks(updated)
                                    }
                                },
                                modifier = Modifier
                                    .size(36.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isItalic) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "I",
                                        fontWeight = FontWeight.Bold,
                                        fontStyle = FontStyle.Italic,
                                        fontSize = 14.sp,
                                        color = if (isItalic) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            val isUnderline = activeBlock?.style?.isUnderline == true
                            IconButton(
                                onClick = {
                                    if (activeBlock != null) {
                                      val updated = blocks.mapIndexed { index, b ->
                                          if (index == activeBlockIndex) b.copy(style = b.style.copy(isUnderline = !isUnderline)) else b
                                      }
                                      viewModel.updateEditorBlocks(updated)
                                    }
                                },
                                modifier = Modifier
                                    .size(36.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isUnderline) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "U",
                                        style = TextStyle(textDecoration = TextDecoration.Underline),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (isUnderline) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(20.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { pickerLauncher.launch("image/*") },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Image,
                                    contentDescription = "Вставить картинку",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            IconButton(
                                onClick = {
                                    showAddBlockBottomSheet = true
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AddCircle,
                                    contentDescription = "Вставить блок",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
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

            if (showAddBlockBottomSheet) {
                AlertDialog(
                    onDismissRequest = { showAddBlockBottomSheet = false },
                    title = {
                        Text(
                            text = if (appLanguage == "ru") "Добавить новый блок" else "Add New Block",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            val blockTypes = listOf(
                                "paragraph" to ((if (appLanguage == "ru") "Параграф / Обычный текст" else "Paragraph / Normal Text") to Icons.AutoMirrored.Filled.Notes),
                                "scene" to ((if (appLanguage == "ru") "Сцена (ИНТ./ЭКСТ.)" else "Scene Heading") to Icons.Filled.Movie),
                                "action" to ((if (appLanguage == "ru") "Описание действия" else "Action Description") to Icons.AutoMirrored.Filled.Notes),
                                "character" to ((if (appLanguage == "ru") "Персонаж" else "Character Name") to Icons.Filled.Person),
                                "dialogue" to ((if (appLanguage == "ru") "Реплика персонажа" else "Character Dialogue") to Icons.Filled.Chat),
                                "parenthetical" to ((if (appLanguage == "ru") "Действие (в скобках)" else "Parenthetical") to Icons.Filled.FormatQuote),
                                "h1" to ((if (appLanguage == "ru") "Заголовок 1 уровня" else "Heading 1") to Icons.Filled.Title),
                                "h2" to ((if (appLanguage == "ru") "Заголовок 2 уровня" else "Heading 2") to Icons.Filled.Title),
                                "h3" to ((if (appLanguage == "ru") "Заголовок 3 уровня" else "Heading 3") to Icons.Filled.Title),
                                "quote" to ((if (appLanguage == "ru") "Цитата" else "Quote") to Icons.Filled.FormatQuote),
                                "image" to ((if (appLanguage == "ru") "Иллюстрация / Картинка" else "Image / Illustration") to Icons.Filled.Image)
                            )

                            blockTypes.forEach { (typeKey, pair) ->
                                val (label, icon) = pair
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .bounceClickable {
                                            val list = blocks.toMutableList()
                                            val insertIdx = if (activeBlockIndex in 0 until list.size) activeBlockIndex + 1 else list.size
                                            when (typeKey) {
                                                "image" -> {
                                                    showAddBlockBottomSheet = false
                                                    pickerLauncher.launch("image/*")
                                                }
                                                else -> {
                                                    val newBlk = EditorBlock(type = typeKey, text = "")
                                                    list.add(insertIdx, newBlk)
                                                    viewModel.updateEditorBlocks(list)
                                                    activeBlockIndex = insertIdx
                                                    showAddBlockBottomSheet = false
                                                }
                                            }
                                        },
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(getBlockAccentColor(typeKey).copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = getBlockAccentColor(typeKey),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Text(
                                            text = label,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showAddBlockBottomSheet = false }) {
                            Text(if (appLanguage == "ru") "Отмена" else "Close", fontWeight = FontWeight.Bold)
                        }
                    },
                    shape = RoundedCornerShape(24.dp)
                )
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
    var isSettingsCollapsed by remember { mutableStateOf(true) }
    var dragOffset by remember { mutableStateOf(0f) }

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

        // Cinematic gradient ambient fading overlays (top and bottom) that blend beautifully
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .align(Alignment.TopCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(prompterBg, Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .align(Alignment.BottomCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, prompterBg)
                    )
                )
        )

        // Reading guide horizontal indicator line & left/right arrows that pulsate dynamically
        val pulseScale by rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 0.85f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f * pulseScale),
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                    }
            )
            // Thin elegant glowing horizontal guide line
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp)
                    .height(1.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent, 
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), 
                                Color.Transparent
                            )
                        )
                    )
            )
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f * pulseScale),
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        rotationZ = 180f
                        scaleX = pulseScale
                        scaleY = pulseScale
                    }
            )
        }

        // Elegant animated slide input toggle drawer controls
        AnimatedContent(
            targetState = isSettingsCollapsed,
            transitionSpec = {
                if (targetState) {
                    (slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(animationSpec = tween(220)))
                        .togetherWith(slideOutVertically(targetOffsetY = { it }) + fadeOut(animationSpec = tween(220)))
                } else {
                    (slideInVertically(initialOffsetY = { it }) + fadeIn(animationSpec = tween(280)))
                        .togetherWith(slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(animationSpec = tween(220)))
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
            label = "PrompterSettingsTransition"
        ) { collapsed ->
            if (collapsed) {
                Surface(
                    modifier = Modifier
                        .padding(bottom = 24.dp),
                    shape = RoundedCornerShape(percent = 50),
                    color = if (prompterTheme.value == "BRIGHT") {
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f)
                    } else {
                        Color(0xFF161618).copy(alpha = 0.9f)
                    },
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // "Settings" Button to expand settings back
                        IconButton(
                            onClick = { isSettingsCollapsed = false },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = if (appLanguage == "ru") "Настройки" else "Settings",
                                tint = if (prompterTheme.value == "BRIGHT") MaterialTheme.colorScheme.onSurface else Color.LightGray,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Play/Pause circular button (Start)
                        FilledIconButton(
                            onClick = { isScrolling = !isScrolling },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("prompter_play_pause"),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            AnimatedContent(
                                targetState = isScrolling,
                                transitionSpec = {
                                    (scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn())
                                        .togetherWith(scaleOut(animationSpec = spring(stiffness = Spring.StiffnessHigh)) + fadeOut())
                                },
                                label = "PlayPauseIconMini"
                            ) { scrolling ->
                                Icon(
                                    imageVector = if (scrolling) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = l("prompter", appLanguage),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Exit IconButton
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = l("exit", appLanguage),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            } else {
                // CONTROL OVERLAY CONTAINER (Material 3 Styled Bottom Sheet style overlay)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = if (prompterTheme.value == "BRIGHT") {
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f)
                    } else {
                        Color(0xFF161618).copy(alpha = 0.95f)
                    },
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                var accumulatedDrag = 0f
                                detectVerticalDragGestures(
                                    onDragStart = { accumulatedDrag = 0f },
                                    onDragEnd = { accumulatedDrag = 0f },
                                    onDragCancel = { accumulatedDrag = 0f },
                                    onVerticalDrag = { change, dragAmount ->
                                        accumulatedDrag += dragAmount
                                        if (accumulatedDrag > 45f) { // Swipe down trigger
                                            isSettingsCollapsed = true
                                            accumulatedDrag = 0f
                                        }
                                    }
                                )
                            }
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Tiny drag handle and swipe-to-collapse row
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp, 4.dp)
                                    .background(
                                        color = (if (prompterTheme.value == "BRIGHT") MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray).copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(2.dp)
                                    )
                            )
                            
                            Text(
                                text = if (appLanguage == "ru") "Проведите пальцем вниз, чтобы свернуть настройки" else "Swipe down to collapse settings",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = (if (prompterTheme.value == "BRIGHT") MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray).copy(alpha = 0.6f)
                            )
                        }

                        // Row 1: Estimated Time, Words & Play/Pause Circular FAB
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${l("speech_live", appLanguage)}${remainingTimeMin}${l("minutes_left", appLanguage)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (prompterTheme.value == "BRIGHT") MaterialTheme.colorScheme.onSurface else Color.LightGray
                                )
                                Text(
                                    text = if (appLanguage == "ru") "Всего: $wordsCount слов(а)" else "Total: $wordsCount words",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (prompterTheme.value == "BRIGHT") MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray
                                )
                            }

                            // Floating Action Indicator Play / Pause
                            FilledIconButton(
                                onClick = { isScrolling = !isScrolling },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("prompter_play_pause"),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                AnimatedContent(
                                    targetState = isScrolling,
                                    transitionSpec = {
                                        (scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn())
                                            .togetherWith(scaleOut(animationSpec = spring(stiffness = Spring.StiffnessHigh)) + fadeOut())
                                    },
                                    label = "PlayPauseIconExpanded"
                                ) { scrolling ->
                                    Icon(
                                        imageVector = if (scrolling) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = l("prompter", appLanguage),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        Divider(
                            color = (if (prompterTheme.value == "BRIGHT") MaterialTheme.colorScheme.outlineVariant else Color.DarkGray).copy(alpha = 0.4f),
                            thickness = 0.5.dp
                        )

                        // Row 2: Contrast & Theme Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = l("prompter_theme", appLanguage),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (prompterTheme.value == "BRIGHT") MaterialTheme.colorScheme.onSurfaceVariant else Color.LightGray,
                                modifier = Modifier.width(64.dp)
                            )
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "DARK" to l("theme_dark_prompter", appLanguage),
                                    "AMBER" to l("theme_amber_prompter", appLanguage),
                                    "BRIGHT" to l("theme_bright_prompter", appLanguage)
                                ).forEach { (themeId, label) ->
                                    val isSel = prompterTheme.value == themeId
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { prompterTheme.value = themeId },
                                        label = { Text(label, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) }
                                    )
                                }
                            }
                        }

                        // Row 3: Font Selection
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (appLanguage == "ru") "Шрифт:" else "Font:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (prompterTheme.value == "BRIGHT") MaterialTheme.colorScheme.onSurfaceVariant else Color.LightGray,
                                modifier = Modifier.width(64.dp)
                            )
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "SERIF" to (if (appLanguage == "ru") "Засечки" else "Serif"),
                                    "SANS_SERIF" to (if (appLanguage == "ru") "Без засечек" else "Sans"),
                                    "MONO" to (if (appLanguage == "ru") "Моно" else "Mono")
                                ).forEach { (fontId, label) ->
                                    val isSel = prompterFont == fontId
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { prompterFont = fontId },
                                        label = { Text(label, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) }
                                    )
                                }
                            }
                        }

                        Divider(
                            color = (if (prompterTheme.value == "BRIGHT") MaterialTheme.colorScheme.outlineVariant else Color.DarkGray).copy(alpha = 0.3f),
                            thickness = 0.5.dp
                        )

                        // Row 4: Speed Slider Control
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = l("speed", appLanguage),
                                tint = if (prompterTheme.value == "BRIGHT") MaterialTheme.colorScheme.onSurfaceVariant else Color.LightGray,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (appLanguage == "ru") "Скорость" else "Speed",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (prompterTheme.value == "BRIGHT") MaterialTheme.colorScheme.onSurface else Color.LightGray,
                                modifier = Modifier.width(84.dp)
                            )
                            Slider(
                                value = scrollSpeed,
                                onValueChange = { scrollSpeed = it },
                                valueRange = 1f..50f,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "${scrollSpeed.toInt()}",
                                color = if (prompterTheme.value == "BRIGHT") MaterialTheme.colorScheme.onSurface else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(28.dp)
                            )
                        }

                        // Row 5: Text Size Slider Control
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.TextFields,
                                contentDescription = "Text Size",
                                tint = if (prompterTheme.value == "BRIGHT") MaterialTheme.colorScheme.onSurfaceVariant else Color.LightGray,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (appLanguage == "ru") "Размер" else "Text Size",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (prompterTheme.value == "BRIGHT") MaterialTheme.colorScheme.onSurface else Color.LightGray,
                                modifier = Modifier.width(84.dp)
                            )
                            Slider(
                                value = textFontSize,
                                onValueChange = { textFontSize = it },
                                valueRange = 16f..72f,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "${textFontSize.toInt()}sp",
                                color = if (prompterTheme.value == "BRIGHT") MaterialTheme.colorScheme.onSurface else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(44.dp)
                            )
                        }

                        Divider(
                            color = (if (prompterTheme.value == "BRIGHT") MaterialTheme.colorScheme.outlineVariant else Color.DarkGray).copy(alpha = 0.3f),
                            thickness = 0.5.dp
                        )

                        // Row 6: Mirror Controls & Close Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (appLanguage == "ru") "Зеркало:" else "Mirror:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (prompterTheme.value == "BRIGHT") MaterialTheme.colorScheme.onSurfaceVariant else Color.LightGray
                                )
                                
                                FilterChip(
                                    selected = isMirrorH,
                                    onClick = { isMirrorH = !isMirrorH },
                                    label = { Text(l("mirror_h", appLanguage), fontSize = 11.sp) },
                                    modifier = Modifier.testTag("prompter_mirror_h")
                                )

                                FilterChip(
                                    selected = isMirrorV,
                                    onClick = { isMirrorV = !isMirrorV },
                                    label = { Text(l("mirror_v", appLanguage), fontSize = 11.sp) }
                                )
                            }

                            Button(
                                onClick = onClose,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.ExitToApp, "Exit", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(l("exit", appLanguage), fontSize = 12.sp)
                            }
                        }
                    }
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
        var streak = 0
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val activeDates = statsList.filter { it.wordsCount > 0 }.map { it.dateString }.toSet()
        
        val today = Calendar.getInstance()
        val todayStr = sdf.format(today.time)
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val yesterdayStr = sdf.format(yesterday.time)
        
        val startCal = when {
            activeDates.contains(todayStr) -> today
            activeDates.contains(yesterdayStr) -> yesterday
            else -> null
        }
        
        if (startCal != null) {
            val checkCal = startCal.clone() as Calendar
            val limitSafety = activeDates.size + 2 // Infinite loop guard
            var count = 0
            while (count < limitSafety) {
                val dateStr = sdf.format(checkCal.time)
                if (activeDates.contains(dateStr)) {
                    streak++
                    checkCal.add(Calendar.DAY_OF_YEAR, -1)
                } else {
                    break
                }
                count++
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
                            tint = MaterialTheme.colorScheme.primary,
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
                            color = MaterialTheme.colorScheme.primary,
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
                                tint = MaterialTheme.colorScheme.primary,
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
                                tint = MaterialTheme.colorScheme.primary,
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
                    tint = MaterialTheme.colorScheme.primary,
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
                        val primaryColor = MaterialTheme.colorScheme.primary
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
                                        color = primaryColor,
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
                        tint = MaterialTheme.colorScheme.primary,
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
fun SettingsSectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    val isLightTheme = MaterialTheme.colorScheme.background == Color(0xFFFFFFFF)
    val cardBg = if (isLightTheme) {
        Color(0xFFF2F4F7)
    } else if (MaterialTheme.colorScheme.background == Color(0xFF000000)) {
        Color(0xFF0D0D0D)
    } else {
        Color(0xFF1B1C26)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBg
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isLightTheme) 0.5f else 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            
            // Content
            content()
        }
    }
}

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
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Redesigned bold Title
        Text(
            text = l("sys_settings", appLanguage), 
            fontWeight = FontWeight.Bold, 
            fontSize = 32.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        )

        // 1. Language settings
        SettingsSectionCard(
            icon = Icons.Outlined.Language,
            title = l("app_language", appLanguage),
            subtitle = l("choose_lang_desc", appLanguage)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    "system" to l("lang_system", appLanguage),
                    "ru" to l("lang_ru", appLanguage),
                    "en" to l("lang_en", appLanguage),
                    "es" to l("lang_es", appLanguage)
                ).forEach { (langCode, label) ->
                    val isSelected = appLanguage == langCode
                    
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .bounceClickable { viewModel.setAppLanguage(langCode) },
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                                else if (currTheme == "LIGHT") Color.White else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    else if (currTheme == "LIGHT") Color(0xFFE2E8F0) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                when (langCode) {
                                    "system" -> Icon(
                                        imageVector = Icons.Outlined.Smartphone,
                                        contentDescription = "System",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    else -> {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (currTheme == "LIGHT") Color(0xFFF1F3F5) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = langCode.uppercase(), 
                                                fontSize = 10.sp, 
                                                fontWeight = FontWeight.Black, 
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer 
                                        else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // 2. Theme settings
        SettingsSectionCard(
            icon = Icons.Outlined.BrightnessMedium,
            title = l("theme", appLanguage),
            subtitle = l("choose_theme_desc", appLanguage)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "LIGHT" to (l("theme_light", appLanguage) to Icons.Outlined.LightMode),
                    "DARK" to (l("theme_dark", appLanguage) to Icons.Outlined.DarkMode),
                    "BLACK" to (l("theme_black", appLanguage) to Icons.Outlined.WaterDrop)
                ).forEach { (themeId, pair) ->
                    val (label, icon) = pair
                    val isSelected = currTheme == themeId
                    
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .bounceClickable { onThemeChange(themeId) },
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                                else if (currTheme == "LIGHT") Color.White else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    else if (currTheme == "LIGHT") Color(0xFFE2E8F0) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer 
                                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer 
                                        else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // 3. Color Palette Selector
        val colorPalette by viewModel.colorPalette.collectAsStateWithLifecycle()
        SettingsSectionCard(
            icon = Icons.Outlined.Palette,
            title = l("color_palette", appLanguage),
            subtitle = l("choose_palette_desc", appLanguage)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val palettes = listOf(
                    "AMBER" to (if (currTheme == "LIGHT") Color(0xFF6200EE) else Color(0xFFC7B5F5)),
                    "BLUE" to (if (currTheme == "LIGHT") Color(0xFF165EC0) else Color(0xFF82B1FF)),
                    "GREEN" to (if (currTheme == "LIGHT") Color(0xFF0F6D2E) else Color(0xFF81C784)),
                    "ORANGE" to (if (currTheme == "LIGHT") Color(0xFFD35400) else Color(0xFFFFB74D)),
                    "RED" to (if (currTheme == "LIGHT") Color(0xFF962D22) else Color(0xFFFF8A80)),
                    "CORAL" to (if (currTheme == "LIGHT") Color(0xFFD85D4E) else Color(0xFFFE8B77)),
                    "GREY" to (if (currTheme == "LIGHT") Color(0xFF707070) else Color(0xFFBEBEBE)),
                    "YELLOW" to (if (currTheme == "LIGHT") Color(0xFFB57C00) else Color(0xFFFAD02C)),
                    "PINK" to (if (currTheme == "LIGHT") Color(0xFFD81B60) else Color(0xFFFF80AC))
                )
                palettes.forEach { (paletteId, colorVal) ->
                    val isSelected = colorPalette == paletteId
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .border(
                                width = if (isSelected) 3.dp else 0.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                shape = CircleShape
                            )
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(colorVal)
                            .bounceClickable { viewModel.setColorPalette(paletteId) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = if (paletteId == "AMBER" || paletteId == "BLUE" || paletteId == "GREEN" || paletteId == "RED" || paletteId == "GREY" || paletteId == "PINK") Color.White else Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // 4. Interface Style Selector
        SettingsSectionCard(
            icon = Icons.Outlined.GridView,
            title = l("interface_style", appLanguage),
            subtitle = l("choose_style_desc", appLanguage)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "PIXEL" to (l("style_pixel", appLanguage) to Icons.Default.Apps),
                    "CLASSIC" to (l("style_classic", appLanguage) to Icons.Outlined.ViewQuilt)
                ).forEach { (styleId, pair) ->
                    val (label, icon) = pair
                    val isSelected = interfaceStyle == styleId
                    
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .bounceClickable { viewModel.setInterfaceStyle(styleId) },
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                                else if (currTheme == "LIGHT") Color.White else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    else if (currTheme == "LIGHT") Color(0xFFE2E8F0) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer 
                                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer 
                                        else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // 5. MASTER LOCK CONFIGS
        SettingsSectionCard(
            icon = Icons.Outlined.Lock,
            title = l("security", appLanguage),
            subtitle = l("security_sub_desc", appLanguage)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = inputPinValue,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) inputPinValue = it },
                        placeholder = { Text(l("four_digits", appLanguage)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = "PIN Lock",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pin_setup_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = if (currTheme == "LIGHT") Color.White else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedContainerColor = if (currTheme == "LIGHT") Color.White else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = if (currTheme == "LIGHT") Color(0xFFE2E8F0) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        )
                    )
                    
                    Button(
                        onClick = {
                            if (inputPinValue.length == 4) {
                                viewModel.setupAppPin(inputPinValue)
                                inputPinValue = ""
                                Toast.makeText(context, l("pin_saved", appLanguage), Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("pin_setup_save_btn")
                    ) {
                        Text(l("set", appLanguage), fontWeight = FontWeight.Bold)
                    }
                }
                
                if (isPinConfigured) {
                    Button(
                        onClick = {
                            viewModel.setupAppPin(null)
                            Toast.makeText(context, l("pin_removed", appLanguage), Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(l("disable_pin", appLanguage), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 6. LOCAL BACKUPS
        SettingsSectionCard(
            icon = Icons.Outlined.Backup,
            title = l("backups", appLanguage),
            subtitle = l("backups_desc", appLanguage)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.runBackup(context) { txt ->
                            if (txt != null) {
                                backupPayloadLocal = txt
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
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).testTag("backup_export_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(Icons.Default.Upload, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(l("export_backup", appLanguage), fontSize = 12.sp, maxLines = 1)
                }

                Button(
                    onClick = { backupRestorerLauncher.launch(arrayOf("application/json", "*/*")) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).testTag("backup_restore_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(l("import_backup", appLanguage), fontSize = 12.sp, maxLines = 1)
                }
            }
        }

        // 7. SYNC TO TELEPHONE STORAGE
        SettingsSectionCard(
            icon = Icons.Outlined.Folder,
            title = if (appLanguage == "ru") "Синхронизация папок с памятью" else "Storage Directory Synchronization",
            subtitle = if (appLanguage == "ru") "Синхронизировать все папки и главы проектов в виде файлов во внутренней памяти телефона (в Documents/WriterStudioProjects)" else "Export and sync folders/documents as editable files in the phone internal memory"
        ) {
            Button(
                onClick = {
                    viewModel.syncProjectsToInternalMemory(context) { success, path ->
                        if (success) {
                            Toast.makeText(context, "Успешно! Файлы экспортированы в:\n$path", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Исключение при экспорте: $path", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("sync_projects_to_storage_btn"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(Icons.Filled.Sync, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (appLanguage == "ru") "Синхронизировать с памятью" else "Synchronize with local storage",
                    fontWeight = FontWeight.Bold
                )
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
            "theme" to "Тема приложения",
            "theme_light" to "Светлая",
            "theme_dark" to "Темная",
            "theme_black" to "Черная",
            "color_palette" to "Цветовая палитра",
            "security" to "Безопасность и PIN-код",
            "security_sub" to "Введите 4-значный пароль для блокировки Writer's Studio:",
            "four_digits" to "4 цифры",
            "set" to "Задать",
            "disable_pin" to "Отключить PIN защиту",
            "backups" to "Резервное копирование",
            "export_backup" to "Экспортировать Бэкап",
            "import_backup" to "Импортировать Бэкап",
            "app_language" to "Язык интерфейса",
            "lang_system" to "Системный",
            "lang_ru" to "Русский",
            "lang_en" to "English",
            "lang_es" to "Español",
            "interface_style" to "Стиль интерфейса",
            "style_pixel" to "Pixel Style",
            "style_classic" to "Classic Style",
            "choose_lang_desc" to "Выберите язык приложения",
            "choose_theme_desc" to "Выберите подходящую вам тему",
            "choose_palette_desc" to "Выберите вашу любимую цветовую гамму",
            "choose_style_desc" to "Выберите оформление приложения",
            "security_sub_desc" to "Защитите Writer's Studio с помощью 4-значного PIN-кода",
            "backups_desc" to "Экспорт или импорт локальных файлов бэкапа",
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
            "theme" to "App theme",
            "theme_light" to "Light",
            "theme_dark" to "Dark",
            "theme_black" to "Black",
            "color_palette" to "Color palette",
            "security" to "Security and PIN protection",
            "security_sub" to "Enter 4-digit password to lock Writer's Studio:",
            "four_digits" to "4 digits",
            "set" to "Set",
            "disable_pin" to "Disable PIN Protection",
            "backups" to "Backup and Restore",
            "export_backup" to "Export Backup",
            "import_backup" to "Import Backup",
            "app_language" to "Interface language",
            "lang_system" to "System",
            "lang_ru" to "Russian",
            "lang_en" to "English",
            "lang_es" to "Spanish",
            "interface_style" to "Interface style",
            "style_pixel" to "Pixel Style",
            "style_classic" to "Classic Style",
            "choose_lang_desc" to "Choose the app language",
            "choose_theme_desc" to "Select your preferred theme",
            "choose_palette_desc" to "Pick your favorite color palette",
            "choose_style_desc" to "Choose the look of the interface",
            "security_sub_desc" to "Protect Writer's Studio with a 4-digit PIN",
            "backups_desc" to "Export or import local backup files",
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
            "theme" to "Tema de la aplicación",
            "theme_light" to "Claro",
            "theme_dark" to "Oscuro",
            "theme_black" to "Negro",
            "color_palette" to "Paleta de colores",
            "security" to "Seguridad y PIN de protección",
            "security_sub" to "Ingrese una contraseña de 4 dígitos para bloquear el Estudio:",
            "four_digits" to "4 dígitos",
            "set" to "Establecer",
            "disable_pin" to "Desactivar PIN",
            "backups" to "Copia de seguridad",
            "export_backup" to "Exportar copia",
            "import_backup" to "Importar copia",
            "app_language" to "Idioma de la interfaz",
            "lang_system" to "Sistema",
            "lang_ru" to "Ruso",
            "lang_en" to "Inglés",
            "lang_es" to "Español",
            "interface_style" to "Estilo de interfaz",
            "style_pixel" to "Pixel Style",
            "style_classic" to "Classic Style",
            "choose_lang_desc" to "Selecciona el idioma de la aplicación",
            "choose_theme_desc" to "Elige tu tema preferido",
            "choose_palette_desc" to "Elige tu paleta de colores favorita",
            "choose_style_desc" to "Elige la apariencia de la interfaz",
            "security_sub_desc" to "Protege Writer's Studio con un PIN de 4 dígitos",
            "backups_desc" to "Exportar o importar archivos de copia de seguridad",
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

fun processPlainTextSmartEditor(oldText: String, newText: String): String {
    if (newText.length < oldText.length) {
        var diffIndex = -1
        for (i in 0 until minOf(oldText.length, newText.length)) {
            if (oldText[i] != newText[i]) {
                diffIndex = i
                break
            }
        }
        if (diffIndex == -1) {
            diffIndex = newText.length
        }
        val lineStart = if (diffIndex <= 0) 0 else {
            val lastNl = newText.substring(0, diffIndex).lastIndexOf('\n')
            if (lastNl == -1) 0 else lastNl + 1
        }
        val lineSnippet = newText.substring(lineStart, minOf(newText.length, lineStart + 10))
        val oldLineSnippet = oldText.substring(lineStart, minOf(oldText.length, lineStart + 11))
        
        if (lineSnippet.startsWith("-") && !lineSnippet.startsWith("- ") && oldLineSnippet.startsWith("- ")) {
            return newText.substring(0, lineStart) + newText.substring(lineStart + 1)
        }
    }

    if (newText.length > oldText.length) {
        var diffIndex = -1
        for (i in 0 until minOf(oldText.length, newText.length)) {
            if (oldText[i] != newText[i]) {
                diffIndex = i
                break
            }
        }
        if (diffIndex == -1) {
            diffIndex = oldText.length
        }
        
        val inserted = newText.substring(diffIndex, diffIndex + (newText.length - oldText.length))
        if (inserted.contains("\n")) {
            val beforePart = newText.substring(0, diffIndex)
            val lastNewline = beforePart.lastIndexOf('\n')
            val lineStart = if (lastNewline == -1) 0 else lastNewline + 1
            val lineBefore = beforePart.substring(lineStart)
            
            if (lineBefore.trimStart().startsWith("- ")) {
                val prefix = if (lineBefore.startsWith("  - ")) "  - " else "- "
                val newlinePosInInserted = inserted.indexOf('\n')
                val absoluteNewlinePos = diffIndex + newlinePosInInserted
                
                val left = newText.substring(0, absoluteNewlinePos + 1)
                val right = newText.substring(absoluteNewlinePos + 1)
                return left + prefix + right
            }
        }
    }
    return newText
}

fun processTextFieldValueSmartEditor(oldVal: TextFieldValue, newVal: TextFieldValue): TextFieldValue {
    val oldText = oldVal.text
    val newText = newVal.text
    
    // 1. Text deleted
    if (newText.length < oldText.length) {
        var diffIndex = -1
        for (i in 0 until minOf(oldText.length, newText.length)) {
            if (oldText[i] != newText[i]) {
                diffIndex = i
                break
            }
        }
        if (diffIndex == -1) {
            diffIndex = newText.length
        }
        val lineStart = if (diffIndex <= 0) 0 else {
            val lastNl = newText.substring(0, diffIndex).lastIndexOf('\n')
            if (lastNl == -1) 0 else lastNl + 1
        }
        val lineSnippet = newText.substring(lineStart, minOf(newText.length, lineStart + 10))
        val oldLineSnippet = oldText.substring(lineStart, minOf(oldText.length, lineStart + 11))
        
        if (lineSnippet.startsWith("-") && !lineSnippet.startsWith("- ") && oldLineSnippet.startsWith("- ")) {
            val processedText = newText.substring(0, lineStart) + newText.substring(lineStart + 1)
            return TextFieldValue(
                text = processedText,
                selection = TextRange(lineStart)
            )
        }
        return newVal
    }

    // 2. Text added
    if (newText.length > oldText.length) {
        var diffIndex = -1
        for (i in 0 until minOf(oldText.length, newText.length)) {
            if (oldText[i] != newText[i]) {
                diffIndex = i
                break
            }
        }
        if (diffIndex == -1) {
            diffIndex = oldText.length
        }
        
        val inserted = newText.substring(diffIndex, diffIndex + (newText.length - oldText.length))
        if (inserted.contains("\n")) {
            val beforePart = newText.substring(0, diffIndex)
            val lastNewline = beforePart.lastIndexOf('\n')
            val lineStart = if (lastNewline == -1) 0 else lastNewline + 1
            val lineBefore = beforePart.substring(lineStart)
            
            if (lineBefore.trimStart().startsWith("- ")) {
                val prefix = if (lineBefore.startsWith("  - ")) "  - " else "- "
                val newlinePosInInserted = inserted.indexOf('\n')
                val absoluteNewlinePos = diffIndex + newlinePosInInserted
                
                val left = newText.substring(0, absoluteNewlinePos + 1)
                val right = newText.substring(absoluteNewlinePos + 1)
                val processedText = left + prefix + right
                
                val newCursorPos = absoluteNewlinePos + 1 + prefix.length
                return TextFieldValue(
                    text = processedText,
                    selection = TextRange(newCursorPos)
                )
            }
        }
    }
    
    return newVal
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

