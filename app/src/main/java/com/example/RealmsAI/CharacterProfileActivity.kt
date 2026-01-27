package com.example.RealmsAI

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.RealmsAI.models.CharacterProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import java.util.UUID

class CharacterProfileActivity : AppCompatActivity() {

    private lateinit var avatarView: ImageView
    private lateinit var nameView: TextView
    private lateinit var sfwBadge: ImageView
    private lateinit var authorView: TextView
    private lateinit var personalityView: TextView
    private lateinit var physicalView: TextView
    private lateinit var physicalDescView: TextView
    private lateinit var outfitsRecycler: RecyclerView
    private lateinit var sessionButton: Button
    private lateinit var saveButton: Button
    private lateinit var reportBtn: ImageButton
    private lateinit var ratingBar: RatingBar
    private lateinit var ratingStats: TextView
    private lateinit var rateButton: Button

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.character_profile)


        // 1. Get Character ID (pass as intent extra)
        val charId = intent.getStringExtra("characterId") ?: run {
            Toast.makeText(this, "No character specified!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        // 2. Find Views
        avatarView = findViewById(R.id.characterProfileAvatar)
        nameView = findViewById(R.id.characterProfileName)
        sfwBadge = findViewById(R.id.characterProfileSfwBadge)
        authorView = findViewById(R.id.characterProfileAuthor)
        personalityView = findViewById(R.id.characterProfilePersonality)
        physicalView = findViewById(R.id.characterProfilePhysical)
        physicalDescView = findViewById(R.id.characterProfilePhysicalDesc)
        outfitsRecycler = findViewById(R.id.characterProfileOutfitsRecycler)
        sessionButton = findViewById(R.id.characterProfileSessionButton)
        saveButton = findViewById(R.id.characterProfileSaveButton)
        reportBtn = findViewById<ImageButton>(R.id.characterProfileReportButton)
        ratingBar = findViewById(R.id.userRatingBar)
        ratingStats = findViewById(R.id.ratingStatsText)
        rateButton = findViewById(R.id.rateButton)


        // 3. Load Character Data
        loadCharacterProfile(charId)
    }

    private fun saveCharacterAsUser(character: CharacterProfile) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Toast.makeText(this, "Sign in first.", Toast.LENGTH_SHORT).show()
            return
        }
        // Optional: block copying private chars
        if (character.private == true) {
            Toast.makeText(this, "This character is private.", Toast.LENGTH_SHORT).show()
            return
        }

        val db = FirebaseFirestore.getInstance()

        // OPTIONAL: prevent duplicates (already saved this source)
        db.collection("characters")
            .whereEqualTo("author", userId)
            .whereEqualTo("sourceCharacterId", character.id)
            .limit(1)
            .get()
            .addOnSuccessListener { existing ->
                if (!existing.isEmpty) {
                    Toast.makeText(this, "You already saved this character.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Create new doc id and mirror it into the stored data
                val docRef = db.collection("characters").document()
                val nowRefId = docRef.id

                // Build a lean clone payload (avoid copying popularity/flags/etc.)
                val data = hashMapOf(
                    // identity/ownership
                    "id" to nowRefId,
                    "author" to userId,

                    // core fields you actually want (adjust to your model)
                    "name" to character.name,
                    "summary" to (character.summary ?: ""),
                    "personality" to (character.personality ?: ""),
                    "greeting" to (character.greeting ?: ""),
                    "avatarUri" to character.avatarUri,
                    "avatarResId" to (character.avatarResId ?: 0),

                    // whatever structured bits you need to carry over:
                    // "memories" to character.memories,
                    // "outfits" to character.outfits,
                    // etc. (include only what your UI needs)

                    // reset privacy/metrics
                    "private" to false,
                    "popularity" to 0,   // if this exists in your schema
                    // timestamps
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "lastUpdated" to com.google.firebase.firestore.FieldValue.serverTimestamp(),

                    // provenance (super useful for dedupe & “update from source”)
                    "sourceCharacterId" to character.id,
                    "sourceAuthorId" to character.author
                )

                docRef.set(data)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Character saved to your library!", Toast.LENGTH_SHORT).show()

                        // OPTIONAL: immediately let user add it to a collection
                        // promptAddToCollection(nowRefId)
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to save character.", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to check duplicates.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadCharacterProfile(characterId: String) {
        db.collection("characters").document(characterId).get()
            .addOnSuccessListener { doc ->
                val profile = doc.toObject(CharacterProfile::class.java)
                if (profile == null) {
                    Toast.makeText(this, "Character not found.", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                // Avatar
                if (!profile.avatarUri.isNullOrBlank()) {
                    Glide.with(this).load(profile.avatarUri).into(avatarView)
                } else {
                    avatarView.setImageResource(R.drawable.placeholder_avatar)
                }

                // Name
                nameView.text = profile.name

                // SFW Badge
                sfwBadge.visibility = if (profile.sfwOnly) View.VISIBLE else View.GONE

                // Author (as clickable link)
                val authorUserId = profile.author
                val usersRef = db.collection("users").document(authorUserId)
                usersRef.get().addOnSuccessListener { userDoc ->
                    val handle = userDoc.getString("handle")
                    val displayName = if (!handle.isNullOrBlank()) "@$handle" else "(unknown)"
                    authorView.text = "by $displayName"
                    // (Optional) set up click as before
                    authorView.setOnClickListener {
                        val intent = Intent(this, DisplayProfileActivity::class.java)
                        intent.putExtra("userId", authorUserId)
                        startActivity(intent)
                    }
                }.addOnFailureListener {
                    authorView.text = "by (unknown)"
                    authorView.setOnClickListener(null)
                }

                // Personality
                personalityView.text = "Personality: ${profile.personality}"

                // Physical info (combine for compactness)
                physicalView.text = buildString {
                    append("Age: ${profile.age}  ")
                    append("Height: ${profile.height}  ")
                    append("Weight: ${profile.weight}  ")
                    append("Gender: ${profile.gender}  ")
                    append("Eyes: ${profile.eyeColor}  ")
                    append("Hair: ${profile.hairColor}")
                }

                // Physical description
                physicalDescView.text = profile.physicalDescription

                // Outfits
                val visibleOutfits = (profile.outfits ?: emptyList())
                    .map { outfit ->
                        outfit.copy(
                            poseSlots = outfit.poseSlots
                                ?.filter { !it.nsfw }   // hide NSFW
                                ?.toMutableList()
                                ?: mutableListOf()
                        )
                    }
                    .filter { it.poseSlots?.isNotEmpty() == true }
                outfitsRecycler.layoutManager = LinearLayoutManager(this)
                outfitsRecycler.adapter = OutfitDisplayAdapter(visibleOutfits)
                outfitsRecycler.isNestedScrollingEnabled = false

                // Go to session
                sessionButton.setOnClickListener {
                    val gson = Gson()
                    val intent = Intent(this, SessionLandingActivity::class.java)
                    intent.putExtra("CHARACTER_ID", profile.id)
                    intent.putExtra("CHARACTER_PROFILES_JSON", gson.toJson(profile))
                    startActivity(intent)
                }
                saveButton.setOnClickListener {
                    if (profile.private == true){
                        val context = this
                        Toast.makeText(context, "This character is private.", Toast.LENGTH_SHORT).show()
                    } else {
                        saveCharacterAsUser(profile)
                    }
                }
                reportBtn.setOnClickListener {
                    showReportDialog(profile)
                }

                val avg = if (profile.ratingCount > 0) profile.ratingSum / profile.ratingCount else 0.0
                ratingBar.rating = avg.toFloat()
                ratingStats.text = "(${profile.ratingCount})"

                // CHECK IF I ALREADY RATED
                val myId = FirebaseAuth.getInstance().currentUser?.uid
                if (myId != null) {
                    val ratingDocId = "${myId}_${profile.id}"
                    db.collection("ratings").document(ratingDocId).get()
                        .addOnSuccessListener { doc ->
                            if (doc.exists()) {
                                val myRating = doc.getDouble("rating")?.toFloat() ?: 0f
                                rateButton.text = "Edit ${myRating.toInt()}★"
                            }
                        }
                }

                rateButton.setOnClickListener {
                    showRatingDialog(profile.id, "characters")
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load character.", Toast.LENGTH_SHORT).show()
                finish()
            }
    }
    private fun showRatingDialog(itemId: String, collectionName: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_rate_item, null) // See XML below
        val dialogRating = dialogView.findViewById<RatingBar>(R.id.dialogRatingBar)

        AlertDialog.Builder(this)
            .setTitle("Rate this Content")
            .setView(dialogView)
            .setPositiveButton("Submit") { _, _ ->
                val stars = dialogRating.rating
                if (stars > 0) {
                    submitRating(itemId, collectionName, stars.toDouble())
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun submitRating(itemId: String, collectionName: String, newStars: Double) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val ratingRef = db.collection("ratings").document("${userId}_${itemId}")
        val itemRef = db.collection(collectionName).document(itemId)

        db.runTransaction { transaction ->
            val ratingDoc = transaction.get(ratingRef)
            val itemDoc = transaction.get(itemRef)

            // Get current stats from the item
            val currentCount = itemDoc.getLong("ratingCount") ?: 0L
            val currentSum = itemDoc.getDouble("ratingSum") ?: 0.0

            if (ratingDoc.exists()) {
                // UPDATE EXISTING RATING
                val oldStars = ratingDoc.getDouble("rating") ?: 0.0
                val diff = newStars - oldStars

                transaction.update(itemRef, "ratingSum", currentSum + diff)
                transaction.update(ratingRef, "rating", newStars)

            } else {
                // NEW RATING
                transaction.update(itemRef, "ratingCount", currentCount + 1)
                transaction.update(itemRef, "ratingSum", currentSum + newStars)

                val data = hashMapOf(
                    "userId" to userId,
                    "itemId" to itemId,
                    "collection" to collectionName,
                    "rating" to newStars,
                    "timestamp" to com.google.firebase.Timestamp.now()
                )
                transaction.set(ratingRef, data)
            }
        }.addOnSuccessListener {
            Toast.makeText(this, "Rating saved!", Toast.LENGTH_SHORT).show()
            // Reload profile to see new stats
            loadCharacterProfile(itemId)
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Failed to rate: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }


    private fun showReportDialog(profile: CharacterProfile) {
        val reasons = arrayOf(
            "Prohibited Content (Underage/Illegal)",
            "Unmarked NSFW",
            "Spam / Low Quality",
            "Harassment / Hate Speech",
            "Other"
        )

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Report Character")
            .setSingleChoiceItems(reasons, -1) { dialog, which ->
                val reason = reasons[which]
                sendCharacterReport(profile, reason)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendCharacterReport(profile: CharacterProfile, reason: String) {
        val reporterId = FirebaseAuth.getInstance().currentUser?.uid ?: "Anonymous"

        val reportBody = """
        CHARACTER REPORT
        ----------------
        Reason: $reason
        Reporter ID: $reporterId
        Date: ${java.util.Date()}
        
        OFFENDING CONTENT:
        Character ID: ${profile.id}
        Name: ${profile.name}
        Author ID: ${profile.author}
        Summary: ${profile.summary}
    """.trimIndent()

        val emailIntent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf("realmsai.report@gmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, "CHARACTER REPORT: ${profile.name}")
            putExtra(Intent.EXTRA_TEXT, reportBody)
        }

        try {
            startActivity(Intent.createChooser(emailIntent, "Send Report..."))
        } catch (e: Exception) {
            Toast.makeText(this, "No email client found.", Toast.LENGTH_SHORT).show()
        }
    }
}
