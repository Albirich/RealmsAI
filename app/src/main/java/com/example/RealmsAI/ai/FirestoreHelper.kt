package com.example.RealmsAI.ai

import android.util.Log
import com.example.RealmsAI.models.ChatMessage
import com.google.firebase.firestore.FirebaseFirestore

object FirestoreHelper {
    fun saveMessages(sessionId: String, messages: List<ChatMessage>) {
        // Example Firestore batch write logic
        val db = FirebaseFirestore.getInstance()
        val batch = db.batch()
        val sessionMessagesRef = db.collection("sessions").document(sessionId).collection("messages")

        messages.forEach { msg ->
            val docRef = sessionMessagesRef.document(msg.id)
            batch.set(docRef, msg)
        }

        batch.commit().addOnSuccessListener {
            Log.d("FirestoreHelper", "Saved ${messages.size} messages to session $sessionId")
        }.addOnFailureListener { e ->
            Log.e("FirestoreHelper", "Failed to save messages", e)
        }
    }
}
