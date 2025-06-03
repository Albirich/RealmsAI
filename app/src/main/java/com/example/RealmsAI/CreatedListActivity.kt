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
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.ChatProfile
import com.example.RealmsAI.models.CharacterProfile
import com.example.RealmsAI.models.PersonaProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject         // ← This brings in the correct .toObject extension
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ktx.firestore
import com.google.gson.Gson
import org.json.JSONObject


class CreatedListActivity : BaseActivity() {

    private val db = Firebase.firestore
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_created_list)
        setupBottomNav()

        val spinner = findViewById<Spinner>(R.id.filterSpinner).apply {
            adapter = ArrayAdapter.createFromResource(
                this@CreatedListActivity,
                R.array.filter_options,
                android.R.layout.simple_spinner_item
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        }

        val chatsRv      = findViewById<RecyclerView>(R.id.recyclerChats)
        val charactersRv = findViewById<RecyclerView>(R.id.recyclerCharacters)
        val personasRv = findViewById<RecyclerView>(R.id.recyclerPersonas)

        // initially show both or just chats
        chatsRv.visibility      = View.VISIBLE
        charactersRv.visibility = View.VISIBLE

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                when (parent.getItemAtPosition(position) as String) {
            //       "All" -> {
            //            chatsRv.visibility = View.VISIBLE
            //            charactersRv.visibility = View.VISIBLE
            //            personasRv.visibility = View.VISIBLE
            //        }

                    "Chats" -> {
                        chatsRv.visibility = View.VISIBLE
                        charactersRv.visibility = View.GONE
                        personasRv.visibility = View.GONE
                    }
                    "Characters" -> {
                        chatsRv.visibility = View.GONE
                        charactersRv.visibility = View.VISIBLE
                        personasRv.visibility = View.GONE
                    }
                    "Personas" -> {
                        chatsRv.visibility = View.GONE
                        charactersRv.visibility = View.GONE
                        personasRv.visibility = View.VISIBLE
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
        showPersonas(personasRv)
    }
    private fun lookupAvatar(charId: String): String? {
        // load from the same shared‐prefs or wherever you persisted your CharacterProfile
        val prefs = getSharedPreferences("characters", Context.MODE_PRIVATE)
        val json  = prefs.getString(charId, null) ?: return null
        return JSONObject(json).optString("avatarUri", null)
    }

    private fun showChats(rv: RecyclerView) {
        rv.layoutManager = GridLayoutManager(this, 2)
        val adapter = ChatPreviewAdapter(
            context = this,
            chatList = emptyList(),
            onClick = { /* ... */ },
            onLongClick = { preview ->
                AlertDialog.Builder(this)
                    .setTitle(preview.title)
                    .setItems(arrayOf("Edit", "Delete")) { _, which ->
                        when (which) {
                            0 -> {
                                // Load full profile from Firestore before editing!
                                FirebaseFirestore.getInstance()
                                    .collection("chats")
                                    .document(preview.id)
                                    .get()
                                    .addOnSuccessListener { snapshot ->
                                        val fullProfile = snapshot.toObject(ChatProfile::class.java)
                                        if (fullProfile != null) {
                                            startActivity(
                                                Intent(this, ChatCreationActivity::class.java)
                                                    .putExtra("CHAT_EDIT_ID", preview.id)
                                                    .putExtra("CHAT_PROFILE_JSON", Gson().toJson(fullProfile))
                                            )
                                        } else {
                                            Toast.makeText(this, "Chat profile not found.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .addOnFailureListener {
                                        Toast.makeText(this, "Failed: ${it.message}", Toast.LENGTH_SHORT).show()
                                    }
                            }
                            1 -> { /* ...delete logic... */ }
                        }
                    }
                    .show()
            }
        )
        rv.adapter = adapter

        // Load chat previews as before...
        FirebaseFirestore.getInstance()
            .collection("chats")
            .whereEqualTo("author", FirebaseAuth.getInstance().uid)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                val previews = snap.documents.mapNotNull { doc ->
                    doc.toObject(ChatProfile::class.java)?.let { p ->
                        ChatPreview(
                            id = p.id,
                            rawJson = Gson().toJson(p),
                            title = p.title,
                            description = p.description,
                            // ...etc
                            avatar1Uri = p.characterIds.getOrNull(0)?.let(::lookupAvatar),
                            avatar2Uri = p.characterIds.getOrNull(1)?.let(::lookupAvatar),
                            avatar1ResId = R.drawable.icon_01,
                            avatar2ResId = R.drawable.icon_01,
                            rating = p.rating,
                            mode = p.mode
                        )
                    }
                }
                adapter.updateList(previews)
            }
            .addOnFailureListener { e ->
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
                        avatarResId = cp.avatarResId ?: R.drawable.icon_01,
                        author      = cp.author,
                        rawJson   = Gson().toJson(cp)
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
                        // THIS is where the dialog should go!
                        AlertDialog.Builder(this)
                            .setTitle(preview.name)
                            .setItems(arrayOf("Edit", "Delete")) { _, which ->
                                when (which) {
                                    0 -> { // Edit (same as above!)
                                        FirebaseFirestore.getInstance().collection("characters")
                                            .document(preview.id)
                                            .get()
                                            .addOnSuccessListener { snapshot ->
                                                val fullProfile = snapshot.toObject(CharacterProfile::class.java)
                                                if (fullProfile != null) {
                                                    startActivity(
                                                        Intent(this, CharacterCreationActivity::class.java)
                                                            .putExtra("CHAR_EDIT_ID", preview.id)
                                                            .putExtra("CHAR_PROFILE_JSON", Gson().toJson(fullProfile))
                                                    )
                                                } else {
                                                    Toast.makeText(
                                                        this,
                                                        "Profile not found.",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                            .addOnFailureListener {
                                                Toast.makeText(
                                                    this,
                                                    "Failed to load profile: ${it.message}",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                    }

                                    1 -> { // Delete
                                        AlertDialog.Builder(this)
                                            .setTitle("Delete?")
                                            .setMessage("Are you sure you want to delete '${preview.name}'?")
                                            .setPositiveButton("Yes") { _, _ ->
                                                // Remove from Firestore
                                                FirebaseFirestore.getInstance()
                                                    .collection("characters")
                                                    .document(preview.id)
                                                    .delete()
                                                    .addOnSuccessListener {
                                                        Toast.makeText(
                                                            this,
                                                            "Deleted.",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        // Optionally refresh the list
                                                    }
                                                    .addOnFailureListener { e ->
                                                        Toast.makeText(
                                                            this,
                                                            "Failed: ${e.message}",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                            }
                                            .setNegativeButton("No", null)
                                            .show()
                                    }
                                }
                            }
                            .show()
                    }
                )
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Could not load characters: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showPersonas(rv: RecyclerView) {
        rv.layoutManager = GridLayoutManager(this, 2)

        if (currentUserId == null) return

        db.collection("personas")
            .whereEqualTo("author", currentUserId)
            .get()
            .addOnSuccessListener { snap ->
                val previews = snap.documents.mapNotNull { doc ->
                    val pp = doc.toObject<PersonaProfile>() ?: return@mapNotNull null
                    PersonaPreview(
                        id = pp.id,
                        name = pp.name,
                        description = pp.physicaldescription,
                        avatarUri = pp.avatarUri,
                        avatarResId = R.drawable.icon_01,
                        author = currentUserId // Or pp.author if present
                    )
                }

                rv.adapter = PersonaPreviewAdapter(
                    this,
                    previews,
                    onClick = { preview ->
                        // Load the *full* persona profile from Firestore before editing
                        FirebaseFirestore.getInstance()
                            .collection("personas")
                            .document(preview.id)
                            .get()
                            .addOnSuccessListener { snapshot ->
                                val fullProfile = snapshot.toObject(PersonaProfile::class.java)
                                if (fullProfile != null) {
                                    startActivity(
                                        Intent(this, PersonaCreationActivity::class.java)
                                            .putExtra("PERSONA_EDIT_ID", preview.id)
                                            .putExtra("PERSONA_PROFILE_JSON", Gson().toJson(fullProfile))
                                    )
                                } else {
                                    Toast.makeText(this, "Persona profile not found.", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Failed: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                    },
                    onLongClick = { preview ->
                        AlertDialog.Builder(this)
                            .setTitle(preview.name)
                            .setItems(arrayOf("Edit", "Delete")) { _, which ->
                                when (which) {
                                    0 -> { // Edit
                                        FirebaseFirestore.getInstance().collection("personas")
                                            .document(preview.id)
                                            .get()
                                            .addOnSuccessListener { snapshot ->
                                                val fullProfile = snapshot.toObject(PersonaProfile::class.java)
                                                if (fullProfile != null) {
                                                    startActivity(
                                                        Intent(this, PersonaCreationActivity::class.java)
                                                            .putExtra("PERSONA_EDIT_ID", preview.id)
                                                            .putExtra("PERSONA_PROFILE_JSON", Gson().toJson(fullProfile))
                                                    )
                                                } else {
                                                    Toast.makeText(
                                                        this,
                                                        "Profile not found.",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                            .addOnFailureListener {
                                                Toast.makeText(
                                                    this,
                                                    "Failed to load profile: ${it.message}",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                    }
                                    1 -> { // Delete
                                        AlertDialog.Builder(this)
                                            .setTitle("Delete?")
                                            .setMessage("Are you sure you want to delete '${preview.name}'?")
                                            .setPositiveButton("Yes") { _, _ ->
                                                FirebaseFirestore.getInstance()
                                                    .collection("personas")
                                                    .document(preview.id)
                                                    .delete()
                                                    .addOnSuccessListener {
                                                        Toast.makeText(this, "Deleted.", Toast.LENGTH_SHORT).show()
                                                    }
                                                    .addOnFailureListener { e ->
                                                        Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                            }
                                            .setNegativeButton("No", null)
                                            .show()
                                    }
                                }
                            }
                            .show()
                    }
                )
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Could not load personas: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
