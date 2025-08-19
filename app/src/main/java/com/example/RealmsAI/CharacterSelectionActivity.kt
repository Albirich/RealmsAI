package com.example.RealmsAI

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.RealmsAI.models.CharacterProfile
import com.example.RealmsAI.models.PersonaProfile
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CharacterSelectionActivity : AppCompatActivity() {

    private val selectedIds = mutableSetOf<String>()
    private var selectionCap = 20
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    // UI from activity_add_character.xml
    private lateinit var globalBtn: Button
    private lateinit var personalBtn: Button
    private lateinit var personasBtn: Button
    private lateinit var recycler: RecyclerView
    private lateinit var doneButton: MaterialButton

    private var isTempMode = false
    private var fromSource = ""

    // current list for adapter + index updates
    private var currentChars: List<CharacterProfile> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Reuse the tabbed layout
        setContentView(R.layout.activity_add_character)

        // Handle extras
        val preSelected = intent.getStringArrayListExtra("preSelectedIds") ?: emptyList()
        val initialCount = intent.getIntExtra("INITIAL_COUNT", 0)
        isTempMode = intent.getBooleanExtra("TEMP_SELECTION_MODE", false)
        fromSource = intent.getStringExtra("from") ?: ""
        selectionCap = 20 - initialCount

        selectedIds.addAll(preSelected)

        // Bind views
        globalBtn   = findViewById(R.id.globalChars)
        personalBtn = findViewById(R.id.personalChars)
        personasBtn = findViewById(R.id.personas)
        recycler    = findViewById(R.id.personaRecycler)
        doneButton  = findViewById(R.id.doneButton)

        recycler.layoutManager = GridLayoutManager(this, 2)

        // Collections flow only needs characters, so hide Personas tab there
        if (fromSource == "collections") {
            personasBtn.isEnabled = false
            personasBtn.alpha = 0.4f
        }

        // Default tab = Global Characters (public)
        loadGlobalCharacters()

        globalBtn.setOnClickListener { loadGlobalCharacters() }
        personalBtn.setOnClickListener { loadPersonalCharacters() }
        personasBtn.setOnClickListener {
            if (fromSource == "collections") return@setOnClickListener
            // You can show personas for other entry points, but they won't be selectable here
            // (collection expects characterIds). Easiest: just show nothing or a toast.
            // If you WANT personas selectable elsewhere, you’d need a personas-enabled adapter.
            // For now we’ll just no-op or toast.
            // Toast.makeText(this, "Personas not selectable here.", Toast.LENGTH_SHORT).show()
            loadPersonasPreview() // optional: preview only; not selectable
        }

        updateDoneCounter()

        doneButton.setOnClickListener {
            val selectedList = ArrayList(selectedIds)
            Log.d("CharacterSelection", "Result intent: mode=${intent.getStringExtra("mode")}, collectionId=${intent.getStringExtra("collectionId")}, collectionName=${intent.getStringExtra("collectionName")}, selected=$selectedList")

            val result = Intent().apply {
                putStringArrayListExtra("selectedCharacterIds", selectedList)

                if (!isTempMode) {
                    when (intent.getStringExtra("mode")) {
                        "edit" -> putExtra("collectionId", intent.getStringExtra("collectionId"))
                        "create" -> putExtra("collectionName", intent.getStringExtra("collectionName"))
                    }
                }
            }
            setResult(RESULT_OK, result)
            finish()
        }
    }

    // ---------- Loaders ----------

    private fun loadGlobalCharacters() {
        val db = FirebaseFirestore.getInstance()
        db.collection("characters")
            .whereEqualTo("private", false)
            .get()
            .addOnSuccessListener { snap ->
                currentChars = snap.documents.mapNotNull { doc ->
                    doc.toObject(CharacterProfile::class.java)?.copy(id = doc.id)
                }
                bindCharacters(currentChars)
            }
    }

    private fun loadPersonalCharacters() {
        val db = FirebaseFirestore.getInstance()
        db.collection("characters")
            .whereEqualTo("author", currentUserId)
            .get()
            .addOnSuccessListener { snap ->
                currentChars = snap.documents.mapNotNull { doc ->
                    doc.toObject(CharacterProfile::class.java)?.copy(id = doc.id)
                }
                bindCharacters(currentChars)
            }
    }

    // Optional: preview personas (disabled selection)
    private fun loadPersonasPreview() {
        val db = FirebaseFirestore.getInstance()
        db.collection("personas")
            .whereEqualTo("author", currentUserId)
            .get()
            .addOnSuccessListener { snap ->
                // Convert to fake CharacterProfile previews (non-selectable)
                val previews = snap.documents.mapNotNull { doc ->
                    doc.toObject(PersonaProfile::class.java)?.let { p ->
                        CharacterProfile(
                            id = "persona:${doc.id}",
                            name = p.name ?: "Persona",
                            avatarUri = p.avatarUri,
                            author = p.author,
                            private = false
                        )
                    }
                }
                currentChars = previews
                bindCharacters(currentChars, selectable = false)
            }
    }

    // ---------- Adapter binding ----------

    private fun bindCharacters(chars: List<CharacterProfile>, selectable: Boolean = true) {
        val adapter = CharacterSelectAdapter(
            characters = chars,
            selectedIds = selectedIds,
            onToggle = { charId ->
                if (!selectable) return@CharacterSelectAdapter
                if (selectedIds.contains(charId)) {
                    selectedIds.remove(charId)
                } else if (selectedIds.size < selectionCap) {
                    selectedIds.add(charId)
                }
                updateDoneCounter()
                val idx = chars.indexOfFirst { it.id == charId }
                if (idx >= 0) recycler.adapter?.notifyItemChanged(idx)
            },
            loadAvatar = { imageView, avatarUri ->
                if (!avatarUri.isNullOrEmpty()) {
                    Glide.with(imageView.context)
                        .load(avatarUri)
                        .placeholder(R.drawable.placeholder_avatar)
                        .error(R.drawable.placeholder_avatar)
                        .into(imageView)
                } else {
                    imageView.setImageResource(R.drawable.placeholder_avatar)
                }
            }
        )
        recycler.adapter = adapter
        updateDoneCounter()
    }

    // ---------- UI helpers ----------

    private fun updateDoneCounter() {
        doneButton.text = "Done — Selected: ${selectedIds.size} / $selectionCap"
        doneButton.isEnabled = selectedIds.isNotEmpty()
    }
}
