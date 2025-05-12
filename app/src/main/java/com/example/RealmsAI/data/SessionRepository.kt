package com.example.RealmsAI.data

import com.example.RealmsAI.FirestoreClient
import com.example.RealmsAI.FirestoreClient.db
import com.example.RealmsAI.models.Message
import com.example.RealmsAI.models.Session
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlin.collections.set

class SessionRepository {

    private val sessions = FirestoreClient.db.collection("sessions")

    /** 1) Create a new Session, return its auto-ID */
    suspend fun createSession(chatId: String): String {
        val docRef = sessions.document()
        val session = Session(
            sessionId = docRef.id,
            chatId = chatId
        )
        docRef.set(session).await()    // use kotlinx-coroutines-play-services
        return docRef.id
    }

    /** 2) Post one message under this session */
    suspend fun sendMessage(
        chatId: String,
        sessionId: String,
        msg: Message
    ) {
        db.collection("chats")
            .document(chatId)
            .collection("sessions")
            .document(sessionId)
            .collection("messages")
            .document(msg.id.ifBlank { db.collection("chats").document().id })
            .set(msg)
            .await()
    }

    /** 3) Listen for real-time updates to messages */
    fun listenMessages(
        sessionId: String,
        onNew: (Message) -> Unit
    ): ListenerRegistration {
        return sessions
            .document(sessionId)
            .collection("messages")
            .orderBy("createdAt")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                for (dc in snapshot.documentChanges) {
                    if (dc.type == DocumentChange.Type.ADDED) {
                        val msg = dc.document.toObject(Message::class.java)
                        onNew(msg)
                    }
                }
            }
    }
}