package com.meterx.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.GasMeter
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.meterx.app.MeterViewModel
import com.meterx.app.data.MeterEntity
import com.meterx.app.data.MeterType
import com.meterx.app.data.MeterWithReadings
import com.meterx.app.data.ReadingEntity
import com.meterx.app.data.UsageLevel
import com.meterx.app.data.dailyConsumptionFor
import com.meterx.app.data.usageStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

@Composable
fun MeterXApp(viewModel: MeterViewModel) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val meters by viewModel.meters.collectAsStateWithLifecycle()
    val importPreview by viewModel.importPreview.collectAsStateWithLifecycle()
    val reminderSettings by viewModel.reminderSettings.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedMeterId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val selectedMeter = meters.firstOrNull { it.meter.id == selectedMeterId }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let(viewModel::exportData)
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(viewModel::previewImport)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.setReminderEnabled(true)
        else viewModel.notificationPermissionDenied()
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collectLatest(snackbarHostState::showSnackbar)
    }

    if (!authState.initialized || (authState.loading && authState.user != null)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val authenticatedUser = authState.user
    if (authenticatedUser == null) {
        AuthScreen(
            loading = authState.loading,
            error = authState.error,
            onLogin = viewModel::login,
            onRegister = viewModel::register,
            onClearError = viewModel::clearAuthError,
        )
        return
    }

    if (showSettings) {
        SettingsScreen(
            snackbarHostState = snackbarHostState,
            username = authenticatedUser.username,
            reminderSettings = reminderSettings,
            onBack = { showSettings = false },
            onExport = {
                exportLauncher.launch("meterx-backup-${LocalDate.now()}.json")
            },
            onImport = {
                importLauncher.launch(arrayOf("application/json", "text/json", "text/plain"))
            },
            onLogout = {
                showSettings = false
                viewModel.logout()
            },
            onReminderEnabledChange = { enabled ->
                if (!enabled) {
                    viewModel.setReminderEnabled(false)
                } else if (
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    viewModel.setReminderEnabled(true)
                } else {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onReminderTimeChange = viewModel::setReminderTime,
        )
    } else if (selectedMeter != null) {
        MeterDetailScreen(
            item = selectedMeter,
            snackbarHostState = snackbarHostState,
            onBack = { selectedMeterId = null },
            onAddReading = { value, date, billed ->
                viewModel.addReading(selectedMeter.meter, value, date, billed)
            },
            onUpdateReading = { reading, value, date, billed ->
                viewModel.updateReading(selectedMeter.meter, reading, value, date, billed)
            },
            onDeleteReading = viewModel::deleteReading,
            onReset = { reading ->
                viewModel.resetFreeUnits(selectedMeter.meter, reading)
            },
        )
    } else {
        MeterListScreen(
            meters = meters,
            snackbarHostState = snackbarHostState,
            onOpenMeter = { selectedMeterId = it.meter.id },
            onAddMeter = viewModel::addMeter,
            onDeleteMeter = viewModel::deleteMeter,
            username = authenticatedUser.username,
            syncStatus = syncStatus,
            onSync = viewModel::syncNow,
            onOpenSettings = { showSettings = true },
        )
    }

    importPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = viewModel::dismissImport,
            title = { Text("Replace current app data?") },
            text = {
                Text(
                    "This file contains ${preview.meterCount} meters and " +
                        "${preview.readingCount} readings. Importing will replace all data " +
                        "currently stored in MeterX on this device.",
                )
            },
            confirmButton = {
                Button(onClick = viewModel::confirmImport) { Text("Import and replace") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissImport) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun AuthScreen(
    loading: Boolean,
    error: String?,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String) -> Unit,
    onClearError: () -> Unit,
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var registering by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "MeterX",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (registering) {
                        "Create an account to securely save your meter records."
                    } else {
                        "Sign in to view and sync your meter records."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        onClearError()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Username") },
                    singleLine = true,
                    enabled = !loading,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        onClearError()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    singleLine = true,
                    enabled = !loading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    supportingText = if (registering) {
                        { Text("Use at least 8 characters.") }
                    } else {
                        null
                    },
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = {
                        if (registering) onRegister(username, password)
                        else onLogin(username, password)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading && username.trim().length >= 3 && password.length >= 8,
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(if (registering) "Create account" else "Sign in")
                    }
                }
                TextButton(
                    onClick = {
                        registering = !registering
                        onClearError()
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    enabled = !loading,
                ) {
                    Text(
                        if (registering) "Already registered? Sign in"
                        else "New to MeterX? Create account",
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeterListScreen(
    meters: List<MeterWithReadings>,
    snackbarHostState: SnackbarHostState,
    onOpenMeter: (MeterWithReadings) -> Unit,
    onAddMeter: (String, MeterType, String, String?, Double?) -> Unit,
    onDeleteMeter: (MeterEntity) -> Unit,
    username: String,
    syncStatus: MeterViewModel.SyncStatus,
    onSync: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var showAddMeter by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<MeterEntity?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddMeter = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Add meter") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(bottom = 10.dp),
                ) {
                    Text(
                        text = "MeterX",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Your utilities, clear and under control.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = username,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        CloudSyncButton(
                            status = syncStatus,
                            onClick = onSync,
                        )
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                        }
                    }
                }
            }
            if (meters.isEmpty()) {
                item { EmptyMetersCard(onAdd = { showAddMeter = true }) }
            } else {
                items(meters, key = { it.meter.id }) { item ->
                    MeterCard(
                        item = item,
                        onClick = { onOpenMeter(item) },
                        onDelete = { pendingDelete = item.meter },
                    )
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    if (showAddMeter) {
        AddMeterSheet(
            onDismiss = { showAddMeter = false },
            onSave = { nickname, type, meterNumber, consumerNumber, freeUnits ->
                onAddMeter(nickname, type, meterNumber, consumerNumber, freeUnits)
                showAddMeter = false
            },
        )
    }

    pendingDelete?.let { meter ->
        ConfirmDeleteDialog(
            title = "Delete ${meter.nickname}?",
            message = "This also deletes every reading saved for this meter. This cannot be undone.",
            onDismiss = { pendingDelete = null },
            onConfirm = {
                onDeleteMeter(meter)
                pendingDelete = null
            },
        )
    }
}

@Composable
private fun CloudSyncButton(
    status: MeterViewModel.SyncStatus,
    onClick: () -> Unit,
) {
    val rotation = if (status == MeterViewModel.SyncStatus.SYNCING) {
        val transition = rememberInfiniteTransition(label = "cloud-sync")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = LinearEasing),
            ),
            label = "cloud-sync-rotation",
        ).value
    } else {
        0f
    }
    val icon = when (status) {
        MeterViewModel.SyncStatus.SYNCED -> Icons.Rounded.CloudDone
        MeterViewModel.SyncStatus.SYNCING -> Icons.Rounded.CloudSync
        MeterViewModel.SyncStatus.ERROR -> Icons.Rounded.CloudOff
    }
    val description = when (status) {
        MeterViewModel.SyncStatus.SYNCED -> "Cloud data synced"
        MeterViewModel.SyncStatus.SYNCING -> "Cloud sync in progress"
        MeterViewModel.SyncStatus.ERROR -> "Cloud sync failed. Tap to retry"
    }

    IconButton(
        onClick = onClick,
        enabled = status != MeterViewModel.SyncStatus.SYNCING,
    ) {
        Icon(
            icon,
            contentDescription = description,
            modifier = Modifier.graphicsLayer { rotationZ = rotation },
            tint = if (status == MeterViewModel.SyncStatus.ERROR) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    snackbarHostState: SnackbarHostState,
    username: String,
    reminderSettings: com.meterx.app.reminder.ReminderSettings,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onLogout: () -> Unit,
    onReminderEnabledChange: (Boolean) -> Unit,
    onReminderTimeChange: (Int, Int) -> Unit,
) {
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    "Notifications",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            item {
                ReminderCard(
                    settings = reminderSettings,
                    onEnabledChange = onReminderEnabledChange,
                    onChooseTime = { showTimePicker = true },
                )
            }
            item {
                Text(
                    "Data",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            item {
                DataTransferCard(onExport = onExport, onImport = onImport)
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "Account",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            username,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = onLogout,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = null,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Sign out")
                        }
                    }
                }
            }
        }
    }

    if (showTimePicker) {
        ReminderTimeDialog(
            hour = reminderSettings.hour,
            minute = reminderSettings.minute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                onReminderTimeChange(hour, minute)
                showTimePicker = false
            },
        )
    }
}

@Composable
private fun ReminderCard(
    settings: com.meterx.app.reminder.ReminderSettings,
    onEnabledChange: (Boolean) -> Unit,
    onChooseTime: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(13.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Daily reading reminder",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (settings.enabled) "Every morning at ${formatReminderTime(settings.hour, settings.minute)}"
                        else "Get a morning notification to update readings.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = onEnabledChange,
                )
            }
            OutlinedButton(
                onClick = onChooseTime,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Rounded.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Reminder time: ${formatReminderTime(settings.hour, settings.minute)}")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(
    hour: Int,
    minute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = hour,
        initialMinute = minute,
        is24Hour = false,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose reminder time") },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(state.hour, state.minute) }) {
                Text("Save time")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun DataTransferCard(
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Data transfer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Export all meters and readings to a file, or restore a MeterX file from another device.",
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onExport,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Rounded.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Export")
                }
                Button(
                    onClick = onImport,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Rounded.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Import")
                }
            }
        }
    }
}

@Composable
private fun EmptyMetersCard(onAdd: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Speed, contentDescription = null, tint = Color.White)
            }
            Text("Add your first meter", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Track electricity, water, or gas readings privately on this device.",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Button(onClick = onAdd) { Text("Get started") }
        }
    }
}

@Composable
private fun MeterCard(
    item: MeterWithReadings,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val usage = item.usageStatus()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MeterIcon(item.meter.type)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.meter.nickname,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${item.meter.type.displayName()}  •  ${item.meter.meterNumber}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete meter")
                }
            }

            Spacer(Modifier.height(16.dp))
            if (usage != null) {
                val statusColor = when (usage.level) {
                    UsageLevel.NORMAL -> MaterialTheme.colorScheme.primary
                    UsageLevel.NEAR_LIMIT -> MaterialTheme.colorScheme.tertiary
                    UsageLevel.OVER_LIMIT -> MaterialTheme.colorScheme.error
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Free-unit cycle", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "${formatUnit(usage.used)} / ${formatUnit(usage.allowance)} units",
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { usage.fraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = statusColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                if (usage.level == UsageLevel.OVER_LIMIT) {
                    Text(
                        "Free-unit allowance reached",
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            } else {
                val reading = item.latestReading
                Text(
                    if (reading == null) "No readings yet"
                    else "Latest  ${formatUnit(reading.value)} units • ${formatDate(reading.readingDate)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddMeterSheet(
    onDismiss: () -> Unit,
    onSave: (String, MeterType, String, String?, Double?) -> Unit,
) {
    var nickname by rememberSaveable { mutableStateOf("") }
    var meterNumber by rememberSaveable { mutableStateOf("") }
    var consumerNumber by rememberSaveable { mutableStateOf("") }
    var freeUnits by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(MeterType.ELECTRICITY) }
    var attempted by rememberSaveable { mutableStateOf(false) }

    val parsedFreeUnits = freeUnits.toDoubleOrNull()
    val valid = nickname.isNotBlank() &&
        meterNumber.isNotBlank() &&
        (type != MeterType.ELECTRICITY || (parsedFreeUnits != null && parsedFreeUnits > 0))

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Add a meter", style = MaterialTheme.typography.headlineSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MeterType.entries.forEach { option ->
                    FilterChip(
                        selected = type == option,
                        onClick = { type = option },
                        label = { Text(option.displayName()) },
                        leadingIcon = {
                            Icon(option.icon(), contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                    )
                }
            }
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nickname *") },
                placeholder = { Text("Home main meter") },
                singleLine = true,
                isError = attempted && nickname.isBlank(),
            )
            OutlinedTextField(
                value = meterNumber,
                onValueChange = { meterNumber = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Meter number *") },
                singleLine = true,
                isError = attempted && meterNumber.isBlank(),
            )
            OutlinedTextField(
                value = consumerNumber,
                onValueChange = { consumerNumber = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Consumer number (optional)") },
                singleLine = true,
            )
            AnimatedVisibility(type == MeterType.ELECTRICITY) {
                OutlinedTextField(
                    value = freeUnits,
                    onValueChange = { freeUnits = it.filterDecimal() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Monthly free units *") },
                    supportingText = { Text("MeterX warns you as this allowance runs out.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = attempted && (parsedFreeUnits == null || parsedFreeUnits <= 0),
                )
            }
            Button(
                onClick = {
                    attempted = true
                    if (valid) {
                        onSave(
                            nickname,
                            type,
                            meterNumber,
                            consumerNumber.takeIf(String::isNotBlank),
                            parsedFreeUnits.takeIf { type == MeterType.ELECTRICITY },
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save meter")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeterDetailScreen(
    item: MeterWithReadings,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onAddReading: (Double, Long, Boolean) -> Unit,
    onUpdateReading: (ReadingEntity, Double, Long, Boolean) -> Unit,
    onDeleteReading: (ReadingEntity) -> Unit,
    onReset: (ReadingEntity) -> Unit,
) {
    var showAddReading by rememberSaveable { mutableStateOf(false) }
    var editingReading by remember { mutableStateOf<ReadingEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<ReadingEntity?>(null) }
    var pendingReset by remember { mutableStateOf<ReadingEntity?>(null) }
    val latestBilledReading = item.latestReading?.takeIf { it.isBilled }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(item.meter.nickname, fontWeight = FontWeight.SemiBold)
                        Text(
                            item.meter.type.displayName(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddReading = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "Add reading")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { MeterSummary(item) }
            if (item.meter.type == MeterType.ELECTRICITY && latestBilledReading != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Column(
                            Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("Bill recorded", fontWeight = FontWeight.Bold)
                            Text(
                                "Start a fresh free-unit cycle from ${formatUnit(latestBilledReading.value)} units.",
                            )
                            OutlinedButton(onClick = { pendingReset = latestBilledReading }) {
                                Text("Reset free units")
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "Reading history",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            if (item.sortedReadings.isEmpty()) {
                item {
                    Text(
                        "No readings yet. Add the current meter value to begin.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                }
            } else {
                items(item.sortedReadings, key = { it.id }) { reading ->
                    ReadingRow(
                        reading = reading,
                        dailyConsumption = item.dailyConsumptionFor(reading),
                        onEdit = { editingReading = reading },
                        onDelete = { pendingDelete = reading },
                    )
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showAddReading) {
        ReadingDialog(
            latestValue = item.latestReading?.value,
            onDismiss = { showAddReading = false },
            onSave = { value, date, billed ->
                onAddReading(value, date, billed)
                showAddReading = false
            },
        )
    }
    editingReading?.let { reading ->
        ReadingDialog(
            reading = reading,
            latestValue = item.latestReading
                ?.takeUnless { it.id == reading.id }
                ?.value,
            onDismiss = { editingReading = null },
            onSave = { value, date, billed ->
                onUpdateReading(reading, value, date, billed)
                editingReading = null
            },
        )
    }
    pendingDelete?.let { reading ->
        ConfirmDeleteDialog(
            title = "Delete this reading?",
            message = "${formatUnit(reading.value)} units on ${formatDate(reading.readingDate)} will be removed.",
            onDismiss = { pendingDelete = null },
            onConfirm = {
                onDeleteReading(reading)
                pendingDelete = null
            },
        )
    }
    pendingReset?.let { reading ->
        AlertDialog(
            onDismissRequest = { pendingReset = null },
            title = { Text("Reset free units?") },
            text = {
                Text("The new cycle will start at ${formatUnit(reading.value)} units. Earlier readings stay in history.")
            },
            confirmButton = {
                Button(onClick = {
                    onReset(reading)
                    pendingReset = null
                }) { Text("Reset cycle") }
            },
            dismissButton = {
                TextButton(onClick = { pendingReset = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun MeterSummary(item: MeterWithReadings) {
    val usage = item.usageStatus()
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MeterIcon(item.meter.type)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Meter ${item.meter.meterNumber}", fontWeight = FontWeight.Bold)
                    item.meter.consumerNumber?.let {
                        Text(
                            "Consumer $it",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            HorizontalDivider()
            if (usage == null) {
                Text(
                    item.latestReading?.let { "${formatUnit(it.value)} units" } ?: "Waiting for first reading",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                val color = when (usage.level) {
                    UsageLevel.NORMAL -> MaterialTheme.colorScheme.primary
                    UsageLevel.NEAR_LIMIT -> MaterialTheme.colorScheme.tertiary
                    UsageLevel.OVER_LIMIT -> MaterialTheme.colorScheme.error
                }
                Text("Used this free-unit cycle", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${formatUnit(usage.used)} units",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
                LinearProgressIndicator(
                    progress = { usage.fraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                    color = color,
                )
                Text(
                    "${(usage.fraction * 100).roundToInt()}% of ${formatUnit(usage.allowance)} free units",
                    color = color,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ReadingRow(
    reading: ReadingEntity,
    dailyConsumption: com.meterx.app.data.DailyConsumption?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${formatUnit(reading.value)} units",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    formatDate(reading.readingDate),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                dailyConsumption?.let { consumption ->
                    Text(
                        "${formatUnit(consumption.unitsUsed)} units in " +
                            "${consumption.elapsedDays} ${if (consumption.elapsedDays == 1L) "day" else "days"}" +
                            "  •  ${formatUnit(consumption.averagePerDay)} units/day",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            if (reading.isBilled) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = "Billed",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Billed", color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Rounded.Edit, contentDescription = "Edit reading")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete reading")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingDialog(
    reading: ReadingEntity? = null,
    latestValue: Double?,
    onDismiss: () -> Unit,
    onSave: (Double, Long, Boolean) -> Unit,
) {
    var value by rememberSaveable(reading?.id) {
        mutableStateOf(reading?.value?.let(::formatUnit).orEmpty())
    }
    var selectedDate by rememberSaveable(reading?.id) {
        mutableLongStateOf(reading?.readingDate ?: LocalDate.now().toEpochDay())
    }
    var billed by rememberSaveable(reading?.id) {
        mutableStateOf(reading?.isBilled ?: false)
    }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val parsedValue = value.toDoubleOrNull()
    val valid = parsedValue != null && parsedValue >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (reading == null) "Add meter reading" else "Edit meter reading") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.filterDecimal() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Current reading *") },
                    suffix = { Text("units") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = attempted && !valid,
                    supportingText = {
                        latestValue?.let { Text("Previous latest: ${formatUnit(it)} units") }
                    },
                    singleLine = true,
                )
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(formatDate(selectedDate))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Bill made through this reading")
                        Text(
                            "Enables a free-unit cycle reset.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = billed, onCheckedChange = { billed = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                attempted = true
                if (valid) onSave(parsedValue!!, selectedDate, billed)
            }) { Text(if (reading == null) "Add reading" else "Save changes") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )

    if (showDatePicker) {
        val initialMillis = LocalDate.ofEpochDay(selectedDate)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        selectedDate = Instant.ofEpochMilli(it)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                            .toEpochDay()
                    }
                    showDatePicker = false
                }) { Text("Done") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun MeterIcon(type: MeterType) {
    val color = when (type) {
        MeterType.ELECTRICITY -> Color(0xFFF59E0B)
        MeterType.WATER -> Color(0xFF2563EB)
        MeterType.GAS -> Color(0xFF7C3AED)
    }
    Box(
        modifier = Modifier
            .size(46.dp)
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(type.icon(), contentDescription = null, tint = color)
    }
}

private fun MeterType.icon(): ImageVector = when (this) {
    MeterType.ELECTRICITY -> Icons.Rounded.Bolt
    MeterType.WATER -> Icons.Rounded.Opacity
    MeterType.GAS -> Icons.Rounded.GasMeter
}

private fun MeterType.displayName(): String =
    name.lowercase().replaceFirstChar(Char::uppercase)

private fun formatUnit(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(value)

private fun formatDate(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("d MMM yyyy"))

private fun formatReminderTime(hour: Int, minute: Int): String =
    LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("h:mm a"))

private fun String.filterDecimal(): String {
    var dotSeen = false
    return filter { char ->
        when {
            char.isDigit() -> true
            char == '.' && !dotSeen -> {
                dotSeen = true
                true
            }
            else -> false
        }
    }
}
