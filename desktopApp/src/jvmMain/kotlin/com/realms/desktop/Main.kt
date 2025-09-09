package com.realms.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

@Composable
fun App() {
    MaterialTheme {
        Surface {
            Column(Modifier.fillMaxSize().padding(24.dp)) {
                Text("RealmsAI Desktop bootstrap", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text("If you see this, the desktop module is wired.")
            }
        }
    }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "RealmsAI") { App() }
}
