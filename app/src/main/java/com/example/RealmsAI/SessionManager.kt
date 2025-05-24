package com.example.RealmsAI

import android.util.Log
import com.example.RealmsAI.models.ChatMessage
import com.example.RealmsAI.models.SessionProfile
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import org.json.JSONObject

object SessionManager {
    private val db = FirebaseFirestore.getInstance()

    /**
     * Creates a session under /chats/{chatId}/sessions and ensures 'participants' is a list of UIDs
     * (including the current user). Calls onResult with the new sessionId on success.
     *
     * This does **not** start any Activities! Pass your navigation code in the onResult callback.
     */
    fun createSession(
        sessionProfile: SessionProfile,
        chatProfileJson: String,
        userId: String,
        characterProfilesJson: String,
        characterId: String,
        extraParticipants: List<String> = emptyList(), // Optional, future-proof!
        onResult: (sessionId: String) -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        val chatId = try {
            JSONObject(chatProfileJson).optString("id", sessionProfile.chatId)
        } catch (e: Exception) {
            sessionProfile.chatId
        }

        // Combine: SessionProfile participants, current user, and any extras from UI
        val participants = (
                (sessionProfile.participants ?: emptyList()) +
                        userId +
                        extraParticipants
                ).filterNotNull().toSet()

        val sessionData = hashMapOf(
            "chatId" to chatId,
            "participants" to participants.toList(),
            "characterId" to characterId,
            "sessionId" to sessionProfile.sessionId,
            "startedAt" to com.google.firebase.Timestamp.now(),
            "sfwOnly" to sessionProfile.sfwOnly,
            "title" to sessionProfile.title,
            "backgroundUri" to sessionProfile.backgroundUri,
            "chatMode" to sessionProfile.chatMode,
            "slotRoster" to sessionProfile.slotRoster,
            "personaProfiles" to sessionProfile.personaProfiles,
            "characterProfilesJson" to characterProfilesJson
        )

        val sessionsRef = db.collection("chats").document(chatId).collection("sessions")
        val newSessionRef = sessionsRef.document() // auto-generate session ID

        newSessionRef.set(sessionData)
            .addOnSuccessListener { onResult(newSessionRef.id) }
            .addOnFailureListener(onError)
    }


    /**
     * Loads chat history (all ChatMessages) for the given chat/session.
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

    /**
     * Finds the most recent session for this chatId/userId combo.
     * Calls onResult with the sessionId or null if not found.
     */
    fun findSessionForUser(
        chatId: String,
        userId: String,
        onResult: (sessionId: String?) -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
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
            .addOnFailureListener { e ->
                onError(e)
            }
    }

    /**
     * Listen for new ChatMessages in real time for a session.
     * Passes each new ChatMessage to onNew as they're added.
     * Returns the ListenerRegistration so you can stop listening.
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
     * Sends a single ChatMessage to Firestore for the given session.
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
