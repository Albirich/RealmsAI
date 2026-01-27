package com.example.RealmsAI

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton // Import
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog // Import
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.RealmsAI.models.ChatProfile
import com.example.RealmsAI.models.CharacterProfile
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.gson.Gson

class ChatHubActivity : BaseActivity() {
    private lateinit var sortSpinner: Spinner
    private lateinit var adapter: ChatPreviewAdapter
    private lateinit var searchEditText: EditText
    private lateinit var filterButton: ImageButton // <--- NEW

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    private var characterMap: Map<String, CharacterProfile> = emptyMap()
    private var allChatProfiles: List<ChatProfile> = emptyList()

    // --- TAG FILTERING DATA ---
    private val availableTags = arrayOf(
        "Fantasy", "Sci-Fi", "Modern", "Romance", "Horror",
        "Comedy", "Action", "Drama", "Mystery", "Adventure",
        "Roleplay", "Strategy", "Co-op", "PvP", "Slow Burn", "Fast Paced"
    )
    private val filterChecked = BooleanArray(availableTags.size)
    private val activeTagFilters = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_hub)
        setupBottomNav()

        val chatsRv = findViewById<RecyclerView>(R.id.chatHubRecyclerView).apply {
            layoutManager = GridLayoutManager(this@ChatHubActivity, 2)
        }

        adapter = ChatPreviewAdapter(
            context = this,
            chatList = emptyList(),
            onClick = { preview ->
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                if (userId == null) {
                    Toast.makeText(this, "You must be signed in to continue.", Toast.LENGTH_SHORT)
                        .show()
                    return@ChatPreviewAdapter
                }

                // Logic to start a FRESH session (goes to Landing Page setup)
                val startNewSession = {
                    val intent = Intent(this, SessionLandingActivity::class.java).apply {
                        putExtra("CHAT_ID", preview.id)
                        putExtra("CHAT_PROFILE_JSON", preview.rawJson)
                    }
                    startActivity(intent)
                }

                // Perform the check
                findSessionForUser(
                    targetId = preview.id,
                    userId = userId,
                    onResult = { existingSessionId ->
                        // FOUND: Show Resume Dialog
                        AlertDialog.Builder(this)
                            .setTitle("Resume Session?")
                            .setMessage("You have an active session for this character/chat. Would you like to pick up where you left off?")
                            .setPositiveButton("Resume") { _, _ ->
                                // Resume: Skip Landing Page, go straight to Chat
                                val intent = Intent(this, MainActivity::class.java).apply {
                                    putExtra("SESSION_ID", existingSessionId)
                                    putExtra("CHAT_ID", preview.id)
                                }
                                startActivity(intent)
                            }
                            .setNegativeButton("Start New") { _, _ ->
                                // New: Go to Landing Page
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
            itemLayoutRes = R.layout.chat_preview_item,
            onLongClick = { preview ->
                AlertDialog.Builder(this)
                    .setTitle(preview.title)
                    .setItems(arrayOf("Profile", "Creator","Save Copy")) { _, which ->
                        when (which) {
                            0 -> { // --- Profile ---
                                startActivity(
                                    Intent(this, ChatProfileActivity::class.java)
                                        .putExtra("chatId", preview.id)
                                )
                            }
                            1 -> { // --- Creator ---
                                startActivity(
                                    Intent(this, DisplayProfileActivity::class.java)
                                        .putExtra("userId", preview.author)
                                )
                            }
                            2 -> { // Save Copy -> LOCKED
                                checkPremiumStatus {
                                    performCopyChat(preview)
                                }
                            }
                        }
                    }
                    .show()
            }

        )
        chatsRv.adapter = adapter

        // --- SEARCH SETUP ---
        searchEditText = findViewById(R.id.searchEditText)
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterChats(s?.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // --- FILTER BUTTON SETUP ---
        filterButton = findViewById(R.id.filterButton)
        filterButton.setOnClickListener {
            showFilterDialog()
        }

        // --- LOAD DATA ---
        FirebaseFirestore.getInstance().collection("characters").get()
            .addOnSuccessListener { charSnap ->
                characterMap = charSnap.documents
                    .mapNotNull { it.toObject(CharacterProfile::class.java) }
                    .associateBy { it.id }
                setupSpinnerAndShowChats(chatsRv)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load characters", Toast.LENGTH_SHORT).show()
                setupSpinnerAndShowChats(chatsRv)
            }
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
                        .setMessage("Cloning chats to your library is a Premium feature.")
                        .setPositiveButton("Upgrade") { _, _ ->
                            startActivity(Intent(this, UpgradeActivity::class.java))
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
    }

    private fun performCopyChat(preview: ChatPreview) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        // Use the rawJson you already have in the preview!
        val sourceProfile = try {
            Gson().fromJson(preview.rawJson, ChatProfile::class.java)
        } catch (e: Exception) { return }

        // Clone it
        val newRef = db.collection("chats").document()
        val newChat = sourceProfile.copy(
            id = newRef.id,
            author = userId,
            title = "Copy of ${sourceProfile.title}",
            private = true,
            timestamp = Timestamp.now(),
            ratingCount = 0,
            ratingSum = 0.0
        )

        // Save
        db.collection("chats").document(newRef.id).set(newChat)
            .addOnSuccessListener {
                Toast.makeText(this, "Chat copied to your library!", Toast.LENGTH_SHORT).show()
            }
    }

    private fun findSessionForUser(
        targetId: String,
        userId: String,
        onResult: (String) -> Unit,
        onNoneFound: () -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()

        // We check if 'chatId' (which stores the CharacterID or ScenarioID) matches
        // AND if the user is a participant.
        db.collection("sessions")
            .whereEqualTo("chatId", targetId)
            .whereArrayContains("userList", userId)
            .limit(1) // We only need to know if ONE exists
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    // Found an existing session! Return its ID.
                    onResult(documents.documents[0].id)
                } else {
                    // No session found.
                    onNoneFound()
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("SessionCheck", "Error checking for existing session", e)
                onNoneFound() // Default to new session on error
            }
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

                // Color the button if filters are active
                if (activeTagFilters.isNotEmpty()) {
                    filterButton.setColorFilter(getColor(R.color.purple_500))
                } else {
                    filterButton.clearColorFilter()
                }

                filterChats(searchEditText.text.toString())
            }
            .setNeutralButton("Clear") { _, _ ->
                filterChecked.fill(false)
                activeTagFilters.clear()
                filterButton.clearColorFilter()
                filterChats(searchEditText.text.toString())
            }
            .show()
    }

    private fun setupSpinnerAndShowChats(chatsRv: RecyclerView) {
        sortSpinner = findViewById(R.id.sortSpinner)
        val options = listOf("Latest", "Hot")
        sortSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        sortSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                val field = if (options[pos] == "Latest") "timestamp" else "rating"
                showChats(chatsRv, orderBy = field)
            }
        }
        showChats(chatsRv, orderBy = "timestamp")
    }

    private fun showChats(chatsRv: RecyclerView, orderBy: String = "timestamp") {
        FirebaseFirestore.getInstance()
            .collection("chats")
            .whereNotEqualTo("private", true)
            .orderBy(orderBy, Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                // 1. Save to Master List

                allChatProfiles = snap.documents.mapNotNull { doc ->
                    doc.toObject(ChatProfile::class.java)?.copy(id = doc.id)
                }

                // 2. Apply Filters (This updates the adapter)
                filterChats(searchEditText.text.toString())
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load chats: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun filterChats(query: String?) {
        val text = query?.trim()?.lowercase() ?: ""

        val filteredList = allChatProfiles.filter { chat ->
            // 1. TEXT SEARCH: Title, Description, or UNIVERSE
            val matchesText = if (text.isEmpty()) true else {
                chat.title.lowercase().contains(text) ||
                        chat.description.lowercase().contains(text) ||
                        chat.universe.lowercase().contains(text) // <--- Search Universe
            }

            // 2. TAG FILTER: Must contain ALL selected tags
            val matchesTags = if (activeTagFilters.isEmpty()) true else {
                activeTagFilters.all { requiredTag ->
                    chat.tags.any { it.equals(requiredTag, ignoreCase = true) }
                }
            }

            matchesText && matchesTags
        }

        // Convert to Previews
        val previews = filteredList.map { profile ->
            val char1Id = profile.characterIds.getOrNull(0)
            val char2Id = profile.characterIds.getOrNull(1)
            val char1 = characterMap[char1Id]
            val char2 = characterMap[char2Id]

            ChatPreview(
                id = profile.id,
                title = profile.title,
                description = profile.description,
                avatar1Uri = char1?.avatarUri ?: "",
                avatar2Uri = char2?.avatarUri ?: "",
                avatar1ResId = R.drawable.placeholder_avatar,
                avatar2ResId = R.drawable.placeholder_avatar,
                rating = if (profile.ratingCount > 0) (profile.ratingSum / profile.ratingCount).toFloat() else 0f,
                timestamp = profile.timestamp,
                author = profile.author,
                tags = profile.tags,
                sfwOnly = profile.sfwOnly,
                chatProfile = profile,
                rawJson = Gson().toJson(profile)
            )
        }

        adapter.updateList(previews)
    }
}