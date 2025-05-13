package com.example.RealmsAI

import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.CharacterProfile
import com.example.RealmsAI.models.ChatProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.gson.Gson

class ChatHubActivity : BaseActivity() {

        // 1) keep a map of all your characters by their ID
        private val charProfilesById = mutableMapOf<String, CharacterProfile>()
        private lateinit var adapter: ChatPreviewAdapter

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_chat_hub)

            // 2) First – load all your characters from Firestore into that map
            val me = FirebaseAuth.getInstance().currentUser?.uid ?: return
            FirebaseFirestore.getInstance()
                .collection("characters")
                .whereEqualTo("author", me)
                .get()
                .addOnSuccessListener { snap ->
                    snap.documents.forEach { doc ->
                        doc.toObject(CharacterProfile::class.java)
                            ?.let { charProfilesById[it.id] = it }
                    }

                    // 3) Now that you have everyone in memory, load your chats
                    loadChatsFromFirestore { previews ->
                        adapter.updateList(previews)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "failed loading characters", e)
                }
        }

        private fun loadChatsFromFirestore(onLoaded: (List<ChatPreview>) -> Unit) {
            val me = FirebaseAuth.getInstance().currentUser?.uid ?: return
            FirebaseFirestore.getInstance()
                .collection("chats")
                .whereEqualTo("author", me)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener { snap ->
                    val list = snap.documents.mapNotNull { doc ->
                        val profile = doc.toObject(ChatProfile::class.java) ?: return@mapNotNull null

                        // pull the raw Firestore timestamp out safely:
                        val ts = doc.get("timestamp").let {
                            when (it) {
                                is com.google.firebase.Timestamp -> it.toDate().time
                                is Long                         -> it
                                else                            -> System.currentTimeMillis()
                            }
                        }

                        // look up each B-slot’s CharacterProfile
                        val ids = profile.characterIds
                        val cp1 = charProfilesById[ids.getOrNull(0)]
                        val cp2 = charProfilesById[ids.getOrNull(1)]
                        val uri1 = cp1?.avatarUri
                        val res1 = cp1?.avatarResId ?: R.drawable.placeholder_character
                        val uri2 = cp2?.avatarUri
                        val res2 = cp2?.avatarResId ?: R.drawable.placeholder_character

                        // serialize the full ChatProfile back to JSON for rawJson
                        val rawJson = Gson().toJson(profile)

                        ChatPreview(
                            id           = profile.id,
                            title        = profile.title,
                            description  = profile.description,
                            avatar1Uri   = uri1,
                            avatar1ResId = res1,
                            avatar2Uri   = uri2,
                            avatar2ResId = res2,
                            rating       = profile.rating,
                            timestamp    = ts,
                            mode         = profile.mode,
                            author       = profile.author,
                            chatProfile  = profile,
                            rawJson      = rawJson
                        )
                    }
                    onLoaded(list)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "fetch failed", e)
                    onLoaded(emptyList())
                }
        }
    }
