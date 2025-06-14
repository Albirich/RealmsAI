package com.example.RealmsAI

import SessionPreview
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.SessionProfile
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.gson.Gson
import com.example.RealmsAI.models.SlotInfo
import com.example.RealmsAI.models.PersonaProfile
import com.google.firebase.firestore.DocumentReference

class SessionHubActivity : BaseActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val currentUserId: String?
        get() = FirebaseAuth.getInstance().currentUser?.uid
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_hub)
        setupBottomNav()

        val rv = findViewById<RecyclerView>(R.id.sessionRecycler)
        rv.layoutManager = LinearLayoutManager(this)

        showSessions(rv)
    }

    private fun showSessions(rv: RecyclerView) {
        val userId = currentUserId
        if (userId == null) {
            Toast.makeText(this, "You must be logged in", Toast.LENGTH_SHORT).show()
            return
        }

        // Find all sessions where user is in userAssignments map
        db.collection("sessions")
            .whereArrayContains("userList", userId)
            .get()
            .addOnSuccessListener { snap ->
                val previews = snap.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    SessionPreview(
                        id = doc.id,
                        title = data["title"] as? String ?: "(Untitled Session)",
                        chatId = data["chatId"] as? String ?: "",
                        timestamp = (data["startedAt"] as? Timestamp)?.seconds ?: 0L,
                        rawJson = gson.toJson(data)
                    )
                }

                rv.adapter = SessionPreviewAdapter(
                    context = this,
                    sessionList = previews,
                    onClick = { preview ->
                        fetchSessionAndLaunchLanding(preview)
                    },
                    onLongClick = { preview ->
                        AlertDialog.Builder(this)
                            .setTitle(preview.title)
                            .setMessage("Leave or delete this session?\n\nIf you are the last player, the session will be deleted for everyone. Bots/characters are ignored.")
                            .setPositiveButton("Leave Session") { _, _ ->
                                leaveOrDeleteSession(preview)
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                )

            }
            .addOnFailureListener { e ->
                Log.e("SessionHub", "fetch failed", e)
                Toast.makeText(this, "Failed to load sessions: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun leaveOrDeleteSession(preview: SessionPreview) {
        val userId = currentUserId ?: return

        db.collection("sessions").document(preview.id)
            .get()
            .addOnSuccessListener { docSnap ->
                if (!docSnap.exists()) {
                    Toast.makeText(this, "Session not found.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                val data = docSnap.data ?: return@addOnSuccessListener

                val userAssignments = (data["userAssignments"] as? Map<String, String>) ?: emptyMap()
                val playerAssignments = (data["playerAssignments"] as? Map<String, String>) ?: emptyMap()
                // Remove user from any slot they occupy
                val updatedUserAssignments = userAssignments.filterValues { it != userId }
                val updatedPlayerAssignments = playerAssignments.filterKeys { key ->
                    // Only keep assignments for slots that still have users
                    updatedUserAssignments.containsKey(key)
                }

                val sessionRef = db.collection("sessions").document(preview.id)

                if (updatedUserAssignments.isEmpty()) {
                    // No users left, delete all subcollections and then the session
                    deleteAllDocsInSubcollection(sessionRef, "messages") {
                        deleteAllDocsInSubcollection(sessionRef, "events") {
                            sessionRef.delete()
                                .addOnSuccessListener {
                                    Toast.makeText(this, "Session deleted (last player left).", Toast.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Failed to delete session: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            finish()
                            startActivity(intent)
                        }
                    }
                } else {
                    // Just update userAssignments and playerAssignments
                    sessionRef.update(
                        mapOf(
                            "userAssignments" to updatedUserAssignments,
                            "playerAssignments" to updatedPlayerAssignments
                        )
                    )
                        .addOnSuccessListener {
                            Toast.makeText(this, "You have left the session.", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to leave session: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    finish()
                    startActivity(intent)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to fetch session: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteAllDocsInSubcollection(
        sessionRef: DocumentReference,
        subcollectionName: String,
        onComplete: () -> Unit = {}
    ) {
        sessionRef.collection(subcollectionName)
            .get()
            .addOnSuccessListener { snapshot ->
                val batch = sessionRef.firestore.batch()
                for (doc in snapshot.documents) {
                    batch.delete(doc.reference)
                }
                batch.commit()
                    .addOnSuccessListener { onComplete() }
                    .addOnFailureListener { onComplete() }
            }
            .addOnFailureListener { onComplete() }
    }

    private fun fetchSessionAndLaunchLanding(preview: SessionPreview) {
        db.collection("sessions").document(preview.id)
            .get()
            .addOnSuccessListener { docSnap ->
                if (!docSnap.exists()) {
                    Toast.makeText(this, "Session not found.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                val data = docSnap.data ?: emptyMap<String, Any>()
                try {
                    val profile = parseSessionProfile(docSnap.id, data)
                    val intent = Intent(this, SessionLandingActivity::class.java)
                    // You want to send the whole profile for editing
                    intent.putExtra("SESSION_PROFILE_JSON", gson.toJson(profile))
                    // Optionally, send extra raw info if you want to display it (not required)
                    intent.putExtra("SESSION_ID", preview.id)
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e("SessionHub", "Error parsing SessionProfile: $e")
                    Toast.makeText(this, "Failed to load session details.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error fetching session: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Parse slotRoster list
    private fun parseSlotRoster(raw: Any?): List<SlotInfo> {
        return (raw as? List<*>)?.mapNotNull { item ->
            (item as? Map<*, *>)?.let { map ->
                SlotInfo(
                    name = map["name"] as? String ?: "",
                    slot = map["slot"] as? String ?: "",
                    summary = map["summary"] as? String ?: "",
                    id = map["id"] as? String ?: "",
                    outfits = map["outfits"] as? List<String> ?: emptyList(),
                    poses = map["poses"] as? Map<String, String> ?: emptyMap(),
                    relationships = emptyList(),
                    sfwOnly = map["sfwOnly"] as? Boolean ?: true
                )
            }
        } ?: emptyList()
    }

    // Parse personaProfiles list
    private fun parsePersonaProfiles(raw: Any?): List<PersonaProfile> {
        return (raw as? List<*>)?.mapNotNull { item ->
            (item as? Map<*, *>)?.let { map ->
                PersonaProfile(
                    age = (map["age"] as? Number)?.toInt() ?: 0,
                    author = map["author"] as? String ?: "",
                    avatarUri = map["avatarUri"] as? String ?: "",
                    physicaldescription = map["description"] as? String ?: "",
                    eyes = map["eyes"] as? String ?: "",
                    gender = map["gender"] as? String ?: "",
                    hair = map["hair"] as? String ?: "",
                    height = map["height"] as? String ?: "",
                    id = map["id"] as? String ?: "",
                    name = map["name"] as? String ?: "",
                    profileType = map["profileType"] as? String ?: "",
                    relationships = emptyList(), // Parse if needed
                    weight = map["weight"] as? String ?: "",
                    bubbleColor = map["bubbleColor"] as? String ?: "#FFFFFF",
                    textColor = map["textColor"] as? String ?: "#000000",
                    outfits = (map["outfits"] as? List<Map<String, Any>>)?.mapNotNull { outfitMap ->
                        try {
                            com.example.RealmsAI.models.Outfit(
                                name = outfitMap["name"] as? String ?: "",
                                poseSlots = (outfitMap["poseSlots"] as? List<Map<String, Any>>)?.mapNotNull { poseMap ->
                                    try {
                                        com.example.RealmsAI.models.PoseSlot(
                                            name = poseMap["name"] as? String ?: "",
                                            uri  = poseMap["uri"] as? String
                                        )
                                    } catch (e: Exception) { null }
                                }?.toMutableList() ?: mutableListOf()
                            )
                        } catch (e: Exception) { null }
                    } ?: emptyList(),
                    currentOutfit = map["currentOutfit"] as? String ?: ""
                )

            }
        } ?: emptyList()
    }

    // Safe mapping for all fields (expand as needed for your actual fields/models)
    private fun parseSessionProfile(docId: String, data: Map<String, Any>): SessionProfile {
        return SessionProfile(
            sessionId = data["sessionId"] as? String ?: docId,
            chatId = data["chatId"] as? String ?: "",
            title = data["title"] as? String ?: "(Untitled Session)",
            sessionDescription = data["sessionDescription"] as? String ?: "",
            backgroundUri = data["backgroundUri"] as? String,
            chatMode = data["chatMode"] as? String ?: "SANDBOX",
            startedAt = (data["startedAt"] as? Timestamp)?.toDate()?.toString(),
            sfwOnly = data["sfwOnly"] as? Boolean ?: true,
            participants = data["participants"] as? List<String> ?: emptyList(),
            slotRoster = parseSlotRoster(data["slotRoster"]),
            personaProfiles = parsePersonaProfiles(data["personaProfiles"]),
            playerAssignments = data["playerAssignments"] as? Map<String, String> ?: emptyMap(),
            )

    }

}
