package com.albirich.RealmsAI

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.albirich.RealmsAI.models.CharacterProfile
import com.albirich.RealmsAI.models.PersonaProfile
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

    private lateinit var searchEditText: android.widget.EditText
    private lateinit var filterButton: android.widget.ImageButton

    private val availableTags = arrayOf(
        "Fantasy", "Sci-Fi", "Modern", "Male", "Female",
        "Non-Binary", "Monster", "Hero", "Villain", "OC",
        "Canon", "Tsundere", "Yandere", "Kuudere", "Dandere"
    )
    private val filterChecked = BooleanArray(availableTags.size)
    private val activeTagFilters = mutableSetOf<String>()

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
        val maxSelectFromCaller = intent.getIntExtra("MAX_SELECT", -1)
        selectionCap = if (maxSelectFromCaller > 0) maxSelectFromCaller else (20 - initialCount)

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

        searchEditText = findViewById(R.id.searchEditText)
        filterButton = findViewById(R.id.filterButton)

        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                applyFilters()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        filterButton.setOnClickListener {
            showFilterDialog()
        }

        // Default tab = Global Characters (public)
        loadGlobalCharacters()

        globalBtn.setOnClickListener { loadGlobalCharacters() }
        personalBtn.setOnClickListener { loadPersonalCharacters() }
        personasBtn.setOnClickListener {
            if (fromSource == "collections") return@setOnClickListener
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

    private fun showCharacterReviewDialog(character: CharacterProfile, onConfirm: () -> Unit) {
        // 1. Use standard Dialog instead of BottomSheetDialog
        val dialog = android.app.Dialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_character_review, null)
        dialog.setContentView(view)

        // 2. Make the window background transparent so your XML's rounded corners show up
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // 3. Set the width to wrap nicely on the screen (with a little margin)
        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)

        // Bind Views (Exact same as before)
        val avatarView = view.findViewById<ImageView>(R.id.reviewAvatar)
        val nameView = view.findViewById<TextView>(R.id.reviewName)
        val personalityView = view.findViewById<TextView>(R.id.reviewPersonality)
        val summaryView = view.findViewById<TextView>(R.id.reviewSummary)
        val revealButton = view.findViewById<Button>(R.id.btnRevealSecret)
        val secretContainer = view.findViewById<LinearLayout>(R.id.secretContainer)
        val secretView = view.findViewById<TextView>(R.id.reviewSecret)
        val btnCancel = view.findViewById<Button>(R.id.btnCancelReview)
        val btnAdd = view.findViewById<Button>(R.id.btnAddCharacter)

        // Populate Data (Exact same as before)
        Glide.with(this).load(character.avatarUri).placeholder(R.drawable.placeholder_avatar).into(avatarView)
        nameView.text = character.name
        personalityView.text = character.personality.takeIf { !it.isNullOrBlank() } ?: "None provided."
        val summaryTxt = character.summary.takeIf { !it.isNullOrBlank() } ?: ""
        val backgroundTxt = character.backstory.takeIf { !it.isNullOrBlank() } ?: "No background provided."

        summaryView.text = summaryTxt + "\n\n" + backgroundTxt
        // The Bouncer Logic
        if (character.privateDescription.isNullOrBlank()) {
            revealButton.visibility = View.GONE
            secretContainer.visibility = View.GONE
        } else {
            revealButton.visibility = View.VISIBLE
            secretView.text = character.privateDescription

            revealButton.setOnClickListener {
                revealButton.visibility = View.GONE
                secretContainer.visibility = View.VISIBLE
            }
        }

        // Handle Actions
        btnCancel.setOnClickListener {
            dialog.dismiss() // Changed from bottomSheetDialog
        }

        btnAdd.setOnClickListener {
            onConfirm()
            dialog.dismiss() // Changed from bottomSheetDialog
            finishWithResult()
        }

        dialog.show()
    }

    private fun showFilterDialog() {
        AlertDialog.Builder(this)
            .setTitle("Filter by Tags")
            .setMultiChoiceItems(availableTags, filterChecked) { _, which, isChecked ->
                filterChecked[which] = isChecked
            }
            .setPositiveButton("Apply") { _, _ ->
                activeTagFilters.clear()
                availableTags.forEachIndexed { index, tag ->
                    if (filterChecked[index]) activeTagFilters.add(tag)
                }
                applyFilters()

                if (activeTagFilters.isNotEmpty()) {
                    filterButton.setColorFilter(getColor(R.color.purple_500))
                } else {
                    filterButton.clearColorFilter()
                }
            }
            .setNeutralButton("Clear") { _, _ ->
                filterChecked.fill(false)
                activeTagFilters.clear()
                filterButton.clearColorFilter()
                applyFilters()
            }
            .show()
    }

    private fun applyFilters() {
        val query = searchEditText.text?.toString()?.trim()?.lowercase() ?: ""

        val filteredList = if (query.isEmpty() && activeTagFilters.isEmpty()) {
            currentChars
        } else {
            currentChars.filter { char ->
                val matchesText = if (query.isEmpty()) true else {
                    char.name.lowercase().contains(query) ||
                            (char.summary?.lowercase()?.contains(query) == true) ||
                            (char.personality?.lowercase()?.contains(query) == true) ||
                            (char.universe.lowercase().contains(query))
                }

                val matchesTags = if (activeTagFilters.isEmpty()) true else {
                    activeTagFilters.all { requiredTag ->
                        char.tags.any { charTag -> charTag.equals(requiredTag, ignoreCase = true) }
                    }
                }

                matchesText && matchesTags
            }
        }

        // Re-bind the adapter with the newly filtered list
        bindCharacters(filteredList, selectable = true)
    }

    // ---------- Loaders ----------

    private fun loadGlobalCharacters() {
        val db = FirebaseFirestore.getInstance()
        db.collection("characters")
            .whereEqualTo("private", false)
            // Sort by createdAt, newest first
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                currentChars = snap.documents.mapNotNull { doc ->
                    doc.toObject(CharacterProfile::class.java)?.copy(id = doc.id)
                }
                applyFilters()
            }
            .addOnFailureListener { e ->
                Log.e("CharacterSelection", "Failed to load global characters", e)
            }
    }

    private fun loadPersonalCharacters() {
        val db = FirebaseFirestore.getInstance()
        db.collection("characters")
            .whereEqualTo("author", currentUserId)
            // Sort by lastTimestamp, newest first
            .orderBy("lastTimestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                currentChars = snap.documents.mapNotNull { doc ->
                    doc.toObject(CharacterProfile::class.java)?.copy(id = doc.id)
                }
                applyFilters() // Changed to applyFilters so search/tags persist when switching tabs!
            }
            .addOnFailureListener { e ->
                Log.e("CharacterSelection", "Failed to load personal characters", e)
            }
    }

    private fun loadPersonasPreview() {
        val db = FirebaseFirestore.getInstance()
        db.collection("personas")
            .whereEqualTo("author", currentUserId)
            .get()
            .addOnSuccessListener { snap ->
                val personasAsChars = snap.documents.mapNotNull { doc ->
                    doc.toObject(PersonaProfile::class.java)?.let { p ->
                        CharacterProfile(
                            id = "persona:${doc.id}",      // <<<<<<<<<< IMPORTANT
                            name = p.name ?: "Persona",
                            avatarUri = p.avatarUri,
                            author = p.author,
                            private = false,
                            profileType = "player"
                        )
                    }
                }
                currentChars = personasAsChars
                applyFilters() // <<<< Renders the list instead of closing the screen!
            }
            .addOnFailureListener { e ->
                Log.e("CharacterSelection", "Failed to load personas", e)
            }
    }

    // ---------- Adapter binding ----------

    private fun bindCharacters(chars: List<CharacterProfile>, selectable: Boolean = true) {
        val adapter = CharacterSelectAdapter(
            characters = chars,
            selectedIds = selectedIds,
            onClick = { clickedCharacter ->

                // THE ROUTING LOGIC:
                // If they are only picking 1 character (meaning they are adding to a chat), show the Bouncer!
                if (selectionCap == 1) {
                    showCharacterReviewDialog(clickedCharacter) {
                        // This runs if they hit "Select Character" in the bottom sheet
                        if (selectedIds.contains(clickedCharacter.id)) {
                            selectedIds.remove(clickedCharacter.id)
                        } else {
                            selectedIds.add(clickedCharacter.id)
                        }
                        updateDoneCounter()
                        notifyChange(chars, clickedCharacter.id)
                    }
                } else {
                    // Otherwise, they are mass-selecting for a Collection. Just toggle instantly.
                    if (selectedIds.contains(clickedCharacter.id)) {
                        selectedIds.remove(clickedCharacter.id)
                    } else if (selectedIds.size < selectionCap) {
                        selectedIds.add(clickedCharacter.id)
                    }
                    updateDoneCounter()
                    notifyChange(chars, clickedCharacter.id)
                }
            },
            loadAvatar = { iv, uri ->
                if (!uri.isNullOrBlank()) {
                    Glide.with(this)
                        .load(uri)
                        .placeholder(R.drawable.placeholder_avatar)
                        .error(R.drawable.placeholder_avatar)
                        .into(iv)
                } else {
                    iv.setImageResource(R.drawable.placeholder_avatar)
                }
            }
        )
        recycler.adapter = adapter
        updateDoneCounter()
    }

    private fun notifyChange(chars: List<CharacterProfile>, id: String?) {
        if (id == null) return
        val idx = chars.indexOfFirst { it.id == id }
        if (idx >= 0) recycler.adapter?.notifyItemChanged(idx)
    }

    private fun finishWithResult() {
        val result = Intent().apply {
            putStringArrayListExtra("selectedCharacterIds", ArrayList(selectedIds))
            if (!isTempMode) {
                when (intent.getStringExtra("mode")) {
                    "edit" -> putExtra("collectionId", intent.getStringExtra("collectionId"))
                    "create" -> putExtra("collectionName", intent.getStringExtra("collectionName"))
                }
            }
            if (intent.hasExtra("replaceSlotId")) {
                putExtra("replaceSlotId", intent.getStringExtra("replaceSlotId"))
                putExtra("oldBaseCharacterId", intent.getStringExtra("oldBaseCharacterId"))
                putExtra("SUBSTITUTION_MODE", intent.getBooleanExtra("SUBSTITUTION_MODE", false))
            }
        }
        setResult(RESULT_OK, result)
        finish()
    }

    // ---------- UI helpers ----------

    private fun updateDoneCounter() {
        val cap = if (selectionCap == Int.MAX_VALUE) "∞" else selectionCap
        doneButton.text = if (selectionCap == 1) "Done — Pick 1" else "Done — Selected: ${selectedIds.size} / $cap"
        doneButton.isEnabled = selectedIds.isNotEmpty()
        doneButton.setOnClickListener { finishWithResult() }
    }
}
