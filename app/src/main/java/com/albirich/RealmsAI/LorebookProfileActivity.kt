package com.albirich.RealmsAI

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.albirich.RealmsAI.models.LoreEntry
import com.albirich.RealmsAI.models.Lorebook
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

class LorebookProfileActivity : AppCompatActivity() {

    private lateinit var backgroundImageView: ImageView
    private lateinit var titleView: TextView
    private lateinit var authorView: TextView
    private lateinit var originalAuthorView: TextView
    private lateinit var descView: TextView
    private lateinit var entriesRecycler: RecyclerView
    private lateinit var saveButton: Button

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lorebook_profile)

        val lorebookId = intent.getStringExtra("LOREBOOK_ID") ?: run {
            Toast.makeText(this, "No lorebook specified!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        backgroundImageView = findViewById(R.id.backgroundImageView)
        titleView = findViewById(R.id.lorebookProfileTitle)
        authorView = findViewById(R.id.lorebookProfileAuthor)
        originalAuthorView = findViewById(R.id.lorebookOriginalAuthor)
        descView = findViewById(R.id.lorebookProfileDesc)
        entriesRecycler = findViewById(R.id.lorebookEntriesRecycler)
        saveButton = findViewById(R.id.lorebookProfileSaveButton)

        loadLorebookProfile(lorebookId)
    }

    private fun loadLorebookProfile(lorebookId: String) {
        db.collection("lorebooks").document(lorebookId).get()
            .addOnSuccessListener { doc ->

                // USE YOUR CUSTOM PARSER HERE
                val profile = doc.toLorebookSafe()

                if (profile == null) {
                    Toast.makeText(this, "Lorebook not found.", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                // Header Info
                titleView.text = profile.title
                descView.text = profile.description.ifBlank { "No description provided." }

                // Set Cover Image to Background (if they provided one)
                if (!profile.coverUri.isNullOrBlank()) {
                    Glide.with(this)
                        .load(profile.coverUri)
                        .centerCrop()
                        .into(backgroundImageView)
                }

                // --- CURRENT AUTHOR LOOKUP ---
                val authorUserId = profile.creatorId
                db.collection("users").document(authorUserId).get().addOnSuccessListener { userDoc ->
                    val handle = userDoc.getString("handle")
                    authorView.text = "by ${if (!handle.isNullOrBlank()) "@$handle" else "(unknown)"}"
                    authorView.setOnClickListener {
                        startActivity(Intent(this, DisplayProfileActivity::class.java).putExtra("userId", authorUserId))
                    }
                }.addOnFailureListener {
                    authorView.text = "by (unknown)"
                }

                // --- ORIGINAL AUTHOR LOOKUP ---
                if (!profile.originalId.isNullOrBlank() && profile.id != profile.originalId) {
                    db.collection("lorebooks").document(profile.originalId!!).get()
                        .addOnSuccessListener { origDoc ->
                            val originalBook = origDoc.toObject(Lorebook::class.java)
                            val origAuthorId = originalBook?.creatorId ?: return@addOnSuccessListener

                            db.collection("users").document(origAuthorId).get()
                                .addOnSuccessListener { userDoc ->
                                    val handle = userDoc.getString("handle")
                                    originalAuthorView.visibility = View.VISIBLE
                                    originalAuthorView.text = "Original by ${if (!handle.isNullOrBlank()) "@$handle" else "(unknown)"}"

                                    originalAuthorView.setOnClickListener {
                                        if (originalBook.private) {
                                            Toast.makeText(this, "Original lorebook is set to Private.", Toast.LENGTH_SHORT).show()
                                            startActivity(Intent(this, DisplayProfileActivity::class.java).putExtra("userId", origAuthorId))
                                        } else {
                                            startActivity(Intent(this, LorebookProfileActivity::class.java).putExtra("LOREBOOK_ID", originalBook.id))
                                        }
                                    }
                                }
                        }
                }

                // Entries Recycler
                entriesRecycler.layoutManager = LinearLayoutManager(this)
                entriesRecycler.adapter = ReadOnlyLoreAdapter(profile.entries)

                Toast.makeText(this@LorebookProfileActivity, "Loaded all ${profile.entries.size} entries!", Toast.LENGTH_LONG).show()

                // Save Action
                saveButton.setOnClickListener {
                    if (profile.private) {
                        Toast.makeText(this, "This Lorebook is private.", Toast.LENGTH_SHORT).show()
                    } else {
                        saveLorebookAsUser(profile)
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load lorebook.", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun saveLorebookAsUser(book: Lorebook) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Toast.makeText(this, "Sign in first.", Toast.LENGTH_SHORT).show()
            return
        }

        val origId = book.originalId ?: book.id

        val clonedBook = book.copy(
            id = UUID.randomUUID().toString(),
            originalId = origId,
            creatorId = userId,
            private = true,
            timestamp = Timestamp.now()
        )

        db.collection("lorebooks").document(clonedBook.id).set(clonedBook)
            .addOnSuccessListener {
                Toast.makeText(this, "Lorebook saved to your Creation Hub!", Toast.LENGTH_SHORT).show()
                saveButton.text = "Saved ✓"
                saveButton.setBackgroundColor(android.graphics.Color.DKGRAY)
                saveButton.isEnabled = false
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save lorebook.", Toast.LENGTH_SHORT).show()
            }
    }

    // A lightweight adapter for displaying the read-only entries
    inner class ReadOnlyLoreAdapter(private val entries: List<LoreEntry>) :
        RecyclerView.Adapter<ReadOnlyLoreAdapter.EntryHolder>() {

        inner class EntryHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nameText: TextView = view.findViewById(R.id.entryNameText)
            val contentText: TextView = view.findViewById(R.id.entryContentText)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntryHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_lore_entry_readonly, parent, false)
            return EntryHolder(view)
        }

        override fun onBindViewHolder(holder: EntryHolder, position: Int) {
            val entry = entries[position]
            holder.nameText.text = entry.name
            holder.contentText.text = entry.content
        }

        override fun getItemCount() = entries.size
    }

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
                    // IF THE WEB SAVED IT AS A STRING, SPLIT IT INTO A LIST!
                    is String -> rawKeys.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    // IF ANDROID SAVED IT AS A LIST, KEEP IT AS A LIST!
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
            android.util.Log.e("LorebookParser", "Critical fail parsing Lorebook: ${this.id}", e)
            null
        }
    }
}