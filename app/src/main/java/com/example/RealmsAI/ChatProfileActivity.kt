package com.example.RealmsAI

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.RealmsAI.models.ChatProfile
import com.example.RealmsAI.models.CharacterProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson

class ChatProfileActivity : AppCompatActivity() {

    private lateinit var titleView: TextView
    private lateinit var sfwBadge: ImageView
    private lateinit var authorView: TextView
    private lateinit var descriptionView: TextView
    private lateinit var charactersRecycler: RecyclerView
    private lateinit var sessionButton: Button
    private lateinit var reportBtn: ImageButton

    private val db = FirebaseFirestore.getInstance()
    private val characters = mutableListOf<CharacterProfile>()
    private lateinit var characterAdapter: CharacterChipAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_profile)

        titleView = findViewById(R.id.chatProfileTitle)
        sfwBadge = findViewById(R.id.chatProfileSfwBadge)
        authorView = findViewById(R.id.chatProfileAuthor)
        descriptionView = findViewById(R.id.chatProfileDescription)
        charactersRecycler = findViewById(R.id.charactersRecyclerView)
        sessionButton = findViewById(R.id.chatProfileSessionButton)
        reportBtn = findViewById(R.id.chatProfileReportButton)

        characterAdapter = CharacterChipAdapter(characters)
        charactersRecycler.adapter = characterAdapter
        charactersRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        // Get chat ID from intent
        val chatId = intent.getStringExtra("chatId")
        if (chatId.isNullOrBlank()) {
            Toast.makeText(this, "No chat specified.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadChatProfile(chatId)
    }

    private fun loadChatProfile(chatId: String) {
        db.collection("chats").document(chatId).get()
            .addOnSuccessListener { doc ->
                val chatProfile = doc.toObject(ChatProfile::class.java)
                if (chatProfile == null) {
                    Toast.makeText(this, "Chat not found.", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                titleView.text = chatProfile.title

                // SFW Badge
                sfwBadge.visibility = if (chatProfile.sfwOnly) View.VISIBLE else View.GONE

                // Description
                descriptionView.text = chatProfile.description

                // Author (fetch handle and set click)
                db.collection("users").document(chatProfile.author).get()
                    .addOnSuccessListener { userDoc ->
                        val handle = userDoc.getString("handle")
                        authorView.text = "by ${if (!handle.isNullOrBlank()) "@$handle" else "(unknown)"}"
                        authorView.setOnClickListener {
                            val intent = Intent(this, DisplayProfileActivity::class.java)
                            intent.putExtra("userId", chatProfile.author)
                            startActivity(intent)
                        }
                    }.addOnFailureListener {
                        authorView.text = "by (unknown)"
                        authorView.setOnClickListener(null)
                    }

                // Session Button - NOW we have chatProfile, so set it here!
                sessionButton.setOnClickListener {
                    val intent = Intent(this, SessionLandingActivity::class.java)
                    intent.putExtra("CHAT_ID", chatProfile.id)
                    intent.putExtra("CHAT_PROFILE_JSON", Gson().toJson(chatProfile))
                    startActivity(intent)
                }

                reportBtn.setOnClickListener {
                    showReportDialog(chatProfile)
                }

                // Load characters
                loadCharacterChips(chatProfile.characterIds)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load chat.", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun loadCharacterChips(characterIds: List<String>) {
        characters.clear()
        characterAdapter.notifyDataSetChanged()
        if (characterIds.isEmpty()) return

        characterIds.forEach { charId ->
            db.collection("characters").document(charId).get()
                .addOnSuccessListener { doc ->
                    doc.toObject(CharacterProfile::class.java)?.let { charProfile ->
                        characters.add(charProfile)
                        characterAdapter.notifyDataSetChanged()
                    }
                }
        }
    }

    private fun showReportDialog(profile: ChatProfile) {
        val reasons = arrayOf(
            "Prohibited Content",
            "Unmarked NSFW",
            "Spam / Low Quality",
            "Other"
        )

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Report Chat")
            .setSingleChoiceItems(reasons, -1) { dialog, which ->
                val reason = reasons[which]
                sendChatReport(profile, reason)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendChatReport(profile: ChatProfile, reason: String) {
        val reporterId = FirebaseAuth.getInstance().currentUser?.uid ?: "Anonymous"

        val reportBody = """
        CHAT REPORT
        ----------------
        Reason: $reason
        Reporter ID: $reporterId
        
        OFFENDING CONTENT:
        Chat ID: ${profile.id}
        Title: ${profile.title}
        Author ID: ${profile.author}
        Description: ${profile.description}
    """.trimIndent()

        val emailIntent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf("realmsai.report@gmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, "CHAT REPORT: ${profile.title}")
            putExtra(Intent.EXTRA_TEXT, reportBody)
        }

        try {
            startActivity(Intent.createChooser(emailIntent, "Send Report..."))
        } catch (e: Exception) {
            Toast.makeText(this, "No email client found.", Toast.LENGTH_SHORT).show()
        }
    }
}
