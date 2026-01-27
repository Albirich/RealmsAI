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
import com.google.android.gms.tasks.Tasks
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
import com.example.RealmsAI.SessionManager.findSessionForUser
import com.example.RealmsAI.models.CharacterPreview


class CreatedListActivity : BaseActivity() {

    private val db = Firebase.firestore
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    private var characterMap: Map<String, CharacterProfile> = emptyMap()

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
        db.collection("characters")
            .get()
            .addOnSuccessListener { snap ->
                characterMap = snap.documents
                    .mapNotNull { it.toObject(CharacterProfile::class.java) }
                    .associateBy { it.id }
                // Now that characterMap is loaded, show chats
                showChats(chatsRv)
                // The rest don't need characterMap
                showCharacters(charactersRv)
                showPersonas(personasRv)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Could not load characters: ${e.message}", Toast.LENGTH_SHORT).show()
                // Still show the other lists (avatars may be missing for chats)
                showChats(chatsRv)
                showCharacters(charactersRv)
                showPersonas(personasRv)
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
            .orderBy("timestamp", Query.Direction.DESCENDING)
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
            .get()
            .addOnSuccessListener { snap ->
                val previews = snap.documents.mapNotNull { doc ->
                    val cp = doc.toObject<CharacterProfile>() ?: return@mapNotNull null
                    CharacterPreview(
                        id = cp.id,
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
