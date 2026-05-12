package com.albirich.RealmsAI

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.albirich.RealmsAI.models.ChatProfile
import com.albirich.RealmsAI.models.CharacterProfile
import com.albirich.RealmsAI.models.PersonaProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject         // ← This brings in the correct .toObject extension
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.gson.Gson
import org.json.JSONObject
import com.albirich.RealmsAI.models.AreaPreview
import com.albirich.RealmsAI.models.CharacterPreview


class CreationHubActivity : BaseActivity() {

    private val db = Firebase.firestore
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    private var characterMap: Map<String, CharacterProfile> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_creation_hub) // Make sure this matches your XML file name
        setupBottomNav()

        val spinner = findViewById<Spinner>(R.id.filterSpinner)
        val btnCreateNew = findViewById<Button>(R.id.btnCreateNew)

        // Hardcode the array here so we don't have to mess with strings.xml right now
        val categories = arrayOf("Chats", "Characters", "Personas", "Backgrounds", "Lorebooks", "Collections")
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        val chatsRv = findViewById<RecyclerView>(R.id.recyclerChats)
        val charactersRv = findViewById<RecyclerView>(R.id.recyclerCharacters)
        val personasRv = findViewById<RecyclerView>(R.id.recyclerPersonas)
        val backgroundsRv = findViewById<RecyclerView>(R.id.recyclerBackgrounds)
        val collectionsRv = findViewById<RecyclerView>(R.id.recyclerCollections)
        val lorebooksRv = findViewById<RecyclerView>(R.id.recyclerLorebooks)

        // Helper to hide all
        fun hideAll() {
            chatsRv.visibility = View.GONE
            charactersRv.visibility = View.GONE
            personasRv.visibility = View.GONE
            backgroundsRv.visibility = View.GONE
            collectionsRv.visibility = View.GONE
            lorebooksRv.visibility = View.GONE
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selected = categories[position]
                hideAll()

                // 1. Update the UI
                btnCreateNew.text = "+ New $selected"

                // 2. Show the correct list
                when (selected) {
                    "Chats" -> chatsRv.visibility = View.VISIBLE
                    "Characters" -> charactersRv.visibility = View.VISIBLE
                    "Personas" -> personasRv.visibility = View.VISIBLE
                    "Backgrounds" -> backgroundsRv.visibility = View.VISIBLE
                    "Lorebooks" -> lorebooksRv.visibility = View.VISIBLE
                    "Collections" -> collectionsRv.visibility = View.VISIBLE
                }

                // 3. Update the Create Button Intent
                btnCreateNew.setOnClickListener {
                    val intent = when (selected) {
                        "Chats" -> Intent(this@CreationHubActivity, ChatCreationActivity::class.java)
                        "Characters" -> Intent(this@CreationHubActivity, CharacterCreationActivity::class.java)
                        "Personas" -> Intent(this@CreationHubActivity, PersonaCreationActivity::class.java)
                        "Backgrounds" -> Intent(this@CreationHubActivity, AreaCreationActivity::class.java).apply {
                            putExtra("START_CREATION", true)
                        }
                        "Lorebooks" -> Intent(this@CreationHubActivity, LorebookCreationActivity::class.java)
                        "Collections" -> {
                            promptNewCollectionName()
                            null // Return null because promptNewCollectionName handles the intent
                        }
                        else -> null
                    }
                    intent?.let { startActivity(it) }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Fetch data exactly as you had it
        db.collection("characters")
            .get()
            .addOnSuccessListener { snap ->
                characterMap = snap.documents
                    .mapNotNull { it.toObject(CharacterProfile::class.java) }
                    .associateBy { it.id }

                showChats(chatsRv)
                showCharacters(charactersRv)
                showPersonas(personasRv)
                showBackgrounds(backgroundsRv)
                showCollections(collectionsRv)
                showLorebooks(lorebooksRv)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Could not load characters: ${e.message}", Toast.LENGTH_SHORT).show()
                showChats(chatsRv)
                showCharacters(charactersRv)
                showPersonas(personasRv)
                showBackgrounds(backgroundsRv)
                showCollections(collectionsRv)
                showLorebooks(lorebooksRv)
            }
    }

    override fun onResume() {
        super.onResume()

        // 1. Grab your spinner (Make sure this ID matches your actual spinner ID in XML!)
        val spinner = findViewById<Spinner>(R.id.filterSpinner)
        val selectedCategory = spinner.selectedItem?.toString() ?: return

        // 2. Re-trigger the fetch function for whatever tab they are currently looking at
        when (selectedCategory) {
            "Chats" -> {
                val rv = findViewById<RecyclerView>(R.id.recyclerChats)
                showChats(rv)
            }
            "Characters" -> {
                val rv = findViewById<RecyclerView>(R.id.recyclerCharacters)
                showCharacters(rv)
            }
            "Personas" -> {
                val rv = findViewById<RecyclerView>(R.id.recyclerPersonas)
                showPersonas(rv)
            }
            "Backgrounds" -> {
                val rv = findViewById<RecyclerView>(R.id.recyclerBackgrounds)
                showBackgrounds(rv)
            }
            "Lorebooks" -> {
                val rv = findViewById<RecyclerView>(R.id.recyclerLorebooks)
                showLorebooks(rv)
            }
            "Collections" -> {
                // Collections already uses the modern ActivityResultLauncher which auto-refreshes,
                // but adding it here doesn't hurt as a failsafe!
                val rv = findViewById<RecyclerView>(R.id.recyclerCollections)
                showCollections(rv)
            }
        }
    }

    private fun lookupAvatar(charId: String): String? {
        // load from the same shared‐prefs or wherever you persisted your CharacterProfile
        val prefs = getSharedPreferences("characters", Context.MODE_PRIVATE)
        val json  = prefs.getString(charId, null) ?: return null
        return JSONObject(json).optString("avatarUri", null)
    }

    /**
     * Queries Firestore to see if a session already exists for this User + Chat/Character.
     */
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

    private fun showChats(rv: RecyclerView) {
        rv.layoutManager = GridLayoutManager(this, 2)
        val adapter = ChatPreviewAdapter(
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
                    .setItems(arrayOf("Profile", "Edit", "Delete", "Top Pick")) { _, which ->
                        when (which) {
                            0 -> { // --- Profile ---
                                startActivity(
                                    Intent(this, ChatProfileActivity::class.java)
                                        .putExtra("chatId", preview.id)
                                )
                            }
                            1 -> { // --- Edit Chat ---
                                db.collection("chats").document(preview.id).get()
                                    .addOnSuccessListener { snapshot ->
                                        val fullProfile = snapshot.toObject(ChatProfile::class.java)
                                        if (fullProfile != null) {
                                            val intent = Intent(this, ChatCreationActivity::class.java)
                                            intent.putExtra("CHAT_EDIT_ID", preview.id)
                                            intent.putExtra("CHAT_EDIT_ORIGINALID", preview.originalId)
                                            intent.putExtra("CHAT_PROFILE_JSON", Gson().toJson(fullProfile))
                                            startActivityForResult(intent, 1010)
                                        } else {
                                            Toast.makeText(this, "Chat profile not found.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .addOnFailureListener {
                                        Toast.makeText(this, "Failed: ${it.message}", Toast.LENGTH_SHORT).show()
                                    }
                            }
                            2 -> { // --- Delete Chat ---
                                AlertDialog.Builder(this)
                                    .setTitle("Delete?")
                                    .setMessage("Are you sure you want to delete '${preview.title}'?")
                                    .setPositiveButton("Yes") { _, _ ->
                                        db.collection("chats").document(preview.id)
                                            .delete()
                                            .addOnSuccessListener {
                                                Toast.makeText(this, "Deleted.", Toast.LENGTH_SHORT).show()
                                                startActivity(intent)
                                            }
                                            .addOnFailureListener { e ->
                                                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                    }
                                    .setNegativeButton("No", null)
                                    .show()
                            }
                            3 -> { // --- Top Pick ---
                                val currentUser = FirebaseAuth.getInstance().currentUser
                                if (currentUser == null) {
                                    Toast.makeText(this, "You must be signed in.", Toast.LENGTH_SHORT).show()
                                    return@setItems
                                }
                                val userId = currentUser.uid
                                val userDoc = db.collection("users").document(userId)
                                userDoc.get().addOnSuccessListener { snapshot ->
                                    val picks = snapshot.get("userPicks") as? MutableList<String> ?: mutableListOf()
                                    val alreadyPicked = picks.contains(preview.id)
                                    if (alreadyPicked) {
                                        picks.remove(preview.id)
                                        userDoc.update("userPicks", picks)
                                            .addOnSuccessListener {
                                                Toast.makeText(this, "Removed from Top Picks.", Toast.LENGTH_SHORT).show()
                                            }
                                            .addOnFailureListener {
                                                Toast.makeText(this, "Failed to update picks.", Toast.LENGTH_SHORT).show()
                                            }
                                    } else {
                                        picks.add(preview.id)
                                        userDoc.update("userPicks", picks)
                                            .addOnSuccessListener {
                                                Toast.makeText(this, "Added to Top Picks!", Toast.LENGTH_SHORT).show()
                                            }
                                            .addOnFailureListener {
                                                Toast.makeText(this, "Failed to update picks.", Toast.LENGTH_SHORT).show()
                                            }
                                    }
                                }
                            }
                        }
                    }
                    .show()
            }

        )
        rv.adapter = adapter

        db.collection("chats")
            .whereEqualTo("author", currentUserId)
            .orderBy("updateTimestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                val previews = snap.documents.mapNotNull { doc ->
                    doc.toObject(ChatProfile::class.java)?.let { p ->
                        val char1Id = p.characterIds.getOrNull(0)
                        val char2Id = p.characterIds.getOrNull(1)
                        val char1 = characterMap[char1Id]
                        val char2 = characterMap[char2Id]
                        ChatPreview(
                            id = p.id,
                            originalId = p.originalId,
                            rawJson = Gson().toJson(p),
                            title = p.title,
                            description = p.description,
                            avatar1Uri = char1?.avatarUri ?: "",
                            avatar2Uri = char2?.avatarUri ?: "",
                            avatar1ResId = R.drawable.icon_01,
                            avatar2ResId = R.drawable.icon_01,
                            rating = p.rating,
                            timestamp = p.timestamp,
                            author = p.author,
                            tags = p.tags,
                            sfwOnly = p.sfwOnly,
                            chatProfile = p
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
            .orderBy("lastTimestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                val previews = snap.documents.mapNotNull { doc ->
                    val cp = doc.toObject<CharacterProfile>() ?: return@mapNotNull null
                    CharacterPreview(
                        id = cp.id,
                        originalId = cp.originalId ?: cp.id,
                        name = cp.name,
                        summary = cp.summary.orEmpty(),
                        avatarUri = cp.avatarUri,
                        avatarResId = cp.avatarResId ?: R.drawable.icon_01,
                        author = cp.author,
                        rawJson = Gson().toJson(cp)
                    )
                }

                rv.adapter = CharacterPreviewAdapter(
                    this,
                    previews,
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
                                putExtra("CHAR_EDIT_ORIGINAL_ID", preview.originalId)
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
                    itemLayoutRes = R.layout.character_preview_item,
                    onLongClick = { preview ->
                        // THIS is where the dialog should go!
                        AlertDialog.Builder(this)
                            .setTitle(preview.name)
                            .setItems(arrayOf("Profile", "Edit", "Delete","Top Pick")) { _, which ->
                                when (which) {
                                    0 -> { // Profile
                                        // Launch CharacterProfileActivity
                                        startActivity(
                                            Intent(this, CharacterProfileActivity::class.java)
                                                .putExtra("characterId", preview.id)
                                        )
                                    }

                                    1 -> { // Edit
                                        FirebaseFirestore.getInstance().collection("characters")
                                            .document(preview.id)
                                            .get()
                                            .addOnSuccessListener { snapshot ->
                                                val fullProfile =
                                                    snapshot.toObject(CharacterProfile::class.java)
                                                if (fullProfile != null) {
                                                    startActivity(
                                                        Intent(
                                                            this,
                                                            CharacterCreationActivity::class.java
                                                        )
                                                            .putExtra("CHAR_EDIT_ID", preview.id)
                                                            .putExtra(
                                                                "CHAR_PROFILE_JSON",
                                                                Gson().toJson(fullProfile)
                                                            )
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

                                    2 -> { // Delete
                                        AlertDialog.Builder(this)
                                            .setTitle("Delete?")
                                            .setMessage("Are you sure you want to delete '${preview.name}'?")
                                            .setPositiveButton("Yes") { _, _ ->
                                                val firestore = FirebaseFirestore.getInstance()
                                                val storage = FirebaseStorage.getInstance()
                                                val characterId = preview.id
                                                val charFolderRef =
                                                    storage.reference.child("characters/$characterId/")

                                                deleteAllInFolder(charFolderRef) { success, error ->
                                                    if (success) {
                                                        firestore.collection("characters")
                                                            .document(characterId)
                                                            .delete()
                                                            .addOnSuccessListener {
                                                                startActivity(intent)
                                                                runOnUiThread {
                                                                    Toast.makeText(
                                                                        this,
                                                                        "Deleted.",
                                                                        Toast.LENGTH_SHORT
                                                                    ).show()
                                                                }
                                                            }
                                                            .addOnFailureListener { e ->
                                                                runOnUiThread {
                                                                    Toast.makeText(
                                                                        this,
                                                                        "Failed: ${e.message}",
                                                                        Toast.LENGTH_SHORT
                                                                    ).show()
                                                                }
                                                            }
                                                        startActivity(intent)
                                                    } else {
                                                        runOnUiThread {
                                                            Toast.makeText(
                                                                this,
                                                                "Failed to delete images: ${error?.message}",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        }
                                                    }
                                                }
                                            }
                                            .setNegativeButton("No", null)
                                            .show()
                                    }

                                    3 -> { // --- Top Pick (for character) ---
                                        val currentUser = FirebaseAuth.getInstance().currentUser
                                        if (currentUser == null) {
                                            Toast.makeText(
                                                this,
                                                "You must be signed in.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            return@setItems
                                        }
                                        val userId = currentUser.uid
                                        val userDoc = db.collection("users").document(userId)
                                        userDoc.get().addOnSuccessListener { snapshot ->
                                            val picks =
                                                snapshot.get("userPicks") as? MutableList<String>
                                                    ?: mutableListOf()
                                            val alreadyPicked = picks.contains(preview.id)
                                            if (alreadyPicked) {
                                                picks.remove(preview.id)
                                                userDoc.update("userPicks", picks)
                                                    .addOnSuccessListener {
                                                        Toast.makeText(
                                                            this,
                                                            "Removed from Top Picks.",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                    .addOnFailureListener {
                                                        Toast.makeText(
                                                            this,
                                                            "Failed to update picks.",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                            } else {
                                                picks.add(preview.id)
                                                userDoc.update("userPicks", picks)
                                                    .addOnSuccessListener {
                                                        Toast.makeText(
                                                            this,
                                                            "Added to Top Picks!",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                    .addOnFailureListener {
                                                        Toast.makeText(
                                                            this,
                                                            "Failed to update picks.",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                            }
                                        }

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
                    onClick = {/* ... */},
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
                                                        startActivity(intent)
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

    private fun showLorebooks(rv: RecyclerView) {
        rv.layoutManager = GridLayoutManager(this, 2)
        val userId = currentUserId ?: return

        db.collection("lorebooks")
            .whereEqualTo("creatorId", userId)
            .get()
            .addOnSuccessListener { snap ->
                val books = snap.documents.mapNotNull { doc ->
                    try {
                        doc.toObject(com.albirich.RealmsAI.models.Lorebook::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.e("CreationHub", "Failed to parse lorebook ${doc.id}: ${e.message}")
                        null // Skip this corrupted document so the app doesn't crash!
                    }
                }

                rv.adapter = com.albirich.RealmsAI.adapters.LorebookPreviewAdapter(
                    context = this,
                    lorebooks = books,
                    onClick = { book ->
                        startActivity(Intent(this, LorebookCreationActivity::class.java).apply {
                            putExtra("LOREBOOK_EDIT_ID", book.id)
                        })
                    },
                    onLongClick = { book ->
                        // THE NEW MENU FIX
                        AlertDialog.Builder(this)
                            .setTitle(book.title.ifBlank { "Lorebook Options" })
                            .setItems(arrayOf("Edit", "Delete")) { _, which ->
                                when (which) {
                                    0 -> { // Edit
                                        startActivity(Intent(this, LorebookCreationActivity::class.java).apply {
                                            putExtra("LOREBOOK_EDIT_ID", book.id)
                                        })
                                    }
                                    1 -> { // Delete
                                        AlertDialog.Builder(this)
                                            .setTitle("Delete '${book.title}'?")
                                            .setMessage("Are you sure you want to delete this Lorebook? This will remove it from any characters currently using it.")
                                            .setPositiveButton("Delete") { _, _ ->
                                                db.collection("lorebooks").document(book.id).delete()
                                                    .addOnSuccessListener {
                                                        Toast.makeText(this, "Lorebook deleted.", Toast.LENGTH_SHORT).show()
                                                        showLorebooks(rv) // Refresh list
                                                    }
                                            }
                                            .setNegativeButton("Cancel", null)
                                            .show()
                                    }
                                }
                            }
                            .show()
                    }
                )
            }
    }

    private fun showBackgrounds(rv: RecyclerView) {
        rv.layoutManager = GridLayoutManager(this, 2)
        if (currentUserId == null) return

        db.collection("areas")
            .whereEqualTo("creatorId", currentUserId)
            .get()
            .addOnSuccessListener { snap ->
                val previews = snap.documents.mapNotNull { doc ->
                    val area = doc.toObject(com.albirich.RealmsAI.models.Area::class.java) ?: return@mapNotNull null
                    AreaPreview(
                        id = area.id,
                        name = area.name,
                        publicInfo = area.publicInfo,
                        coverImageUri = area.locations.firstOrNull()?.uri,
                        locationCount = area.locations.size,
                        nsfw = area.nsfw
                    )
                }

                rv.adapter = com.albirich.RealmsAI.adapters.AreaPreviewAdapter(
                    context = this,
                    previews = previews,
                    onClick = { preview ->
                        // Standard Click -> Edit Area
                        startActivity(Intent(this, AreaCreationActivity::class.java).apply {
                            putExtra("AREA_EDIT_ID", preview.id)
                        })
                    },
                    onLongClick = { preview ->
                        // Long Click -> Popup Menu
                        AlertDialog.Builder(this)
                            .setTitle(preview.name)
                            .setItems(arrayOf("Edit", "Delete")) { _, which ->
                                when (which) {
                                    0 -> { // Edit
                                        startActivity(Intent(this, AreaCreationActivity::class.java).apply {
                                            putExtra("AREA_EDIT_ID", preview.id)
                                        })
                                    }
                                    1 -> { // Delete (Note: This is a basic DB delete. You might want to copy your recursive storage delete here later!)
                                        AlertDialog.Builder(this)
                                            .setTitle("Delete Area?")
                                            .setMessage("Are you sure you want to delete '${preview.name}'?")
                                            .setPositiveButton("Yes") { _, _ ->
                                                db.collection("areas").document(preview.id).delete()
                                                    .addOnSuccessListener {
                                                        Toast.makeText(this, "Deleted.", Toast.LENGTH_SHORT).show()
                                                        showBackgrounds(rv) // Refresh the list
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
    }

    private fun showCollections(rv: RecyclerView) {
        rv.layoutManager = LinearLayoutManager(this)
        val userId = currentUserId ?: return

        db.collection("users").document(userId).collection("collections")
            .get()
            .addOnSuccessListener { snap ->
                val collections = snap.toObjects(com.albirich.RealmsAI.models.CharacterCollection::class.java)

                rv.adapter = com.albirich.RealmsAI.adapters.CollectionAdapter(
                    collections = collections,
                    onEditClicked = { collection ->
                        // Launch the Character Selector in Edit Mode
                        val intent = Intent(this, CharacterSelectionActivity::class.java)
                        intent.putExtra("mode", "edit")
                        intent.putExtra("from", "collections")
                        intent.putExtra("collectionId", collection.id)
                        intent.putExtra("collectionName", collection.name)
                        intent.putStringArrayListExtra("preSelectedIds", ArrayList(collection.characterIds))
                        intent.putExtra("selectionCap", 20)
                        editCollectionLauncher.launch(intent) // Uses the edit launcher!
                    },
                    onDeleteClicked = {
                        // Reload the list after the adapter handles the deletion
                        showCollections(rv)
                    }
                )
            }
    }

    // --- Collection Launchers ---
    private val createCollectionLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val name = data?.getStringExtra("collectionName") ?: return@registerForActivityResult
            val selected = data.getStringArrayListExtra("selectedCharacterIds") ?: return@registerForActivityResult
            createNewCollection(name, selected)
        }
    }

    private val editCollectionLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val id = data?.getStringExtra("collectionId") ?: return@registerForActivityResult
            val selected = data.getStringArrayListExtra("selectedCharacterIds") ?: return@registerForActivityResult
            updateCollection(id, selected)
        }
    }

    // ==========================================
    //          COLLECTION ENGINE
    // ==========================================

    private fun promptNewCollectionName() {
        val input = EditText(this).apply {
            hint = "Collection Name"
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("Create New Collection")
            .setView(input)
            .setPositiveButton("Next") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val intent = Intent(this, CharacterSelectionActivity::class.java)
                    intent.putExtra("mode", "create")
                    intent.putExtra("from", "collections")
                    intent.putExtra("collectionName", name)
                    intent.putExtra("selectionCap", 20)
                    createCollectionLauncher.launch(intent) // Uses the new launcher!
                } else {
                    Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createNewCollection(name: String, characterIds: List<String>) {
        val userId = currentUserId ?: return
        val id = java.util.UUID.randomUUID().toString()
        val newCollection = com.albirich.RealmsAI.models.CharacterCollection(id, name, characterIds)

        db.collection("users").document(userId)
            .collection("collections").document(id)
            .set(newCollection)
            .addOnSuccessListener {
                Toast.makeText(this, "Collection created", Toast.LENGTH_SHORT).show()
                // Refresh the list directly
                val collectionsRv = findViewById<RecyclerView>(R.id.recyclerCollections)
                showCollections(collectionsRv)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to create collection", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateCollection(id: String, updatedIds: List<String>) {
        val userId = currentUserId ?: return

        db.collection("users").document(userId)
            .collection("collections").document(id)
            .update("characterIds", updatedIds)
            .addOnSuccessListener {
                Toast.makeText(this, "Collection updated", Toast.LENGTH_SHORT).show()
                // Refresh the list directly
                val collectionsRv = findViewById<RecyclerView>(R.id.recyclerCollections)
                showCollections(collectionsRv)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to update collection", Toast.LENGTH_SHORT).show()
            }
    }

    // Recursive function to delete all files in a folder and its subfolders
    fun deleteAllInFolder(folderRef: StorageReference, onComplete: (Boolean, Exception?) -> Unit) {
        folderRef.listAll()
            .addOnSuccessListener { listResult ->
                val deleteTasks = mutableListOf<com.google.android.gms.tasks.Task<Void>>()

                // Delete all files in this folder
                for (item in listResult.items) {
                    deleteTasks.add(item.delete())
                }

                // Recursively delete all files in all subfolders
                var subfolderTasks = 0
                var error: Exception? = null

                if (listResult.prefixes.isEmpty()) {
                    // No subfolders, just wait for file deletes
                    com.google.android.gms.tasks.Tasks.whenAllComplete(deleteTasks)
                        .addOnSuccessListener { onComplete(true, null) }
                        .addOnFailureListener { e -> onComplete(false, e) }
                } else {
                    // There are subfolders
                    for (prefix in listResult.prefixes) {
                        subfolderTasks++
                        deleteAllInFolder(prefix) { success, e ->
                            subfolderTasks--
                            if (!success && error == null) error = e
                            if (subfolderTasks == 0) {
                                // All subfolders done, now wait for files in this folder
                                com.google.android.gms.tasks.Tasks.whenAllComplete(deleteTasks)
                                    .addOnSuccessListener { onComplete(error == null, error) }
                                    .addOnFailureListener { e2 -> onComplete(false, e2) }
                            }
                        }
                    }
                }
            }
            .addOnFailureListener { e -> onComplete(false, e) }
    }


}
