package com.albirich.RealmsAI

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.albirich.RealmsAI.models.ChatProfile
import com.albirich.RealmsAI.models.CharacterProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson

class ChatProfileActivity : AppCompatActivity() {

    private lateinit var titleView: TextView
    private lateinit var sfwBadge: ImageView
    private lateinit var authorView: TextView
    private lateinit var originalAuthorView: TextView
    private lateinit var descriptionView: TextView
    private lateinit var charactersRecycler: RecyclerView
    private lateinit var sessionButton: Button
    private lateinit var reportBtn: Button
    private lateinit var shareBtn: ImageButton

    private val db = FirebaseFirestore.getInstance()
    private val characters = mutableListOf<CharacterProfile>()
    private lateinit var characterAdapter: CharacterChipAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_profile)

        titleView = findViewById(R.id.chatProfileTitle)
        sfwBadge = findViewById(R.id.chatProfileSfwBadge)
        authorView = findViewById(R.id.chatProfileAuthor)
        originalAuthorView = findViewById(R.id.chatOriginalProfileAuthor)
        descriptionView = findViewById(R.id.chatProfileDescription)
        charactersRecycler = findViewById(R.id.charactersRecyclerView)
        sessionButton = findViewById(R.id.chatProfileSessionButton)
        reportBtn = findViewById(R.id.chatProfileReportButton)
        shareBtn = findViewById(R.id.chatProfileShareButton)
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

                // Find original author
                if (!chatProfile.originalId.isNullOrBlank() && chatProfile.id != chatProfile.originalId) {
                    db.collection("chats").document(chatProfile.originalId).get()
                        .addOnSuccessListener { doc ->
                            val originalChatProfile = doc.toObject(ChatProfile::class.java)
                            val authorId = originalChatProfile?.author ?: return@addOnSuccessListener
                            db.collection("users").document(authorId).get()
                                .addOnSuccessListener { userDoc ->
                                    val handle = userDoc.getString("handle")
                                    originalAuthorView.visibility = VISIBLE
                                    originalAuthorView.text =
                                        "Original by ${if (!handle.isNullOrBlank()) "@$handle" else "(unknown)"}"
                                    originalAuthorView.setOnClickListener {
                                        if (originalChatProfile.private == false) {
                                            val intent = Intent(this, ChatProfileActivity::class.java)
                                            intent.putExtra("chatId", originalChatProfile.id)
                                            startActivity(intent)
                                        } else {
                                            Toast.makeText(this, "Original chat is set to Private.", Toast.LENGTH_SHORT).show()
                                            val intent = Intent(this, DisplayProfileActivity::class.java)
                                            intent.putExtra("userId", originalChatProfile.author)
                                            startActivity(intent)
                                        }
                                    }
                                }.addOnFailureListener {
                                    originalAuthorView.text = "Original by (unknown)"
                                    originalAuthorView.setOnClickListener(null)
                                }
                        }.addOnFailureListener {
                            originalAuthorView.text = "Original chat not found"
                            originalAuthorView.setOnClickListener(null)
                        }
                }

                // Session Button - NOW we have chatProfile, so set it here!
                sessionButton.setOnClickListener {
                    val intent = Intent(this, SessionLandingActivity::class.java)
                    intent.putExtra("CHAT_ID", chatProfile.id)
                    intent.putExtra("CHAT_PROFILE_JSON", Gson().toJson(chatProfile))
                    startActivity(intent)
                }

                titleView.text = chatProfile.title

                // --- REPORT BUTTON LOGIC ---
                if (chatProfile.isUnderReview) {
                    reportBtn.text = "⚠️ Under Moderation"
                    reportBtn.isEnabled = false
                    reportBtn.setBackgroundColor(android.graphics.Color.DKGRAY)
                } else {
                    reportBtn.text = "Report"
                    reportBtn.isEnabled = true
                    reportBtn.setOnClickListener {
                        showReportDialog(chatProfile)
                    }
                }

                // --- SHARE BUTTON LOGIC ---
                shareBtn.setOnClickListener {
                    val shareUrl = "https://realmsai.net/chat-profile/$chatId"

                    // 1. Auto-copy to Clipboard
                    val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("RealmsAI Chat Link", shareUrl)
                    clipboard.setPrimaryClip(clip)

                    Toast.makeText(this, "Link copied to clipboard!", Toast.LENGTH_SHORT).show()

                    // 2. Open the Android Share Sheet (Messages, Discord, Twitter, etc.)
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, "Check out this Chat on RealmsAI!\n$shareUrl")
                        type = "text/plain"
                    }
                    startActivity(Intent.createChooser(sendIntent, "Share Chat"))
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

        // THE FIX: Filter out any blank IDs to prevent segment crash
        val validIds = characterIds.filter { it.isNotBlank() }

        if (validIds.isEmpty()) return

        validIds.forEach { charId ->
            db.collection("characters").document(charId).get()
                .addOnSuccessListener { doc ->
                    doc.toObject(CharacterProfile::class.java)?.let { charProfile ->
                        // Double-check we don't add the same char twice if the list had dupes
                        if (characters.none { it.id == charProfile.id }) {
                            characters.add(charProfile)
                            characterAdapter.notifyDataSetChanged()
                        }
                    }
                }
        }
    }

    private fun showReportDialog(profile: ChatProfile) {
        val context = this
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val reasons = arrayOf(
            "Prohibited Content",
            "Unmarked NSFW",
            "Spam / Low Quality",
            "Other"
        )
        val spinner = Spinner(context)
        spinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, reasons)
        layout.addView(spinner)

        val detailsInput = EditText(context).apply {
            hint = "Please provide details..."
            minLines = 3
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 30 }
        }
        layout.addView(detailsInput)

        AlertDialog.Builder(context)
            .setTitle("Report Chat")
            .setView(layout)
            .setPositiveButton("Submit") { dialog, _ ->
                val selectedReason = spinner.selectedItem.toString()
                val details = detailsInput.text.toString().trim()
                sendChatReport(profile, selectedReason, details)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendChatReport(profile: ChatProfile, reason: String, details: String) {
        val reporterId = FirebaseAuth.getInstance().currentUser?.uid
        if (reporterId == null) {
            Toast.makeText(this, "You must be logged in to report.", Toast.LENGTH_SHORT).show()
            return
        }

        val db = FirebaseFirestore.getInstance()

        // 1. Build the Report Payload
        val reportData = hashMapOf(
            "authorId" to profile.author,
            "details" to details,
            "reason" to reason,
            "reporterId" to reporterId,
            "status" to "pending",
            "targetId" to profile.id,
            "targetName" to profile.title,
            "targetType" to "chat",
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        reportBtn.text = "Submitting..."
        reportBtn.isEnabled = false

        // 2. Batch Write: Create report AND flag the chat
        val batch = db.batch()

        val newReportRef = db.collection("reports").document()
        batch.set(newReportRef, reportData)

        val chatRef = db.collection("chats").document(profile.id)
        batch.update(chatRef, "isUnderReview", true)

        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(this, "Report submitted.", Toast.LENGTH_LONG).show()
                reportBtn.text = "⚠️ Under Moderation"
                reportBtn.setBackgroundColor(android.graphics.Color.DKGRAY)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                reportBtn.text = "Report"
                reportBtn.isEnabled = true
            }
    }
}
