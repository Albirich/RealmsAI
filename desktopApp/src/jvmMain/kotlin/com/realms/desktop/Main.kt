package com.realms.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "RealmsAI",
        state = rememberWindowState(width = 1200.dp, height = 800.dp)
    ) {
        RealmsDesktopApp()
    }
}

@Composable
fun RealmsDesktopApp() {
    // TODO: swap this palette to match your phone app colors
    val scheme = darkColorScheme(
        primary = Color(0xFF6750A4),
        secondary = Color(0xFF625B71)
    )

    MaterialTheme(colorScheme = scheme) {
        Surface(tonalElevation = 2.dp) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text("RealmsAI Desktop", style = MaterialTheme.typography.headlineMedium)
                Text("Pipeline smoke test build. Replace this with your real screens next.")
                Button(onClick = {}) { Text("It builds!") }
            }
        }
    }
}
