package com.meterx.app

import android.app.Application
import com.meterx.app.BuildConfig
import com.meterx.app.data.AuthSession
import com.meterx.app.data.LocalBackup
import com.meterx.app.data.MeterApi
import com.meterx.app.data.MeterDatabase
import com.meterx.app.data.MeterRepository
import com.meterx.app.reminder.ReminderManager

class MeterXApplication : Application() {
    val authSession: AuthSession by lazy { AuthSession(this) }

    val repository: MeterRepository by lazy {
        val database = MeterDatabase.getInstance(this)
        MeterRepository(
            database = database,
            backup = LocalBackup(this),
            api = MeterApi(BuildConfig.API_BASE_URL),
            session = authSession,
        )
    }

    val reminderManager: ReminderManager by lazy {
        ReminderManager(this)
    }

    override fun onCreate() {
        super.onCreate()
        reminderManager.createNotificationChannel()
        reminderManager.restoreSchedule()
    }
}
