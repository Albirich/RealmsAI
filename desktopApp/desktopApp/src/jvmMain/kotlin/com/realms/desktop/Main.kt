package com.realms.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.realms.desktop.theme.RealmsAITheme

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "RealmsAI") {
        RealmsAITheme {
            Surface {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        "RealmsAI (Desktop bootstrap)",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("If you see your palette and fonts, the theme is live.")
                    Spacer(Modifier.height(24.dp))
                    Text("Next: add :shared KMP module and port screens.")
                }
            }
        }
    }
}