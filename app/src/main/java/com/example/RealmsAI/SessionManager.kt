package com.example.RealmsAI

import android.util.Log
import com.example.RealmsAI.models.ChatMessage
import com.example.RealmsAI.models.SessionProfile
import com.google.firebase.Timestamp
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
        // Combine: SessionProfile participants, current user, and any extras from UI
        val participants = (
                (sessionProfile.participants ?: emptyList()) +
                        userId +
                        extraParticipants
                ).filterNotNull().toSet()

        // Generate a unique sessionId if not provided
        val sessionId = if (sessionProfile.sessionId.isNullOrBlank()) {
            db.collection("sessions").document().id
        } else {
            sessionProfile.sessionId
        }

        val sessionData = hashMapOf(
            "sessionId" to sessionId,
            "participants" to participants.toList(),
            "characterId" to characterId,
            "chatId" to try {
                JSONObject(chatProfileJson).optString("id", sessionProfile.chatId)
            } catch (e: Exception) {
                sessionProfile.chatId
            },
            "startedAt" to com.google.firebase.Timestamp.now(),
            "sfwOnly" to sessionProfile.sfwOnly,
            "title" to sessionProfile.title,
            "backgroundUri" to sessionProfile.backgroundUri,
            "chatMode" to sessionProfile.chatMode,
            "slotRoster" to sessionProfile.slotRoster,
            "personaProfiles" to sessionProfile.personaProfiles,
            "characterProfilesJson" to characterProfilesJson
        )

        // Sessions are now top-level documents
        val sessionRef = db.collection("sessions").document(sessionId)

        sessionRef.set(sessionData)
            .addOnSuccessListener {
                Log.d("SessionManager", "Successfully saved session: $sessionId")
                onResult(sessionId)
            }
            .addOnFailureListener { e ->
                Log.e("SessionManager", "Failed to save session with sessionId=$sessionId", e)
                onError(e)
            }
    }


    /**
     * Loads chat history (all ChatMessages) for the given chat/session.
     */
        fun loadHistory(
            sessionId: String,
            onResult: (List<ChatMessage>) -> Unit,
            onError: (Exception) -> Unit = {}
        ) {
            Log.d("SessionManager", "Calling loadHistory for session $sessionId")

            db.collection("sessions").document(sessionId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener { snap ->
                    Log.d("SessionManager", "Messages fetched: ${snap.documents.size}")
                    val all = snap.documents
                        .mapNotNull { it.toObject(ChatMessage::class.java) }
                    onResult(all)
                }
                .addOnFailureListener {
                    Log.e("SessionManager", "Failed to load messages", it)
                    onError(it)
                }
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
        db.collection("sessions")
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
        sessionId: String,
        onNew: (ChatMessage) -> Unit
    ): ListenerRegistration {
        val collRef = db.collection("sessions").document(sessionId)
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
            "timestamp" to (chatMessage.timestamp ?: Timestamp.now())
        )
        db.collection("sessions")
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
