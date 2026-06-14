package com.meterx.app

import android.app.Application
import com.meterx.app.data.LocalBackup
import com.meterx.app.data.MeterDatabase
import com.meterx.app.data.MeterRepository

class MeterXApplication : Application() {
    val repository: MeterRepository by lazy {
        val database = MeterDatabase.getInstance(this)
        MeterRepository(database, LocalBackup(this))
    }
}
