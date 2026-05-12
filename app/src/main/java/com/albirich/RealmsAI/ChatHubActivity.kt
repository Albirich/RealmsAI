package com.albirich.RealmsAI

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.albirich.RealmsAI.adapters.AreaPreviewAdapter
import com.albirich.RealmsAI.adapters.LorebookPreviewAdapter
import com.albirich.RealmsAI.models.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.gson.Gson
import java.util.UUID

class ChatHubActivity : BaseActivity() {

    private lateinit var categorySpinner: Spinner
    private lateinit var sortSpinner: Spinner
    private lateinit var galleryRecycler: RecyclerView
    private lateinit var searchEditText: EditText
    private lateinit var filterButton: ImageButton

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    private val db = FirebaseFirestore.getInstance()

    // --- CACHING & STATE ---
    private var characterMap: Map<String, CharacterProfile> = emptyMap()
    private var blockedUsersList: List<String> = emptyList() // <--- NEW: Holds our blocked IDs!

    // These hold the downloaded data so the Search Bar can filter instantly!
    private var allChatProfiles: List<ChatProfile> = emptyList()
    private var allCharacterProfiles: List<CharacterProfile> = emptyList()
    private var allAreas: List<Area> = emptyList()
    private var allLorebooks: List<Lorebook> = emptyList()

    // --- CONTEXT-AWARE TAG LISTS ---
    private val chatTags = arrayOf("Action", "Romance", "Mystery", "Horror", "Comedy", "Drama", "Slice of Life", "Adventure", "Thriller", "Fantasy", "Sci-Fi", "Cyberpunk", "Medieval", "Historical", "Modern", "Post-Apocalyptic", "Canon", "AU", "Slow Burn", "Grimdark", "High Stakes")
    private val charTags = arrayOf("Fantasy", "Sci-Fi", "Modern", "Male", "Female", "Non-Binary", "Monster", "Hero", "Villain", "OC", "Canon", "Tsundere", "Yandere", "Kuudere", "Dandere")
    // private val areaTags = arrayOf("City", "Dungeon", "Tavern", "Space Station", "Nature", "Interior", "Exterior", "Safe Zone", "Hostile")
    // private val lorebookTags = arrayOf("Magic System", "World History", "Factions", "Technology", "Religion", "Bestiary", "Items")
    private val areaTags = emptyArray<String>()
    private val lorebookTags = emptyArray<String>()

    private val activeTagFilters = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_hub)
        setupBottomNav()

        galleryRecycler = findViewById(R.id.chatHubRecyclerView)
        galleryRecycler.layoutManager = GridLayoutManager(this, 2)
        searchEditText = findViewById(R.id.searchEditText)
        filterButton = findViewById(R.id.filterButton)

        // --- SEARCH SETUP ---
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                applyCurrentFilters()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        filterButton.setOnClickListener { showFilterDialog() }

        // --- 1. INITIALIZE DATA CHAIN ---
        // Fetch blocked users FIRST, then character map, then setup spinners
        val userId = currentUserId
        if (userId != null) {
            db.collection("users").document(userId).get()
                .addOnSuccessListener { doc ->
                    // Safely pull the list of strings. If it doesn't exist, return empty.
                    blockedUsersList = (doc.get("blockedUsers") as? List<String>) ?: emptyList()
                    loadCharacterMapAndSpinners()
                }
                .addOnFailureListener {
                    loadCharacterMapAndSpinners() // Proceed anyway if it fails
                }
        } else {
            loadCharacterMapAndSpinners() // Proceed if not logged in
        }
    }

    private fun loadCharacterMapAndSpinners() {
        // Pre-load characters for Chat Previews
        db.collection("characters").get().addOnSuccessListener { snap ->
            characterMap = snap.documents.mapNotNull { it.toObject(CharacterProfile::class.java) }.associateBy { it.id }
            setupSpinners()
        }
    }

    private fun setupSpinners() {
        categorySpinner = findViewById(R.id.categorySpinner)
        sortSpinner = findViewById(R.id.sortSpinner)

        val categories = arrayOf("Chats", "Characters", "Areas", "Lorebooks")
        categorySpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        val sortOptions = arrayOf("Latest", "Hot")
        sortSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sortOptions)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        val spinnerListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                // Clear the search bar AND the tags when swapping categories!
                searchEditText.setText("")
                activeTagFilters.clear()
                filterButton.clearColorFilter()

                loadSelectedCategory()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        categorySpinner.onItemSelectedListener = spinnerListener
        sortSpinner.onItemSelectedListener = spinnerListener
    }

    private fun loadSelectedCategory() {
        val category = categorySpinner.selectedItem.toString()
        val sortBy = if (sortSpinner.selectedItem.toString() == "Latest") {
            if (category == "Chats" || category == "Areas" || category == "Lorebooks") "timestamp" else "createdAt"
        } else {
            "ratingSum"
        }

        when (category) {
            "Chats" -> loadPublicChats(sortBy)
            "Characters" -> loadPublicCharacters(sortBy)
            "Areas" -> loadPublicAreas(sortBy)
            "Lorebooks" -> loadPublicLorebooks(sortBy)
        }
    }

    // ==========================================
    //       FILTER & SEARCH ROUTER
    // ==========================================
    private fun applyCurrentFilters() {
        // THE SHIELD: If Android hasn't built the spinner yet, just stop and do nothing!
        if (!::categorySpinner.isInitialized) return

        val category = categorySpinner.selectedItem?.toString() ?: return
        val query = searchEditText.text.toString().trim().lowercase()

        when (category) {
            "Chats" -> filterChats(query)
            "Characters" -> filterCharacters(query)
            "Areas" -> filterAreas(query)
            "Lorebooks" -> filterLorebooks(query)
        }
    }

    private fun showFilterDialog() {
        val category = categorySpinner.selectedItem?.toString() ?: return

        // 1. Grab the correct list for the current tab
        val currentTags = when (category) {
            "Chats" -> chatTags
            "Characters" -> charTags
            "Areas" -> areaTags
            "Lorebooks" -> lorebookTags
            else -> emptyArray()
        }

        if (currentTags.isEmpty()) {
            Toast.makeText(this, "Tags for $category are coming soon!", Toast.LENGTH_SHORT).show()
            return // Stop the dialog from opening!
        }

        // 2. Figure out which ones are already checked
        val checkedItems = BooleanArray(currentTags.size) { index ->
            activeTagFilters.contains(currentTags[index])
        }

        // 3. Temporary set to hold selections while dialog is open
        val tempSelections = mutableSetOf<String>().apply { addAll(activeTagFilters) }

        AlertDialog.Builder(this)
            .setTitle("Filter $category")
            .setMultiChoiceItems(currentTags, checkedItems) { _, which, isChecked ->
                val tag = currentTags[which]
                if (isChecked) tempSelections.add(tag) else tempSelections.remove(tag)
            }
            .setPositiveButton("Apply") { _, _ ->
                activeTagFilters.clear()
                activeTagFilters.addAll(tempSelections)

                filterButton.setColorFilter(if (activeTagFilters.isNotEmpty()) getColor(R.color.purple_500) else 0)
                applyCurrentFilters()
            }
            .setNeutralButton("Clear") { _, _ ->
                activeTagFilters.clear()
                filterButton.clearColorFilter()
                applyCurrentFilters()
            }
            .show()
    }

    // ==========================================
    //           1. PUBLIC CHATS
    // ==========================================
    private fun loadPublicChats(orderBy: String) {
        db.collection("chats")
            .whereNotEqualTo("private", true)
            .orderBy(orderBy, Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener { snap ->
                // NEW: Added `.takeIf { it.author !in blockedUsersList }`
                allChatProfiles = snap.documents.mapNotNull {
                    it.toObject(ChatProfile::class.java)?.copy(id = it.id)?.takeIf { profile -> profile.author !in blockedUsersList }
                }

                galleryRecycler.adapter = ChatPreviewAdapter(
                    context = this, chatList = emptyList(), itemLayoutRes = R.layout.chat_preview_item,
                    onClick = { preview -> handleChatClick(preview) },
                    onLongClick = { preview ->
                        AlertDialog.Builder(this).setTitle(preview.title)
                            .setItems(arrayOf("Profile", "Creator", "Save Copy")) { _, which ->
                                when (which) {
                                    0 -> startActivity(Intent(this, ChatProfileActivity::class.java).putExtra("chatId", preview.id))
                                    1 -> startActivity(Intent(this, DisplayProfileActivity::class.java).putExtra("userId", preview.author))
                                    2 -> checkPremiumStatus { performCopyChat(preview) }
                                }
                            }.show()
                    }
                )
                applyCurrentFilters()
            }
    }

    private fun filterChats(query: String) {
        val filtered = allChatProfiles.filter { chat ->
            val matchesText = if (query.isEmpty()) true else {
                chat.title.lowercase().contains(query) || chat.description.lowercase().contains(query) || chat.universe.lowercase().contains(query)
            }
            val matchesTags = if (activeTagFilters.isEmpty()) true else {
                activeTagFilters.all { required -> chat.tags.any { it.equals(required, true) } }
            }
            matchesText && matchesTags
        }

        val previews = filtered.map { p ->
            val char1 = characterMap[p.characterIds.getOrNull(0)]
            val char2 = characterMap[p.characterIds.getOrNull(1)]
            ChatPreview(
                id = p.id, originalId = p.originalId, rawJson = Gson().toJson(p),
                title = p.title, description = p.description,
                avatar1Uri = char1?.avatarUri ?: "", avatar2Uri = char2?.avatarUri ?: "",
                avatar1ResId = R.drawable.placeholder_avatar, avatar2ResId = R.drawable.placeholder_avatar,
                rating = if (p.ratingCount > 0) (p.ratingSum / p.ratingCount).toFloat() else 0f,
                timestamp = p.timestamp, author = p.author, tags = p.tags, sfwOnly = p.sfwOnly, chatProfile = p
            )
        }
        (galleryRecycler.adapter as? ChatPreviewAdapter)?.updateList(previews)
    }

    // ==========================================
    //           2. PUBLIC CHARACTERS
    // ==========================================
    private fun loadPublicCharacters(orderBy: String) {
        db.collection("characters")
            .whereNotEqualTo("private", true)
            .orderBy(orderBy, Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener { snap ->
                // NEW: Added `.takeIf { it.author !in blockedUsersList }`
                allCharacterProfiles = snap.documents.mapNotNull {
                    it.toObject(CharacterProfile::class.java)?.copy(id = it.id)?.takeIf { cp -> cp.author !in blockedUsersList }
                }

                galleryRecycler.adapter = CharacterPreviewAdapter(
                    context = this, items = emptyList(), itemLayoutRes = R.layout.character_preview_item,
                    onClick = { preview -> handleCharacterClick(preview) },
                    onLongClick = { preview ->
                        AlertDialog.Builder(this).setTitle(preview.name)
                            .setItems(arrayOf("Profile", "Creator", "Save Copy")) { _, which ->
                                when (which) {
                                    0 -> startActivity(Intent(this, CharacterProfileActivity::class.java).putExtra("characterId", preview.id))
                                    1 -> startActivity(Intent(this, DisplayProfileActivity::class.java).putExtra("userId", preview.author))
                                    2 -> checkPremiumStatus {
                                        db.collection("characters").document(preview.id).get().addOnSuccessListener { snapshot ->
                                            val fullProfile = snapshot.toObject(CharacterProfile::class.java)
                                            if (fullProfile != null) saveCharacterAsUser(fullProfile)
                                        }
                                    }
                                }
                            }.show()
                    }
                )
                applyCurrentFilters()
            }
    }

    private fun filterCharacters(query: String) {
        val filtered = allCharacterProfiles.filter { char ->
            val matchesText = if (query.isEmpty()) true else {
                char.name.lowercase().contains(query) || (char.summary?.lowercase()?.contains(query) == true) || char.universe.lowercase().contains(query)
            }
            val matchesTags = if (activeTagFilters.isEmpty()) true else {
                activeTagFilters.all { required -> char.tags.any { it.equals(required, true) } }
            }
            matchesText && matchesTags
        }

        val previews = filtered.map { cp ->
            CharacterPreview(
                id = cp.id, originalId = cp.originalId ?: cp.id, name = cp.name,
                summary = cp.summary.orEmpty(), avatarUri = cp.avatarUri,
                avatarResId = R.drawable.placeholder_avatar, author = cp.author,
                rawJson = Gson().toJson(cp), rating = if (cp.ratingCount > 0) cp.ratingSum / cp.ratingCount else 0.0
            )
        }
        (galleryRecycler.adapter as? CharacterPreviewAdapter)?.updateList(previews)
    }

    // ==========================================
    //           3. PUBLIC AREAS
    // ==========================================
    private fun loadPublicAreas(orderBy: String) {
        db.collection("areas")
            .whereEqualTo("private", false)
            .orderBy(orderBy, Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener { snap ->
                // NEW: Added `.takeIf { it.creatorId !in blockedUsersList }`
                allAreas = snap.documents.mapNotNull {
                    it.toObject(Area::class.java)?.copy(id = it.id)?.takeIf { area -> area.creatorId !in blockedUsersList }
                }

                galleryRecycler.adapter = AreaPreviewAdapter(
                    context = this, previews = emptyList(),
                    onClick = { preview -> startActivity(Intent(this, AreaProfileActivity::class.java).putExtra("AREA_ID", preview.id)) },
                    onLongClick = { preview ->
                        AlertDialog.Builder(this).setTitle("Save Area?")
                            .setMessage("Add '${preview.name}' to your personal Creation Hub?")
                            .setPositiveButton("Save") { _, _ -> cloneAreaToUser(preview) }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                )
                applyCurrentFilters()
            }
    }

    private fun filterAreas(query: String) {
        val filtered = allAreas.filter { area ->
            if (query.isEmpty()) true else {
                area.name.lowercase().contains(query) || area.publicInfo.lowercase().contains(query)
            }
        }
        val previews = filtered.map { area ->
            AreaPreview(
                id = area.id, name = area.name, publicInfo = area.publicInfo,
                coverImageUri = area.locations.firstOrNull()?.uri,
                locationCount = area.locations.size, nsfw = area.nsfw, rawJson = Gson().toJson(area)
            )
        }
        (galleryRecycler.adapter as? AreaPreviewAdapter)?.updateList(previews)
    }

    // ==========================================
    //           4. PUBLIC LOREBOOKS
    // ==========================================
    private fun loadPublicLorebooks(orderBy: String) {
        db.collection("lorebooks")
            .whereEqualTo("private", false)
            .orderBy(orderBy, Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener { snap ->

                // NEW: Added `.takeIf { it.creatorId !in blockedUsersList }`
                allLorebooks = snap.documents.mapNotNull {
                    it.toLorebookSafe()?.takeIf { book -> book.creatorId !in blockedUsersList }
                }

                galleryRecycler.adapter = LorebookPreviewAdapter(
                    context = this, lorebooks = emptyList(),
                    onClick = { book -> startActivity(Intent(this, LorebookProfileActivity::class.java).putExtra("LOREBOOK_ID", book.id)) },
                    onLongClick = { book ->
                        AlertDialog.Builder(this).setTitle("Save Lorebook?")
                            .setMessage("Add '${book.title}' to your personal Creation Hub?")
                            .setPositiveButton("Save") { _, _ -> cloneLorebookToUser(book) }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                )
                applyCurrentFilters()
            }
    }

    private fun filterLorebooks(query: String) {
        val filtered = allLorebooks.filter { book ->
            if (query.isEmpty()) true else {
                book.title.lowercase().contains(query) || book.description.lowercase().contains(query)
            }
        }
        (galleryRecycler.adapter as? LorebookPreviewAdapter)?.updateList(filtered)
    }

    // ==========================================
    //           HELPER FUNCTIONS
    // ==========================================

    // --- CROSS-PLATFORM LOREBOOK PARSER ---
    private fun com.google.firebase.firestore.DocumentSnapshot.toLorebookSafe(): Lorebook? {
        if (!exists()) return null

        return try {
            val book = Lorebook(
                id = this.id,
                originalId = getString("originalId"),
                creatorId = getString("creatorId") ?: "",
                title = getString("title") ?: "",
                description = getString("description") ?: "",
                coverUri = getString("coverUri"),
                private = getBoolean("private") ?: false,
                timestamp = getTimestamp("timestamp")
            )

            val rawEntries = get("entries") as? List<Map<String, Any>> ?: emptyList()
            val parsedEntries = rawEntries.map { e ->

                val rawKeys = e["keys"]
                val safeKeys = when (rawKeys) {
                    is String -> rawKeys.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    is List<*> -> rawKeys.mapNotNull { it?.toString() }
                    else -> emptyList()
                }

                val rawEmbedding = e["embedding"] as? List<*>
                val safeEmbedding = rawEmbedding?.mapNotNull { (it as? Number)?.toDouble() }

                LoreEntry(
                    id = e["id"] as? String ?: UUID.randomUUID().toString(),
                    name = e["name"] as? String ?: "",
                    content = e["content"] as? String ?: "",
                    keys = safeKeys.toMutableList(),
                    embedding = safeEmbedding
                )
            }

            book.entries = parsedEntries.toMutableList()
            book

        } catch (e: Exception) {
            Log.e("LorebookParser", "Critical fail parsing Lorebook: ${this.id}", e)
            null
        }
    }

    private fun handleChatClick(preview: ChatPreview) {
        val userId = currentUserId ?: run { Toast.makeText(this, "Sign in required", Toast.LENGTH_SHORT).show(); return }
        findSessionForUser(preview.id, userId,
            onResult = { existingId -> showResumeDialog(existingId, preview.id, preview.rawJson, isCharacter = false) },
            onNoneFound = { startNewSession(preview.id, preview.rawJson, isCharacter = false) }
        )
    }

    private fun handleCharacterClick(preview: CharacterPreview) {
        val userId = currentUserId ?: run { Toast.makeText(this, "Sign in required", Toast.LENGTH_SHORT).show(); return }
        findSessionForUser(preview.id, userId,
            onResult = { existingId -> showResumeDialog(existingId, preview.id, preview.rawJson, isCharacter = true) },
            onNoneFound = { startNewSession(preview.id, preview.rawJson, isCharacter = true) }
        )
    }

    private fun startNewSession(id: String, rawJson: String, isCharacter: Boolean) {
        startActivity(Intent(this, SessionLandingActivity::class.java).apply {
            putExtra(if (isCharacter) "CHARACTER_ID" else "CHAT_ID", id)
            putExtra(if (isCharacter) "CHARACTER_PROFILES_JSON" else "CHAT_PROFILE_JSON", rawJson)
        })
    }

    private fun showResumeDialog(sessionId: String, targetId: String, rawJson: String, isCharacter: Boolean) {
        AlertDialog.Builder(this)
            .setTitle("Resume Session?")
            .setMessage("You have an active session. Pick up where you left off?")
            .setPositiveButton("Resume") { _, _ -> startActivity(Intent(this, MainActivity::class.java).apply { putExtra("SESSION_ID", sessionId); putExtra("CHAT_ID", targetId) }) }
            .setNegativeButton("Start New") { _, _ -> startNewSession(targetId, rawJson, isCharacter) }
            .setNeutralButton("Cancel", null).show()
    }

    private fun findSessionForUser(targetId: String, userId: String, onResult: (String) -> Unit, onNoneFound: () -> Unit) {
        db.collection("sessions").whereEqualTo("chatId", targetId).whereArrayContains("userList", userId).limit(1).get()
            .addOnSuccessListener { docs -> if (!docs.isEmpty) onResult(docs.documents[0].id) else onNoneFound() }
            .addOnFailureListener { onNoneFound() }
    }

    private fun checkPremiumStatus(onPremium: () -> Unit) {
        val userId = currentUserId ?: return
        db.collection("users").document(userId).get().addOnSuccessListener { doc ->
            if (doc.getBoolean("isPremium") == true) onPremium()
            else AlertDialog.Builder(this).setTitle("Premium Feature").setMessage("Cloning to your library is a Premium feature.").setPositiveButton("Upgrade") { _, _ -> startActivity(Intent(this, UpgradeActivity::class.java)) }.setNegativeButton("Cancel", null).show()
        }
    }

    private fun performCopyChat(preview: ChatPreview) {
        val userId = currentUserId ?: return
        val source = try { Gson().fromJson(preview.rawJson, ChatProfile::class.java) } catch (e: Exception) { return }
        val newChar = source.copy(id = UUID.randomUUID().toString(), originalId = source.originalId, author = userId, title = "Copy of ${source.title}", private = true, timestamp = Timestamp.now(), secretDescription = "", ratingCount = 0, ratingSum = 0.0)
        db.collection("chats").document(newChar.id).set(newChar).addOnSuccessListener { Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show() }
    }

    private fun saveCharacterAsUser(char: CharacterProfile) {
        val userId = currentUserId ?: return
        val newChar = char.copy(id = UUID.randomUUID().toString(), author = userId, privateDescription = "", originalId = char.id, private = true)
        db.collection("characters").document(newChar.id).set(newChar).addOnSuccessListener { Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show() }
    }

    private fun cloneAreaToUser(preview: AreaPreview) {
        val userId = currentUserId ?: return
        val sourceArea = Gson().fromJson(preview.rawJson, Area::class.java) ?: return
        val clonedArea = sourceArea.copy(id = UUID.randomUUID().toString(), creatorId = userId, private = true, timestamp = Timestamp.now())
        db.collection("areas").document(clonedArea.id).set(clonedArea).addOnSuccessListener { Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show() }
    }

    private fun cloneLorebookToUser(book: Lorebook) {
        val userId = currentUserId ?: return
        val clonedBook = book.copy(id = UUID.randomUUID().toString(), creatorId = userId, private = true, timestamp = Timestamp.now())
        db.collection("lorebooks").document(clonedBook.id).set(clonedBook).addOnSuccessListener { Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show() }
    }
}