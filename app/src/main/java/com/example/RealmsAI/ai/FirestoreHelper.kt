package com.example.RealmsAI.ai

import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log
import com.example.RealmsAI.models.ChatMessage
import java.util.UUID

object FirestoreHelper {
    fun saveMessages(sessionId: String, messages: List<ChatMessage>) {
        val db = FirebaseFirestore.getInstance()
        val batch = db.batch()
        val sessionMessagesRef = db.collection("sessions")
            .document(sessionId)
            .collection("messages")

        messages.forEach { msg ->
            // Use msg.id if not blank/null, else generate a new UUID
            val messageId = msg.id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
            val docRef = sessionMessagesRef.document(messageId)
            batch.set(docRef, msg)
        }

        batch.commit()
            .addOnSuccessListener {
                Log.d("FirestoreHelper", "Saved ${messages.size} messages to session $sessionId")
            }
            .addOnFailureListener { e ->
                Log.e("FirestoreHelper", "Failed to save messages", e)
            }
    }
}
