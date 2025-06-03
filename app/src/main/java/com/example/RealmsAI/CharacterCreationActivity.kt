package com.example.RealmsAI

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.RealmsAI.models.CharacterProfile
import com.example.RealmsAI.models.Outfit
import com.example.RealmsAI.models.Relationship
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream

class CharacterCreationActivity : AppCompatActivity() {
    // UI
    private lateinit var avatarView: ImageView
    private lateinit var relationshipBtn: Button
    private lateinit var wardrobeButton: MaterialButton
    private lateinit var bgButton: ImageButton
    private lateinit var bgRecycler: RecyclerView
    private lateinit var bubbleColorSpinner: Spinner
    private lateinit var textColorSpinner: Spinner
    private lateinit var submitBtn: MaterialButton

    // EditTexts
    private lateinit var nameEt: EditText
    private lateinit var bioEt: EditText
    private lateinit var personalityEt: EditText
    private lateinit var privateDescEt: EditText
    private lateinit var ageEt: EditText
    private lateinit var heightEt: EditText
    private lateinit var weightEt: EditText
    private lateinit var eyeColorEt: EditText
    private lateinit var hairColorEt: EditText
    private lateinit var physicalDescEt: EditText
    private lateinit var genderEt: EditText
    private lateinit var backstoryEt: EditText
    private lateinit var greetingEt: EditText

    // State
    private var progressDialog: AlertDialog? = null
    private var avatarUri: Uri? = null             // Only local uri if user picks new
    private var avatarChanged = false              // Tracks if avatar was re-picked
    private var originalAvatarUrl: String? = null  // Download url from firestore
    private var selectedBgUri: Uri? = null
    private var selectedBgResId: Int? = null
    private var outfitsList: List<Outfit> = emptyList()
    private var relationships: List<Relationship> = emptyList()
    private val colorOptions = listOf(
        "White" to "#FFFFFF",
        "Yellow" to "#FFEB3B",
        "Orange" to "#FF9800",
        "Blue" to "#2196F3",
        "Pink" to "#E91E63",
        "Green" to "#4CAF50",
        "Black" to "#000000"
    )

    // Launcher
    private lateinit var wardrobeLauncher: ActivityResultLauncher<Intent>
    private lateinit var avatarPicker: ActivityResultLauncher<String>
    private lateinit var bgPicker: ActivityResultLauncher<String>
    private lateinit var sfwSwitch: Switch
    companion object {
        private const val RELATIONSHIP_REQ_CODE = 5001
        const val EXTRA_OUTFITS_JSON = "EXTRA_OUTFITS_JSON"
    }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private val presetBackgrounds = listOf(
        R.drawable.bg_beach, R.drawable.bg_castle, R.drawable.bg_comedy_club,
        R.drawable.bg_forest, R.drawable.bg_mountain_path,
        R.drawable.bg_newsroom, R.drawable.bg_office,
        R.drawable.bg_space, R.drawable.bg_woods
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_character)

        // Find Views
        avatarView = findViewById(R.id.avatarImageView)
        relationshipBtn = findViewById(R.id.charrelationshipBtn)
        wardrobeButton = findViewById(R.id.wardrobeButton)
        bgButton = findViewById(R.id.backgroundButton)
        bgRecycler = findViewById(R.id.backgroundRecycler)
        bubbleColorSpinner = findViewById(R.id.bubbleColorSpinner)
        textColorSpinner = findViewById(R.id.textColorSpinner)
        submitBtn = findViewById(R.id.charSubmitButton)
        nameEt = findViewById(R.id.characterNameInput)
        bioEt = findViewById(R.id.etSummary)
        personalityEt = findViewById(R.id.characterPersonalityInput)
        privateDescEt = findViewById(R.id.characterprivateDescriptionInput)
        ageEt = findViewById(R.id.ageEditText)
        heightEt = findViewById(R.id.heightEditText)
        weightEt = findViewById(R.id.weightEditText)
        eyeColorEt = findViewById(R.id.eyeColorEditText)
        hairColorEt = findViewById(R.id.hairColorEditText)
        physicalDescEt = findViewById(R.id.physicalDescriptionEditText)
        genderEt = findViewById(R.id.genderEditText)
        backstoryEt = findViewById(R.id.backstoryEditText)
        greetingEt = findViewById(R.id.characterGreetingInput)
        sfwSwitch = findViewById(R.id.sfwSwitch)


        // Color spinners show names but store hex
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, colorOptions.map { it.first })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        bubbleColorSpinner.adapter = adapter
        textColorSpinner.adapter = adapter

        // Preset avatar
        avatarView.setImageResource(R.drawable.placeholder_avatar)

        // Avatar Picker
        avatarPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                avatarUri = uri
                avatarChanged = true
                avatarView.setImageURI(uri)
            }
        }
        avatarView.setOnClickListener { avatarPicker.launch("image/*") }

        // Wardrobe Launcher
        wardrobeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                result.data?.getStringExtra(EXTRA_OUTFITS_JSON)?.let { outfitsJson ->
                    outfitsList = Gson().fromJson(outfitsJson, Array<Outfit>::class.java).toList()
                }
            }
        }
        wardrobeButton.setOnClickListener {
            val intent = Intent(this, WardrobeActivity::class.java)
            val outfitsJson = Gson().toJson(outfitsList)
            intent.putExtra(EXTRA_OUTFITS_JSON, outfitsJson)
            wardrobeLauncher.launch(intent)
        }

        // Background Picker
        bgPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                selectedBgUri = it
                selectedBgResId = null
                bgButton.setImageURI(it)
            }
        }
        bgButton.setOnClickListener { bgPicker.launch("image/*") }
        bgRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        bgRecycler.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = presetBackgrounds.size
            override fun onCreateViewHolder(parent: ViewGroup, vt: Int) =
                object : RecyclerView.ViewHolder(ImageView(parent.context).apply {
                    val size = (64 * resources.displayMetrics.density).toInt()
                    layoutParams = ViewGroup.LayoutParams(size, size)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setPadding(8, 8, 8, 8)
                }) {}
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, i: Int) {
                val res = presetBackgrounds[i]
                (holder.itemView as ImageView).apply {
                    setImageResource(res)
                    setOnClickListener {
                        selectedBgResId = res
                        selectedBgUri = null
                        bgButton.setImageResource(res)
                    }
                }
            }
        }

        // Relationships button
        relationshipBtn.setOnClickListener {
            val intent = Intent(this, CharacterRelationshipActivity::class.java)
            intent.putExtra("RELATIONSHIPS_JSON", Gson().toJson(relationships))
            startActivityForResult(intent, RELATIONSHIP_REQ_CODE)
        }

        // --- Expand/Collapse Physical Info Section ---
        val physicalHeader: LinearLayout = findViewById(R.id.physicalInfoHeader)
        val physicalSection: LinearLayout = findViewById(R.id.physicalInfoSection)
        val physicalToggle: ImageView = findViewById(R.id.physicalInfoToggle)

        physicalHeader.setOnClickListener {
            val isVisible = physicalSection.visibility == View.VISIBLE
            physicalSection.visibility = if (isVisible) View.GONE else View.VISIBLE
            physicalToggle.setImageResource(
                if (isVisible) R.drawable.ic_expand_more else R.drawable.ic_expand_less
            )
        }

        // --- Edit support: Fill from profile if present ---
        val editCharId = intent.getStringExtra("CHAR_EDIT_ID")
        val charJson = intent.getStringExtra("CHAR_PROFILE_JSON")
        if (editCharId != null && !charJson.isNullOrBlank()) {
            val profile = Gson().fromJson(charJson, CharacterProfile::class.java)
            fillFormFromProfile(profile)
        }

        // Submit
        submitBtn.setOnClickListener {
            val name = nameEt.text.toString().trim()
            val bio = bioEt.text.toString().trim()
            val personality = personalityEt.text.toString().trim()
            val privateDesc = privateDescEt.text.toString().trim()
            val backstory = backstoryEt.text.toString().trim()
            val age = ageEt.text.toString().toFloatOrNull() ?: 0.0f
            val height = heightEt.text.toString().trim()
            val weight = weightEt.text.toString().trim()
            val eyeColor = eyeColorEt.text.toString().trim()
            val hairColor = hairColorEt.text.toString().trim()
            val physicalDesc = physicalDescEt.text.toString().trim()
            val gender = genderEt.text.toString().trim()
            val greeting = greetingEt.text.toString().trim()
            val bubbleColor = colorOptions[bubbleColorSpinner.selectedItemPosition].second
            val textColor = colorOptions[textColorSpinner.selectedItemPosition].second

            if (name.isEmpty()) return@setOnClickListener toast("Name required")
            if (originalAvatarUrl.isNullOrBlank() && !avatarChanged) return@setOnClickListener toast("Pick an avatar")

            showProgressDialog()
            saveCharacterAndReturnToHub(
                editCharId,
                name, bio, personality, privateDesc, backstory, greeting,
                age, height, weight, eyeColor, hairColor, physicalDesc, gender,
                bubbleColor, textColor
            )
        }
    }

    private fun saveCharacterAndReturnToHub(
        editCharId: String?,
        name: String,
        summary: String,
        personality: String,
        privateDescription: String,
        backstory: String,
        greeting: String,
        age: Float,
        height: String,
        weight: String,
        eyeColor: String,
        hairColor: String,
        physicalDescription: String,
        gender: String,
        bubbleColor: String,
        textColor: String
    ) {
        val charId = editCharId ?: System.currentTimeMillis().toString()
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val storage = FirebaseStorage.getInstance().reference
        val firestore = FirebaseFirestore.getInstance()

        // 1. Upload avatar if changed
        fun onAvatarUploaded(avatarUrl: String) {
            // 2. Upload any local pose images (skip those with http/https)
            val poseTasks = outfitsList.flatMap { outfit ->
                outfit.poseSlots.mapNotNull { poseSlot ->
                    val poseName = poseSlot.name.trim()
                    val uriStr = poseSlot.uri?.trim() ?: ""
                    if (poseName.isBlank() || uriStr.isBlank() || uriStr.startsWith("http")) return@mapNotNull null
                    val fileUri = Uri.parse(uriStr)
                    val ext = File(fileUri.path ?: "").extension.ifBlank { "jpg" }
                    val ref = storage.child("characters/$charId/poses/${outfit.name}/$poseName.$ext")
                    Log.d("CharCreation", "Uploading $fileUri to $ref")
                    val uploadTask = ref.putFile(fileUri)
                        .continueWithTask { t ->
                            if (!t.isSuccessful) throw t.exception!!
                            ref.downloadUrl
                        }
                        .continueWith { t ->
                            Triple(outfit.name, poseName, t.result.toString())
                        }
                    uploadTask
                }
            }

            com.google.android.gms.tasks.Tasks.whenAllSuccess<Any>(poseTasks)
                .addOnSuccessListener { results ->
                    val poseTriples = results.filterIsInstance<Triple<String, String, String>>()
                    // Map of outfitName -> Map<poseName, url>
                    val outfitsMap = mutableMapOf<String, MutableMap<String, String>>()
                    for ((outfitName, poseName, url) in poseTriples) {
                        val poseMap = outfitsMap.getOrPut(outfitName) { mutableMapOf() }
                        poseMap[poseName] = url
                    }
                    // Update poseSlots with uploaded URLs (leave remote URLs as-is)
                    val updatedOutfits = outfitsList.map { outfit ->
                        val poseMap = outfitsMap[outfit.name] ?: emptyMap()
                        val updatedPoseSlots = outfit.poseSlots.map { poseSlot ->
                            val uploadedUrl = poseMap[poseSlot.name]
                            if (uploadedUrl != null) poseSlot.copy(uri = uploadedUrl) else poseSlot
                        }.toMutableList()
                        outfit.copy(poseSlots = updatedPoseSlots)
                    }

                    val age = ageEt.text.toString().toFloatOrNull() ?: 0f
                    val isSfwOnly = if (age < 18) true else sfwSwitch.isChecked

                    val charData: Map<String, Any?> = mapOf(
                        "id" to charId,
                        "name" to name,
                        "summary" to summary,
                        "personality" to personality,
                        "privateDescription" to privateDescription,
                        "greeting" to greeting,
                        "backstory" to backstory,
                        "age" to age,
                        "height" to height,
                        "weight" to weight,
                        "gender" to gender,
                        "physicalDescription" to physicalDescription,
                        "eyeColor" to eyeColor,
                        "hairColor" to hairColor,
                        "bubbleColor" to bubbleColor,
                        "textColor" to textColor,
                        "author" to currentUserId,
                        "tags" to emptyList<String>(),
                        "avatarUri" to avatarUrl,
                        "background" to (selectedBgUri?.toString() ?: selectedBgResId?.let { "android.resource://$packageName/$it" } ?: ""),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "relationships" to relationships.map { it.copy(fromId = charId) },
                        "sfwOnly" to isSfwOnly,
                        "outfits" to updatedOutfits // will save as a list of outfits, each with poseSlots (name+uri)
                    )
                    firestore.collection("characters").document(charId)
                        .set(charData)
                        .addOnSuccessListener {
                            dismissProgressDialog()
                            Toast.makeText(this, "Character saved!", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this, CreationHubActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            startActivity(intent)
                            finish()
                        }
                        .addOnFailureListener { e ->
                            dismissProgressDialog()
                            toast("Failed to save character: ${e.message}")
                        }
                }
                .addOnFailureListener { e ->
                    dismissProgressDialog()
                    toast("Pose upload failed: ${e.message}")
                }
        }


        // Only upload new image if user picked one
        if (avatarChanged && avatarUri != null) {
            val ext = contentResolver.getType(avatarUri!!)
                ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
                ?: "jpg"
            val avatarCache = File(cacheDir, "avatar_$charId.$ext")
            contentResolver.openInputStream(avatarUri!!)?.use { input ->
                FileOutputStream(avatarCache).use { output ->
                    input.copyTo(output)
                }
            }
            val fileUri = Uri.fromFile(avatarCache)
            val ref = storage.child("characters/$charId/avatar.$ext")
            ref.putFile(fileUri)
                .continueWithTask { t -> if (!t.isSuccessful) throw t.exception!!; ref.downloadUrl }
                .addOnSuccessListener { uri ->
                    onAvatarUploaded(uri.toString())
                }
                .addOnFailureListener { e ->
                    dismissProgressDialog()
                    toast("Avatar upload failed: ${e.message}")
                }
        } else {
            onAvatarUploaded(originalAvatarUrl ?: "")
        }
    }

    private fun showProgressDialog() {
        progressDialog = AlertDialog.Builder(this)
            .setTitle("Creating Character")
            .setMessage("Please don’t close the app. Your character is being created…")
            .setCancelable(false)
            .show()
    }
    private fun dismissProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RELATIONSHIP_REQ_CODE && resultCode == Activity.RESULT_OK && data != null) {
            val relJson = data.getStringExtra("RELATIONSHIPS_JSON")
            if (!relJson.isNullOrEmpty()) {
                relationships = Gson().fromJson(relJson, Array<Relationship>::class.java).toList()
                toast("Loaded ${relationships.size} relationships")
            }
        }
    }

    private fun fillFormFromProfile(profile: CharacterProfile) {
        nameEt.setText(profile.name)
        bioEt.setText(profile.summary)
        personalityEt.setText(profile.personality)
        privateDescEt.setText(profile.privateDescription)
        ageEt.setText(profile.age.toString())
        heightEt.setText(profile.height)
        weightEt.setText(profile.weight)
        eyeColorEt.setText(profile.eyeColor)
        hairColorEt.setText(profile.hairColor)
        physicalDescEt.setText(profile.physicalDescription)
        genderEt.setText(profile.gender)
        backstoryEt.setText(profile.backstory)
        greetingEt.setText(profile.greeting)
        outfitsList = profile.outfits ?: emptyList()
        if (!profile.avatarUri.isNullOrBlank()) {
            avatarUri = Uri.parse(profile.avatarUri)
            originalAvatarUrl = profile.avatarUri
            Glide.with(this)
                .load(profile.avatarUri)
                .placeholder(R.drawable.placeholder_avatar)
                .error(R.drawable.placeholder_avatar)
                .into(avatarView)
        }
        bubbleColorSpinner.setSelection(colorOptions.indexOfFirst { it.second == profile.bubbleColor }.takeIf { it >= 0 } ?: 0)
        textColorSpinner.setSelection(colorOptions.indexOfFirst { it.second == profile.textColor }.takeIf { it >= 0 } ?: 0)
    }
}
