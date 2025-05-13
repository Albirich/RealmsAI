package com.example.RealmsAI

import com.example.RealmsAI.models.ChatMessage
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

object SessionManager {
    private val db = FirebaseFirestore.getInstance()
    private val _skippedLocal = mutableSetOf<String>()

    /**
     * Finds or creates a session under /chats/{chatId}/sessions.
     */
    fun getOrCreateSessionFor(
        chatId: String,
        onResult: (sessionId: String) -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        val sessionsRef = db
            .collection("chats")
            .document(chatId)
            .collection("sessions")      // ← nested under /chats/{chatId}

        sessionsRef
            .orderBy("startedAt", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                if (!snap.isEmpty) {
                    onResult(snap.documents.first().id)
                } else {
                    // create a new session doc under that same nested path
                    val newDoc = sessionsRef.document()
                    newDoc
                        .set(mapOf(
                            "startedAt"     to FieldValue.serverTimestamp(),
                            "lastMessage"   to null,
                            "lastTimestamp" to FieldValue.serverTimestamp()
                        ))
                        .addOnSuccessListener { onResult(newDoc.id) }
                        .addOnFailureListener(onError)
                }
            }
            .addOnFailureListener(onError)
    }


    /**
     * Starts streaming messages from /chats/{chatId}/sessions/{sessionId}/messages.
     */
    fun listenMessages(
        chatId: String,
        sessionId: String,
        onNew: (ChatMessage) -> Unit
    ): ListenerRegistration {
        val collRef = db.collection("chats").document(chatId)
            .collection("sessions").document(sessionId)
            .collection("messages")
        return collRef
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (snap == null || err != null) return@addSnapshotListener
                snap.documentChanges
                    .filter { it.type == DocumentChange.Type.ADDED }
                    .forEach { dc ->
                        val msg = dc.document.toObject(ChatMessage::class.java)
                        onNew(msg)
                    }
            }
    }


    /**
     * Writes a ChatMessage to Firestore under the proper nested path.
     */
    fun sendMessage(
        chatId: String,
        sessionId: String,
        msg: ChatMessage
    ) {
        db
            .collection("chats").document(chatId)
            .collection("sessions").document(sessionId)
            .collection("messages")
            .add(mapOf(
                "sender"    to msg.sender,
                "messageText"      to msg.messageText,
                "timestamp" to FieldValue.serverTimestamp()
            ))
    }

    // In SessionManager.kt
    fun loadHistory(
        chatId: String,
        sessionId: String,
        onResult: (List<ChatMessage>) -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        db.collection("chats").document(chatId)
            .collection("sessions").document(sessionId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { snap ->
                val all = snap.documents
                    .mapNotNull { it.toObject(ChatMessage::class.java) }
                onResult(all)
            }
            .addOnFailureListener(onError)
    }

}
