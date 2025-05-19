package com.example.RealmsAI

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.ChatProfile
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.gson.Gson

class ChatHubActivity : BaseActivity() {
    private lateinit var sortSpinner: Spinner
    private lateinit var adapter: ChatPreviewAdapter

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_chat_hub)
            setupBottomNav()

            // 1) RecyclerView + adapter
            val chatsRv = findViewById<RecyclerView>(R.id.chatHubRecyclerView).apply {
                layoutManager = GridLayoutManager(this@ChatHubActivity, 2)
            }
            // 2) Initialize the CLASS‐LEVEL adapter (not a local val)
            adapter = ChatPreviewAdapter(
                context  = this,
                chatList = emptyList(),
                onClick  = { preview ->
                    startActivity(Intent(this, SessionLandingActivity::class.java).apply {
                        putExtra("CHAT_ID", preview.id)
                        putExtra("CHAT_PROFILE_JSON", preview.rawJson)
                    })
                }
            )
            // 3) Attach it to YOUR chatsRv
            chatsRv.adapter = adapter

            // 4) Spinner setup
            sortSpinner = findViewById(R.id.sortSpinner)
            val options = listOf("Latest", "Hot")
            sortSpinner.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                options
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

            sortSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                    // Call your showChats with chatsRv, not 'rv'
                    val field = if (options[pos] == "Latest") "timestamp" else "rating"
                    showChats(chatsRv, orderBy = field)
                }
            }


            // 3) Kick off initial load as “Latest”
            showChats(chatsRv, orderBy = "timestamp")
        }

        /**
         * Fetches & displays your chats sorted by the given field.
         */
        private fun showChats(
            chatsRv: RecyclerView,
            orderBy: String = "timestamp"
        ) {
            FirebaseFirestore.getInstance()
                .collection("chats")
                .whereNotEqualTo("mode", "ONE_ON_ONE")
                .orderBy(orderBy, Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener { snap ->
                    val previews = snap.documents.mapNotNull { doc ->
                        // 1) Deserialize + inject your ID
                        val profile = doc.toObject(ChatProfile::class.java)
                            ?.copy(id = doc.id)
                            ?: return@mapNotNull null

                        val ts = doc.getTimestamp("timestamp") ?: Timestamp.now()

                        // 3) Build ChatPreview (placeholders for avatars)
                        ChatPreview(
                            id           = profile.id,
                            title        = profile.title,
                            description  = profile.description,
                            avatar1ResId = R.drawable.placeholder_character,
                            avatar2ResId = R.drawable.placeholder_character,
                            avatar1Uri   = null,
                            avatar2Uri   = null,
                            rating       = profile.rating,
                            timestamp    = profile.timestamp,
                            mode         = profile.mode,
                            author       = profile.author,
                            tags         = profile.tags,
                            sfwOnly      = profile.sfwOnly,
                            chatProfile  = profile,
                            rawJson      = Gson().toJson(profile)
                        )
                    }

                    adapter.updateList(previews)
                }
                .addOnFailureListener { e ->
                    Toast.makeText(
                        this,
                        "Failed to load chats: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }
