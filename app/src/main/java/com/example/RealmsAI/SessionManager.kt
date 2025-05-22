package com.example.RealmsAI

import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.ChatMessage
import com.example.RealmsAI.models.SessionProfile
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import org.json.JSONObject
import java.util.UUID

object SessionManager {
    private val db = FirebaseFirestore.getInstance()

    /**
     * Finds or creates a session under /chats/{chatId}/sessions.
     */
    fun createSession(
        sessionProfile: SessionProfile,
        chatProfileJson: String,
        userId: String,
        characterProfilesJson: String,
        characterId: String,
        onResult: (sessionId: String) -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        // Parse chatId from chatProfileJson if needed
        val chatId = try {
            JSONObject(chatProfileJson).optString("id", sessionProfile.chatId)
        } catch (e: Exception) {
            sessionProfile.chatId
        }

        // Prepare session data to write
        val sessionData = hashMapOf(
            "chatId" to chatId,
            "participants" to sessionProfile.participants,
            "characterId" to characterId,
            "sessionId" to sessionProfile.sessionId,
            "startedAt" to com.google.firebase.Timestamp.now(),
            "sfwOnly" to sessionProfile.sfwOnly,
            "title" to sessionProfile.title,
            "backgroundUri" to sessionProfile.backgroundUri,
            "chatMode" to sessionProfile.chatMode,
            "slotRoster" to sessionProfile.slotRoster, // you might want to serialize this properly
            "personaProfiles" to sessionProfile.personaProfiles, // same here
            // Add any other needed sessionProfile fields here
            "characterProfilesJson" to characterProfilesJson
        )

        val sessionsRef = db.collection("chats").document(chatId).collection("sessions")
        val newSessionRef = sessionsRef.document() // auto-generate session ID

        newSessionRef.set(sessionData)
            .addOnSuccessListener { onResult(newSessionRef.id) }
            .addOnFailureListener(onError)
    }



    /**
     * Loads chat history from Firestore and patches missing IDs.
     */
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

    fun findSessionForUser(
        chatId: String,
        userId: String,
        onResult: (sessionId: String?) -> Unit,
        onError: (Exception) -> Unit = {}
    )
    {
        db.collection("chats")
            .document(chatId)
            .collection("sessions")
            .whereArrayContains("participants", userId)
            .orderBy("startedAt", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                val sessionId = snap.documents.firstOrNull()?.id
                onResult(sessionId)
            }
            .addOnFailureListener { e -> onResult(null) }
    }



    /**
     * Listen to new messages in real time.
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
     * Send a ChatMessage to Firestore.
     */
    fun sendMessage(chatId: String, sessionId: String, chatMessage: ChatMessage) {
        val msgMap = mapOf(
            "id" to chatMessage.id,
            "sender" to chatMessage.sender,
            "messageText" to chatMessage.messageText,
            "timestamp" to chatMessage.timestamp
        )
        db.collection("chats")
            .document(chatId)
            .collection("sessions")
            .document(sessionId)
            .collection("messages")
            .add(msgMap)
            .addOnSuccessListener { docRef ->
                Log.d("SessionManager", "Message sent: ${docRef.id}")
            }
            .addOnFailureListener { e ->
                Log.e("SessionManager", "Failed to send message", e)
            }
    }


}
