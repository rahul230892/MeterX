package com.meterx.app.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.meterx.app.MainActivity
import com.meterx.app.R
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ReminderSettings(
    val enabled: Boolean = false,
    val hour: Int = 8,
    val minute: Int = 0,
)

class ReminderManager(private val context: Context) {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<ReminderSettings> = _settings.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        updateSettings(_settings.value.copy(enabled = enabled))
    }

    fun setTime(hour: Int, minute: Int) {
        require(hour in 0..23 && minute in 0..59)
        updateSettings(_settings.value.copy(hour = hour, minute = minute))
    }

    fun restoreSchedule() {
        if (_settings.value.enabled) scheduleNext(_settings.value)
    }

    fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Reading reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Daily reminders to update meter readings"
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun updateSettings(settings: ReminderSettings) {
        preferences.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putInt(KEY_HOUR, settings.hour)
            .putInt(KEY_MINUTE, settings.minute)
            .apply()
        _settings.value = settings
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
        if (settings.enabled) {
            scheduleNext(settings)
        }
    }

    private fun scheduleNext(settings: ReminderSettings) {
        val now = ZonedDateTime.now()
        var next = now.withHour(settings.hour).withMinute(settings.minute)
            .withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMillis()
        val request = OneTimeWorkRequestBuilder<ReadingReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "$WORK_NAME-${next.toEpochSecond()}",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private fun readSettings() = ReminderSettings(
        enabled = preferences.getBoolean(KEY_ENABLED, false),
        hour = preferences.getInt(KEY_HOUR, 8),
        minute = preferences.getInt(KEY_MINUTE, 0),
    )

    companion object {
        const val CHANNEL_ID = "reading_reminders"
        const val NOTIFICATION_ID = 1001
        private const val WORK_NAME = "daily_reading_reminder"
        private const val WORK_TAG = "daily_reading_reminder_tag"
        private const val PREFERENCES_NAME = "meterx_reminders"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_HOUR = "hour"
        private const val KEY_MINUTE = "minute"
    }
}

class ReadingReminderWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : Worker(appContext, workerParameters) {
    override fun doWork(): Result {
        val manager = ReminderManager(applicationContext)
        val settings = manager.settings.value
        if (!settings.enabled) return Result.success()

        manager.createNotificationChannel()
        val openApp = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, ReminderManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Time to update your readings")
            .setContentText("Add today's electricity, water, or gas meter readings.")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(applicationContext)
                .notify(ReminderManager.NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Notification permission can be revoked after the reminder is scheduled.
        }
        manager.restoreSchedule()
        return Result.success()
    }
}
