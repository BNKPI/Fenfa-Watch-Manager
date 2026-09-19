package com.bantje.fenfawatchmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.bantje.fenfawatchmanager.ui.HomeScreen
import com.bantje.fenfawatchmanager.ui.MainViewModel
import com.bantje.fenfawatchmanager.ui.theme.FenfaWatchManagerTheme
import com.bantje.fenfawatchmanager.update.SelfUpdateManager

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var selfUpdateManager: SelfUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        selfUpdateManager = SelfUpdateManager(this)

        setContent {
            FenfaWatchManagerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeScreen(viewModel = viewModel)
                }
            }
        }

        // Beim Start einmal die Verbindung testen, Fenfa nach Watch-Apps und Self-Updates abfragen
        viewModel.testWatchConnection()
        viewModel.checkAllApps()
        viewModel.checkSelfUpdate()
    }

    override fun onResume() {
        super.onResume()
        selfUpdateManager.resumePendingWork()
    }
}
