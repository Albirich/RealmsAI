package com.example.RealmsAI

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.ChatProfile           // ← Make sure you import your data class here!
import com.example.RealmsAI.ChatPreview
import com.example.RealmsAI.ChatPreviewAdapter
import com.example.RealmsAI.models.CharacterProfile
import com.example.RealmsAI.models.ChatMode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject         // ← This brings in the correct .toObject extension
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ktx.firestore
import com.google.gson.Gson
import org.json.JSONObject

class CreatedListActivity : AppCompatActivity() {

    private val db = Firebase.firestore
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_created_list)

        val spinner = findViewById<Spinner>(R.id.filterSpinner).apply {
            adapter = ArrayAdapter.createFromResource(
                this@CreatedListActivity,
                R.array.filter_options,
                android.R.layout.simple_spinner_item
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        }

        val chatsRv      = findViewById<RecyclerView>(R.id.recyclerChats)
        val charactersRv = findViewById<RecyclerView>(R.id.recyclerCharacters)

        // initially show both or just chats
        chatsRv.visibility      = View.VISIBLE
        charactersRv.visibility = View.VISIBLE

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                when (parent.getItemAtPosition(position) as String) {
                    "All" -> {
                        chatsRv.visibility      = View.VISIBLE
                        charactersRv.visibility = View.VISIBLE
                    }
                    "Chats" -> {
                        chatsRv.visibility      = View.VISIBLE
                        charactersRv.visibility = View.GONE
                    }
                    "Characters" -> {
                        chatsRv.visibility      = View.GONE
                        charactersRv.visibility = View.VISIBLE
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {
                // no-op
            }
        }

        // …then kick off your Firestore loads into each RecyclerView…
        showChats(chatsRv)
        showCharacters(charactersRv)
    }
    private fun lookupAvatar(charId: String): String? {
        // load from the same shared‐prefs or wherever you persisted your CharacterProfile
        val prefs = getSharedPreferences("characters", Context.MODE_PRIVATE)
        val json  = prefs.getString(charId, null) ?: return null
        return JSONObject(json).optString("avatarUri", null)
    }

    private fun showChats(rv: RecyclerView) {
        // 1) Layout
        rv.layoutManager = GridLayoutManager(this, 2)

        // 2) Adapter
        val adapter = ChatPreviewAdapter(
            context = this,
            chatList = emptyList(),
            onClick = { preview ->
                startActivity(Intent(this, MainActivity::class.java).apply {
                    putExtra("CHAT_ID", preview.id)
                    putExtra("CHAT_PROFILE_JSON", preview.rawJson)
                })
            },
            onLongClick = { /* e.g. delete or share */ }
        )
        rv.adapter = adapter


        // 3) Fetch from Firestore
        FirebaseFirestore.getInstance()
            .collection("chats")
            .whereEqualTo("author", FirebaseAuth.getInstance().uid)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                Log.d("CreatedList", "🔥 raw chats.size() = ${snap.size()}")
                Toast.makeText(this, "Chats fetched: ${snap.size()}", Toast.LENGTH_LONG).show()

                // 4) Map to ChatPreview
                val previews = snap.documents.mapNotNull { doc ->
                    doc.toObject(ChatProfile::class.java)?.let { p ->
                        ChatPreview(
                            id = p.id,
                            rawJson = Gson().toJson(p),
                            title = p.title,
                            description = p.description,
                            avatar1Uri = p.characterIds.getOrNull(0)?.let(::lookupAvatar),
                            avatar2Uri = p.characterIds.getOrNull(1)?.let(::lookupAvatar),
                            avatar1ResId = R.drawable.placeholder_avatar,
                            avatar2ResId = R.drawable.placeholder_avatar,
                            rating = p.rating,
                            mode = p.mode
                        )
                    }
                }

                // 5) Push into adapter
                adapter.updateList(previews)

            }
            .addOnFailureListener { e ->
                Log.e("CreatedList", "fetch failed", e)
                Toast.makeText(this, "Fetch error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }


    private fun showCharacters(rv: RecyclerView) {
        rv.layoutManager = GridLayoutManager(this, 2)

        if (currentUserId == null) return

        db.collection("characters")
            .whereEqualTo("author", currentUserId)
            .get()
            .addOnSuccessListener { snap ->
                val previews = snap.documents.mapNotNull { doc ->
                    val cp = doc.toObject<CharacterProfile>() ?: return@mapNotNull null
                    CharacterPreview(
                        id          = cp.id,
                        name        = cp.name,
                        summary     = cp.summary.orEmpty(),
                        avatarUri   = cp.avatarUri,
                        avatarResId = cp.avatarResId ?: R.drawable.placeholder_avatar,
                        author      = cp.author
                    )
                }

                rv.adapter = CharacterPreviewAdapter(
                    this,
                    previews,
                    onClick = { preview ->
                        startActivity(
                            Intent(this, CharacterCreationActivity::class.java)
                                .putExtra("CHAR_EDIT_ID", preview.id)
                                .putExtra("CHAR_PROFILE_JSON", Gson().toJson(preview))
                        )
                    },
                    onLongClick = { preview ->
                        // …delete‐dialog…
                    }
                )
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Could not load characters: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
