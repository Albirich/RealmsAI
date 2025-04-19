package com.example.emotichat.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    // Define a data class to hold style settings for a message.
data class MessageStyle(
    val backgroundColor: Int,
    val textColor: Int
)

// Function to retrieve style settings based on the sender's ID.
fun getSenderStyle(sender: String): MessageStyle {
    return when (sender) {
        "User" -> MessageStyle(
            backgroundColor = 0xFF0000FF.toInt(),  // Blue
            textColor = 0xFFFFFFFF.toInt()           // White
        )
        "Bot 1" -> MessageStyle(
            backgroundColor = 0xFFFFA500.toInt(),   // Orange
            textColor = 0xFF008000.toInt()           // Green
        )
        "Bot 2" -> MessageStyle(
            backgroundColor = 0xFF800080.toInt(),   // Purple
            textColor = 0xFFFFC0CB.toInt()           // Pink
        )
        else -> MessageStyle(
            backgroundColor = 0xFFCCCCCC.toInt(),   // Light gray as default background
            textColor = 0xFF000000.toInt()           // Black as default text color
        )
    }
}

    */
)