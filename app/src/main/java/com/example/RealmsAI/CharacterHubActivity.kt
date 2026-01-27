package com.example.RealmsAI

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.CharacterCollection
import com.example.RealmsAI.models.CharacterPreview
import com.example.RealmsAI.models.CharacterProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.gson.Gson

class CharacterHubActivity : BaseActivity() {
    private lateinit var sortSpinner: Spinner
    private lateinit var adapter: CharacterPreviewAdapter
    private lateinit var searchEditText: EditText
    private lateinit var filterButton: ImageButton

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    private var allCharacterProfiles: List<CharacterProfile> = emptyList()

    private val availableTags = arrayOf(
        "Fantasy", "Sci-Fi", "Modern", "Male", "Female",
        "Non-Binary", "Monster", "Hero", "Villain", "OC",
        "Canon", "Tsundere", "Yandere", "Kuudere", "Dandere"
    )
    private val filterChecked = BooleanArray(availableTags.size)
    private val activeTagFilters = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_character_hub)
        setupBottomNav()


        // 1) Find & configure RecyclerView
        val charsRv = findViewById<RecyclerView>(R.id.characterHubRecyclerView).apply {
            layoutManager = GridLayoutManager(this@CharacterHubActivity, 2)
        }

        adapter = CharacterPreviewAdapter(
            context = this,
            items = emptyList(),
            itemLayoutRes = R.layout.character_preview_item,
            onClick = { preview ->
                val userId = currentUserId
                if (userId == null) {
                    Toast.makeText(this, "You must be signed in to continue.", Toast.LENGTH_SHORT).show()
                    return@CharacterPreviewAdapter
                }

                // 1. Define the "Start New" Logic (Preserving your specific extras)
                val startNewSession = {
                    startActivity(Intent(this, SessionLandingActivity::class.java).apply {
                        Log.d("Characterhubactivity", "it has an id of: ${preview.id}")
                        // Note: Keeping your specific keys for Characters
                        putExtra("CHARACTER_ID", preview.id)
                        putExtra("CHARACTER_PROFILES_JSON", preview.rawJson)
                    })
                }

                // 2. Check for Existing Session
                findSessionForUser(
                    targetId = preview.id,
                    userId = userId,
                    onResult = { existingSessionId ->
                        // FOUND: Show Resume Dialog
                        AlertDialog.Builder(this)
                            .setTitle("Resume Session?")
                            .setMessage("You have an active session for this character. Would you like to pick up where you left off?")
                            .setPositiveButton("Resume") { _, _ ->
                                // Resume: Go straight to MainActivity with the Session ID
                                val intent = Intent(this, MainActivity::class.java).apply {
                                    putExtra("SESSION_ID", existingSessionId)
                                    // Pass ID for context/logging if needed
                                    putExtra("CHAT_ID", preview.id)
                                }
                                startActivity(intent)
                            }
                            .setNegativeButton("Start New") { _, _ ->
                                startNewSession()
                            }
                            .setNeutralButton("Cancel", null)
                            .show()
                    },
                    onNoneFound = {
                        // NOT FOUND: Start New immediately
                        startNewSession()
                    }
                )
            },
            onLongClick = { preview ->
                AlertDialog.Builder(this)
                    .setTitle(preview.name)
                    .setItems(arrayOf("Profile", "Creator", "Add to Collection")) { _, which ->
                        when (which) {
                            0 -> { // Profile
                                startActivity(
                                    Intent(this, CharacterProfileActivity::class.java)
                                        .putExtra("characterId", preview.id)
                                )
                            }
                            1 -> { // Creator
                                startActivity(
                                    Intent(this, DisplayProfileActivity::class.java)
                                        .putExtra("userId", preview.author)
                                )
                            }
                            2 -> { // Add to Collection
                                checkPremiumStatus {
                                    // EXISTING LOGIC MOVED INSIDE HERE
                                    val userId = currentUserId ?: return@checkPremiumStatus
                                    loadUserCollections { colls ->
                                        if (colls.isEmpty()) {
                                            promptNewCollectionName { name ->
                                                createCollection(name, userId) { newColl ->
                                                    addCharacterToCollection(newColl.id, preview.id)
                                                }
                                            }
                                        } else {
                                            showCollectionPickerDialog(colls) { picked ->
                                                addCharacterToCollection(picked.id, preview.id)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                .show()
            }

        )

        charsRv.adapter = adapter

        searchEditText = findViewById(R.id.searchEditText)
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterCharacters(s?.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // 3) Setup Filter Button
        filterButton = findViewById(R.id.filterButton)
        filterButton.setOnClickListener {
            showFilterDialog()
        }

        // 4) Set up the Spinner ("Latest" vs. "Hot")
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
                // pos==0 → sort by createdAt; pos==1 → sort by your “hotness” field (e.g. popularity)
                val field = if (pos == 0) "createdAt" else "popularity"
                showCharacters(charsRv, orderBy = field)
            }
        }

        // 5) Initial load = “Latest”
        showCharacters(charsRv, orderBy = "createdAt")
    }

    private fun findSessionForUser(
        targetId: String,
        userId: String,
        onResult: (String) -> Unit,
        onNoneFound: () -> Unit
    ) {
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("sessions")
            .whereEqualTo("chatId", targetId) // Note: We use 'chatId' field for both Chat and Character IDs in the DB
            .whereArrayContains("userList", userId)
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    onResult(documents.documents[0].id)
                } else {
                    onNoneFound()
                }
            }
            .addOnFailureListener {
                onNoneFound()
            }
    }

    private fun loadUserCollections(onLoaded: (List<CharacterCollection>) -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) return onLoaded(emptyList())

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("collections")
            .orderBy("name") // optional
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.mapNotNull { doc ->
                    doc.toObject(CharacterCollection::class.java)?.copy(id = doc.id)
                }
                onLoaded(list)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load collections.", Toast.LENGTH_SHORT).show()
                onLoaded(emptyList())
            }
    }

    private fun showFilterDialog() {
        AlertDialog.Builder(this)
            .setTitle("Filter by Tags")
            .setMultiChoiceItems(availableTags, filterChecked) { _, which, isChecked ->
                filterChecked[which] = isChecked
            }
            .setPositiveButton("Apply") { _, _ ->
                // 1. Rebuild active set
                activeTagFilters.clear()
                availableTags.forEachIndexed { index, tag ->
                    if (filterChecked[index]) activeTagFilters.add(tag)
                }

                // 2. Re-run filter with current search text
                val currentSearch = searchEditText.text.toString()
                filterCharacters(currentSearch)

                // Optional: Tint button to show active filter
                if (activeTagFilters.isNotEmpty()) {
                    filterButton.setColorFilter(getColor(R.color.purple_500)) // Use your accent color
                } else {
                    filterButton.clearColorFilter()
                }
            }
            .setNeutralButton("Clear") { _, _ ->
                // Reset everything
                filterChecked.fill(false)
                activeTagFilters.clear()
                filterButton.clearColorFilter()

                val currentSearch = searchEditText.text.toString()
                filterCharacters(currentSearch)
            }
            .show()
    }

    private fun showCollectionPickerDialog(
        collections: List<CharacterCollection>,
        onPicked: (CharacterCollection) -> Unit
    ) {
        val names = collections.map { it.name } + "New collection…"
        AlertDialog.Builder(this)
            .setTitle("Add to collection")
            .setItems(names.toTypedArray()) { _, idx ->
                if (idx == collections.size) {
                    // "New collection…"
                    promptNewCollectionName { name ->
                        val userId = currentUserId ?: return@promptNewCollectionName
                        createCollection(name, userId) { newColl ->
                            onPicked(newColl)
                        }
                    }
                } else {
                    onPicked(collections[idx])
                }
            }
            .show()
    }

    private fun promptNewCollectionName(onNamed: (String) -> Unit) {
        val input = android.widget.EditText(this).apply { hint = "Collection name" }
        AlertDialog.Builder(this)
            .setTitle("New collection")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isBlank()) {
                    Toast.makeText(this, "Name cannot be empty.", Toast.LENGTH_SHORT).show()
                } else onNamed(name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createCollection(
        name: String,
        userId: String,
        onCreated: (CharacterCollection) -> Unit
    ) {
        val data = hashMapOf(
            "name" to name,
            "characterIds" to emptyList<String>()
        )

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("collections")
            .add(data)
            .addOnSuccessListener { ref ->
                onCreated(CharacterCollection(id = ref.id, name = name, characterIds = emptyList()))
                Toast.makeText(this, "Collection created.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to create collection.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun addCharacterToCollection(collectionId: String, characterId: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val ref = FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("collections")
            .document(collectionId)

        ref.update("characterIds", com.google.firebase.firestore.FieldValue.arrayUnion(characterId))
            .addOnSuccessListener {
                Toast.makeText(this, "Added to collection.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to add to collection.", Toast.LENGTH_SHORT).show()
            }
    }


    private fun showCharacters(
        rv: RecyclerView,
        orderBy: String = "createdAt"
    ) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "You must be signed in to view characters.", Toast.LENGTH_SHORT).show()
            return
        }

        FirebaseFirestore.getInstance()
            .collection("characters")
            .whereNotEqualTo("private", true)
            .orderBy(orderBy, Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                allCharacterProfiles = snap.documents.mapNotNull { doc ->
                    doc.toObject(CharacterProfile::class.java)?.copy(id = doc.id)
                }

                // 2. Apply current filter
                filterCharacters(searchEditText.text.toString())

                val list = snap.documents.mapNotNull { doc ->
                    // 1) Deserialize + inject doc ID
                    val cp = doc.toObject(CharacterProfile::class.java)
                        ?.copy(id = doc.id)
                        ?: return@mapNotNull null

                    // 2) Build preview
                    CharacterPreview(
                        id = cp.id,
                        name = cp.name,
                        summary = cp.summary.orEmpty(),
                        avatarUri = cp.avatarUri,
                        avatarResId = cp.avatarResId ?: R.drawable.placeholder_avatar,
                        author = cp.author,
                        rawJson = Gson().toJson(cp),
                        rating = if (cp.ratingCount > 0) cp.ratingSum / cp.ratingCount else 0.0
                    )

                }
                adapter.updateList(list)
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    "Failed to load characters: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun filterCharacters(query: String?) {
        val text = query?.trim()?.lowercase() ?: ""

        val filteredList = allCharacterProfiles.filter { char ->

            // 1. TEXT SEARCH: Name, Summary, Personality, Scenario, OR UNIVERSE
            val matchesText = if (text.isEmpty()) true else {
                char.name.lowercase().contains(text) ||
                        (char.summary?.lowercase()?.contains(text) == true) ||
                        (char.personality?.lowercase()?.contains(text) == true) ||
                        (char.soloScenario?.lowercase()?.contains(text) == true) ||
                        (char.universe.lowercase().contains(text)) // <--- Checks Universe field
            }

            // 2. TAG FILTER: Must contain ALL selected tags (AND logic)
            // If you prefer OR logic (match ANY tag), change .all to .any
            val matchesTags = if (activeTagFilters.isEmpty()) true else {
                activeTagFilters.all { requiredTag ->
                    char.tags.any { charTag -> charTag.equals(requiredTag, ignoreCase = true) }
                }
            }

            matchesText && matchesTags
        }

        // Convert to Previews
        val previews = filteredList.map { cp ->
            CharacterPreview(
                id = cp.id,
                name = cp.name,
                summary = cp.summary.orEmpty(),
                avatarUri = cp.avatarUri,
                avatarResId = cp.avatarResId ?: R.drawable.placeholder_avatar,
                author = cp.author,
                rawJson = Gson().toJson(cp),
                rating = if (cp.ratingCount > 0) cp.ratingSum / cp.ratingCount else 0.0
            )
        }

        adapter.updateList(previews)
    }

    private fun checkPremiumStatus(onPremium: () -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                if (doc.getBoolean("isPremium") == true) {
                    onPremium()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Premium Feature")
                        .setMessage("Saving characters to your collection allows you to edit and customize them.\n\nThis is a Premium feature.")
                        .setPositiveButton("Upgrade") { _, _ ->
                            startActivity(Intent(this, UpgradeActivity::class.java))
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
    }
}
