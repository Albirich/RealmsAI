package com.albirich.RealmsAI

import android.R.id.content
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintSet
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.albirich.RealmsAI.models.CharacterProfile
import com.albirich.RealmsAI.models.PoseSlot
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson

class CharacterProfileActivity : AppCompatActivity() {

    private lateinit var avatarView: ImageView
    private lateinit var nameView: TextView
    private lateinit var sfwBadge: ImageView
    private lateinit var authorView: TextView
    private lateinit var originalAuthorView: TextView
    private lateinit var personalityView: TextView
    private lateinit var notesView: TextView
    private lateinit var physicalView: TextView
    private lateinit var physicalDescView: TextView
    private lateinit var outfitSpinner: Spinner
    private lateinit var outfitsRecycler: RecyclerView
    private lateinit var sessionButton: Button
    private lateinit var saveButton: Button
    private lateinit var reportBtn: Button
    private lateinit var ratingBar: RatingBar
    private lateinit var ratingStats: TextView
    private lateinit var rateButton: Button
    private lateinit var shareBtn: ImageButton

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
        originalAuthorView = findViewById(R.id.characterOriginalProfileAuthor)
        personalityView = findViewById(R.id.characterProfilePersonality)
        notesView = findViewById(R.id.characterProfileNotes)
        physicalView = findViewById(R.id.characterProfilePhysical)
        physicalDescView = findViewById(R.id.characterProfilePhysicalDesc)
        outfitsRecycler = findViewById(R.id.characterProfileOutfitsRecycler)
        sessionButton = findViewById(R.id.characterProfileSessionButton)
        saveButton = findViewById(R.id.characterProfileSaveButton)
        reportBtn = findViewById(R.id.characterProfileReportButton)
        shareBtn = findViewById(R.id.characterProfileShareButton)
        ratingBar = findViewById(R.id.userRatingBar)
        ratingStats = findViewById(R.id.ratingStatsText)
        rateButton = findViewById(R.id.rateButton)
        outfitSpinner = findViewById(R.id.characterProfileOutfitSpinner)


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

                // Original Author (as clickable link)
                if (!profile.originalId.isNullOrBlank() && profile.id != profile.originalId) {
                    db.collection("characters").document(profile.originalId!!).get()
                        .addOnSuccessListener { doc ->
                            val originalCharacterProfile = doc.toObject(CharacterProfile::class.java)
                            val authorId = originalCharacterProfile?.author ?: return@addOnSuccessListener
                            db.collection("users").document(authorId).get()
                                .addOnSuccessListener { userDoc ->
                                    val handle = userDoc.getString("handle")
                                    originalAuthorView.visibility = VISIBLE
                                    originalAuthorView.text =
                                        "Original by ${if (!handle.isNullOrBlank()) "@$handle" else "(unknown)"}"
                                    originalAuthorView.setOnClickListener {
                                        if (!originalCharacterProfile.private) {
                                            val intent = Intent(this, CharacterProfileActivity::class.java)
                                            intent.putExtra("characterId", originalCharacterProfile.id)
                                            startActivity(intent)
                                        } else {
                                            Toast.makeText(this, "Original character is set to Private.", Toast.LENGTH_SHORT).show()
                                            val intent = Intent(this, DisplayProfileActivity::class.java)
                                            intent.putExtra("userId", originalCharacterProfile.author)
                                            startActivity(intent)
                                        }
                                    }
                                }.addOnFailureListener {
                                    originalAuthorView.text = "Original by (unknown)"
                                    originalAuthorView.setOnClickListener(null)
                                }

                        }.addOnFailureListener {
                            originalAuthorView.text = "Original character not found"
                            originalAuthorView.setOnClickListener(null)
                        }
                }


                val notesContent = if (profile.creatorNotes != null)
                {
                    "Creator Note: ${profile.creatorNotes}"
                }else{
                    ""
                }
                notesView.text = notesContent

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
                val allOutfits = profile.outfits ?: emptyList()

                if (allOutfits.isNotEmpty()) {
                    // 1. Setup Spinner
                    val outfitNames = allOutfits.map { it.name }
                    val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, outfitNames)
                    spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    outfitSpinner.adapter = spinnerAdapter

                    // 2. Setup RecyclerView as GRID
                    outfitsRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
                    outfitsRecycler.isNestedScrollingEnabled = false

                    // 3. Selection Listener
                    outfitSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                            val selectedOutfit = allOutfits[position]
                            // Filter NSFW if needed
                            val validPoses = selectedOutfit.poseSlots?.filter { !it.nsfw } ?: emptyList()

                            // Use our NEW inner adapter
                            outfitsRecycler.adapter = PoseGridAdapter(validPoses)
                        }
                        override fun onNothingSelected(parent: AdapterView<*>) {}
                    }
                } else {
                    findViewById<View>(R.id.outfitHeaderContainer).visibility = View.GONE
                    outfitsRecycler.visibility = View.GONE
                }
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
                // --- REPORT BUTTON LOGIC ---
                if (profile.isUnderReview) {
                    reportBtn.text = "⚠️ Under Moderation"
                    reportBtn.isEnabled = false
                    reportBtn.setBackgroundColor(android.graphics.Color.DKGRAY)
                } else {
                    reportBtn.text = "Report"
                    reportBtn.isEnabled = true
                    reportBtn.setOnClickListener {
                        showReportDialog(profile)
                    }
                }

                // --- SHARE BUTTON LOGIC ---
                shareBtn.setOnClickListener {
                    val shareUrl = "https://realmsai.net/character/$characterId"

                    // 1. Auto-copy to Clipboard
                    val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("RealmsAI Character Link", shareUrl)
                    clipboard.setPrimaryClip(clip)

                    Toast.makeText(this, "Link copied to clipboard!", Toast.LENGTH_SHORT).show()

                    // 2. Open the Android Share Sheet (Messages, Discord, Twitter, etc.)
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, "Check out this character on RealmsAI!\n$shareUrl")
                        type = "text/plain"
                    }
                    startActivity(Intent.createChooser(sendIntent, "Share Character"))
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
        val context = this
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        // 1. Create the Spinner for the Reason
        val reasons = arrayOf(
            "Prohibited Content (Underage/Illegal)",
            "Unmarked NSFW",
            "Spam / Low Quality",
            "Harassment / Hate Speech",
            "Other"
        )
        val spinner = Spinner(context)
        spinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, reasons)
        layout.addView(spinner)

        // 2. Create the EditText for Details
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
            .setTitle("Report Character")
            .setView(layout)
            .setPositiveButton("Submit") { dialog, _ ->
                val selectedReason = spinner.selectedItem.toString()
                val details = detailsInput.text.toString().trim()
                sendCharacterReport(profile, selectedReason, details)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendCharacterReport(profile: CharacterProfile, reason: String, details: String) {
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
            "targetName" to profile.name,
            "targetType" to "character",
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        // 2. Disable the button immediately so they can't spam click it
        reportBtn.text = "Submitting..."
        reportBtn.isEnabled = false

        // 3. Batch Write: Create the report AND update the character
        val batch = db.batch()

        val newReportRef = db.collection("reports").document()
        batch.set(newReportRef, reportData)

        val characterRef = db.collection("characters").document(profile.id)
        batch.update(characterRef, "isUnderReview", true)

        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(this, "Report submitted successfully.", Toast.LENGTH_LONG).show()
                // Update the UI
                reportBtn.text = "⚠️ Under Moderation"
                reportBtn.setBackgroundColor(android.graphics.Color.DKGRAY)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to submit report: ${e.message}", Toast.LENGTH_LONG).show()
                // Re-enable if it failed
                reportBtn.text = "Report"
                reportBtn.isEnabled = true
            }
    }

    inner class PoseGridAdapter(private val poses: List<PoseSlot>) :
        RecyclerView.Adapter<PoseGridAdapter.GridHolder>() {

        inner class GridHolder(view: View) : RecyclerView.ViewHolder(view) {
            val image: ImageView = view.findViewById(R.id.pose_image)
            val label: TextView = view.findViewById(R.id.pose_name)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GridHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.pose_list_item, parent, false) // Uses our new XML
            return GridHolder(view)
        }

        override fun onBindViewHolder(holder: GridHolder, position: Int) {
            val pose = poses[position]

            holder.label.text = pose.name

            if (!pose.uri.isNullOrBlank()) {
                Glide.with(this@CharacterProfileActivity)
                    .load(pose.uri)
                    .placeholder(R.drawable.placeholder_avatar)
                    .into(holder.image)
            } else {
                holder.image.setImageResource(R.drawable.placeholder_avatar)
            }

            // Optional: Add click listener here to view full screen
        }

        override fun getItemCount() = poses.size
    }
}
