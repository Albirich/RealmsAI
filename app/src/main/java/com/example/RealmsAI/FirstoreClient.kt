package com.example.RealmsAI

import com.google.firebase.firestore.FirebaseFirestore

object FirestoreClient {
    val db: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }
}
