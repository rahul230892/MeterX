package com.meterx.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.meterx.app.ui.MeterXApp
import com.meterx.app.ui.MeterXTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MeterViewModel by viewModels {
        val app = application as MeterXApplication
        MeterViewModel.Factory(app.repository, app.reminderManager, app.authSession)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MeterXTheme {
                MeterXApp(viewModel)
            }
        }
    }
}
