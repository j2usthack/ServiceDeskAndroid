package com.example.servicedeskapk

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.example.servicedeskapk.push.PushConstants
import com.example.servicedeskapk.ui.navigation.AppNavigation
import com.example.servicedeskapk.ui.navigation.Screen
import com.example.servicedeskapk.ui.theme.ServiceDeskTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as ServiceDeskApp
        val prefs = app.preferencesManager
        handleNotificationIntent(intent, app, prefs.isLoggedIn)

        val startDestination = when {
            !prefs.isLoggedIn -> Screen.Login.route
            prefs.mustChangePassword -> Screen.ChangePassword.createRoute(forced = true)
            else -> Screen.Main.route
        }

        setContent {
            val navController = rememberNavController()
            val snackbarHostState = remember { SnackbarHostState() }

            LaunchedEffect(Unit) {
                app.foregroundPushEvents.collect { payload ->
                    val text = listOfNotNull(payload.title, payload.body)
                        .filter { it.isNotBlank() }
                        .joinToString("\n")
                        .ifBlank { "Новое уведомление" }
                    val result = snackbarHostState.showSnackbar(
                        message = text,
                        duration = SnackbarDuration.Long,
                        withDismissAction = true,
                        actionLabel = payload.ticketId?.let { "Открыть" }
                    )
                    if (result == SnackbarResult.ActionPerformed && payload.ticketId != null) {
                        navController.navigate(Screen.TicketDetail.createRoute(payload.ticketId!!)) {
                            launchSingleTop = true
                        }
                    }
                }
            }

            LaunchedEffect(navController) {
                app.navigateToTicketRequests.collect { ticketId ->
                    navController.navigate(Screen.TicketDetail.createRoute(ticketId)) {
                        launchSingleTop = true
                    }
                }
            }

            ServiceDeskTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = {
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                ) { paddingValues ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            AppNavigation(
                                navController = navController,
                                startDestination = startDestination
                            )
                        }
                    }
                }
            }
        }

        val ticketFromIntent = readTicketIdFromIntent(intent)
        if (ticketFromIntent != null && prefs.isLoggedIn && !prefs.mustChangePassword) {
            lifecycleScope.launch {
                delay(250)
                app.navigateToTicketRequests.emit(ticketFromIntent)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val app = application as ServiceDeskApp
        val prefs = app.preferencesManager
        handleNotificationIntent(intent, app, prefs.isLoggedIn)
        readTicketIdFromIntent(intent)?.let { id ->
            if (prefs.isLoggedIn && !prefs.mustChangePassword) {
                lifecycleScope.launch {
                    app.navigateToTicketRequests.emit(id)
                }
            }
        }
    }

    private fun handleNotificationIntent(intent: Intent?, app: ServiceDeskApp, isLoggedIn: Boolean) {
        val id = readTicketIdFromIntent(intent) ?: return
        if (!isLoggedIn) {
            app.deferredDeepLinkTicketId = id
        }
    }

    private fun readTicketIdFromIntent(intent: Intent?): Int? {
        if (intent == null) return null
        val id = intent.getIntExtra(PushConstants.EXTRA_TICKET_ID, -1)
        return id.takeIf { it > 0 }
    }
}
