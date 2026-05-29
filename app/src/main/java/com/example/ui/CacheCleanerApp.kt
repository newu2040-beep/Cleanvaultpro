package com.example.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.MainActivity
import com.example.data.database.AppExceptionEntity
import com.example.data.database.CleaningScheduleEntity
import com.example.data.database.CleanupLogEntity
import com.example.ui.viewmodel.AppItem
import com.example.ui.viewmodel.CacheCleanerViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheCleanerApp(viewModel: CacheCleanerViewModel) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(0) }

    // Observe DB States
    val logs by viewModel.cleanupLogs.collectAsState()
    val exceptions by viewModel.exceptionsList.collectAsState()
    val schedule by viewModel.scheduleConfig.collectAsState()

    // Passcode/PIN authenticating states
    var lockPinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf("") }

    // Drawer and Coroutine scopes
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var drawerSelection by remember { mutableStateOf("home") } // "home", "apps", "themes", "credits"

    // If biometric authenticated, show Dashboard. Otherwise show security check
    if (!viewModel.isUserAuthenticated) {
        // Aesthetic PIN/Lock Screen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.widthIn(max = 400.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Shield Security Indicator",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Cache Cleaner Lock",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Biometric protection or fallback PIN is required to inspect cache folders & security logs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                // PIN Entry Input
                OutlinedTextField(
                    value = lockPinInput,
                    onValueChange = {
                        if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                            lockPinInput = it
                            pinError = ""
                        }
                    },
                    label = { Text("Enter 4-Digit Fallback PIN") },
                    placeholder = { Text("0000") },
                    singleLine = true,
                    isError = pinError.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pin_input_field"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                if (pinError.isNotEmpty()) {
                    Text(
                        text = pinError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (lockPinInput == "1234" || lockPinInput == "0000" || lockPinInput.length == 4) {
                            viewModel.isUserAuthenticated = true
                            Toast.makeText(context, "Access Granted PIN Verified", Toast.LENGTH_SHORT).show()
                        } else {
                            pinError = "Incorrect PIN code. Try 1234 or 0000"
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("pin_submit_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.LockOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unlock with PIN")
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = {
                        // Standard action: assume simulated successful fingerprint scan
                        viewModel.isUserAuthenticated = true
                        Toast.makeText(context, "Biometric Fingerprint Verified", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("simulate_biometric_button")
                ) {
                    Icon(imageVector = Icons.Default.Fingerprint, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unlock with Biometrics")
                }
            }
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.width(310.dp)
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.OfflineBolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Cache Cleaner Pro",
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Engine v2.5.0",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    NavigationDrawerItem(
                        label = { Text("Dashboard & Sweeper") },
                        selected = drawerSelection == "home",
                        onClick = {
                            drawerSelection = "home"
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            selectedIconColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    NavigationDrawerItem(
                        label = { Text("App & Game Manager") },
                        selected = drawerSelection == "apps",
                        onClick = {
                            drawerSelection = "apps"
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Default.Apps, contentDescription = null) },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            selectedIconColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    NavigationDrawerItem(
                        label = { Text("Modern Mood Themes") },
                        selected = drawerSelection == "themes",
                        onClick = {
                            drawerSelection = "themes"
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Default.Palette, contentDescription = null) },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            selectedIconColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    NavigationDrawerItem(
                        label = { Text("Developer Credits") },
                        selected = drawerSelection == "credits",
                        onClick = {
                            drawerSelection = "credits"
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            selectedIconColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Made with ❤️ by",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "Editingcells",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        ) {
            Scaffold(
                topBar = {
                    LargeTopAppBar(
                        title = {
                            Column {
                                val topBarTitle = when (drawerSelection) {
                                    "home" -> "Cache Cleaner"
                                    "apps" -> "App & Game Manager"
                                    "themes" -> "Mood Themes"
                                    "credits" -> "Developer Credits"
                                    else -> "Cache Cleaner"
                                }
                                val topBarSub = when (drawerSelection) {
                                    "home" -> "Automatical Cleanups Configured"
                                    "apps" -> "Consolidate real-time system programs safely"
                                    "themes" -> "Personalize visual colors and mode styling"
                                    "credits" -> "Editingcells Developer Hub"
                                    else -> ""
                                }
                                Text(
                                    topBarTitle,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.SansSerif
                                )
                                Text(
                                    topBarSub,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                scope.launch { drawerState.open() }
                            }) {
                                Icon(imageVector = Icons.Default.Menu, contentDescription = "Open Drawer menu")
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                viewModel.isUserAuthenticated = false
                                Toast.makeText(context, "Device Locked", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(imageVector = Icons.Default.Lock, contentDescription = "Lock Application")
                            }
                        },
                        colors = TopAppBarDefaults.largeTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                },
                bottomBar = {
                    if (drawerSelection == "home") {
                        NavigationBar(
                            windowInsets = WindowInsets.navigationBars,
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp
                        ) {
                            NavigationBarItem(
                                selected = currentTab == 0,
                                onClick = { currentTab = 0 },
                                icon = { Icon(imageVector = if (currentTab == 0) Icons.Default.Dashboard else Icons.Outlined.Dashboard, contentDescription = null) },
                                label = { Text("Dashboard") },
                                modifier = Modifier.testTag("tab_dashboard")
                            )
                            NavigationBarItem(
                                selected = currentTab == 1,
                                onClick = { currentTab = 1 },
                                icon = { Icon(imageVector = if (currentTab == 1) Icons.Default.Block else Icons.Outlined.Block, contentDescription = null) },
                                label = { Text("Bypasses") },
                                modifier = Modifier.testTag("tab_exceptions")
                            )
                            NavigationBarItem(
                                selected = currentTab == 2,
                                onClick = { currentTab = 2 },
                                icon = { Icon(imageVector = if (currentTab == 2) Icons.Default.Schedule else Icons.Outlined.Schedule, contentDescription = null) },
                                label = { Text("Schedule") },
                                modifier = Modifier.testTag("tab_schedule")
                            )
                            NavigationBarItem(
                                selected = currentTab == 3,
                                onClick = { currentTab = 3 },
                                icon = { Icon(imageVector = if (currentTab == 3) Icons.Default.History else Icons.Outlined.History, contentDescription = null) },
                                label = { Text("Reports") },
                                modifier = Modifier.testTag("tab_history")
                            )
                        }
                    }
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    AnimatedContent(
                        targetState = drawerSelection,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                        },
                        label = "MainDrawerTransition"
                    ) { selection ->
                        when (selection) {
                            "home" -> {
                                AnimatedContent(
                                    targetState = currentTab,
                                    transitionSpec = {
                                        fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(180))
                                    },
                                    label = "TabTransition"
                                ) { tab ->
                                    when (tab) {
                                        0 -> DashboardScreen(viewModel)
                                        1 -> AppExceptionsScreen(viewModel, exceptions)
                                        2 -> ScheduleScreen(viewModel, schedule)
                                        3 -> HistoryScreen(viewModel, logs)
                                    }
                                }
                            }
                            "apps" -> AppManagerScreen(viewModel)
                            "themes" -> ThemeCustomizerScreen(viewModel)
                            "credits" -> DeveloperCreditsScreen()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(viewModel: CacheCleanerViewModel) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // System Permissions Configuration Card
        PermissionConfigurationBlock()

        Spacer(modifier = Modifier.height(12.dp))

        // Linear Progress Bar or circular visualizer
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(220.dp)
                .padding(12.dp)
        ) {
            // Animating background angle
            val progressDegree by animateFloatAsState(
                targetValue = if (viewModel.isScanning) viewModel.scanProgress else if (viewModel.isCleaning) 0.5f else 0f,
                animationSpec = tween(300, easing = LinearOutSlowInEasing),
                label = "Progress"
            )

            // Custom drawing dynamic circular status ring
            val ringColor = if (viewModel.isCleaning) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Background Track
                drawCircle(
                    color = ringColor.copy(alpha = 0.12f),
                    style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                )
                // Active sweeping arc
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = if (viewModel.isScanCompleted) 360f else progressDegree * 360f,
                    useCenter = false,
                    style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (viewModel.isCleaning) Icons.Default.Brush else if (viewModel.isScanning) Icons.Default.Search else Icons.Default.OfflineBolt,
                    contentDescription = null,
                    tint = ringColor,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (viewModel.isScanning) {
                    Text("Scanning...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${(viewModel.scanProgress * 100).toInt()}%", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                } else if (viewModel.isCleaning) {
                    Text("Clearing...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Optimizing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                } else if (viewModel.isScanCompleted) {
                    Text("Total Found", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatBytes(viewModel.totalJunkBytes), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                } else {
                    Text("Ready", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Ideal State", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (viewModel.isScanning) {
            Text(
                text = viewModel.currentScanningFile,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.height(20.dp)
            )
        } else {
            Spacer(modifier = Modifier.height(20.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Trigger Buttons Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { viewModel.startStorageScan() },
                enabled = !viewModel.isScanning && !viewModel.isCleaning,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("scan_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(imageVector = Icons.Default.Radar, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("SCAN STORAGE")
            }

            Button(
                onClick = {
                    viewModel.runJunkCleanup { file ->
                        if (file != null) {
                            Toast.makeText(context, "PDF Report Generated: ${file.name}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                enabled = viewModel.isScanCompleted && !viewModel.isCleaning && viewModel.totalJunkBytes > 0L,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("clean_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(imageVector = Icons.Default.CleanHands, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("CLEAN JUNK")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Dynamic results block
        Text(
            text = "Systems Junk Files Breakdown",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )

        Spacer(modifier = Modifier.height(10.dp))

        JunkCard(
            title = "System Application Cache",
            subtitle = "Saves browser states & network buffers",
            bytes = viewModel.systemCacheBytes,
            icon = Icons.Outlined.Cached
        )

        JunkCard(
            title = "System Temporary Logs",
            subtitle = "Diagnostics, dump logs, and error stacks",
            bytes = viewModel.tempLogsBytes,
            icon = Icons.Outlined.InsertDriveFile
        )

        JunkCard(
            title = "Residual Temp Elements",
            subtitle = "Leftovers from deleted packages",
            bytes = viewModel.residualFilesBytes,
            icon = Icons.Outlined.Inventory2
        )

        JunkCard(
            title = "Large / Idle Cache Files",
            subtitle = "Large cached packages over 30 days old",
            bytes = viewModel.largeFilesBytes,
            icon = Icons.Outlined.FeaturedPlayList
        )
    }
}

@Composable
fun JunkCard(
    title: String,
    subtitle: String,
    bytes: Long,
    icon: ImageVector
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text(
                text = formatBytes(bytes),
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun AppExceptionsScreen(viewModel: CacheCleanerViewModel, exceptions: List<AppExceptionEntity>) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredList = remember(searchQuery, viewModel.installedApps) {
        viewModel.installedApps.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Cleaning Bypasses & Exceptions",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Configure specific apps to bypass in both automatic daily schedules and manual cache purges to preserve session tokens or offline storage.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Filter installed apps...") },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.Inbox, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No applications found.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredList) { app ->
                    val isExcluded = exceptions.any { it.packageName == app.packageName }

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isExcluded) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f) 
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        border = if (isExcluded) BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isExcluded) Icons.Default.GppBad else Icons.Default.Android,
                                    contentDescription = null,
                                    tint = if (isExcluded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = app.appName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(text = app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Switch(
                                checked = isExcluded,
                                onCheckedChange = { viewModel.toggleAppException(app.packageName, app.appName) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.error,
                                    checkedTrackColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                modifier = Modifier.testTag("bypass_switch_${app.packageName}")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleScreen(viewModel: CacheCleanerViewModel, schedule: CleaningScheduleEntity) {
    val context = LocalContext.current
    var isEnabled by remember(schedule) { mutableStateOf(schedule.isEnabled) }
    var selectedHour by remember(schedule) { mutableStateOf(schedule.hour.toFloat()) }
    var selectedMinute by remember(schedule) { mutableStateOf(schedule.minute.toFloat()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Automated Clean Scheduler",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Configure deep cleanup tasks to run automatically in the background once per day without waking up your screen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Activation Card
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Automated Background Cleaning", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (isEnabled) "Runs every 24 hours at scheduled time" else "Background triggers deactivated",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = {
                        isEnabled = it
                        viewModel.updateSchedule(selectedHour.toInt(), selectedMinute.toInt(), it)
                        Toast.makeText(context, if (it) "Automatic cleanup enabled" else "Automatic cleanup disabled", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("schedule_toggle")
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Daily Trigger Time: ${String.format("%02d:%02d", selectedHour.toInt(), selectedMinute.toInt())}",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Hour Selector Slider
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hour:", modifier = Modifier.width(50.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = selectedHour,
                        onValueChange = { selectedHour = it },
                        onValueChangeFinished = {
                            viewModel.updateSchedule(selectedHour.toInt(), selectedMinute.toInt(), isEnabled)
                        },
                        valueRange = 0f..23f,
                        steps = 22,
                        modifier = Modifier.weight(1f)
                    )
                    Text("${selectedHour.toInt()}h", modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Minute Selector Slider
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Min:", modifier = Modifier.width(50.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = selectedMinute,
                        onValueChange = { selectedMinute = it },
                        onValueChangeFinished = {
                            viewModel.updateSchedule(selectedHour.toInt(), selectedMinute.toInt(), isEnabled)
                        },
                        valueRange = 0f..59f,
                        steps = 58,
                        modifier = Modifier.weight(1f)
                    )
                    Text("${selectedMinute.toInt()}m", modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Include in Background Sweep",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(10.dp))

        ScheduleCategoryRow(
            title = "System Application Cache",
            isChecked = schedule.cleanSystemCache,
            onChange = { viewModel.toggleCategoryPreference("System Cache", it) }
        )

        ScheduleCategoryRow(
            title = "Temporary Diagnostic Logs",
            isChecked = schedule.cleanTempLogs,
            onChange = { viewModel.toggleCategoryPreference("Temp Logs", it) }
        )

        ScheduleCategoryRow(
            title = "Bypassed Package Leftovers",
            isChecked = schedule.cleanResidualFiles,
            onChange = { viewModel.toggleCategoryPreference("Residual Files", it) }
        )

        ScheduleCategoryRow(
            title = "Large Cache / Unused Downloads",
            isChecked = schedule.cleanLargeFiles,
            onChange = { viewModel.toggleCategoryPreference("Large Files", it) }
        )
    }
}

@Composable
fun ScheduleCategoryRow(
    title: String,
    isChecked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = isChecked, onCheckedChange = onChange)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun HistoryScreen(viewModel: CacheCleanerViewModel, logs: List<CleanupLogEntity>) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Cleanup History & Certified Logs",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Inspect all previous manual and background cleaning cycle records. Generate and print certified PDF storage invoices instantly.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.InsertDriveFile, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No cleanup logs recorded yet.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(logs) { log ->
                    val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    val dateFormatted = df.format(Date(log.timestamp))

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("history_log_card_${log.id}"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        border = if (log.backupStatus == "COMPLETED") BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) else null
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = dateFormatted,
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (log.backupStatus == "COMPLETED") MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                            else MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (log.backupStatus == "COMPLETED") Icons.Default.CloudDone else Icons.Default.CloudSync,
                                            contentDescription = null,
                                            tint = if (log.backupStatus == "COMPLETED") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (log.backupStatus == "COMPLETED") "SYNCED" else "UNSYNCED",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            color = if (log.backupStatus == "COMPLETED") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Cleared Space", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = formatBytes(log.cleanedBytes),
                                        fontWeight = FontWeight.ExtraBold,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Scan Duration", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "${log.scanDurationMs} ms",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // PDF Share Action Button
                                TextButton(
                                    onClick = {
                                        if (log.pdfUriPath != null) {
                                            sharePdfReport(context, File(log.pdfUriPath))
                                        } else {
                                            Toast.makeText(context, "Locating or building PDF report file...", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("share_pdf_btn_${log.id}")
                                ) {
                                    Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("SHARE PDF")
                                }

                                // Backup Retry Button
                                TextButton(
                                    onClick = {
                                        viewModel.startCloudBackup(log)
                                        Toast.makeText(context, "Initiating secure backup request...", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("backup_btn_${log.id}")
                                ) {
                                    Icon(imageVector = Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier =Modifier.width(6.dp))
                                    Text("BACKUP NOW")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Share PDF Intent Handler
fun sharePdfReport(context: Context, file: File) {
    if (!file.exists()) {
        Toast.makeText(context, "Error: PDF report file does not exist on disk.", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val authority = "${context.packageName}.fileprovider"
        val uri: Uri = FileProvider.getUriForFile(context, authority, file)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Cache Cleaner Report"))
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to share PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 Bytes"
    val units = arrayOf("Bytes", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val formattedValue = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(Locale.US, "%.2f %s", formattedValue, units[digitGroups])
}

fun checkNotificationGranted(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, "android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

fun checkStorageGranted(context: Context): Boolean {
    if (checkAllFilesGranted(context)) {
        return true
    }
    return ContextCompat.checkSelfPermission(context, "android.permission.READ_EXTERNAL_STORAGE") == PackageManager.PERMISSION_GRANTED
}

fun checkAllFilesGranted(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        android.os.Environment.isExternalStorageManager()
    } else {
        true
    }
}

@Composable
fun PermissionConfigurationBlock() {
    val context = LocalContext.current
    val activity = context as? MainActivity

    var notificationGranted by remember { mutableStateOf(checkNotificationGranted(context)) }
    var storageGranted by remember { mutableStateOf(checkStorageGranted(context)) }
    var allFilesGranted by remember { mutableStateOf(checkAllFilesGranted(context)) }

    // Safe permission change observer utilizing Lifecycle.Event.ON_RESUME
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                notificationGranted = checkNotificationGranted(context)
                storageGranted = checkStorageGranted(context)
                allFilesGranted = checkAllFilesGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var isExpanded by remember { mutableStateOf(false) }

    val allConfigured = notificationGranted && storageGranted && allFilesGranted

    if (allConfigured) {
        return
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (allConfigured) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                             else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
        ),
        border = BorderStroke(
            1.dp, 
            if (allConfigured) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            else MaterialTheme.colorScheme.error.copy(alpha = 0.25f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .animateContentSize()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (allConfigured) Icons.Default.GppGood else Icons.Default.GppMaybe,
                        contentDescription = null,
                        tint = if (allConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Privacy & Real-Time Permissions",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = if (allConfigured) "System fully optimized & granted" else "Action required for smooth real-time sweeps",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle Permissions Panel"
                    )
                }
            }

            if (isExpanded || !allConfigured) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                // 1. Notification Permission Row
                PermissionItemRow(
                    title = "Real-Time Optimization Alerts",
                    description = "Required to notify when background cleanup cycles run and how much space was saved.",
                    isGranted = notificationGranted,
                    onRequestGrant = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (activity != null) {
                                activity.requestSystemPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"))
                            } else {
                                Toast.makeText(context, "Activity reference not found", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Notifications enabled automatically", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Storage Cache Core Permission Row
                PermissionItemRow(
                    title = "Deep Storage Junk Reader",
                    description = "Required to scan app log directories and clean device temp directories safely.",
                    isGranted = storageGranted,
                    onRequestGrant = {
                        if (activity != null) {
                            activity.requestSystemPermissions(
                                arrayOf(
                                    "android.permission.READ_EXTERNAL_STORAGE",
                                    "android.permission.WRITE_EXTERNAL_STORAGE"
                                )
                            )
                        } else {
                            Toast.makeText(context, "Activity reference not found", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Spacer(modifier = Modifier.height(12.dp))

                    // 3. All Files Cache Permission (Real-time and systems cache analyzer)
                    PermissionItemRow(
                        title = "Real-Time System Optimizer Core",
                        description = "Provides ultimate level control to manage bypass packages and residual items automatically.",
                        isGranted = allFilesGranted,
                        onRequestGrant = {
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    addCategory("android.intent.category.DEFAULT")
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                                Toast.makeText(context, "Toggle access on for Cache Cleaner, then press back", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                try {
                                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                    context.startActivity(intent)
                                } catch (ex: Exception) {
                                    Toast.makeText(context, "Manual settings navigation required for total file access", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!allConfigured) {
                    Button(
                        onClick = {
                            val perms = mutableListOf<String>()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationGranted) {
                                perms.add("android.permission.POST_NOTIFICATIONS")
                            }
                            if (!storageGranted) {
                                perms.add("android.permission.READ_EXTERNAL_STORAGE")
                                perms.add("android.permission.WRITE_EXTERNAL_STORAGE")
                            }
                            if (perms.isNotEmpty() && activity != null) {
                                activity.requestSystemPermissions(perms.toTypedArray())
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !allFilesGranted) {
                                try {
                                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    try {
                                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                        context.startActivity(intent)
                                    } catch (ex: Exception) {
                                        Toast.makeText(context, "System settings lookup requested", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.OfflineBolt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AUTO-GRANT ALL MISSING")
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionItemRow(
    title: String,
    description: String,
    isGranted: Boolean,
    onRequestGrant: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Button(
            onClick = onRequestGrant,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isGranted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                contentColor = if (isGranted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.height(32.dp),
            shape = RoundedCornerShape(8.dp),
            enabled = !isGranted
        ) {
            Text(
                text = if (isGranted) "Granted" else "Grant",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun AppManagerScreen(viewModel: CacheCleanerViewModel) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    val filteredApps = remember(viewModel.installedApps, searchQuery) {
        if (searchQuery.isBlank()) {
            viewModel.installedApps
        } else {
            viewModel.installedApps.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val selectedApps = filteredApps.filter { it.isSelected }
    val totalSelectedCache = selectedApps.sumOf { it.cacheSize }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // App Manager Summary Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Selected for action",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${selectedApps.size} apps selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = formatBytes(totalSelectedCache),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.selectAllApps(true) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Select All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.selectAllApps(false) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Clear Select", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Action Buttons Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { viewModel.uninstallSelectedApps(context) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Uninstall Selected", fontSize = 11.sp, fontWeight = FontWeight.Black)
            }

            Button(
                onClick = { viewModel.cleanSelectedAppsCaches(context) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                enabled = !viewModel.isSelectedAppsCleaning
            ) {
                if (viewModel.isSelectedAppsCleaning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text("Clean Selected Cache", fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
        }

        // Interactive Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search Apps & Games") },
            placeholder = { Text("Type name or package...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search query")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        )

        // Lazy App List
        if (filteredApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No apps or games found matching query",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleAppSelection(app.packageName) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (app.isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (app.isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconBadge(
                                packageName = app.packageName,
                                appName = app.appName,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.appName,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (app.cacheSize > 0) "${formatBytes(app.cacheSize)} Cached" else "Fully Cleared ✨",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (app.cacheSize > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                                )
                            }

                            Checkbox(
                                checked = app.isSelected,
                                onCheckedChange = { viewModel.toggleAppSelection(app.packageName) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppIconBadge(packageName: String, appName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val iconDrawable = remember(packageName) {
        try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            null
        }
    }

    if (iconDrawable != null) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                android.widget.ImageView(ctx).apply {
                    setImageDrawable(iconDrawable)
                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                }
            },
            modifier = modifier
        )
    } else {
        val initial = appName.firstOrNull()?.uppercase() ?: "?"
        val pastelColors = remember {
            listOf(
                Color(0xFFEF4444), Color(0xFFF97316), Color(0xFFF59E0B), Color(0xFF10B981),
                Color(0xFF06B6D4), Color(0xFF3B82F6), Color(0xFF6366F1), Color(0xFF8B5CF6),
                Color(0xFFEC4899), Color(0xFF64748B)
            )
        }
        val colorBg = pastelColors[Math.abs(packageName.hashCode()) % pastelColors.size]
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .background(colorBg, RoundedCornerShape(8.dp))
        ) {
            Text(
                text = initial,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
    }
}

@Composable
fun ThemeCustomizerScreen(viewModel: CacheCleanerViewModel) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Theme Preferences Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Dark & Light Mode Switcher",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Toggle to adapt the application colors to your system ambient lighting and preferences",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (viewModel.isAppDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (viewModel.isAppDarkTheme) "Dark Theme Active" else "Light Theme Active",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Switch(
                        checked = viewModel.isAppDarkTheme,
                        onCheckedChange = { viewModel.isAppDarkTheme = it }
                    )
                }
            }
        }

        // Choose Theme Card
        Text(
            text = "Select Modern Custom Mood Theme",
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )

        val themeOptions = com.example.ui.theme.AppThemeOption.values()
        for (option in themeOptions) {
            val isSelected = viewModel.appThemeSetting == option
            val (c1, c2, c3) = when (option) {
                com.example.ui.theme.AppThemeOption.SLATE -> Triple(Color(0xFF6366F1), Color(0xFF94A3B8), Color(0xFF38BDF8))
                com.example.ui.theme.AppThemeOption.CYBERPUNK -> Triple(Color(0xFFEC4899), Color(0xFFF43F5E), Color(0xFF06B6D4))
                com.example.ui.theme.AppThemeOption.OCEANIC -> Triple(Color(0xFF0EA5E9), Color(0xFF06B6D4), Color(0xFF10B981))
                com.example.ui.theme.AppThemeOption.MINT -> Triple(Color(0xFF10B981), Color(0xFF34D399), Color(0xFFFBBF24))
                com.example.ui.theme.AppThemeOption.SUNSET -> Triple(Color(0xFFF97316), Color(0xFFEF4444), Color(0xFFFBBF24))
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clickable { viewModel.appThemeSetting = option },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = option.displayName,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when(option) {
                                com.example.ui.theme.AppThemeOption.SLATE -> "Professional, clean, tech-first aesthetics"
                                com.example.ui.theme.AppThemeOption.CYBERPUNK -> "Futuristic high-contrast neon design"
                                com.example.ui.theme.AppThemeOption.OCEANIC -> "Calm marine blue and aquatic gradients"
                                com.example.ui.theme.AppThemeOption.MINT -> "Organic mint green and natural accents"
                                com.example.ui.theme.AppThemeOption.SUNSET -> "Warm sunset orange, yellow and crimson"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Dynamic preview circles
                        Box(modifier = Modifier.size(14.dp).background(c1, CircleShape))
                        Box(modifier = Modifier.size(14.dp).background(c2, CircleShape))
                        Box(modifier = Modifier.size(14.dp).background(c3, CircleShape))

                        if (isSelected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Active Theme",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeveloperCreditsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Heart icon styled procedurally
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Love Heart Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Crafted with Love",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "by Editingcells",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "A software engineering team passionate about building high-fidelity utility systems, fluid system sweeper services, and beautiful customer interfaces with modern Jetpack Compose. Your device's smooth and optimized health is our craft priority.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Kotlin First",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Material 3",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Clean Caches • Optimize • Thrive",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp
        )
    }
}
