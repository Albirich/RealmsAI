package com.example.RealmsAI

import android.util.Log
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
        onNewList: (List<ChatMessage>) -> Unit
    ): ListenerRegistration {
        val msgsRef = db
            .collection("chats").document(chatId)
            .collection("sessions").document(sessionId)
            .collection("messages")

        Log.d("SessionManager", "➡️ Listening on path: ${msgsRef.path}")

        // Build ordered query
        val query = msgsRef.orderBy("timestamp", Query.Direction.ASCENDING)

        // Single snapshot listener that gives the full contents each time
        return query.addSnapshotListener { snap, err ->
            if (err != null || snap == null) {
                Log.e("SessionManager", "Listen failed on ${msgsRef.path}", err)
                return@addSnapshotListener
            }
            // Map docs → ChatMessage
            val allMsgs = snap.documents
                .mapNotNull { it.toObject(ChatMessage::class.java) }
            // Callback with the complete list
            onNewList(allMsgs)
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
}
