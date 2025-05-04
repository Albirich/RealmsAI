package com.example.RealmsAI

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

object SessionManager {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun getOrCreateSessionFor(
        sourceId: String,
        type: String, // "character" or "chat"
        onResult: (sessionId: String) -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        val user = auth.currentUser ?: return onError(Exception("User not logged in"))
        val sessionRef = db.collection("users")
            .document(user.uid)
            .collection("sessions")

        // Step 1: Try to find existing session
        sessionRef
            .whereEqualTo("sourceId", sourceId)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    val sessionId = result.documents[0].id
                    onResult(sessionId)
                } else {
                    // Step 2: Create new session
                    val newSessionId = UUID.randomUUID().toString()
                    val sessionData = hashMapOf(
                        "sourceId" to sourceId,
                        "type" to type,
                        "createdAt" to System.currentTimeMillis()
                    )
                    sessionRef.document(newSessionId)
                        .set(sessionData)
                        .addOnSuccessListener {
                            onResult(newSessionId)
                        }
                        .addOnFailureListener { e ->
                            onError(e)
                        }
                }
            }
            .addOnFailureListener { e ->
                onError(e)
            }
    }
}
