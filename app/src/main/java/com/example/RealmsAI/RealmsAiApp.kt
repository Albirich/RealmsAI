package com.example.RealmsAI

import android.app.Application
import com.google.firebase.FirebaseApp

class RealmsAiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // This kicks off Firebase SDK initialization once for your entire app
        FirebaseApp.initializeApp(this)
    }
}
