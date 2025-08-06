package com.example.RealmsAI

import SessionPreview
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentReference
import com.google.gson.Gson
import com.example.RealmsAI.models.*
import com.google.firebase.firestore.Query

class SessionHubActivity : BaseActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val currentUserId: String? get() = FirebaseAuth.getInstance().currentUser?.uid
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

        Log.d("SessionHub", "Querying for userId: $userId")
        db.collection("sessions")
            .whereArrayContains("userList", userId)
            .orderBy("startedAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                Log.d("SessionHub", "Sessions found: ${snap.documents.size}")
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
                    onClick = { preview -> fetchSessionAndLaunchLanding(preview) },
                    onLongClick = { preview ->
                        AlertDialog.Builder(this)
                            .setTitle(preview.title)
                            .setMessage("Leave or delete this session?\n\nIf you are the last player, the session will be deleted for everyone.")
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
        val sessionRef = db.collection("sessions").document(preview.id)
        sessionRef.get().addOnSuccessListener { docSnap ->
            if (!docSnap.exists()) {
                Toast.makeText(this, "Session not found.", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }
            val data = docSnap.data ?: return@addOnSuccessListener

            val userAssignments = (data["userAssignments"] as? Map<String, String>) ?: emptyMap()
            val userList = (data["userList"] as? List<String>)?.toMutableList() ?: mutableListOf()

            // Remove user from all structures
            val updatedUserAssignments = userAssignments.filterValues { it != userId }
            userList.remove(userId)

            // TODO: If you want, also update/remove user from userMap and any other structures.

            if (userList.isEmpty()) {
                // Last user: delete subcollections, then session doc
                deleteAllDocsInSubcollection(sessionRef, "messages") {
                    deleteAllDocsInSubcollection(sessionRef, "events") {
                        sessionRef.delete()
                            .addOnSuccessListener {
                                Toast.makeText(this, "Session deleted (last player left).", Toast.LENGTH_SHORT).show()
                                finish(); startActivity(intent)
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Failed to delete session: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
            } else {
                sessionRef.update(
                    mapOf(
                        "userAssignments" to updatedUserAssignments,
                        "userList" to userList
                    )
                )
                    .addOnSuccessListener {
                        Toast.makeText(this, "You have left the session.", Toast.LENGTH_SHORT).show()
                        finish(); startActivity(intent)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to leave session: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }.addOnFailureListener { e ->
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
                    Log.d("SessionHub", "Raw session data: ${gson.toJson(data)}")
                    val profile = parseSessionProfile(docSnap.id, data)
                    Log.d("SessionHub", "Parsed SessionProfile: $profile")
                    val intent = Intent(this, SessionLandingActivity::class.java)
                    intent.putExtra("SESSION_PROFILE_JSON", gson.toJson(profile))
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

    private fun parseSessionProfile(docId: String, data: Map<String, Any>): SessionProfile {
        val rawAvatarMap = data["currentAvatarMap"] as? Map<String, Map<String, Any>>
        val currentAvatarMap: Map<String, AvatarMapEntry> = rawAvatarMap?.mapValues { entry ->
            val mapVal = entry.value
            val first = mapVal["first"] as? String ?: ""
            val second = mapVal["second"] as? String?
            AvatarMapEntry(first, second)
        } ?: emptyMap()
        return SessionProfile(
            sessionId = data["sessionId"] as? String ?: docId,
            chatId = data["chatId"] as? String ?: "",
            title = data["title"] as? String ?: "(Untitled Session)",
            sessionDescription = data["sessionDescription"] as? String ?: "",
            chatMode = data["chatMode"] as? String ?: "SANDBOX",
            startedAt = data["startedAt"] as? Timestamp,
            sfwOnly = data["sfwOnly"] as? Boolean ?: true,
            sessionSummary = data["sessionSummary"] as? String ?: "",
            userMap = parseUserMap(data["userMap"]),
            userList = (data["userList"] as? List<String>) ?: emptyList(),
            userAssignments = data["userAssignments"] as? Map<String, String> ?: emptyMap(),
            slotRoster = parseSlotRoster(data["slotRoster"]),
            areas = parseAreas(data["areas"]),
            history = parseChatMessages(data["history"]),
            currentAreaId = data["currentAreaId"] as? String
            // ...add other fields as needed!
        )
    }

    private fun parseUserMap(raw: Any?): Map<String, SessionUser> {
        return (raw as? Map<*, *>)?.mapNotNull { (userId, userData) ->
            val key = userId as? String
            val user = parseSessionUser(userData)
            if (key != null && user != null) key to user else null
        }?.toMap() ?: emptyMap()
    }


    private fun parseSessionUser(raw: Any?): SessionUser? {
        val map = raw as? Map<*, *> ?: return null
        return SessionUser(
            userId = map["userId"] as? String ?: "",
            username = map["username"] as? String ?: "",
            personaIds = map["personaIds"] as? List<String> ?: emptyList(),
            activeSlotId = map["activeSlotId"] as? String, // update field if you renamed!
            bubbleColor = map["bubbleColor"] as? String ?: "#CCCCCC",
            textColor = map["textColor"] as? String ?: "#000000"
        )
    }

    private fun parseSlotRoster(raw: Any?): List<SlotProfile> {
        return (raw as? List<*>)?.mapNotNull { item ->
            parseSlotProfile(item)
        } ?: emptyList()
    }
    private fun parseTaggedMemories(raw: Any?): List<TaggedMemory> {
        return (raw as? List<*>)?.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            TaggedMemory(
                id = map["id"] as? String ?: "",
                tags = (map["tags"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                text = map["text"] as? String ?: "",
                messageIds = (map["messageIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                nsfw = map["nsfw"] as? Boolean ?: false
            )
        } ?: emptyList()
    }


    private fun parseSlotProfile(raw: Any?): SlotProfile? {
        val map = raw as? Map<*, *> ?: return null
        return SlotProfile(
            slotId = map["slotId"] as? String ?: "",
            baseCharacterId = map["baseCharacterId"] as? String ?: "",
            name = map["name"] as? String ?: "",
            summary = map["summary"] as? String ?: "",
            personality = map["personality"] as? String ?: "",
            memories = parseTaggedMemories(map["memories"]),
            privateDescription = map["privateDescription"] as? String ?: "",
            greeting = map["greeting"] as? String ?: "",
            avatarUri = map["avatarUri"] as? String,
            outfits = parseOutfits(map["outfits"]),
            currentOutfit = map["currentOutfit"] as? String ?: "",
            height = map["height"] as? String ?: "",
            weight = map["weight"] as? String ?: "",
            age = (map["age"] as? Number)?.toInt() ?: 0,
            eyeColor = map["eyeColor"] as? String ?: "",
            hairColor = map["hairColor"] as? String ?: "",
            gender = map["gender"] as? String ?: "",
            physicalDescription = map["physicalDescription"] as? String ?: "",
            relationships = parseRelationships(map["relationships"]),
            bubbleColor = map["bubbleColor"] as? String ?: "#FFFFFF",
            textColor = map["textColor"] as? String ?: "#000000",
            sfwOnly = map["sfwOnly"] as? Boolean ?: true,
            profileType = map["profileType"] as? String ?: "bot",
            hiddenRoles = map["hiddenRoles"] as? String ?: "",
            statusEffects = ((map["statusEffects"] as? List<*>)?.mapNotNull { it as? String }?.toMutableSet()) ?: mutableSetOf(),
            lastActiveArea = map["lastActiveArea"] as? String,
            lastActiveLocation = map["lastActiveLocation"] as? String,
            lastSynced = null, // Parse if needed
        )
    }

    private fun parseRelationships(raw: Any?): List<Relationship> {
        return (raw as? List<*>)?.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            Relationship(
                fromId = map["fromId"] as? String ?: "",
                toName = map["toName"] as? String ?: "",
                type = map["type"] as? String ?: "",
                description = map["description"] as? String ?: ""
            )
        } ?: emptyList()
    }


    private fun parseAreas(raw: Any?): List<Area> {
        return (raw as? List<*>)?.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            Area(
                id = map["id"] as? String ?: "",
                name = map["name"] as? String ?: "",
                locations = parseLocations(map["locations"]).toMutableList(),
                creatorId = map["creatorId"] as? String ?: ""
            )
        } ?: emptyList()
    }


    private fun parseLocations(raw: Any?): List<LocationSlot> {
        return (raw as? List<*>)?.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            LocationSlot(
                id = map["id"] as? String ?: "",
                name = map["name"] as? String ?: "",
                uri = map["uri"] as? String ?: "",
                characters = ((map["characters"] as? List<String>) ?: emptyList()).toMutableList()
            )
        } ?: emptyList()
    }

    private fun parseChatMessages(raw: Any?): List<ChatMessage> {
        return (raw as? List<*>)?.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val poseMap = (map["pose"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                val key = k as? String ?: return@mapNotNull null
                val poseName = v as? String ?: return@mapNotNull null
                key to poseName
            }?.toMap()
            val imageUpdatesMap = (map["imageUpdates"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                val key = (k as? String)?.toIntOrNull() ?: return@mapNotNull null
                key to (v as? String)
            }?.toMap()

            ChatMessage(
                id = map["id"] as? String ?: "",
                senderId = map["senderId"] as? String ?: "",
                text = map["text"] as? String ?: "",
                pose = poseMap,
                delay = (map["delay"] as? Number)?.toInt() ?: 0,
                timestamp = map["timestamp"] as? com.google.firebase.Timestamp
                    ?: com.google.firebase.Timestamp.now(),
                imageUpdates = imageUpdatesMap,
                visibility = map["visibility"] as? Boolean ?: true
            )
        } ?: emptyList()
    }



    private fun parseOutfits(raw: Any?): List<Outfit> {
        return (raw as? List<*>)?.mapNotNull { outfitRaw ->
            val map = outfitRaw as? Map<*, *> ?: return@mapNotNull null
            Outfit(
                name = map["name"] as? String ?: "",
                poseSlots = parsePoseSlots(map["poseSlots"]).toMutableList()
            )
        } ?: emptyList()
    }

    private fun parsePoseSlots(raw: Any?): List<PoseSlot> {
        return (raw as? List<*>)?.mapNotNull { poseRaw ->
            val map = poseRaw as? Map<*, *> ?: return@mapNotNull null
            PoseSlot(
                name = map["name"] as? String ?: "",
                uri = map["uri"] as? String
            )
        } ?: emptyList()
    }


}
