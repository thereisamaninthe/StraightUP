package com.example.straightup

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.straightup.ui.theme.StraightUPTheme
import com.example.straightup.ui.DashboardUI
import com.example.straightup.ui.ReminderUI
import com.example.straightup.reminders.UserAction
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allPermissionsGranted = permissions.values.all { it }
        if (allPermissionsGranted) {
            // Permissions granted, can start monitoring
        } else {
            // Handle permission denial
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request necessary permissions
        requestPermissions()

        setContent {
            StraightUPTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    StraightUPApp()
                }
            }
        }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.VIBRATE
        )

        val permissionsToRequest = permissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}

@Composable
fun StraightUPApp() {
    val viewModel: PostureViewModel = viewModel()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Observe view model state
    val postureScore by viewModel.postureScore.collectAsState()
    val userBehavior by viewModel.userBehavior.collectAsState()
    val sessionStats by viewModel.sessionStats.collectAsState()
    val isMonitoring by viewModel.isMonitoring.collectAsState()
    val activeReminder by viewModel.activeReminder.collectAsState()

    // Initialize view model with context
    LaunchedEffect(context) {
        viewModel.initialize(context)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        DashboardUI(
            postureScore = postureScore,
            userBehavior = userBehavior,
            sessionStats = sessionStats,
            isMonitoring = isMonitoring,
            onToggleMonitoring = {
                scope.launch {
                    if (isMonitoring) {
                        viewModel.stopMonitoring()
                    } else {
                        viewModel.startMonitoring()
                    }
                }
            },
            onShowSettings = {
                // TODO: Navigate to settings screen
            },
            modifier = Modifier.padding(innerPadding)
        )

        // Overlay reminder UI
        ReminderUI(
            reminderEvent = activeReminder,
            onDismiss = {
                viewModel.dismissReminder()
            },
            onUserAction = { action ->
                viewModel.handleUserAction(action)
            }
        )
    }
}