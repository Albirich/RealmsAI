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

        db.collectionGroup("sessions")
            .whereArrayContains("participants", userId)
            .orderBy("startedAt", Query.Direction.DESCENDING)
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
                        // Fetch the full session doc to get ALL fields and pass to MainActivity!
                        fetchSessionAndLaunchMain(preview)
                    },
                    onLongClick = { preview ->
                        AlertDialog.Builder(this)
                            .setTitle(preview.title)
                            .setMessage("Delete this session?\n\nThis action cannot be undone.")
                            .setPositiveButton("Delete") { _, _ ->
                                FirebaseFirestore.getInstance()
                                    .collection("chats").document(preview.chatId)
                                    .collection("sessions").document(preview.id)
                                    .delete()
                                    .addOnSuccessListener {
                                        Toast.makeText(this, "Session deleted.", Toast.LENGTH_SHORT).show()
                                        // Optionally: reload/refresh the session list
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
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

    private fun fetchSessionAndLaunchMain(preview: SessionPreview) {
        // Get the full session document from Firestore
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
                    // Launch MainActivity with full profile and also provide the raw JSON as backup
                    val intent = Intent(this, MainActivity::class.java)
                    intent.putExtra("SESSION_PROFILE_JSON", gson.toJson(profile))
                    intent.putExtra("SESSION_ID", preview.id)
                    intent.putExtra("CHAT_ID", preview.chatId)
                    intent.putExtra("SESSION_JSON", gson.toJson(data)) // fallback
                    intent.putExtra("ENTRY_MODE", "LOAD")
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
                    poses = map["poses"] as? Map<String, List<String>> ?: emptyMap(),
                    relationships = emptyList() // You can parse this too if you have the structure
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
                    images = map["images"] as? List<String> ?: emptyList(),
                    name = map["name"] as? String ?: "",
                    profileType = map["profileType"] as? String ?: "",
                    relationships = emptyList(), // Parse if needed
                    weight = map["weight"] as? String ?: ""
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
            personaProfiles = parsePersonaProfiles(data["personaProfiles"])
        )

    }

}
