package com.meterx.app

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.meterx.app.data.AuthSession
import com.meterx.app.data.AuthUser
import com.meterx.app.data.MeterEntity
import com.meterx.app.data.MeterRepository
import com.meterx.app.data.MeterType
import com.meterx.app.data.MeterWithReadings
import com.meterx.app.data.ReadingEntity
import com.meterx.app.data.ImportPreview
import com.meterx.app.reminder.ReminderManager
import com.meterx.app.reminder.ReminderSettings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MeterViewModel(
    private val repository: MeterRepository,
    private val reminderManager: ReminderManager,
    private val authSession: AuthSession,
) : ViewModel() {
    enum class SyncStatus {
        SYNCED,
        SYNCING,
        ERROR,
    }

    data class AuthUiState(
        val user: AuthUser? = null,
        val loading: Boolean = false,
        val initialized: Boolean = false,
        val error: String? = null,
    )

    private val _authState = MutableStateFlow(AuthUiState(user = authSession.user.value))
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()
    private val _importPreview = MutableStateFlow<ImportPreview?>(null)
    val importPreview: StateFlow<ImportPreview?> = _importPreview.asStateFlow()
    private val _syncStatus = MutableStateFlow(SyncStatus.SYNCED)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    val reminderSettings: StateFlow<ReminderSettings> = reminderManager.settings

    val meters: StateFlow<List<MeterWithReadings>> = repository.meters.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    init {
        viewModelScope.launch {
            val existingUser = authSession.user.value
            if (existingUser == null) {
                _authState.value = AuthUiState(initialized = true)
            } else {
                _authState.value = AuthUiState(user = existingUser, loading = true)
                runCatching { repository.restoreSession() }
                    .onSuccess {
                        _authState.value = AuthUiState(
                            user = existingUser,
                            initialized = true,
                        )
                    }
                    .onFailure {
                        _authState.value = AuthUiState(
                            user = existingUser,
                            initialized = true,
                            error = it.message ?: "Could not refresh cloud data.",
                        )
                    }
            }
        }
    }

    fun login(username: String, password: String) = authenticate {
        repository.login(username, password)
    }

    fun register(username: String, password: String) = authenticate {
        repository.register(username, password)
    }

    fun logout() = viewModelScope.launch {
        repository.logout()
        _authState.value = AuthUiState(initialized = true)
        _syncStatus.value = SyncStatus.SYNCED
    }

    fun syncNow() = viewModelScope.launch {
        syncOperation(
            action = repository::syncNow,
            failureMessage = "Cloud sync failed.",
            showSuccessMessage = true,
        )
    }

    fun clearAuthError() {
        _authState.value = _authState.value.copy(error = null)
    }

    fun addMeter(
        nickname: String,
        type: MeterType,
        meterNumber: String,
        consumerNumber: String?,
        freeUnits: Double?,
    ) = viewModelScope.launch {
        syncOperation {
            repository.addMeter(nickname, type, meterNumber, consumerNumber, freeUnits)
        }
    }

    fun deleteMeter(meter: MeterEntity) = viewModelScope.launch {
        syncOperation { repository.deleteMeter(meter) }
    }

    fun addReading(meter: MeterEntity, value: Double, date: Long, isBilled: Boolean) =
        viewModelScope.launch {
            syncOperation { repository.addReading(meter, value, date, isBilled) }
        }

    fun deleteReading(reading: ReadingEntity) = viewModelScope.launch {
        syncOperation { repository.deleteReading(reading) }
    }

    fun updateReading(
        meter: MeterEntity,
        reading: ReadingEntity,
        value: Double,
        date: Long,
        isBilled: Boolean,
    ) = viewModelScope.launch {
        syncOperation {
            repository.updateReading(meter, reading, value, date, isBilled)
        }
    }

    fun resetFreeUnits(meter: MeterEntity, billedReading: ReadingEntity) =
        viewModelScope.launch {
            syncOperation { repository.resetFreeUnits(meter, billedReading) }
        }

    fun exportData(uri: Uri) = viewModelScope.launch {
        runCatching { repository.exportData(uri) }
            .onSuccess { _messages.emit("MeterX data exported.") }
            .onFailure { _messages.emit(it.message ?: "Export failed.") }
    }

    fun previewImport(uri: Uri) = viewModelScope.launch {
        runCatching { repository.previewImport(uri) }
            .onSuccess { _importPreview.value = it }
            .onFailure { _messages.emit(it.message ?: "Import file is invalid.") }
    }

    fun dismissImport() {
        _importPreview.value = null
    }

    fun confirmImport() = viewModelScope.launch {
        val preview = _importPreview.value ?: return@launch
        _syncStatus.value = SyncStatus.SYNCING
        runCatching { repository.importData(preview) }
            .onSuccess {
                _syncStatus.value = SyncStatus.SYNCED
                _importPreview.value = null
                _messages.emit("Imported ${preview.meterCount} meters and ${preview.readingCount} readings.")
            }
            .onFailure {
                _syncStatus.value = SyncStatus.ERROR
                _messages.emit(it.message ?: "Import failed.")
            }
    }

    fun setReminderEnabled(enabled: Boolean) {
        reminderManager.setEnabled(enabled)
        viewModelScope.launch {
            _messages.emit(
                if (enabled) "Daily reading reminder enabled."
                else "Daily reading reminder disabled.",
            )
        }
    }

    fun setReminderTime(hour: Int, minute: Int) {
        reminderManager.setTime(hour, minute)
        viewModelScope.launch { _messages.emit("Reminder time updated.") }
    }

    fun notificationPermissionDenied() = viewModelScope.launch {
        _messages.emit("Notification permission is required for daily reminders.")
    }

    private fun authenticate(action: suspend () -> AuthUser) = viewModelScope.launch {
        _authState.value = _authState.value.copy(loading = true, error = null)
        runCatching { action() }
            .onSuccess { user ->
                _syncStatus.value = SyncStatus.SYNCED
                _authState.value = AuthUiState(user = user, initialized = true)
            }
            .onFailure { error ->
                _authState.value = AuthUiState(
                    initialized = true,
                    error = error.message ?: "Authentication failed.",
                )
            }
    }

    private suspend fun syncOperation(
        failureMessage: String = "Saved locally, but cloud sync failed.",
        showSuccessMessage: Boolean = false,
        action: suspend () -> Unit,
    ) {
        _syncStatus.value = SyncStatus.SYNCING
        runCatching { action() }
            .onSuccess {
                _syncStatus.value = SyncStatus.SYNCED
                if (showSuccessMessage) {
                    _messages.emit("Cloud data is up to date.")
                }
            }
            .onFailure {
                _syncStatus.value = SyncStatus.ERROR
                _messages.emit(it.message ?: failureMessage)
            }
    }

    class Factory(
        private val repository: MeterRepository,
        private val reminderManager: ReminderManager,
        private val authSession: AuthSession,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MeterViewModel(repository, reminderManager, authSession) as T
    }
}
